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
package com.spotify.ffwd;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Optional;
import com.google.common.base.Supplier;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.spotify.ffwd.input.InputManagerModule;
import com.spotify.ffwd.output.OutputManagerModule;

import lombok.Data;

@Data
public class AgentConfig {
    public static final int DEFAULT_ASYNC_THREADS = 4;
    public static final int DEFAULT_SCHEDULER_THREADS = 4;
    public static final int DEFAULT_BOSS_THREADS = 2;
    public static final int DEFAULT_WORKER_THREADS = 4;

    public static final Map<String, String> DEFAULT_ATTRIBUTES = Maps.newHashMap();
    public static final Set<String> DEFAULT_TAGS = Sets.newHashSet();
    public static final String DEFAULT_QLOG = "./qlog/";

    private final Optional<Debug> debug;
    private final String host;
    private final Map<String, String> attributes;
    private final InputManagerModule input;
    private final OutputManagerModule output;
    private final int schedulerThreads;
    private final int asyncThreads;
    private final int bossThreads;
    private final int workerThreads;
    private final long ttl;
    private final Path qlog;

    @JsonCreator
    public AgentConfig(@JsonProperty("debug") Debug debug, @JsonProperty("host") String host,
            @JsonProperty("attributes") Map<String, String> attributes,
            @JsonProperty("input") InputManagerModule input, @JsonProperty("output") OutputManagerModule output,
            @JsonProperty("asyncThreads") Integer asyncThreads,
            @JsonProperty("schedulerThreads") Integer schedulerThreads,
            @JsonProperty("bossThreads") Integer bossThreads,
            @JsonProperty("workerThreads") Integer workerThreads, @JsonProperty("ttl") Long ttl,
            @JsonProperty("qlog") String qlog) {
        this.debug = Optional.fromNullable(debug);
        this.host = Optional.fromNullable(host).or(this.defaultHostSupplier());
        this.attributes = Optional.fromNullable(attributes).or(DEFAULT_ATTRIBUTES);
        this.input = Optional.fromNullable(input).or(InputManagerModule.supplyDefault());
        this.output = Optional.fromNullable(output).or(OutputManagerModule.supplyDefault());
        this.asyncThreads = Optional.fromNullable(asyncThreads).or(DEFAULT_ASYNC_THREADS);
        this.schedulerThreads = Optional.fromNullable(schedulerThreads).or(DEFAULT_SCHEDULER_THREADS);
        this.bossThreads = Optional.fromNullable(workerThreads).or(DEFAULT_BOSS_THREADS);
        this.workerThreads = Optional.fromNullable(workerThreads).or(DEFAULT_WORKER_THREADS);
        this.ttl = Optional.fromNullable(ttl).or(0l);
        this.qlog = Paths.get(Optional.fromNullable(qlog).or(DEFAULT_QLOG));
    }

    private Supplier<String> defaultHostSupplier() {
        return new Supplier<String>() {
            @Override
            public String get() {
                return buildDefaultHost();
            }
        };
    }

    private String buildDefaultHost() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            throw new RuntimeException("unable to get local host", e);
        }
    }

    @Data
    public static final class Debug {
        public static final String DEFAULT_HOST = "localhost";
        public static final int DEFAULT_PORT = 19001;

        private final InetSocketAddress localAddress;

        @JsonCreator
        public Debug(@JsonProperty("host") String host, @JsonProperty("port") Integer port) {
            this.localAddress = buildLocalAddress(Optional.fromNullable(host).or(DEFAULT_HOST),
                    Optional.fromNullable(port).or(DEFAULT_PORT));
        }

        private InetSocketAddress buildLocalAddress(String host, Integer port) {
            return new InetSocketAddress(host, port);
        }
    }
}
