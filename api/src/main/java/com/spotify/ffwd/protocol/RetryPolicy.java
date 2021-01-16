/*-
 * -\-\-
 * FastForward API
 * --
 * Copyright (C) 2016 - 2018 Spotify AB
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

package com.spotify.ffwd.protocol;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeName;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(RetryPolicy.Constant.class),
    @JsonSubTypes.Type(RetryPolicy.Exponential.class), @JsonSubTypes.Type(RetryPolicy.Linear.class)
})
public interface RetryPolicy {

  /**
   * Get the required delay in milliseconds.
   * <p>
   * A value of {@code 0} or less will cause no delay.
   *
   * @param attempt A zero-based number indicating the current attempt.
   */
  long delay(int attempt);

  /**
   * A retry policy with a constant delay.
   */
  @JsonTypeName("constant")
  class Constant implements RetryPolicy {

    public static final long DEFAULT_VALUE =
        TimeUnit.MILLISECONDS.convert(10, TimeUnit.SECONDS);

    private final long value;

    @JsonCreator
    public Constant(@JsonProperty("value") Long value) {
      this.value = Optional.ofNullable(value).orElse(DEFAULT_VALUE);
    }

    @Override
    public long delay(int attempt) {
      return value;
    }
  }

  /**
   * A retry policy that increases delay exponentially.
   */
  @JsonTypeName("exponential")
  class Exponential implements RetryPolicy {

    public static final long DEFAULT_INITIAL =
        TimeUnit.MILLISECONDS.convert(2, TimeUnit.SECONDS);
    public static final long DEFAULT_MAX = TimeUnit.MILLISECONDS.convert(5, TimeUnit.MINUTES);

    private final long initial;
    private final long max;
    private final int maxAttempt;

    @JsonCreator
    public Exponential(@JsonProperty("initial") Long initial, @JsonProperty("max") Long max) {
      this.initial = Optional.ofNullable(initial).orElse(DEFAULT_INITIAL);
      this.max = Optional.ofNullable(max).orElse(DEFAULT_MAX);
      this.maxAttempt =
          new Double(Math.floor(Math.log(this.max / this.initial) / Math.log(2))).intValue();
    }

    public Exponential() {
      this(null, null);
    }

    @Override
    public long delay(int attempt) {
      if (attempt > maxAttempt) {
        return max;
      }
      return initial * (long) Math.pow(2, attempt);
    }
  }

  /**
   * A retry policy that increases delay linearly.
   */
  @JsonTypeName("linear")
  class Linear implements RetryPolicy {

    public static final long DEFAULT_VALUE = TimeUnit.MILLISECONDS.convert(2, TimeUnit.SECONDS);
    public static final long DEFAULT_MAX = TimeUnit.MILLISECONDS.convert(5, TimeUnit.MINUTES);

    private final long value;
    private final long max;
    private final int maxAttempt;

    @JsonCreator
    public Linear(@JsonProperty("value") Long value, @JsonProperty("max") Long max) {
      this.value = Optional.ofNullable(value).orElse(DEFAULT_VALUE);
      this.max = Optional.ofNullable(max).orElse(DEFAULT_MAX);
      this.maxAttempt = (int) ((this.max / this.value) - 1);
    }

    @Override
    public long delay(int attempt) {
      if (attempt > maxAttempt) {
        return max;
      }
      return value * (attempt + 1);
    }
  }
}
