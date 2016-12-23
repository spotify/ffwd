/**
 * Copyright 2013-2014 Spotify AB. All rights reserved.
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
 **/
package com.spotify.ffwd.kafka;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.name.Names;
import com.spotify.ffwd.output.BatchedPluginSink;
import com.spotify.ffwd.output.FlushingPluginSink;
import com.spotify.ffwd.output.OutputPlugin;
import com.spotify.ffwd.output.OutputPluginModule;
import com.spotify.ffwd.output.PluginSink;
import com.spotify.ffwd.serializer.Serializer;
import kafka.javaapi.producer.Producer;
import kafka.producer.ProducerConfig;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Optional;

public class KafkaOutputPlugin implements OutputPlugin {
    public static final int DEFAULT_BATCH_SIZE = 1000;
    public static final boolean DEFAULT_COMPRESSION = true;
    public static final int DEFAULT_ASYNC_THREADS = 2;

    private final KafkaRouter router;
    private final KafkaPartitioner partitioner;
    private final Map<String, String> properties;
    private final Optional<Long> flushInterval;
    private final Optional<Serializer> serializer;
    private final int batchSize;
    private final boolean compression;
    private final int asyncThreads;

    @JsonCreator
    public KafkaOutputPlugin(
        @JsonProperty("producer") Map<String, String> properties,
        @JsonProperty("flushInterval") Long flushInterval,
        @JsonProperty("router") KafkaRouter router,
        @JsonProperty("partitioner") KafkaPartitioner partitioner,
        @JsonProperty("serializer") Serializer serializer,
        @JsonProperty("batchSize") Integer batchSize,
        @JsonProperty("compression") Boolean compression,
        @JsonProperty("asyncThreads") Integer asyncThreads
    ) {
        this.router = Optional.ofNullable(router).orElseGet(KafkaRouter.Tag.supplier());
        this.partitioner = Optional.ofNullable(partitioner)
                                   .orElseGet(KafkaPartitioner.Host::new);
        this.flushInterval = Optional.ofNullable(flushInterval);
        this.properties = Optional.ofNullable(properties).orElseGet(HashMap::new);
        this.serializer = Optional.ofNullable(serializer);
        this.batchSize = Optional.ofNullable(batchSize).orElse(DEFAULT_BATCH_SIZE);
        this.compression = Optional.ofNullable(compression).orElse(DEFAULT_COMPRESSION);
        this.asyncThreads = Optional.ofNullable(asyncThreads).orElse(DEFAULT_ASYNC_THREADS);
    }

    @Override
    public Module module(final Key<PluginSink> key, final String id) {
        return new OutputPluginModule(id) {
            @Provides
            @Singleton
            public Producer<Integer, byte[]> producer() {
                final Properties props = new Properties();
                props.putAll(properties);
                props.put("partitioner.class", IntegerPartitioner.class.getCanonicalName());
                props.put("key.serializer.class", IntegerEncoder.class.getCanonicalName());

                // enable gzip.
                if (compression) {
                    props.put("compression.codec", "1");
                }

                final ProducerConfig config = new ProducerConfig(props);
                return new Producer<Integer, byte[]>(config);
            }

            @Override
            protected void configure() {
                bind(KafkaRouter.class).toInstance(router);
                bind(KafkaPartitioner.class).toInstance(partitioner);

                if (serializer.isPresent()) {
                    bind(Serializer.class).toInstance(serializer.get());
                } else {
                    // bind to default implementation, provided by core.
                    bind(Serializer.class).to(Key.get(Serializer.class, Names.named("default")));
                }

                if (flushInterval.isPresent()) {
                    bind(BatchedPluginSink.class).toInstance(new KafkaPluginSink(batchSize, asyncThreads));
                    bind(key).toInstance(new FlushingPluginSink(flushInterval.get()));
                } else {
                    bind(key).toInstance(new KafkaPluginSink(batchSize, asyncThreads));
                }

                expose(key);
            }
        };
    }

    @Override
    public String id(int index) {
        final String brokers = properties.get("metadata.broker.list");

        if (brokers != null) {
            return brokers;
        }

        return Integer.toString(index);
    }
}
