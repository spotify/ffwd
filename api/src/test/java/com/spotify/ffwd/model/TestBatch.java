package com.spotify.ffwd.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.io.Resources;
import com.spotify.ffwd.Mappers;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.Before;
import org.junit.Test;

public class TestBatch {
    private ObjectMapper mapper;

    @Before
    public void setUp() {
        this.mapper = Mappers.setupApplicationJson();
    }

    @Test
    public void testBatch0() throws Exception {
        final String value = readResources("TestBatch.0.json");
        mapper.readValue(value, Batch.class);
    }

    @Test
    public void testBatch1() throws Exception {
        final String value = readResources("TestBatch.1.json");
        mapper.readValue(value, Batch.class);
    }

    @Test
    public void testBatchWithResource() throws Exception {
        final String value = readResources("TestBatch.WithResource.json");
        mapper.readValue(value, Batch.class);
    }

    @Test(expected = Exception.class)
    public void testBatchBad() throws Exception {
        final String value = readResources("TestBatch.bad.json");
        mapper.readValue(value, Batch.class);
    }

    private String readResources(final String name) throws IOException {
        return Resources.toString(Resources.getResource(name), StandardCharsets.UTF_8);
    }
}
