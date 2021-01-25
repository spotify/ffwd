/*-
 * -\-\-
 * FastForward Core
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

package com.spotify.ffwd.serializer;

import static org.hamcrest.core.Is.is;
import static org.hamcrest.MatcherAssert.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.protobuf.ByteString;
import com.spotify.ffwd.cache.NoopCache;
import com.spotify.ffwd.model.v2.Metric;
import com.spotify.ffwd.model.v2.Value;
import com.spotify.proto.*;
import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.UnknownFormatConversionException;
import org.junit.Before;
import org.junit.Test;
import org.xerial.snappy.Snappy;


public class TestSpotify100ProtoSerializer {

  private Serializer serializer;
  private Metric metric;


  private static NoopCache writeCache = new NoopCache();

  //Test Data
  private String key = "KEY";
  private Value distributionDoubleValue = Value.DoubleValue.create(0.02);
  private Value distributionBytStringValue =
      Value.DistributionValue.create(ByteString.copyFromUtf8("ABCDEFG_1234"));
  private long time = 1542812184;
  private Map<String, String> tags = ImmutableMap.of("tag_key", "tag_val");
  private Map<String, String> resource = ImmutableMap.of("res_key", "res_val");

  @Before
  public void setup() {
    serializer = new Spotify100ProtoSerializer();
  }


  @Test(expected = UnsupportedOperationException.class)
  public void testSerializeMetric() throws Exception {
    metric = create("", distributionDoubleValue, time, tags, resource);
    serializer.serialize(metric);
  }

  @Test(expected = NullPointerException.class)
  public void testNullKeyException() throws Exception {
    metric = create(null, distributionDoubleValue, time, tags, resource);
    serializer.serialize(ImmutableList.of(metric), writeCache);
  }

  @Test(expected = UnknownFormatConversionException.class)
  public void testNullValueException() throws Exception {
    metric = create(key, null, time, tags, resource);
    serializer.serialize(ImmutableList.of(metric), writeCache);
  }

  @Test
  public void testSerializedMetricBatching() throws Exception {
    com.spotify.ffwd.model.v2.Metric metric1 =
        create(key, distributionDoubleValue, time, tags, resource);
    com.spotify.ffwd.model.v2.Metric metric2 =
        create(key, distributionBytStringValue, time, tags, resource);
    com.spotify.ffwd.model.v2.Metric metric3 =
        create(key, Value.DoubleValue.create(40), time, tags, resource);
    Collection<Metric> batchIn = ImmutableList.of(metric1, metric2, metric3);

    final byte[] bytes = serializer.serialize(batchIn, writeCache);

    Spotify100.Batch spotify100BatchOut = deserialize(bytes);

    assertThat(batchIn.size(), is(spotify100BatchOut.getMetricList().size()));
  }


  @Test
  public void testSerializedMetricWithDoubleValue() throws Exception {
    Map<String, String> tags = ImmutableMap.of("a", "b");
    Map<String, String> resource = ImmutableMap.of("res1", "res2");
    Value value = Value.DoubleValue.create(0.02);
    long time = System.currentTimeMillis();

    Metric metric1 = create(key, value, time, tags, resource);

    final byte[] bytes = serializer.serialize(ImmutableList.of(metric1), writeCache);

    Spotify100.Batch spotify100BatchOut = deserialize(bytes);

    assertThat(value.getValue(), is(spotify100BatchOut.getMetricList()
        .get(0).getDistributionTypeValue().getDoubleValue()));

    assertThat(key, is(spotify100BatchOut.getMetricList()
        .get(0).getKey()));

    assertThat(tags, is(spotify100BatchOut.getMetricList()
        .get(0).getTagsMap()));
    assertThat(resource, is(spotify100BatchOut.getMetricList()
        .get(0).getResourceMap()));
    assertThat(time, is(spotify100BatchOut.getMetricList()
        .get(0).getTime()));
  }

  @Test
  public void testSerializedMetricWithDistributionValue() throws Exception {
    Value value = Value.DistributionValue.create((ByteString.copyFromUtf8("ABCDEFG")));

    Metric metric1 = create(key, value, time, tags, resource);

    final byte[] bytes = serializer.serialize(ImmutableList.of(metric1), writeCache);

    Spotify100.Batch spotify100BatchOut = deserialize(bytes);

    assertThat(value.getValue(), is(spotify100BatchOut.getMetricList()
        .get(0).getDistributionTypeValue().getDistributionValue()));

    assertThat(key, is(spotify100BatchOut.getMetricList()
        .get(0).getKey()));

    assertThat(tags, is(spotify100BatchOut.getMetricList()
        .get(0).getTagsMap()));
    assertThat(resource, is(spotify100BatchOut.getMetricList()
        .get(0).getResourceMap()));
    assertThat(time, is(spotify100BatchOut.getMetricList()
        .get(0).getTime()));
  }


  private Spotify100.Batch deserialize(final byte bytesIn[]) throws
                                                             IOException {
    final byte[] bytesOut = Snappy.uncompress(bytesIn);
    return Spotify100.Batch.parseFrom(bytesOut);
  }


  private com.spotify.ffwd.model.v2.Metric create(final String key,
                                                  final Value value,
                                                  final long time,
                                                  final Map<String, String> tags,
                                                  final Map<String, String> resource) {
    return new com.spotify.ffwd.model.v2.Metric(
        key,
        value,
        time,
        tags,
        resource);

  }

}
