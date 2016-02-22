package com.spotify.ffwd.filter;

import java.io.IOException;
import java.util.List;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.google.common.collect.ImmutableList;
import com.spotify.ffwd.model.Event;
import com.spotify.ffwd.model.Metric;

import lombok.Data;

@Data
public class OrFilter implements Filter {
    final List<Filter> terms;

    @Override
    public boolean matchesEvent(Event event) {
        for (final Filter f : terms) {
            if (f.matchesEvent(event)) {
                return true;
            }
        }

        return false;
    }

    @Override
    public boolean matchesMetric(Metric metric) {
        for (final Filter f : terms) {
            if (f.matchesMetric(metric)) {
                return true;
            }
        }

        return false;
    }

    public static class Deserializer implements FilterDeserializer.PartialDeserializer {
        @Override
        public Filter deserialize(JsonParser p, DeserializationContext ctx) throws IOException, JsonProcessingException {
            final ImmutableList.Builder<Filter> builder = ImmutableList.builder();

            while (p.nextToken() == JsonToken.START_ARRAY) {
                builder.add(p.readValueAs(Filter.class));
            }

            if (p.getCurrentToken() != JsonToken.END_ARRAY) {
                throw ctx.wrongTokenException(p, JsonToken.END_ARRAY, null);
            }

            final List<Filter> filters = builder.build();

            if (filters.isEmpty()) {
                return new FalseFilter();
            }

            if (filters.size() == 1) {
                return filters.iterator().next();
            }

            return new OrFilter(filters);
        }
    }
}