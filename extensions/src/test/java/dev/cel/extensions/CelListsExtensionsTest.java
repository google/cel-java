// Copyright 2024 Google LLC
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

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import com.google.testing.junit.testparameterinjector.TestParameterInjector;
import com.google.testing.junit.testparameterinjector.TestParameters;
import dev.cel.bundle.Cel;
import dev.cel.bundle.CelFactory;
import dev.cel.common.CelValidationException;
import dev.cel.parser.CelStandardMacro;
import dev.cel.runtime.CelEvaluationException;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(TestParameterInjector.class)
public class CelListsExtensionsTest {
  private static final Cel CEL =
      CelFactory.standardCelBuilder()
          .setStandardMacros(CelStandardMacro.STANDARD_MACROS)
          .addCompilerLibraries(CelExtensions.lists())
          .addRuntimeLibraries(CelExtensions.lists())
          .build();

  @Test
  @TestParameters("{expression: '[].flatten() == []'}")
  @TestParameters("{expression: '[[1, 2]].flatten().exists(i, i == 1)'}")
  @TestParameters("{expression: '[[], [[]], [[[]]]].flatten() == [[], [[]]]'}")
  @TestParameters("{expression: '[1,[2,[3,4]]].flatten() == [1,2,[3,4]]'}")
  @TestParameters("{expression: '[1,2,[],[],[3,4]].flatten() == [1,2,3,4]'}")
  @TestParameters("{expression: '[1,[2,3],[[4,5]], [[[6,7]]]].flatten() == [1,2,3,[4,5],[[6,7]]]'}")
  @TestParameters("{expression: 'dyn([1]).flatten() == [1]'}")
  @TestParameters("{expression: 'dyn([{1: 2}]).flatten() == [{1: 2}]'}")
  @TestParameters("{expression: 'dyn([1,2,3,4]).flatten() == [1,2,3,4]'}")
  public void flattenSingleLevel_success(String expression) throws Exception {
    boolean result = (boolean) CEL.createProgram(CEL.compile(expression).getAst()).eval();

    assertThat(result).isTrue();
  }

  @Test
  @TestParameters("{expression: '[1,2,3,4].flatten(1) == [1,2,3,4]'}")
  @TestParameters("{expression: '[1,[2,[3,[4]]]].flatten(0) == [1,[2,[3,[4]]]]'}")
  @TestParameters("{expression: '[1,[2,[3,[4]]]].flatten(2) == [1,2,3,[4]]'}")
  @TestParameters("{expression: '[1,[2,[3,4]]].flatten(2) == [1,2,3,4]'}")
  @TestParameters("{expression: '[[], [[]], [[[]]]].flatten(2) == [[]]'}")
  @TestParameters("{expression: '[[], [[]], [[[]]]].flatten(3) == []'}")
  @TestParameters("{expression: '[[], [[]], [[[]]]].flatten(4) == []'}")
  // The overload with the depth accepts and returns a List(dyn), so the following is permitted.
  @TestParameters("{expression: '[1].flatten(1) == [1]'}")
  public void flatten_withDepthValue_success(String expression) throws Exception {
    boolean result = (boolean) CEL.createProgram(CEL.compile(expression).getAst()).eval();

    assertThat(result).isTrue();
  }

  @Test
  public void flatten_negativeDepth_throws() {
    CelEvaluationException e =
        assertThrows(
            CelEvaluationException.class,
            () -> CEL.createProgram(CEL.compile("[1,2,3,4].flatten(-1)").getAst()).eval());

    assertThat(e)
        .hasMessageThat()
        .contains("evaluation error at <input>:17: Function 'list_flatten_list_int' failed");
    assertThat(e).hasCauseThat().hasMessageThat().isEqualTo("Level must be non-negative");
  }

  @Test
  @TestParameters("{expression: '[1].flatten()'}")
  @TestParameters("{expression: '[{1: 2}].flatten()'}")
  @TestParameters("{expression: '[1,2,3,4].flatten()'}")
  public void flattenSingleLevel_listIsSingleLevel_throws(String expression) {
    // Note: Java lacks the capability of conditionally disabling type guards
    // due to the lack of full-fledged dynamic dispatch.
    assertThrows(CelValidationException.class, () -> CEL.compile(expression).getAst());
  }
}
