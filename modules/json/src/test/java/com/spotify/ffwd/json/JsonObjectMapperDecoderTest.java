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

}
