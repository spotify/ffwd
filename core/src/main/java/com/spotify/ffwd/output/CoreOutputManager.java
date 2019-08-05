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

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.spotify.ffwd.debug.DebugServer;
import com.spotify.ffwd.filter.Filter;
import com.spotify.ffwd.model.Batch;
import com.spotify.ffwd.model.Metric;
import com.spotify.ffwd.statistics.OutputManagerStatistics;
import eu.toolchain.async.AsyncFramework;
import eu.toolchain.async.AsyncFuture;
import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import org.isomorphism.util.TokenBucket;
import org.isomorphism.util.TokenBuckets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CoreOutputManager implements OutputManager {
    private static final String DEBUG_ID = "core.output";
    private static final String HOST = "host";
    private static final Logger log = LoggerFactory.getLogger(CoreOutputManager.class);

    private final TokenBucket rateLimiter;

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

    public final Long getRateLimit() {
        if (rateLimiter == null) {
            return null;
        }
        return rateLimiter.getCapacity();
    }

    @Inject
    CoreOutputManager(@Named("rateLimit") @Nullable Integer rateLimit) {
        if (rateLimit != null && rateLimit > 0) {
            // Create a rate limiter with a configurable QPS, and
            // tick every half second to reduce the delay between refills.
            rateLimiter = TokenBuckets.builder()
              .withCapacity(rateLimit)
              .withInitialTokens(rateLimit)
              .withFixedIntervalRefillStrategy(rateLimit / 2, 500, TimeUnit.MILLISECONDS)
              .build();
        } else {
            rateLimiter = null;
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

        if (!rateLimitAllowed(1)) {
            log.debug("Dropping a metric due to rate limiting");
            statistics.reportMetricsDroppedByRateLimit(1);
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

        if (batchSize > 0 && !rateLimitAllowed(batchSize)) {
            log.debug("Dropping {} metrics due to rate limiting", batchSize);
            statistics.reportMetricsDroppedByRateLimit(batchSize);
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
