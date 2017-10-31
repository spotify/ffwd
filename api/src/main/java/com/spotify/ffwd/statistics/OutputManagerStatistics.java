/*
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
 */
package com.spotify.ffwd.statistics;

public interface OutputManagerStatistics {
    /**
     * Report that the given number of events have been sent to output plugins.
     *
     * @param sent The number of metrics sent.
     */
    public void reportSentEvents(int sent);

    /**
     * Report that the given number of metrics have been sent to output plugins.
     *
     * @param sent The number of metrics sent.
     */
    public void reportSentMetrics(int sent);

    /**
     * Reported that the given number of events were filtered.
     * <p>
     * Filtered events are <em>not</em> sent to output plugins.
     *
     * @param filtered The number of filtered events.
     */
    public void reportEventsDroppedByFilter(int dropped);

    /**
     * Reported that the given number of metrics were filtered.
     * <p>
     * Filtered metrics are <em>not</em> sent to output plugins.
     *
     * @param filtered The number of filtered metrics.
     */
    public void reportMetricsDroppedByFilter(int dropped);
}
