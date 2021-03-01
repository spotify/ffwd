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
