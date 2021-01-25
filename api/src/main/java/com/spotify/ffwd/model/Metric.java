/*-
 * -\-\-
 * FastForward API
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

package com.spotify.ffwd.model;

import com.google.common.base.Charsets;
import com.google.common.hash.HashFunction;
import com.google.common.hash.Hasher;
import com.google.common.hash.Hashing;
import java.util.Date;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import lombok.Data;
import lombok.EqualsAndHashCode;


@Data
@EqualsAndHashCode(of = { "key", "tags" })
public class Metric implements Metrics {

  static final HashFunction HASH_FUNCTION = Hashing.murmur3_128();

  private final String key;
  private final double value;
  private final Date time;
  private final Set<String> riemannTags;
  private final Map<String, String> tags;
  private final Map<String, String> resource;
  private final String proc;

  /**
   * Convert into a batch point, lose information that is not relevant for batches.
   *
   * @return a batch point
   */
  public Batch.Point toBatchPoint() {
    return new Batch.Point(key, tags, resource, value, time.getTime());
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

}
