/*
 * Copyright 2013-2017 Spotify AB. All rights reserved.
 *
 * The contents of this file are licensed under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.spotify.ffwd.output;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.spotify.ffwd.debug.DebugServer;
import com.spotify.ffwd.filter.Filter;
import com.spotify.ffwd.model.Batch;
import com.spotify.ffwd.model.Event;
import com.spotify.ffwd.model.Metric;
import com.spotify.ffwd.statistics.OutputManagerStatistics;
import eu.toolchain.async.AsyncFramework;
import eu.toolchain.async.AsyncFuture;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CoreOutputManager implements OutputManager {
    private static final String DEBUG_ID = "core.output";
    private static final String HOST = "host";

    @Inject
    @Getter
    private List<PluginSink> sinks;

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

    @Inject
    @Named("ttl")
    private long ttl;

    @Inject
    private DebugServer debug;

    @Inject
    private OutputManagerStatistics statistics;

    @Inject
    private Filter filter;

    @Override
    public void init() {
        log.info("Initializing (filter: {})", filter);

        for (final PluginSink s : sinks) {
            s.init();
        }
    }

    @Override
    public void sendEvent(Event event) {
        if (!filter.matchesEvent(event)) {
            statistics.reportEventsDroppedByFilter(1);
            return;
        }

        statistics.reportSentEvents(1);

        final Event filtered = filter(event);

        debug.inspectEvent(DEBUG_ID, filtered);

        for (final PluginSink s : sinks) {
            if (s.isReady()) {
                s.sendEvent(filtered);
            }
        }
    }

    @Override
    public void sendMetric(Metric metric) {
        if (!filter.matchesMetric(metric)) {
            statistics.reportMetricsDroppedByFilter(1);
            return;
        }

        statistics.reportSentMetrics(1);

        final Metric filtered = filter(metric);

        debug.inspectMetric(DEBUG_ID, filtered);

        for (final PluginSink s : sinks) {
            if (s.isReady()) {
                s.sendMetric(filtered);
            }
        }
    }

    @Override
    public void sendBatch(Batch batch) {
        statistics.reportSentMetrics(batch.getPoints().size());

        final Batch filtered = filter(batch);

        debug.inspectBatch(DEBUG_ID, filtered);

        for (final PluginSink s : sinks) {
            if (s.isReady()) {
                s.sendBatch(filtered);
            }
        }
    }

    @Override
    public AsyncFuture<Void> start() {
        final ArrayList<AsyncFuture<Void>> futures = Lists.newArrayList();

        for (final PluginSink s : sinks) {
            futures.add(s.start());
        }

        return async.collectAndDiscard(futures);
    }

    @Override
    public AsyncFuture<Void> stop() {
        final ArrayList<AsyncFuture<Void>> futures = Lists.newArrayList();

        for (final PluginSink s : sinks) {
            futures.add(s.stop());
        }

        return async.collectAndDiscard(futures);
    }

    /**
     * Filter the provided Event and complete fields.
     */
    private Event filter(Event event) {
        if (tags.isEmpty() && ttl == 0) {
            return event;
        }

        final String host = event.getHost() != null ? event.getHost() : this.host;
        final Map<String, String> mergedTags = Maps.newHashMap(tags);
        mergedTags.putAll(event.getTags());

        final Set<String> mergedRiemannTags = Sets.newHashSet(riemannTags);
        mergedRiemannTags.addAll(event.getRiemannTags());

        final Date time = event.getTime() != null ? event.getTime() : new Date();
        final Long ttl = event.getTtl() != 0 ? event.getTtl() : this.ttl;

        return new Event(event.getKey(), event.getValue(), time, ttl, event.getState(),
            event.getDescription(), host, mergedRiemannTags, mergedTags);
    }

    /**
     * Filter the provided Metric and complete fields.
     */
    private Metric filter(final Metric metric) {
        final Date time = metric.getTime() != null ? metric.getTime() : new Date();

        final Map<String, String> mergedTags = selectTags(metric);

        final Map<String, String> mergedResource = Maps.newHashMap(resource);
        mergedResource.putAll(metric.getResource());

        processTagsToResource(mergedTags, mergedResource);

        final Set<String> mergedRiemannTags = Sets.newHashSet(riemannTags);
        mergedRiemannTags.addAll(metric.getRiemannTags());

        return new Metric(metric.getKey(), metric.getValue(), time, mergedRiemannTags,
            mergedTags, mergedResource, metric.getProc());
    }

    /**
     * Filter the provided Batch and complete fields.
     */
    private Batch filter(final Batch batch) {
        final Map<String, String> mergedCommonResource;
        if (batch.getCommonResource().isEmpty()) {
            mergedCommonResource = resource;
        } else {
            mergedCommonResource = Maps.newHashMap(resource);
            mergedCommonResource.putAll(batch.getCommonResource());
        }

        final Map<String, String> mergedCommonTags;
        if (batch.getCommonTags().isEmpty()) {
            mergedCommonTags = tags;
        } else {
            mergedCommonTags = Maps.newHashMap(tags);
            mergedCommonTags.putAll(batch.getCommonTags());
        }

        return new Batch(mergedCommonTags, mergedCommonResource, batch.getPoints());
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
    private void processTagsToResource(
        final Map<String, String> tags, final Map<String, String> resource
    ) {
        if (tagsToResource.isEmpty()) {
            return;
        }

        tagsToResource.forEach((fromTag, toResource) -> {
            final String tag = tags.remove(fromTag);
            // Set as resource if the tag exists and there were not already a resource with the
            // wanted name
            if (tag != null) {
                resource.putIfAbsent(toResource, tag);
            }
        });
    }

}
