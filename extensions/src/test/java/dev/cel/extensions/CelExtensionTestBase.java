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

package dev.cel.extensions;

import com.google.common.collect.ImmutableMap;
import com.google.testing.junit.testparameterinjector.TestParameter;
import dev.cel.bundle.Cel;
import dev.cel.common.CelAbstractSyntaxTree;
import dev.cel.common.CelException;
import dev.cel.testing.CelRuntimeFlavor;
import java.util.Map;
import org.junit.Assume;
import org.junit.Before;

/**
 * Abstract base class for extension tests to facilitate executing tests with both legacy and
 * planner runtime, along with parsed-only and checked expression evaluations for the planner.
 */
abstract class CelExtensionTestBase {
  @TestParameter CelRuntimeFlavor runtimeFlavor;
  @TestParameter boolean isParseOnly;

  @Before
  public void setUpBase() {
    // Legacy runtime does not support parsed-only evaluation.
    Assume.assumeFalse(runtimeFlavor.equals(CelRuntimeFlavor.LEGACY) && isParseOnly);
    this.cel = newCelEnv();
  }

  protected Cel cel;

  /**
   * Subclasses must implement this to provide a Cel instance configured with the specific
   * extensions being tested.
   */
  protected abstract Cel newCelEnv();

  protected Object eval(String expr) throws CelException {
    return eval(cel, expr, ImmutableMap.of());
  }

  protected Object eval(String expr, Map<String, ?> variables) throws CelException {
    return eval(cel, expr, variables);
  }

  protected Object eval(Cel cel, String expr) throws CelException {
    return eval(cel, expr, ImmutableMap.of());
  }

  protected Object eval(Cel cel, String expr, Map<String, ?> variables) throws CelException {
    CelAbstractSyntaxTree ast = isParseOnly ? cel.parse(expr).getAst() : cel.compile(expr).getAst();
    return cel.createProgram(ast).eval(variables);
  }
}
