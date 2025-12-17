// Copyright 2025 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      https://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package dev.cel.common.internal;

import static com.google.common.truth.Truth.assertThat;
import static dev.cel.common.internal.ProtoTimeUtils.DURATION_SECONDS_MAX;
import static dev.cel.common.internal.ProtoTimeUtils.DURATION_SECONDS_MIN;

import com.google.protobuf.Duration;
import com.google.protobuf.Timestamp;
import com.google.testing.junit.testparameterinjector.TestParameterInjector;
import java.time.Instant;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(TestParameterInjector.class)
public class ProtoTimeUtilsTest {

  @Test
  public void toJavaInstant_overRfc3339Range() {
    Timestamp ts =
        Timestamp.newBuilder().setSeconds(ProtoTimeUtils.TIMESTAMP_SECONDS_MAX + 1).build();

    Instant instant = ProtoTimeUtils.toJavaInstant(ts);

    assertThat(instant).isEqualTo(Instant.ofEpochSecond(ProtoTimeUtils.TIMESTAMP_SECONDS_MAX + 1));
  }

  @Test
  public void toJavaInstant_underRfc3339Range() {
    Timestamp ts =
        Timestamp.newBuilder().setSeconds(ProtoTimeUtils.TIMESTAMP_SECONDS_MIN - 1).build();

    Instant instant = ProtoTimeUtils.toJavaInstant(ts);

    assertThat(instant).isEqualTo(Instant.ofEpochSecond(ProtoTimeUtils.TIMESTAMP_SECONDS_MIN - 1));
  }

  @Test
  public void toJavaDuration_overRfc3339Range() {
    Duration d = Duration.newBuilder()
        .setSeconds(DURATION_SECONDS_MAX + 1)
        .build();

    java.time.Duration duration = ProtoTimeUtils.toJavaDuration(d);

    assertThat(duration).isEqualTo(java.time.Duration.ofSeconds(DURATION_SECONDS_MAX + 1));
  }

  @Test
  public void toJavaDuration_underRfc3339Range() {
    Duration d = Duration.newBuilder()
        .setSeconds(DURATION_SECONDS_MIN - 1)
        .build();

    java.time.Duration duration = ProtoTimeUtils.toJavaDuration(d);

    assertThat(duration).isEqualTo(java.time.Duration.ofSeconds(DURATION_SECONDS_MIN - 1));
  }
}
