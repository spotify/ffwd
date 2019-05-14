/*-
 * -\-\-
 * FastForward SignalFx Module
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

package com.spotify.ffwd.signalfx;

import com.google.common.base.Strings;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.inject.Inject;
import com.signalfx.metrics.flush.AggregateMetricSender;
import com.signalfx.metrics.protobuf.SignalFxProtocolBuffers;
import com.spotify.ffwd.model.Batch;
import com.spotify.ffwd.model.Event;
import com.spotify.ffwd.model.Metric;
import com.spotify.ffwd.output.BatchablePluginSink;
import com.spotify.ffwd.output.FakeBatchablePluginSinkBase;
import eu.toolchain.async.AsyncFramework;
import eu.toolchain.async.AsyncFuture;
import eu.toolchain.async.FutureFailed;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SignalFxPluginSink extends FakeBatchablePluginSinkBase implements BatchablePluginSink {
    @Inject
    AsyncFramework async;

    @Inject
    Supplier<AggregateMetricSender> senderSupplier;

    /* @see https://docs.signalfx.com/en/latest/best-practices/naming-conventions.html */

    private static final int CHAR_LIMIT = 256;

    private final ExecutorService executorService = Executors.newCachedThreadPool(
        new ThreadFactoryBuilder().setNameFormat("ffwd-signalfx-async-%d").build());

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
    public void sendBatch(final Batch batch) {
        sendBatches(Collections.singletonList(batch));
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
    public AsyncFuture<Void> sendBatches(final Collection<Batch> batches) {
        final List<Metric> metrics = convertBatchesToMetrics(batches);
        return sendMetrics(metrics);
    }


    @Override
    public AsyncFuture<Void> sendMetrics(final Collection<Metric> metrics) {
        final AsyncFuture<Void> future = async.call(new Callable<Void>() {
            @Override
            public Void call() throws Exception {
                try (AggregateMetricSender.Session i = senderSupplier.get().createSession()) {
                    for (Metric metric : metrics) {
                        final SignalFxProtocolBuffers.DataPoint.Builder datapointBuilder =
                            SignalFxProtocolBuffers.DataPoint
                                .newBuilder()
                                .setMetric(composeMetricIdentity(metric))
                                .setMetricType(getMetricType(metric))
                                .setValue(SignalFxProtocolBuffers.Datum
                                    .newBuilder()
                                    .setDoubleValue(metric.getValue()))
                                .setTimestamp(metric.getTime().getTime());

                        metric
                            .getTags()
                            .entrySet()
                            .stream()
                            .map(attribute -> SignalFxProtocolBuffers.Dimension
                                .newBuilder()
                                .setKey(attribute.getKey())
                                .setValue(composeDimensionValue(attribute.getValue()))
                                .build())
                            .forEach(datapointBuilder::addDimensions);

                      metric
                        .getResource()
                        .entrySet()
                        .stream()
                        .map(attribute -> SignalFxProtocolBuffers.Dimension
                          .newBuilder()
                          .setKey(attribute.getKey())
                          .setValue(composeDimensionValue(attribute.getValue()))
                          .build())
                        .forEach(datapointBuilder::addDimensions);

                        // TODO: Why is this here? When we loop above all the tags above.
                        final String host = metric.getTags().get("host");
                        if (host != null) {
                            datapointBuilder.addDimensions(SignalFxProtocolBuffers.Dimension
                                .newBuilder()
                                .setKey("host")
                                .setValue(host)
                                .build());
                        }

                        final SignalFxProtocolBuffers.DataPoint dataPoint =
                            datapointBuilder.build();
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

    /**
     * Get the appropriate SignalFx metric type
     *
     * https://docs.signalfx.com/en/latest/getting-started/concepts/metric-types.html
     * @param metric Metric to check its type
     * @return SignalFx MetricType
     */
    private SignalFxProtocolBuffers.MetricType getMetricType(final Metric metric) {
        final SignalFxProtocolBuffers.MetricType metricType;
        if (metric.getTags().getOrDefault("metric_type", "").equals("counter")) {
            metricType = SignalFxProtocolBuffers.MetricType.CUMULATIVE_COUNTER;
        } else {
            metricType = SignalFxProtocolBuffers.MetricType.GAUGE;
        }
        return metricType;
    }

    private String composeMetricIdentity(final Metric metric) {
        final List<String> metricIdentity = new ArrayList<>();
        metricIdentity.add(metric.getKey());

        final Map<String, String> tags = metric.getTags();
        final String what = tags.get("what");
        if (what != null) {
            metricIdentity.add(what);
            final String stat = tags.get("stat");
            if (stat != null) {
                metricIdentity.add(stat);
            }
        }
        String resultIdentity = metricIdentity.stream().collect(Collectors.joining("."));

        return resultIdentity.length() > CHAR_LIMIT ? resultIdentity.substring(0, CHAR_LIMIT)
            : resultIdentity;
    }

    private String composeDimensionValue(final String value) {
        final String dimensionVal = Strings.nullToEmpty(value);

        return dimensionVal.length() > CHAR_LIMIT ? dimensionVal.substring(0, CHAR_LIMIT)
            : dimensionVal;
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
