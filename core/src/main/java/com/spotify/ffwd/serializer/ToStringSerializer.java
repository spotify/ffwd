/*
 * Copyright 2013-2017 Spotify AB. All rights reserved.
 *
 * The contents of this file are licensed under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.spotify.ffwd.serializer;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.spotify.ffwd.model.Event;
import com.spotify.ffwd.model.Metric;
import lombok.extern.slf4j.Slf4j;

import java.util.function.Supplier;

@Slf4j
public class ToStringSerializer implements Serializer {
    @JsonCreator
    public ToStringSerializer() {
        log.warn("This serializer should only be used for debugging purposes");
    }

    @Override
    public byte[] serialize(Event event) throws Exception {
        return event.toString().getBytes();
    }

    @Override
    public byte[] serialize(Metric metric) throws Exception {
        return metric.toString().getBytes();
    }

    public static Supplier<Serializer> defaultSupplier() {
        return ToStringSerializer::new;
    }
}
