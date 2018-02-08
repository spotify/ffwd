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
package com.spotify.ffwd.statistics;

import com.codahale.metrics.Counter;
import com.codahale.metrics.Histogram;
import com.codahale.metrics.Meter;
import com.spotify.metrics.core.MetricId;
import com.spotify.metrics.core.SemanticMetricRegistry;
import eu.toolchain.async.FutureFinished;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class SemanticCoreStatistics implements CoreStatistics {
    private final SemanticMetricRegistry registry;
    private final MetricId metric = MetricId.build();

    @Override
    public InputManagerStatistics newInputManager() {
        final MetricId m = metric.tagged("component", "input-manager");

        return new InputManagerStatistics() {
            private final Meter receivedMetrics =
                registry.meter(m.tagged("what", "received-metrics", "unit", "metric"));
            private final Meter receivedEvents =
                registry.meter(m.tagged("what", "received-events", "unit", "event"));
            private final Meter metricsDroppedByFilter =
                registry.meter(m.tagged("what", "metrics-dropped-by-filter", "unit", "metric"));
            private final Meter eventsDroppedByFilter =
                registry.meter(m.tagged("what", "events-dropped-by-filter", "unit", "event"));

            @Override
            public void reportReceivedMetrics(int received) {
                receivedMetrics.mark(received);
            }

            @Override
            public void reportReceivedEvents(int received) {
                receivedEvents.mark(received);
            }

            @Override
            public void reportEventsDroppedByFilter(int dropped) {
                eventsDroppedByFilter.mark(dropped);
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

        return new OutputManagerStatistics() {
            private final Meter sentMetrics =
                registry.meter(m.tagged("what", "sent-metrics", "unit", "metric"));
            private final Meter sentEvents =
                registry.meter(m.tagged("what", "sent-events", "unit", "event"));
            private final Meter metricsDroppedByFilter =
                registry.meter(m.tagged("what", "metrics-dropped-by-filter", "unit", "metric"));
            private final Meter eventsDroppedByFilter =
                registry.meter(m.tagged("what", "events-dropped-by-filter", "unit", "event"));

            @Override
            public void reportSentMetrics(int sent) {
                sentMetrics.mark(sent);
            }

            @Override
            public void reportSentEvents(int sent) {
                sentEvents.mark(sent);
            }

            @Override
            public void reportEventsDroppedByFilter(int dropped) {
                eventsDroppedByFilter.mark(dropped);
            }

            @Override
            public void reportMetricsDroppedByFilter(int dropped) {
                metricsDroppedByFilter.mark(dropped);
            }
        };
    }

    @Override
    public OutputPluginStatistics newOutputPlugin(final String id) {
        final MetricId m = metric.tagged("component", "output-plugin", "plugin_id", id);

        return new OutputPluginStatistics() {
            private final Meter dropped =
                registry.meter(m.tagged("what", "dropped-metrics", "unit", "metric"));

            @Override
            public void reportDropped(int dropped) {
                this.dropped.mark(dropped);
            }
        };
    }

    @Override
    public BatchingStatistics newBatching(final String id) {
        final MetricId m = metric.tagged("component", "batching-plugin", "plugin_id", id);

        return new BatchingStatistics() {
            private final Meter sentMetrics =
                registry.meter(m.tagged("what", "sent-metrics", "unit", "metric"));
            private final Meter sentEvents =
                registry.meter(m.tagged("what", "sent-events", "unit", "event"));
            private final Meter sentBatches =
                registry.meter(m.tagged("what", "sent-batches", "unit", "batches"));

            // Total number of metrics & events that has been sent, including batch content
            private final Meter sentTotal =
                registry.meter(m.tagged("what", "sent-total", "unit", "count"));

            // Number of internal batches within the batching plugin
            private final Meter batchedCount =
                registry.meter(m.tagged("what", "batched-into-batches", "unit", "count"));

            // The size of internal batches that were written, from the batching plugin to an output
            private final Histogram writeBatchSize =
                registry.histogram(m.tagged("what", "write-batch-size"));

            // Total number of metrics & events that is currently enqueued, including batch content
            private final Counter totalEnqueued =
                registry.counter(m.tagged("what", "total-enqueued", "unit", "count"));

            // Total number of pending writes/flushes
            private final Counter pendingWrites =
                registry.counter(m.tagged("what", "pending-writes", "unit", "count"));

            // Write latency histogram, in ms
            private final Histogram writeLatency =
                registry.histogram(m.tagged("what", "write-latency"));

            private final Meter metricsDroppedByFilter =
                registry.meter(m.tagged("what", "metrics-dropped-by-filter", "unit", "metric"));
            private final Meter eventsDroppedByFilter =
                registry.meter(m.tagged("what", "events-dropped-by-filter", "unit", "event"));

            @Override
            public void reportSentEvents(final int sent) {
                sentEvents.mark(sent);
                sentTotal.mark(sent);
            }

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
            public FutureFinished monitorWrite() {
                pendingWrites.inc();
                final long startTime = System.nanoTime();

                return () -> {
                    writeLatency.update(
                        TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime));
                    pendingWrites.dec();
                };
            }

            @Override
            public void reportEventsDroppedByFilter(final int dropped) {
                eventsDroppedByFilter.mark(dropped);
            }

            @Override
            public void reportMetricsDroppedByFilter(final int dropped) {
                metricsDroppedByFilter.mark(dropped);
            }
        };
    }
}
