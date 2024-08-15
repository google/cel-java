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

package dev.cel.validator.validators;

import static com.google.common.truth.Truth.assertThat;
import static dev.cel.common.CelFunctionDecl.newFunctionDeclaration;
import static dev.cel.common.CelOverloadDecl.newGlobalOverload;
import static dev.cel.validator.validators.AstDepthLimitValidator.DEFAULT_DEPTH_LIMIT;
import static org.junit.Assert.assertThrows;

import dev.cel.expr.CheckedExpr;
import com.google.protobuf.ByteString;
import com.google.protobuf.ExtensionRegistryLite;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.testing.junit.testparameterinjector.TestParameter;
import com.google.testing.junit.testparameterinjector.TestParameterInjector;
import dev.cel.bundle.Cel;
import dev.cel.bundle.CelFactory;
import dev.cel.common.CelAbstractSyntaxTree;
import dev.cel.common.CelIssue.Severity;
import dev.cel.common.CelProtoAbstractSyntaxTree;
import dev.cel.common.CelValidationResult;
import dev.cel.common.types.SimpleType;
import dev.cel.validator.CelValidator;
import dev.cel.validator.CelValidatorFactory;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(TestParameterInjector.class)
public class AstDepthLimitValidatorTest {

  private static final Cel CEL =
      CelFactory.standardCelBuilder()
          .addVar("x", SimpleType.DYN)
          .addFunctionDeclarations(
              newFunctionDeclaration(
                  "f", newGlobalOverload("f_int64", SimpleType.INT, SimpleType.INT)))
          .build();

  private static final CelValidator CEL_VALIDATOR =
      CelValidatorFactory.standardCelValidatorBuilder(CEL)
          .addAstValidators(AstDepthLimitValidator.DEFAULT)
          .build();

  private enum DefaultTestCase {
    NESTED_SELECTS(
        "x.y.y.y.y.y.y.y.y.y.y.y.y.y.y.y.y.y.y.y.y.y.y.y.y.y.y.y.y.y.y.y.y.y.y.y.y.y.y.y.y.y.y.y.y.y.y.y.y.y.y"),
    NESTED_CALCS(
        "0+1+2+3+4+5+6+7+8+9+10+11+12+13+14+15+16+17+18+19+20+21+22+23+24+25+26+27+28+29+30+31+32+33+34+35+36+37+38+39+40+41+42+43+44+45+46+47+48+49+50"),
    NESTED_FUNCS(
        "f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(0)))))))))))))))))))))))))))))))))))))))))))))))))))");

    private final String expression;

    DefaultTestCase(String expression) {
      this.expression = expression;
    }
  }

  @Test
  public void astExceedsDefaultDepthLimit_populatesErrors(@TestParameter DefaultTestCase testCase)
      throws Exception {
    CelAbstractSyntaxTree ast = CEL.compile(testCase.expression).getAst();

    CelValidationResult result = CEL_VALIDATOR.validate(ast);

    assertThat(result.hasError()).isTrue();
    assertThat(result.getAllIssues()).hasSize(1);
    assertThat(result.getAllIssues().get(0).getSeverity()).isEqualTo(Severity.ERROR);
    assertThat(result.getAllIssues().get(0).toDisplayString(ast.getSource()))
        .contains("AST's depth exceeds the configured limit: 50.");
    assertThrows(InvalidProtocolBufferException.class, () -> verifyProtoAstRoundTrips(ast));
  }

  @Test
  public void astIsUnderDepthLimit_noErrors() throws Exception {
    StringBuilder sb = new StringBuilder().append("x");
    for (int i = 0; i < DEFAULT_DEPTH_LIMIT - 1; i++) {
      sb.append(".y");
    }
    // Depth level of 49
    CelAbstractSyntaxTree ast = CEL.compile(sb.toString()).getAst();

    CelValidationResult result = CEL_VALIDATOR.validate(ast);

    assertThat(result.hasError()).isFalse();
    assertThat(result.getAllIssues()).isEmpty();
    verifyProtoAstRoundTrips(ast);
  }

  private void verifyProtoAstRoundTrips(CelAbstractSyntaxTree ast) throws Exception {
    CheckedExpr checkedExpr = CelProtoAbstractSyntaxTree.fromCelAst(ast).toCheckedExpr();
    ByteString serialized = checkedExpr.toByteString();
    CheckedExpr deserializedCheckedExpr =
        CheckedExpr.parseFrom(serialized, ExtensionRegistryLite.getEmptyRegistry());
    if (!checkedExpr.equals(deserializedCheckedExpr)) {
      throw new IllegalStateException("Expected checked expressions to round trip!");
    }
  }
}
