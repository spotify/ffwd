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
package com.spotify.ffwd.output;

import java.util.List;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Optional;
import com.google.common.base.Supplier;
import com.google.common.collect.Lists;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.PrivateModule;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import com.google.inject.Singleton;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.name.Named;
import com.google.inject.name.Names;
import com.spotify.ffwd.AgentConfig;
import com.spotify.ffwd.statistics.CoreStatistics;
import com.spotify.ffwd.statistics.OutputManagerStatistics;

public class OutputManagerModule {
    private final List<OutputPlugin> DEFAULT_PLUGINS = Lists.newArrayList();

    private final List<OutputPlugin> plugins;

    @JsonCreator
    public OutputManagerModule(@JsonProperty("plugins") List<OutputPlugin> plugins) {
        this.plugins = Optional.of(plugins).or(DEFAULT_PLUGINS);
    }

    public Module module() {
        return new PrivateModule() {
            @Provides
            @Singleton
            public OutputManagerStatistics statistics(CoreStatistics statistics) {
                return statistics.newOutputManager();
            }

            @Provides
            @Singleton
            public List<PluginSink> sources(final Set<PluginSink> sinks) {
                return Lists.newArrayList(sinks);
            }

            @Provides
            @Singleton
            @Named("attributes")
            public Map<String, String> attributes(AgentConfig config) {
                return config.getAttributes();
            }

            @Provides
            @Singleton
            @Named("tags")
            public Set<String> tags(AgentConfig config) {
                return config.getTags();
            }

            @Provides
            @Singleton
            @Named("host")
            public String host(AgentConfig config) {
                return config.getHost();
            }

            @Provides
            @Singleton
            @Named("ttl")
            public long ttl(AgentConfig config) {
                return config.getTtl();
            }

            @Override
            protected void configure() {
                bind(OutputManager.class).to(CoreOutputManager.class).in(Scopes.SINGLETON);
                expose(OutputManager.class);

                bindPlugins();
            }

            private void bindPlugins() {
                final Multibinder<PluginSink> sinks = Multibinder.newSetBinder(binder(), PluginSink.class);

                int i = 0;

                for (final OutputPlugin p : plugins) {
                    final String id = p.id(i++);
                    final Key<PluginSink> k = Key.get(PluginSink.class, Names.named(id));
                    install(p.module(k, id));
                    sinks.addBinding().to(k);
                }
            }
        };
    }

    public static Supplier<OutputManagerModule> supplyDefault() {
        return new Supplier<OutputManagerModule>() {
            @Override
            public OutputManagerModule get() {
                return new OutputManagerModule(null);
            }
        };
    }
}
