package com.spotify.ffwd.filter;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.google.common.collect.ImmutableList;
import com.spotify.ffwd.model.Event;
import com.spotify.ffwd.model.Metric;
import lombok.Data;

import java.io.IOException;
import java.util.List;

@Data
public class TypeFilter implements Filter {
    final String type;

    @Override
    public boolean matchesEvent(Event event) {
        return "event".equals(type);
    }

    @Override
    public boolean matchesMetric(Metric metric) {
        return "metric".equals(type);
    }

    public static class Deserializer implements FilterDeserializer.PartialDeserializer {
        @Override
        public Filter deserialize(JsonParser p, DeserializationContext ctx) throws IOException, JsonProcessingException {
            final String type = p.nextTextValue();

            if (p.nextToken() != JsonToken.END_ARRAY) {
                throw ctx.wrongTokenException(p, JsonToken.END_ARRAY, null);
            }

            return new TypeFilter(type);
        }
    }
}