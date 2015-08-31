package com.spotify.ffwd.filter;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.spotify.ffwd.model.Event;
import com.spotify.ffwd.model.Metric;

import lombok.Data;

@Data
public class NotFilter implements Filter {
    final Filter filter;

    @Override
    public boolean matchesEvent(Event event) {
        return !filter.matchesEvent(event);
    }

    @Override
    public boolean matchesMetric(Metric metric) {
        return !filter.matchesMetric(metric);
    }

    public static class Deserializer implements FilterDeserializer.PartialDeserializer {
        @Override
        public Filter deserialize(JsonParser p, DeserializationContext ctx)
                throws IOException, JsonProcessingException {
            if (p.nextToken() != JsonToken.START_ARRAY) {
                throw ctx.wrongTokenException(p, JsonToken.START_ARRAY, null);
            }

            final Filter filter = p.readValueAs(Filter.class);

            if (p.nextToken() != JsonToken.END_ARRAY) {
                throw ctx.wrongTokenException(p, JsonToken.END_ARRAY, null);
            }

            return new NotFilter(filter);
        }
    }
}