/*
 * Copyright (C) 2016 - 2019 Spotify AB
 *
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
 */

package com.spotify.ffwd

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import com.spotify.ffwd.domain.SearchDomainDiscovery
import com.spotify.ffwd.input.InputManagerModule
import com.spotify.ffwd.output.OutputManagerModule
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.UnknownHostException
import java.nio.file.Path
import java.nio.file.Paths

data class AgentConfig(
    @JsonProperty("debug") var debug: Debug?,
    @JsonProperty("host") var host: String = buildDefaultHost(),
    @JsonProperty("tags") var tags: Map<String, String> = emptyMap(),
    @JsonProperty("tagsToResource") var tagsToResource: Map<String, String> = emptyMap(),
    @JsonProperty("riemannTags") var riemannTags: Set<String> = emptySet(),
    @JsonProperty("skipTagsForKeys") var skipTagsForKeys: Set<String> = emptySet(),
    @JsonProperty("automaticHostTag") var automaticHostTag: Boolean = true,
    var input: InputManagerModule =
        InputManagerModule.supplyDefault().get(),
    @JsonProperty("output") var output: OutputManagerModule =
        OutputManagerModule.supplyDefault().get(),
    @JsonProperty("searchDomain") var searchDomain: SearchDomainDiscovery =
        SearchDomainDiscovery.supplyDefault(),
    @JsonProperty("asyncThreads") var asyncThreads: Int = 4,
    @JsonProperty("schedulerThreads") var schedulerThreads: Int = 4,
    @JsonProperty("bossThreads") var bossThreads: Int = 2,
    @JsonProperty("workerThreads") var workerThreads: Int = 4,
    @JsonProperty("ttl") var ttl: Long = 0,
    // NB(hexedpackets): qlog is unused and can be removed once the config parser ignores unknown
    // properties.
    @JsonProperty("qlog") var qlog: String?
)

private fun buildDefaultHost(): String {
    try {
        return InetAddress.getLocalHost().hostName
    } catch (e: UnknownHostException) {
        throw RuntimeException("unable to get local host", e)
    }
}

data class Debug(
    val localAddress: InetSocketAddress
) {
    @JsonCreator
    constructor(@JsonProperty("host") host: String?, @JsonProperty port: Int?)
        : this(InetSocketAddress(host ?: "localhost", port ?: 19001))
}