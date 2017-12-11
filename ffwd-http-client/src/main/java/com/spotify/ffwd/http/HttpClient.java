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

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.netflix.loadbalancer.ILoadBalancer;
import com.netflix.loadbalancer.LoadBalancerBuilder;
import com.netflix.loadbalancer.reactive.LoadBalancerCommand;
import java.util.Optional;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import rx.Observable;


@Slf4j
@Data
public class HttpClient {

    private final RawHttpClientFactory clientFactory;

    private final ILoadBalancer loadBalancer;

    public Observable<Void> sendBatch(final Batch batch) {
        return buildCommand()
            .submit(server -> clientFactory.newClient(server).sendBatch(batch));
    }

    public static class Builder {

        private Optional<HttpDiscovery> discovery = Optional.empty();

        private Optional<String> searchDomain = Optional.empty();

        public static ObjectMapper setupApplicationJson() {
            final ObjectMapper mapper = new ObjectMapper();
            mapper.registerModule(new Jdk8Module());
            mapper.enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES);
            mapper.enable(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES);
            return mapper;
        }

        public HttpClient build() {
            final HttpDiscovery discovery = this.discovery.orElseGet(HttpDiscovery::supplyDefault);
            final OkHttpClient okHttpClient = new OkHttpClient();
            final ObjectMapper objectMapper = setupApplicationJson();
            final RawHttpClientFactory httpClientFactory =
                new RawHttpClientFactory(objectMapper, okHttpClient);
            final HttpPing httpPing = new HttpPing(httpClientFactory);

            final ILoadBalancer loadBalancer = discovery
                .apply(LoadBalancerBuilder.newBuilder(), searchDomain)
                .withPing(httpPing)
                .buildDynamicServerListLoadBalancer();

            return new HttpClient(httpClientFactory, loadBalancer);
        }

        public Builder discovery(HttpDiscovery discovery) {
            this.discovery = Optional.of(discovery);
            return this;
        }

        public Builder searchDomain(String searchDomain) {
            this.searchDomain = Optional.of(searchDomain);
            return this;
        }
    }

    protected LoadBalancerCommand<Void> buildCommand(){
        return LoadBalancerCommand.<Void>builder().withLoadBalancer(loadBalancer).build();
    }
}
