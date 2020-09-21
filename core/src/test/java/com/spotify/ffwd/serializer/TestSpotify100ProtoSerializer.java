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
import static org.junit.Assert.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.protobuf.ByteString;
import com.spotify.ffwd.cache.NoopCache;
import com.spotify.ffwd.model.Metric;
import com.spotify.ffwd.model.v2.Value;
import com.spotify.proto.Spotify100;
import java.io.IOException;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.xerial.snappy.Snappy;

public class TestSpotify100ProtoSerializer {
  private Serializer serializer;
  private Metric metric1;
  private com.spotify.ffwd.model.v2.Metric metricV2;

  private static NoopCache writeCache = new NoopCache();

  //Test Data
  private String key = "";
  private double value = 0.5;
  private Value distributionDoubleValue = Value.DoubleValue.create(0.02);
  private Value distributionBytStringValue = Value.DistributionValue.create(ByteString.copyFromUtf8("ABCDEFG_1234"));
  private long time = System.currentTimeMillis();
  private Date date = Date.from(Instant.ofEpochSecond(1542812184));
  private Set<String> riemannTags = ImmutableSet.of("rtags");
  private Map<String,String> tags =  ImmutableMap.of("tag_key", "tag_val");
  private Map<String,String> resource = ImmutableMap.of("res_key","res_val");
  private String proc ="proc";

  @Before
  public void setup() {
    serializer = new Spotify100ProtoSerializer();
  }
  @Test(expected = UnsupportedOperationException.class)
  public void testSerializeMetric() throws Exception {
      metric1 = create("",value,date, riemannTags,tags,resource,proc);
      serializer.serialize(metric1);
  }

  @Test(expected = UnsupportedOperationException.class)
  public void testSerializeMetricV2() throws Exception {
      metricV2 = create("",distributionDoubleValue,time,tags,resource);
      serializer.serialize(metric1);
  }

  @Test(expected = NullPointerException.class)
  public void testNullKeyExceptionV2() throws Exception {
    metricV2 = create(null,distributionDoubleValue,time,tags,resource);
    serializer.serialize(ImmutableList.of(metric1), writeCache);
  }

    @Test(expected = NullPointerException.class)
  public void testNullValueExceptionV2() throws Exception {
    metricV2 = create(key,null,time,tags,resource);
    serializer.serialize(ImmutableList.of(metric1), writeCache);
  }


  @Test(expected = NullPointerException.class)
  public void testNullKeyException() throws Exception {
    metric1 = create(null,value,date, riemannTags,tags,resource,proc);
    serializer.serialize(ImmutableList.of(metric1), writeCache);
  }

    @Test
    public void testSerializedMetricBatchingV2() throws Exception {
        com.spotify.ffwd.model.v2.Metric metric1 = create(key,distributionDoubleValue,time,tags,resource);
        com.spotify.ffwd.model.v2.Metric metric2 = create(key,distributionBytStringValue,time,tags,resource);
        com.spotify.ffwd.model.v2.Metric metric3  = create(key,Value.DoubleValue.create(40),time,tags,resource);
        List<com.spotify.ffwd.model.v2.Metric> batchIn = ImmutableList.of(metric1,metric2,metric3);

        final byte [] bytes = serializer.serializeMetrics( batchIn, writeCache );

        Spotify100.Batch spotify100BatchOut = deserialize(bytes);

        assertThat( batchIn.size(), is(spotify100BatchOut.getMetricList().size()));
    }

    @Test
    public void testSerializedMetricBatching() throws Exception {
        Metric metric1 = create(key,0.001,date,riemannTags,tags,resource,proc);
        Metric metric2 = create(key,0.002,date,riemannTags,tags,resource,proc);
        Metric metric3  = create(key,0.0003,date,riemannTags,tags,resource,proc);
        List<Metric> batchIn = ImmutableList.of(metric1,metric2,metric3);

        final byte [] bytes = serializer.serialize( batchIn, writeCache );

        Spotify100.Batch spotify100BatchOut = deserialize(bytes);

        assertThat( batchIn.size(), is(spotify100BatchOut.getMetricList().size()));
    }

  @Test
  public void testSerializedMetricWithDoubleValue() throws Exception {
      String key ="testV2";
      Map<String,String> tags =  ImmutableMap.of("a", "b");
      Map<String,String> resource = ImmutableMap.of("res1","res2");
      Value value = Value.DoubleValue.create(0.02);
      long time = System.currentTimeMillis();

      com.spotify.ffwd.model.v2.Metric  metric1 = create(key,value,time,tags,resource);

     final byte [] bytes = serializer.serializeMetrics(ImmutableList.of(metric1), writeCache );

     Spotify100.Batch spotify100BatchOut = deserialize(bytes);

     assertThat( value.getValue(), is(spotify100BatchOut.getMetricList()
          .get(0).getDistributionTypeValue().getDoubleValue()));

     assertThat( key, is(spotify100BatchOut.getMetricList()
            .get(0).getKey()));

     assertThat( tags, is(spotify100BatchOut.getMetricList()
            .get(0).getTagsMap()));
     assertThat( resource, is(spotify100BatchOut.getMetricList()
            .get(0).getResourceMap()));
     assertThat( time, is(spotify100BatchOut.getMetricList()
            .get(0).getTime()));
  }
  @Test
  public void testSerializedMetricWithDistributionValue() throws Exception {
    String key ="testV2";
    Value value = Value.DistributionValue.create((ByteString.copyFromUtf8("ABCDEFG")));

    com.spotify.ffwd.model.v2.Metric  metric1 = create(key,value,time,tags,resource);

    final byte [] bytes = serializer.serializeMetrics(ImmutableList.of(metric1), writeCache );

    Spotify100.Batch spotify100BatchOut = deserialize(bytes);

    assertThat( value.getValue(), is(spotify100BatchOut.getMetricList()
            .get(0).getDistributionTypeValue().getDistributionValue()));

    assertThat( key, is(spotify100BatchOut.getMetricList()
            .get(0).getKey()));

    assertThat( tags, is(spotify100BatchOut.getMetricList()
            .get(0).getTagsMap()));
    assertThat( resource, is(spotify100BatchOut.getMetricList()
            .get(0).getResourceMap()));
    assertThat( time, is(spotify100BatchOut.getMetricList()
            .get(0).getTime()));
  }


  @Test
  public void testSerializeMetrics() throws Exception {
    metric1 = new Metric("",
            0.0,
            Date.from(Instant.ofEpochSecond(1542812184)),
            ImmutableSet.of(),
            ImmutableMap.of("tag-a", "foo"),
            ImmutableMap.of("resource-a", "bar"),
            "");
    final Metric metric2 = new Metric("",
            1.0,
            Date.from(Instant.ofEpochSecond(1542812190)),
            ImmutableSet.of(),
            ImmutableMap.of("tag-a", "foo"),
            ImmutableMap.of("resource-a", "bar"),
            "");

    final byte[] serialize = serializer.serialize(ImmutableList.of(metric1, metric2), writeCache);

    assertThat(serialize, is(
            new byte[]{93, -120, 10, 40, 16, -64, -37, -106, -74, -13, 44, 34, 12, 10, 5, 116, 97, 103,
                    45, 97, 18, 3, 102, 111, 111, 42, 17, 10, 10, 114, 101, 115, 111, 117, 114, 99,
                    101, 1, 19, 52, 98, 97, 114, 10, 49, 16, -80, -118, -105, -74, -13, 44, 25, 0, 5,
                    1, 4, -16, 63, -126, 51, 0})
    );
  }


  private Spotify100.Batch deserialize( final byte bytesIn [] ) throws
          IOException {
      final byte [] bytesOut = Snappy.uncompress(bytesIn);
      return  Spotify100.Batch.parseFrom(bytesOut);
  }

  private Metric create(final String key,
                              final double value,
                              final Date date,
                              final Set<String> riemannTags,
                              final Map<String,String>tags,
                              final Map<String,String>resource,
                              final String proc  ){
    return new Metric (
            key,
            value,
            date,
            riemannTags,
            tags,
            resource,
            proc);

  }

  private com.spotify.ffwd.model.v2.Metric create(final String key,
                              final Value value,
                              final long time,
                              final Map<String,String>tags,
                              final Map<String,String>resource){
    return new com.spotify.ffwd.model.v2.Metric (
            key,
            value,
            time,
            tags,
            resource);

  }

}
