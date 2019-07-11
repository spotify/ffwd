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

package com.spotify.ffwd.statistics;

import com.codahale.metrics.Metric;
import com.spotify.metrics.core.MetricId;
import eu.toolchain.async.FutureFinished;
import java.util.Collections;
import java.util.Map;

public class NoopCoreStatistics implements CoreStatistics {
    private static final InputManagerStatistics noopInputManagerStatistics =
        new InputManagerStatistics() {
            @Override
            public void reportReceivedMetrics(int received) {
            }

            @Override
            public void reportReceivedEvents(int received) {
            }

            @Override
            public void reportEventsDroppedByFilter(int dropped) {
            }

            @Override
            public void reportMetricsDroppedByFilter(int dropped) {
            }
        };

    @Override
    public InputManagerStatistics newInputManager() {
        return noopInputManagerStatistics;
    }

    private static final OutputManagerStatistics noopOutputManagerStatistics =
        new OutputManagerStatistics() {
            @Override
            public void reportSentMetrics(int sent) {
            }

            @Override
            public void reportSentEvents(int sent) {
            }

            @Override
            public void reportEventsDroppedByFilter(int dropped) {
            }

            @Override
            public void reportEventsDroppedByRateLimit(int dropped) {
            }

            @Override
            public void reportMetricsDroppedByFilter(int dropped) {
            }

            @Override
            public void reportMetricsDroppedByRateLimit(final int dropped) {
            }
        };

    @Override
    public OutputManagerStatistics newOutputManager() {
        return noopOutputManagerStatistics;
    }

    private static final OutputPluginStatistics noopOutputPluginStatistics =
        new OutputPluginStatistics() {
          @Override
          public Map<MetricId, Metric> getMetrics() {
            return Collections.emptyMap();
          }

          @Override
          public void reportDropped(int dropped) { }

          @Override
          public void registerCacheStats(SemanticCacheStatistics stats) { }
        };

    @Override
    public OutputPluginStatistics newOutputPlugin(String id) {
        return noopOutputPluginStatistics;
    }

    public static final BatchingStatistics noopBatchingStatistics = new BatchingStatistics() {
        @Override
        public void reportSentEvents(final int sent) {
        }

        @Override
        public void reportSentMetrics(final int sent) {
        }

        @Override
        public void reportSentBatches(final int sent, final int contentSize) {
        }

        @Override
        public void reportInternalBatchCreate(final int num) {
        }

        @Override
        public void reportInternalBatchWrite(final int size) {
        }

        @Override
        public void reportQueueSizeInc(final int num) {
        }

        @Override
        public void reportQueueSizeDec(final int num) {
        }

        @Override
        public FutureFinished monitorWrite() {
            return () -> {
            };
        }

        @Override
        public void reportEventsDroppedByFilter(final int dropped) {
        }

        @Override
        public void reportMetricsDroppedByFilter(final int dropped) {
        }
    };

    @Override
    public BatchingStatistics newBatching(String id) {
        return noopBatchingStatistics;
    }

    private static final NoopCoreStatistics instance = new NoopCoreStatistics();

    public static NoopCoreStatistics get() {
        return instance;
    }
}
