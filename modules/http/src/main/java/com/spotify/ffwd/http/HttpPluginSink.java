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
import com.spotify.ffwd.model.v2.Batch;
import com.spotify.ffwd.model.v2.Metric;
import com.spotify.ffwd.output.BatchablePluginSink;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.inject.Inject;
import rx.Observable;
import rx.Observer;

public class HttpPluginSink implements BatchablePluginSink {

  private static final int PARALLELISM = 10;
  private static ExecutorService EXECUTOR = Executors.newFixedThreadPool(PARALLELISM);

  @Inject
  private HttpClientFactory clientFactory;

  @Inject
  private ILoadBalancer loadBalancer;

  @Override
  public void sendMetric(final Metric metric) {
    sendMetrics(Collections.singletonList(metric));
  }

  @Override
  public void sendBatch(final Batch batch) {
  }

  @Override
  public CompletableFuture<Void> sendMetrics(final Collection<Metric> metrics) {
    final List<Metric> out = new ArrayList<>(metrics);

    // creates a new batch _without_ common tags, prefer using sendBatches instead.
    final Batch b = new Batch(ImmutableMap.of(), ImmutableMap.of(), out);

    return sendBatches(ImmutableList.of(b));
  }

  @Override
  public CompletableFuture<Void> sendBatches(final Collection<Batch> batches) {
    List<CompletableFuture<Void>> futures = new ArrayList<>();

    for (final Batch b : batches) {
      CompletableFuture<Void> future =
          CompletableFuture.supplyAsync(() -> {
            return LoadBalancerCommand.<Void>builder()
                .withLoadBalancer(loadBalancer)
                .build()
                .submit(server -> toObservable(clientFactory.newClient(server).sendBatch(b)));
          }, EXECUTOR).thenCompose(this::fromObservable);

      futures.add(future);
    }

    CompletableFuture<Void> collected =
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
    futures.forEach(f -> f.exceptionally(e -> {
      collected.completeExceptionally(e);
      return null;
    }));

    return collected;
  }


  private <T> CompletableFuture<T> fromObservable(final Observable<T> observable) {
    CompletableFuture<T> future = new CompletableFuture<>();

    observable.subscribe(new Observer<T>() {
      @Override
      public void onCompleted() {
        if (future.cancel(true)) {
          throw new IllegalStateException();
        }
      }

      @Override
      public void onError(final Throwable e) {
        future.completeExceptionally(e);
      }

      @Override
      public void onNext(final T v) {
        future.complete(v);
      }
    });

    return future;
  }

  private <T> Observable<T> toObservable(final CompletableFuture<T> future) {
    return Observable.create(observer -> {
      future.handle((result, error) -> {
        if (error == null) {
          observer.onNext(result);
          observer.onCompleted();
        } else if (error instanceof CancellationException) {
          observer.onCompleted();
        } else {
          observer.onError(error);
        }

        return result;
      });
    });
  }
}
