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