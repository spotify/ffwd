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
import com.spotify.ffwd.cache.WriteCache;
import com.spotify.ffwd.model.Metric;
import java.util.Collection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ToStringSerializer implements Serializer {
    private static final Logger log = LoggerFactory.getLogger(ToStringSerializer.class);

    @JsonCreator
    public ToStringSerializer() {
        log.warn("This serializer should only be used for debugging purposes");
    }

    @Override
    public byte[] serialize(Metric metric) {
        return metric.toString().getBytes();
    }

    @Override
    public byte[] serialize(Collection<Metric> metrics, WriteCache writeCache) {
        throw new UnsupportedOperationException("Not supported");
    }
}
