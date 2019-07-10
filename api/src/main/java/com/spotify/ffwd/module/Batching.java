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

package com.spotify.ffwd.module;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Optional;
import javax.annotation.Nullable;
import lombok.Data;

@Data
public class Batching {
    public static final boolean DEFAULT_REPORT_STATISTICS = false;

    @Nullable protected final Long flushInterval;
    protected final Optional<Long> batchSizeLimit;
    protected final Optional<Long> maxPendingFlushes;

    /**
     * Should batching-specific statistics (metrics) be reported? This adds a number of metrics.
     */
    protected final boolean reportStatistics;

    @JsonCreator
    public Batching(
        @JsonProperty("flushInterval") @Nullable Long flushInterval,
        @JsonProperty("batchSizeLimit") Optional<Long> batchSizeLimit,
        @JsonProperty("maxPendingFlushes") Optional<Long> maxPendingFlushes,
        @JsonProperty("reportStatistics") Optional<Boolean> reportStatistics
    ) {
        this.flushInterval = flushInterval;
        this.batchSizeLimit = batchSizeLimit;
        this.maxPendingFlushes = maxPendingFlushes;
        this.reportStatistics = reportStatistics.orElse(DEFAULT_REPORT_STATISTICS);
    }

    /**
     * This method exists to create a unified interface for configuration batching. This object
     * hides a compatibility path to the old way of specifying flushInterval (outside of a
     * 'batching' block).
     * <p>
     * BatchingPluginSink can be placed in front of any output plugin. Different output plugins
     * might use different configuration for their batching. This class contains all configuration
     * for batching, and may be specified for every output plugin.
     *
     * @param flushInterval Optional configuration on the output plugin level in the configuration.
     * This only contains something when the user didn't use any 'batching' sub structure in the
     * configuration, and just specified flushInterval.
     * @param batching A complete Batching structure, on the output plugin level in the conf.
     * @return A Batching object.
     */
    public static Batching from(
        @Nullable final Long flushInterval,
        final Optional<Batching> batching
    ) {
        return batching.orElseGet(
          () -> new Batching(flushInterval, Optional.empty(), Optional.empty(), Optional.empty())
        );
    }
}
