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

import com.google.testing.junit.testparameterinjector.TestParameterInjector;
import com.google.testing.junit.testparameterinjector.TestParameters;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(TestParameterInjector.class)
public class WellKnownProtoTest {

  @Test
  @TestParameters("{typeName: 'google.protobuf.FloatValue'}")
  @TestParameters("{typeName: 'google.protobuf.Int32Value'}")
  @TestParameters("{typeName: 'google.protobuf.Int64Value'}")
  @TestParameters("{typeName: 'google.protobuf.StringValue'}")
  @TestParameters("{typeName: 'google.protobuf.BoolValue'}")
  @TestParameters("{typeName: 'google.protobuf.BytesValue'}")
  @TestParameters("{typeName: 'google.protobuf.DoubleValue'}")
  @TestParameters("{typeName: 'google.protobuf.UInt32Value'}")
  @TestParameters("{typeName: 'google.protobuf.UInt64Value'}")
  @TestParameters("{typeName: 'google.protobuf.Empty'}")
  @TestParameters("{typeName: 'google.protobuf.FieldMask'}")
  public void isWrapperType_withTypeName_true(String typeName) {
    assertThat(WellKnownProto.isWrapperType(typeName)).isTrue();
  }

  @Test
  @TestParameters("{typeName: 'not.wellknown.type'}")
  @TestParameters("{typeName: 'google.protobuf.Any'}")
  @TestParameters("{typeName: 'google.protobuf.Duration'}")
  @TestParameters("{typeName: 'google.protobuf.ListValue'}")
  @TestParameters("{typeName: 'google.protobuf.Struct'}")
  @TestParameters("{typeName: 'google.protobuf.Value'}")
  @TestParameters("{typeName: 'google.protobuf.Timestamp'}")
  public void isWrapperType_withTypeName_false(String typeName) {
    assertThat(WellKnownProto.isWrapperType(typeName)).isFalse();
  }
}
