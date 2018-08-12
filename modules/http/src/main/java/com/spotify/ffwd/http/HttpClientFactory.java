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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.loadbalancer.Server;
import eu.toolchain.async.AsyncFramework;
import javax.inject.Inject;
import javax.inject.Named;
import lombok.Data;
import okhttp3.OkHttpClient;

@Data
public class HttpClientFactory {
    @Inject
    private AsyncFramework async;

    @Inject
    @Named("application/json")
    private ObjectMapper mapper;

    @Inject
    private OkHttpClient httpClient;

    public HttpClient newClient(final Server server) {
        final String baseUrl = "http://" + server.getHost() + ":" + server.getPort();
        return new HttpClient(async, mapper, httpClient, baseUrl);
    }
}
