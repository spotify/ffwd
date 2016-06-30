/**
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
 **/
package com.spotify.ffwd.filter;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import lombok.RequiredArgsConstructor;

import java.io.IOException;
import java.util.Map;

/**
 * A filter deserializer.
 * <p>
 * Filters are represented as array where the first element in the array is the filter identifier,
 * and the rest are arguments to that filter.
 *
 * @author udoprog
 */
@RequiredArgsConstructor
public class FilterDeserializer extends JsonDeserializer<Filter> {
    final Map<String, PartialDeserializer> suppliers;

    @Override
    public Filter deserialize(JsonParser p, DeserializationContext ctx)
        throws IOException, JsonProcessingException {
        if (p.getCurrentToken() != JsonToken.START_ARRAY) {
            throw ctx.wrongTokenException(p, JsonToken.START_ARRAY, null);
        }

        final String id = p.nextTextValue();

        final PartialDeserializer d = suppliers.get(id);

        if (d == null) {
            throw ctx.weirdStringException(id, Filter.class,
                String.format("Expected one of %s", suppliers.keySet()));
        }

        final Filter instance = d.deserialize(p, ctx);

        if (p.getCurrentToken() != JsonToken.END_ARRAY) {
            throw ctx.wrongTokenException(p, JsonToken.END_ARRAY, null);
        }

        return instance;
    }

    public static interface PartialDeserializer {
        public Filter deserialize(JsonParser p, DeserializationContext ctx)
            throws IOException, JsonProcessingException;
    }
}
