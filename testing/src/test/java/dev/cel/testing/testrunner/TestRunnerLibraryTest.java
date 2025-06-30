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
import static org.junit.Assert.assertThrows;

import com.google.common.collect.ImmutableMap;
import com.google.testing.junit.testparameterinjector.TestParameterInjector;
import dev.cel.bundle.CelFactory;
import dev.cel.testing.testrunner.CelTestSuite.CelTestSection.CelTestCase;
import dev.cel.testing.testrunner.TestRunnerLibrary.CelExprFileSource;
import dev.cel.runtime.CelFunctionBinding;
import dev.cel.runtime.CelLateFunctionBindings;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(TestParameterInjector.class)
public class TestRunnerLibraryTest {

  @Before
  public void setUp() {
    System.setProperty("is_raw_expr", "False");
  }

  @Test
  public void runPolicyTest_simpleBooleanOutput() throws Exception {
    CelTestCase simpleOutputTestCase =
            CelTestCase.newBuilder()
                    .setName("simple_output_test_case")
                    .setDescription("simple_output_test_case_description")
                    .setInput(
                            CelTestSuite.CelTestSection.CelTestCase.Input.ofBindings(
                                    ImmutableMap.of(
                                            "a",
                                            CelTestSuite.CelTestSection.CelTestCase.Input.Binding.ofExpr("'foo'"))))
                    .setOutput(CelTestSuite.CelTestSection.CelTestCase.Output.ofResultValue(true))
                    .build();

    System.setProperty(
            "config_path",
            "testng/src/test/resources/policy/late_function_binding/config.yaml");

    CelExprFileSource celExprFileSource =
            CelExprFileSource.fromFile(
                    "test/resources/policy/late_function_binding/policy.yaml");

    TestRunnerLibrary.evaluateTestCase(
            simpleOutputTestCase,
            CelTestContext.newBuilder()
                    .setCelLateFunctionBindings(
                            CelLateFunctionBindings.from(
                                    CelFunctionBinding.from("foo_id", String.class, (String a) -> a.equals("foo")),
                                    CelFunctionBinding.from("bar_id", String.class, (String a) -> a.equals("bar"))))
                    .build(),
            celExprFileSource);
  }
//
//  @Test
//  public void runPolicyTest_outputMismatch_failureAssertion() throws Exception {
//    CelTestCase simpleOutputTestCase =
//        CelTestCase.newBuilder()
//            .setName("output_mismatch_test_case")
//            .setDescription("output_mismatch_test_case_description")
//            .setInput(CelTestSuite.CelTestSection.CelTestCase.Input.ofBindings(ImmutableMap.of()))
//            .setOutput(CelTestSuite.CelTestSection.CelTestCase.Output.ofResultValue(true))
//            .build();
//    CelExprFileSource celExprFileSource =
//        CelExprFileSource.fromFile(
//            TestUtil.getSrcDir()
//                + "/google3/third_party/java/cel/testing/src/test/java/dev/cel/testing/testrunner/resources/empty_policy.yaml");
//
//    AssertionError thrown =
//        assertThrows(
//            AssertionError.class,
//            () ->
//                TestRunnerLibrary.evaluateTestCase(
//                    simpleOutputTestCase, CelTestContext.newBuilder().build(), celExprFileSource));
//
//    assertThat(thrown).hasMessageThat().contains("modified: value.bool_value: true -> false");
//  }
//
//  @Test
//  public void runPolicyTest_evaluatedContextExprNotProtoMessage_failure() throws Exception {
//    CelTestCase simpleOutputTestCase =
//        CelTestCase.newBuilder()
//            .setName("output_mismatch_test_case")
//            .setDescription("output_mismatch_test_case_description")
//            .setInput(CelTestSuite.CelTestSection.CelTestCase.Input.ofContextExpr("1 > 2"))
//            .setOutput(CelTestSuite.CelTestSection.CelTestCase.Output.ofResultValue(false))
//            .build();
//    CelExprFileSource celExprFileSource =
//        CelExprFileSource.fromFile(
//            TestUtil.getSrcDir()
//                + "/google3/third_party/java/cel/testing/src/test/java/dev/cel/testing/testrunner/resources/empty_policy.yaml");
//
//    IllegalArgumentException thrown =
//        assertThrows(
//            IllegalArgumentException.class,
//            () ->
//                TestRunnerLibrary.evaluateTestCase(
//                    simpleOutputTestCase,
//                    CelTestContext.newBuilder()
//                        .setCel(
//                            CelFactory.standardCelBuilder()
//                                .addFileTypes(TestAllTypes.getDescriptor().getFile())
//                                .addProtoTypeMasks(
//                                    ProtoTypeMask.ofAllFields(
//                                            TestAllTypes.getDescriptor().getFullName())
//                                        .withFieldsAsVariableDeclarations())
//                                .build())
//                        .build(),
//                    celExprFileSource));
//
//    assertThat(thrown)
//        .hasMessageThat()
//        .contains("Context expression must evaluate to a proto message.");
//  }
//
//  @Test
//  public void runPolicyTest_evaluationError_failureAssertion() throws Exception {
//    CelTestCase simpleOutputTestCase =
//        CelTestCase.newBuilder()
//            .setName("evaluation_error_test_case")
//            .setDescription("evaluation_error_test_case_description")
//            .setInput(CelTestSuite.CelTestSection.CelTestCase.Input.ofNoInput())
//            .setOutput(CelTestSuite.CelTestSection.CelTestCase.Output.ofResultValue(false))
//            .build();
//    CelExprFileSource celExprFileSource =
//        CelExprFileSource.fromFile(
//            TestUtil.getSrcDir()
//                + "/google3/third_party/java/cel/testing/src/test/java/dev/cel/testing/testrunner/resources/eval_error_policy.yaml");
//
//    AssertionError thrown =
//        assertThrows(
//            AssertionError.class,
//            () ->
//                TestRunnerLibrary.evaluateTestCase(
//                    simpleOutputTestCase,
//                    CelTestContext.newBuilder()
//                        .setCel(CelFactory.standardCelBuilder().build())
//                        .build(),
//                    celExprFileSource));
//
//    assertThat(thrown)
//        .hasMessageThat()
//        .contains(
//            "Error: Evaluation failed for test case: evaluation_error_test_case."
//                + " Error: evaluation error: / by zero");
//  }
//
//  @Test
//  public void runExpressionTest_outputMismatch_failureAssertion() throws Exception {
//    System.setProperty(
//        "cel_expr",
//        TestUtil.getSrcDir()
//            + "/google3/third_party/java/cel/testing/src/test/java/dev/cel/testing/testrunner/output.textproto");
//
//    CelTestCase simpleOutputTestCase =
//        CelTestCase.newBuilder()
//            .setName("output_mismatch_test_case")
//            .setDescription("output_mismatch_test_case_description")
//            .setInput(CelTestSuite.CelTestSection.CelTestCase.Input.ofNoInput())
//            .setOutput(CelTestSuite.CelTestSection.CelTestCase.Output.ofResultValue(true))
//            .build();
//
//    AssertionError thrown =
//        assertThrows(
//            AssertionError.class,
//            () ->
//                TestRunnerLibrary.runTest(
//                    simpleOutputTestCase,
//                    CelTestContext.newBuilder()
//                        .setCel(CelFactory.standardCelBuilder().build())
//                        .build()));
//
//    assertThat(thrown).hasMessageThat().contains("modified: value.bool_value: true -> false");
//  }
//
//  @Test
//  public void runTest_illegalFileType_failure() throws Exception {
//    System.setProperty("cel_expr", "output.txt");
//
//    CelTestCase simpleOutputTestCase =
//        CelTestCase.newBuilder()
//            .setName("illegal_file_type_test_case")
//            .setDescription("illegal_file_type_test_case_description")
//            .setInput(CelTestSuite.CelTestSection.CelTestCase.Input.ofNoInput())
//            .setOutput(CelTestSuite.CelTestSection.CelTestCase.Output.ofNoOutput())
//            .build();
//
//    IllegalArgumentException thrown =
//        assertThrows(
//            IllegalArgumentException.class,
//            () ->
//                TestRunnerLibrary.runTest(
//                    simpleOutputTestCase, CelTestContext.newBuilder().build()));
//
//    assertThat(thrown).hasMessageThat().contains("Unsupported expression type: output.txt");
//  }
}
