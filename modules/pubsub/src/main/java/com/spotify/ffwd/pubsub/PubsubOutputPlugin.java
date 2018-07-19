/*
 * Copyright 2013-2018 Spotify AB. All rights reserved.
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
package com.spotify.ffwd.pubsub;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.api.gax.batching.BatchingSettings;
import com.google.api.gax.core.CredentialsProvider;
import com.google.api.gax.core.ExecutorProvider;
import com.google.api.gax.core.FixedCredentialsProvider;
import com.google.api.gax.core.InstantiatingExecutorProvider;
import com.google.api.gax.core.NoCredentialsProvider;
import com.google.api.gax.grpc.GrpcTransportChannel;
import com.google.api.gax.rpc.FixedTransportChannelProvider;
import com.google.api.gax.rpc.TransportChannelProvider;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.cloud.pubsub.v1.Publisher;
import com.google.common.base.Preconditions;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import com.google.inject.Singleton;
import com.google.inject.name.Names;
import com.google.pubsub.v1.ProjectTopicName;
import com.spotify.ffwd.filter.Filter;
import com.spotify.ffwd.module.Batching;
import com.spotify.ffwd.output.OutputPlugin;
import com.spotify.ffwd.output.OutputPluginModule;
import com.spotify.ffwd.output.PluginSink;
import com.spotify.ffwd.serializer.Serializer;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Optional;
import org.threeten.bp.Duration;

public class PubsubOutputPlugin extends OutputPlugin {

  private static final long DEFAULT_BYTES_THRESHOLD = 5000L;
  private static final long DEFAULT_COUNT_THRESHOLD = 1000L;
  private static final long DEFAULT_DELAY_THRESHOLD = Duration.ofMillis(200).toMillis();

  private final Optional<Serializer> serializer;

  private final Optional<String> serviceAccount;
  private final Optional<String> project;
  private final Optional<String> topic;

  private final long requestBytesThreshold;
  private final long messageCountBatchSize;
  private final Duration publishDelayThreshold;

  @JsonCreator
  public PubsubOutputPlugin(
    @JsonProperty("filter") Optional<Filter> filter,
    @JsonProperty("flushInterval") Optional<Long> flushInterval,
    @JsonProperty("batching") Optional<Batching> batching,
    @JsonProperty("serializer") Serializer serializer,

    @JsonProperty("serviceAccount") String serviceAccount,
    @JsonProperty("project") String project,
    @JsonProperty("topic") String topic,

    @JsonProperty("requestBytesThreshold") Long requestBytesThreshold,
    @JsonProperty("messageCountBatchSize") Long messageCountBatchSize,
    @JsonProperty("publishDelayThresholdMs") Long publishDelayThresholdMs
  ) {
    super(filter, Batching.from(flushInterval, batching));
    this.serializer = Optional.ofNullable(serializer);

    this.serviceAccount = Optional.ofNullable(serviceAccount);
    this.project = Optional.ofNullable(project);
    this.topic = Optional.ofNullable(topic);

    this.requestBytesThreshold = Optional.ofNullable(
      requestBytesThreshold).orElse(DEFAULT_BYTES_THRESHOLD);
    this.messageCountBatchSize = Optional.ofNullable(
      messageCountBatchSize).orElse(DEFAULT_COUNT_THRESHOLD);
    this.publishDelayThreshold = Duration.ofMillis(
      Optional.ofNullable(publishDelayThresholdMs).orElse(DEFAULT_DELAY_THRESHOLD));
  }

  @Override
  public Module module(final Key<PluginSink> key, final String id) {
    return new OutputPluginModule(id) {

      @Provides
      @Singleton
      public ProjectTopicName topicName() {
        Preconditions.checkArgument(project.isPresent(), "Google project must be set in config");
        Preconditions.checkArgument(topic.isPresent(), "Pubsub topic must be set in config");
        return ProjectTopicName.of(project.get(), topic.get());
      }

      @Provides
      @Singleton
      public Publisher publisher() throws IOException {
        // Publish request based on request size, messages count & time since last publish
        BatchingSettings batchingSettings = BatchingSettings.newBuilder()
          .setElementCountThreshold(messageCountBatchSize)
          .setRequestByteThreshold(requestBytesThreshold)
          .setDelayThreshold(publishDelayThreshold)
          .build();

        ExecutorProvider executorProvider = InstantiatingExecutorProvider.newBuilder()
          .setExecutorThreadCount(1).build();


        final Publisher.Builder publisher = Publisher.newBuilder(topicName())
          .setBatchingSettings(batchingSettings)
          .setExecutorProvider(executorProvider);

        if (serviceAccount.isPresent()) {
          CredentialsProvider credentials =
            FixedCredentialsProvider.create(
              ServiceAccountCredentials.fromStream(new FileInputStream(serviceAccount.get())));
          publisher.setCredentialsProvider(credentials);
        }

        final String emulatorHost = System.getenv("PUBSUB_EMULATOR_HOST");
        if (emulatorHost != null) {
          ManagedChannel channel =
            ManagedChannelBuilder.forTarget(emulatorHost).usePlaintext().build();
          TransportChannelProvider channelProvider =
            FixedTransportChannelProvider.create(GrpcTransportChannel.create(channel));

          publisher.setChannelProvider(channelProvider);
          publisher.setCredentialsProvider(NoCredentialsProvider.create());
        }

        return publisher.build();
      }


      @Override
      protected void configure() {
        if (serializer.isPresent()) {
          bind(Serializer.class).toInstance(serializer.get());
        } else {
          bind(Serializer.class).to(Key.get(Serializer.class, Names.named("default")));
        }

        final Key<PubsubPluginSink> sinkKey =
          Key.get(PubsubPluginSink.class, Names.named("pubsubSink"));
        bind(sinkKey).to(PubsubPluginSink.class).in(Scopes.SINGLETON);
        install(wrapPluginSink(sinkKey, key));
        expose(key);
      }
    };
  }
}
