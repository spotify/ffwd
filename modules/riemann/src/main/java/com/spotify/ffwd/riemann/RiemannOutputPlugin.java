/*
 * Copyright 2013-2017 Spotify AB. All rights reserved.
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
package com.spotify.ffwd.riemann;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.Scopes;
import com.google.inject.name.Names;
import com.spotify.ffwd.filter.Filter;
import com.spotify.ffwd.filter.TrueFilter;
import com.spotify.ffwd.output.BatchedPluginSink;
import com.spotify.ffwd.output.FlushingPluginSink;
import com.spotify.ffwd.output.OutputPlugin;
import com.spotify.ffwd.output.OutputPluginModule;
import com.spotify.ffwd.output.PluginSink;
import com.spotify.ffwd.protocol.Protocol;
import com.spotify.ffwd.protocol.ProtocolClient;
import com.spotify.ffwd.protocol.ProtocolFactory;
import com.spotify.ffwd.protocol.ProtocolPluginSink;
import com.spotify.ffwd.protocol.ProtocolType;
import com.spotify.ffwd.protocol.RetryPolicy;

import java.util.Optional;

public class RiemannOutputPlugin implements OutputPlugin {
    private static final ProtocolType DEFAULT_PROTOCOL = ProtocolType.TCP;
    private static final int DEFAULT_PORT = 5555;
    private static final long DEFAULT_FLUSH_INTERVAL = 0;
    // TimeUnit.MILLISECONDS.convert(10, TimeUnit.SECONDS);

    private final Filter filter;
    private final Long flushInterval;
    private final Protocol protocol;
    private final Class<? extends ProtocolClient> protocolClient;
    private final RetryPolicy retry;

    @JsonCreator
    public RiemannOutputPlugin(
        @JsonProperty("filter") Filter filter, @JsonProperty("flushInterval") Long flushInterval,
        @JsonProperty("protocol") ProtocolFactory protocol,
        @JsonProperty("retry") RetryPolicy retry
    ) {
        this.filter = Optional.ofNullable(filter).orElseGet(TrueFilter::new);
        this.flushInterval = Optional.ofNullable(flushInterval).orElse(DEFAULT_FLUSH_INTERVAL);
        this.protocol = Optional
            .ofNullable(protocol)
            .orElseGet(ProtocolFactory.defaultFor())
            .protocol(DEFAULT_PROTOCOL, DEFAULT_PORT);
        this.protocolClient = parseProtocolClient();
        this.retry = Optional.ofNullable(retry).orElseGet(RetryPolicy.Exponential::new);
    }

    private Class<? extends ProtocolClient> parseProtocolClient() {
        if (protocol.getType() == ProtocolType.TCP) {
            return RiemannTCPProtocolClient.class;
        }

        throw new IllegalArgumentException("Protocol not supported: " + protocol.getType());
    }

    @Override
    public Module module(final Key<PluginSink> key, final String id) {
        return new OutputPluginModule(id) {
            @Override
            protected void configure() {
                bind(Protocol.class).toInstance(protocol);
                bind(RiemannMessageDecoder.class).in(Scopes.SINGLETON);
                bind(ProtocolClient.class).to(protocolClient).in(Scopes.SINGLETON);

                if (flushInterval != null && flushInterval > 0) {
                    bind(Key.get(Filter.class, Names.named("flushing"))).toInstance(filter);
                    bind(BatchedPluginSink.class).toInstance(new ProtocolPluginSink(retry));
                    bind(key).toInstance(new FlushingPluginSink(flushInterval));
                } else {
                    bind(Filter.class).toInstance(filter);
                    bind(key).toInstance(new ProtocolPluginSink(retry));
                }

                expose(key);
            }
        };
    }

    @Override
    public String id(int index) {
        return protocol.toString();
    }
}
