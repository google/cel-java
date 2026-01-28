// Copyright 2023 Google LLC
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
// import com.google.testing.testsize.MediumTest;
import dev.cel.testing.BaseInterpreterTest;
import org.junit.runner.RunWith;

/** Tests for {@link Interpreter} and related functionality using {@code CelValue}. */
// @MediumTest
@RunWith(TestParameterInjector.class)
public class CelValueInterpreterTest extends BaseInterpreterTest {

  public CelValueInterpreterTest() {
    super(newBaseCelOptions().toBuilder().enableCelValue(true).build());
  }

  @Override
  public void wrappers() throws Exception {
    // Field selection on repeated wrappers broken.
    // This test along with CelValue adapter will be removed in a separate CL
    skipBaselineVerification();
  }
}
