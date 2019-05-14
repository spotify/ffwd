/*-
 * -\-\-
 * FastForward Core
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

package com.spotify.ffwd.domain

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonSubTypes.Type
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.annotation.JsonTypeName
import com.google.common.io.CharStreams
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.*

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes(
        Type(SearchDomainDiscovery.Static::class),
        Type(SearchDomainDiscovery.DynamicGcpMetadataMapping::class))
interface SearchDomainDiscovery {
    /**
     * Discovers the search domain, which is used for domain resolution
     */
    fun discover(): Optional<String>

    class Empty : SearchDomainDiscovery {

        override fun discover(): Optional<String> {
            return Optional.empty()
        }
    }

    @JsonTypeName("static")
    data class Static @java.beans.ConstructorProperties("domain")
    constructor(val domain: String) : SearchDomainDiscovery {

        override fun discover(): Optional<String> {
            return Optional.of(domain)
        }
    }

    @JsonTypeName("dynamic-gcp-metadata-mapping")
    data class DynamicGcpMetadataMapping @java.beans.ConstructorProperties("mapping")
    constructor(val mapping: Map<String, String>) : SearchDomainDiscovery {

        override fun discover(): Optional<String> {
            val content = doMetadataRequest()
            val zone = parseZone(content)
            val domain = mapping[zone]
                    ?: throw RuntimeException("No domain mapping configured for zone: $zone")

            return Optional.of(domain)
        }

        /**
         * Parse zone out of metadata response. Example of content:
         * projects/12345678/zones/europe-west1-d
         */
        private fun parseZone(content: String): String {
            val trimmedContent = content.trim { it <= ' ' }
            val parts = trimmedContent.split("/".toRegex()).dropLastWhile({ it.isEmpty() }).toTypedArray()

            if (parts.size != 4) {
                throw RuntimeException(
                        "Metadata request returned unexpected content: $content")
            }

            return parts[3]
        }

        private fun doMetadataRequest(): String {
            // TODO: Add a timeout to the metadata call.
            try {
                val requestUrl = URL(
                        "http://metadata.google.internal/computeMetadata/v1" + "/instance/zone")
                val connection = requestUrl.openConnection() as HttpURLConnection
                connection.setRequestProperty("Metadata-Flavor", "Google")
                val responseCode = connection.responseCode

                if (responseCode != 200) {
                    throw RuntimeException(
                            "Metadata request returned unexpected code: $responseCode")
                }

                return CharStreams.toString(
                        InputStreamReader(connection.inputStream, StandardCharsets.UTF_8))
            } catch (e: Exception) {
                throw RuntimeException(e)
            }

        }
    }

    companion object {
        /**
         * Default implementation for http discovery when nothing else is configured.
         */
        @JvmStatic
        fun supplyDefault(): SearchDomainDiscovery {
            return SearchDomainDiscovery.Empty()
        }
    }
}
