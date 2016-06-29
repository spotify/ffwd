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
import com.google.common.base.Optional;
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

public class KafkaOutputPlugin implements OutputPlugin {
    public static final int DEFAULT_BATCH_SIZE = 1000;
    public static final boolean DEFAULT_COMPRESSION = true;

    private final KafkaRouter router;
    private final KafkaPartitioner partitioner;
    private final Map<String, String> properties;
    private final Long flushInterval;
    private final Serializer serializer;
    private final int batchSize;
    private final boolean compression;

    @JsonCreator
    public KafkaOutputPlugin(
        @JsonProperty("producer") Map<String, String> properties,
        @JsonProperty("flushInterval") Long flushInterval,
        @JsonProperty("router") KafkaRouter router,
        @JsonProperty("partitioner") KafkaPartitioner partitioner,
        @JsonProperty("serializer") Serializer serializer,
        @JsonProperty("batchSize") Integer batchSize,
        @JsonProperty("compression") Boolean compression
    ) {
        this.router = Optional.fromNullable(router).or(KafkaRouter.Tag.supplier());
        this.partitioner = Optional.fromNullable(partitioner).or(KafkaPartitioner.Host.supplier());
        this.flushInterval = Optional.fromNullable(flushInterval).orNull();
        this.properties = Optional.fromNullable(properties).or(new HashMap<String, String>());
        this.serializer = Optional.fromNullable(serializer).orNull();
        this.batchSize = Optional.fromNullable(batchSize).or(DEFAULT_BATCH_SIZE);
        this.compression = Optional.fromNullable(compression).or(DEFAULT_COMPRESSION);
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

                if (serializer == null) {
                    // bind to default implementation, provided by core.
                    bind(Serializer.class).to(Key.get(Serializer.class, Names.named("default")));
                } else {
                    bind(Serializer.class).toInstance(serializer);
                }

                if (flushInterval != null) {
                    bind(BatchedPluginSink.class).toInstance(new KafkaPluginSink(batchSize));
                    bind(key).toInstance(new FlushingPluginSink(flushInterval));
                } else {
                    bind(key).toInstance(new KafkaPluginSink(batchSize));
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
