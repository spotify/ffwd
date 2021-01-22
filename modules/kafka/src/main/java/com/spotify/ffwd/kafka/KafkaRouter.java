/*-
 * -\-\-
 * FastForward Kafka Module
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

package com.spotify.ffwd.kafka;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.spotify.ffwd.model.v2.Metric;
import java.util.Optional;
import java.util.function.Supplier;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = KafkaRouter.Tag.class, name = "tag"),
    @JsonSubTypes.Type(value = KafkaRouter.Static.class, name = "static")
})
public interface KafkaRouter {
    String route(final Metric metric);

    class Tag implements KafkaRouter {
        private static final String DEFAULT = "default";
        private static final String DEFAULT_TAGKEY = "site";
        private static final String DEFAULT_METRICS = "metrics-%s";

        private final String tagKey;
        private final String metrics;

        @JsonCreator
        public Tag(
            @JsonProperty("tag") final String tagKey,
            @JsonProperty("metrics") String metrics
        ) {
            this.tagKey = Optional.ofNullable(tagKey).orElse(DEFAULT_TAGKEY);
            this.metrics = Optional.ofNullable(metrics).orElse(DEFAULT_METRICS);
        }

        @Override
        public String route(final Metric metric) {
            final String tagValue = metric.getTags().get(tagKey);

            if (tagValue != null) {
                return String.format(metrics, tagValue);
            }

            return String.format(metrics, DEFAULT);
        }

        static Supplier<KafkaRouter> supplier() {
            return () -> new Tag(null, null);
        }
    }

    class Static implements KafkaRouter {
        private static final String DEFAULT_METRICS = "metrics";

        private final String metrics;

        @JsonCreator
        public Static(@JsonProperty("metrics") String metrics) {
            this.metrics = Optional.ofNullable(metrics).orElse(DEFAULT_METRICS);
        }

        @Override
        public String route(final Metric metric) {
            return metrics;
        }
    }
}
