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
public class MatchTag implements Filter {
    private final String key;
    private final String value;

    @Override
    public boolean matchesEvent(final Event event) {
        final String value = event.getTags().get(key);

        if (value == null) {
            return false;
        }

        return value.equals(this.value);
    }

    @Override
    public boolean matchesMetric(final Metric metric) {
        final String value = metric.getTags().get(key);

        if (value == null) {
            return false;
        }

        return value.equals(this.value);
    }

    public static class Deserializer implements FilterDeserializer.PartialDeserializer {
        @Override
        public Filter deserialize(JsonParser p, DeserializationContext ctx) throws IOException, JsonProcessingException {
            final String key = p.nextTextValue();
            final String value = p.nextTextValue();

            if (p.nextToken() != JsonToken.END_ARRAY) {
                throw ctx.wrongTokenException(p, JsonToken.END_ARRAY, null);
            }

            return new MatchTag(key, value);
        }
    }
}