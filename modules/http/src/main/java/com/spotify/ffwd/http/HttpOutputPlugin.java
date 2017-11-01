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
package com.spotify.ffwd.http;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.google.inject.name.Names;
import com.netflix.loadbalancer.ILoadBalancer;
import com.netflix.loadbalancer.LoadBalancerBuilder;
import com.spotify.ffwd.filter.Filter;
import com.spotify.ffwd.output.BatchedPluginSink;
import com.spotify.ffwd.output.OutputPlugin;
import com.spotify.ffwd.output.OutputPluginModule;
import com.spotify.ffwd.output.PluginSink;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class HttpOutputPlugin extends OutputPlugin {
    public static final Long DEFAULT_FLUSH_INTERVAL = 500L;

    private final HttpDiscovery discovery;

    @JsonCreator
    public HttpOutputPlugin(
        @JsonProperty("id") String id, @JsonProperty("flushInterval") Optional<Long> flushInterval,
        @JsonProperty("discovery") HttpDiscovery discovery,
        @JsonProperty("filter") Optional<Filter> filter

    ) {
        super(filter,
            flushInterval.isPresent() ? flushInterval : Optional.of(DEFAULT_FLUSH_INTERVAL));
        this.discovery = Optional.ofNullable(discovery).orElseGet(HttpDiscovery::supplyDefault);
    }

    @Override
    public Module module(final Key<PluginSink> key, final String id) {
        return new OutputPluginModule(id) {
            @Provides
            @Singleton
            @Named("http")
            public ExecutorService threadPool() {
                return Executors.newCachedThreadPool(
                    new ThreadFactoryBuilder().setNameFormat("ffwd-okhttp-async-%d").build());
            }

            @Provides
            @Singleton
            public ILoadBalancer setupRibbonClient(
                HttpPing httpPing, @Named("searchDomain") final Optional<String> searchDomain
            ) {
                return discovery
                    .apply(LoadBalancerBuilder.newBuilder(), searchDomain)
                    .withPing(httpPing)
                    .buildDynamicServerListLoadBalancer();
            }

            @Override
            protected void configure() {
                bind(BatchedPluginSink.class).to(HttpPluginSink.class);
                Key<PluginSink> sinkKey = Key.get(PluginSink.class, Names.named("httpSink"));
                bind(sinkKey).to(HttpPluginSink.class);
                install(wrapPluginSink(sinkKey, key));
                expose(key);
            }
        };
    }
}
