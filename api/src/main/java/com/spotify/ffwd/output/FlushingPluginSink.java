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
package com.spotify.ffwd.output;

import com.google.inject.Inject;
import com.spotify.ffwd.filter.Filter;
import com.spotify.ffwd.model.Event;
import com.spotify.ffwd.model.Metric;
import com.spotify.ffwd.statistics.OutputPluginStatistics;
import eu.toolchain.async.AsyncFramework;
import eu.toolchain.async.AsyncFuture;
import eu.toolchain.async.FutureFinished;
import eu.toolchain.async.LazyTransform;
import eu.toolchain.async.Transform;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;

import javax.inject.Named;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Facade implementation of a plugin sink that receives metrics and events, puts them on a
 * buffer, then flushes them at
 * regular intervals.
 *
 * @author udoprog
 */
@RequiredArgsConstructor
public class FlushingPluginSink implements PluginSink {
    public static final long DEFAULT_BATCH_SIZE_LIMIT = 10000;
    public static final long DEFAULT_MAX_PENDING_FLUSHES = 10;

    @Inject
    AsyncFramework async;

    @Inject
    BatchedPluginSink sink;

    @Inject
    ScheduledExecutorService scheduler;

    @Inject
    Logger log;

    @Inject
    OutputPluginStatistics statistics;

    @Named("flushing")
    @Inject(optional = true)
    Filter filter = null;

    /**
     * future associated with the timing of the next flush
     */
    final AtomicReference<ScheduledFuture<?>> nextFlush = new AtomicReference<>();

    /**
     * lock that governs access to the current batch
     */
    final Object nextBatchLock = new Object();
    /**
     * the next batch to be flushed.
     */
    Batch nextBatch = new Batch();

    /**
     * lock that governs access to the pending set of futures, this is preferred over eventually
     * consistent concurrent
     * data structures since we desire a clean shutdown that tracks _all_ pending flushes.
     */
    final Object pendingLock = new Object();
    /**
     * a pending set of futures, all access has to be synchronized on {@link #pendingLock}
     */
    final Set<AsyncFuture<Void>> pending = new HashSet<>();

    /**
     * The default flush interval in milliseconds.
     *
     * Flushes will not be scheduled if this value is lower than, or equal to {@code 0}.
     */
    final long flushInterval;

    /**
     * Max size of each batch.
     *
     * A size limit will not be imposed if this value is lower than, or equal to {@code 0}.
     */
    final long batchSizeLimit;

    /**
     * Max number of pending flushes allowed.
     *
     * A pending flush limit will not be imposed if this value is lower than, or equal to {@code 0}.
     */
    final long maxPendingFlushes;

    volatile boolean stopped = false;

    public FlushingPluginSink(long flushInterval) {
        this(flushInterval, DEFAULT_BATCH_SIZE_LIMIT, DEFAULT_MAX_PENDING_FLUSHES);
    }

    @Override
    public void init() {
    }

    @Override
    public void sendMetric(final Metric metric) {
        if (filter != null && !filter.matchesMetric(metric)) {
            return;
        }

        // shortcut: check before synchronized block.
        if (nextBatch == null) {
            // TODO: instrument dropped metric.
            return;
        }

        synchronized (nextBatchLock) {
            final Batch batch = nextBatch;

            if (batch == null) {
                // TODO: instrument dropped metric.
                return;
            }

            batch.metrics.add(metric);
            checkBatch(batch);
        }
    }

    @Override
    public void sendEvent(Event event) {
        if (filter != null && !filter.matchesEvent(event)) {
            return;
        }

        // shortcut: check before synchronized block.
        if (nextBatch == null) {
            // TODO: instrument dropped metric.
            return;
        }

        synchronized (nextBatchLock) {
            final Batch batch = nextBatch;

            if (batch == null) {
                // TODO: instrument dropped metric.
                return;
            }

            batch.events.add(event);
            checkBatch(batch);
        }
    }

    void checkBatch(Batch batch) {
        synchronized (nextBatchLock) {
            if (batchSizeLimit <= 0) {
                return;
            }

            if (batch.size() >= batchSizeLimit) {
                log.debug("Flushing because limit of {} reached", batchSizeLimit);
                flushNowThenScheduleNext();
            }
        }
    }

    @Override
    public AsyncFuture<Void> start() {
        log.info("Starting (Filter: {})", filter);

        return sink.start().transform(new Transform<Void, Void>() {
            @Override
            public Void transform(Void result) throws Exception {
                scheduleNext();
                return null;
            }
        });
    }

    @Override
    public AsyncFuture<Void> stop() {
        stopped = true;

        // stop scheduler -> flush remaining items -> stop sink.
        return async.call(new Callable<Void>() {
            @Override
            public Void call() throws Exception {
                if (flushInterval > 0) {
                    ScheduledFuture<?> next = nextFlush.getAndSet(null);
                    next.cancel(false);
                }

                return null;
            }
        }).lazyTransform(new LazyTransform<Void, Void>() {
            /**
             * Wait for all pending tasks to finish, stop accepting events and metrics.
             */
            @Override
            public AsyncFuture<Void> transform(Void result) throws Exception {
                final ArrayList<AsyncFuture<Void>> pending = new ArrayList<>();

                pending.add(doLastFlush());

                synchronized (pendingLock) {
                    pending.addAll(FlushingPluginSink.this.pending);
                    FlushingPluginSink.this.pending.clear();
                }

                return async.collectAndDiscard(pending);
            }
        }).lazyTransform(new LazyTransform<Void, Void>() {
            /**
             * Stop the underlying sink.
             */
            @Override
            public AsyncFuture<Void> transform(Void result) throws Exception {
                return sink.stop();
            }
        });
    }

    @Override
    public boolean isReady() {
        return sink.isReady();
    }

    /**
     * Flushes the current batch and schedules the next one
     *
     * Maintains the set of pending tasks.
     */
    void flushNowThenScheduleNext() {
        final AsyncFuture<Void> flush = doFlush(newBatch());

        // Shutting down.
        if (flush == null) {
            return;
        }

        // shortcut: future is most likely an immediate, no reason to maintain the set of pending
        // tasks.
        if (!flush.isDone()) {
            synchronized (pendingLock) {
                log.debug("Adding pending flush (size: {})", pending.size());

                pending.add(flush);
            }
            // when future is done, remove this as a pending task.
            flush.on(new FutureFinished() {
                @Override
                public void finished() throws Exception {
                    synchronized (pendingLock) {
                        log.debug("Removing pending flush (size: {})", pending.size());
                        pending.remove(flush);
                    }
                }
            });
        }

        // this is the last batch, don't bother scheduling flush of another one.
        flush.on(new FutureFinished() {
            @Override
            public void finished() throws Exception {
                scheduleNext();
            }
        });
    }

    /**
     * Schedule the next flush, if applicable.
     */
    void scheduleNext() {
        if (stopped) {
            return;
        }

        if (flushInterval <= 0) {
            return;
        }

        final Runnable flusher = new Runnable() {
            @Override
            public void run() {
                flushNowThenScheduleNext();
            }
        };

        if (log.isDebugEnabled()) {
            log.debug("Scheduling next flush at {}",
                new Date(System.currentTimeMillis() + flushInterval));
        }

        final ScheduledFuture<?> next =
            scheduler.schedule(flusher, flushInterval, TimeUnit.MILLISECONDS);

        final ScheduledFuture<?> future = nextFlush.getAndSet(next);

        if (future != null && !future.isDone()) {
            future.cancel(false);
        }
    }

    /**
     * Perform the last flush, setting the nextBatch to null, indicating that we are shutting down.
     */
    AsyncFuture<Void> doLastFlush() {
        return doFlush(null);
    }

    /**
     * Perform a flush of the next batch.
     *
     * The given parameter will be set as the next batch.
     *
     * @param newBatch The next batch to flush, setting to {@code null} will cause all subsequent
     * batches to be
     *            rejected.
     * @return A future associated with the current flush, or {@code null} if we are stopping.
     */
    AsyncFuture<Void> doFlush(Batch newBatch) {
        final Batch batch;

        synchronized (nextBatchLock) {
            batch = nextBatch;

            if (batch == null) {
                return null;
            }

            nextBatch = newBatch;
        }

        if (batch == null || batch.isEmpty()) {
            return async.resolved();
        }

        if (maxPendingFlushes > 0) {
            synchronized (pendingLock) {
                if (pending.size() >= maxPendingFlushes) {
                    log.warn(
                        "Max number of pending flushes ({}) reached, dropping {} metric(s) and " +
                            "event(s)", pending.size(), batch.size());
                    statistics.reportDropped(batch.size());
                    return async.resolved();
                }
            }
        }

        final List<AsyncFuture<Void>> futures = new ArrayList<>();

        if (!batch.events.isEmpty()) {
            futures.add(sink.sendEvents(batch.events));
        }

        if (!batch.metrics.isEmpty()) {
            futures.add(sink.sendMetrics(batch.metrics));
        }

        // chain into batch future.
        return async.collectAndDiscard(futures);
    }

    /**
     * Build a new batch.
     *
     * This is primarily to make mocking easier.
     *
     * @return A new batch.
     */
    Batch newBatch() {
        return new Batch();
    }

    @RequiredArgsConstructor
    static class Batch {
        private final List<Event> events = new ArrayList<>();
        private final List<Metric> metrics = new ArrayList<>();

        public int size() {
            return events.size() + metrics.size();
        }

        public boolean isEmpty() {
            return events.isEmpty() && metrics.isEmpty();
        }

        public String toString() {
            return "Batch{metrics=" + metrics.size() + ", events=" + events.size() + "}";
        }
    }
}
