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

import dev.cel.bundle.CelFactory;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/**
 * This test demonstrates the use case where the declarations are provided in the environment config
 * file.
 */
@RunWith(Parameterized.class)
public class EnvConfigUserTest extends CelUserTestTemplate {

  public EnvConfigUserTest() {
    super(CelTestContext.newBuilder().setCel(CelFactory.standardCelBuilder().build()).build());
  }
}
