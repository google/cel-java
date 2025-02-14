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

package dev.cel.runtime;

import static org.junit.Assert.assertThrows;

import com.google.testing.junit.testparameterinjector.TestParameterInjector;
import dev.cel.common.CelOptions;
import dev.cel.expr.conformance.proto2.TestAllTypes;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(TestParameterInjector.class)
public final class RuntimeEqualityTest {

  @Test
  public void objectEquals_messageLite_throws() {
    RuntimeEquality runtimeEquality =
        RuntimeEquality.create(RuntimeHelpers.create(), CelOptions.DEFAULT);

    // Unimplemented until CelLiteDescriptor is available.
    assertThrows(
        UnsupportedOperationException.class,
        () ->
            runtimeEquality.objectEquals(
                TestAllTypes.newBuilder(), TestAllTypes.getDefaultInstance()));
  }
}
