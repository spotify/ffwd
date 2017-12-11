/**
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
 **/
package com.spotify.ffwd.http;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class Batch {
    private final Map<String, String> commonTags;
    private final List<Point> points;

    @JsonCreator
    public Batch(
        @JsonProperty("commonTags") final Map<String, String> commonTags,
        @JsonProperty("points") final List<Point> points
    ) {
        this.commonTags = commonTags;
        this.points = points;
    }

    @Data
    public static class Point {
        private final String key;
        private final Map<String, String> tags;
        private final double value;
        private final long timestamp;

        public Point(
            @JsonProperty("key") final String key,
            @JsonProperty("tags") final Map<String, String> tags,
            @JsonProperty("value") final double value,
            @JsonProperty("timestamp") final long timestamp
        ) {
            this.key = key;
            this.tags = tags;
            this.value = value;
            this.timestamp = timestamp;
        }
    }
}
