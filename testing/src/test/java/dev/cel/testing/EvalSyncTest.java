// Copyright 2022 Google LLC
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

package dev.cel.testing;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.protobuf.Any;
import com.google.protobuf.BoolValue;
import com.google.protobuf.ByteString;
import com.google.protobuf.BytesValue;
import com.google.protobuf.Descriptors.FileDescriptor;
import com.google.protobuf.DoubleValue;
import com.google.protobuf.FloatValue;
import com.google.protobuf.Int32Value;
import com.google.protobuf.Int64Value;
import com.google.protobuf.Message;
import com.google.protobuf.StringValue;
import com.google.protobuf.UInt32Value;
import com.google.protobuf.UInt64Value;
import com.google.type.Expr;
import dev.cel.common.CelAbstractSyntaxTree;
import dev.cel.common.CelOptions;
import dev.cel.common.types.CelType;
import dev.cel.common.types.SimpleType;
import dev.cel.compiler.CelCompiler;
import dev.cel.compiler.CelCompilerFactory;
import dev.cel.parser.CelStandardMacro;
import dev.cel.runtime.Activation;
import dev.cel.runtime.InterpreterException;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import org.junit.experimental.runners.Enclosed;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Enclosed.class)
public final class EvalSyncTest {
  private static final ImmutableList<FileDescriptor> TEST_FILE_DESCRIPTOR =
      ImmutableList.of(Expr.getDescriptor().getFile());

  private static final CelOptions TEST_OPTIONS = CelOptions.current().build();

  private static final EvalSync EVAL = new EvalSync(TEST_FILE_DESCRIPTOR, TEST_OPTIONS);

  @RunWith(JUnit4.class)
  public static class EvalSyncApiTests {
    @Test
    public void fileDescriptorsTest() {
      assertThat(EVAL.fileDescriptors()).isEqualTo(TEST_FILE_DESCRIPTOR);
    }
  }

  @RunWith(Parameterized.class)
  public static class ProtoTypeAdapterTests {

    @Parameters
    public static List<Object[]> data() {
      return Arrays.asList(
          new Object[][] {
            {BoolValue.of(true), true},
            {BoolValue.of(false), false},
            {DoubleValue.of(1.5D), 1.5D},
            {FloatValue.of(1.5f), 1.5D},
            {StringValue.of("test"), "test"},
            {Int32Value.of(1), 1L},
            {Int64Value.of(1), 1L},
            {UInt32Value.of(1), 1L},
            {UInt64Value.of(1), 1L},
            {BytesValue.of(ByteString.copyFromUtf8("test")), ByteString.copyFromUtf8("test")},
          });
    }

    private final Message protoMessage;
    private final Object nativeValue;

    public ProtoTypeAdapterTests(Message protoMessage, Object nativeValue) {
      this.protoMessage = protoMessage;
      this.nativeValue = nativeValue;
    }

    @Test
    public void protoMessageAdapt_convertsToNativeValues() throws InterpreterException {
      assertThat(EVAL.adapt(protoMessage)).isEqualTo(nativeValue);
      assertThat(EVAL.adapt(Any.pack(protoMessage))).isEqualTo(nativeValue);
    }

    @Test
    public void nativeValueAdapt_doesNothing() throws InterpreterException {
      assertThat(EVAL.adapt(nativeValue)).isEqualTo(nativeValue);
    }
  }

  /**
   * Test cases to show that basic evaluation is working as intended. A comprehensive set of tests
   * can be found in {@code BaseInterpreterTest}.
   */
  @RunWith(Parameterized.class)
  public static class EvalWithoutActivationTests {
    private final String expr;
    private final Object evaluatedResult;

    private static final CelCompiler COMPILER =
        CelCompilerFactory.standardCelCompilerBuilder()
            .setOptions(TEST_OPTIONS)
            .setStandardMacros(CelStandardMacro.STANDARD_MACROS)
            .build();

    @Parameters
    public static List<Object[]> data() {
      return Arrays.asList(
          new Object[][] {
            {"1 < 2", true},
            {"1 + 2 + 3", 6L},
            {"1.9 + 2.0", 3.9},
            {"true == true", true},
          });
    }

    public EvalWithoutActivationTests(String expr, Object evaluatedResult) {
      this.expr = expr;
      this.evaluatedResult = evaluatedResult;
    }

    @Test
    public void evaluateExpr_returnsExpectedResult() throws Exception {
      CelAbstractSyntaxTree ast = COMPILER.compile(expr).getAst();
      assertThat(EVAL.eval(ast.toCheckedExpr(), Activation.EMPTY)).isEqualTo(evaluatedResult);
    }
  }

  @RunWith(Parameterized.class)
  public static class EvalWithActivationTests {
    private final String expr;
    private final Object paramValue;
    private final Object evaluatedResult;
    private final CelCompiler compiler;

    @Parameters
    public static List<Object[]> data() {
      return Arrays.asList(
          new Object[][] {
            {"x < 2", 1, SimpleType.INT, true},
            {"x + 2 + 3", 1, SimpleType.INT, 6L},
            {"x + 2.0", 1.9, SimpleType.DOUBLE, 3.9},
            {"x == true", true, SimpleType.BOOL, true},
          });
    }

    public EvalWithActivationTests(
        String expr, Object paramValue, CelType paramType, Object evaluatedResult) {
      this.expr = expr;
      this.paramValue = paramValue;
      this.evaluatedResult = evaluatedResult;
      this.compiler =
          CelCompilerFactory.standardCelCompilerBuilder()
              .setOptions(TEST_OPTIONS)
              .setStandardMacros(CelStandardMacro.STANDARD_MACROS)
              .addVar("x", paramType)
              .build();
    }

    @Test
    public void expr_returnsExpectedResult() throws Exception {
      CelAbstractSyntaxTree ast = compiler.compile(expr).getAst();
      assertThat(EVAL.eval(ast.toCheckedExpr(), Activation.of("x", paramValue)))
          .isEqualTo(evaluatedResult);
    }
  }
}
