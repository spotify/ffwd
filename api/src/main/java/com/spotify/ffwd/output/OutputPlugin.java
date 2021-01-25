/*-
 * -\-\-
 * FastForward API
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

package com.spotify.ffwd.output;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeInfo.Id;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.PrivateModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.google.inject.name.Names;
import com.spotify.ffwd.filter.Filter;
import com.spotify.ffwd.module.Batching;
import com.spotify.ffwd.statistics.BatchingStatistics;
import com.spotify.ffwd.statistics.CoreStatistics;
import com.spotify.ffwd.statistics.NoopCoreStatistics;
import java.util.Optional;
import java.util.logging.Logger;

@JsonTypeInfo(use = Id.NAME, property = "type")
public abstract class OutputPlugin {

  protected final Batching batching;
  protected final Optional<Filter> filter;

  public OutputPlugin(
      final Optional<Filter> filter, final Batching batching
  ) {
    this.filter = filter;
    this.batching = batching;
  }

  /**
   * This method allows to wrap plugin sink implementation type depending on configuration. If no
   * additional configuration is specified then subtype of com.spotify.ffwd.output
   * .BatchedPluginSink will be bound per plugin.
   * <p>
   * <code>output := new SubtypeOfBatchedPluginSink()</code>
   * <p>
   * If 'flushInterval' key is specified, then corresponding subtype of
   * com.spotify.ffwd.output.BatchedPluginSink will be wrapped into com.spotify.ffwd.output
   * .FlushingPluginSink:
   * <p>
   * <code>output := new FlushingPluginSink(flushInterval, delegator:=output)</code>
   * <p>
   * The resulting plugin sink type may be further wrapped into com.spotify.ffwd.output
   * .FilteringPluginSink type if 'filter' key is specified in plugin configuration:
   * <p>
   * <code>output := new FilteringPluginSink(filter, delegator:=output)</code>
   *
   * @param input   binding key with injection type of plugin sink
   * @param wrapKey binding key, containing injection type of wrapping plugin sink
   *
   * @return module that exposes output binding key
   */
  protected Module wrapPluginSink(
      final Key<? extends PluginSink> input, final Key<PluginSink> wrapKey
  ) {
    return new PrivateModule() {
      @Override
      protected void configure() {
        @SuppressWarnings("unchecked")
        Key<PluginSink> sinkKey = (Key<PluginSink>) input;

        if (batching.getFlushInterval() != null &&
            BatchablePluginSink.class.isAssignableFrom(
                input.getTypeLiteral().getRawType())) {
          final Key<PluginSink> flushingKey =
              Key.get(PluginSink.class, Names.named("flushing"));
          // IDEA doesn't like this cast, but it's correct, tho admittedly not pretty
          @SuppressWarnings({ "RedundantCast", "unchecked" })
          final Key<? extends BatchablePluginSink> batchedPluginSink =
              (Key<? extends BatchablePluginSink>) (Key<? extends PluginSink>) sinkKey;

          // Use annotation so that we can avoid name space clash
          bind(BatchablePluginSink.class)
              .annotatedWith(BatchingDelegate.class)
              .to(batchedPluginSink);
          bind(flushingKey).toInstance(
              new BatchingPluginSink(batching.getFlushInterval(),
                  batching.getBatchSizeLimit(), batching.getMaxPendingFlushes()));

          sinkKey = flushingKey;
        }

        if (filter.isPresent()) {
          final Key<PluginSink> filteringKey =
              Key.get(PluginSink.class, Names.named("filtered"));

          // Use annotation so that we can avoid name space clash
          bind(PluginSink.class).annotatedWith(FilteringDelegate.class).to(sinkKey);
          bind(filteringKey).toInstance(new FilteringPluginSink(filter.get()));

          sinkKey = filteringKey;
        }

        bind(wrapKey).to(sinkKey);
        expose(wrapKey);
      }

      @Provides
      @Singleton
      public BatchingStatistics batchingStatisticsProvider(
          CoreStatistics statistics, @Named("pluginId") String pluginId, Logger log
      ) {
        if (batching.isReportStatistics()) {
          log.info("Enabling reporting of batching-specific metrics for output " +
                   input.getTypeLiteral().getRawType().getName());
          return statistics.newBatching(pluginId);
        } else {
          return NoopCoreStatistics.noopBatchingStatistics;
        }
      }
    };
  }

  public abstract Module module(Key<PluginSink> key, String id);
}
