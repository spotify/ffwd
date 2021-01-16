/*-
 * -\-\-
 * FastForward Core
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

package com.spotify.ffwd.input;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.Lists;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.PrivateModule;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import com.google.inject.Singleton;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.name.Names;
import com.spotify.ffwd.filter.Filter;
import com.spotify.ffwd.filter.TrueFilter;
import com.spotify.ffwd.statistics.CoreStatistics;
import com.spotify.ffwd.statistics.InputManagerStatistics;
import io.netty.channel.ChannelInboundHandler;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

public class InputManagerModule {

  private static final List<InputPlugin> DEFAULT_PLUGINS = Lists.newArrayList();

  private final List<InputPlugin> plugins;
  private final Filter filter;

  @JsonCreator
  public InputManagerModule(
      @JsonProperty("plugins") List<InputPlugin> plugins, @JsonProperty("filter") Filter filter
  ) {
    this.plugins = Optional.ofNullable(plugins).orElse(DEFAULT_PLUGINS);
    this.filter = Optional.ofNullable(filter).orElseGet(TrueFilter::new);
  }

  public Module module() {
    return new PrivateModule() {
      @Provides
      @Singleton
      public InputManagerStatistics statistics(CoreStatistics statistics) {
        return statistics.newInputManager();
      }

      @Provides
      @Singleton
      public List<PluginSource> sources(final Set<PluginSource> sources) {
        return Lists.newArrayList(sources);
      }

      @Provides
      @Singleton
      public Filter filter() {
        return filter;
      }

      @Override
      protected void configure() {
        bind(ChannelInboundHandler.class).to(InputChannelInboundHandler.class);

        bind(InputManager.class).to(CoreInputManager.class).in(Scopes.SINGLETON);
        expose(InputManager.class);

        bindPlugins();
      }

      private void bindPlugins() {
        final Multibinder<PluginSource> sources =
            Multibinder.newSetBinder(binder(), PluginSource.class);

        int i = 0;
        for (final InputPlugin p : plugins) {
          final String id = String.valueOf(++i);
          final Key<PluginSource> k = Key.get(PluginSource.class, Names.named(id));
          install(p.module(k, String.valueOf(id)));
          sources.addBinding().to(k);
        }
      }
    };
  }

  public static Supplier<InputManagerModule> supplyDefault() {
    return () -> new InputManagerModule(null, null);
  }
}
