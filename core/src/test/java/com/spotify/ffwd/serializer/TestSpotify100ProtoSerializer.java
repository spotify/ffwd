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
import com.spotify.ffwd.model.Metric;
import java.time.Instant;
import java.util.Date;
import org.junit.Before;
import org.junit.Test;

public class TestSpotify100ProtoSerializer {
  private Serializer serializer;
  private Metric metric1;

  @Before
  public void setup() {
    serializer = new Spotify100ProtoSerializer();
  }

  @Test(expected = UnsupportedOperationException.class)
  public void testSerializeMetric() throws Exception {
    metric1 = new Metric("",
      0.0,
      Date.from(Instant.ofEpochSecond(1542812184)),
      ImmutableSet.of(),
      ImmutableMap.of("a", "b"),
      ImmutableMap.of(),
      "");

    serializer.serialize(metric1);
  }

  @Test(expected = NullPointerException.class)
  public void testNullKeyException() throws Exception {
    metric1 = new Metric(null,
      0.0,
      Date.from(Instant.ofEpochSecond(1542812184)),
      ImmutableSet.of(),
      ImmutableMap.of("a", "b"),
      ImmutableMap.of(),
      "");

    serializer.serialize(ImmutableList.of(metric1));
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

    final byte[] serialize = serializer.serialize(ImmutableList.of(metric1, metric2));

    assertThat(serialize, is(
      new byte[]{93, -120, 10, 40, 16, -64, -37, -106, -74, -13, 44, 34, 12, 10, 5, 116, 97, 103,
                 45, 97, 18, 3, 102, 111, 111, 42, 17, 10, 10, 114, 101, 115, 111, 117, 114, 99,
                 101, 1, 19, 52, 98, 97, 114, 10, 49, 16, -80, -118, -105, -74, -13, 44, 25, 0, 5,
                 1, 4, -16, 63, -126, 51, 0})
    );
  }

}
