/**
 * Copyright 2013-2017 Spotify AB. All rights reserved.
 * <p>
 * The contents of this file are licensed under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 **/
package com.spotify.ffwd.http;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.netflix.loadbalancer.LoadBalancerBuilder;
import com.netflix.loadbalancer.Server;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.Data;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(HttpDiscovery.Static.class), @JsonSubTypes.Type(HttpDiscovery.Srv.class),
})
public interface HttpDiscovery {
    HostAndPort DEFAULT_SERVER = new HostAndPort("localhost", 8080);

    LoadBalancerBuilder<Server> apply(
        LoadBalancerBuilder<Server> builder, String searchDomain
    );

    @JsonTypeName("static")
    @Data
    class Static implements HttpDiscovery {
        private final List<HostAndPort> servers;

        @Override
        public LoadBalancerBuilder<Server> apply(
            final LoadBalancerBuilder<Server> builder, final String searchDomain
        ) {
            final List<Server> out = new ArrayList<>();

            for (final HostAndPort hostAndPort : this.servers) {
                final HostAndPort modified = hostAndPort.withOptionalSearchDomain(searchDomain);
                out.add(new Server(modified.getHost(), modified.getPort()));
            }

            return builder.withDynamicServerList(new StaticServerList(out));
        }

        /**
         * Default implementation for http discovery when nothing else is configured.
         */
        static HttpDiscovery supplyDefault() {
            return new HttpDiscovery.Static(Collections.singletonList(DEFAULT_SERVER));
        }
    }

    @JsonTypeName("srv")
    @Data
    class Srv implements HttpDiscovery {
        private final String record;

        @Override
        public LoadBalancerBuilder<Server> apply(
            final LoadBalancerBuilder<Server> builder, final String searchDomain
        ) {
            final SrvServerList list;

            if (searchDomain != null) {
                list = new SrvServerList(this.record + "." + searchDomain);
            } else {
                list = new SrvServerList(this.record);
            }

            return builder.withDynamicServerList(list);
        }
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
            final int port = Integer.parseInt(parts[1]);

            return new HostAndPort(host, port);
        }

        public HostAndPort withOptionalSearchDomain(final String searchDomain) {
            if (host.equals("localhost") || host.endsWith(".")) {
                return this;
            }

            if (searchDomain == null) {
                return this;
            }

            return new HostAndPort(host + "." + searchDomain, port);
        }
    }
}
