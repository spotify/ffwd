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
package com.spotify.ffwd.carbon;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.PrivateModule;
import com.google.inject.Scopes;
import com.spotify.ffwd.input.InputPlugin;
import com.spotify.ffwd.input.PluginSource;
import com.spotify.ffwd.protocol.Protocol;
import com.spotify.ffwd.protocol.ProtocolFactory;
import com.spotify.ffwd.protocol.ProtocolServer;
import com.spotify.ffwd.protocol.ProtocolType;
import com.spotify.ffwd.protocol.RetryPolicy;

import java.util.Optional;

public class CarbonInputPlugin implements InputPlugin {
    private static final ProtocolType DEFAULT_PROTOCOL = ProtocolType.TCP;
    private static final int DEFAULT_PORT = 20003;

    private static final String FRAME = "frame";
    private static final String LINE = "line";

    private static final String DEFAULT_KEY = "carbon";

    private final Protocol protocol;
    private final Class<? extends ProtocolServer> protocolServer;
    private final RetryPolicy retry;
    private final String metricKey;

    @JsonCreator
    public CarbonInputPlugin(
        @JsonProperty("protocol") final ProtocolFactory protocol,
        @JsonProperty("delimiter") final String delimiter,
        @JsonProperty("retry") final RetryPolicy retry, @JsonProperty("key") final String key
    ) {
        this.protocol = Optional
            .ofNullable(protocol)
            .orElseGet(ProtocolFactory.defaultFor())
            .protocol(DEFAULT_PROTOCOL, DEFAULT_PORT);
        this.protocolServer =
            parseProtocolServer(Optional.ofNullable(delimiter).orElseGet(this::defaultDelimiter));
        this.retry = Optional.ofNullable(retry).orElseGet(RetryPolicy.Exponential::new);
        this.metricKey = Optional.ofNullable(key).orElse(DEFAULT_KEY);
    }

    private String defaultDelimiter() {
        if (protocol.getType() == ProtocolType.TCP) {
            return LINE;
        }

        if (protocol.getType() == ProtocolType.UDP) {
            throw new IllegalArgumentException("udp protocol is not supported yet");
        }

        return LINE;
    }

    private Class<? extends ProtocolServer> parseProtocolServer(final String delimiter) {
        if (FRAME.equals(delimiter)) {
            if (protocol.getType() == ProtocolType.TCP) {
                throw new IllegalArgumentException("frame-based decoding is not suitable for TCP");
            }

            throw new IllegalArgumentException("frame-based decoding is not supported yet");
        }

        if (LINE.equals(delimiter)) {
            return CarbonLineServer.class;
        }

        return defaultProtocolServer();
    }

    private Class<? extends ProtocolServer> defaultProtocolServer() {
        return CarbonLineServer.class;
    }

    @Override
    public Module module(final Key<PluginSource> key, final String id) {
        return new PrivateModule() {
            @Override
            protected void configure() {
                bind(CarbonDecoder.class).toInstance(new CarbonDecoder(metricKey));
                bind(Protocol.class).toInstance(protocol);
                bind(ProtocolServer.class).to(protocolServer).in(Scopes.SINGLETON);
                bind(RetryPolicy.class).toInstance(retry);

                bind(key).to(CarbonPluginSource.class).in(Scopes.SINGLETON);
                expose(key);
            }
        };
    }

    @Override
    public String id(int index) {
        return String.format("%s[%s]", getClass().getPackage().getName(), protocol.toString());
    }
}
