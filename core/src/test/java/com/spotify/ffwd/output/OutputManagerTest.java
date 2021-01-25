/*-
 * -\-\-
 * FastForward Core
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

package com.spotify.ffwd.output;

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.TypeLiteral;
import com.google.inject.name.Names;
import com.google.inject.util.Providers;
import com.spotify.ffwd.debug.DebugServer;
import com.spotify.ffwd.filter.Filter;
import com.spotify.ffwd.model.v2.Batch;
import com.spotify.ffwd.model.v2.Metric;
import com.spotify.ffwd.model.v2.Value;
import com.spotify.ffwd.statistics.OutputManagerStatistics;
import eu.toolchain.async.AsyncFramework;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.runners.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class OutputManagerTest {

  private final static String HOST = "thehost";
  private final static String KEY = "thekey";

  @Mock
  private PluginSink sink;

  @Mock
  private AsyncFramework async;

  private Map<String, String> tags = new HashMap<>();
  private Map<String, String> tagsToResource = new HashMap<>();
  private Map<String, String> resource = new HashMap<>();
  private Set<String> riemannTags = ImmutableSet.of();
  private Set<String> skipTagsForKeys = ImmutableSet.of();
  private Boolean automaticHostTag = true;
  private String host = HOST;
  private long ttl = 0L;
  private Integer rateLimit = null;
  private Long cardinalityLimit = null;
  private Long hyperLogLogPlusSwapPeriodMS = null;

  @Mock
  private DebugServer debugServer;

  @Mock
  private OutputManagerStatistics statistics;

  @Mock
  private Filter filter;

  private Metric m1;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
    tags.put("role", "abc");
    tagsToResource.put("foo", "bar");
    resource.put("gke_pod", "123");
    Mockito.doReturn(true).when(filter).matchesMetric(any());
    doNothing().when(sink).sendMetric(any(Metric.class));
    when(sink.isReady()).thenReturn(true);
    Map<String, String> testTags = new HashMap<>();
    testTags.put("tag1", "value1");
    m1 =
        new Metric(KEY, Value.DoubleValue.create(42.0), System.currentTimeMillis(), testTags,
            ImmutableMap.of());
  }

  public OutputManager createOutputManager() {
    final List<Module> modules = Lists.newArrayList();

    modules.add(new AbstractModule() {
      @Override
      protected void configure() {
        bind(new TypeLiteral<List<PluginSink>>() {
        }).toInstance(ImmutableList.of(sink));
        bind(AsyncFramework.class).toInstance(async);
        bind(new TypeLiteral<Map<String, String>>() {
        }).annotatedWith(Names.named("tags")).toInstance(tags);
        bind(new TypeLiteral<Map<String, String>>() {
        }).annotatedWith(Names.named("tagsToResource")).toInstance(tagsToResource);
        bind(new TypeLiteral<Map<String, String>>() {
        }).annotatedWith(Names.named("resource")).toInstance(resource);
        bind(new TypeLiteral<Set<String>>() {
        }).annotatedWith(Names.named("riemannTags")).toInstance(riemannTags);
        bind(new TypeLiteral<Set<String>>() {
        }).annotatedWith(Names.named("skipTagsForKeys")).toInstance(skipTagsForKeys);
        bind(Boolean.class)
            .annotatedWith(Names.named("automaticHostTag"))
            .toInstance(automaticHostTag);
        bind(String.class).annotatedWith(Names.named("host")).toInstance(host);
        bind(long.class).annotatedWith(Names.named("ttl")).toInstance(ttl);
        bind(Integer.class).annotatedWith(Names.named("rateLimit"))
            .toProvider(Providers.of(rateLimit));
        bind(DebugServer.class).toInstance(debugServer);
        bind(OutputManagerStatistics.class).toInstance(statistics);
        bind(Filter.class).toInstance(filter);
        bind(String.class).annotatedWith(Names.named("dynamicTagsFile")).toInstance("");
        bind(OutputManager.class).to(CoreOutputManager.class);
        bind(Long.class).annotatedWith(Names.named("cardinalityLimit"))
            .toProvider(Providers.of(cardinalityLimit));
        bind(Long.class).annotatedWith(Names.named("hyperLogLogPlusSwapPeriodMS"))
            .toProvider(Providers.of(
                hyperLogLogPlusSwapPeriodMS));
      }
    });

    final Injector injector = Guice.createInjector(modules);

    return injector.getInstance(OutputManager.class);
  }

  @Test
  public void testAutomaticHostEnabled() {
    automaticHostTag = true;
    Map<String, String> expectedTags = new HashMap<>();
    expectedTags.putAll(m1.getTags());
    expectedTags.putAll(tags);
    expectedTags.put("host", host);

    assertEquals(
        new Metric(m1.getKey(), m1.getValue(), m1.getTimestamp(), expectedTags,
            m1.getResource()), sendAndCaptureMetric(m1));
  }

  @Test
  public void testAutomaticHostDisabled() {
    automaticHostTag = false;
    Map<String, String> expectedTags = new HashMap<>();
    expectedTags.putAll(m1.getTags());
    expectedTags.putAll(tags);

    assertEquals(
        new Metric(m1.getKey(), m1.getValue(), m1.getTimestamp(), expectedTags,
            m1.getResource()), sendAndCaptureMetric(m1));
  }

  @Test
  public void testSkipTagsForKeys() {
    skipTagsForKeys = ImmutableSet.of(KEY);

    assertEquals(
        new Metric(m1.getKey(), m1.getValue(), m1.getTimestamp(), m1.getTags(),
            m1.getResource()), sendAndCaptureMetric(m1));
  }

  @Test
  public void testTagsToResource() {
    Map<String, String> m2Tags = new HashMap<>();
    m2Tags.put("role", "abc");
    m2Tags.put("foo", "fooval");
    Metric m2 = new Metric(m1.getKey(), m1.getValue(), m1.getTimestamp(), m2Tags,
        m1.getResource());

    assertEquals(
        new Metric(m1.getKey(), m1.getValue(), m1.getTimestamp(),
            ImmutableMap.of("host", "thehost", "role", "abc"),
            ImmutableMap.of("bar", "fooval", "gke_pod", "123")), sendAndCaptureMetric(m2));
  }

  @Test
  public void testTagsToResourceForBatches() {
    Map<String, String> m2Tags = new HashMap<>();
    m2Tags.put("role", "abc");
    m2Tags.put("foo", "fooval");

    Metric point =
        new Metric(m1.getKey(), m1.getValue(), m1.getTimestamp(), m2Tags, m1.getResource());
    Batch batch = new Batch(Maps.newHashMap(), Maps.newHashMap(), Collections.singletonList(point));

    Metric expected = new Metric(m1.getKey(), m1.getValue(), m1.getTimestamp(),
        ImmutableMap.of("role", "abc"), ImmutableMap.of("bar", "fooval"));

    assertEquals(expected, sendAndCaptureBatch(batch).getPoints().get(0));
  }

  @Test
  public void testAcceptedRateLimiting() {
    rateLimit = 1000;
    OutputManager outputManager = createOutputManager();
    ArgumentCaptor<Metric> captor = ArgumentCaptor.forClass(Metric.class);

    outputManager.sendMetric(m1);
    outputManager.sendMetric(m1);

    verify(sink, times(2)).sendMetric(captor.capture());
  }

  @Test
  public void testDroppingRateLimiting() {
    rateLimit = 5;
    OutputManager outputManager = createOutputManager();
    ArgumentCaptor<Metric> captor = ArgumentCaptor.forClass(Metric.class);

    // Send a burst of metrics, all should be accepted
    for (int i = 0; i < rateLimit; i++) {
      outputManager.sendMetric(m1);
    }
    // Next metric is expected to be dropped
    Metric mTest = new Metric(null, Value.DoubleValue.create(42.0), System.currentTimeMillis(),
        ImmutableMap.of(), ImmutableMap.of());
    outputManager.sendMetric(mTest);

    verify(sink, times(rateLimit)).sendMetric(captor.capture());

    // Next metric shouldn't be dropped
    Metric mKey =
        new Metric("ffwd-java", Value.DoubleValue.create(42.0), System.currentTimeMillis(),
            ImmutableMap.of(), ImmutableMap.of());
    outputManager.sendMetric(mKey);

    verify(sink, times(rateLimit + 1)).sendMetric(captor.capture());
  }

  @Test
  public void testBatchNullKeyNotDropping() {
    rateLimit = 5;
    Map<String, String> m2Tags = new HashMap<>();
    m2Tags.put("role", "abc");
    m2Tags.put("foo", "fooval");

    Metric point = new Metric(null, m1.getValue(), m1.getTimestamp(), m2Tags, m1.getResource());
    Batch batch = new Batch(Maps.newHashMap(), Maps.newHashMap(), Collections.singletonList(point));

    Metric expected = new Metric(null, m1.getValue(), m1.getTimestamp(),
        ImmutableMap.of("role", "abc"), ImmutableMap.of("bar", "fooval"));
    assertEquals(expected, sendAndCaptureBatch(batch).getPoints().get(0));
  }

  @Test
  public void testBatchCardinalityNotDropping() {
    cardinalityLimit = 5L;
    Map<String, String> m2Tags = new HashMap<>();
    m2Tags.put("role", "abc");
    m2Tags.put("foo", "fooval");

    Metric point = new Metric(null, m1.getValue(), m1.getTimestamp(), m2Tags, m1.getResource());
    Batch batch = new Batch(Maps.newHashMap(), Maps.newHashMap(), Collections.singletonList(point));

    Metric expected = new Metric(null, m1.getValue(), m1.getTimestamp(),
        ImmutableMap.of("role", "abc"), ImmutableMap.of("bar", "fooval"));
    assertEquals(expected, sendAndCaptureBatch(batch).getPoints().get(0));
  }

  @Test
  public void testMetricCardinalityDropping() {
    cardinalityLimit = 19L;
    int sendNum = 20;
    OutputManager outputManager = createOutputManager();
    ArgumentCaptor<Metric> captor = ArgumentCaptor.forClass(Metric.class);

    // Send a burst of metrics, all should be accepted
    for (int i = 0; i < sendNum; i++) {
      outputManager.sendMetric(new Metric("main-key" + i, Value.DoubleValue.create(42.0),
          System.currentTimeMillis(), Collections.singletonMap("key" + i, "value" + i),
          ImmutableMap.of()));
    }

    // sent 18 as cardinality limit is 19
    verify(sink, times(sendNum - 2)).sendMetric(captor.capture());

    // Next metric shouldn't be dropped as it uses special key
    Metric mKey =
        new Metric("ffwd-java", Value.DoubleValue.create(42.0), System.currentTimeMillis(),
            ImmutableMap.of(), ImmutableMap.of());
    outputManager.sendMetric(mKey);

    verify(sink, times(sendNum - 1)).sendMetric(captor.capture());
  }

  @Test
  public void testMetricCardinalityDroppingWithSwap() {
    cardinalityLimit = 20L;
    hyperLogLogPlusSwapPeriodMS = 2000L;
    int sendNum = 20;
    CoreOutputManager outputManager = (CoreOutputManager) createOutputManager();
    ArgumentCaptor<Metric> captor = ArgumentCaptor.forClass(Metric.class);

    // Send a burst of metrics, all should be accepted
    for (int i = 0; i < sendNum; i++) {
      outputManager.sendMetric(new Metric("main-key" + i,
          Value.DoubleValue.create(42.0), System.currentTimeMillis()
          , Collections.singletonMap("key" + i, "value" + i), ImmutableMap.of()));
    }

    // Next metric shouldn't be dropped as it uses special key
    Metric mKey = new Metric("ffwd-java", Value.DoubleValue.create(42.0),
        System.currentTimeMillis(), ImmutableMap.of(), ImmutableMap.of());
    outputManager.sendMetric(mKey);

    // This should give enough time to reset HLL++ in the next .sendMetric(..)
    try {
      Thread.sleep(2500);
    } catch (InterruptedException e) {
      System.out.println(e);
    }

    // This should allow most of the metrics to be sent even though the cardinality is high
    for (int i = 0; i < sendNum; i++) {
      outputManager.sendMetric(new Metric("main-key" + i, Value.DoubleValue.create(42.0),
          System.currentTimeMillis(), Collections.singletonMap("key" + i, "value" + i),
          ImmutableMap.of()));
    }

    verify(sink, times(39)).sendMetric(captor.capture());
  }


  @Test
  public void testBatchCardinalityDropping() {
    cardinalityLimit = 20L;
    int sendNum = 20;
    OutputManager outputManager = createOutputManager();
    ArgumentCaptor<Metric> captor = ArgumentCaptor.forClass(Metric.class);
    ArgumentCaptor<Batch> captorBatch = ArgumentCaptor.forClass(Batch.class);

    List<Metric> points = new ArrayList<>();

    // Send a burst of metrics, all should be accepted
    for (int i = 0; i < sendNum; i++) {
      double val = (double) m1.getValue().getValue();
      points.add(
          new Metric("main-key" + i, Value.DoubleValue.create(val + i), m1.getTimestamp(),
              Collections.singletonMap("key" + i, "value" + i), m1.getResource()));
    }

    final Batch batch = new Batch(Maps.newHashMap(), Maps.newHashMap(), points);

    outputManager.sendBatch(batch);

    verify(sink, times(0)).sendBatch(captorBatch.capture());

    // Next metric shouldn't be dropped as it uses special key
    Metric mKey = new Metric("ffwd-java", Value.DoubleValue.create(42.0),
        System.currentTimeMillis(), ImmutableMap.of(), ImmutableMap.of());
    outputManager.sendMetric(mKey);

    verify(sink, times(1)).sendMetric(captor.capture());
  }

  @Test
  public void testBatchCardinalityDroppingWithSwap() {
    cardinalityLimit = 20L;
    hyperLogLogPlusSwapPeriodMS = 2000L;
    int sendNum = 20;
    CoreOutputManager outputManager = (CoreOutputManager) createOutputManager();
    ArgumentCaptor<Metric> captor = ArgumentCaptor.forClass(Metric.class);
    ArgumentCaptor<Batch> captorBatch = ArgumentCaptor.forClass(Batch.class);

    List<Metric> points = new ArrayList<>();

    // Send a burst of metrics, all should be accepted
    for (int i = 0; i < sendNum; i++) {
      double val = (double) m1.getValue().getValue();
      points.add(
          new Metric("main-key" + i, Value.DoubleValue.create(val + i), m1.getTimestamp(),
              Collections.singletonMap("key" + i, "value" + i), m1.getResource()));
    }

    final Batch batch = new Batch(Maps.newHashMap(), Maps.newHashMap(), points);
    outputManager.sendBatch(batch);
    verify(sink, times(0)).sendBatch(captorBatch.capture());

    // Next metric shouldn't be dropped as it uses special key
    Metric mKey = new Metric("ffwd-java", Value.DoubleValue.create(42.0),
        System.currentTimeMillis(), ImmutableMap.of(), ImmutableMap.of());
    outputManager.sendMetric(mKey);

    // This should give enough time to reset HLL++ in the next .sendMetric(..)
    try {
      Thread.sleep(2500);
    } catch (InterruptedException e) {
      System.out.println(e);
    }

    // This should allow most of the metrics to be sent even though the cardinality is high
    for (int i = 0; i < sendNum; i++) {
      outputManager.sendMetric(
          new Metric("main-key" + i, Value.DoubleValue.create(42.0), System.currentTimeMillis(),
              Collections.singletonMap("key" + i, "value" + i), ImmutableMap.of()));
    }

    verify(sink, times(20)).sendMetric(captor.capture());
  }

  private Metric sendAndCaptureMetric(Metric metric) {
    final OutputManager outputManager = createOutputManager();
    ArgumentCaptor<Metric> captor = ArgumentCaptor.forClass(Metric.class);
    outputManager.sendMetric(metric);
    verify(sink, times(1)).sendMetric(captor.capture());
    return captor.getValue();
  }

  private Batch sendAndCaptureBatch(Batch batch) {
    final OutputManager outputManager = createOutputManager();
    ArgumentCaptor<Batch> captor = ArgumentCaptor.forClass(Batch.class);
    outputManager.sendBatch(batch);
    verify(sink, times(1)).sendBatch(captor.capture());
    return captor.getValue();
  }
}
