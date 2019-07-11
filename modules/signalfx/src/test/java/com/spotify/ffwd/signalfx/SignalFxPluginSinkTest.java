/*-
 * -\-\-
 * FastForward SignalFx Module
 * --
 * Copyright (C) 2016 - 2019 Spotify AB
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

package com.spotify.ffwd.signalfx;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;
import com.signalfx.metrics.flush.AggregateMetricSender;
import com.signalfx.metrics.protobuf.SignalFxProtocolBuffers.DataPoint;
import com.signalfx.metrics.protobuf.SignalFxProtocolBuffers.Dimension;
import com.spotify.ffwd.model.Metric;
import eu.toolchain.async.AsyncFramework;
import eu.toolchain.async.TinyAsync;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Supplier;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class SignalFxPluginSinkTest {

    private SignalFxPluginSink sink;

    private AsyncFramework async;
    private Supplier<AggregateMetricSender> senderSupplier;

    private final int threadCount = Runtime.getRuntime().availableProcessors();

    private final ExecutorService executor = Executors.newFixedThreadPool(threadCount);

    @Mock
    private AggregateMetricSender sender;

    @Mock
    private AggregateMetricSender.Session mockSession;

    private final String METRIC_KEY_NOT_EXCEEDING = "signalfx_test";
    private final String METRIC_KEY_EXCEEDING =
        "signalfx_test_8217398173982139821739872198372198372198379djsfjjksdfhkdsjhfkjdsh2187392187392183798213982173982139219382198329379218739821739821723982179871298379128739281739827398273747874657678478384787498983598984759837598439583498734985793487";
    private final String WHAT = "error-reply-ratio";
    private final String TAG_VAL_NOT_EXCEEDING = "awsus";
    private final String TAG_VAL_EXCEEDING =
        "awsuskjdfkjsfvlsdjldskfjsdlkfdskfds;kfmdkslfmsdkfjdlsjflksvmjnskjdkjsdkdbkdbv " +
            "dklmlskkmxvmkmvdkmvkclx;d;skf;smvkvmdskmvdsvmdsvxmv kxmvlxmldksdmlksdmvdlsjcxzm" +
            ".xmldkm;dvsmv;dskmv;dslvmlx,xmlkvxmd;md;mdknvldnvldnsvlndlsnlnvlcnvlkndlvndlvnjslnvc";

    private static final int CHAR_LIMIT = 256;

    @Before
    public void setup() {
        sink = new SignalFxPluginSink();
        async = TinyAsync.builder().executor(executor).build();
        senderSupplier = () -> sender;
        sink.async = async;
        sink.senderSupplier = senderSupplier;
    }

    @Test
    public void sendMetricNotExceedCharLim() throws ExecutionException, InterruptedException {
        Metric metric =
            new Metric(METRIC_KEY_NOT_EXCEEDING, 1278, new Date(), Sets.newHashSet(),
                ImmutableMap.of("what", WHAT, "pod", TAG_VAL_NOT_EXCEEDING),
                new HashMap<>(), "test_proc");
        final List<Metric> metricsList = Collections.singletonList(metric);

        ArgumentCaptor<DataPoint> captor = ArgumentCaptor.forClass(DataPoint.class);
        when(sender.createSession()).thenReturn(mockSession);
        when(mockSession.setDatapoint(captor.capture())).thenReturn(mockSession);

        sink.start();
        Future<Void> future = sink.sendMetrics(metricsList);
        future.get();
        sink.stop();
        List<DataPoint> points = captor.getAllValues();
        assertEquals(1, points.size());
        DataPoint point = points.get(0);
        assertEquals(METRIC_KEY_NOT_EXCEEDING.length() + 1 + WHAT.length(),
            point.getMetric().length());
        for (Dimension d : point.getDimensionsList()) {
            String dimVal = d.getValue();
            assertTrue(dimVal.length() <= CHAR_LIMIT);
        }
    }

    @Test
    public void sendMetricExceedCharLim() throws InterruptedException, ExecutionException {
        Metric metric = new Metric(METRIC_KEY_EXCEEDING, 1278, new Date(), Sets.newHashSet(),
            ImmutableMap.of("what", WHAT, "pod", TAG_VAL_EXCEEDING),
            new HashMap<>(), "test_proc");
        final List<Metric> metricsList = Collections.singletonList(metric);

        ArgumentCaptor<DataPoint> captor = ArgumentCaptor.forClass(DataPoint.class);
        when(sender.createSession()).thenReturn(mockSession);
        when(mockSession.setDatapoint(captor.capture())).thenReturn(mockSession);

        sink.start();
        Future<Void> future = sink.sendMetrics(metricsList);
        future.get();
        sink.stop();
        List<DataPoint> points = captor.getAllValues();
        assertEquals(1, points.size());
        DataPoint point = points.get(0);
        assertEquals(CHAR_LIMIT, point.getMetric().length());
        for (Dimension d : point.getDimensionsList()) {
            String dimVal = d.getValue();
            assertTrue(dimVal.length() <= CHAR_LIMIT);
        }
    }
}
