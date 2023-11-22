// Copyright 2023 Google LLC
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

package dev.cel.common.values;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import dev.cel.common.types.SimpleType;
import java.time.Duration;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class DurationValueTest {

  @Test
  public void emptyDuration() {
    DurationValue durationValue = DurationValue.create(Duration.ZERO);

    assertThat(durationValue.value()).isEqualTo(Duration.ofSeconds(0));
    assertThat(durationValue.isZeroValue()).isTrue();
  }

  @Test
  public void constructDuration() {
    DurationValue durationValue = DurationValue.create(Duration.ofSeconds(10000));

    assertThat(durationValue.value()).isEqualTo(Duration.ofSeconds(10000));
    assertThat(durationValue.isZeroValue()).isFalse();
  }

  @Test
  public void celTypeTest() {
    DurationValue value = DurationValue.create(Duration.ZERO);

    assertThat(value.celType()).isEqualTo(SimpleType.DURATION);
  }

  @Test
  public void create_nullValue_throws() {
    assertThrows(NullPointerException.class, () -> DurationValue.create(null));
  }
}
