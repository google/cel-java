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

import dev.cel.testing.testrunner.CelTestSuite.CelTestSection.CelTestCase;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;

/**
 * Template to be extended by user's test class in order to be parameterized based on individual
 * test cases.
 */
@RunWith(Parameterized.class)
public abstract class CelUserTestTemplate {

  @Parameter public CelTestCase testCase;
  private final CelTestContext celTestContext;

  public CelUserTestTemplate(CelTestContext celTestContext) {
    this.celTestContext = celTestContext;
  }

  @Test
  public void test() throws Exception {
    TestRunnerLibrary.runTest(testCase, celTestContext);
  }
}
