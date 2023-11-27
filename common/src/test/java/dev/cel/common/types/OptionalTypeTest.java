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

package dev.cel.common.types;

import static com.google.common.truth.Truth.assertThat;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class OptionalTypeTest {

  @Test
  public void createOptionalType_success() {
    OptionalType optionalType = OptionalType.create(SimpleType.STRING);

    assertThat(optionalType.name()).isEqualTo("optional_type");
    assertThat(optionalType.kind()).isEqualTo(CelKind.OPAQUE);
    assertThat(optionalType.parameters()).containsExactly(SimpleType.STRING);
  }
}
