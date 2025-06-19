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

import com.google.protobuf.Any;
import com.google.testing.junit.testparameterinjector.TestParameterInjector;
import com.google.testing.util.TestUtil;
import dev.cel.bundle.CelFactory;
import dev.cel.checker.ProtoTypeMask;
import dev.cel.expr.conformance.proto3.TestAllTypes;
import dev.cel.testing.testrunner.CelTestSuite.CelTestSection.CelTestCase;
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
            .setOutput(CelTestSuite.CelTestSection.CelTestCase.Output.ofResultValue(false))
            .build();

    TestRunnerLibrary.evaluateTestCase(
        simpleOutputTestCase,
        CelTestContext.newBuilder()
            .setCelExpression(
                CelExpressionSource.fromSource(
                    TestUtil.getSrcDir()
                        + "/google3/third_party/java/cel/testing/src/test/java/dev/cel/testing/testrunner/resources/empty_policy.yaml"))
            .build());
  }

  @Test
  public void triggerRunTest_evaluatePolicy_simpleBooleanOutput() throws Exception {
    CelTestCase simpleOutputTestCase =
        CelTestCase.newBuilder()
            .setName("simple_output_test_case")
            .setDescription("simple_output_test_case_description")
            .setOutput(CelTestSuite.CelTestSection.CelTestCase.Output.ofResultValue(false))
            .build();

    TestRunnerLibrary.runTest(
        simpleOutputTestCase,
        CelTestContext.newBuilder()
            .setCelExpression(
                CelExpressionSource.fromSource(
                    TestUtil.getSrcDir()
                        + "/google3/third_party/java/cel/testing/src/test/java/dev/cel/testing/testrunner/resources/empty_policy.yaml"))
            .build());
  }

  @Test
  public void triggerRunTest_evaluateRawExpr_simpleBooleanOutput() throws Exception {
    CelTestCase simpleOutputTestCase =
        CelTestCase.newBuilder()
            .setName("simple_output_test_case")
            .setDescription("simple_output_test_case_description")
            .setOutput(CelTestSuite.CelTestSection.CelTestCase.Output.ofResultValue(true))
            .build();

    TestRunnerLibrary.runTest(
        simpleOutputTestCase,
        CelTestContext.newBuilder()
            .setCelExpression(CelExpressionSource.fromRawExpr("1 > 0"))
            .build());
  }

  @Test
  public void runPolicyTest_outputMismatch_failureAssertion() throws Exception {
    CelTestCase simpleOutputTestCase =
        CelTestCase.newBuilder()
            .setName("output_mismatch_test_case")
            .setDescription("output_mismatch_test_case_description")
            .setOutput(CelTestSuite.CelTestSection.CelTestCase.Output.ofResultValue(true))
            .build();

    AssertionError thrown =
        assertThrows(
            AssertionError.class,
            () ->
                TestRunnerLibrary.evaluateTestCase(
                    simpleOutputTestCase,
                    CelTestContext.newBuilder()
                        .setCelExpression(
                            CelExpressionSource.fromSource(
                                TestUtil.getSrcDir()
                                    + "/google3/third_party/java/cel/testing/src/test/java/dev/cel/testing/testrunner/resources/empty_policy.yaml"))
                        .build()));

    assertThat(thrown).hasMessageThat().contains("modified: value.bool_value: true -> false");
  }

  @Test
  public void runPolicyTest_evaluatedContextExprNotProtoMessage_failure() throws Exception {
    CelTestCase simpleOutputTestCase =
        CelTestCase.newBuilder()
            .setName("output_mismatch_test_case")
            .setDescription("output_mismatch_test_case_description")
            .setInput(CelTestSuite.CelTestSection.CelTestCase.Input.ofContextExpr("1 > 2"))
            .setOutput(CelTestSuite.CelTestSection.CelTestCase.Output.ofResultValue(false))
            .build();

    IllegalArgumentException thrown =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                TestRunnerLibrary.evaluateTestCase(
                    simpleOutputTestCase,
                    CelTestContext.newBuilder()
                        .setCelExpression(
                            CelExpressionSource.fromSource(
                                TestUtil.getSrcDir()
                                    + "/google3/third_party/java/cel/testing/src/test/java/dev/cel/testing/testrunner/resources/empty_policy.yaml"))
                        .setCel(
                            CelFactory.standardCelBuilder()
                                .addFileTypes(TestAllTypes.getDescriptor().getFile())
                                .addProtoTypeMasks(
                                    ProtoTypeMask.ofAllFields(
                                            TestAllTypes.getDescriptor().getFullName())
                                        .withFieldsAsVariableDeclarations())
                                .build())
                        .build()));

    assertThat(thrown)
        .hasMessageThat()
        .contains("Context expression must evaluate to a proto message.");
  }

  @Test
  public void runPolicyTest_evaluationError_failureAssertion() throws Exception {
    CelTestCase simpleOutputTestCase =
        CelTestCase.newBuilder()
            .setName("evaluation_error_test_case")
            .setDescription("evaluation_error_test_case_description")
            .setOutput(CelTestSuite.CelTestSection.CelTestCase.Output.ofResultValue(false))
            .build();

    AssertionError thrown =
        assertThrows(
            AssertionError.class,
            () ->
                TestRunnerLibrary.evaluateTestCase(
                    simpleOutputTestCase,
                    CelTestContext.newBuilder()
                        .setCelExpression(
                            CelExpressionSource.fromSource(
                                TestUtil.getSrcDir()
                                    + "/google3/third_party/java/cel/testing/src/test/java/dev/cel/testing/testrunner/resources/eval_error_policy.yaml"))
                        .setCel(CelFactory.standardCelBuilder().build())
                        .build()));

    assertThat(thrown)
        .hasMessageThat()
        .contains(
            "Error: Evaluation failed for test case: evaluation_error_test_case."
                + " Error: evaluation error: / by zero");
  }

  @Test
  public void runExpressionTest_outputMismatch_failureAssertion() throws Exception {
    System.setProperty(
        "cel_expr",
        TestUtil.getSrcDir()
            + "/google3/third_party/java/cel/testing/src/test/java/dev/cel/testing/testrunner/output.textproto");

    CelTestCase simpleOutputTestCase =
        CelTestCase.newBuilder()
            .setName("output_mismatch_test_case")
            .setDescription("output_mismatch_test_case_description")
            .setOutput(CelTestSuite.CelTestSection.CelTestCase.Output.ofResultValue(true))
            .build();

    AssertionError thrown =
        assertThrows(
            AssertionError.class,
            () ->
                TestRunnerLibrary.runTest(
                    simpleOutputTestCase,
                    CelTestContext.newBuilder()
                        .setCelExpression(
                            CelExpressionSource.fromSource(
                                TestUtil.getSrcDir()
                                    + "/google3/third_party/java/cel/testing/src/test/java/dev/cel/testing/testrunner/output.textproto"))
                        .setCel(CelFactory.standardCelBuilder().build())
                        .build()));

    assertThat(thrown).hasMessageThat().contains("modified: value.bool_value: true -> false");
  }

  @Test
  public void runTest_illegalFileType_failure() throws Exception {
    CelTestCase simpleOutputTestCase =
        CelTestCase.newBuilder()
            .setName("illegal_file_type_test_case")
            .setDescription("illegal_file_type_test_case_description")
            .setOutput(CelTestSuite.CelTestSection.CelTestCase.Output.ofNoOutput())
            .build();

    IllegalArgumentException thrown =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                TestRunnerLibrary.runTest(
                    simpleOutputTestCase,
                    CelTestContext.newBuilder()
                        .setCelExpression(CelExpressionSource.fromSource("output.txt"))
                        .build()));

    assertThat(thrown).hasMessageThat().contains("Unsupported expression file type: output.txt");
  }

  @Test
  public void runTest_missingProtoDescriptors_failure() throws Exception {
    CelTestCase simpleOutputTestCase =
        CelTestCase.newBuilder()
            .setName("missing_file_descriptor_set_path_test_case")
            .setDescription("missing_file_descriptor_set_path_test_case_description")
            .setInput(
                CelTestSuite.CelTestSection.CelTestCase.Input.ofContextMessage(
                    Any.pack(TestAllTypes.getDefaultInstance())))
            .setOutput(CelTestSuite.CelTestSection.CelTestCase.Output.ofNoOutput())
            .build();

    IllegalArgumentException thrown =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                TestRunnerLibrary.runTest(
                    simpleOutputTestCase,
                    CelTestContext.newBuilder()
                        .setCelExpression(CelExpressionSource.fromRawExpr("true"))
                        .build()));

    assertThat(thrown)
        .hasMessageThat()
        .contains("Proto descriptors are required for unpacking Any messages.");
  }
}
