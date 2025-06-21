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

package codelab.testrunner;

import dev.cel.bundle.CelFactory;
import dev.cel.runtime.CelFunctionBinding;
import dev.cel.testing.testrunner.CelTestContext;
import dev.cel.testing.testrunner.CelUserTestTemplate;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/** A sample basic user test class demonstrating a basic user journey. */
@RunWith(Parameterized.class)
public class BasicUserTest extends CelUserTestTemplate {

  public BasicUserTest() {
    super(
        CelTestContext.newBuilder()
            .setCel(
                CelFactory.standardCelBuilder()
                    .addFunctionBindings(
                        CelFunctionBinding.from(
                            "foo_id", String.class, (String a) -> a.equals("foo")))
                    .addFunctionBindings(
                        CelFunctionBinding.from(
                            "bar_id", String.class, (String a) -> a.equals("bar")))
                    .build())
            .build());
  }
}
