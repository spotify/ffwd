/*
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
 */
package com.spotify.ffwd.http;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.spotify.ffwd.model.Batch;
import com.spotify.ffwd.model.Event;
import com.spotify.ffwd.model.Metric;
import com.spotify.ffwd.output.BatchedPluginSink;
import eu.toolchain.async.AsyncFramework;
import eu.toolchain.async.AsyncFuture;
import eu.toolchain.async.ResolvableFuture;
import eu.toolchain.async.StreamCollector;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import javax.inject.Inject;
import javax.inject.Named;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

@Slf4j
public class HttpPluginSink implements BatchedPluginSink {
    public static final String V1_BATCH_ENDPOINT = "v1/batch";
    public static final StreamCollector<Void, Void> VOID_COLLECTOR =
        new StreamCollector<Void, Void>() {
            @Override
            public void resolved(final Void aVoid) throws Exception {

            }

            @Override
            public void failed(final Throwable throwable) throws Exception {

            }

            @Override
            public void cancelled() throws Exception {

            }

            @Override
            public Void end(final int i, final int i1, final int i2) throws Exception {
                return null;
            }
        };

    private static final int PARALLELISM = 10;

    @Inject
    private AsyncFramework async;

    @Inject
    @Named("application/json")
    private ObjectMapper mapper;

    @Inject
    private OkHttpClient httpClient;

    @Inject
    @Named("baseUrl")
    private String baseUrl;

    @Override
    public void init() {
    }

    @Override
    public void sendEvent(final Event event) {
        sendEvents(Collections.singletonList(event));
    }

    @Override
    public void sendMetric(final Metric metric) {
        sendMetrics(Collections.singletonList(metric));
    }

    @Override
    public void sendBatch(final Batch batch) {
    }

    @Override
    public AsyncFuture<Void> sendEvents(final Collection<Event> events) {
        // TODO: Does not support events (yet?). Instrument that they have been dropped?
        return async.resolved();
    }

    @Override
    public AsyncFuture<Void> sendMetrics(final Collection<Metric> metrics) {
        final List<Batch.Point> out = new ArrayList<>();

        for (final Metric m : metrics) {
            out.add(m.toBatchPoint());
        }

        // creates a new batch _without_ common tags, prefer using sendBatches instead.
        final Batch b = new Batch(ImmutableMap.of(), out);

        return sendBatches(ImmutableList.of(b));
    }

    @Override
    public AsyncFuture<Void> sendBatches(final Collection<Batch> batches) {
        final List<Callable<AsyncFuture<Void>>> callables = new ArrayList<>();

        for (final Batch b : batches) {
            callables.add(() -> {
                final byte[] body;

                try {
                    body = mapper.writeValueAsBytes(b);
                } catch (JsonProcessingException e) {
                    throw new RuntimeException(e);
                }

                final Request.Builder request = new Request.Builder();

                request.url(baseUrl + "/" + V1_BATCH_ENDPOINT);
                request.post(RequestBody.create(MediaType.parse("application/json"), body));

                final ResolvableFuture<Void> future = async.future();

                httpClient.newCall(request.build()).enqueue(new Callback() {
                    @Override
                    public void onFailure(final Call call, final IOException e) {
                        future.fail(e);
                    }

                    @Override
                    public void onResponse(final Call call, final Response response)
                        throws IOException {
                        future.resolve(null);
                    }
                });

                return future;
            });
        }

        return async.eventuallyCollect(callables, VOID_COLLECTOR, PARALLELISM);
    }

    @Override
    public AsyncFuture<Void> start() {
        return async.resolved();
    }

    @Override
    public AsyncFuture<Void> stop() {
        return async.resolved();
    }

    @Override
    public boolean isReady() {
        return true;
    }
}
