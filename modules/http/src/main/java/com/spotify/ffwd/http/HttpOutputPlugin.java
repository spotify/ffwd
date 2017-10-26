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
import com.netflix.loadbalancer.ILoadBalancer;
import com.netflix.loadbalancer.LoadBalancerBuilder;
import com.spotify.ffwd.output.BatchedPluginSink;
import com.spotify.ffwd.output.FlushingPluginSink;
import com.spotify.ffwd.output.OutputPlugin;
import com.spotify.ffwd.output.OutputPluginModule;
import com.spotify.ffwd.output.PluginSink;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class HttpOutputPlugin implements OutputPlugin {
    public static final String DEFAULT_ID = "http";
    public static final Long DEFAULT_FLUSH_INTERVAL = 500L;

    private final String id;
    private final Long flushInterval;
    private final HttpDiscovery discovery;

    @JsonCreator
    public HttpOutputPlugin(
        @JsonProperty("id") String id, @JsonProperty("flushInterval") Long flushInterval,
        @JsonProperty("discovery") HttpDiscovery discovery
    ) {
        this.id = Optional.ofNullable(id).orElse(DEFAULT_ID);
        this.flushInterval = Optional.ofNullable(flushInterval).orElse(DEFAULT_FLUSH_INTERVAL);
        this.discovery = Optional.ofNullable(discovery).orElseGet(HttpDiscovery::supplyDefault);
    }

    @Override
    public Module module(final Key<PluginSink> key, final String id) {
        return new OutputPluginModule(id) {
            @Provides
            @Singleton
            @Named(DEFAULT_ID)
            public ExecutorService threadPool() {
                return Executors.newCachedThreadPool(
                    new ThreadFactoryBuilder().setNameFormat("ffwd-okhttp-async-%d").build());
            }

            @Provides
            @Singleton
            public ILoadBalancer setupRibbonClient(HttpPing httpPing) {
                return discovery
                    .apply(LoadBalancerBuilder.newBuilder())
                    .withPing(httpPing)
                    .buildDynamicServerListLoadBalancer();
            }

            @Override
            protected void configure() {
                bind(BatchedPluginSink.class).to(HttpPluginSink.class);
                bind(key).toInstance(new FlushingPluginSink(flushInterval));
                expose(key);
            }
        };
    }

    @Override
    public String id(int index) {
        return this.id;
    }
}
