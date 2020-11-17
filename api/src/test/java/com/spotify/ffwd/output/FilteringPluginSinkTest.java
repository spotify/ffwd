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

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.internal.verification.VerificationModeFactory.times;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.spotify.ffwd.filter.MatchKey;
import com.spotify.ffwd.filter.NotFilter;
import com.spotify.ffwd.filter.TrueFilter;
import com.spotify.ffwd.model.v2.Batch;
import com.spotify.ffwd.model.v2.Metric;
import com.spotify.ffwd.model.v2.Value;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class FilteringPluginSinkTest {

    @Mock
    private BatchingPluginSink childSink;
    private FilteringPluginSink sink;

    private Metric metric;
    private Batch batch;

    @Before
    public void setup() {
        sink = spy(new FilteringPluginSink(new TrueFilter()));
        sink.sink = childSink;
        metric = new Metric("test_metric", Value.DoubleValue.create(1278), System.currentTimeMillis(),
            ImmutableMap.of("what", "stats", "pod", "gew1"), ImmutableMap.of());
        batch = new Batch(ImmutableMap.of("what", "stats", "pod", "gew1"), ImmutableMap.of(),
            ImmutableList.of());
    }

    @Test
    public void testSendMetricTrueFilter() {
        doNothing().when(childSink).sendMetric(metric);
        ArgumentCaptor<Metric> captor = ArgumentCaptor.forClass(Metric.class);

        sink.sendMetric(metric);
        verify(childSink, times(1)).sendMetric(captor.capture());

        Metric sentMetric = captor.getValue();
        assertEquals("test_metric", sentMetric.getKey());
    }

    @Test
    public void testSendMetricNotFilter() {
        sink.filter = new NotFilter(new MatchKey(metric.getKey()));
        doNothing().when(childSink).sendMetric(metric);
        ArgumentCaptor<Metric> captor = ArgumentCaptor.forClass(Metric.class);

        sink.sendMetric(metric);
        verify(childSink, times(0)).sendMetric(captor.capture());
    }

    @Test
    public void testSendBatchTrueFilter() {
        sink.filter = new TrueFilter();
        doNothing().when(childSink).sendBatch(batch);
        ArgumentCaptor<Batch> captor = ArgumentCaptor.forClass(Batch.class);

        sink.sendBatch(batch);
        verify(childSink, times(1)).sendBatch(captor.capture());

        Batch sentBatch = captor.getValue();
        assertEquals("gew1", sentBatch.getCommonTags().get("pod"));
        assertEquals("stats", sentBatch.getCommonTags().get("what"));
    }
}
