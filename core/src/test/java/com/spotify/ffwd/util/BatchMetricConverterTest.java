/*-
 * -\-\-
 * FastForward API
 * --
 * Copyright (C) 2020 Spotify AB
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

package com.spotify.ffwd.util;

import static org.junit.Assert.assertEquals;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.spotify.ffwd.model.v2.Batch;
import com.spotify.ffwd.model.v2.Batch.Point;
import com.spotify.ffwd.model.v2.Metric;
import com.spotify.ffwd.model.v2.Value;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;


public class BatchMetricConverterTest {
    public Map<String, String> baseTags;
    public Map<String, String> baseResources;
    public Map<String, String> commonTags;
    public Map<String, String> commonResources;
    public Batch.Point point1;

    @Before
    public void setUp() throws Exception {
        baseTags = ImmutableMap.of("tag1", "v1", "tag2", "v2");
        baseResources = ImmutableMap.of("resource1", "v1", "resource2", "v2");
        commonTags = ImmutableMap.of("ctag1", "1", "ctag2", "2", "ctag3", "3");
        commonResources = ImmutableMap.of("cResource1", "1", "cResource2", "2");
        point1 = Batch.Point.create("test", Optional.of(baseTags), Optional.of(baseResources), Value.DoubleValue.create(5), 0);

    }

    @Test
    public void convertBatchMetric() throws Exception {
        final ImmutableList<Point> points = ImmutableList.of(point1);
        final Batch batch = Batch.create(Optional.of(commonTags), Optional.of(commonResources), points);
        final Metric metric = BatchMetricConverter.convertBatchMetric(batch, points.get(0));

        final Map<String, String> resultingTags = metric.getTags();
        final Map<String, String> resultingResources = metric.getResource();

        final ImmutableMap<Object, Object> targetTags = ImmutableMap.builder().putAll(baseTags).putAll(commonTags).build();
        final ImmutableMap<Object, Object> targetResources = ImmutableMap.builder().putAll(baseResources).putAll(commonResources).build();

        assertEquals(targetTags, resultingTags);
        assertEquals(targetResources, resultingResources);
    }

    @Test
    public void testConvertBatchesToMetrics() throws Exception {
        final Batch batch1 = Batch.create(Optional.of(commonTags), Optional.of(commonResources), ImmutableList.of(point1));
        final Batch batch2 = Batch.create(Optional.of(commonTags), Optional.of(commonResources), ImmutableList.of(point1));
        final List<Metric> results = BatchMetricConverter.convertBatchesToMetrics(ImmutableList.of(batch1, batch2));
        assertEquals(2, results.size());
    }

    @Test
    public void convertBatchesToMetricsEmptyPoints() throws Exception {
        final Batch batch1 = Batch.create(Optional.of(commonTags), Optional.of(commonResources), ImmutableList.of());
        final Batch batch2 = Batch.create(Optional.of(commonTags), Optional.of(commonResources), ImmutableList.of());
        final List<Metric> results = BatchMetricConverter.convertBatchesToMetrics(ImmutableList.of(batch1, batch2));
        assertEquals(0, results.size());
    }

    @Test
    public void convertBatchesToMetricsEmptyBatch() throws Exception {
        final List<Metric> results = BatchMetricConverter.convertBatchesToMetrics(ImmutableList.of());
        assertEquals(0, results.size());
    }
}