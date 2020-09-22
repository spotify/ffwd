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

import static org.junit.Assert.assertEquals;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.name.Names;
import com.spotify.ffwd.model.Metric;
import com.spotify.ffwd.statistics.HighFrequencyDetectorStatistics;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RunWith(MockitoJUnitRunner.class)
public class HighFrequencyDetectorTest {

  private final boolean dropHighFrequencyMetric = true;
  private final int minFrequencyMillisAllowed = 1000;
  private final long highFrequencyDataRecycleMS = 300_000;
  private final int minNumberOfTriggers = 2;

  private HighFrequencyDetector detector;
  private HighFrequencyDetector shortSwapDetector;
  private Logger log;

  @Mock private HighFrequencyDetectorStatistics statistics;

  @Before
  public void setup() {
    log = LoggerFactory.getLogger(getClass());
    detector = createHighFreqDetector(highFrequencyDataRecycleMS);
    shortSwapDetector = createHighFreqDetector(500L);
  }

  public HighFrequencyDetector createHighFreqDetector(long swapMS) {
    final List<Module> modules = Lists.newArrayList();

    modules.add(
        new AbstractModule() {
          @Override
          protected void configure() {
            bind(Logger.class).toInstance(log);
            bind(Boolean.class)
                .annotatedWith(Names.named("dropHighFrequencyMetric"))
                .toInstance(dropHighFrequencyMetric);
            bind(Integer.class)
                .annotatedWith(Names.named("minFrequencyMillisAllowed"))
                .toInstance(minFrequencyMillisAllowed);
            bind(Long.class)
                .annotatedWith(Names.named("highFrequencyDataRecycleMS"))
                .toInstance(swapMS);
            bind(Integer.class)
                .annotatedWith(Names.named("minNumberOfTriggers"))
                .toInstance(minNumberOfTriggers);
            bind(HighFrequencyDetectorStatistics.class).toInstance(statistics);
          }
        });

    final Injector injector = Guice.createInjector(modules);

    return injector.getInstance(HighFrequencyDetector.class);
  }

  @Test
  public void testHighFrequencyDetection() {
    // verifies if high frequency metrics are captured and sortable
    List<Metric> finalList = new ArrayList<>();
    for (int y = 0; y < 3; y++) {
      for (int x = 0; x < 3; x++) {
        List<Metric> list = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
          list.add(
              new Metric(
                  "KEY" + y,
                  42.0 + i,
                  new Date(),
                  ImmutableSet.of(),
                  Map.of("tag1", "value1", "what", "fun"),
                  ImmutableMap.of(),
                  null));
        }

        finalList.addAll(detector.detect(list));
      }
    }
    assertEquals(90, finalList.size());
  }

  @Test
  public void testLargeNumberOfHighFreqMetrics() {
    // verifies if high frequency metrics are captured and sortable - up to 1k
    List<Metric> finalList = new ArrayList<>();
    for (int y = 0; y < 1000; y++) { // <-- number of high freq metrics kept in memory
      for (int x = 0; x < 3; x++) { // <-- making sure it will kick in the detection
        List<Metric> list = new ArrayList<>();
        for (int i = 0; i < 20; i++) { // <--- reasonable batches
          list.add(
              new Metric(
                  "KEY" + y,
                  42.0 + i,
                  new Date(),
                  ImmutableSet.of(),
                  Map.of("tag1", "value1", "what", "fun"),
                  ImmutableMap.of(),
                  null));
        }

        finalList.addAll(detector.detect(list));
      }
    }
    assertEquals(20000, finalList.size());
  }

  @Test
  public void testHighFreqStatsSwapping() {
    // sleeps between detect() method call so due to
    // highFrequencyDataRecycleMS set to 1 second for this test
    // it will reset high freq stats and allow more metrics to be sent
    List<Metric> finalList = new ArrayList<>();
    for (int y = 0; y < 5; y++) { // <-- number of high freq metrics kept in memory
      for (int x = 0; x < 3; x++) { // <-- making sure it will kick in the detection
        List<Metric> list = new ArrayList<>();
        for (int i = 0; i < 20; i++) { // <--- reasonable batches
          list.add(
              new Metric(
                  "KEY" + y,
                  42.0 + i,
                  new Date(),
                  ImmutableSet.of(),
                  Map.of("tag1", "value1", "what", "fun"),
                  ImmutableMap.of(),
                  null));
        }

        finalList.addAll(shortSwapDetector.detect(list));
        try {
          Thread.sleep(300);
        } catch (InterruptedException e) {
          // NOTHING TO SEE HERE! MOVE ALONG!
        }
      }
    }
    assertEquals(280, finalList.size());
  }

  @Test
  public void testNew() {
    long myTime = System.currentTimeMillis();
    // verifies if high frequency metrics are captured and sortable - up to 1k
    List<Metric> finalList = new ArrayList<>();
    for (int y = 0; y < 10; y++) { // <-- number of high freq metrics kept in memory
      List<Metric> list = new ArrayList<>();
      for (int x = 0; x < 5; x++) { // <-- making sure it will kick in the detection
        for (int i = 0; i < 20; i++) { // <--- reasonable batches
          list.add(
                  new Metric(
                          "KEY" + x,
                          42.0 + i,
                          new Date(myTime + i*x),
                          ImmutableSet.of(),
                          Map.of("tag1", "value1", "what", "fun"),
                          ImmutableMap.of(),
                          null));
        }
      }
      finalList.addAll(detector.detect(list));
    }
    assertEquals(20000, finalList.size());
  }
}
