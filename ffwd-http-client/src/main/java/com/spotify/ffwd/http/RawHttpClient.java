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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import lombok.Data;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import rx.Observable;
import rx.Subscriber;

@Data
public class RawHttpClient {
    public static final String V1_BATCH_ENDPOINT = "v1/batch";
    public static final String PING_ENDPOINT = "ping";

    private final ObjectMapper mapper;
    private final OkHttpClient httpClient;
    private final String baseUrl;

    public Observable<Void> sendBatch(final Batch batch) {
        System.out.println("Haha1");
        final byte[] body;

        try {
            body = mapper.writeValueAsBytes(batch);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }

        final Request.Builder request = new Request.Builder();

        request.url(baseUrl + "/" + V1_BATCH_ENDPOINT);
        request.post(RequestBody.create(MediaType.parse("application/json"), body));

        return execute(request);
    }

    public Observable<Void> ping() {
        final Request.Builder request = new Request.Builder();

        request.url(baseUrl + "/" + PING_ENDPOINT);
        request.get();

        return execute(request);
    }

    private Observable<Void> execute(final Request.Builder request) {
        //TODO: handle unsubscribe and cancel the request if that happens.
        return Observable.unsafeCreate(new Observable.OnSubscribe<Void>() {
            public void call(final Subscriber<? super Void> subscriber) {
                final Call call = httpClient.newCall(request.build());

                call.enqueue(new Callback() {
                    public void onFailure(final Call call, final IOException e) {
                        subscriber.onError(e);
                    }

                    public void onResponse(final Call call, final Response response)
                        throws IOException {
                        if (response.isSuccessful()) {
                            subscriber.onCompleted();
                        } else {
                            subscriber.onError(new RuntimeException(
                                "HTTP request failed: " + response.code() + ": " +
                                    response.message()));
                        }

                        response.close();
                    }
                });
            }
        });
    }
}
