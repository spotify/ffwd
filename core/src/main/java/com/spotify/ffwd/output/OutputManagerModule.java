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
package com.spotify.ffwd.output;

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
import com.google.inject.name.Named;
import com.google.inject.name.Names;
import com.spotify.ffwd.AgentConfig;
import com.spotify.ffwd.filter.Filter;
import com.spotify.ffwd.filter.TrueFilter;
import com.spotify.ffwd.statistics.CoreStatistics;
import com.spotify.ffwd.statistics.OutputManagerStatistics;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

public class OutputManagerModule {
    private static final List<OutputPlugin> DEFAULT_PLUGINS = Lists.newArrayList();
    /**
     * Prefix of environment variable that adds additional tags.
     */
    public static final String FFWD_TAG_PREFIX = "FFWD_TAG_";
    public static final String FFWD_RESOURCE_PREFIX = "FFWD_RESOURCE_";

    private final List<OutputPlugin> plugins;
    private final Filter filter;

    @JsonCreator
    public OutputManagerModule(
        @JsonProperty("plugins") List<OutputPlugin> plugins, @JsonProperty("filter") Filter filter
    ) {
        this.plugins = Optional.ofNullable(plugins).orElse(DEFAULT_PLUGINS);
        this.filter = Optional.ofNullable(filter).orElseGet(TrueFilter::new);
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
            @Named("tags")
            public Map<String, String> tags(final AgentConfig config) {
                final Map<String, String> systemEnvTags = systemEnvTags();

                if (systemEnvTags.isEmpty()) {
                    return config.getTags();
                }

                final Map<String, String> merged = new HashMap<>(config.getTags());
                merged.putAll(systemEnvTags);
                return merged;
            }

            @Provides
            @Singleton
            @Named("resource")
            public Map<String, String> resource() {
                return systemEnvResourceTags();
            }

            @Provides
            @Singleton
            @Named("riemannTags")
            public Set<String> riemannTags(AgentConfig config) {
                return config.getRiemannTags();
            }

            @Provides
            @Singleton
            @Named("skipTagsForKeys")
            public Set<String> skipTagsForKeys(AgentConfig config) {
                return config.getSkipTagsForKeys();
            }

            @Provides
            @Singleton
            @Named("automaticHostTag")
            public Boolean automaticHostTag(AgentConfig config) {
                return config.getAutomaticHostTag();
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

            @Provides
            @Singleton
            public Filter filter() {
                return filter;
            }

            @Override
            protected void configure() {
                bind(OutputManager.class).to(CoreOutputManager.class).in(Scopes.SINGLETON);
                expose(OutputManager.class);

                bindPlugins();
            }

            private void bindPlugins() {
                final Multibinder<PluginSink> sinks =
                    Multibinder.newSetBinder(binder(), PluginSink.class);

                int i = 0;

                for (final OutputPlugin p : plugins) {
                    final String id = String.valueOf(++i);
                    final Key<PluginSink> k = Key.get(PluginSink.class, Names.named(id));
                    install(p.module(k, id));
                    sinks.addBinding().to(k);
                }
            }
        };
    }

    /**
     * Extract tags from the system environment.
     */
    static Map<String, String> systemEnvTags() {
        return filterEnvironmentTags(System.getenv());
    }

    /**
     * Extract tags from a map that can correspond to a system environment.
     *
     * @return extracted tags.
     */
    static Map<String, String> filterEnvironmentTags(final Map<String, String> env) {
        final Map<String, String> tags = new HashMap<>();

        for (final Map.Entry<String, String> e : env.entrySet()) {
            if (e.getKey().startsWith(FFWD_TAG_PREFIX)) {
                final String tag = e.getKey().substring(FFWD_TAG_PREFIX.length());
                tags.put(tag.toLowerCase(), e.getValue());
            }
        }

        return tags;
    }

    /**
     * Extract tags from the system environment.
     */
    static Map<String, String> systemEnvResourceTags() {
        return filterEnvironmentResourceTags(System.getenv());
    }

    /**
     * Extract tags from a map that can correspond to a system environment.
     *
     * @return extracted tags.
     */
    static Map<String, String> filterEnvironmentResourceTags(final Map<String, String> env) {
        final Map<String, String> tags = new HashMap<>();

        for (final Map.Entry<String, String> e : env.entrySet()) {
            if (e.getKey().startsWith(FFWD_RESOURCE_PREFIX)) {
                final String tag = e.getKey().substring(FFWD_RESOURCE_PREFIX.length());
                tags.put(tag.toLowerCase(), e.getValue());
            }
        }

        return tags;
    }

    public static Supplier<OutputManagerModule> supplyDefault() {
        return () -> new OutputManagerModule(null, null);
    }
}
