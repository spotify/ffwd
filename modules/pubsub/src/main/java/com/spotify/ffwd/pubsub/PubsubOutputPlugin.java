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

import static com.google.common.base.Preconditions.checkArgument;
import static java.util.Optional.ofNullable;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.api.gax.batching.BatchingSettings;
import com.google.api.gax.core.ExecutorProvider;
import com.google.api.gax.core.InstantiatingExecutorProvider;
import com.google.api.gax.core.NoCredentialsProvider;
import com.google.api.gax.grpc.GrpcTransportChannel;
import com.google.api.gax.rpc.FixedTransportChannelProvider;
import com.google.api.gax.rpc.TransportChannelProvider;
import com.google.cloud.pubsub.v1.Publisher;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import com.google.inject.Singleton;
import com.google.inject.name.Names;
import com.google.pubsub.v1.ProjectTopicName;
import com.spotify.ffwd.cache.ExpiringCache;
import com.spotify.ffwd.cache.NoopCache;
import com.spotify.ffwd.cache.WriteCache;
import com.spotify.ffwd.filter.Filter;
import com.spotify.ffwd.module.Batching;
import com.spotify.ffwd.output.OutputPlugin;
import com.spotify.ffwd.output.OutputPluginModule;
import com.spotify.ffwd.output.PluginSink;
import com.spotify.ffwd.serializer.Serializer;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import java.io.IOException;
import java.util.Optional;
import javax.annotation.Nullable;
import org.threeten.bp.Duration;

public class PubsubOutputPlugin extends OutputPlugin {

  private static final long DEFAULT_BYTES_THRESHOLD = 5000L;
  private static final long DEFAULT_COUNT_THRESHOLD = 1000L;
  private static final long DEFAULT_DELAY_THRESHOLD = Duration.ofMillis(200).toMillis();

  private static final long DEFAULT_CACHE_MINUTES = 0L;
  private static final long DEFAULT_CACHE_MAX_SIZE = 50_000L; // roughly 7.2 MB of memory

  private final Optional<Serializer> serializer;

  private final Optional<String> project;
  private final Optional<String> topic;

  private final long writeCacheDurationMinutes;
  private final long writeCacheMaxSize;

  private final long requestBytesThreshold;
  private final long messageCountBatchSize;
  private final Duration publishDelayThreshold;

  @JsonCreator
  public PubsubOutputPlugin(
    @JsonProperty("filter") Optional<Filter> filter,
    @JsonProperty("flushInterval") @Nullable Long flushInterval,
    @JsonProperty("batching") Optional<Batching> batching,
    @JsonProperty("serializer") Serializer serializer,

    @JsonProperty("project") String project,
    @JsonProperty("topic") String topic,

    @JsonProperty("writeCacheDurationMinutes") Long writeCacheDurationMinutes,
    @JsonProperty("writeCacheMaxSize") Long writeCacheMaxSize,

    @JsonProperty("requestBytesThreshold") Long requestBytesThreshold,
    @JsonProperty("messageCountBatchSize") Long messageCountBatchSize,
    @JsonProperty("publishDelayThresholdMs") Long publishDelayThresholdMs
  ) {
    super(filter, Batching.from(flushInterval, batching));
    this.serializer = ofNullable(serializer);

    this.project = ofNullable(project);
    this.topic = ofNullable(topic);

    this.writeCacheDurationMinutes =
        ofNullable(writeCacheDurationMinutes).orElse(DEFAULT_CACHE_MINUTES);
    this.writeCacheMaxSize = ofNullable(writeCacheMaxSize).orElse(DEFAULT_CACHE_MAX_SIZE);

    this.requestBytesThreshold = ofNullable(requestBytesThreshold).orElse(DEFAULT_BYTES_THRESHOLD);
    this.messageCountBatchSize = ofNullable(messageCountBatchSize).orElse(DEFAULT_COUNT_THRESHOLD);
    this.publishDelayThreshold = Duration.ofMillis(
      ofNullable(publishDelayThresholdMs).orElse(DEFAULT_DELAY_THRESHOLD));
  }

  @Override
  public Module module(final Key<PluginSink> key, final String id) {
    return new OutputPluginModule(id) {

      @Provides
      @Singleton
      public ProjectTopicName topicName() {
        checkArgument(project.isPresent(), "Google project must be set in config");
        checkArgument(topic.isPresent(), "Pubsub topic must be set in config");
        return ProjectTopicName.of(project.get(), topic.get());
      }

      @Provides
      @Singleton
      public Publisher publisher() throws IOException {
        // Publish request based on request size, messages count & time since last publish
        BatchingSettings batchingSettings =
            BatchingSettings.newBuilder()
                .setElementCountThreshold(messageCountBatchSize)
                .setRequestByteThreshold(requestBytesThreshold)
                .setDelayThreshold(publishDelayThreshold)
                .build();

        ExecutorProvider executorProvider =
            InstantiatingExecutorProvider.newBuilder().setExecutorThreadCount(1).build();

        final Publisher.Builder publisher =
            Publisher.newBuilder(topicName())
                .setBatchingSettings(batchingSettings)
                .setExecutorProvider(executorProvider);

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

      @Provides
      @Singleton
      public Optional<Cache<String, Boolean>> cache() {
        if (writeCacheDurationMinutes > 0) {
          return Optional.of(CacheBuilder.newBuilder()
              .expireAfterAccess(java.time.Duration.ofMinutes(writeCacheDurationMinutes))
              .maximumSize(writeCacheMaxSize)
              .recordStats()
              .build());
        }
        return Optional.empty();
      }

      @Provides
      @Singleton
      public WriteCache writeCache(Optional<Cache<String, Boolean>> cache) {
        return cache.<WriteCache>map(ExpiringCache::new).orElseGet(NoopCache::new);
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
