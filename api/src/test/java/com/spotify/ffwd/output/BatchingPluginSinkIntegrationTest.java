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

package com.spotify.ffwd.output;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.anyCollection;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.name.Names;
import com.spotify.ffwd.model.v2.Metric;
import com.spotify.ffwd.model.v2.Value;
import com.spotify.ffwd.noop.NoopPluginSink;
import com.spotify.ffwd.statistics.BatchingStatistics;
import com.spotify.ffwd.statistics.HighFrequencyDetectorStatistics;
import com.spotify.ffwd.statistics.NoopCoreStatistics;
import com.spotify.ffwd.statistics.OutputPluginStatistics;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.slf4j.Logger;

@RunWith(MockitoJUnitRunner.class)
public class BatchingPluginSinkIntegrationTest {

  private static final int BATCH_SIZE = 1000;
  private final boolean dropHighFrequencyMetric = false;
  private final int minFrequencyMillisAllowed = 1000;
  private final long highFrequencyDataRecycleMS = 300_000;
  private final int minNumberOfTriggers = 5;

  @Mock
  private BatchablePluginSink childSink;

  @Mock
  private Metric metric;

  @Mock
  private Logger log;

  private BatchingStatistics batchingStatistics = new NoopCoreStatistics().newBatching("");

  @Captor
  private ArgumentCaptor<Collection<Metric>> metricsCaptor;

  private BatchingPluginSink sink;

  private final ExecutorService executor =
      Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());

  @Mock
  private ScheduledExecutorService scheduler;

  @Before
  public void setup() {
    // flush every second, limiting the batch sizes to 1000, with 5 max pending flushes.
    sink = createBatchingPluginSink();

    doReturn(CompletableFuture.completedFuture(null)).when(childSink).start();
    doReturn(CompletableFuture.completedFuture(null)).when(childSink).stop();

    metric = new Metric("KEY", Value.DoubleValue.create(42.0), System.currentTimeMillis(),
        Collections.singletonMap("tag1", "value1"), ImmutableMap.of());

    sink.sink = childSink;
    sink.log = log;
    sink.batchingStatistics = batchingStatistics;
  }

  public BatchingPluginSink createBatchingPluginSink() {
    final List<Module> modules = Lists.newArrayList();

    modules.add(new AbstractModule() {
      @Override
      protected void configure() {
        bind(BatchingPluginSink.class).toInstance(new BatchingPluginSink(0, BATCH_SIZE, 0));
        bind(ExecutorService.class).toInstance(executor);
        bind(BatchablePluginSink.class).annotatedWith(BatchingDelegate.class)
            .toInstance(new NoopPluginSink());
        bind(Boolean.class).annotatedWith(Names.named("dropHighFrequencyMetric"))
            .toInstance(dropHighFrequencyMetric);
        bind(Integer.class).annotatedWith(Names.named("minFrequencyMillisAllowed"))
            .toInstance(minFrequencyMillisAllowed);
        bind(Long.class)
            .annotatedWith(Names.named("highFrequencyDataRecycleMS"))
            .toInstance(highFrequencyDataRecycleMS);
        bind(Integer.class)
            .annotatedWith(Names.named("minNumberOfTriggers"))
            .toInstance(minNumberOfTriggers);
        bind(BatchingStatistics.class).toInstance(NoopCoreStatistics.noopBatchingStatistics);
        bind(OutputPluginStatistics.class)
            .toInstance(NoopCoreStatistics.get().newOutputPlugin("output"));
        bind(HighFrequencyDetectorStatistics.class)
            .toInstance(NoopCoreStatistics.get().newHighFrequency());
        bind(Logger.class).toInstance(log);
        bind(ScheduledExecutorService.class).toInstance(scheduler);
      }
    });

    final Injector injector = Guice.createInjector(modules);

    return injector.getInstance(BatchingPluginSink.class);
  }

  @After
  public void teardown() throws InterruptedException {
    executor.shutdownNow();
    executor.awaitTermination(10, TimeUnit.SECONDS);
  }

  /**
   * Tests that the component creates batches that are being individually sized, and sent to the
   * underlying sink.
   */
  @Test
  public void testSizeLimitedFlushing() throws InterruptedException, ExecutionException {
    final CompletableFuture<Void> sendFuture = new CompletableFuture<>();

    // when sending any metrics, invoke the send future.
    doReturn(sendFuture).when(childSink).sendMetrics(anyCollection());

    // starts the scheduling of the next flush.
    sink.start().get();

    final int batches = 100;
    final int metricCount = BATCH_SIZE * batches;

    // send the given number of metrics, over the given number of threads.
    sendMetrics(sink, 4, metricCount);

    // Metrics will have been divided into batches because none of the batches have been
    // successfully sent yet,
    // which is indicated by resolving `sendFuture'.
    synchronized (sink.pendingLock) {
      assertEquals(batches, sink.pending.size());
    }

    // a very late flush resolve.
    sendFuture.complete(null);

    // all pending batches should have been marked as sent.
    synchronized (sink.pendingLock) {
      assertEquals(0, sink.pending.size());
    }

    sink.stop().get();

    verify(childSink, atLeastOnce()).sendMetrics(metricsCaptor.capture());

    int sum = 0;

    for (final Collection<Metric> c : metricsCaptor.getAllValues()) {
      sum += c.size();
      // no single batch may be larger than the given batch size.
      assertTrue(c.size() <= BATCH_SIZE);
    }

    assertEquals(metricCount, sum);
  }

  private void sendMetrics(final PluginSink sink, final int threadCount, final int metricCount)
      throws InterruptedException {
    final ExecutorService threads = Executors.newFixedThreadPool(threadCount);

    final AtomicInteger count = new AtomicInteger();
    final CountDownLatch latch = new CountDownLatch(threadCount);

    // hammer time.
    for (int i = 0; i < threadCount; i++) {
      threads.submit((Callable<Void>) () -> {
        try {
          while (count.getAndIncrement() < metricCount) {
            sink.sendMetric(metric);
          }
        } finally {
          latch.countDown();
        }

        return null;
      });
    }

    latch.await();
    threads.shutdown();
    threads.awaitTermination(1, TimeUnit.SECONDS);
  }
}
