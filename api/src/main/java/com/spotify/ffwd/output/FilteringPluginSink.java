/*-
 * -\-\-
 * FastForward API
 * --
 * Copyright (C) 2016 - 2018 Spotify AB
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

import com.google.inject.Inject;
import com.spotify.ffwd.filter.Filter;
import com.spotify.ffwd.model.v2.Batch;
import com.spotify.ffwd.model.v2.Metric;
import eu.toolchain.async.AsyncFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FilteringPluginSink implements PluginSink {
    private static final Logger log = LoggerFactory.getLogger(FilteringPluginSink.class);

    @Inject
    @FilteringDelegate
    protected PluginSink sink;

    protected Filter filter;

    public FilteringPluginSink(final Filter filter) {
        this.filter = filter;
    }

    @Override
    public void init() {
        sink.init();
    }

    @Override
    public void sendMetric(final Metric metric) {
        if (filter.matchesMetric(metric)) {
            sink.sendMetric(metric);
        }
    }

    @Override
    public void sendBatch(final Batch batch) {
        if (filter.matchesBatch(batch)) {
            sink.sendBatch(batch);
        }
    }

    @Override
    public AsyncFuture<Void> start() {
        log.info("Starting filtering sink {}", filter);
        return sink.start();
    }

    @Override
    public AsyncFuture<Void> stop() {
        return sink.stop();
    }

    @Override
    public boolean isReady() {
        return sink.isReady();
    }

    public PluginSink getSink() {
        return this.sink;
    }
}
