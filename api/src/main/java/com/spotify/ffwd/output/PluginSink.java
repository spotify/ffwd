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

import com.spotify.ffwd.Initializable;
import com.spotify.ffwd.model.v2.Batch;
import com.spotify.ffwd.model.v2.Metric;
import eu.toolchain.async.AsyncFuture;

public interface PluginSink extends Initializable {
    /**
     * Send the given collection of metrics.
     * <p>
     * This method is fire-and-forget, because tracking each individual send is too expensive.
     *
     * @param metric Metric to send.
     */
    void sendMetric(Metric metric);

    /**
     * Send the given collection of metrics.
     * <p>
     * This method is fire-and-forget, because tracking each individual send is too expensive.
     *
     * @param batch Batch to send.
     */
    void sendBatch(Batch batch);

    AsyncFuture<Void> start();

    AsyncFuture<Void> stop();

    boolean isReady();
}
