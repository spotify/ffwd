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

package com.spotify.ffwd.model.v2;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.io.Resources;
import com.google.protobuf.ByteString;
import com.spotify.ffwd.Mappers;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;

public class TestBatch {

  private ObjectMapper mapper;

  @Before
  public void setUp() {
    this.mapper = Mappers.setupApplicationJson();
  }

  @Test
  public void testBatch0() throws Exception {
    final String value = readResources("TestBatchV2.withoutResource.json");
    mapper.readValue(value, com.spotify.ffwd.model.v2.Batch.class);
  }

  @Test
  public void testBatchSerializationWithoutResource() throws Exception {
    final String value = readResources("TestBatch.0.json");
    mapper.readValue(value, com.spotify.ffwd.model.v2.Batch.class);
  }

  @Test(expected = Exception.class)
  public void testBatchBad() throws Exception {
    final String value = readResources("TestBatch.bad.json");
    mapper.readValue(value, com.spotify.ffwd.model.v2.Batch.class);
  }


  @Test
  public void testBatchSerializationWithResource() throws Exception {
    Map<String, String> commonResources = Map.of("node1", "instance1", "node2", "instance2");
    Map<String, String> commonTags = Map.of("host", "host1");
    Batch batchIn = createBatch(commonTags, commonResources);
    String jsonStr = mapper.writeValueAsString(batchIn);
    Batch batchOut = mapper.readValue(jsonStr, com.spotify.ffwd.model.v2.Batch.class);
    assertThat(batchIn, is(batchOut));
  }


  private com.spotify.ffwd.model.v2.Batch createBatch
      (final Map<String, String> commonTags, final Map<String, String> commonResources) {
    ByteString byteString = ByteString.copyFromUtf8("addddesgeagtept");
    Batch.Point p1 = createPoint(byteString);
    Batch.Point p2 = createPoint(600.56);
    List<Batch.Point> points = List.of(p1, p2);
    return new com.spotify.ffwd.model.v2.Batch(commonTags, commonResources, points);
  }


  private Batch.Point createPoint(final Double val) {
    Optional<Map<String, String>> tag1 =
        Optional.of(Map.of("what", "cpu-used-percentage", "units",
            "%"));

    Optional<Map<String, String>> resource =
        Optional.of(Map.of("instance", "instance_point1"));

    Value.DoubleValue val1 = com.spotify.ffwd.model.v2.Value.DoubleValue.create(val);

    return Batch.Point.create("distribution-test", tag1, resource, val1,
        System.currentTimeMillis());
  }

  private static Batch.Point createPoint(final ByteString val) {
    Optional<Map<String, String>> tag1 =
        Optional.of(Map.of("what", "cpu-used-percentage", "units",
            "%"));
    Optional<Map<String, String>> resource =
        Optional.of(Map.of("instance", "instance_point1"));

    Value.DistributionValue val1 =
        com.spotify.ffwd.model.v2.Value.DistributionValue.create(val);

    return Batch.Point.create("distribution-test", tag1, resource, val1,
        System.currentTimeMillis());
  }

  private String readResources(final String name) throws IOException {
    return Resources.toString(Resources.getResource(name), StandardCharsets.UTF_8);
  }

}
