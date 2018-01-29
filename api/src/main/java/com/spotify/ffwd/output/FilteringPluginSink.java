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

import com.google.inject.Inject;
import com.spotify.ffwd.filter.Filter;
import com.spotify.ffwd.model.Batch;
import com.spotify.ffwd.model.Event;
import com.spotify.ffwd.model.Metric;
import eu.toolchain.async.AsyncFuture;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class FilteringPluginSink implements PluginSink {

    @Inject
    @FilteringDelegate
    @Getter
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
    public void sendEvent(final Event event) {
        if (filter.matchesEvent(event)) {
            sink.sendEvent(event);
        }
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
}
