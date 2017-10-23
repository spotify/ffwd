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
package com.spotify.ffwd.kafka;

import com.google.common.base.Stopwatch;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterators;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.inject.Inject;
import com.spotify.ffwd.model.Batch;
import com.spotify.ffwd.model.Event;
import com.spotify.ffwd.model.Metric;
import com.spotify.ffwd.output.BatchedPluginSink;
import com.spotify.ffwd.serializer.Serializer;
import eu.toolchain.async.AsyncFramework;
import eu.toolchain.async.AsyncFuture;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.inject.Named;
import kafka.javaapi.producer.Producer;
import kafka.producer.KeyedMessage;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class KafkaPluginSink implements BatchedPluginSink {
    @Inject
    private AsyncFramework async;

    @Inject
    private Producer<Integer, byte[]> producer;

    @Inject
    private KafkaRouter router;

    @Inject
    private KafkaPartitioner partitioner;

    @Inject
    private Serializer serializer;

    @Inject
    @Named("host")
    private String host;

    private final int batchSize;

    private final ExecutorService executorService = Executors.newCachedThreadPool(
        new ThreadFactoryBuilder().setNameFormat("ffwd-kafka-async-%d").build());

    public KafkaPluginSink(int batchSize) {
        this.batchSize = batchSize;
    }

    @Override
    public void init() {
    }

    @Override
    public void sendEvent(final Event event) {
        async.call(new Callable<Void>() {
            @Override
            public Void call() throws Exception {
                producer.send(eventConverter.toMessage(event));
                return null;
            }
        }, executorService);
    }

    @Override
    public void sendMetric(final Metric metric) {
        async.call(new Callable<Void>() {
            @Override
            public Void call() throws Exception {
                producer.send(metricConverter.toMessage(metric));
                return null;
            }
        }, executorService);
    }

    @Override
    public void sendBatch(final Batch batch) {
        send(toBatches(
            iteratorFor(batch.getPoints(), metric -> convertBatchMetric(batch, metric))));
    }

    private KeyedMessage<Integer, byte[]> convertBatchMetric(
        final Batch batch, final Batch.Point point
    ) throws Exception {
        final Map<String, String> allTags = new HashMap<>();
        allTags.putAll(batch.getCommonTags());
        allTags.putAll(point.getTags());

        final String host = allTags.remove("host");

        return metricConverter.toMessage(
            new Metric(point.getKey(), point.getValue(), new Date(point.getTimestamp()), host,
                ImmutableSet.of(), allTags, null));
    }

    @Override
    public AsyncFuture<Void> sendEvents(final Collection<Event> events) {
        return send(toBatches(iteratorFor(events, eventConverter)));
    }

    private AsyncFuture<Void> send(final Iterator<List<KeyedMessage<Integer, byte[]>>> batches) {
        final UUID id = UUID.randomUUID();

        return async.call(new Callable<Void>() {
            @Override
            public Void call() throws Exception {
                final List<Long> times = new ArrayList<>();

                log.info("{}: Start sending of batch", id);

                while (batches.hasNext()) {
                    final Stopwatch watch = Stopwatch.createStarted();
                    producer.send(batches.next());
                    times.add(watch.elapsed(TimeUnit.MILLISECONDS));
                }

                log.info("{}: Done sending batch (timings in ms: {})", id, times);
                return null;
            }
        }, executorService);
    }

    @Override
    public AsyncFuture<Void> sendMetrics(final Collection<Metric> metrics) {
        return send(toBatches(iteratorFor(metrics, metricConverter)));
    }

    @Override
    public AsyncFuture<Void> sendBatches(final Collection<Batch> batches) {
        final List<Iterator<KeyedMessage<Integer, byte[]>>> iterators = new ArrayList<>();
        batches.forEach(batch -> iterators.add(
            iteratorFor(batch.getPoints(), metric -> convertBatchMetric(batch, metric))));

        return send(toBatches(Iterators.concat(iterators.iterator())));
    }

    @Override
    public AsyncFuture<Void> start() {
        return async.resolved(null);
    }

    @Override
    public AsyncFuture<Void> stop() {
        return async.call(new Callable<Void>() {
            @Override
            public Void call() throws Exception {
                producer.close();
                return null;
            }
        }, executorService);
    }

    @Override
    public boolean isReady() {
        // TODO: how to check that producer is ready?
        return true;
    }

    /**
     * Convert the given message iterator to an iterator of batches of a specific size.
     * <p>
     * This is an attempt to reduce the required maximum amount of live memory required at a single
     * time.
     *
     * @param iterator Iterator to convert into batches.
     */
    private Iterator<List<KeyedMessage<Integer, byte[]>>> toBatches(
        final Iterator<KeyedMessage<Integer, byte[]>> iterator
    ) {
        return Iterators.partition(iterator, batchSize);
    }

    final Converter<Metric> metricConverter = new Converter<Metric>() {
        @Override
        public KeyedMessage<Integer, byte[]> toMessage(final Metric metric) throws Exception {
            final String topic = router.route(metric);
            final int partition = partitioner.partition(metric, host);
            final byte[] payload = serializer.serialize(metric);
            return new KeyedMessage<>(topic, partition, payload);
        }
    };

    final Converter<Event> eventConverter = new Converter<Event>() {
        @Override
        public KeyedMessage<Integer, byte[]> toMessage(final Event event) throws Exception {
            final String topic = router.route(event);
            final int partition = partitioner.partition(event);
            final byte[] payload = serializer.serialize(event);
            return new KeyedMessage<>(topic, partition, payload);
        }
    };

    final <T> Iterator<KeyedMessage<Integer, byte[]>> iteratorFor(
        Iterable<? extends T> iterable, final Converter<T> converter
    ) {
        final Iterator<? extends T> iterator = iterable.iterator();

        return new Iterator<KeyedMessage<Integer, byte[]>>() {
            @Override
            public boolean hasNext() {
                return iterator.hasNext();
            }

            @Override
            public KeyedMessage<Integer, byte[]> next() {
                try {
                    return converter.toMessage(iterator.next());
                } catch (final Exception e) {
                    throw new RuntimeException("Failed to produce next element", e);
                }
            }

            @Override
            public void remove() {
            }
        };
    }

    static interface Converter<T> {
        KeyedMessage<Integer, byte[]> toMessage(T object) throws Exception;
    }
}
