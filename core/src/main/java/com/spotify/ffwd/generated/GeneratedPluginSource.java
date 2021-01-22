/*-
 * -\-\-
 * FastForward Core
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

package com.spotify.ffwd.generated;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import com.spotify.ffwd.input.InputManager;
import com.spotify.ffwd.input.PluginSource;
import com.spotify.ffwd.model.v2.Metric;
import com.spotify.ffwd.model.v2.Value;
import eu.toolchain.async.AsyncFramework;
import eu.toolchain.async.AsyncFuture;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class GeneratedPluginSource implements PluginSource {
    @Inject
    private AsyncFramework async;
    @Inject
    private InputManager input;

    private volatile AsyncFuture<Void> task;
    private volatile List<Metric> metrics;
    private volatile boolean stopped = false;

    private final Random random = new Random();
    private final ExecutorService single = Executors.newSingleThreadExecutor();

    private final boolean sameHost;
    private final int count;

    GeneratedPluginSource(boolean sameHost, int count) {
        this.sameHost = sameHost;
        this.count = count;
    }

    @Override
    public void init() {
        task = async.call(() -> {
            generate();
            return null;
        }, single);
    }

    @Override
    public AsyncFuture<Void> start() {
        return async.call(() -> {
            metrics = generateMetrics();
            return null;
        });
    }

    private List<Metric> generateMetrics() {
        final List<Metric> metrics = new ArrayList<>(count);

        for (int i = 0; i < count; i++) {
            final String key = "generated";
            final Value value = Value.DoubleValue.create(0.42 * i);
            final String host = generateHost(i);
            final Map<String, String> tags = ImmutableMap.of(
                    "what", "metric-" + i,
                    "host", host
            );
            final Map<String, String> resource = ImmutableMap.of();

            metrics.add(new Metric(key, value, System.currentTimeMillis(), tags, resource));
        }

        return metrics;
    }

    private String generateHost(int i) {
        if (sameHost) {
            return "host";
        }

        return "host" + i;
    }

    @Override
    public AsyncFuture<Void> stop() {
        stopped = true;
        return task;
    }

    private void generate() throws InterruptedException {
        while (!stopped) {
            input.receiveMetric(randomMetric());
            Thread.sleep(10);
        }
    }

    private Metric randomMetric() {
        return metrics.get(random.nextInt(metrics.size()));
    }
}
