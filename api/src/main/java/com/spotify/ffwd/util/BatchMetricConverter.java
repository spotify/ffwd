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

import com.google.common.collect.ImmutableSet;
import com.spotify.ffwd.model.Batch;
import com.spotify.ffwd.model.Metric;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BatchMetricConverter {
    public static Metric convertBatchMetric(final Batch batch, final Batch.Point point) {
        final Map<String, String> allTags = new HashMap<>(batch.getCommonTags());
        allTags.putAll(point.getTags());

        final Map<String, String> allResource = new HashMap<>(batch.getCommonResource());
        allResource.putAll(point.getResource());

        return new Metric(point.getKey(), point.getValue(), new Date(point.getTimestamp()),
            ImmutableSet.of(), allTags, allResource, null);
    }

    public static List<Metric> convertBatchesToMetrics(final Collection<Batch> batches) {
        final List<Metric> metrics = new ArrayList<>();

        batches.forEach(batch -> batch.getPoints().forEach(point -> {
            metrics.add(convertBatchMetric(batch, point));
        }));
        return metrics;
    }
}
