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

import static org.hamcrest.CoreMatchers.any;
import static org.junit.Assert.assertThat;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.spotify.ffwd.model.Metric;
import java.time.Instant;
import java.util.Date;
import org.junit.Before;
import org.junit.Test;

public class TestSpotify100ProtoSerializer {
  private Spotify100ProtoSerializer spotify100ProtoSerializer;
  private Metric metric;

  @Before
  public void setup() {
    spotify100ProtoSerializer = new Spotify100ProtoSerializer();
  }

  @Test
  public void testSerializeMetric() throws Exception {
    metric = new Metric("",
      0.0,
      Date.from(Instant.ofEpochSecond(1542812184)),
      ImmutableSet.of(),
      ImmutableMap.of("a", "b"),
      ImmutableMap.of(),
      "");

    final byte[] serialize = spotify100ProtoSerializer.serialize(metric);

    assertThat(serialize, any(byte[].class));
  }

  @Test(expected = NullPointerException.class)
  public void testNullKeyException() throws Exception {
    metric = new Metric(null,
      0.0,
      Date.from(Instant.ofEpochSecond(1542812184)),
      ImmutableSet.of(),
      ImmutableMap.of("a", "b"),
      ImmutableMap.of(),
      "");

    spotify100ProtoSerializer.serialize(metric);
  }

}
