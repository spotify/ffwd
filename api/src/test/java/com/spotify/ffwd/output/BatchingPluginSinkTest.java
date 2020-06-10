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
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.name.Names;
import com.spotify.ffwd.model.Metric;
import com.spotify.ffwd.noop.NoopPluginSink;
import com.spotify.ffwd.statistics.BatchingStatistics;
import com.spotify.ffwd.statistics.HighFrequencyDetectorStatistics;
import com.spotify.ffwd.statistics.NoopCoreStatistics;
import com.spotify.ffwd.statistics.OutputPluginStatistics;
import eu.toolchain.async.AsyncFramework;
import eu.toolchain.async.AsyncFuture;
import eu.toolchain.async.TinyAsync;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.slf4j.Logger;

@RunWith(MockitoJUnitRunner.class)
public class BatchingPluginSinkTest {
    private final long flushInterval = 500;
    private final long batchSizeLimit = 100;
    private final long maxPendingFlushes = 1;
    private final boolean dropHighFrequencyMetric = true;
    private final int minFrequencyMillisAllowed = 1000;
    private final long highFrequencyDataRecycleMS = 300_000;
    private final int minNumberOfTriggers = 5;

    @Mock
    private Metric metric;

    @Mock
    private BatchingPluginSink.Batch batch;

    @Mock
    private Logger log;

    @Mock
    private ScheduledExecutorService scheduler;

    private BatchingPluginSink sink;

    @Captor
    private ArgumentCaptor<Collection<Metric>> metricsCaptor;

    private final ExecutorService executor = Executors.newFixedThreadPool(2);

    @Mock
    private AsyncFuture<Void> future;

    AsyncFramework asyncFramework = TinyAsync.builder().executor(executor).build();
    BatchablePluginSink batchablePluginSink;

    @Before
    public void setup() {
        batchablePluginSink = spy(new NoopPluginSink());
        sink = createBatchingPluginSink();
        when(future.onFinished(any())).thenReturn(null);
        metric = new Metric("KEY", 42.0, new Date(), ImmutableSet.of(), Map.of("tag", "value"), ImmutableMap.of(), null);
    }


    public BatchingPluginSink createBatchingPluginSink() {
        final List<Module> modules = Lists.newArrayList();

        modules.add(new AbstractModule() {
            @Override
            protected void configure() {
            bind(BatchingPluginSink.class).toInstance(spy(new BatchingPluginSink(flushInterval, batchSizeLimit, maxPendingFlushes)));
            bind(AsyncFramework.class).toInstance(asyncFramework);
            bind(BatchablePluginSink.class).annotatedWith(BatchingDelegate.class).toInstance(batchablePluginSink);
            bind(Boolean.class).annotatedWith(Names.named("dropHighFrequencyMetric")).toInstance(dropHighFrequencyMetric);
            bind(Integer.class).annotatedWith(Names.named("minFrequencyMillisAllowed")).toInstance(minFrequencyMillisAllowed);
            bind(Long.class).annotatedWith(Names.named("highFrequencyDataRecycleMS")).toInstance(highFrequencyDataRecycleMS);
            bind(Integer.class).annotatedWith(Names.named("minNumberOfTriggers")).toInstance(minNumberOfTriggers);
            bind(BatchingStatistics.class).toInstance(NoopCoreStatistics.noopBatchingStatistics);
            bind(HighFrequencyDetectorStatistics.class).toInstance(NoopCoreStatistics.get().newHighFrequency());
            bind(OutputPluginStatistics.class).toInstance(NoopCoreStatistics.get().newOutputPlugin("output"));
            bind(Logger.class).toInstance(log);
            bind(ScheduledExecutorService.class).toInstance(scheduler);
            }
        });

        final Injector injector = Guice.createInjector(modules);

        return injector.getInstance(BatchingPluginSink.class);
    }

    @Test
    public void testDefaultConstructor() {
        final BatchingPluginSink s =
            new BatchingPluginSink(1, Optional.empty(), Optional.empty());

        assertEquals(1, s.flushInterval);
        assertEquals(BatchingPluginSink.DEFAULT_BATCH_SIZE_LIMIT, s.batchSizeLimit);
        assertEquals(BatchingPluginSink.DEFAULT_MAX_PENDING_FLUSHES, s.maxPendingFlushes);
    }

    @Test
    public void testSendMetric() {
        assertEquals(0, sink.nextBatch.size());
        doNothing().when(sink).checkBatch(sink.nextBatch);

        sink.sendMetric(metric);

        assertEquals(1, sink.nextBatch.size());
        verify(sink).checkBatch(sink.nextBatch);
    }

    @Test
    public void testSendMetricDrop() {
        sink.nextBatch = null;

        doNothing().when(sink).checkBatch(sink.nextBatch);

        sink.sendMetric(metric);

        verify(sink, never()).checkBatch(sink.nextBatch);
    }

    @Test
    public void testCheckBatchFlushes() {
        doReturn((int) batchSizeLimit).when(batch).size();
        doNothing().when(sink).flushNowThenScheduleNext();

        sink.checkBatch(batch);

        verify(sink).flushNowThenScheduleNext();
    }

    @Test
    public void testCheckBatchDoesntFlush() {
        doReturn((int) batchSizeLimit - 1).when(batch).size();
        doNothing().when(sink).flushNowThenScheduleNext();

        sink.checkBatch(batch);

        verify(sink, never()).flushNowThenScheduleNext();
    }

    @Test
    public void testSendMetricHighFrequency() throws InterruptedException{
        //Sends the same metric with different data points
        // should drop all metrics after 5 detection events

        assertEquals(0, sink.nextBatch.size());

        sink.sendMetric(metric);

        for (int i = 0; i < 1000; i++) {
            Metric tMetric = new Metric("KEY", 42.0 + i, new Date(), ImmutableSet.of(), Map
              .of("tag1", "value1"), ImmutableMap.of(), null);
            sink.sendMetric(tMetric);
        }

        assertEquals(1, sink.nextBatch.size());
        verify(sink).checkBatch(sink.nextBatch);
        verify(sink.sink, times(10)).sendMetrics(metricsCaptor.capture());

        int sum = 0;

        for (final Collection<Metric> c : metricsCaptor.getAllValues()) {
            sum += c.size();
            // no single batch may be larger than the given batch size.
            assertTrue(c.size() <= 100);
        }

        // It starts dropping after detection happened 5 times
        assertEquals(400, sum);
    }

    @Test
    public void testSendMetricRandomHighFrequency() throws InterruptedException{
        //Sends the different metric with different data points
        // shouldn't drop any metrics

        assertEquals(0, sink.nextBatch.size());

        sink.sendMetric(metric);

        for (int i = 0; i < 1000; i++) {
            Metric tMetric = new Metric("KEY"+i, 42.0 + i, new Date(), ImmutableSet.of(), Map
              .of("tag1", "value1"), ImmutableMap.of(), null);
            sink.sendMetric(tMetric);
        }

        assertEquals(1, sink.nextBatch.size());
        verify(sink).checkBatch(sink.nextBatch);
        verify(sink.sink, times(10)).sendMetrics(metricsCaptor.capture());

        int sum = 0;

        for (final Collection<Metric> c : metricsCaptor.getAllValues()) {
            sum += c.size();
            // no single batch may be larger than the given batch size.
            assertTrue(c.size() <= 100);
        }

        assertEquals(1000, sum);
    }

}
