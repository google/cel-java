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
import org.jspecify.annotations.Nullable;
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

  @Parameter(0)
  public CelTestCase testCase;

  @Parameter(1)
  public @Nullable CelCoverageIndex celCoverageIndex;

  private final CelTestContext celTestContext;

  public CelUserTestTemplate(CelTestContext celTestContext) {
    this.celTestContext = celTestContext;
  }

  @Test
  public void test() throws Exception {
    if (celCoverageIndex != null) {
      TestRunnerLibrary.runTest(testCase, updateCelTestContext(celTestContext), celCoverageIndex);
    } else {
      TestRunnerLibrary.runTest(testCase, updateCelTestContext(celTestContext));
    }
  }

  /**
   * Updates the CEL test context based on the system properties.
   *
   * <p>This method is used to update the CEL test context based on the system properties. It checks
   * if the runner library is triggered via blaze macro or via JUnit and assigns values accordingly.
   *
   * @param celTestContext The CEL test context to update.
   * @return The updated CEL test context.
   */
  private CelTestContext updateCelTestContext(CelTestContext celTestContext) {
    String celExpr = System.getProperty("cel_expr");
    String configPath = System.getProperty("config_path");
    String fileDescriptorSetPath = System.getProperty("file_descriptor_set_path");
    String isRawExpr = System.getProperty("is_raw_expr");

    CelTestContext.Builder celTestContextBuilder = celTestContext.toBuilder();
    if (celExpr != null) {
      if (isRawExpr.equals("True")) {
        celTestContextBuilder.setCelExpression(CelExpressionSource.fromRawExpr(celExpr));
      } else {
        celTestContextBuilder.setCelExpression(CelExpressionSource.fromSource(celExpr));
      }
    }
    if (configPath != null) {
      celTestContextBuilder.setConfigFile(configPath);
    }
    if (fileDescriptorSetPath != null) {
      celTestContextBuilder.setFileDescriptorSetPath(fileDescriptorSetPath);
    }
    return celTestContextBuilder.build();
  }
}
