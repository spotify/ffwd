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
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.TypeLiteral;
import com.google.inject.name.Names;
import com.spotify.ffwd.debug.DebugServer;
import com.spotify.ffwd.filter.Filter;
import com.spotify.ffwd.model.Batch;
import com.spotify.ffwd.model.Event;
import com.spotify.ffwd.model.Metric;
import com.spotify.ffwd.statistics.OutputManagerStatistics;
import eu.toolchain.async.AsyncFramework;
import eu.toolchain.async.AsyncFuture;
import java.util.ArrayList;
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

    private Map<String, String> tags = ImmutableMap.of("role", "abc");
    private Map<String, String> resource = ImmutableMap.of("gke_pod", "123");
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

    private final Metric m1 =
        new Metric(KEY, 42.0, new Date(), ImmutableSet.of(), ImmutableMap.of("tag1", "value1"),
            ImmutableMap.of(), null);

    @Before
    public void setup() {
        Mockito.doReturn(true).when(filter).matchesMetric(any());
        doNothing().when(sink).sendMetric(any(Metric.class));
        when(sink.isReady()).thenReturn(true);
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
                m1.getResource(), m1.getProc()), sendAndCaptureMetric());
    }

    @Test
    public void testAutomaticHostDisabled() {
        automaticHostTag = false;
        Map<String, String> expectedTags = new HashMap<>();
        expectedTags.putAll(m1.getTags());
        expectedTags.putAll(tags);

        assertEquals(
            new Metric(m1.getKey(), m1.getValue(), m1.getTime(), m1.getRiemannTags(), expectedTags,
                m1.getResource(), m1.getProc()), sendAndCaptureMetric());
    }

    @Test
    public void testSkipTagsForKeys() {
        skipTagsForKeys = ImmutableSet.of(KEY);

        assertEquals(
            new Metric(m1.getKey(), m1.getValue(), m1.getTime(), m1.getRiemannTags(), m1.getTags(),
                m1.getResource(), m1.getProc()), sendAndCaptureMetric());
    }

    private Metric sendAndCaptureMetric(){
        final OutputManager outputManager = createOutputManager();
        ArgumentCaptor<Metric> captor = ArgumentCaptor.forClass(Metric.class);
        outputManager.sendMetric(m1);
        verify(sink, times(1)).sendMetric(captor.capture());
        return captor.getValue();
    }
}
