/*
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
 */
package com.spotify.ffwd.statistics;

import com.codahale.metrics.Meter;
import com.spotify.metrics.core.MetricId;
import com.spotify.metrics.core.SemanticMetricRegistry;

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
}
