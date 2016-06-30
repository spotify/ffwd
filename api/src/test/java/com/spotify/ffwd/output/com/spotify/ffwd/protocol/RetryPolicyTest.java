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
package com.spotify.ffwd.output.com.spotify.ffwd.protocol;

import static org.junit.Assert.assertEquals;

import com.spotify.ffwd.protocol.RetryPolicy;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;


/**
 * Created by parmus on 29-01-16.
 */
@RunWith(MockitoJUnitRunner.class)
public class RetryPolicyTest {
    @Test
    public void testlinearBackoff(){
        final long initial = 2L;
        final long max = initial * 5;

        RetryPolicy.Linear policy = new RetryPolicy.Linear(initial, max);

        // The delay should ramp up regularly
        for(int i=0; i<5; i++){
            assertEquals(initial * (i + 1), policy.delay(i));
        }

        // Here the delay should be capped by the max value
        assertEquals(max, policy.delay(5));

        // Test for potential overflow issues
        assertEquals(max, policy.delay(Integer.MAX_VALUE));
    }

    @Test
    public void testExponentialBackoff(){
        final long initial = 2L;
        final long max = ((long) Math.pow(2, 5) * initial) - 1;

        RetryPolicy.Exponential policy = new RetryPolicy.Exponential(initial, max);

        // The delay should ramp up regularly
        for(int i=0; i<5; i++){
            assertEquals(initial * (long) Math.pow(2, i), policy.delay(i));
        }

        // Here the delay should be capped by the max value
        assertEquals(max, policy.delay(5));

        // Test for potential overflow issues
        assertEquals(max, policy.delay(Long.SIZE));
        assertEquals(max, policy.delay(Integer.MAX_VALUE));
    }
}
