/**
 * Copyright 2013-2014 Spotify AB. All rights reserved.
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
 **/
package com.spotify.ffwd.kafka;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.spotify.ffwd.model.Event;
import com.spotify.ffwd.model.Metric;

import java.util.Optional;
import java.util.function.Supplier;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = KafkaPartitioner.Tag.class, name = "tag"),
    @JsonSubTypes.Type(value = KafkaPartitioner.Hashed.class, name = "static"),
    @JsonSubTypes.Type(value = KafkaPartitioner.Host.class, name = "host")
})
public interface KafkaPartitioner {
    int partition(final Event event);

    int partition(final Metric metric);

    class Host implements KafkaPartitioner {
        @JsonCreator
        public Host() {
        }

        @Override
        public int partition(final Event event) {
            return event.getHost().hashCode();
        }

        @Override
        public int partition(final Metric metric) {
            return metric.getHost().hashCode();
        }

        public static Supplier<KafkaPartitioner> supplier() {
            return Host::new;
        }
    }

    class Tag implements KafkaPartitioner {
        private static final String DEFAULT_TAGKEY = "site";

        private final String tagKey;

        @JsonCreator
        public Tag(@JsonProperty("tag") final String tagKey) {
            this.tagKey = Optional.ofNullable(tagKey).orElse(DEFAULT_TAGKEY);
        }

        @Override
        public int partition(final Event event) {
            final String tagValue = event.getTags().get(tagKey);

            if (tagValue != null) {
                return tagValue.hashCode();
            }

            throw new IllegalArgumentException(
                String.format("missing tag '%s' for event %s", tagKey, event));
        }

        @Override
        public int partition(final Metric metric) {
            final String tagValue = metric.getTags().get(tagKey);

            if (tagValue != null) {
                return tagValue.hashCode();
            }

            throw new IllegalArgumentException(
                String.format("missing tag '%s' for metric %s", tagKey, metric));
        }

        public static Supplier<KafkaPartitioner> supplier() {
            return () -> new Tag(null);
        }
    }

    class Hashed implements KafkaPartitioner {
        @JsonCreator
        public Hashed() {
        }

        @Override
        public int partition(final Event event) {
            return event.hashCode();
        }

        @Override
        public int partition(final Metric metric) {
            return metric.hashCode();
        }

        public static Supplier<KafkaPartitioner> supplier() {
            return Hashed::new;
        }
    }
}
