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

package com.spotify.ffwd.protocol;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.net.InetSocketAddress;
import java.util.function.Supplier;
import lombok.Data;

/**
 * Data type suitable for building using a @JsonCreator block.
 *
 * @author udoprog
 */
@Data
public class ProtocolFactory {
    public static final String DEFAULT_HOST = "127.0.0.1";

    private final String type;
    private final String host;
    private final Integer port;
    private final Integer receiveBufferSize;

    @JsonCreator
    public ProtocolFactory(
        @JsonProperty("type") String type, @JsonProperty("host") String host,
        @JsonProperty("port") Integer port,
        @JsonProperty("receiveBufferSize") Integer receiveBufferSize
    ) {
        this.type = type;
        this.host = host;
        this.port = port;
        this.receiveBufferSize = receiveBufferSize;
    }

    /**
     * Build a default instance of {@link ProtocolFactory}.
     *
     * @return
     */
    public static Supplier<ProtocolFactory> defaultFor() {
        return () -> new ProtocolFactory(null, null, null, null);
    }

    /**
     * @see #protocol(ProtocolType, int, String)
     */
    public Protocol protocol(ProtocolType defaultType, int defaultPort) {
        return protocol(defaultType, defaultPort, DEFAULT_HOST);
    }

    /**
     * Build a new protocol instance with the given defaults if they are missing.
     *
     * @param defaultType Default type.
     * @param defaultPort Default port.
     * @param defaultHost Default host.
     * @return
     */
    public Protocol protocol(ProtocolType defaultType, int defaultPort, String defaultHost) {
        final ProtocolType t = parseProtocolType(type, defaultType);
        final InetSocketAddress address = parseSocketAddress(host, port, defaultPort, defaultHost);
        return new Protocol(t, address, receiveBufferSize);
    }

    private InetSocketAddress parseSocketAddress(
        String host, Integer port, int defaultPort, String defaultHost
    ) {
        if (host == null) {
            host = defaultHost;
        }

        if (port == null) {
            port = defaultPort;
        }

        return new InetSocketAddress(host, port);
    }

    private ProtocolType parseProtocolType(String type, ProtocolType defaultType) {
        if (type == null) {
            return defaultType;
        }

        type = type.toUpperCase();

        if (ProtocolType.TCP.name().equals(type)) {
            return ProtocolType.TCP;
        }

        if (ProtocolType.UDP.name().equals(type)) {
            return ProtocolType.UDP;
        }

        throw new IllegalArgumentException("Invalid protocol type: " + type);
    }
}
