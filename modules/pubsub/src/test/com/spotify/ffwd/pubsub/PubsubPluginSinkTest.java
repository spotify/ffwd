package com.spotify.ffwd.pubsub;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import com.google.cloud.pubsub.v1.Publisher;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.pubsub.v1.PubsubMessage;
import com.spotify.ffwd.model.Batch;
import com.spotify.ffwd.model.Event;
import com.spotify.ffwd.model.Metric;
import com.spotify.ffwd.serializer.ToStringSerializer;
import eu.toolchain.async.AsyncFramework;
import java.util.Date;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class PubsubPluginSinkTest {

    @Mock
    private AsyncFramework async;

    @Mock
    private Publisher publisher;

    private PubsubPluginSink sink;

    @Before
    public void setUp() throws Exception {
        sink = spy(new PubsubPluginSink());
        sink.async = async;
        sink.publisher = publisher;
        sink.serializer = new ToStringSerializer();
    }

    private Event makeEvent() {
        return new Event("test_event", 1278, new Date(), 12L, "critical", "test_event", "test_host",
            ImmutableSet.of(), ImmutableMap.of("what", "stats", "pod", "gew1"));
    }
    private Metric makeMetric() {
        return new Metric("key1", 1278, new Date(), ImmutableSet.of(),
                ImmutableMap.of("what", "test"), ImmutableMap.of(), null);
    }

    @Test
    public void testSendEvent() {
        sink.sendEvent(makeEvent());
        verify(publisher).publish(any(PubsubMessage.class));
    }

    @Test
    public void testSendMetric() {
        sink.sendMetric(makeMetric());
        verify(publisher).publish(any(PubsubMessage.class));
    }


    @Test
    public void testSendBatch() {
        final Batch batch = Batch.create(Optional.of(ImmutableMap.of("tag1", "foo")),
            Optional.of(ImmutableMap.of("resource", "foo")),
            ImmutableList.of(makeMetric().toBatchPoint()));

        sink.sendBatch(batch);
        verify(publisher).publish(any(PubsubMessage.class));
    }

}