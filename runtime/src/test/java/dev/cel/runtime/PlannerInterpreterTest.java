// Copyright 2026 Google LLC
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

import com.google.testing.junit.testparameterinjector.TestParameterInjector;
import dev.cel.common.CelOptions;
import dev.cel.extensions.CelExtensions;
import dev.cel.testing.BaseInterpreterTest;
import org.junit.runner.RunWith;

/** Interpreter tests using ProgramPlanner */
@RunWith(TestParameterInjector.class)
public class PlannerInterpreterTest extends BaseInterpreterTest {

  @Override
  protected CelRuntimeBuilder newBaseRuntimeBuilder(CelOptions celOptions) {
    return CelRuntimeImpl.newBuilder()
        .addLateBoundFunctions("record")
        // CEL-Internal-2
        .setOptions(celOptions)
        .addLibraries(CelExtensions.optional())
        .addFileTypes(TEST_FILE_DESCRIPTORS);
  }

  @Override
  public void unknownField() {
    // TODO: Unknown support not implemented yet
    skipBaselineVerification();
  }

  @Override
  public void unknownResultSet() {
    // TODO: Unknown support not implemented yet
    skipBaselineVerification();
  }

  @Override
  public void jsonFieldNames() throws Exception {
    // TODO: Support JSON field names for planner
    skipBaselineVerification();
  }
}
