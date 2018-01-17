/**
 * Copyright 2013-2017 Spotify AB. All rights reserved.
 * <p>
 * The contents of this file are licensed under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 **/
package com.spotify.ffwd.http;

import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.util.ArrayList;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockserver.client.server.MockServerClient;
import org.mockserver.junit.MockServerRule;
import org.mockserver.model.HttpRequest;
import org.mockserver.verify.VerificationTimes;

public class HttpClientTest {
    @Rule
    public MockServerRule mockServer = new MockServerRule(this);

    @Rule
    public ExpectedException expected = ExpectedException.none();

    private MockServerClient mockServerClient;

    private HttpClient httpClient;

    private final HttpRequest pingRequest = request().withMethod("GET").withPath("/ping");

    final List<HttpDiscovery.HostAndPort> servers = new ArrayList<>();

    @Before
    public void setUp() throws Exception {
        mockServerClient.when(pingRequest).respond(response().withStatusCode(200));

        servers.add(new HttpDiscovery.HostAndPort("localhost", mockServer.getPort()));
        servers.add(new HttpDiscovery.HostAndPort("localhost", mockServer.getPort()));
        servers.add(new HttpDiscovery.HostAndPort("localhost", mockServer.getPort()));

        httpClient = new HttpClient.Builder().discovery(new HttpDiscovery.Static(servers)).build();
    }

    @After
    public void tearDown() {
        mockServerClient.verify(pingRequest, VerificationTimes.atLeast(1));
        httpClient.shutdown();
    }

    @Test
    public void testSendBatchSuccess() {
        final String batchRequest =
            "{\"commonTags\":{\"what\":\"error-rate\"},\"points\":[{\"key\":\"test_key\"," +
                "\"tags\":{\"what\":\"error-rate\"},\"value\":1234.0,\"timestamp\":11111}]}";

        final HttpRequest request = request()
            .withMethod("POST")
            .withPath("/v1/batch")
            .withHeader("content-type", "application/json")
            .withBody(batchRequest);

        mockServerClient.when(request).respond(response().withStatusCode(200));

        final Batch.Point point =
            new Batch.Point("test_key", ImmutableMap.of("what", "error-rate"), 1234L, 11111L);
        final Batch batch =
            new Batch(ImmutableMap.of("what", "error-rate"), ImmutableList.of(point));

        httpClient.sendBatch(batch).toCompletable().await();

        mockServerClient.verify(request, VerificationTimes.atLeast(1));
    }

    @Test
    public void testSendBatchFail() {
        expected.expectMessage("500: Internal Server Error");

        final String batchRequest =
            "{\"commonTags\":{\"what\":\"error-rate\"},\"points\":[{\"key\":\"test_key\"," +
                "\"tags\":{\"what\":\"error-rate\"},\"value\":1234.0,\"timestamp\":11111}]}";

        final HttpRequest request = request()
            .withMethod("POST")
            .withPath("/v1/batch")
            .withHeader("content-type", "application/json")
            .withBody(batchRequest);

        mockServerClient.when(request).respond(response().withStatusCode(500));

        final Batch.Point point =
            new Batch.Point("test_key", ImmutableMap.of("what", "error-rate"), 1234L, 11111L);
        final Batch batch =
            new Batch(ImmutableMap.of("what", "error-rate"), ImmutableList.of(point));

        try {
            httpClient.sendBatch(batch).toCompletable().await();
        } finally {
            mockServerClient.verify(request, VerificationTimes.atLeast(3));
        }
    }
}