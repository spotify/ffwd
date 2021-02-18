/*-
 * -\-\-
 * FastForward API
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

package com.spotify.ffwd.output;


import com.spotify.ffwd.model.v2.Batch;
import com.spotify.ffwd.model.v2.Metric;
import java.util.Collection;
import java.util.concurrent.CompletableFuture;

public interface BatchablePluginSink extends PluginSink {

  /**
   * Send the given collection of metrics.
   *
   * @param metrics Metrics to send.
   *
   * @return A future that will be resolved when the metrics have been sent.
   */
  CompletableFuture<Void> sendMetrics(Collection<Metric> metrics);

  /**
   * Send the given collection of batches.
   *
   * @param batches Batches to send.
   *
   * @return A future that will be resolved when the batches have been sent.
   */
  CompletableFuture<Void> sendBatches(Collection<Batch> batches);
}
