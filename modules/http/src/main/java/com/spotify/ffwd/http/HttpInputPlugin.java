/*-
 * -\-\-
 * FastForward HTTP Module
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

package com.spotify.ffwd.http;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.PrivateModule;
import com.google.inject.Scopes;
import com.spotify.ffwd.filter.Filter;
import com.spotify.ffwd.input.InputPlugin;
import com.spotify.ffwd.input.PluginSource;
import com.spotify.ffwd.protocol.Protocol;
import com.spotify.ffwd.protocol.ProtocolFactory;
import com.spotify.ffwd.protocol.ProtocolServer;
import com.spotify.ffwd.protocol.ProtocolType;
import com.spotify.ffwd.protocol.RetryPolicy;
import java.util.Optional;

public class HttpInputPlugin implements InputPlugin {

  private static final ProtocolType DEFAULT_PROTOCOL = ProtocolType.TCP;
  private static final int DEFAULT_PORT = 8080;

  private final Protocol protocol;
  private final Class<? extends ProtocolServer> protocolServer;
  private final RetryPolicy retry;

  @JsonCreator
  public HttpInputPlugin(
      @JsonProperty("protocol") ProtocolFactory protocol,
      @JsonProperty("retry") RetryPolicy retry, @JsonProperty("filter") Optional<Filter> filter
  ) {
    this.protocol = Optional
        .ofNullable(protocol)
        .orElseGet(ProtocolFactory.defaultFor())
        .protocol(DEFAULT_PROTOCOL, DEFAULT_PORT);
    this.protocolServer = parseProtocolServer();
    this.retry = Optional.ofNullable(retry).orElseGet(RetryPolicy.Exponential::new);
  }

  private Class<? extends ProtocolServer> parseProtocolServer() {
    if (protocol.getType() == ProtocolType.TCP) {
      return HttpProtocolServer.class;
    }

    throw new IllegalArgumentException("Protocol not supported: " + protocol.getType());
  }

  @Override
  public Module module(final Key<PluginSource> key, final String id) {
    return new PrivateModule() {
      @Override
      protected void configure() {
        bind(ProtocolServer.class).to(protocolServer).in(Scopes.SINGLETON);
        bind(Protocol.class).toInstance(protocol);
        bind(RetryPolicy.class).toInstance(retry);
        bind(HttpDecoder.class).in(Scopes.SINGLETON);

        bind(key).to(HttpPluginSource.class).in(Scopes.SINGLETON);
        expose(key);
      }
    };
  }
}
