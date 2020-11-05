/*-
 * -\-\-
 * FastForward Core
 * --
 * Copyright (C) 2016 - 2019 Spotify AB
 * --
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * -/-/-
 */

package com.spotify.ffwd.output;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.spotify.ffwd.debug.DebugServer;
import com.spotify.ffwd.filter.Filter;
import com.spotify.ffwd.model.Batch;
import com.spotify.ffwd.model.Metric;
import com.spotify.ffwd.statistics.OutputManagerStatistics;
import com.spotify.ffwd.util.BatchMetricConverter;
import eu.toolchain.async.AsyncFramework;
import eu.toolchain.async.AsyncFuture;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import net.agkn.hll.HLL;
import net.agkn.hll.HLLType;
import org.isomorphism.util.TokenBucket;
import org.isomorphism.util.TokenBuckets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CoreOutputManager implements OutputManager {
    private static final String DEBUG_ID = "core.output";
    private static final String HOST = "host";
    private static final Logger log = LoggerFactory.getLogger(CoreOutputManager.class);
    private static final String[] KEYS_NEVER_TO_DROP = {"ffwd-java", "ffwd-java.ffwd-java"};
    private static final int HYPER_LOG_LOG_LOG2M = 14;
    private static final int HYPER_LOG_LOG_REG_WIDTH = 5;

    private final TokenBucket rateLimiter;
    private final Long cardinalityLimit;

    @Inject
    private List<PluginSink> sinks;

    public List<PluginSink> getSinks() {
        return this.sinks;
    }

    @Inject
    private AsyncFramework async;

    @Inject
    @Named("tags")
    private Map<String, String> tags;

    @VisibleForTesting
    public Map<String, String> getTags() {
        return tags;
    }

    @Inject
    @Named("tagsToResource")
    private Map<String, String> tagsToResource;

    @Inject
    @Named("resource")
    private Map<String, String> resource;

    @Inject
    @Named("riemannTags")
    private Set<String> riemannTags;

    @Inject
    @Named("skipTagsForKeys")
    private Set<String> skipTagsForKeys;

    @Inject
    @Named("automaticHostTag")
    private Boolean automaticHostTag;

    @Inject
    @Named("host")
    private String host;

    public String getHost() {
        return host;
    }

    @Inject
    @Named("ttl")
    private long ttl;

    public long getTtl() {
        return ttl;
    }

    @Inject
    private DebugServer debug;

    @Inject
    private OutputManagerStatistics statistics;

    @Inject
    private Filter filter;

    @Inject
    private String dynamicTagsFile;

    private AtomicReference<HLL> hyperLog;
    private AtomicLong hyperLogSwapTS;
    private AtomicBoolean hyperLogSwapLock;
    private Long hyperLogLogPlusSwapPeriodMS;
    private ScheduledExecutorService tagRefreshingThread = null;

    public final Long getRateLimit() {
        if (rateLimiter == null) {
            return null;
        }
        return rateLimiter.getCapacity();
    }

    public final Long getCardinalityLimit() {
        return cardinalityLimit;
    }

    public final Long getHLLPRefreshPeriodLimit() {
        return hyperLogLogPlusSwapPeriodMS;
    }

    @Inject
    CoreOutputManager(@Named("rateLimit") @Nullable Integer rateLimit,
        @Named("cardinalityLimit") @Nullable Long cardinalityLimit,
        @Named("hyperLogLogPlusSwapPeriodMS") @Nullable Long hyperLogLogPlusSwapPeriodMS,
        @Named("dynamicTagsFile") @Nullable String dynamicTagsFile) {

        if (rateLimit != null && rateLimit > 0) {
            // Create a rate limiter with a configurable QPS, and
            // tick every half second to reduce the delay between refills.
            log.info("Initializing rate limiting: {} per second", rateLimit);
            rateLimiter = TokenBuckets.builder()
              .withCapacity(rateLimit)
              .withInitialTokens(rateLimit)
              .withFixedIntervalRefillStrategy(rateLimit / 2, 500, TimeUnit.MILLISECONDS)
              .build();
        } else {
            rateLimiter = null;
        }

        hyperLog = new AtomicReference<>(new HLL(
                HYPER_LOG_LOG_LOG2M,
                HYPER_LOG_LOG_REG_WIDTH,
                -1, false, HLLType.FULL));
        hyperLogSwapTS =  new AtomicLong(System.currentTimeMillis());
        hyperLogSwapLock = new AtomicBoolean(false);
        this.hyperLogLogPlusSwapPeriodMS =
            Optional.ofNullable(hyperLogLogPlusSwapPeriodMS).orElse(3_600_000L);

        if (cardinalityLimit != null && cardinalityLimit > 0) {
            // Use cardinalityLimit to limit cardinality
            log.info("Initializing cardinality limit: {}", cardinalityLimit);
            log.info("Initializing HyperLogLogPlus swap time: {} ms",
                this.hyperLogLogPlusSwapPeriodMS);
            this.cardinalityLimit = cardinalityLimit;
        } else {
            this.cardinalityLimit = null;
        }

        if (!Strings.isNullOrEmpty(dynamicTagsFile)) {
            final File file = new File(dynamicTagsFile);
            if (file.exists() && file.canRead()) {
                final int initialDelay =
                    System.getenv().containsKey("IS_FFWD_CONFIGURATION_TEST") ? 3 : 60;
                tagRefreshingThread = Executors.newSingleThreadScheduledExecutor(
                    new ThreadFactoryBuilder().setNameFormat("tag-refreshing-thread").build());
                tagRefreshingThread.scheduleWithFixedDelay(() -> refreshTagsFromFile(file.toPath()),
                    initialDelay, 60, TimeUnit.SECONDS);
            } else {
                log.warn("Cannot read file at {}. Ignoring it.", dynamicTagsFile);
            }
        }
    }

    private void refreshTagsFromFile(final Path path) {
        try (final Stream<String> lines = Files.lines(path)) {
            lines.forEach(label -> {
                final String[] labelParts = label.split("=", 2);
                // Only update tag values if the tag keys are explicitly specified in normal configs
                if (tags.get(labelParts[0]) != null) {
                    // Remove quotes from the value string
                    tags.put(labelParts[0], labelParts[1].replace("\"", ""));
                }
            });
        } catch (IOException e) {
            // Ignore
        }
    }

    @Override
    public void init() {
        log.info("Initializing (filter: {})", filter);

        for (final PluginSink s : sinks) {
            s.init();
        }
    }

    @Override
    public void sendMetric(Metric metric) {
        if (!filter.matchesMetric(metric)) {
            statistics.reportMetricsDroppedByFilter(1);
            return;
        }

        final Metric filtered = filter(metric);

        debug.inspectMetric(DEBUG_ID, filtered);

        hyperLog.get().addRaw(metric.hashCode());
        statistics.reportMetricsCardinality(hyperLog.get().cardinality());

        if (isDroppable(1, metric.getKey())) {
            return;
        }

        sinks.stream()
          .filter(PluginSink::isReady)
          .forEach(s -> s.sendMetric(filtered));

        statistics.reportSentMetrics(1);
    }

    @Override
    public void sendBatch(Batch batch) {
        final Batch filtered = filter(batch);

        debug.inspectBatch(DEBUG_ID, filtered);

        int batchSize = batch.getPoints().size();

        BatchMetricConverter
            .convertBatchesToMetrics(Collections.singletonList(batch))
            .stream().forEach(metric -> hyperLog.get().addRaw(metric.hashCode()));

        statistics.reportMetricsCardinality(hyperLog.get().cardinality());

        if (isDroppable(batchSize, batch.getPoints().get(0).getKey())) {
            return;
        }

        sinks.stream()
          .filter(PluginSink::isReady)
          .forEach(s -> s.sendBatch(filtered));

        statistics.reportSentMetrics(batchSize);
    }

    @Override
    public AsyncFuture<Void> start() {
        List<AsyncFuture<Void>> futures = sinks.stream()
          .map(PluginSink::start)
          .collect(Collectors.toList());

        return async.collectAndDiscard(futures);
    }

    @Override
    public AsyncFuture<Void> stop() {
        List<AsyncFuture<Void>> futures = sinks.stream()
          .map(PluginSink::stop)
          .collect(Collectors.toList());

        return async.collectAndDiscard(futures);
    }

    private boolean rateLimitAllowed(int permits) {
        if (rateLimiter == null) {
            return true;
        }
        try {
            return rateLimiter.tryConsume(permits);
        } catch (IllegalArgumentException e) {
            // Thrown if permits > max capacity or permits is not a positive number
            return false;
        }
    }

    private boolean cardinalityLimitAllowed(long currentCardinality) {
        return cardinalityLimit == null || cardinalityLimit >= currentCardinality;
    }

    /**
    * To reset cardinality this will swap HLL++ if it was tripped after configured period of ms
    */
    private void swapHyperLogLogPlus() {
        if (System.currentTimeMillis() - hyperLogSwapTS.get() > hyperLogLogPlusSwapPeriodMS
            && hyperLogSwapLock.compareAndExchange(false, true)) {
            hyperLog.set(new HLL(
                    HYPER_LOG_LOG_LOG2M,
                    HYPER_LOG_LOG_REG_WIDTH,
                    -1, false, HLLType.FULL));
            hyperLogSwapTS.set(System.currentTimeMillis());
            hyperLogSwapLock.set(false);
        }
    }

    /**
     * Makes sure either batch or individual metric should be dropped either
     *  1. by rate limit
     *  2. by cardinality limit
     */
    private boolean isDroppable(final int batchSize, String key) {
        if (!Arrays.asList(KEYS_NEVER_TO_DROP).contains(key)) {
            if (batchSize > 0 && !rateLimitAllowed(batchSize)) {
                log.debug("Dropping {} metrics due to rate limiting", batchSize);
                statistics.reportMetricsDroppedByRateLimit(batchSize);
                return true;
            }

            if (!cardinalityLimitAllowed(hyperLog.get().cardinality())) {
              log.debug(
                  "Dropping {} metrics due to cardinality limiting; cardinality {}",
                  batchSize,
                  hyperLog.get().cardinality());
              statistics.reportMetricsDroppedByCardinalityLimit(batchSize);
              swapHyperLogLogPlus();
              return true;
            }
        }
        return false;
    }

    /**
     * Filter the provided Metric and complete fields.
     */
    private Metric filter(final Metric metric) {
        final Date time = metric.getTime() != null ? metric.getTime() : new Date();

        final Map<String, String> tags = selectTags(metric);
        final Map<String, String> commonResource = Maps.newHashMap(resource);
        commonResource.putAll(metric.getResource());

        final SimpleImmutableEntry<Map<String, String>, Map<String, String>>
            tagsAndResources = processTagsToResource(tags, commonResource);
        final Map<String, String> mergedTags = tagsAndResources.getKey();
        final Map<String, String> mergedResource = tagsAndResources.getValue();

        final Set<String> mergedRiemannTags = Sets.newHashSet(riemannTags);
        mergedRiemannTags.addAll(metric.getRiemannTags());

        return new Metric(metric.getKey(), metric.getValue(), time, mergedRiemannTags,
            mergedTags, mergedResource, metric.getProc());
    }

    /**
     * Filter the provided Batch and complete fields.
     */
    private Batch filter(final Batch batch) {
        final Map<String, String> commonTags = Maps.newHashMap(tags);
        commonTags.putAll(batch.getCommonTags());

        final Map<String, String> commonResource = Maps.newHashMap(resource);
        commonResource.putAll(batch.getCommonResource());

        final SimpleImmutableEntry<Map<String, String>, Map<String, String>>
            tagsAndResources = processTagsToResource(commonTags, commonResource);
        final Map<String, String> mergedCommonTags = tagsAndResources.getKey();
        final Map<String, String> mergedCommonResource = tagsAndResources.getValue();

        final List<Batch.Point> points = batch.getPoints().stream().map(point -> {
            final Map<String, String> pointTags = point.getTags();
            final Map<String, String> pointResource = point.getResource();

            final SimpleImmutableEntry<Map<String, String>, Map<String, String>>
                pointTagsAndResources = processTagsToResource(pointTags, pointResource);
            final Map<String, String> mergedTags = pointTagsAndResources.getKey();
            final Map<String, String> mergedResource = pointTagsAndResources.getValue();

            return new Batch.Point(
                point.getKey(),
                mergedTags,
                mergedResource,
                point.getValue(),
                point.getTimestamp());

        }).collect(Collectors.toList());

        return new Batch(mergedCommonTags, mergedCommonResource, points);
    }

    private Map<String, String> selectTags(Metric metric) {
        if (skipTagsForKeys.contains(metric.getKey())) {
            return metric.getTags();
        }

        final Map<String, String> mergedTags;
        mergedTags = Maps.newHashMap(tags);
        mergedTags.putAll(metric.getTags());

        if (automaticHostTag) {
            mergedTags.putIfAbsent(HOST, this.host);
        }

        return mergedTags;
    }

    /**
     * Potentially convert some tags to resource identifiers - i.e. tags in tagsToResource conf.
     *
     * If there are conflicts, the existing resource identifiers takes precedence over tags.
     *
     * @param tags Map of tags that will be used when constructing a metric
     * @param resource Map of resource identifiers that will be used when constructing a metric
     */
    private SimpleImmutableEntry<Map<String, String>, Map<String, String>> processTagsToResource(
        final Map<String, String> tags, final Map<String, String> resource
    ) {
        if (tagsToResource.isEmpty()) {
            return new SimpleImmutableEntry<>(tags, resource);
        }

        final Map<String, String> mergedResources = new HashMap<>(resource);
        final Map<String, String> strippedTags = new HashMap<>(tags);

        tagsToResource.forEach((fromTag, toResource) -> {
            final String tag = strippedTags.remove(fromTag);
            // Set as resource if the tag exists and there were not already a resource with the
            // wanted name
            if (tag != null) {
                mergedResources.putIfAbsent(toResource, tag);
            }
        });

        return new SimpleImmutableEntry<>(strippedTags, mergedResources);
    }
}
