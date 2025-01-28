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

package dev.cel.common.types;

import static com.google.common.truth.Truth.assertThat;

import dev.cel.expr.Type;
import com.google.testing.junit.testparameterinjector.TestParameterInjector;
import dev.cel.expr.conformance.proto3.TestAllTypes;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(TestParameterInjector.class)
public final class CelProtoMessageTypesTest {

  @Test
  public void createMessage_fromDescriptor() {
    Type type = CelProtoMessageTypes.createMessage(TestAllTypes.getDescriptor());

    assertThat(type)
        .isEqualTo(
            Type.newBuilder().setMessageType(TestAllTypes.getDescriptor().getFullName()).build());
  }
}
