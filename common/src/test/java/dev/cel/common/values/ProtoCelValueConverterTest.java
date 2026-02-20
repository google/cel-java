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

import dev.cel.common.CelOptions;
import dev.cel.common.internal.DefaultDescriptorPool;
import dev.cel.common.internal.DefaultMessageFactory;
import dev.cel.common.internal.DynamicProto;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class ProtoCelValueConverterTest {

  private static final ProtoCelValueConverter PROTO_CEL_VALUE_CONVERTER =
      ProtoCelValueConverter.newInstance(
          DefaultDescriptorPool.INSTANCE,
          DynamicProto.create(DefaultMessageFactory.INSTANCE),
          CelOptions.DEFAULT);

  @Test
  public void unwrap_nullValue() {
    NullValue nullValue = (NullValue) PROTO_CEL_VALUE_CONVERTER.unwrap(NullValue.NULL_VALUE);

    // Note: No conversion is attempted. We're using dev.cel.common.values.NullValue.NULL_VALUE as
    // the
    // sentinel type for CEL's `null`.
    assertThat(nullValue).isEqualTo(NullValue.NULL_VALUE);
  }
}
