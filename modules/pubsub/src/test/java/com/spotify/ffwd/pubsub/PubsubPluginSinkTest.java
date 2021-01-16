/*-
 * -\-\-
 * FastForward Pubsub Module
 * --
 * Copyright (C) 2016 - 2019 Spotify AB
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

package com.spotify.ffwd.pubsub;

import static com.spotify.ffwd.pubsub.Utils.makeMetric;
import static org.mockito.Matchers.isA;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import com.google.api.core.ApiFutures;
import com.google.cloud.pubsub.v1.Publisher;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.pubsub.v1.PubsubMessage;
import com.spotify.ffwd.cache.NoopCache;
import com.spotify.ffwd.model.v2.Batch;
import com.spotify.ffwd.model.v2.Metric;
import com.spotify.ffwd.serializer.Spotify100ProtoSerializer;
import eu.toolchain.async.AsyncFramework;
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

  private Metric metric;

  @Before
  public void setUp() {
    sink = new PubsubPluginSink();
    sink.async = async;
    sink.publisher = publisher;
    sink.serializer = new Spotify100ProtoSerializer();
    sink.writeCache = new NoopCache();
    sink.logger = LoggerFactory.getLogger(PubsubPluginSinkTest.class);

    metric = makeMetric();

  }

  @Test
  public void testSendMetric() {
    when(publisher.publish(Mockito.any())).thenReturn(ApiFutures.immediateFuture("Sent"));
    sink.sendMetric(metric);
    verify(publisher).publish(isA(PubsubMessage.class));
  }


  @Test
  public void testSendMetricCache() {
    sink.writeCache = metric -> true;
    when(publisher.publish(Mockito.any())).thenReturn(ApiFutures.immediateFuture("Sent"));
    sink.sendMetric(metric);
    verifyZeroInteractions(publisher);
  }

  @Test
  public void testSendBatch() {
    final Batch batch = Batch.create(Optional.of(ImmutableMap.of("tag1", "foo")),
        Optional.of(ImmutableMap.of("resource", "foo")),
        ImmutableList.of(metric.toBatchPoint()));

    sink.sendBatch(batch);
    verify(publisher).publish(isA(PubsubMessage.class));
  }

}
