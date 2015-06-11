// $LICENSE
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

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import kafka.javaapi.producer.Producer;
import kafka.producer.ProducerConfig;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Optional;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.PrivateModule;
import com.google.inject.Provides;
import com.google.inject.name.Names;
import com.spotify.ffwd.output.BatchedPluginSink;
import com.spotify.ffwd.output.FlushingPluginSink;
import com.spotify.ffwd.output.OutputPlugin;
import com.spotify.ffwd.output.PluginSink;
import com.spotify.ffwd.serializer.Serializer;

public class KafkaOutputPlugin implements OutputPlugin {
    private final KafkaRouter router;
    private final KafkaPartitioner partitioner;
    private final Map<String, String> properties;
    private final Long flushInterval;
    private final Serializer serializer;

    @JsonCreator
    public KafkaOutputPlugin(@JsonProperty("producer") Map<String, String> properties,
            @JsonProperty("flushInterval") Long flushInterval, @JsonProperty("router") KafkaRouter router,
            @JsonProperty("partitioner") KafkaPartitioner partitioner, @JsonProperty("serializer") Serializer serializer) {
        this.router = Optional.fromNullable(router).or(KafkaRouter.Attribute.supplier());
        this.partitioner = Optional.fromNullable(partitioner).or(KafkaPartitioner.Host.supplier());
        this.flushInterval = Optional.fromNullable(flushInterval).orNull();
        this.properties = Optional.fromNullable(properties).or(new HashMap<String, String>());
        this.serializer = Optional.fromNullable(serializer).orNull();
    }

    @Override
    public Module module(final Key<PluginSink> key) {
        return new PrivateModule() {
            @Provides
            public Producer<Integer, byte[]> producer() {
                final Properties props = new Properties();
                props.putAll(properties);
                props.put("partitioner.class", IntegerPartitioner.class.getCanonicalName());
                props.put("key.serializer.class", IntegerEncoder.class.getCanonicalName());
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
                    bind(BatchedPluginSink.class).to(KafkaPluginSink.class);
                    bind(key).toInstance(new FlushingPluginSink(flushInterval));
                } else {
                    bind(key).to(KafkaPluginSink.class);
                }

                expose(key);
            }
        };
    }
}