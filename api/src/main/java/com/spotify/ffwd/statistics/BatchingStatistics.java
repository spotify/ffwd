/*
 * Copyright 2018 Spotify AB. All rights reserved.
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

import eu.toolchain.async.FutureFinished;

public interface BatchingStatistics {
    /**
     * Report that the given number of events have been sent to output plugins.
     *
     * @param sent The number of events sent.
     */
    void reportSentEvents(int sent);

    /**
     * Report that the given number of metrics have been sent to output plugins.
     *
     * @param sent The number of metrics sent.
     */
    void reportSentMetrics(int sent);

    /**
     * Report that the given number of batches have been sent to output plugins.
     *
     * @param sent The number of batches sent.
     */
    void reportSentBatches(int sent, int contentSize);

    /**
     * Report that metrics/events/batches were internally batched up into a new batch
     *
     * @param num The number of internal batches
     */
    void reportInternalBatchCreate(int num);

    /**
     * Report that an internally batched up batch was written
     *
     * @param size The size of the internal batch that was written
     */
    void reportInternalBatchWrite(int size);

    /**
     * Report metrics/events that is queued up while batching. This is a combined count for all
     * metrics and events together, including metrics&events contained in batches.
     *
     * @param num The count of metrics & events that are now added to the queue
     */
    void reportQueueSizeInc(final int num);

    /**
     * Report that the queue of metrics/events were decreased. This happens when metrics and events
     * were flushed by the batching plugin and the flush finished.
     *
     * @param num The count of metrics & events that are no longer enqueued
     */
    void reportQueueSizeDec(final int num);

    /**
     * Helper to monitor the completion of a write. This can be used to know the latency of writes
     * and the number of outstanding writes.
     *
     * @return A FutureFinished object to be registered with a write completion future
     */
    FutureFinished monitorWrite();

    /**
     * Reported that the given number of events were dropped.
     * <p>
     * Filtered events are <em>not</em> sent to output plugins.
     *
     * @param dropped The number of dropped events.
     */
    void reportEventsDroppedByFilter(int dropped);

    /**
     * Reported that the given number of metrics were dropped.
     * <p>
     * Filtered metrics are <em>not</em> sent to output plugins.
     *
     * @param dropped The number of dropped metrics.
     */
    void reportMetricsDroppedByFilter(int dropped);
}
