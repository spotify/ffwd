/*
 * -\-\-
 * FastForward OpenTelemetry Module
 * --
 * Copyright (C) 2021 Spotify AB.
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

package com.spotify.ffwd.opentelemetry;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.PrivateModule;
import com.spotify.ffwd.filter.Filter;
import com.spotify.ffwd.module.Batching;
import com.spotify.ffwd.output.OutputPlugin;
import com.spotify.ffwd.output.PluginSink;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import javax.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OpenTelemetryOutputPlugin extends OutputPlugin {

  private final Map<String, String> headers;
  private final String endpoint;
  private final String compression;

  @JsonCreator
  public OpenTelemetryOutputPlugin(
      @JsonProperty("filter") Optional<Filter> filter,
      @JsonProperty("flushInterval") @Nullable Long flushInterval,
      @JsonProperty("batching") Optional<Batching> batching,
      @JsonProperty("headers") Optional<Map<String, String>> headers,
      @JsonProperty("endpoint") @Nullable String endpoint,
      @JsonProperty("compression") @Nullable String compression
  ) {
    super(filter, Batching.from(flushInterval, batching));
    this.headers = headers.orElse(new HashMap<>());
    this.endpoint = Objects.requireNonNull(endpoint, "endpoint must be set");
    this.compression = compression;
  }

  @Override
  public Module module(Key<PluginSink> key, String id) {
    return new PrivateModule() {
      @Override
      protected void configure() {
        bind(Logger.class).toInstance(LoggerFactory.getLogger(id));
        bind(key).toInstance(
            new OpenTelemetryPluginSink(endpoint, headers, compression));
        expose(key);
      }
    };
  }
}
