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
public class MatchKey implements Filter {
    private final String value;

    @Override
    public boolean matchesEvent(Event event) {
        return event.getKey().equals(value);
    }

    @Override
    public boolean matchesMetric(Metric metric) {
        return metric.getKey().equals(value);
    }

    public static class Deserializer implements FilterDeserializer.PartialDeserializer {
        @Override
        public Filter deserialize(JsonParser p, DeserializationContext ctx)
                throws IOException, JsonProcessingException {
            final String key = p.nextTextValue();

            if (p.nextToken() != JsonToken.END_ARRAY) {
                throw ctx.wrongTokenException(p, JsonToken.END_ARRAY, null);
            }

            return new MatchKey(key);
        }
    }
}