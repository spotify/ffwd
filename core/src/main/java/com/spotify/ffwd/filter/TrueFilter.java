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
public class TrueFilter implements Filter {
    @Override
    public boolean matchesEvent(Event event) {
        return true;
    }

    @Override
    public boolean matchesMetric(Metric metric) {
        return true;
    }

    public static class Deserializer implements FilterDeserializer.PartialDeserializer {
        @Override
        public Filter deserialize(JsonParser p, DeserializationContext ctx) throws IOException, JsonProcessingException {
            if (p.nextToken() != JsonToken.END_ARRAY) {
                throw ctx.wrongTokenException(p, JsonToken.END_ARRAY, null);
            }

            return new TrueFilter();
        }
    }
}