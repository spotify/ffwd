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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.netflix.loadbalancer.ILoadBalancer;
import com.netflix.loadbalancer.reactive.LoadBalancerCommand;
import com.spotify.ffwd.model.Batch;
import com.spotify.ffwd.model.Event;
import com.spotify.ffwd.model.Metric;
import com.spotify.ffwd.output.BatchablePluginSink;
import eu.toolchain.async.AsyncFramework;
import eu.toolchain.async.AsyncFuture;
import eu.toolchain.async.FutureDone;
import eu.toolchain.async.ResolvableFuture;
import eu.toolchain.async.StreamCollector;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import javax.inject.Inject;
import rx.Observable;
import rx.Observer;

public class HttpPluginSink implements BatchablePluginSink {
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
    private HttpClientFactory clientFactory;

    @Inject
    private ILoadBalancer loadBalancer;

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
        final Batch b = new Batch(ImmutableMap.of(), ImmutableMap.of(), out);

        return sendBatches(ImmutableList.of(b));
    }

    @Override
    public AsyncFuture<Void> sendBatches(final Collection<Batch> batches) {
        final List<Callable<AsyncFuture<Void>>> callables = new ArrayList<>();

        for (final Batch b : batches) {
            callables.add(() -> {
                final Observable<Void> observable = LoadBalancerCommand.<Void>builder()
                    .withLoadBalancer(loadBalancer)
                    .build()
                    .submit(server -> toObservable(clientFactory.newClient(server).sendBatch(b)));

                return fromObservable(observable);
            });
        }

        return async.eventuallyCollect(callables, VOID_COLLECTOR, PARALLELISM);
    }

    private <T> AsyncFuture<T> fromObservable(final Observable<T> observable) {
        final ResolvableFuture<T> future = async.future();

        observable.subscribe(new Observer<T>() {
            @Override
            public void onCompleted() {
                if (future.cancel()) {
                    throw new IllegalStateException();
                }
            }

            @Override
            public void onError(final Throwable e) {
                future.fail(e);
            }

            @Override
            public void onNext(final T v) {
                future.resolve(v);
            }
        });

        return future;
    }

    private <T> Observable<T> toObservable(final AsyncFuture<T> future) {
        return Observable.create(observer -> {
            future.onDone(new FutureDone<T>() {
                @Override
                public void failed(final Throwable throwable) throws Exception {
                    observer.onError(throwable);
                }

                @Override
                public void resolved(final T v) throws Exception {
                    observer.onNext(v);
                    observer.onCompleted();
                }

                @Override
                public void cancelled() throws Exception {
                    observer.onCompleted();
                }
            });
        });
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
