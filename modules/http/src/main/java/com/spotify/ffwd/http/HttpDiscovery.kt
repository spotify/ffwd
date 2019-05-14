/*-
 * -\-\-
 * FastForward HTTP Module
 * --
 * Copyright (C) 2019 Spotify AB
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

package com.spotify.ffwd.http

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonSubTypes.Type
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.annotation.JsonTypeName
import com.google.common.collect.ImmutableList
import com.netflix.loadbalancer.LoadBalancerBuilder
import com.netflix.loadbalancer.Server
import java.util.*

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes(Type(HttpDiscovery.Static::class), Type(HttpDiscovery.Srv::class))
interface HttpDiscovery {

    fun apply(
            builder: LoadBalancerBuilder<Server>, searchDomain: Optional<String>
    ): LoadBalancerBuilder<Server>


    @JsonTypeName("static")
    data class Static(val servers: List<HostAndPort>) : HttpDiscovery {

        override fun apply(
                builder: LoadBalancerBuilder<Server>, searchDomain: Optional<String>
        ): LoadBalancerBuilder<Server> {
            val servers = this.servers
                    .map { hostAndPort -> hostAndPort.withOptionalSearchDomain(searchDomain) }
                    .map { hostAndPort -> Server(hostAndPort.host, hostAndPort.port) }
                    .toList()
            return builder.withDynamicServerList(StaticServerList(servers))
        }
    }

    @JsonTypeName("srv")
    data class Srv(val record: String?) : HttpDiscovery {

        override fun apply(
                builder: LoadBalancerBuilder<Server>, searchDomain: Optional<String>
        ): LoadBalancerBuilder<Server> {
            val record = searchDomain.map { s -> this.record + "." + s }.orElse(this.record)
            return builder.withDynamicServerList(SrvServerList(record))
        }
    }

    data class HostAndPort(val host: String, val port: Int) {

        fun withOptionalSearchDomain(searchDomain: Optional<String>): HostAndPort {
            return searchDomain.map { s -> HostAndPort("$host.$s", port) }.orElse(this)
        }

        companion object {
            @JsonCreator
            fun create(input: String): HostAndPort {
                val parts = input.split(":".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                if (parts.size != 2) {
                    throw RuntimeException("Not a valid server (expected host:port): $input")
                }

                val host = parts[0]
                val port = Integer.parseUnsignedInt(parts[1])

                return HostAndPort(host, port)
            }
        }
    }

    companion object {
        val DEFAULT_SERVER = HostAndPort("localhost", 8080)

        /**
         * Default implementation for http discovery when nothing else is configured.
         */
        @JvmStatic
        fun supplyDefault(): HttpDiscovery {
            return HttpDiscovery.Static(ImmutableList.of(DEFAULT_SERVER))
        }
    }
}
