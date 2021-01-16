/*-
 * -\-\-
 * FastForward API
 * --
 * Copyright (C) 2016 - 2020 Spotify AB
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

import static org.mockito.Mockito.spy;

import com.codahale.metrics.Gauge;
import com.spotify.metrics.core.MetricId;
import com.spotify.metrics.core.SemanticMetricRegistry;
import java.util.List;
import java.util.SortedMap;
import java.util.stream.Collectors;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class SemanticCoreStatisticsTest {

  private SemanticMetricRegistry registry;
  private SemanticCoreStatistics statistics;

  @Before
  public void setup() {
    registry = spy(new SemanticMetricRegistry());
    statistics = new SemanticCoreStatistics(registry);
  }

  @Test
  public void testNewHighFrequency() {
    HighFrequencyDetectorStatistics stats = statistics.newHighFrequency();

    stats.reportHighFrequencyMetrics(1, "keys", "key1|key2|key3", "whats", "what,waht,what2");
    stats.reportHighFrequencyMetrics(2, "keys", "key1|key2|key3|key4", "whats", "waht,what2");
    stats.reportHighFrequencyMetrics(10, "keys", "key1|key2|key3", "whats", "what,what2");
    stats.reportHighFrequencyMetrics(70, "keys", "key1|key2|key3", "whats", "what,waht,what2");

    SortedMap<MetricId, Gauge> gaugesFinal = registry.getGauges();

    List<Long> longs = gaugesFinal.entrySet().stream().map(set -> (Long) set.getValue().getValue())
        .collect(Collectors.toList());
    assertEquals(List.of(2L, 70L, 10L), longs);
  }
}
