/*-
 * -\-\-
 * FastForward Pubsub Module
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

package com.spotify.ffwd.pubsub;

import com.google.api.core.ApiFuture;
import com.google.api.core.ApiFutureCallback;
import com.google.api.core.ApiFutures;
import com.google.api.gax.rpc.NotFoundException;
import com.google.cloud.pubsub.v1.Publisher;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.inject.Inject;
import com.google.protobuf.ByteString;
import com.google.pubsub.v1.ProjectTopicName;
import com.google.pubsub.v1.PubsubMessage;
import com.spotify.ffwd.model.Batch;
import com.spotify.ffwd.model.Event;
import com.spotify.ffwd.model.Metric;
import com.spotify.ffwd.output.BatchablePluginSink;
import com.spotify.ffwd.output.FakeBatchablePluginSinkBase;
import com.spotify.ffwd.serializer.Serializer;
import eu.toolchain.async.AsyncFramework;
import eu.toolchain.async.AsyncFuture;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;
import lombok.extern.slf4j.Slf4j;

/**
 * This output plugin sends metrics to Google pubsub.
 *
 * Notice most of the methods return `async.resolved()`. This is because there's no apparent way to
 * map from `ApiFuture` to `AsyncFuture` and the PluginSink that executes this one calls
 * `collectAndDiscard` on the futures anyway.
 */
@Slf4j
public class PubsubPluginSink extends FakeBatchablePluginSinkBase implements BatchablePluginSink {
  @Inject
  AsyncFramework async;

  @Inject
  Publisher publisher;

  @Inject
  Serializer serializer;

  @Inject
  TopicAdmin topicAdmin;

  @Inject
  ProjectTopicName topicName;

  private final Executor executorService = MoreExecutors.directExecutor();

  @Override
  public boolean isReady() {
    return true;
  }

  @Override
  public void init() { }

  @Override
  public AsyncFuture<Void> sendEvents(Collection<Event> events) {
    log.debug("Sending events is not supported!");
    return async.resolved();
  }

  private void publishPubSub(ByteString bytes) {
    final ApiFuture<String> publish = publisher.publish(
      PubsubMessage.newBuilder().setData(bytes).build());

    ApiFutures.addCallback(publish, new ApiFutureCallback<String>() {
      @Override
      public void onFailure(Throwable t) {
        log.error("Failed sending metrics {}", t.getMessage());
      }

      @Override
      public void onSuccess(String messageId) { }

    }, executorService);
  }

  @Override
  public AsyncFuture<Void> sendMetrics(Collection<Metric> metrics) {
    final UUID traceId = UUID.randomUUID();
    log.debug("{}: Start sending metrics", traceId);

    for (Metric metric : metrics) {
      try {
        final ApiFuture<String> publish = publisher.publish(PubsubMessage.newBuilder()
          .setData(ByteString.copyFrom(serializer.serialize(metric)))
          .build()
        );
        ApiFutures.addCallback(publish, new ApiFutureCallback<String>() {
          @Override
          public void onFailure(Throwable t) {
            log.error("Failed sending metrics {}", t.getMessage());
          }

          @Override
          public void onSuccess(String messageId) { }

        }, executorService);
      } catch (Exception e) {
        log.error("Failed to publish metric {}", e);
      }
    }
    log.debug("{}: Finished sending metrics", traceId);
    return async.resolved();
  }

  @Override
  public AsyncFuture<Void> sendBatches(Collection<Batch> batches) {
    final List<Metric> metrics = convertBatchesToMetrics(batches);
    return sendMetrics(metrics);
  }


  @Override
  public void sendEvent(final Event event) {
    sendEvents(Collections.singletonList(event));
  }

  @Override
  public void sendMetric(final Metric metric) {
    sendMetrics(Collections.singletonList(metric));
  }

  @Override
  public void sendBatch(final Batch batch) {
    sendBatches(Collections.singletonList(batch));
  }

  /**
   * If the service account permissions allow it, check to see if the topic exists. Topic
   * creation is handled outside of this plugin as to limit the privileges given to the producer.
   */
  @Override
  public AsyncFuture<Void> start() {
    log.info("Connecting to topic {}", topicName);
    try {
      topicAdmin.getClient().getTopic(topicName);
      log.info("Topic exists");
    } catch (IOException e) {
      log.error("Topic admin", e);
    } catch (NotFoundException e) {
      log.warn("Topic {} not found or permission issues", topicName);
    }
    return async.resolved();
  }

  @Override
  public AsyncFuture<Void> stop() {
    return async.call(() -> {
      publisher.shutdown();
      return null;
    });
  }
}
