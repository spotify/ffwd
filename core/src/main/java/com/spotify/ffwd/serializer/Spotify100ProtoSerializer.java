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

import com.spotify.ffwd.model.Event;
import com.spotify.ffwd.model.Metric;
import com.spotify.proto.Spotify100;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Spotify100ProtoSerializer implements Serializer {

  @Override
  public byte[] serialize(final Event event) throws Exception {
    log.debug("Serializing events is not supported");
    return new byte[0];
  }

  @Override
  public byte[] serialize(final Metric metric) throws Exception {
    return Spotify100.Metric.newBuilder()
      .setKey(metric.getKey())
      .setTime(metric.getTime().getTime())
      .setValue(metric.getValue())
      .putAllTags(metric.getTags())
      .putAllResource(metric.getResource())
      .build()
      .toByteArray();
  }
}
