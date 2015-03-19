// $LICENSE
/**
 * Copyright 2013-2014 Spotify AB. All rights reserved.
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
 **/
package com.spotify.ffwd.noop;

import java.util.Collection;
import java.util.concurrent.atomic.AtomicLong;

import lombok.extern.slf4j.Slf4j;

import com.google.inject.Inject;
import com.spotify.ffwd.model.Event;
import com.spotify.ffwd.model.Metric;
import com.spotify.ffwd.output.BatchedPluginSink;

import eu.toolchain.async.AsyncFramework;
import eu.toolchain.async.AsyncFuture;

@Slf4j
public class NoopPluginSink implements BatchedPluginSink {
    private final AtomicLong date = new AtomicLong();
    private final AtomicLong last = new AtomicLong();
    private final AtomicLong total = new AtomicLong();

    @Inject
    private AsyncFramework async;

    @Override
    public void sendEvent(Event event) {
    }

    @Override
    public void sendMetric(Metric metric) {
    }

    @Override
    public AsyncFuture<Void> sendEvents(Collection<Event> events) {
        return count(events.size());
    }

    @Override
    public AsyncFuture<Void> sendMetrics(Collection<Metric> metrics) {
        return count(metrics.size());
    }

    private AsyncFuture<Void> count(int size) {
        final long now = System.currentTimeMillis();
        final long then = this.date.getAndSet(now);
        final long total = this.total.addAndGet(size);

        final double rate;

        final long diff = (now - then) / 1000;

        if (then != 0 && diff != 0) {
            final long seen = total - this.last.getAndSet(total);
            rate = Math.round((double)seen / (double)diff);
        } else {
            rate = Double.NaN;
        }

        log.info("{} things/s (total: {})", rate, total);
        return async.resolved(null);
    }

    @Override
    public AsyncFuture<Void> start() {
        return async.resolved(null);
    }

    @Override
    public AsyncFuture<Void> stop() {
        return async.resolved(null);
    }

    @Override
    public boolean isReady() {
        return true;
    }
}