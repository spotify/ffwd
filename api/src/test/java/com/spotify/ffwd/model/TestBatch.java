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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.io.Resources;
import com.spotify.ffwd.Mappers;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
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
    final String value = readResources("TestBatch.0.json");
    mapper.readValue(value, Batch.class);
  }

  @Test
  public void testBatch1() throws Exception {
    final String value = readResources("TestBatch.1.json");
    mapper.readValue(value, Batch.class);
  }

  @Test
  public void testBatchWithResource() throws Exception {
    final String value = readResources("TestBatch.WithResource.json");
    mapper.readValue(value, Batch.class);
  }

  @Test(expected = Exception.class)
  public void testBatchBad() throws Exception {
    final String value = readResources("TestBatch.bad.json");
    mapper.readValue(value, Batch.class);
  }

  private String readResources(final String name) throws IOException {
    return Resources.toString(Resources.getResource(name), StandardCharsets.UTF_8);
  }
}
