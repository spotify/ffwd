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

package com.spotify.ffwd.output;

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.TypeLiteral;
import com.google.inject.name.Names;
import com.spotify.ffwd.debug.DebugServer;
import com.spotify.ffwd.filter.Filter;
import com.spotify.ffwd.model.Batch;
import com.spotify.ffwd.model.Metric;
import com.spotify.ffwd.statistics.OutputManagerStatistics;
import eu.toolchain.async.AsyncFramework;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.runners.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class OutputManagerTest {
    private final static String HOST = "thehost";
    private final static String KEY = "thekey";

    @Mock
    private PluginSink sink;

    @Mock
    private AsyncFramework async;

    private Map<String, String> tags = new HashMap<>();
    private Map<String, String> tagsToResource = new HashMap<>();
    private Map<String, String> resource = new HashMap<>();
    private Set<String> riemannTags = ImmutableSet.of();
    private Set<String> skipTagsForKeys = ImmutableSet.of();
    private Boolean automaticHostTag = true;

    private String host = HOST;

    private long ttl = 0L;

    @Mock
    private DebugServer debugServer;

    @Mock
    private OutputManagerStatistics statistics;

    @Mock
    private Filter filter;

    private Metric m1;

    @Before
    public void setup() {
        tags.put("role", "abc");
        tagsToResource.put("foo", "bar");
        resource.put("gke_pod", "123");
        Mockito.doReturn(true).when(filter).matchesMetric(any());
        doNothing().when(sink).sendMetric(any(Metric.class));
        when(sink.isReady()).thenReturn(true);
        Map<String, String> testTags = new HashMap<>();
        testTags.put("tag1", "value1");
        m1 =
            new Metric(KEY, 42.0, new Date(), ImmutableSet.of(), testTags, ImmutableMap.of(), null);
    }

    public OutputManager createOutputManager() {
        final List<Module> modules = Lists.newArrayList();

        modules.add(new AbstractModule() {
            @Override
            protected void configure() {
                bind(new TypeLiteral<List<PluginSink>>() {
                }).toInstance(ImmutableList.of(sink));
                bind(AsyncFramework.class).toInstance(async);
                bind(new TypeLiteral<Map<String, String>>() {
                }).annotatedWith(Names.named("tags")).toInstance(tags);
                bind(new TypeLiteral<Map<String, String>>() {
                }).annotatedWith(Names.named("tagsToResource")).toInstance(tagsToResource);
                bind(new TypeLiteral<Map<String, String>>() {
                }).annotatedWith(Names.named("resource")).toInstance(resource);
                bind(new TypeLiteral<Set<String>>() {
                }).annotatedWith(Names.named("riemannTags")).toInstance(riemannTags);
                bind(new TypeLiteral<Set<String>>() {
                }).annotatedWith(Names.named("skipTagsForKeys")).toInstance(skipTagsForKeys);
                bind(Boolean.class)
                    .annotatedWith(Names.named("automaticHostTag"))
                    .toInstance(automaticHostTag);
                bind(String.class).annotatedWith(Names.named("host")).toInstance(host);
                bind(long.class).annotatedWith(Names.named("ttl")).toInstance(ttl);
                bind(DebugServer.class).toInstance(debugServer);
                bind(OutputManagerStatistics.class).toInstance(statistics);
                bind(Filter.class).toInstance(filter);
                bind(OutputManager.class).to(CoreOutputManager.class);
            }
        });

        final Injector injector = Guice.createInjector(modules);

        return injector.getInstance(OutputManager.class);
    }

    @Test
    public void testAutomaticHostEnabled() {
        automaticHostTag = true;
        Map<String, String> expectedTags = new HashMap<>();
        expectedTags.putAll(m1.getTags());
        expectedTags.putAll(tags);
        expectedTags.put("host", host);

        assertEquals(
            new Metric(m1.getKey(), m1.getValue(), m1.getTime(), m1.getRiemannTags(), expectedTags,
                m1.getResource(), m1.getProc()), sendAndCaptureMetric(m1));
    }

    @Test
    public void testAutomaticHostDisabled() {
        automaticHostTag = false;
        Map<String, String> expectedTags = new HashMap<>();
        expectedTags.putAll(m1.getTags());
        expectedTags.putAll(tags);

        assertEquals(
            new Metric(m1.getKey(), m1.getValue(), m1.getTime(), m1.getRiemannTags(), expectedTags,
                m1.getResource(), m1.getProc()), sendAndCaptureMetric(m1));
    }

    @Test
    public void testSkipTagsForKeys() {
        skipTagsForKeys = ImmutableSet.of(KEY);

        assertEquals(
            new Metric(m1.getKey(), m1.getValue(), m1.getTime(), m1.getRiemannTags(), m1.getTags(),
                m1.getResource(), m1.getProc()), sendAndCaptureMetric(m1));
    }

    @Test
    public void testTagsToResource() {
        Map<String, String> m2Tags = new HashMap<>();
        m2Tags.put("role", "abc");
        m2Tags.put("foo", "fooval");
        Metric m2 = new Metric(m1.getKey(), m1.getValue(), m1.getTime(), m1.getRiemannTags(), m2Tags,
        m1.getResource(), m1.getProc());

        assertEquals(
            new Metric(m1.getKey(), m1.getValue(), m1.getTime(), m1.getRiemannTags(), ImmutableMap.of("host","thehost","role","abc"),
                ImmutableMap.of("bar","fooval","gke_pod","123"), m1.getProc()), sendAndCaptureMetric(m2));
    }

    @Test
    public void testTagsToResourceForBatches() {
        Map<String, String> m2Tags = new HashMap<>();
        m2Tags.put("role", "abc");
        m2Tags.put("foo", "fooval");

        final Batch.Point point = new Batch.Point(m1.getKey(), m2Tags, m1.getResource(), m1.getValue(), m1.getTime().getTime());
        final Batch batch = new Batch(Maps.newHashMap(), Maps.newHashMap(), Collections.singletonList(point));

        final Batch.Point expected = new Batch.Point(m1.getKey(), ImmutableMap.of("role","abc"),
            ImmutableMap.of("bar","fooval"), m1.getValue(), m1.getTime().getTime());

        assertEquals(expected, sendAndCaptureBatch(batch).getPoints().get(0));
    }

    private Metric sendAndCaptureMetric(Metric metric) {
        final OutputManager outputManager = createOutputManager();
        ArgumentCaptor<Metric> captor = ArgumentCaptor.forClass(Metric.class);
        outputManager.sendMetric(metric);
        verify(sink, times(1)).sendMetric(captor.capture());
        return captor.getValue();
    }

    private Batch sendAndCaptureBatch(Batch batch) {
        final OutputManager outputManager = createOutputManager();
        ArgumentCaptor<Batch> captor = ArgumentCaptor.forClass(Batch.class);
        outputManager.sendBatch(batch);
        verify(sink, times(1)).sendBatch(captor.capture());
        return captor.getValue();
    }
}
