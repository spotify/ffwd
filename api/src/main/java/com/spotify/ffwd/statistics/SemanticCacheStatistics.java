/*-
 * -\-\-
 * FastForward API
 * --
 * Copyright (C) 2016 - 2019 Spotify AB
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

import com.codahale.metrics.Gauge;
import com.codahale.metrics.Metric;
import com.google.common.cache.Cache;
import com.spotify.metrics.core.MetricId;
import com.spotify.metrics.core.SemanticMetricSet;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Semantic guava cache statistics. All the metrics except size are actually counters but we
 * register them as gauges in order to cut down on the logic needed to track counters being
 * incremented. Semantic-metrics will add a metric-type tag of gauge that will be wrong. At some
 * point semantic-metrics should be changed to preserve the metric-type tag if it exists.
 */
public class SemanticCacheStatistics implements SemanticMetricSet {
  private final Cache cache;

  public SemanticCacheStatistics(final Cache cache) {
    this.cache = cache;
  }

  @Override
  public Map<MetricId, Metric> getMetrics() {
    final Map<MetricId, Metric> gauges = new HashMap<>();
    final MetricId m = MetricId.EMPTY.tagged("subcomponent", "cache");

    gauges.put(m.tagged("what", "hit-count"), (Gauge<Long>) () -> cache.stats().hitCount());
    gauges.put(m.tagged("what", "miss-count"), (Gauge<Long>) () -> cache.stats().missCount());
    gauges.put(
        m.tagged("what", "eviction-count"), (Gauge<Long>) () -> cache.stats().evictionCount());
    gauges.put(m.tagged("what", "size"), (Gauge<Long>) cache::size);

    return Collections.unmodifiableMap(gauges);
  }
}
