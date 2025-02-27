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

package dev.cel.validator.validators;

import static com.google.common.truth.Truth.assertThat;
import static dev.cel.common.CelFunctionDecl.newFunctionDeclaration;
import static dev.cel.common.CelOverloadDecl.newGlobalOverload;
import static dev.cel.common.CelOverloadDecl.newMemberOverload;

import com.google.testing.junit.testparameterinjector.TestParameterInjector;
import com.google.testing.junit.testparameterinjector.TestParameters;
import dev.cel.bundle.Cel;
import dev.cel.bundle.CelFactory;
import dev.cel.common.CelAbstractSyntaxTree;
import dev.cel.common.CelValidationResult;
import dev.cel.common.types.SimpleType;
import dev.cel.extensions.CelOptionalLibrary;
import dev.cel.runtime.CelFunctionBinding;
import dev.cel.validator.CelValidator;
import dev.cel.validator.CelValidatorFactory;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(TestParameterInjector.class)
public class HomogeneousLiteralValidatorTest {
  private static final Cel CEL =
      CelFactory.standardCelBuilder()
          .addCompilerLibraries(CelOptionalLibrary.INSTANCE)
          .addRuntimeLibraries(CelOptionalLibrary.INSTANCE)
          .build();

  private static final CelValidator CEL_VALIDATOR =
      CelValidatorFactory.standardCelValidatorBuilder(CEL)
          .addAstValidators(HomogeneousLiteralValidator.newInstance())
          .build();

  @Test
  @TestParameters("{source: '[1, 2, 3]'}")
  @TestParameters("{source: '[dyn(1), dyn(2), dyn(3)]'}")
  @TestParameters("{source: '[''hello'', ''world'', ''test'']'}")
  @TestParameters("{source: '[''hello'', ?optional.ofNonZeroValue(''''), ?optional.of('''')]'}")
  @TestParameters("{source: '[?optional.ofNonZeroValue(''''), ?optional.of(''''), ''hello'']'}")
  public void list_containsHomogeneousLiterals(String source) throws Exception {
    CelAbstractSyntaxTree ast = CEL.compile(source).getAst();

    CelValidationResult result = CEL_VALIDATOR.validate(ast);

    assertThat(result.hasError()).isFalse();
    assertThat(result.getAllIssues()).isEmpty();
    assertThat(CEL.createProgram(ast).eval()).isInstanceOf(List.class);
  }

  @Test
  @TestParameters("{source: '{1: false, 2: true}'}")
  @TestParameters("{source: '{''hello'': false, ''world'': true}'}")
  @TestParameters("{source: '{''hello'': false, ?''world'': optional.ofNonZeroValue(true)}'}")
  @TestParameters("{source: '{?''hello'': optional.ofNonZeroValue(false), ''world'': true}'}")
  public void map_containsHomogeneousLiterals(String source) throws Exception {
    CelAbstractSyntaxTree ast = CEL.compile(source).getAst();

    CelValidationResult result = CEL_VALIDATOR.validate(ast);

    assertThat(result.hasError()).isFalse();
    assertThat(result.getAllIssues()).isEmpty();
    assertThat(CEL.createProgram(ast).eval()).isInstanceOf(Map.class);
  }

  @Test
  public void list_containsHeterogeneousLiterals() throws Exception {
    CelAbstractSyntaxTree ast = CEL.compile("[1, 2, 'hello']").getAst();

    CelValidationResult result = CEL_VALIDATOR.validate(ast);

    assertThat(result.hasError()).isTrue();
    assertThat(result.getAllIssues()).hasSize(1);
    assertThat(result.getErrorString())
        .contains(
            "ERROR: <input>:1:8: expected type 'int' but found 'string'\n"
                + " | [1, 2, 'hello']\n"
                + " | .......^");
  }

  @Test
  public void list_containsHeterogeneousLiteralsInNestedLists() throws Exception {
    CelAbstractSyntaxTree ast = CEL.compile("[[1], ['hello']]").getAst();

    CelValidationResult result = CEL_VALIDATOR.validate(ast);

    assertThat(result.hasError()).isTrue();
    assertThat(result.getAllIssues()).hasSize(1);
    assertThat(result.getErrorString())
        .contains(
            "ERROR: <input>:1:7: expected type 'list(int)' but found 'list(string)'\n"
                + " | [[1], ['hello']]\n"
                + " | ......^");
  }

  @Test
  public void list_containsHeterogeneousLiteralsInDyn() throws Exception {
    CelAbstractSyntaxTree ast = CEL.compile("[1, 2, dyn(3)]").getAst();

    CelValidationResult result = CEL_VALIDATOR.validate(ast);

    assertThat(result.hasError()).isTrue();
    assertThat(result.getAllIssues()).hasSize(1);
    assertThat(result.getErrorString())
        .contains(
            "ERROR: <input>:1:11: expected type 'int' but found 'dyn'\n"
                + " | [1, 2, dyn(3)]\n"
                + " | ..........^");
  }

  @Test
  public void mapKey_containsHeterogeneousLiterals() throws Exception {
    CelAbstractSyntaxTree ast = CEL.compile("{1: true, 'hello': false}").getAst();

    CelValidationResult result = CEL_VALIDATOR.validate(ast);

    assertThat(result.hasError()).isTrue();
    assertThat(result.getAllIssues()).hasSize(1);
    assertThat(result.getErrorString())
        .contains(
            "ERROR: <input>:1:18: expected type 'int' but found 'string'\n"
                + " | {1: true, 'hello': false}\n"
                + " | .................^");
  }

  @Test
  public void mapKey_containsHeterogeneousLiteralsInNestedMaps() throws Exception {
    CelAbstractSyntaxTree ast = CEL.compile("{{'a': 1}: true, {'b': 'hello'}: false}").getAst();

    CelValidationResult result = CEL_VALIDATOR.validate(ast);

    assertThat(result.hasError()).isTrue();
    assertThat(result.getAllIssues()).hasSize(1);
    assertThat(result.getErrorString())
        .contains(
            "ERROR: <input>:1:32: expected type 'map(string, int)' but found 'map(string,"
                + " string)'\n"
                + " | {{'a': 1}: true, {'b': 'hello'}: false}\n"
                + " | ...............................^");
  }

  @Test
  public void mapKey_containsHeterogeneousLiteralsInDyn() throws Exception {
    CelAbstractSyntaxTree ast = CEL.compile("{1: true, dyn(2): false}").getAst();

    CelValidationResult result = CEL_VALIDATOR.validate(ast);

    assertThat(result.hasError()).isTrue();
    assertThat(result.getAllIssues()).hasSize(1);
    assertThat(result.getErrorString())
        .contains(
            "ERROR: <input>:1:17: expected type 'int' but found 'dyn'\n"
                + " | {1: true, dyn(2): false}\n"
                + " | ................^");
  }

  @Test
  public void mapValue_containsHeterogeneousLiterals() throws Exception {
    CelAbstractSyntaxTree ast = CEL.compile("{1: true, 2: 'hello'}").getAst();

    CelValidationResult result = CEL_VALIDATOR.validate(ast);

    assertThat(result.hasError()).isTrue();
    assertThat(result.getAllIssues()).hasSize(1);
    assertThat(result.getErrorString())
        .contains(
            "ERROR: <input>:1:12: expected type 'bool' but found 'string'\n"
                + " | {1: true, 2: 'hello'}\n"
                + " | ...........^");
  }

  @Test
  public void mapValue_containsHeterogeneousLiteralsInNestedMaps() throws Exception {
    CelAbstractSyntaxTree ast = CEL.compile("{1: {'a': true}, 2: {'b': 'hello'}}").getAst();

    CelValidationResult result = CEL_VALIDATOR.validate(ast);

    assertThat(result.hasError()).isTrue();
    assertThat(result.getAllIssues()).hasSize(1);
    assertThat(result.getErrorString())
        .contains(
            "ERROR: <input>:1:19: expected type 'map(string, bool)' but found 'map(string,"
                + " string)'\n"
                + " | {1: {'a': true}, 2: {'b': 'hello'}}\n"
                + " | ..................^");
  }

  @Test
  public void mapValue_containsHeterogeneousLiteralsInDyn() throws Exception {
    CelAbstractSyntaxTree ast = CEL.compile("{1: true, 2: dyn(false)}").getAst();

    CelValidationResult result = CEL_VALIDATOR.validate(ast);

    assertThat(result.hasError()).isTrue();
    assertThat(result.getAllIssues()).hasSize(1);
    assertThat(result.getErrorString())
        .contains(
            "ERROR: <input>:1:12: expected type 'bool' but found 'dyn'\n"
                + " | {1: true, 2: dyn(false)}\n"
                + " | ...........^");
  }

  @Test
  @TestParameters("{source: 'exemptFunction([''a'', 2])'}")
  @TestParameters("{source: 'exemptFunction({1: true, ''hello'': false})'}")
  @TestParameters("{source: 'exemptFunction({1: {''a'': true, 2: false}})'}")
  @TestParameters("{source: 'exemptFunction({{''a'': true, 2: false} : false})'}")
  @TestParameters("{source: '''%s''.format([[1], [2.0]])'}")
  @TestParameters("{source: '''%s''.format([[1, 2, [3.0, 4]]])'}")
  @TestParameters("{source: '''%d''.format([[[1, 2, [3.0, 4]]].size()])'}")
  @TestParameters("{source: '''%d''.format([[1, 2, size([3.0, 4])]])'}")
  @TestParameters("{source: '''%s''.format([[[1, 2, [3.0, 4]]][0]])'}")
  public void heterogeneousLiterals_inExemptFunction(String source) throws Exception {
    Cel cel =
        CelFactory.standardCelBuilder()
            .addFunctionDeclarations(
                newFunctionDeclaration(
                    "exemptFunction",
                    newGlobalOverload("exemptFunctionOverloadId", SimpleType.BOOL, SimpleType.DYN)),
                newFunctionDeclaration(
                    "format",
                    newMemberOverload(
                        "stringFormatOverloadId",
                        SimpleType.BOOL,
                        SimpleType.STRING,
                        SimpleType.DYN)))
            .addFunctionBindings(
                CelFunctionBinding.from("exemptFunctionOverloadId", Object.class, (arg) -> true),
                CelFunctionBinding.from(
                    "stringFormatOverloadId", String.class, Object.class, (str, arg) -> true))
            .build();
    CelValidator validator =
        CelValidatorFactory.standardCelValidatorBuilder(cel)
            .addAstValidators(HomogeneousLiteralValidator.newInstance("exemptFunction", "format"))
            .build();
    CelAbstractSyntaxTree ast = cel.compile(source).getAst();

    CelValidationResult result = validator.validate(ast);

    assertThat(result.hasError()).isFalse();
    assertThat(result.getAllIssues()).isEmpty();
    assertThat(cel.createProgram(ast).eval()).isInstanceOf(Boolean.class);
  }
}
