/*-
 * -\-\-
 * FastForward Agent
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

package com.spotify.ffwd.statistics;

import com.codahale.metrics.Counter;
import com.codahale.metrics.Gauge;
import com.codahale.metrics.Histogram;
import com.codahale.metrics.Meter;
import com.codahale.metrics.Metric;
import com.codahale.metrics.SlidingTimeWindowReservoir;
import com.spotify.metrics.core.MetricId;
import com.spotify.metrics.core.SemanticMetricBuilder;
import com.spotify.metrics.core.SemanticMetricRegistry;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public class SemanticCoreStatistics implements CoreStatistics {

  private static final int HISTOGRAM_TTL_MINUTES = 2;
  private final SemanticMetricRegistry registry;
  private final MetricId metric = MetricId.build();

  /* Builds a Histogram with a Reservoir that remembers values for a maximum of x minutes, so that
   * slow rate doesn't cause long-lingering values in the Histogram. */
  private static final SemanticMetricBuilder<Histogram> HISTOGRAM_BUILDER =
      new SemanticMetricBuilder<>() {

        @Override
        public Histogram newMetric() {
          return new Histogram(new SlidingTimeWindowReservoir(
              TimeUnit.MINUTES.toMinutes(HISTOGRAM_TTL_MINUTES), TimeUnit.MINUTES));
        }

        @Override
        public boolean isInstance(final Metric metric) {
          return metric instanceof Histogram;
        }
      };

  public SemanticCoreStatistics(SemanticMetricRegistry registry) {
    this.registry = registry;
  }

  @Override
  public InputManagerStatistics newInputManager() {
    final MetricId m = metric.tagged("component", "input-manager");

    return new InputManagerStatistics() {
      private final Meter receivedMetrics =
          registry.meter(m.tagged("what", "received-metrics", "unit", "metric"));
      private final Meter metricsDroppedByFilter =
          registry.meter(m.tagged("what", "metrics-dropped-by-filter", "unit", "metric"));

      @Override
      public void reportReceivedMetrics(int received) {
        receivedMetrics.mark(received);
      }

      @Override
      public void reportMetricsDroppedByFilter(int dropped) {
        metricsDroppedByFilter.mark(dropped);
      }
    };
  }

  @Override
  public OutputManagerStatistics newOutputManager() {
    final MetricId m = metric.tagged("component", "output-manager");
    final AtomicLong metricsCardinality = new AtomicLong();

    return new OutputManagerStatistics() {
      private final Counter sentMetrics =
          registry.counter(m.tagged("what", "sent-metrics", "unit", "metric"));
      private final Counter metricsDroppedByFilter =
          registry.counter(m.tagged("what", "metrics-dropped-by-filter", "unit", "metric"));
      private final Counter metricsDroppedByRateLimit =
          registry.counter(m.tagged("what", "metrics-dropped-by-ratelimit", "unit", "metric"));
      private final Counter metricsDroppedByCardinalityLimit =
          registry.counter(m.tagged("what", "metrics-dropped-by-cardlimit", "unit", "metric"));

      private final Gauge<Long> metricsCardinalityMetric =
          registry.register(m.tagged("what", "metrics-cardinality"),
              (Gauge<Long>) metricsCardinality::get);

      @Override
      public void reportSentMetrics(int sent) {
        sentMetrics.inc(sent);
      }

      @Override
      public void reportMetricsDroppedByFilter(int dropped) {
        metricsDroppedByFilter.inc(dropped);
      }

      @Override
      public void reportMetricsDroppedByRateLimit(int dropped) {
        metricsDroppedByRateLimit.inc(dropped);
      }

      @Override
      public void reportMetricsDroppedByCardinalityLimit(int dropped) {
        metricsDroppedByCardinalityLimit.inc(dropped);
      }

      @Override
      public void reportMetricsCardinality(long cardinality) {
        metricsCardinality.set(cardinality);
      }
    };
  }

  @Override
  public OutputPluginStatistics newOutputPlugin(final String id) {
    final MetricId m = metric.tagged("component", "output-plugin", "plugin_id", id);

    return new OutputPluginStatistics() {
      private final Meter dropped = registry.meter(
          m.tagged("what", "dropped-metrics", "unit", "metric"));
      private Map<MetricId, Metric> gauges = Collections.emptyMap();

      @Override
      public Map<MetricId, Metric> getMetrics() {
        return this.gauges;
      }

      @Override
      public void reportDropped(int dropped) {
        this.dropped.mark(dropped);
      }

      @Override
      public void registerCacheStats(SemanticCacheStatistics s) {
        registry.register(m, s);
      }
    };
  }

  @Override
  public BatchingStatistics newBatching(final String id) {
    final MetricId m = metric.tagged("component", "batching-plugin", "plugin_id", id);

    return new BatchingStatistics() {
      private final Meter sentMetrics =
          registry.meter(m.tagged("what", "sent-metrics", "unit", "metric"));
      private final Meter sentBatches =
          registry.meter(m.tagged("what", "sent-batches", "unit", "batches"));

      // Total number of metrics that have been sent, including batch content
      private final Meter sentTotal =
          registry.meter(m.tagged("what", "sent-total", "unit", "count"));

      // Number of internal batches within the batching plugin
      private final Meter batchedCount =
          registry.meter(m.tagged("what", "batched-into-batches", "unit", "count"));

      // The size of internal batches that were written, from the batching plugin to an output
      private final Histogram writeBatchSize =
          registry.getOrAdd(m.tagged("what", "write-batch-size"), HISTOGRAM_BUILDER);

      // Total number of metrics that are currently enqueued, including batch content
      private final Counter totalEnqueued =
          registry.counter(m.tagged("what", "total-enqueued", "unit", "count"));

      // Total number of pending writes/flushes
      private final Counter pendingWrites =
          registry.counter(m.tagged("what", "pending-writes", "unit", "count"));

      // Write latency histogram, in ms
      private final Histogram writeLatency =
          registry.getOrAdd(m.tagged("what", "write-latency"), HISTOGRAM_BUILDER);

      private final Meter metricsDroppedByFilter =
          registry.meter(m.tagged("what", "metrics-dropped-by-filter", "unit", "metric"));

      @Override
      public void reportSentMetrics(final int sent) {
        sentMetrics.mark(sent);
        sentTotal.mark(sent);
      }

      @Override
      public void reportSentBatches(final int sent, int contentSize) {
        sentBatches.mark(sent);
        sentTotal.mark(contentSize);
      }

      @Override
      public void reportInternalBatchCreate(final int num) {
        batchedCount.mark(num);
      }

      @Override
      public void reportInternalBatchWrite(final int size) {
        writeBatchSize.update(size);
      }

      @Override
      public void reportQueueSizeInc(final int num) {
        totalEnqueued.inc(num);
      }

      @Override
      public void reportQueueSizeDec(final int num) {
        totalEnqueued.dec(num);
      }

      @Override
      public Runnable monitorWrite() {
        pendingWrites.inc();
        final long startTime = System.nanoTime();

        return () -> {
          writeLatency.update(
              TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime));
          pendingWrites.dec();
        };
      }

      @Override
      public void reportMetricsDroppedByFilter(final int dropped) {
        metricsDroppedByFilter.mark(dropped);
      }
    };
  }

  @Override
  public HighFrequencyDetectorStatistics newHighFrequency() {
    final MetricId m = metric.tagged("component", "high-freq");
    final Map<MetricId, Long> highFreqMetricsMap = new ConcurrentHashMap<>();

    return new HighFrequencyDetectorStatistics() {
      private final Counter dropped = registry.counter(
          m.tagged("what", "dropped-metrics", "unit", "metric"));

      @Override
      public void reportHighFrequencyMetricsDropped(int dropped) {
        this.dropped.inc(dropped);
      }

      @Override
      public void reportHighFrequencyMetrics(int marked, String... tags) {
        MetricId gaugeMetric = m.tagged("what", "high-freq-metrics").tagged(tags);

        registerGauge(gaugeMetric);

        highFreqMetricsMap.put(gaugeMetric, (long) marked);
      }

      private void registerGauge(MetricId gauge) {
        if (highFreqMetricsMap.containsKey(gauge)) {
          return;
        }
        try {
          registry.register(gauge, new Gauge<Long>() {
            @Override
            public Long getValue() {
              return highFreqMetricsMap.get(gauge);
            }
          });
        } catch (IllegalArgumentException e) {
          // Do nothing as Gauge already registered
        }
      }
    };
  }
}
