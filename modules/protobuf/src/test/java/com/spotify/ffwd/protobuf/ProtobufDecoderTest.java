/*-
 * -\-\-
 * FastForward API
 * --
 * Copyright (C) 2020 Spotify AB
 * --
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * -/-/-
 */

package com.spotify.ffwd.protobuf;

import static org.junit.Assert.assertEquals;


import com.google.protobuf.ByteString;
import com.spotify.ffwd.model.v2.Metric;
import com.spotify.ffwd.model.v2.Value;
import com.spotify.ffwd.protocol0.Protocol0;
import com.spotify.ffwd.protocol1.Protocol1;
import java.util.HashMap;
import java.util.Map;
import lombok.Data;
import org.junit.Before;
import org.junit.Test;

public class ProtobufDecoderTest {
    private final static String HOST ="host";
    private final static String KEY = "key";
    private final static double DVALUE = 0.098;
    private final static long TIME = 1234879475;
    private final static Map<String,String> TAGS = Map.of("tagKey", "tagVal");
    private final static Map<String,String> RESOURCE = Map.of();  // Place holder, not supported yet.
    private final static ByteString byteString = ByteString.copyFromUtf8("xxveagefanmvjdueancge");
    private Field field;
    private ProtobufDecoder decoder;


    @Before
    public void setup() {
        decoder = new ProtobufDecoder();
        field = new Field();
        field.hasValue = true;
        field.hasKey  = true;
        field.hasHost = true;
        field.hasTime = true;
        field.hasDistribution = false;
    }

    @Test
    public void testProtocol0Metric() {
        verifyMetric0(field);
    }

    @Test
    public void testProtocolOMetricWithoutHost(){
        field.hasHost = false;
        verifyMetric0(field);
    }

    @Test
    public void testProtocolOMetricWithoutKey(){
        field.hasKey = false;
        verifyMetric0(field);
    }

    @Test
    public void testProtocol1Metric() {
        verifyMetric1(field);
    }


    @Test
    public void testProtocol0MetricWithoutValue(){
        field.hasValue = false;
        verifyMetric0(field);
    }

    @Test
    public void testProtocol1MetricWithoutHost(){
        field.hasHost = false;
        verifyMetric1(field);
    }

    @Test
    public void testProtocol1MetricWithoutKey(){
        field.hasKey = false;
        verifyMetric1(field);
    }

    @Test
    public void testProtocol1MetricWithoutValue(){
        field.hasValue = false;
        verifyMetric1(field);
    }

    @Test
    public void testProtocol1MetricWithDistribution(){
        field.hasDistribution = true;
        verifyMetric1(field);
    }

    private void verifyMetric0(Field field){
        Protocol0.Message message = Protocol0.Message.newBuilder()
                .setMetric(createMetric0(field)).build();
        Metric expected = getExceptedV2Metric(field);
        assertEquals(expected, decoder.decodeMetric0(message));
    }


    private void verifyMetric1(Field field){
        Protocol1.Message message = Protocol1.Message.newBuilder()
                .setMetric(createMetric1(field)).build();
        Metric expected = getExceptedV2Metric(field);
        assertEquals(expected, decoder.decodeMetric1(message));
    }

    private Metric getExceptedV2Metric(Field field) {
        final Map<String, String> tags = new HashMap<>((TAGS));
        if (field.hasHost) tags.put("host", HOST);
        final String key = (field.hasKey) ? KEY:null;
        Value value = Value.DoubleValue.create(Double.NaN);
        if ( field.hasValue) {
             value = (!field.hasDistribution) ? Value.DoubleValue.create(DVALUE) :
                    Value.DistributionValue.create(byteString);
        }
        long time = (field.hasTime) ? TIME:0;
        return new Metric(key, value, time, tags, RESOURCE);
    }


    private Protocol1.Metric createMetric1(Field field) {
        Protocol1.Metric.Builder builder = Protocol1.Metric.newBuilder();
        if (field.hasHost) builder.setHost(HOST);
        if (field.hasKey) builder.setKey(KEY);
        if (field.hasTime) builder.setTime(TIME);
        if (field.hasValue) {
            Protocol1.Value  value = (field.hasDistribution) ?
                    Protocol1.Value.newBuilder().setDistributionValue(byteString).build():
                    Protocol1.Value.newBuilder().setDoubleValue(DVALUE).build();
            builder.setValue(value);
        }
        builder.addAttributes(0,
                Protocol1.Attribute.newBuilder()
                        .setKey("tagKey")
                        .setValue("tagVal")
                        .build());
        return builder.build();
    }


    private Protocol0.Metric createMetric0(Field field){
        Protocol0.Metric.Builder builder = Protocol0.Metric.newBuilder();
        if(field.hasHost) builder.setHost(HOST);
        if (field.hasKey) builder.setKey(KEY);
        if (field.hasValue) builder.setValue(DVALUE);
        if (field.hasTime) builder.setTime(TIME);
        builder.addAttributes(0,
                Protocol0.Attribute.newBuilder()
                .setKey("tagKey")
                .setValue("tagVal")
                .build());
        return builder.build();
    }


    @Data
    private static class Field {
        boolean hasKey;
        boolean hasValue;
        boolean hasHost;
        boolean hasTime;
        boolean hasDistribution;
    }


}
