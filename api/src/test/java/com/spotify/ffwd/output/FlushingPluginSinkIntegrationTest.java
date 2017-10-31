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
package com.spotify.ffwd.output;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.anyCollection;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

import com.spotify.ffwd.model.Metric;
import eu.toolchain.async.AsyncFramework;
import eu.toolchain.async.ResolvableFuture;
import eu.toolchain.async.TinyAsync;
import java.util.Collection;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
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
public class FlushingPluginSinkIntegrationTest {
    private static final int BATCH_SIZE = 1000;

    @Mock
    private BatchedPluginSink childSink;

    @Mock
    private Metric metric;

    @Mock
    private Logger log;

    @Captor
    private ArgumentCaptor<Collection<Metric>> metricsCaptor;

    private FlushingPluginSink sink;
    private AsyncFramework async;

    private final ExecutorService executor =
        Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());

    @Before
    public void setup() {
        // flush every second, limiting the batch sizes to 1000, with 5 max pending flushes.
        sink = new FlushingPluginSink(0, BATCH_SIZE, 0);
        async = TinyAsync.builder().executor(executor).build();

        doReturn(async.resolved()).when(childSink).start();
        doReturn(async.resolved()).when(childSink).stop();

        sink.sink = childSink;
        sink.async = async;
        sink.log = log;
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
        final ResolvableFuture<Void> sendFuture = async.future();

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
        sendFuture.resolve(null);

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
            threads.submit(new Callable<Void>() {
                @Override
                public Void call() throws Exception {
                    try {
                        while (count.getAndIncrement() < metricCount) {
                            sink.sendMetric(metric);
                        }
                    } finally {
                        latch.countDown();
                    }

                    return null;
                }
            });
        }

        latch.await();
        threads.shutdown();
        threads.awaitTermination(1, TimeUnit.SECONDS);
    }
}
