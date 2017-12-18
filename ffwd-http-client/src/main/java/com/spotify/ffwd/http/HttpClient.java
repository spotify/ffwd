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

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.netflix.loadbalancer.AvailabilityFilteringRule;
import com.netflix.loadbalancer.ILoadBalancer;
import com.netflix.loadbalancer.LoadBalancerBuilder;
import com.netflix.loadbalancer.RetryRule;
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
    private final int retries;
    private final long baseDelayMillis;
    private final long maxDelayMillis;

    public Observable<Void> sendBatch(final Batch batch) {
        return buildCommand()
            .submit(server -> clientFactory.newClient(server).sendBatch(batch))
            .retryWhen(new RetryWithDelay(retries, baseDelayMillis, maxDelayMillis));
    }

    public static class Builder {
        public static final int DEFAULT_RETRIES = 3;
        public static final long DEFAULT_BASE_DELAY_MILLIS = 50L;
        public static final long DEFAULT_MAX_DELAY_MILLIS = 10000L;

        private Optional<HttpDiscovery> discovery = Optional.empty();
        private Optional<String> searchDomain = Optional.empty();
        private Optional<Integer> retries = Optional.empty();
        private Optional<Long> baseDelayMillis = Optional.empty();
        private Optional<Long> maxDelayMillis = Optional.empty();

        public static ObjectMapper setupApplicationJson() {
            final ObjectMapper mapper = new ObjectMapper();
            mapper.registerModule(new Jdk8Module());
            mapper.enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES);
            mapper.enable(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES);
            return mapper;
        }

        public HttpClient build() {
            final HttpDiscovery discovery = this.discovery.orElseGet(HttpDiscovery::supplyDefault);
            final OkHttpClient.Builder builder = new OkHttpClient.Builder();

            builder.addInterceptor(new GzipRequestInterceptor());

            final ObjectMapper objectMapper = setupApplicationJson();
            final RawHttpClientFactory httpClientFactory =
                new RawHttpClientFactory(objectMapper, builder.build());
            final HttpPing httpPing = new HttpPing(httpClientFactory);

            final ILoadBalancer loadBalancer = discovery
                .apply(LoadBalancerBuilder.newBuilder(), searchDomain)
                .withPing(httpPing)
                .withRule(new RetryRule(new AvailabilityFilteringRule()))
                .buildDynamicServerListLoadBalancer();

            final int retries = this.retries.orElse(DEFAULT_RETRIES);
            final long baseDelayMillis = this.baseDelayMillis.orElse(DEFAULT_BASE_DELAY_MILLIS);
            final long maxDelayMillis = this.maxDelayMillis.orElse(DEFAULT_MAX_DELAY_MILLIS);

            return new HttpClient(httpClientFactory, loadBalancer, retries, baseDelayMillis,
                maxDelayMillis);
        }

        /**
         * Discovery mechanism to use.
         * <p>
         * Example SRV:
         * {@code
         * builder.discover(new HttpDiscovery.Srv("ffwd._tcp.example.com"));
         * }
         * <p>
         * Example Static:
         * <pre>{@code
         * final List<HostAndPort> servers = new ArrayList<>();
         * servers.add(new HostAndPort("ffwd1.example.com", 12345));
         * servers.add(new HostAndPort("ffwd2.example.com", 12345));
         *
         * builder.discover(new HttpDiscovery.Static(servers));
         * }</pre>
         *
         * @param discovery
         * @return
         */
        public Builder discovery(HttpDiscovery discovery) {
            this.discovery = Optional.of(discovery);
            return this;
        }

        /**
         * Search domain to use when looking up DNS records.
         * <p>
         * Search domain applies as a suffix for all domain lookups except:
         * <p>
         * <li>The domain ends with a dot (<code>.</code>)</li>
         * <li>The domain is <code>localhost</code></li>
         *
         * @return
         */
        public Builder searchDomain(String searchDomain) {
            this.searchDomain = Optional.of(searchDomain);
            return this;
        }

        /**
         * Number of retries to perform for each batch.
         *
         * @return this builder
         */
        public Builder retries(int retries) {
            this.retries = Optional.of(retries);
            return this;
        }

        /**
         * Base delay between retries.
         * <p>
         * Client uses an exponential backoff policy with jitter.
         * The base delay is the first delay before a retry.
         *
         * @return this builder
         */
        public Builder baseDelayMillis(long baseDelayMillis) {
            this.baseDelayMillis = Optional.of(baseDelayMillis);
            return this;
        }

        /**
         * Max delay between retries.
         * <p>
         * If delay would exceed the max delay, max delay would be used instead.
         *
         * @return this builder
         */
        public Builder maxDelayMillis(long maxDelayMillis) {
            this.maxDelayMillis = Optional.of(maxDelayMillis);
            return this;
        }
    }

    protected LoadBalancerCommand<Void> buildCommand() {
        return LoadBalancerCommand.<Void>builder().withLoadBalancer(loadBalancer).build();
    }
}
