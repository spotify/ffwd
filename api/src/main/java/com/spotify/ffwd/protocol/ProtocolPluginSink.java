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

package com.spotify.ffwd.protocol;

import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;
import com.spotify.ffwd.filter.Filter;
import com.spotify.ffwd.filter.TrueFilter;
import com.spotify.ffwd.model.v2.Batch;
import com.spotify.ffwd.model.v2.Metric;
import com.spotify.ffwd.output.BatchablePluginSink;
import eu.toolchain.async.AsyncFramework;
import eu.toolchain.async.AsyncFuture;
import java.util.Collection;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;

public class ProtocolPluginSink implements BatchablePluginSink {
    @Inject
    private AsyncFramework async;

    @Inject
    private ProtocolClients clients;

    @Inject
    private Protocol protocol;

    @Inject
    private ProtocolClient client;

    @Inject
    private Logger log;

    @Inject(optional = true)
    private Filter filter = null;

    private final RetryPolicy retry;

    private final AtomicReference<ProtocolConnection> connection = new AtomicReference<>();

    public ProtocolPluginSink(RetryPolicy retry) {
        this.retry = retry;
    }

    @Override
    public void init() {
    }

    @Override
    public void sendMetric(Metric metric) {
        final ProtocolConnection c = connection.get();

        if (c == null) {
            return;
        }

        if (filter != null && !filter.matchesMetric(metric)) {
            return;
        }

        c.send(metric);
    }

    @Override
    public void sendBatch(Batch batch) {
        final ProtocolConnection c = connection.get();

        if (c == null) {
            return;
        }

        c.send(batch);
    }

    @Override
    public AsyncFuture<Void> sendMetrics(Collection<Metric> metrics) {
        final ProtocolConnection c = connection.get();

        if (c == null) {
            return async.failed(new IllegalStateException("not connected to " + protocol));
        }

        return c.sendAll(filterMetrics(metrics));
    }

    @Override
    public AsyncFuture<Void> sendBatches(final Collection<Batch> batches) {
        final ProtocolConnection c = connection.get();

        if (c == null) {
            return async.failed(new IllegalStateException("not connected to " + protocol));
        }

        return c.sendAll(batches);
    }


    public Collection<Metric> filterMetrics(Collection<Metric> input) {
        if (filter == null || filter instanceof TrueFilter) {
            return input;
        }

        final ImmutableList.Builder<Metric> output = ImmutableList.builder();

        for (final Metric m : input) {
            if (filter.matchesMetric(m)) {
                output.add(m);
            }
        }

        return output.build();
    }

    @Override
    public AsyncFuture<Void> start() {
        return clients
            .connect(log, protocol, client, retry)
            .lazyTransform(result -> {
                if (!connection.compareAndSet(null, result)) {
                    return result.stop();
                }

                return async.resolved(null);
            });
    }

    @Override
    public AsyncFuture<Void> stop() {
        final ProtocolConnection c = connection.getAndSet(null);

        if (c == null) {
            return async.resolved(null);
        }

        return c.stop();
    }

    @Override
    public boolean isReady() {
        final ProtocolConnection c = connection.get();

        if (c == null) {
            return false;
        }

        return c.isConnected();
    }
}
