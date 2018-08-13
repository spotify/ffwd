/*-
 * -\-\-
 * FastForward Kafka Module
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

package com.spotify.ffwd.kafka;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.name.Names;
import com.spotify.ffwd.filter.Filter;
import com.spotify.ffwd.module.Batching;
import com.spotify.ffwd.output.OutputPlugin;
import com.spotify.ffwd.output.OutputPluginModule;
import com.spotify.ffwd.output.PluginSink;
import com.spotify.ffwd.serializer.Serializer;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.function.Supplier;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.IntegerSerializer;

public class KafkaOutputPlugin extends OutputPlugin {
    public static final int DEFAULT_BATCH_SIZE = 1000;
    public static final boolean DEFAULT_COMPRESSION = true;

    private final KafkaRouter router;
    private final KafkaPartitioner partitioner;
    private final Map<String, String> properties;
    private final Optional<Serializer> serializer;
    private final int batchSize;
    private final boolean compression;

    @JsonCreator
    public KafkaOutputPlugin(
        @JsonProperty("producer") Map<String, String> properties,
        @JsonProperty("flushInterval") Optional<Long> flushInterval,
        @JsonProperty("batching") Optional<Batching> batching,
        @JsonProperty("router") KafkaRouter router,
        @JsonProperty("partitioner") KafkaPartitioner partitioner,
        @JsonProperty("serializer") Serializer serializer,
        @JsonProperty("batchSize") Integer batchSize,
        @JsonProperty("compression") Boolean compression,
        @JsonProperty("filter") Optional<Filter> filter
    ) {
        super(filter, Batching.from(flushInterval, batching));
        this.router = Optional.ofNullable(router).orElseGet(KafkaRouter.Tag.supplier());
        this.partitioner = Optional.ofNullable(partitioner).orElseGet(KafkaPartitioner.Host::new);
        this.properties = Optional.ofNullable(properties).orElseGet(HashMap::new);
        this.serializer = Optional.ofNullable(serializer);
        this.batchSize = Optional.ofNullable(batchSize).orElse(DEFAULT_BATCH_SIZE);
        this.compression = Optional.ofNullable(compression).orElse(DEFAULT_COMPRESSION);
    }

    @Override
    public Module module(final Key<PluginSink> key, final String id) {
        return new OutputPluginModule(id) {
            @Provides
            @Singleton
            public Supplier<Producer<Integer, byte[]>> producer() {
                return () -> {
                    final Properties props = new Properties();
                    props.putAll(properties);
                    props.put("partitioner.class", IntegerPartitioner.class.getCanonicalName());
                    props.put("key.serializer", IntegerSerializer.class.getCanonicalName());
                    props.put("value.serializer", ByteArraySerializer.class.getCanonicalName());

                    // enable gzip.
                    if (compression) {
                        props.put("compression.type", "gzip");
                    }

                    return new KafkaProducer<>(props);
                };
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

                final Key<KafkaPluginSink> sinkKey =
                    Key.get(KafkaPluginSink.class, Names.named("kafkaSink"));
                bind(sinkKey).toInstance(new KafkaPluginSink(batchSize));
                install(wrapPluginSink(sinkKey, key));
                expose(key);
            }
        };
    }
}
