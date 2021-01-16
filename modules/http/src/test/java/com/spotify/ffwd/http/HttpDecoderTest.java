/*-
 * -\-\-
 * FastForward Agent
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
package com.spotify.ffwd.http;

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.anyObject;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.name.Names;
import com.spotify.ffwd.Mappers;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class HttpDecoderTest {

  private HttpDecoder httpDecoder;
  @Mock private ChannelHandlerContext mockCtx;
  @Mock private Channel mockChannel;
  @Mock private ChannelFuture mockChannelFuture;
  @Captor private ArgumentCaptor<DefaultFullHttpResponse> responseCaptor;

  @Before
  public void setup() {
    ObjectMapper mapper = Mappers.setupApplicationJson();
    Injector injector =
        Guice.createInjector(
            new AbstractModule() {
              @Override
              protected void configure() {
                bind(ObjectMapper.class)
                    .annotatedWith(Names.named("application/json"))
                    .toInstance(mapper);
              }
            });

    httpDecoder = new HttpDecoder();
    injector.injectMembers(httpDecoder);
    when(mockCtx.channel()).thenReturn(mockChannel);
    when(mockChannel.writeAndFlush(anyObject())).thenReturn(mockChannelFuture);
  }

  @Test(expected = HttpException.class)
  public void testDecodeURINotFound() throws Exception {
    FullHttpRequest httpRequest = createTestRequest(HttpMethod.POST, "/doesNotExist");
    httpDecoder.decode(mockCtx, httpRequest, null);
  }

  @Test(expected = HttpException.class)
  public void testPingIllegalPost() throws Exception {
    FullHttpRequest httpRequest = createTestRequest(HttpMethod.POST, "/ping");

    httpDecoder.decode(mockCtx, httpRequest, null);
  }

  @Test
  public void testDecodePing() throws Exception {
    FullHttpRequest httpRequest = createTestRequest(HttpMethod.GET, "/ping");

    httpDecoder.decode(mockCtx, httpRequest, null);
    verify(mockChannel).writeAndFlush(responseCaptor.capture());
    DefaultFullHttpResponse response = responseCaptor.getValue();
    assertEquals(HttpVersion.HTTP_1_1, response.protocolVersion());
    assertEquals(HttpResponseStatus.OK, response.status());
  }

  @Test(expected = HttpException.class)
  public void testV1BatchGET() throws Exception {
    FullHttpRequest httpRequest = createTestRequest(HttpMethod.GET, "/v1/batch");

    httpDecoder.decode(mockCtx, httpRequest, null);
  }

  @Test(expected = HttpException.class)
  public void testV2BatchGET() throws Exception {
    FullHttpRequest httpRequest = createTestRequest(HttpMethod.GET, "/v2/batch");
    httpDecoder.decode(mockCtx, httpRequest, null);
  }

  @Test(expected = HttpException.class)
  public void testV1BatchUnsupportedMediaType() throws Exception {
    FullHttpRequest httpRequest = createTestRequest(HttpMethod.POST, "/v1/batch");
    httpDecoder.decode(mockCtx, httpRequest, null);
  }

  @Test(expected = HttpException.class)
  public void testV2BatchUnsupportedMediaType() throws Exception {
    FullHttpRequest httpRequest = createTestRequest(HttpMethod.POST, "/v2/batch");
    httpDecoder.decode(mockCtx, httpRequest, null);
  }

  @Test
  public void testV1Batch() throws Exception {
    String v1SchemaContent =
        "{\n"
        + "  \"commonTags\": {},\n"
        + "  \"commonResource\": {},\n"
        + "  \"points\": [\n"
        + "    {\n"
        + "      \"key\":  \"pointKey\",\n"
        + "      \"tags\": {},\n"
        + "      \"resource\": {},\n"
        + "      \"value\": 1.0,\n"
        + "      \"timestamp\": 1608650674\n"
        + "    }\n"
        + "  ]\n"
        + "}";
    DefaultHttpHeaders httpHeaders = new DefaultHttpHeaders();
    httpHeaders.add("Content-Type", "application/json");
    FullHttpRequest httpRequest =
        createTestRequest(HttpMethod.POST, "/v1/batch", httpHeaders, v1SchemaContent);
    List<Object> out = new ArrayList<>();
    httpDecoder.decode(mockCtx, httpRequest, out);
    assertEquals(1, out.size());
  }

  @Test(expected = HttpException.class)
  public void testV1BatchBadContent() throws Exception {
    String v2SchemaContent =
        "{\n"
        + "  \"commonTags\": {},\n"
        + "  \"commonResource\": {},\n"
        + "  \"points\": [\n"
        + "    {\n"
        + "      \"key\":  \"pointKey\",\n"
        + "      \"tags\": {},\n"
        + "      \"resource\": {},\n"
        + "      \"value\": {\n"
        + "        \"doubleValue\": 1.0\n"
        + "      },\n"
        + "      \"timestamp\": 1608650674\n"
        + "    }\n"
        + "  ]\n"
        + "}";
    DefaultHttpHeaders httpHeaders = new DefaultHttpHeaders();
    httpHeaders.add("Content-Type", "application/json");
    FullHttpRequest httpRequest =
        createTestRequest(HttpMethod.POST, "/v1/batch", httpHeaders, v2SchemaContent);
    httpDecoder.decode(mockCtx, httpRequest, null);
  }

  @Test
  public void testV2Batch() throws Exception {
    String v2SchemaContent =
        "{\n"
        + "  \"commonTags\": {},\n"
        + "  \"commonResource\": {},\n"
        + "  \"points\": [\n"
        + "    {\n"
        + "      \"key\":  \"pointKey\",\n"
        + "      \"tags\": {},\n"
        + "      \"resource\": {},\n"
        + "      \"value\": {\n"
        + "        \"doubleValue\": 1.0\n"
        + "      },\n"
        + "      \"timestamp\": 1608650674\n"
        + "    }\n"
        + "  ]\n"
        + "}";
    DefaultHttpHeaders httpHeaders = new DefaultHttpHeaders();
    httpHeaders.add("Content-Type", "application/json");
    FullHttpRequest httpRequest =
        createTestRequest(HttpMethod.POST, "/v2/batch", httpHeaders, v2SchemaContent);
    List<Object> out = new ArrayList<>();
    httpDecoder.decode(mockCtx, httpRequest, out);
    assertEquals(1, out.size());
  }

  @Test(expected = HttpException.class)
  public void testV2BatchBadContent() throws Exception {
    String v2SchemaContent = "bad content";
    DefaultHttpHeaders httpHeaders = new DefaultHttpHeaders();
    httpHeaders.add("Content-Type", "application/json");
    FullHttpRequest httpRequest =
        createTestRequest(HttpMethod.POST, "/v2/batch", httpHeaders, v2SchemaContent);
    httpDecoder.decode(mockCtx, httpRequest, null);
  }

  private FullHttpRequest createTestRequest(HttpMethod httpMethod, String uri) {
    return createTestRequest(httpMethod, uri, new DefaultHttpHeaders(), "testContent");
  }

  private FullHttpRequest createTestRequest(
      HttpMethod httpMethod, String uri, HttpHeaders headers, String content) {

    return new DefaultFullHttpRequest(
        HttpVersion.HTTP_1_1,
        httpMethod,
        uri,
        Unpooled.copiedBuffer(content.getBytes()),
        headers,
        new DefaultHttpHeaders(false));
  }
}
