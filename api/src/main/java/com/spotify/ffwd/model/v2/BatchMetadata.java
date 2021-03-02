/*-
 * -\-\-
 * FastForward HTTP Module
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

package com.spotify.ffwd.model.v2;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableMap;
import java.util.Map;
import java.util.Optional;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
@EqualsAndHashCode(of = { "commonTags", "commonResource" })
public class BatchMetadata {

  private final Map<String, String> commonTags;
  private final Map<String, String> commonResource;

  /**
   * JSON creator.
   */
  @JsonCreator
  public static BatchMetadata create(
      @JsonProperty("commonTags") final Optional<Map<String, String>> commonTags,
      @JsonProperty("commonResource") final Optional<Map<String, String>> commonResource
  ) {
    return new BatchMetadata(commonTags.orElseGet(ImmutableMap::of),
        commonResource.orElseGet(ImmutableMap::of));
  }
}
