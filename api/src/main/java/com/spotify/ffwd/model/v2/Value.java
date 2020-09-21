/*-
 * -\-\-
 * FastForward API
 * --
 * Copyright (C) 2016 - 2020 Spotify AB
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

package com.spotify.ffwd.model.v2;


import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.google.protobuf.ByteString;
import lombok.Data;

/**
 * Distribution histogram point value. Currently we
 * support {@link Value.DoubleValue} and {@link Value.DistributionValue}
 */
@JsonSerialize(using = ValueSerializer.class)
@JsonDeserialize(using = ValueDeserializer.class)
public  abstract class Value {
    @JsonProperty("value")
    public abstract Object getValue();

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class DoubleValue extends Value {
        private final Double value;


        @Override
        public Double getValue() {
            return value;
        }

        public static com.spotify.ffwd.model.v2.Value.DoubleValue create(
                final double value) {
            return new com.spotify.ffwd.model.v2.Value.DoubleValue(value);
        }
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class DistributionValue  extends Value {
        private final ByteString value;


        @Override
        public ByteString getValue() {
            return value;
        }


        public static com.spotify.ffwd.model.v2.Value.DistributionValue create(
                final ByteString value) {
            return new com.spotify.ffwd.model.v2.Value.DistributionValue(value);
        }
    }

}
