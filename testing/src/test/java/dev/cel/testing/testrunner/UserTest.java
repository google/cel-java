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
import dev.cel.common.types.MapType;
import dev.cel.common.types.SimpleType;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/**
 * A sample test class for demonstrating the use of the CEL test runner when no config file is
 * provided.
 */
@RunWith(Parameterized.class)
public class UserTest extends CelUserTestTemplate {

  public UserTest() {
    super(
        CelTestContext.newBuilder()
            .setCel(
                CelFactory.standardCelBuilder()
                    .addVar("resource", MapType.create(SimpleType.STRING, SimpleType.ANY))
                    .build())
            .build());
  }
}
