/*
 * Copyright 2013-2014 Spotify AB. All rights reserved.
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
