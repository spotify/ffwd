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
    @JsonSubTypes.Type(value = KafkaRouter.Tag.class, name = "tag"),
    @JsonSubTypes.Type(value = KafkaRouter.Static.class, name = "static")
})
public interface KafkaRouter {
    String route(final Event event);

    String route(final Metric metric);

    class Tag implements KafkaRouter {
        private static final String DEFAULT = "default";
        private static final String DEFAULT_TAGKEY = "site";
        private static final String DEFAULT_METRICS = "metrics-%s";
        private static final String DEFAULT_EVENTS = "events-%s";

        private final String tagKey;
        private final String metrics;
        private final String events;

        @JsonCreator
        public Tag(
            @JsonProperty("tag") final String tagKey, @JsonProperty("metrics") String metrics,
            @JsonProperty("events") String events
        ) {
            this.tagKey = Optional.ofNullable(tagKey).orElse(DEFAULT_TAGKEY);
            this.metrics = Optional.ofNullable(metrics).orElse(DEFAULT_METRICS);
            this.events = Optional.ofNullable(events).orElse(DEFAULT_EVENTS);
        }

        @Override
        public String route(final Event event) {
            final String tagValue = event.getTags().get(tagKey);

            if (tagValue != null) {
                return String.format(events, tagValue);
            }

            return String.format(events, DEFAULT);
        }

        @Override
        public String route(final Metric metric) {
            final String tagValue = metric.getTags().get(tagKey);

            if (tagValue != null) {
                return String.format(metrics, tagValue);
            }

            return String.format(metrics, DEFAULT);
        }

        public static Supplier<KafkaRouter> supplier() {
            return () -> new Tag(null, null, null);
        }
    }

    class Static implements KafkaRouter {
        private static final String DEFAULT_METRICS = "metrics";
        private static final String DEFAULT_EVENTS = "events";

        private final String metrics;
        private final String events;

        @JsonCreator
        public Static(
            @JsonProperty("metrics") String metrics, @JsonProperty("events") String events
        ) {
            this.metrics = Optional.ofNullable(metrics).orElse(DEFAULT_METRICS);
            this.events = Optional.ofNullable(events).orElse(DEFAULT_EVENTS);
        }

        @Override
        public String route(final Event event) {
            return events;
        }

        @Override
        public String route(final Metric metric) {
            return metrics;
        }

        public static Supplier<KafkaRouter> supplier() {
            return () -> new Static(null, null);
        }
    }
}
