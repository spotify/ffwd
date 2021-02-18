/*
 * -\-\-
 * FastForward OpenTelemetry Module
 * --
 * Copyright (C) 2021 Spotify AB.
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

package com.spotify.ffwd.opentelemetry;

import com.google.inject.Inject;
import com.spotify.ffwd.model.v2.Batch;
import com.spotify.ffwd.model.v2.Metric;
import com.spotify.ffwd.output.PluginSink;
import com.spotify.ffwd.util.BatchMetricConverter;
import eu.toolchain.async.AsyncFramework;
import eu.toolchain.async.AsyncFuture;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Metadata;
import io.grpc.stub.MetadataUtils;
import io.opentelemetry.proto.collector.metrics.v1.ExportMetricsServiceRequest;
import io.opentelemetry.proto.collector.metrics.v1.MetricsServiceGrpc;
import io.opentelemetry.proto.common.v1.StringKeyValue;
import io.opentelemetry.proto.metrics.v1.DoubleDataPoint;
import io.opentelemetry.proto.metrics.v1.DoubleGauge;
import io.opentelemetry.proto.metrics.v1.InstrumentationLibraryMetrics;
import io.opentelemetry.proto.metrics.v1.ResourceMetrics;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OpenTelemetryPluginSink implements PluginSink {

  private static final Logger log = LoggerFactory.getLogger(OpenTelemetryPluginSink.class);

  @Inject private AsyncFramework async;
  private String endpoint;
  private Map<String, String> headers;
  private ManagedChannel channel;
  private MetricsServiceGrpc.MetricsServiceBlockingStub stub;

  OpenTelemetryPluginSink(
      String endpoint,
      Map<String, String> headers
  ) {
    this.endpoint = endpoint;
    this.headers = headers;
  }


  @Override
  public void sendMetric(Metric metric) {
    if (metric.hasDistribution()) {
      log.warn("Distributions not supported, dropping metric");
      return;
    }

    Map<String, String> tags = metric.getTags();

    // NB(hexedpackets): Name is supposed to uniquely identify the metric. The combination
    // of `key` and `what` may or may not be adequate. It might make sense to have this be
    // configurable?
    String name = metric.getKey() + "." + tags.get("what");
    DoubleGauge gauge = convertGauge(metric);
    io.opentelemetry.proto.metrics.v1.Metric.Builder metricBuilder =
        io.opentelemetry.proto.metrics.v1.Metric.newBuilder()
            .setName(name)
            .setDoubleGauge(gauge);

    String unit = tags.get("unit");
    if (unit != null) {
      metricBuilder.setUnit(unit);
    }

    ExportMetricsServiceRequest request = ExportMetricsServiceRequest.newBuilder()
        .addResourceMetrics(ResourceMetrics.newBuilder()
            .addInstrumentationLibraryMetrics(
                InstrumentationLibraryMetrics.newBuilder().addMetrics(metricBuilder.build())
                    .build())
            .build())
        .build();

    try {
      //noinspection ResultOfMethodCallIgnored
      stub.export(request);
    } catch (Exception e) {
      log.error(e.getMessage());
    }
  }

  @Override
  public void sendBatch(Batch batch) {
    // TODO(hexedpackets): The batch point iteration is temporary.
    //  Multiple metrics can be more efficiently sent in a single protobuf
    batch.getPoints().forEach(point ->
        sendMetric(BatchMetricConverter.convertBatchMetric(batch, point)));
  }

  @Override
  public AsyncFuture<Void> start() {
    channel = ManagedChannelBuilder.forTarget(this.endpoint)
        .useTransportSecurity()
        .build();

    MetricsServiceGrpc.MetricsServiceBlockingStub stub =
        MetricsServiceGrpc.newBlockingStub(channel);

    Metadata extraHeaders = new Metadata();
    headers.forEach((key, value) -> {
      Metadata.Key<String> header = Metadata.Key.of(key, Metadata.ASCII_STRING_MARSHALLER);
      extraHeaders.put(header, value);
    });
    this.stub = MetadataUtils.attachHeaders(stub, extraHeaders);

    return async.resolved();
  }

  @Override
  public AsyncFuture<Void> stop() {
    channel.shutdown();
    try {
      channel.awaitTermination(30, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      log.error("Channel termination interrupted: ", e);
      return async.failed(e);
    }
    return async.resolved();
  }

  @Override
  public boolean isReady() {
    return (channel != null && !channel.isTerminated());
  }

  private DoubleGauge convertGauge(Metric metric) {
    Map<String, String> tags = new HashMap<>();
    tags.putAll(metric.getTags());
    tags.putAll(metric.getResource());
    tags.put("key", metric.getKey());
    List<StringKeyValue> labels = convertTags(tags);

    return DoubleGauge.newBuilder()
        .addDataPoints(DoubleDataPoint.newBuilder()
            .setStartTimeUnixNano(0)
            .setTimeUnixNano(metric.getTimestamp() * 1000 * 1000)
            .setValue((Double) metric.getValue().getValue())
            .addAllLabels(labels)
            .build())
        .build();
  }

  private List<StringKeyValue> convertTags(Map<String, String> tags) {
    return tags.entrySet()
        .parallelStream()
        .map(entry -> StringKeyValue.newBuilder()
            .setKey(entry.getKey())
            .setValue(entry.getValue())
            .build())
        .collect(Collectors.toList());
  }
}
