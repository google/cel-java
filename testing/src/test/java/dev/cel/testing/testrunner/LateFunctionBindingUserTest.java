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

import dev.cel.runtime.CelFunctionBinding;
import dev.cel.runtime.CelLateFunctionBindings;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/** This test demonstrates the use case where the function bindings are provided at eval stage. */
@RunWith(Parameterized.class)
public class LateFunctionBindingUserTest extends CelUserTestTemplate {

  public LateFunctionBindingUserTest() {
    super(
        CelTestContext.newBuilder()
            .setCelLateFunctionBindings(
                CelLateFunctionBindings.from(
                    CelFunctionBinding.from("foo_id", String.class, (String a) -> a.equals("foo")),
                    CelFunctionBinding.from("bar_id", String.class, (String a) -> a.equals("bar"))))
            .build());
  }
}
