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

package dev.cel.testing.testrunner;

import com.google.common.collect.ImmutableMap;
import com.google.protobuf.Any;
import dev.cel.expr.conformance.proto2.TestAllTypes;
import dev.cel.expr.conformance.proto2.TestAllTypesExtensions;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/**
 * This test demonstrates the use case where the custom variable bindings are being provided
 * programmatically for using extensions.
 */
@RunWith(Parameterized.class)
public class CustomVariableBindingUserTest extends CelUserTestTemplate {

  public CustomVariableBindingUserTest() {
    super(
        CelTestContext.newBuilder()
            .setVariableBindings(
                ImmutableMap.of(
                    "spec",
                    Any.pack(
                        TestAllTypes.newBuilder()
                            .setExtension(TestAllTypesExtensions.int32Ext, 1)
                            .build())))
            .build());
  }
}
