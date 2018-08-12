/*-
 * -\-\-
 * FastForward Core
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

package com.spotify.ffwd.domain;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.google.common.io.CharStreams;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import lombok.Data;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(SearchDomainDiscovery.Static.class),
    @JsonSubTypes.Type(SearchDomainDiscovery.DynamicGcpMetadataMapping.class),
})
public interface SearchDomainDiscovery {
    /**
     * Discovers the search domain, which is used for domain resolution
     */
    Optional<String> discover();

    @Data
    class Empty implements SearchDomainDiscovery {

        @Override
        public Optional<String> discover() {
            return Optional.empty();
        }
    }

    @JsonTypeName("static")
    @Data
    class Static implements SearchDomainDiscovery {
        private final String domain;

        @Override
        public Optional<String> discover() {
            return Optional.of(domain);
        }
    }

    @JsonTypeName("dynamic-gcp-metadata-mapping")
    @Data
    class DynamicGcpMetadataMapping implements SearchDomainDiscovery {
        private final Map<String, String> mapping;

        @Override
        public Optional<String> discover() {
            final String content = doMetadataRequest();
            final String zone = parseZone(content);
            final String domain = mapping.get(zone);

            if (domain == null) {
                throw new RuntimeException("No domain mapping configured for zone: " + zone);
            }

            return Optional.of(domain);
        }

        /**
         * Parse zone out of metadata response. Example of content:
         * projects/12345678/zones/europe-west1-d
         */
        private String parseZone(final String content) {
            final String trimmedContent = content.trim();
            final String[] parts = trimmedContent.split("/");

            if (parts.length != 4) {
                throw new RuntimeException(
                    "Metadata request returned unexpected content: " + content);
            }

            return parts[3];
        }

        private String doMetadataRequest() {
            try {
                final URL requestUrl = new URL(
                    "http://metadata.google.internal/computeMetadata/v1" + "/instance/zone");
                final HttpURLConnection connection =
                    (HttpURLConnection) requestUrl.openConnection();
                connection.setRequestProperty("Metadata-Flavor", "Google");
                int responseCode = connection.getResponseCode();

                if (responseCode != 200) {
                    throw new RuntimeException(
                        "Metadata request returned unexpected code: " + responseCode);
                }

                return CharStreams.toString(
                    new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8));
            } catch (final Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    /**
     * Default implementation for http discovery when nothing else is configured.
     */
    static SearchDomainDiscovery supplyDefault() {
        return new SearchDomainDiscovery.Empty();
    }
}
