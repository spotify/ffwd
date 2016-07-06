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
package com.spotify.ffwd.template;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.PrivateModule;
import com.spotify.ffwd.output.OutputPlugin;
import com.spotify.ffwd.output.PluginSink;
import com.spotify.ffwd.protocol.Protocol;
import com.spotify.ffwd.protocol.ProtocolClient;
import com.spotify.ffwd.protocol.ProtocolFactory;
import com.spotify.ffwd.protocol.ProtocolPluginSink;
import com.spotify.ffwd.protocol.ProtocolType;
import com.spotify.ffwd.protocol.RetryPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

public class TemplateOutputPlugin implements OutputPlugin {
    private static final ProtocolType DEFAULT_PROTOCOL = ProtocolType.TCP;
    private static final int DEFAULT_PORT = 8910;

    private final Protocol protocol;
    private final RetryPolicy retry;

    @JsonCreator
    public TemplateOutputPlugin(
        @JsonProperty("protocol") final ProtocolFactory protocol,
        @JsonProperty("retry") final RetryPolicy retry
    ) {
        this.protocol = Optional
            .ofNullable(protocol)
            .orElseGet(ProtocolFactory.defaultFor())
            .protocol(DEFAULT_PROTOCOL, DEFAULT_PORT);
        this.retry = Optional.ofNullable(retry).orElseGet(() -> new RetryPolicy.Exponential());
    }

    @Override
    public Module module(final Key<PluginSink> key, final String id) {
        return new PrivateModule() {
            @Override
            protected void configure() {
                bind(Logger.class).toInstance(LoggerFactory.getLogger(id));
                bind(TemplateOutputEncoder.class).toInstance(new TemplateOutputEncoder());
                bind(Protocol.class).toInstance(protocol);
                bind(ProtocolClient.class).toInstance(new TemplateOutputProtocolClient());

                bind(key).toInstance(new ProtocolPluginSink(retry));
                expose(key);
            }
        };
    }

    @Override
    public String id(int index) {
        return protocol.toString();
    }
}
