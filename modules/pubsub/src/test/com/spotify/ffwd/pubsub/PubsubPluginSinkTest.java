package com.spotify.ffwd.pubsub;

import static org.mockito.Matchers.isA;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import com.google.api.core.ApiFutures;
import com.google.cloud.pubsub.v1.Publisher;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.pubsub.v1.PubsubMessage;
import com.spotify.ffwd.model.Batch;
import com.spotify.ffwd.model.Event;
import com.spotify.ffwd.model.Metric;
import com.spotify.ffwd.cache.NoopCache;
import com.spotify.ffwd.cache.WriteCache;
import com.spotify.ffwd.serializer.ToStringSerializer;
import eu.toolchain.async.AsyncFramework;
import java.util.Date;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.runners.MockitoJUnitRunner;
import org.slf4j.LoggerFactory;

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
        sink.writeCache = new NoopCache();
        sink.logger = LoggerFactory.getLogger(PubsubPluginSinkTest.class);

    }

    private Event makeEvent() {
        return new Event("test_event", 1278, new Date(), 12L, "critical", "test_event", "test_host",
            ImmutableSet.of(), ImmutableMap.of("what", "stats", "pod", "gew1"));
    }

    @Test
    public void testSendEvent() {
        sink.sendEvent(makeEvent());
        verifyZeroInteractions(publisher);
    }

    @Test
    public void testSendMetric() {
        when(publisher.publish(Mockito.any())).thenReturn(ApiFutures.immediateFuture("Sent"));
        sink.sendMetric(makeMetric());
        verify(publisher).publish(isA(PubsubMessage.class));
    }


    @Test
    public void testSendMetricCache() {
        sink.writeCache = new WriteCache() {
            @Override
            public boolean isCached(Metric metric) {
                return true;
            }

            @Override
            public void remove(Metric metric) {

            }
        };
        when(publisher.publish(Mockito.any())).thenReturn(ApiFutures.immediateFuture("Sent"));
        sink.sendMetric(makeMetric());
        verifyZeroInteractions(publisher);
    }

    @Test
    public void testSendBatch() {
        final Batch batch = Batch.create(Optional.of(ImmutableMap.of("tag1", "foo")),
            Optional.of(ImmutableMap.of("resource", "foo")),
            ImmutableList.of(makeMetric().toBatchPoint()));

        sink.sendBatch(batch);
        verify(publisher).publish(isA(PubsubMessage.class));
    }

}
