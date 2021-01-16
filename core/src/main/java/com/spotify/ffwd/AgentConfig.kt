/*-
 * -\-\-
 * FastForward Core
 * --
 * Copyright (C) 2016 - 2019 Spotify AB
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

package com.spotify.ffwd

import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module
import com.spotify.ffwd.domain.SearchDomainDiscovery
import com.spotify.ffwd.input.InputManagerModule
import com.spotify.ffwd.output.OutputManagerModule
import com.uchuhimo.konf.Config
import com.uchuhimo.konf.ConfigSpec
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.UnknownHostException
import java.nio.file.Path

// Helper class to make interop with java easier. The configuration loading is done through the
// static object.
class AgentConfig(val config: Config) {
    fun hasDebug(): Boolean = config.contains(Debug.host) or config.contains(Debug.port)

    val debugLocalAddress = config[Debug.localAddress]
    val host = config[AgentConfig.host]
    val tags = config[AgentConfig.tags]
    val tagsToResource = config[AgentConfig.tagsToResource]
    val riemannTags = config[AgentConfig.riemannTags]
    val skipTagsForKeys = config[AgentConfig.skipTagsForKeys]
    val automaticHostTag = config[AgentConfig.automaticHostTag]
    val input: InputManagerModule = config[AgentConfig.input]
    val output: OutputManagerModule = config[AgentConfig.output]
    val searchDomain = config[AgentConfig.searchDomain]
    val asyncThreads = config[AgentConfig.asyncThreads]
    val schedulerThreads = config[AgentConfig.schedulerThreads]
    val bossThreads = config[AgentConfig.bossThreads]
    val workerThreads = config[AgentConfig.workerThreads]
    val ttl = config[AgentConfig.ttl]

    companion object : ConfigSpec("") {
        object Debug : ConfigSpec() {
            val host by optional("localhost")
            val port by optional(19001)
            val localAddress by lazy { InetSocketAddress(it[host], it[port]) }
        }

        val host by lazy { buildDefaultHost() }
        val tags by optional(emptyMap<String, String>())
        val tagsToResource by optional(emptyMap<String, String>())
        val riemannTags by optional(emptySet<String>())
        val skipTagsForKeys by optional(emptySet<String>())
        val automaticHostTag by optional(true)
        val input by lazy { InputManagerModule.supplyDefault().get() }
        val output by lazy { OutputManagerModule.supplyDefault().get() }

        val searchDomain by lazy { SearchDomainDiscovery.supplyDefault() }
        val asyncThreads by optional(4)
        val schedulerThreads by optional(4)
        val bossThreads by optional(2)
        val workerThreads by optional(4)
        val ttl by optional(0)

        @JvmStatic
        fun load(path: Path, extraModule: SimpleModule): Config {
            val config = Config { addSpec(AgentConfig) }
            config.mapper
                    .registerModule(Jdk8Module())
                    .registerModule(extraModule)

            // Load yaml config files with no prefix, then set it to "ffwd" for other sources.
            return config
                    .from.yaml.file(path.toFile())
                    .withPrefix("ffwd")
                    .from.env()
                    .from.systemProperties()
        }
    }
}

private fun buildDefaultHost(): String {
    try {
        return InetAddress.getLocalHost().hostName
    } catch (e: UnknownHostException) {
        throw RuntimeException("unable to get local host", e)
    }
}
