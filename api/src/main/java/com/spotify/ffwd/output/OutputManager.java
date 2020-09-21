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
import com.spotify.ffwd.model.Batch;
import com.spotify.ffwd.model.Metric;
import eu.toolchain.async.AsyncFuture;

public interface OutputManager extends Initializable {
    /**
     * Send a collection of metrics to all output plugins.
     */
    void sendMetric(Metric metric);

    /**
     * Send a batch collection of metrics to all output plugins.
     *
     * <p> This method was added to maintain compatibility.
     * Please create an implementation that fit your need.
     */
    default void sendBatch(com.spotify.ffwd.model.v2.Batch batch) {
        throw new RuntimeException("Method has not been implemented");
    }

    /**
     * Send a collection of metrics to all output plugins.
     *
     * <p> This method was added to maintain compatibility.
     * Please create an implementation that fit your need.
     */
    default void sendMetric(com.spotify.ffwd.model.v2.Metric metric) {
        throw new RuntimeException("Method has not been implemented");
    }

    /**
     * Send a batch collection of metrics to all output plugins.
     */
    void sendBatch(Batch batch);

    AsyncFuture<Void> start();

    AsyncFuture<Void> stop();
}
