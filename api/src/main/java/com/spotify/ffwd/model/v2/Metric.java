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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableMap;
import com.google.common.hash.HashFunction;
import com.google.common.hash.Hasher;
import com.google.common.hash.Hashing;
import com.spotify.ffwd.model.Metrics;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import lombok.Data;
import lombok.EqualsAndHashCode;


@Data
@EqualsAndHashCode(of = { "key", "tags" })
public class Metric implements Metrics {

  static final HashFunction HASH_FUNCTION = Hashing.murmur3_128();

  private final String key;
  private final Value value;
  private final long timestamp;
  private final Map<String, String> tags;
  private final Map<String, String> resource;

  @JsonCreator
  public static Metric create(
      @JsonProperty("key") final String key,
      @JsonProperty("tags") final Optional<Map<String, String>> tags,
      @JsonProperty("resource") final Optional<Map<String, String>> resource,
      @JsonProperty("value") final Value value,
      @JsonProperty("timestamp") final long timestamp
  ) {
    return new Metric(key, value, timestamp,
        tags.orElseGet(ImmutableMap::of), resource.orElseGet(ImmutableMap::of));
  }

  public boolean hasDistribution() {
    if (value != null && value.isValid()) {
      return value instanceof Value.DistributionValue;
    }
    return false;
  }

  @Override
  public String generateHash() {
    final Hasher hasher = HASH_FUNCTION.newHasher();

    if (key != null) {
      hasher.putString(key, Charsets.UTF_8);
    }

    for (final Map.Entry<String, String> kv : new TreeMap<>(tags).entrySet()) {
      final String k = kv.getKey();
      final String v = kv.getValue();

      if (k != null) {
        hasher.putString(k, Charsets.UTF_8);
      }

      if (v != null) {
        hasher.putString(v, Charsets.UTF_8);
      }
    }

    return hasher.hash().toString();
  }

  public static Builder builder() {
    return new Builder();
  }

  public static class Builder {

    private String key;
    private Value value;
    private Long timestamp;
    private Map<String, String> tags = ImmutableMap.of();
    private Map<String, String> resource = ImmutableMap.of();


    public Builder setKey(String key) {
      this.key = key;
      return this;
    }

    public Builder setValue(Value value) {
      this.value = value;
      return this;
    }

    public Builder setTimestamp(long timestamp) {
      this.timestamp = timestamp;
      return this;
    }

    public Builder setTags(Map<String, String> tags) {
      this.tags = tags;
      return this;
    }

    public Builder setResource(Map<String, String> resource) {
      this.resource = resource;
      return this;
    }

    public Metric build() {
      return new Metric(key, value, timestamp, tags, resource);
    }

  }
}
