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

package dev.cel.runtime;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import com.google.common.collect.ImmutableList;
import com.google.testing.junit.testparameterinjector.TestParameterInjector;
import dev.cel.common.CelAbstractSyntaxTree;
import dev.cel.common.CelFunctionDecl;
import dev.cel.common.CelOptions;
import dev.cel.common.CelOverloadDecl;
import dev.cel.common.types.SimpleType;
import dev.cel.compiler.CelCompiler;
import dev.cel.compiler.CelCompilerFactory;
import dev.cel.parser.CelStandardMacro;
import dev.cel.runtime.DefaultInterpreter.DefaultInterpretable;
import dev.cel.runtime.DefaultInterpreter.ExecutionFrame;
import dev.cel.runtime.standard.NotStrictlyFalseFunction.NotStrictlyFalseOverload;
import java.util.Map;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Exercises tests for the internals of the {@link DefaultInterpreter}. Tests should only be added
 * here if we absolutely must add coverage for internal state of the interpreter that's otherwise
 * impossible with the CEL public APIs.
 */
@RunWith(TestParameterInjector.class)
public class DefaultInterpreterTest {

  @Test
  public void nestedComprehensions_accuVarContainsErrors_scopeLevelInvariantNotViolated()
      throws Exception {
    CelCompiler celCompiler =
        CelCompilerFactory.standardCelCompilerBuilder()
            .addFunctionDeclarations(
                CelFunctionDecl.newFunctionDeclaration(
                    "error", CelOverloadDecl.newGlobalOverload("error_overload", SimpleType.DYN)))
            .setStandardMacros(CelStandardMacro.STANDARD_MACROS)
            .build();
    RuntimeTypeProvider emptyProvider =
        new RuntimeTypeProvider() {
          @Override
          public Object createMessage(String messageName, Map<String, Object> values) {
            return null;
          }

          @Override
          public Object selectField(Object message, String fieldName) {
            return null;
          }

          @Override
          public Object hasField(Object message, String fieldName) {
            return null;
          }

          @Override
          public Object adapt(String messageName, Object message) {
            return message;
          }
        };
    CelAbstractSyntaxTree ast = celCompiler.compile("[1].all(x, [2].all(y, error()))").getAst();
    DefaultDispatcher.Builder dispatcherBuilder = DefaultDispatcher.newBuilder();
    dispatcherBuilder.addOverload(
        "error",
        ImmutableList.of(long.class),
        /* isStrict= */ true,
        (args) -> new IllegalArgumentException("Always throws"));
    CelFunctionBinding notStrictlyFalseBinding =
        NotStrictlyFalseOverload.NOT_STRICTLY_FALSE.newFunctionBinding(
            CelOptions.DEFAULT,
            RuntimeEquality.create(RuntimeHelpers.create(), CelOptions.DEFAULT));
    dispatcherBuilder.addOverload(
        notStrictlyFalseBinding.getOverloadId(),
        notStrictlyFalseBinding.getArgTypes(),
        notStrictlyFalseBinding.isStrict(),
        notStrictlyFalseBinding.getDefinition());
    DefaultInterpreter defaultInterpreter =
        new DefaultInterpreter(
            new TypeResolver(), emptyProvider, dispatcherBuilder.build(), CelOptions.DEFAULT);
    DefaultInterpretable interpretable =
        (DefaultInterpretable) defaultInterpreter.createInterpretable(ast);

    ExecutionFrame frame = interpretable.newTestExecutionFrame(GlobalResolver.EMPTY);

    assertThrows(CelEvaluationException.class, () -> interpretable.populateExecutionFrame(frame));
    assertThat(frame.scopeLevel).isEqualTo(0);
  }
}
