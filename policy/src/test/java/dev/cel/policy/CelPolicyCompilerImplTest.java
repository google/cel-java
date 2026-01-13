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

package dev.cel.policy;

import static com.google.common.base.Strings.isNullOrEmpty;
import static com.google.common.truth.Truth.assertThat;
import static dev.cel.policy.PolicyTestHelper.readFromYaml;
import static org.junit.Assert.assertThrows;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.testing.junit.testparameterinjector.TestParameter;
import com.google.testing.junit.testparameterinjector.TestParameterInjector;
import com.google.testing.junit.testparameterinjector.TestParameterValue;
import com.google.testing.junit.testparameterinjector.TestParameterValuesProvider;
import dev.cel.bundle.Cel;
import dev.cel.bundle.CelEnvironment;
import dev.cel.bundle.CelEnvironmentYamlParser;
import dev.cel.bundle.CelFactory;
import dev.cel.common.CelAbstractSyntaxTree;
import dev.cel.common.CelOptions;
import dev.cel.common.ast.CelConstant;
import dev.cel.common.ast.CelExpr.ExprKind;
import dev.cel.common.navigation.CelNavigableAst;
import dev.cel.common.navigation.CelNavigableExpr;
import dev.cel.common.navigation.TraversalOrder;
import dev.cel.common.types.OptionalType;
import dev.cel.common.types.SimpleType;
import dev.cel.expr.conformance.proto3.TestAllTypes;
import dev.cel.extensions.CelOptionalLibrary;
import dev.cel.parser.CelStandardMacro;
import dev.cel.parser.CelUnparserFactory;
import dev.cel.policy.PolicyTestHelper.K8sTagHandler;
import dev.cel.policy.PolicyTestHelper.PolicyTestSuite;
import dev.cel.policy.PolicyTestHelper.PolicyTestSuite.PolicyTestSection;
import dev.cel.policy.PolicyTestHelper.PolicyTestSuite.PolicyTestSection.PolicyTestCase;
import dev.cel.policy.PolicyTestHelper.PolicyTestSuite.PolicyTestSection.PolicyTestCase.PolicyTestInput;
import dev.cel.policy.PolicyTestHelper.TestYamlPolicy;
import dev.cel.runtime.CelFunctionBinding;
import dev.cel.runtime.CelLateFunctionBindings;
import dev.cel.testing.testdata.SingleFileProto.SingleFile;
import dev.cel.testing.testdata.proto3.StandaloneGlobalEnum;
import dev.cel.validator.CelAstValidator;
import dev.cel.validator.CelAstValidator.IssuesFactory;
import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(TestParameterInjector.class)
public final class CelPolicyCompilerImplTest {

  private static final CelPolicyParser POLICY_PARSER =
      CelPolicyParserFactory.newYamlParserBuilder().addTagVisitor(new K8sTagHandler()).build();
  private static final CelEnvironmentYamlParser ENVIRONMENT_PARSER =
      CelEnvironmentYamlParser.newInstance();
  private static final CelOptions CEL_OPTIONS =
      CelOptions.current().populateMacroCalls(true).build();

  @Test
  public void compileYamlPolicy_success(@TestParameter TestYamlPolicy yamlPolicy) throws Exception {
    // Read config and produce an environment to compile policies
    String configSource = yamlPolicy.readConfigYamlContent();
    CelEnvironment celEnvironment = ENVIRONMENT_PARSER.parse(configSource);
    Cel cel = celEnvironment.extend(newCel(), CEL_OPTIONS);
    // Read the policy source
    String policySource = yamlPolicy.readPolicyYamlContent();
    CelPolicy policy = POLICY_PARSER.parse(policySource);

    CelAbstractSyntaxTree ast =
        CelPolicyCompilerFactory.newPolicyCompiler(cel).build().compile(policy);

    assertThat(CelUnparserFactory.newUnparser().unparse(ast)).isEqualTo(yamlPolicy.getUnparsed());
  }

  @Test
  public void compileYamlPolicy_withImportsOnNestedRules() throws Exception {
    String policySource =
        "imports:\n"
            + "  - name: cel.expr.conformance.proto3.TestAllTypes\n"
            + "  - name: dev.cel.testing.testdata.SingleFile\n"
            + "rule:\n"
            + "  match:\n"
            + "  - rule:\n"
            + "      id: 'nested rule with imports'\n"
            + "      match:\n"
            + "        - condition: 'TestAllTypes{}.single_string == SingleFile{}.name'\n"
            + "          output: 'true'\n";
    Cel cel = newCel();
    CelPolicy policy = POLICY_PARSER.parse(policySource);

    CelAbstractSyntaxTree ast =
        CelPolicyCompilerFactory.newPolicyCompiler(cel).build().compile(policy);

    assertThat(ast.getResultType()).isEqualTo(OptionalType.create(SimpleType.BOOL));
  }

  @Test
  public void compileYamlPolicy_containsCompilationError_throws(
      @TestParameter TestErrorYamlPolicy testCase) throws Exception {
    // Read config and produce an environment to compile policies
    String configSource = testCase.readConfigYamlContent();
    CelEnvironment celEnvironment = ENVIRONMENT_PARSER.parse(configSource);
    Cel cel = celEnvironment.extend(newCel(), CEL_OPTIONS);
    // Read the policy source
    String policySource = testCase.readPolicyYamlContent();
    CelPolicy policy = POLICY_PARSER.parse(policySource, testCase.getPolicyFilePath());

    CelPolicyValidationException e =
        assertThrows(
            CelPolicyValidationException.class,
            () -> CelPolicyCompilerFactory.newPolicyCompiler(cel).build().compile(policy));

    assertThat(e).hasMessageThat().isEqualTo(testCase.readExpectedErrorsBaseline());
  }

  @Test
  public void compileYamlPolicy_multilineContainsError_throws(
      @TestParameter MultilineErrorTest testCase) throws Exception {
    String policyContent = testCase.yaml;
    CelPolicy policy = POLICY_PARSER.parse(policyContent);

    CelPolicyValidationException e =
        assertThrows(
            CelPolicyValidationException.class,
            () -> CelPolicyCompilerFactory.newPolicyCompiler(newCel()).build().compile(policy));

    assertThat(e).hasMessageThat().isEqualTo(testCase.expected);
  }

  @Test
  public void compileYamlPolicy_exceedsDefaultAstDepthLimit_throws() throws Exception {
    Cel cel = newCel().toCelBuilder().addVar("msg", SimpleType.DYN).build();
    String longExpr = "msg.b.c.d.e.f";
    String policyContent =
        String.format(
            "name: deeply_nested_ast\n" + "rule:\n" + "  match:\n" + "    - output: %s", longExpr);
    CelPolicy policy = POLICY_PARSER.parse(policyContent);

    CelPolicyValidationException e =
        assertThrows(
            CelPolicyValidationException.class,
            () ->
                CelPolicyCompilerFactory.newPolicyCompiler(cel)
                    .setAstDepthLimit(5)
                    .build()
                    .compile(policy));

    assertThat(e)
        .hasMessageThat()
        .isEqualTo("ERROR: <input>:-1:0: AST's depth exceeds the configured limit: 5.");
  }

  @Test
  public void compileYamlPolicy_constantFoldingFailure_throwsDuringComposition() throws Exception {
    String policyContent =
        "name: ast_with_div_by_zero\n" //
            + "rule:\n" //
            + "  match:\n" //
            + "    - output: 1 / 0";
    CelPolicy policy = POLICY_PARSER.parse(policyContent);

    CelPolicyValidationException e =
        assertThrows(
            CelPolicyValidationException.class,
            () -> CelPolicyCompilerFactory.newPolicyCompiler(newCel()).build().compile(policy));
    assertThat(e)
        .hasMessageThat()
        .isEqualTo(
            "Failed to optimize the composed policy. Reason: Constant folding failure. Failed to"
                + " evaluate subtree due to: evaluation error: / by zero");
  }

  @Test
  public void compileYamlPolicy_astDepthLimitCheckDisabled_doesNotThrow() throws Exception {
    String longExpr =
        "0+1+2+3+4+5+6+7+8+9+10+11+12+13+14+15+16+17+18+19+20+21+22+23+24+25+26+27+28+29+30+31+32+33+34+35+36+37+38+39+40+41+42+43+44+45+46+47+48+49+50";
    String policyContent =
        String.format(
            "name: deeply_nested_ast\n" + "rule:\n" + "  match:\n" + "    - output: %s", longExpr);
    CelPolicy policy = POLICY_PARSER.parse(policyContent);

    CelAbstractSyntaxTree ast =
        CelPolicyCompilerFactory.newPolicyCompiler(newCel())
            .setAstDepthLimit(-1)
            .build()
            .compile(policy);
    assertThat(ast).isNotNull();
  }

  @Test
  @SuppressWarnings("unchecked")
  public void evaluateYamlPolicy_withCanonicalTestData(
      @TestParameter(valuesProvider = EvaluablePolicyTestDataProvider.class)
          EvaluablePolicyTestData testData)
      throws Exception {
    // Setup
    // Read config and produce an environment to compile policies
    String configSource = testData.yamlPolicy.readConfigYamlContent();
    CelEnvironment celEnvironment = ENVIRONMENT_PARSER.parse(configSource);
    Cel cel = celEnvironment.extend(newCel(), CEL_OPTIONS);
    // Read the policy source
    String policySource = testData.yamlPolicy.readPolicyYamlContent();
    CelPolicy policy = POLICY_PARSER.parse(policySource);
    CelAbstractSyntaxTree expectedOutputAst = cel.compile(testData.testCase.getOutput()).getAst();
    Object expectedOutput = cel.createProgram(expectedOutputAst).eval();

    // Act
    // Compile then evaluate the policy
    CelAbstractSyntaxTree compiledPolicyAst =
        CelPolicyCompilerFactory.newPolicyCompiler(cel).build().compile(policy);
    ImmutableMap.Builder<String, Object> inputBuilder = ImmutableMap.builder();
    for (Map.Entry<String, PolicyTestInput> entry : testData.testCase.getInput().entrySet()) {
      String exprInput = entry.getValue().getExpr();
      if (isNullOrEmpty(exprInput)) {
        inputBuilder.put(entry.getKey(), entry.getValue().getValue());
      } else {
        CelAbstractSyntaxTree exprInputAst = cel.compile(exprInput).getAst();
        inputBuilder.put(entry.getKey(), cel.createProgram(exprInputAst).eval());
      }
    }
    Object evalResult = cel.createProgram(compiledPolicyAst).eval(inputBuilder.buildOrThrow());

    // Assert
    // Note that policies may either produce an optional or a non-optional result,
    // if all the rules included nested ones can always produce a default result when none of the
    // condition matches
    if (testData.yamlPolicy.producesOptionalResult()) {
      Optional<Object> policyOutput = (Optional<Object>) evalResult;
      if (policyOutput.isPresent()) {
        assertThat(policyOutput).hasValue(expectedOutput);
      } else {
        assertThat(policyOutput).isEmpty();
      }
    } else {
      assertThat(evalResult).isEqualTo(expectedOutput);
    }
  }

  @Test
  @SuppressWarnings("unchecked")
  public void evaluateYamlPolicy_nestedRuleProducesOptionalOutput() throws Exception {
    Cel cel = newCel();
    String policySource =
        "name: nested_rule_with_optional_result\n"
            + "rule:\n"
            + "  match:\n"
            + "    - rule:\n"
            + "        match:\n"
            + "          - condition: 'true'\n"
            + "            output: 'optional.of(true)'\n";
    CelPolicy policy = POLICY_PARSER.parse(policySource);
    CelAbstractSyntaxTree compiledPolicyAst =
        CelPolicyCompilerFactory.newPolicyCompiler(cel).build().compile(policy);

    Optional<Object> evalResult = (Optional<Object>) cel.createProgram(compiledPolicyAst).eval();

    // Result is Optional<Optional<Object>>
    assertThat(evalResult).hasValue(Optional.of(true));
  }

  static final class NoFooLiteralsValidator implements CelAstValidator {
    private static boolean isFooLiteral(CelNavigableExpr node) {
      return node.getKind().equals(ExprKind.Kind.CONSTANT)
          && node.expr().constant().getKind().equals(CelConstant.Kind.STRING_VALUE)
          && node.expr().constant().stringValue().equals("foo");
    }

    @Override
    public void validate(CelNavigableAst navigableAst, Cel cel, IssuesFactory issuesFactory) {
      navigableAst
          .getRoot()
          .descendants(TraversalOrder.POST_ORDER)
          .filter(NoFooLiteralsValidator::isFooLiteral)
          .forEach(node -> issuesFactory.addError(node.id(), "'foo' is a forbidden literal"));
    }
  }

  @Test
  public void evaluateYamlPolicy_validatorReportsErrors() throws Exception {
    Cel cel = newCel();
    String policySource =
        "name: nested_rule_with_forbidden_literal\n"
            + "rule:\n"
            + "  variables:\n"
            + "    - name: 'foo'\n"
            + "      expression: \"(true) ? 'bar' : 'foo'\"\n"
            + "  match:\n"
            + "    - condition: |\n"
            + "        variables.foo in ['foo', 'bar', 'foo']\n"
            + "      output: >\n"
            + "        'foo' == variables.foo\n";
    CelPolicy policy = POLICY_PARSER.parse(policySource);
    CelPolicyValidationException e =
        assertThrows(
            CelPolicyValidationException.class,
            () ->
                CelPolicyCompilerFactory.newPolicyCompiler(cel)
                    .addValidators(new NoFooLiteralsValidator())
                    .build()
                    .compile(policy));

    assertThat(e)
        .hasMessageThat()
        .contains(
            "ERROR: <input>:5:37: 'foo' is a forbidden literal\n"
                + " |       expression: \"(true) ? 'bar' : 'foo'\"\n"
                + " | ....................................^");
    assertThat(e)
        .hasMessageThat()
        .contains(
            "ERROR: <input>:8:27: 'foo' is a forbidden literal\n"
                + " |         variables.foo in ['foo', 'bar', 'foo']\n"
                + " | ..........................^");
    assertThat(e)
        .hasMessageThat()
        .contains(
            "ERROR: <input>:8:41: 'foo' is a forbidden literal\n"
                + " |         variables.foo in ['foo', 'bar', 'foo']\n"
                + " | ........................................^");
  }

  // If the condition fails to validate, then the compiler doesn't attempt to compile or validate
  // the output, so second test case for coverage.
  @Test
  public void evaluateYamlPolicy_validatorReportsOutput() throws Exception {
    Cel cel = newCel();
    String policySource =
        "name: nested_rule_with_forbidden_literal\n"
            + "rule:\n"
            + "  variables:\n"
            + "    - name: 'foo'\n"
            + "      expression: \"(true) ? 'bar' : 'foo'\"\n"
            + "  match:\n"
            + "    - output: >\n"
            + "        'foo' == variables.foo\n";
    CelPolicy policy = POLICY_PARSER.parse(policySource);
    CelPolicyValidationException e =
        assertThrows(
            CelPolicyValidationException.class,
            () ->
                CelPolicyCompilerFactory.newPolicyCompiler(cel)
                    .addValidators(new NoFooLiteralsValidator())
                    .build()
                    .compile(policy));

    assertThat(e)
        .hasMessageThat()
        .contains(
            "ERROR: <input>:5:37: 'foo' is a forbidden literal\n"
                + " |       expression: \"(true) ? 'bar' : 'foo'\"\n"
                + " | ....................................^");
    assertThat(e)
        .hasMessageThat()
        .contains(
            "ERROR: <input>:8:9: 'foo' is a forbidden literal\n"
                + " |         'foo' == variables.foo\n"
                + " | ........^");
  }

  @Test
  public void evaluateYamlPolicy_lateBoundFunction() throws Exception {
    String configSource =
        "name: late_bound_function_config\n"
            + "functions:\n"
            + "  - name: 'lateBoundFunc'\n"
            + "    overloads:\n"
            + "      - id: 'lateBoundFunc_string'\n"
            + "        args:\n"
            + "          - type_name: 'string'\n"
            + "        return:\n"
            + "          type_name: 'string'\n";
    CelEnvironment celEnvironment = ENVIRONMENT_PARSER.parse(configSource);
    Cel cel = celEnvironment.extend(newCel(), CelOptions.DEFAULT);
    String policySource =
        "name: late_bound_function_policy\n"
            + "rule:\n"
            + "  match:\n"
            + "   - output: |\n"
            + "       lateBoundFunc('foo')\n";
    CelPolicy policy = POLICY_PARSER.parse(policySource);
    CelAbstractSyntaxTree compiledPolicyAst =
        CelPolicyCompilerFactory.newPolicyCompiler(cel).build().compile(policy);
    String exampleValue = "bar";
    CelLateFunctionBindings lateFunctionBindings =
        CelLateFunctionBindings.from(
            CelFunctionBinding.from(
                "lateBoundFunc_string", String.class, arg -> arg + exampleValue));

    String evalResult =
        (String)
            cel.createProgram(compiledPolicyAst)
                .eval((unused) -> Optional.empty(), lateFunctionBindings);

    assertThat(evalResult).isEqualTo("foo" + exampleValue);
  }

  private static final class EvaluablePolicyTestData {
    private final TestYamlPolicy yamlPolicy;
    private final PolicyTestCase testCase;

    private EvaluablePolicyTestData(TestYamlPolicy yamlPolicy, PolicyTestCase testCase) {
      this.yamlPolicy = yamlPolicy;
      this.testCase = testCase;
    }
  }

  private static final class EvaluablePolicyTestDataProvider extends TestParameterValuesProvider {

    @Override
    protected ImmutableList<TestParameterValue> provideValues(Context context) throws Exception {
      ImmutableList.Builder<TestParameterValue> builder = ImmutableList.builder();
      for (TestYamlPolicy yamlPolicy : TestYamlPolicy.values()) {
        PolicyTestSuite testSuite = yamlPolicy.readTestYamlContent();
        for (PolicyTestSection testSection : testSuite.getSection()) {
          for (PolicyTestCase testCase : testSection.getTests()) {
            String testName =
                String.format(
                    "%s %s %s",
                    yamlPolicy.getPolicyName(), testSection.getName(), testCase.getName());
            builder.add(
                value(new EvaluablePolicyTestData(yamlPolicy, testCase)).withName(testName));
          }
        }
      }

      return builder.build();
    }
  }

  private static Cel newCel() {
    return CelFactory.standardCelBuilder()
        .setStandardMacros(CelStandardMacro.STANDARD_MACROS)
        .addCompilerLibraries(CelOptionalLibrary.INSTANCE)
        .addRuntimeLibraries(CelOptionalLibrary.INSTANCE)
        .addFileTypes(StandaloneGlobalEnum.getDescriptor().getFile())
        .addMessageTypes(TestAllTypes.getDescriptor(), SingleFile.getDescriptor())
        .setOptions(CEL_OPTIONS)
        .addFunctionBindings(
            CelFunctionBinding.from(
                "locationCode_string",
                String.class,
                (ip) -> {
                  switch (ip) {
                    case "10.0.0.1":
                      return "us";
                    case "10.0.0.2":
                      return "de";
                    default:
                      return "ir";
                  }
                }))
        .build();
  }

  private enum MultilineErrorTest {
    SINGLE_FOLDED(
        "name: \"errors\"\n"
            + "rule:\n"
            + "  match:\n"
            + "    - output: >\n"
            + "        'test'.format(variables.missing])",
        "ERROR: <input>:5:40: extraneous input ']' expecting ')'\n"
            + " |         'test'.format(variables.missing])\n"
            + " | .......................................^"),
    DOUBLE_FOLDED(
        "name: \"errors\"\n"
            + "rule:\n"
            + "  match:\n"
            + "    - output: >\n"
            + "        'test'.format(\n"
            + "        variables.missing])",
        "ERROR: <input>:6:26: extraneous input ']' expecting ')'\n"
            + " |         variables.missing])\n"
            + " | .........................^"),
    TRIPLE_FOLDED(
        "name: \"errors\"\n"
            + "rule:\n"
            + "  match:\n"
            + "    - output: >\n"
            + "        'test'.\n"
            + "        format(\n"
            + "        variables.missing])",
        "ERROR: <input>:7:26: extraneous input ']' expecting ')'\n"
            + " |         variables.missing])\n"
            + " | .........................^"),
    SINGLE_LITERAL(
        "name: \"errors\"\n"
            + "rule:\n"
            + "  match:\n"
            + "    - output: |\n"
            + "        'test'.format(variables.missing])",
        "ERROR: <input>:5:40: extraneous input ']' expecting ')'\n"
            + " |         'test'.format(variables.missing])\n"
            + " | .......................................^"),
    DOUBLE_LITERAL(
        "name: \"errors\"\n"
            + "rule:\n"
            + "  match:\n"
            + "    - output: |\n"
            + "        'test'.format(\n"
            + "        variables.missing])",
        "ERROR: <input>:6:26: extraneous input ']' expecting ')'\n"
            + " |         variables.missing])\n"
            + " | .........................^"),
    TRIPLE_LITERAL(
        "name: \"errors\"\n"
            + "rule:\n"
            + "  match:\n"
            + "    - output: |\n"
            + "        'test'.\n"
            + "        format(\n"
            + "        variables.missing])",
        "ERROR: <input>:7:26: extraneous input ']' expecting ')'\n"
            + " |         variables.missing])\n"
            + " | .........................^"),
    ;

    private final String yaml;
    private final String expected;

    MultilineErrorTest(String yaml, String expected) {
      this.yaml = yaml;
      this.expected = expected;
    }
  }

  private enum TestErrorYamlPolicy {
    COMPILE_ERRORS("compile_errors"),
    COMPOSE_ERRORS_CONFLICTING_OUTPUT("compose_errors_conflicting_output"),
    COMPOSE_ERRORS_CONFLICTING_SUBRULE("compose_errors_conflicting_subrule"),
    ERRORS_UNREACHABLE("errors_unreachable");

    private final String name;
    private final String policyFilePath;

    private String getPolicyFilePath() {
      return policyFilePath;
    }

    private String readPolicyYamlContent() throws IOException {
      return readFromYaml(String.format("policy/%s/policy.yaml", name));
    }

    private String readConfigYamlContent() throws IOException {
      return readFromYaml(String.format("policy/%s/config.yaml", name));
    }

    private String readExpectedErrorsBaseline() throws IOException {
      return readFromYaml(String.format("policy/%s/expected_errors.baseline", name));
    }

    TestErrorYamlPolicy(String name) {
      this.name = name;
      this.policyFilePath = String.format("%s/policy.yaml", name);
    }
  }
}
