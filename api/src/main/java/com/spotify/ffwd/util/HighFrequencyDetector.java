/*-
 * -\-\-
 * FastForward API
 * --
 * Copyright (C) 2020 Spotify AB
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

package com.spotify.ffwd.util;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.spotify.ffwd.model.Metric;
import com.spotify.ffwd.statistics.HighFrequencyDetectorStatistics;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IntSummaryStatistics;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.slf4j.Logger;

/**
 * Class responsible for high frequency metrics detection.
 * All batched metrics will be examined.
 * If configured will drop metrics marked as high frequency.
 */
public class HighFrequencyDetector {

  public static final int BURST_THRESHOLD = 5;

  /** Allow to drop high frequency metrics. */
  @Inject
  @Named("dropHighFrequencyMetric")
  boolean dropHighFrequencyMetric;

  /** Minimum number of milliseconds allowed between data points. */
  @Inject
  @Named("minFrequencyMillisAllowed")
  int minFrequencyMillisAllowed;

  /** Minimum number of times high frequency detected before metrics are dropped. */
  @Inject
  @Named("minNumberOfTriggers")
  int minNumberOfTriggers;

  /** Milliseconds high frequency triggers are refreshed. */
  @Inject
  @Named("highFrequencyDataRecycleMS")
  long highFrequencyDataRecycleMS;

  @Inject
  Logger log;

  @Inject
  private HighFrequencyDetectorStatistics statistics;

  /** High frequency metrics counter. */
  final AtomicReference<Map<Integer, Integer>> highFrequencyMetrics =
      new AtomicReference<>(new HashMap<>());

  final AtomicLong highFrequencyTriggersTS;

  /* TODO
      1. unit tests
          a. sendBatch with many points
          b. send both with many points
          c. check swap of high freq hashmap
   */

  @Inject
  public HighFrequencyDetector() {
    this.highFrequencyTriggersTS = new AtomicLong(System.currentTimeMillis());
  }

  /**
   * Detects high frequency metrics by grouping and calculating
   * time delta between metric timestamps
   *
   * @param metrics
   * @return list of filtered metrics
   */
  public List<Metric> detect(final List<Metric> metrics) {
    List<Metric> newList = new ArrayList<>();

    // Groups metrics by metric hash code and finds times deltas of ordered data points.
    // {hashcode -> -1, hashcode1 -> 10}
    Map<Integer, Integer> groupedMetrics =
        metrics.stream()
            .sorted(Comparator.comparing(Metric::getTime))
            .collect(
                Collectors.groupingBy(
                    Metric::hashCode,
                    Collectors.collectingAndThen(
                        Collectors.toList(),
                        this::computeTimeDelta)));

    updateHighFrequencyMetricsStats(groupedMetrics);

    if (dropHighFrequencyMetric && highFrequencyMetrics.get().size() > 0) {
      metrics.stream()
          .filter(
              metric ->
                  highFrequencyMetrics.get().getOrDefault(metric.hashCode(), 0)
                      < minNumberOfTriggers)
          .forEach(newList::add);

      statistics.reportHighFrequencyMetricsDropped(metrics.size() - newList.size());
      return newList;
    }

    return metrics;
  }

  private int computeTimeDelta(List<Metric> list) {
    int size = list.size();
    IntSummaryStatistics stats = IntStream.range(1, size)
        .map(
            x ->
                (int)
                    (list.get(size - x).getTime().getTime()
                        - list.get(size - x - 1).getTime().getTime()))
        .filter(d -> (d >= 0 && d < minFrequencyMillisAllowed))
        .summaryStatistics();

    int result = -1;

    /**
     * In order to be marked as high frequency metric the number of points
     * should be above the BURST_THRESHOLD.
     * It ignores any small bursts of high frequency metrics.
     */
    if (stats.getCount() > BURST_THRESHOLD) {
      // uses minimal delta time from all consecutive data points
      result = stats.getMin();
      log.info("stats: " + stats);
    }
    return result;
  }

  /**
   * Updates internal map of high frequency metrics
   *
   * @param groupedMetrics - Grouped metrics
   */
  private void updateHighFrequencyMetricsStats(final Map<Integer, Integer> groupedMetrics) {
    groupedMetrics.entrySet().stream()
        .filter(x -> x.getValue() >= 0)
        .forEach(
            hashcode -> {
              highFrequencyMetrics
                  .get()
                  .compute(hashcode.getKey(), (key, val) -> (val == null) ? 1 : val + 1);
            });

    statistics.reportHighFrequencyMetrics(highFrequencyMetrics.get().size());
    swapHighFrequencyTriggersData();
  }

  /** Resets high frequency triggers data hashmap */
  private synchronized void swapHighFrequencyTriggersData() {
    if (System.currentTimeMillis() - highFrequencyTriggersTS.get() > highFrequencyDataRecycleMS) {
      highFrequencyMetrics.set(new HashMap<>());
      highFrequencyTriggersTS.set(System.currentTimeMillis());
    }
  }
}
