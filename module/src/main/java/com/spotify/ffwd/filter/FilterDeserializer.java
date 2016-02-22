package com.spotify.ffwd.filter;

import java.io.IOException;
import java.util.Map;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

import lombok.RequiredArgsConstructor;

/**
 * A filter deserializer.
 *
 * Filters are represented as array where the first element in the array is the filter identifier, and the rest are
 * arguments to that filter.
 *
 * @author udoprog
 */
@RequiredArgsConstructor
public class FilterDeserializer extends JsonDeserializer<Filter> {
    final Map<String, PartialDeserializer> suppliers;

    @Override
    public Filter deserialize(JsonParser p, DeserializationContext ctx) throws IOException, JsonProcessingException {
        if (p.getCurrentToken() != JsonToken.START_ARRAY) {
            throw ctx.wrongTokenException(p, JsonToken.START_ARRAY, null);
        }

        final String id = p.nextTextValue();

        final PartialDeserializer d = suppliers.get(id);

        if (d == null) {
            throw ctx.weirdStringException(id, Filter.class, String.format("Expected one of %s", suppliers.keySet()));
        }

        final Filter instance = d.deserialize(p, ctx);

        if (p.getCurrentToken() != JsonToken.END_ARRAY) {
            throw ctx.wrongTokenException(p, JsonToken.END_ARRAY, null);
        }

        return instance;
    }

    public static interface PartialDeserializer {
        public Filter deserialize(JsonParser p, DeserializationContext ctx) throws IOException, JsonProcessingException;
    }
}