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

package com.spotify.ffwd.serializer;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.spotify.ffwd.cache.WriteCache;
import com.spotify.ffwd.model.v2.Metric;
import com.spotify.ffwd.model.v2.Value;
import java.util.Collection;
import java.util.Map;
import lombok.Data;

@JsonTypeName("spotify100")
public class Spotify100Serializer implements Serializer {
    public static final String SCHEMA_VERSION = "2.0.0";

    @Inject
    @Named("application/json")
    private ObjectMapper mapper;

    @Data
    public static class Spotify100Metric {
        private final String version = SCHEMA_VERSION;
        private final String key;
        private final Long time;
        private final Map<String, String> attributes;
        private final Map<String, String> resource;
        private final Value value;
    }

    @Data
    public static class Spotify100Event {
        private final String version = SCHEMA_VERSION;
        private final String key;
        private final String host;
        private final Long time;
        private final Map<String, String> attributes;
        private final Double value;
    }

    @JsonCreator
    public Spotify100Serializer() {
    }

    @Override
    public byte[] serialize(Metric source) throws Exception {
        final Spotify100Metric m =
            new Spotify100Metric(source.getKey(), source.getTimestamp(),
                source.getTags(), source.getResource(), source.getValue());
        return mapper.writeValueAsBytes(m);
    }



    @Override
    public byte[] serialize(Collection<Metric> metrics, WriteCache writeCache) {
        throw new UnsupportedOperationException("Not supported");
    }
}

