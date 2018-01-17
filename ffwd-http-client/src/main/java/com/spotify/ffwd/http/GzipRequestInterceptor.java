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

import java.io.IOException;
import okhttp3.Interceptor;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okio.Buffer;
import okio.BufferedSink;
import okio.GzipSink;
import okio.Okio;

/**
 * https://github.com/square/okhttp/issues/350
 */
class GzipRequestInterceptor implements Interceptor {
    @Override
    public Response intercept(Chain chain) throws IOException {
        final Request request = chain.request();

        if (request.body() == null || request.header("Content-Encoding") != null) {
            return chain.proceed(request);
        }

        final Request compressedRequest = request
            .newBuilder()
            .header("Content-Encoding", "gzip")
            .method(request.method(), compress(request.body()))
            .build();

        return chain.proceed(compressedRequest);
    }

    private RequestBody compress(final RequestBody body) throws IOException {
        final Buffer buffer = new Buffer();

        try (final BufferedSink gzipSink = Okio.buffer(new GzipSink(buffer))) {
            body.writeTo(gzipSink);
        }

        return new RequestBody() {
            @Override
            public MediaType contentType() {
                return body.contentType();
            }

            @Override
            public long contentLength() {
                return buffer.size();
            }

            @Override
            public void writeTo(BufferedSink sink) throws IOException {
                sink.write(buffer.snapshot());
            }
        };
    }
}