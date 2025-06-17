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

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.extensions.proto.ProtoTruth.assertThat;
import static com.google.protobuf.LegacyUnredactedTextFormat.legacyUnredactedStringValueOf;
import static dev.cel.testing.utils.ExprValueUtils.toExprValue;

import dev.cel.expr.ExprValue;
import dev.cel.expr.MapValue;
import dev.cel.bundle.Cel;
import dev.cel.common.CelAbstractSyntaxTree;
import dev.cel.runtime.CelEvaluationException;
import dev.cel.runtime.CelRuntime.Program;
import dev.cel.testing.testrunner.CelTestSuite.CelTestSection.CelTestCase.Output;
import dev.cel.testing.testrunner.ResultMatcher.ResultMatcherParams;
import dev.cel.testing.testrunner.ResultMatcher.ResultMatcherParams.ComputedOutput;
import java.io.IOException;

final class DefaultResultMatcher implements ResultMatcher {

  @Override
  public void match(ResultMatcherParams params, Cel cel) throws Exception {
    Output result = params.expectedOutput().get();
    switch (result.kind()) {
      case RESULT_EXPR:
        if (params.computedOutput().kind().equals(ComputedOutput.Kind.ERROR)) {
          throw new AssertionError(
              "Error: " + params.computedOutput().error().getMessage(),
              params.computedOutput().error());
        }
        CelAbstractSyntaxTree exprAst = cel.compile(result.resultExpr()).getAst();
        Program exprProgram = cel.createProgram(exprAst);
        Object evaluationResult = null;
        try {
          evaluationResult = exprProgram.eval();
        } catch (CelEvaluationException e) {
          throw new IllegalArgumentException(
              "Failed to evaluate result_expr: " + e.getMessage(), e);
        }
        ExprValue expectedExprValue = toExprValue(evaluationResult, exprAst.getResultType());
        assertThat(params.computedOutput().exprValue()).isEqualTo(expectedExprValue);
        break;
      case RESULT_VALUE:
        if (params.computedOutput().kind().equals(ComputedOutput.Kind.ERROR)) {
          throw new AssertionError(
              "Error: " + params.computedOutput().error().getMessage(),
              params.computedOutput().error());
        }
        assertExprValue(
            params.computedOutput().exprValue(),
            toExprValue(result.resultValue(), params.resultType()));
        break;
      case EVAL_ERROR:
        if (params.computedOutput().kind().equals(ComputedOutput.Kind.EXPR_VALUE)) {
          throw new AssertionError(
              "Evaluation was successful but no value was provided. Computed output: "
                  + legacyUnredactedStringValueOf(params.computedOutput().exprValue()));
        }
        assertThat(params.computedOutput().error().toString())
            .contains(result.evalError().get(0).toString());
        break;
      case UNKNOWN_SET:
        assertThat(params.computedOutput().unknownSet())
            .containsExactlyElementsIn(result.unknownSet());
        break;
      default:
        throw new IllegalArgumentException("Unexpected output type: " + result.kind());
    }
  }

  private static void assertExprValue(ExprValue exprValue, ExprValue expectedExprValue)
      throws IOException {
    String fileDescriptorSetPath = System.getProperty("file_descriptor_set_path");
    if (fileDescriptorSetPath != null) {
      assertThat(exprValue)
          .ignoringRepeatedFieldOrderOfFieldDescriptors(
              MapValue.getDescriptor().findFieldByName("entries"))
          .unpackingAnyUsing(RegistryUtils.getTypeRegistry(), RegistryUtils.getExtensionRegistry())
          .isEqualTo(expectedExprValue);
    } else {
      assertThat(exprValue)
          .ignoringRepeatedFieldOrderOfFieldDescriptors(
              MapValue.getDescriptor().findFieldByName("entries"))
          .isEqualTo(expectedExprValue);
    }
  }
}
