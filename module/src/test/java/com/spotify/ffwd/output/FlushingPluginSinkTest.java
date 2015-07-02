package com.spotify.ffwd.output;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import java.util.concurrent.ScheduledExecutorService;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.slf4j.Logger;

import com.spotify.ffwd.model.Event;
import com.spotify.ffwd.model.Metric;

import eu.toolchain.async.AsyncFramework;

@RunWith(MockitoJUnitRunner.class)
public class FlushingPluginSinkTest {
    private final long flushInterval = 1000;
    private final long batchSizeLimit = 2000;
    private final long maxPendingFlushes = 3000;

    @Mock
    private AsyncFramework async;

    @Mock
    private BatchedPluginSink childSink;

    @Mock
    private Metric metric;

    @Mock
    private Event event;

    @Mock
    private FlushingPluginSink.Batch batch;

    @Mock
    private Logger log;

    @Mock
    private ScheduledExecutorService scheduler;

    private FlushingPluginSink sink;

    @Before
    public void setup() {
        sink = spy(new FlushingPluginSink(flushInterval, batchSizeLimit, maxPendingFlushes));
        sink.sink = childSink;
        sink.async = async;
        sink.log = log;
        sink.scheduler = scheduler;
    }

    @Test
    public void testDefaultConstructor() {
        final FlushingPluginSink s = new FlushingPluginSink(1);

        assertEquals(1, s.flushInterval);
        assertEquals(FlushingPluginSink.DEFAULT_BATCH_SIZE_LIMIT, s.batchSizeLimit);
        assertEquals(FlushingPluginSink.DEFAULT_MAX_PENDING_FLUSHES, s.maxPendingFlushes);
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

        sink.sendEvent(event);

        verify(sink, never()).checkBatch(sink.nextBatch);
    }

    @Test
    public void testSendEvent() {
        assertEquals(0, sink.nextBatch.size());
        doNothing().when(sink).checkBatch(sink.nextBatch);

        sink.sendEvent(event);

        assertEquals(1, sink.nextBatch.size());
        verify(sink).checkBatch(sink.nextBatch);
    }

    @Test
    public void testSendEventDrop() {
        sink.nextBatch = null;

        doNothing().when(sink).checkBatch(sink.nextBatch);

        sink.sendEvent(event);

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
}