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

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = KafkaPartitioner.Tag.class, name = "tag"),
    @JsonSubTypes.Type(value = KafkaPartitioner.Hashed.class, name = "static"),
    @JsonSubTypes.Type(value = KafkaPartitioner.Host.class, name = "host"),
    @JsonSubTypes.Type(value = KafkaPartitioner.Random.class, name = "random")
})
public interface KafkaPartitioner {

  int partition(final Metric metric, final String defaultHost);

  class Host implements KafkaPartitioner {

    @JsonCreator
    public Host() {
    }

    @Override
    public int partition(final Metric metric, final String defaultHost) {
      final String host = metric.getTags().get("host");
      if (host == null) {
        return defaultHost.hashCode();
      }
      return host.hashCode();
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
    public int partition(final Metric metric, final String defaultHost) {
      final String tagValue = metric.getTags().get(tagKey);

      if (tagValue != null) {
        return tagValue.hashCode();
      }

      throw new IllegalArgumentException(
          String.format("missing tag '%s' for metric %s", tagKey, metric));
    }
  }

  class Hashed implements KafkaPartitioner {

    @JsonCreator
    public Hashed() {
    }

    @Override
    public int partition(final Metric metric, final String defaultHost) {
      return metric.hashCode();
    }
  }

  class Random implements KafkaPartitioner {

    private final java.util.Random rand;

    @JsonCreator
    public Random() {
      this.rand = new java.util.Random();
    }

    @Override
    public int partition(final Metric metric, final String defaultHost) {
      return rand.nextInt();
    }
  }
}
