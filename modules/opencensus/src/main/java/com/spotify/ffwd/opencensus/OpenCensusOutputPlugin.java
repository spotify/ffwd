/*-
 * -\-\-
 * FastForward OpenCensus Module
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

package com.spotify.ffwd.opencensus;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.PrivateModule;
import com.spotify.ffwd.filter.Filter;
import com.spotify.ffwd.module.Batching;
import com.spotify.ffwd.output.OutputPlugin;
import com.spotify.ffwd.output.PluginSink;
import com.spotify.ffwd.protocol.RetryPolicy;
import java.util.Optional;
import javax.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OpenCensusOutputPlugin extends OutputPlugin {

  private final RetryPolicy retry;
  private final Optional<String> gcpProject;
  private final Optional<Integer> maxViews;
  private final Optional<String> outputMetricNamePattern;

  @JsonCreator
  public OpenCensusOutputPlugin(
      @JsonProperty("retry") final RetryPolicy retry,
      @JsonProperty("filter") Optional<Filter> filter,
      @JsonProperty("flushInterval") @Nullable Long flushInterval,
      @JsonProperty("batching") Optional<Batching> batching,
      @JsonProperty("gcpProject") Optional<String> gcpProject,
      @JsonProperty("maxViews") Optional<Integer> maxViews,
      @JsonProperty("outputMetricNamePattern") Optional<String> outputMetricNamePattern
  ) {
    super(filter, Batching.from(flushInterval, batching));
    this.retry = Optional.ofNullable(retry).orElseGet(RetryPolicy.Exponential::new);
    this.gcpProject = gcpProject;
    this.maxViews = maxViews;
    this.outputMetricNamePattern = outputMetricNamePattern;
  }

  @Override
  public Module module(final Key<PluginSink> key, final String id) {
    return new PrivateModule() {
      @Override
      protected void configure() {
        bind(Logger.class).toInstance(LoggerFactory.getLogger(id));
        bind(key).toInstance(
            new OpenCensusPluginSink(gcpProject, maxViews, outputMetricNamePattern)
        );
        expose(key);
      }
    };
  }
}
