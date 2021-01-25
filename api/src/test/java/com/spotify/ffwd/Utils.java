/*-
 * -\-\-
 * FastForward Pubsub Module
 * --
 * Copyright (C) 2016 - 2019 Spotify AB
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
package com.spotify.ffwd;

import com.google.common.collect.ImmutableMap;
import com.google.protobuf.ByteString;
import com.spotify.ffwd.model.v2.Metric;
import com.spotify.ffwd.model.v2.Value;


public class Utils {

  public static Metric makeMetric() {
    return makeDistributionMetric(1278);
  }

  public static Metric makeDistributionMetric(double val) {
    return new com.spotify.ffwd.model.v2.Metric("key1", create(val), System.currentTimeMillis(),
        ImmutableMap.of("what", "test"), ImmutableMap.of());
  }

  public static com.spotify.ffwd.model.v2.Metric makeDistributionMetric(ByteString val) {
    return new com.spotify.ffwd.model.v2.Metric("key1", create(val), System.currentTimeMillis(),
        ImmutableMap.of("what", "test"), ImmutableMap.of());
  }

  private static Value create(final ByteString byteString) {
    return Value.DistributionValue.create(byteString);
  }


  private static Value create(final double doubleVal) {
    return Value.DoubleValue.create(doubleVal);
  }

}
