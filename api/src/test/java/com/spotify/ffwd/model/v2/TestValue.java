/*-
 * -\-\-
 * FastForward API
 * --
 * Copyright (C) 2016 - 2019 Spotify AB
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

package com.spotify.ffwd.model.v2;

import static junit.framework.TestCase.assertEquals;


import com.google.protobuf.ByteString;
import org.junit.Test;


public class TestValue {
      @Test
      public void doNothingYet(){}

    private final static double DOUBLE_VAL = 0.022;
    private final static ByteString BYTE_STRING =
            ByteString.copyFromUtf8("0s0s0s0s0s0s0s0s");
    @Test
    public void testCreateDoubleValue(){
        Value.DoubleValue val = Value.DoubleValue.create(DOUBLE_VAL);
        assertEquals(DOUBLE_VAL,val.getValue());
    }

    @Test
   public void testCreateDistributionValue() {
        Value.DistributionValue val = Value.DistributionValue.create(BYTE_STRING);
        assertEquals(BYTE_STRING, val.getValue());
    }
}
