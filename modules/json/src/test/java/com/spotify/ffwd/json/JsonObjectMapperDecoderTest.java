/*-
 * -\-\-
 * FastForward JSON Module
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

package com.spotify.ffwd.json;

import static junit.framework.TestCase.assertNull;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import org.junit.Before;
import org.junit.Test;

public class JsonObjectMapperDecoderTest {
  private final ObjectMapper mapper = new ObjectMapper();

  private JsonObjectMapperDecoder j;

  @Before
  public void setup() {
   j = new JsonObjectMapperDecoder();
  }

  @Test
  public void nullKey() throws IOException {
    final JsonNode json = mapper.readTree("{\"key\": null}");
    final String key = j.decodeString(json, "key");

    assertNull(key);
  }

  @Test
  public void noHostInJson() throws IOException {
    final JsonNode json = mapper.readTree("{\"key\": null}");
    final String host = j.decodeString(json, "host");

    assertNull(host);
  }

}
