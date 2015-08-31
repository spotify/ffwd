package com.spotify.ffwd.filter;

import static org.junit.Assert.assertEquals;

import java.util.HashMap;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;

public class FilterDeserializerTest {
    ObjectMapper mapper;

    @Before
    public void setup() {
        final SimpleModule m = new SimpleModule();

        final Map<String, FilterDeserializer.PartialDeserializer> filters = new HashMap<>();
        filters.put("and", new AndFilter.Deserializer());
        filters.put("or", new OrFilter.Deserializer());
        filters.put("key", new MatchKey.Deserializer());
        filters.put("not", new NotFilter.Deserializer());

        m.addDeserializer(Filter.class, new FilterDeserializer(filters));

        mapper = new ObjectMapper();
        mapper.registerModule(m);
    }

    @Test
    public void testArray() throws Exception {
        assertEquals(new MatchKey("value"), mapper.readValue("[\"key\", \"value\"]", Filter.class));
        assertEquals(new FalseFilter(), mapper.readValue("[\"or\"]", Filter.class));
        assertEquals(new MatchKey("value"), mapper.readValue("[\"or\", [\"key\", \"value\"]]", Filter.class));
    }
}