// $LICENSE
/**
 * Copyright 2013-2014 Spotify AB. All rights reserved.
 * <p>
 * The contents of this file are licensed under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 **/
package com.spotify.ffwd.signalfx;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.inject.Inject;
import com.signalfx.metrics.flush.AggregateMetricSender;
import com.signalfx.metrics.protobuf.SignalFxProtocolBuffers;
import com.spotify.ffwd.model.Event;
import com.spotify.ffwd.model.Metric;
import com.spotify.ffwd.output.BatchedPluginSink;
import eu.toolchain.async.AsyncFramework;
import eu.toolchain.async.AsyncFuture;
import eu.toolchain.async.FutureFailed;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Slf4j
public class SignalFxPluginSink implements BatchedPluginSink {
    @Inject
    private AsyncFramework async;

    @Inject
    private Supplier<AggregateMetricSender> senderSupplier;

    private final ExecutorService executorService;

    public SignalFxPluginSink(int asyncThreads) {
        executorService = Executors.newFixedThreadPool(asyncThreads, new ThreadFactoryBuilder()
            .setNameFormat("ffwd-signalfx-async-%d").build());
    }
    @Override
    public void init() {
    }

    @Override
    public void sendEvent(final Event event) {
        sendEvents(Collections.singletonList(event));
    }

    @Override
    public void sendMetric(final Metric metric) {
        sendMetrics(Collections.singletonList(metric));
    }

    @Override
    public AsyncFuture<Void> sendEvents(final Collection<Event> events) {
        // Ignore all events
        return async.call(new Callable<Void>() {
            @Override
            public Void call() throws Exception {
                return null;
            }
        }, executorService);
    }

    @Override
    public AsyncFuture<Void> sendMetrics(final Collection<Metric> metrics) {
        final AsyncFuture<Void> future = async.call(new Callable<Void>() {
            @Override
            public Void call() throws Exception {
                try (AggregateMetricSender.Session i = senderSupplier.get().createSession()) {
                    for (Metric metric : metrics) {
                        SignalFxProtocolBuffers.DataPoint.Builder datapointBuilder = SignalFxProtocolBuffers.DataPoint.newBuilder()
                                .setMetric(composeMetricIdentity(metric))
                                .setMetricType(SignalFxProtocolBuffers.MetricType.GAUGE).setValue(
                                        SignalFxProtocolBuffers.Datum.newBuilder()
                                                .setDoubleValue(metric.getValue())
                                )
                                .setTimestamp(metric.getTime().getTime());
                        metric.getTags().entrySet()
                                .stream()
                                .map(attribute -> SignalFxProtocolBuffers.Dimension.newBuilder()
                                        .setKey(attribute.getKey())
                                        .setValue(attribute.getValue())
                                        .build())
                                .forEach(datapointBuilder::addDimensions);
                        datapointBuilder.addDimensions(
                                SignalFxProtocolBuffers.Dimension.newBuilder()
                                        .setKey("host")
                                        .setValue(metric.getHost())
                                        .build()
                        );
                        final SignalFxProtocolBuffers.DataPoint dataPoint = datapointBuilder.build();
                        i.setDatapoint(dataPoint);
                    }
                }
                return null;
            }
        }, executorService);

        future.on(new FutureFailed() {
            @Override
            public void failed(Throwable throwable) throws Exception {
                log.error("Failed to send metrics", throwable);
            }
        });

        return future;
    }

    private String composeMetricIdentity(Metric metric) {
        final List<String> metricIdentity = new ArrayList<>();
        metricIdentity.add(metric.getKey());

        final Map<String, String> tags = metric.getTags();
        final String what = tags.get("what");
        if (what != null){
            metricIdentity.add(what);
            final String stat = tags.get("stat");
            if (stat != null){
                metricIdentity.add(stat);
            }
        }
        return metricIdentity.stream().collect(Collectors.joining("."));
    }

    @Override
    public AsyncFuture<Void> start() {
        return async.resolved();
    }

    @Override
    public AsyncFuture<Void> stop() {
        return async.resolved();
    }

    @Override
    public boolean isReady() {
        return true;
    }
}
