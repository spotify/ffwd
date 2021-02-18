/*-
 * -\-\-
 * FastForward HTTP Module
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

package com.spotify.ffwd.http;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.spotify.ffwd.model.v2.Batch;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class HttpClient {

  private static final String V2_BATCH_ENDPOINT = "v2/batch";
  private static final String PING_ENDPOINT = "ping";

  private final ObjectMapper mapper;
  private final OkHttpClient httpClient;
  private final String baseUrl;

  public HttpClient(ObjectMapper mapper, OkHttpClient httpClient, String baseUrl) {
    this.mapper = mapper;
    this.httpClient = httpClient;
    this.baseUrl = baseUrl;
  }

  public CompletableFuture<Void> sendBatch(final Batch batch) {
    final byte[] body;

    try {
      body = mapper.writeValueAsBytes(batch);
    } catch (JsonProcessingException e) {
      throw new RuntimeException(e);
    }

    final Request.Builder request = new Request.Builder();

    request.url(baseUrl + "/" + V2_BATCH_ENDPOINT);
    request.post(RequestBody.create(MediaType.parse("application/json"), body));

    return execute(request);
  }

  public CompletableFuture<Void> ping() {
    final Request.Builder request = new Request.Builder();

    request.url(baseUrl + "/" + PING_ENDPOINT);
    request.get();

    return execute(request);
  }

  private CompletableFuture<Void> execute(final Request.Builder request) {
    CompletableFuture<Void> future = new CompletableFuture<>();

    httpClient.newCall(request.build()).enqueue(new Callback() {
      @Override
      public void onFailure(final Call call, final IOException e) {
        future.completeExceptionally(e);
      }

      @Override
      public void onResponse(final Call call, final Response response) {
        if (response.isSuccessful()) {
          future.complete(null);
        } else {
          future.completeExceptionally(new RuntimeException(
              "HTTP request failed: " + response.code() + ": " + response.message()));
        }

        response.close();
      }
    });

    return future;
  }
}
