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
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.google.common.collect.ImmutableList;
import com.netflix.loadbalancer.LoadBalancerBuilder;
import com.netflix.loadbalancer.Server;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.Data;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(HttpDiscovery.Static.class), @JsonSubTypes.Type(HttpDiscovery.Srv.class),
})
public interface HttpDiscovery {
    HostAndPort DEFAULT_SERVER = new HostAndPort("localhost", 8080);

    LoadBalancerBuilder<Server> apply(
        LoadBalancerBuilder<Server> builder, Optional<String> searchDomain
    );

    @JsonTypeName("static")
    @Data
    class Static implements HttpDiscovery {
        private final List<HostAndPort> servers;

        @Override
        public LoadBalancerBuilder<Server> apply(
            final LoadBalancerBuilder<Server> builder, final Optional<String> searchDomain
        ) {
            final List<Server> servers = this.servers
                .stream()
                .map(hostAndPort -> hostAndPort.withOptionalSearchDomain(searchDomain))
                .map(hostAndPort -> new Server(hostAndPort.getHost(), hostAndPort.getPort()))
                .collect(Collectors.toList());
            return builder.withDynamicServerList(new StaticServerList(servers));
        }
    }

    @JsonTypeName("srv")
    @Data
    class Srv implements HttpDiscovery {
        private final String record;

        @Override
        public LoadBalancerBuilder<Server> apply(
            final LoadBalancerBuilder<Server> builder, final Optional<String> searchDomain
        ) {
            final String record = searchDomain.map(s -> this.record + "." + s).orElse(this.record);
            return builder.withDynamicServerList(new SrvServerList(record));
        }
    }

    /**
     * Default implementation for http discovery when nothing else is configured.
     */
    static HttpDiscovery supplyDefault() {
        return new HttpDiscovery.Static(ImmutableList.of(DEFAULT_SERVER));
    }

    @Data
    class HostAndPort {
        private final String host;
        private final int port;

        @JsonCreator
        public static HostAndPort create(String input) {
            final String[] parts = input.split(":");
            if (parts.length != 2) {
                throw new RuntimeException("Not a valid server (expected host:port): " + input);
            }

            final String host = parts[0];
            final int port = Integer.parseUnsignedInt(parts[1]);

            return new HostAndPort(host, port);
        }

        public HostAndPort withOptionalSearchDomain(final Optional<String> searchDomain) {
            return searchDomain.map(s -> new HostAndPort(host + "." + s, port)).orElse(this);
        }
    }
}
