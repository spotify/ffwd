/**
 * Copyright 2013-2017 Spotify AB. All rights reserved.
 *
 * The contents of this file are licensed under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 **/
package com.spotify.ffwd.http;

import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import okhttp3.OkHttpClient;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockserver.client.server.MockServerClient;
import org.mockserver.junit.MockServerRule;

public class RawHttpClientTest {
    @Rule
    public MockServerRule mockServer = new MockServerRule(this);

    @Rule
    public ExpectedException expected = ExpectedException.none();

    private MockServerClient mockServerClient;

    private final ObjectMapper mapper = HttpClient.Builder.setupApplicationJson();

    private final OkHttpClient okHttpClient = new OkHttpClient();

    private RawHttpClient rawHttpClient;

    @Before
    public void setUp() throws Exception {
        mockServer.getPort();

        rawHttpClient =
            new RawHttpClient(mapper, okHttpClient, "http://localhost:" + mockServer.getPort());


    }

    @Test
    public void testPing() {
        mockServerClient
            .when(request().withMethod("GET").withPath("/ping"))
            .respond(response().withStatusCode(200));
        rawHttpClient.ping().toCompletable().await();
    }

    @Test
    public void testSendBatchSuccess() {
        final String batchRequest =
            "{\"commonTags\":{\"what\":\"error-rate\"},\"points\":[{\"key\":\"test_key\"," +
                "\"tags\":{\"what\":\"error-rate\"},\"value\":1234.0,\"timestamp\":11111}]}";

        mockServerClient
            .when(request()
                .withMethod("POST")
                .withPath("/v1/batch")
                .withHeader("content-type", "application/json")
                .withBody(batchRequest))
            .respond(response().withStatusCode(200));

        final Batch.Point point =
            new Batch.Point("test_key", ImmutableMap.of("what", "error-rate"), 1234L, 11111L);
        final Batch batch =
            new Batch(ImmutableMap.of("what", "error-rate"), ImmutableList.of(point));

        rawHttpClient.sendBatch(batch).toCompletable().await();
    }

    @Test
    public void testSendBatchFail() {
        expected.expectMessage("500: Internal Server Error");

        final String batchRequest =
            "{\"commonTags\":{\"what\":\"error-rate\"},\"points\":[{\"key\":\"test_key\"," +
                "\"tags\":{\"what\":\"error-rate\"},\"value\":1234.0,\"timestamp\":11111}]}";

        mockServerClient
            .when(request()
                .withMethod("POST")
                .withPath("/v1/batch")
                .withHeader("content-type", "application/json")
                .withBody(batchRequest))
            .respond(response().withStatusCode(500));

        final Batch.Point point =
            new Batch.Point("test_key", ImmutableMap.of("what", "error-rate"), 1234L, 11111L);
        final Batch batch =
            new Batch(ImmutableMap.of("what", "error-rate"), ImmutableList.of(point));

        rawHttpClient.sendBatch(batch).toCompletable().await();

    }
}