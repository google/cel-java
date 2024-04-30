// Copyright 2022 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License aj
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

import com.google.api.expr.v1alpha1.Constant;
import com.google.api.expr.v1alpha1.Expr;
import com.google.api.expr.v1alpha1.Type.PrimitiveType;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.protobuf.Any;
import com.google.protobuf.BoolValue;
import com.google.protobuf.ByteString;
import com.google.protobuf.DescriptorProtos.FileDescriptorSet;
import com.google.protobuf.DynamicMessage;
import com.google.rpc.context.AttributeContext;
import com.google.testing.junit.testparameterinjector.TestParameter;
import com.google.testing.junit.testparameterinjector.TestParameterInjector;
import com.google.testing.junit.testparameterinjector.TestParameters;
import dev.cel.bundle.Cel;
import dev.cel.bundle.CelFactory;
import dev.cel.common.CelAbstractSyntaxTree;
import dev.cel.common.CelOptions;
import dev.cel.common.CelProtoV1Alpha1AbstractSyntaxTree;
import dev.cel.common.CelSource;
import dev.cel.common.ast.CelConstant;
import dev.cel.common.ast.CelExpr;
import dev.cel.common.ast.CelExpr.ExprKind.Kind;
import dev.cel.common.types.CelV1AlphaTypes;
import dev.cel.common.types.SimpleType;
import dev.cel.common.types.StructTypeReference;
import dev.cel.compiler.CelCompiler;
import dev.cel.compiler.CelCompilerFactory;
import dev.cel.parser.CelStandardMacro;
import dev.cel.parser.CelUnparserFactory;
import dev.cel.testing.testdata.proto3.TestAllTypesProto.TestAllTypes;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(TestParameterInjector.class)
public class CelRuntimeTest {

  @Test
  public void evaluate_anyPackedEqualityUsingProtoDifferencer_success() throws Exception {
    Cel cel =
        CelFactory.standardCelBuilder()
            .setOptions(CelOptions.current().enableProtoDifferencerEquality(true).build())
            .addVar("a", StructTypeReference.create(AttributeContext.getDescriptor().getFullName()))
            .addVar("b", StructTypeReference.create(AttributeContext.getDescriptor().getFullName()))
            .addMessageTypes(AttributeContext.getDescriptor())
            .build();
    CelAbstractSyntaxTree ast = cel.compile("a == b").getAst();
    CelRuntime.Program program = cel.createProgram(ast);

    Object evaluatedResult =
        program.eval(
            ImmutableMap.of(
                "a",
                AttributeContext.newBuilder()
                    .addExtensions(
                        Any.newBuilder()
                            .setTypeUrl("type.googleapis.com/google.rpc.context.AttributeContext")
                            .setValue(ByteString.copyFromUtf8("\032\000:\000"))
                            .build())
                    .build(),
                "b",
                AttributeContext.newBuilder()
                    .addExtensions(
                        Any.newBuilder()
                            .setTypeUrl("type.googleapis.com/google.rpc.context.AttributeContext")
                            .setValue(ByteString.copyFromUtf8(":\000\032\000"))
                            .build())
                    .build()));

    assertThat(evaluatedResult).isEqualTo(true);
  }

  @Test
  public void evaluate_v1alpha1CheckedExpr() throws Exception {
    // Note: v1alpha1 proto support exists only to help migrate existing consumers.
    // New users of CEL should use the canonical protos instead (I.E: dev.cel.expr)
    com.google.api.expr.v1alpha1.CheckedExpr checkedExpr =
        com.google.api.expr.v1alpha1.CheckedExpr.newBuilder()
            .setExpr(
                Expr.newBuilder()
                    .setId(1)
                    .setConstExpr(Constant.newBuilder().setStringValue("Hello world!").build())
                    .build())
            .putTypeMap(1, CelV1AlphaTypes.create(PrimitiveType.STRING))
            .build();
    CelRuntime celRuntime = CelRuntimeFactory.standardCelRuntimeBuilder().build();
    CelRuntime.Program program =
        celRuntime.createProgram(
            CelProtoV1Alpha1AbstractSyntaxTree.fromCheckedExpr(checkedExpr).getAst());

    String evaluatedResult = (String) program.eval();

    assertThat(evaluatedResult).isEqualTo("Hello world!");
  }

  @Test
  public void newWellKnownTypeMessage_withDifferentDescriptorInstance() throws Exception {
    CelCompiler celCompiler =
        CelCompilerFactory.standardCelCompilerBuilder()
            .addMessageTypes(BoolValue.getDescriptor())
            .build();
    CelRuntime celRuntime =
        CelRuntimeFactory.standardCelRuntimeBuilder()
            .addFileTypes(
                FileDescriptorSet.newBuilder()
                    .addFile(
                        BoolValue.getDescriptor().getFile().toProto()) // Copy the WKT descriptor
                    .build())
            .build();

    CelAbstractSyntaxTree ast =
        celCompiler.compile("google.protobuf.BoolValue{value: false}").getAst();

    assertThat(celRuntime.createProgram(ast).eval()).isEqualTo(false);
  }

  @Test
  public void newWellKnownTypeMessage_inDynamicMessage_withSetTypeFactory() throws Exception {
    CelCompiler celCompiler =
        CelCompilerFactory.standardCelCompilerBuilder()
            .addMessageTypes(BoolValue.getDescriptor())
            .build();
    CelRuntime celRuntime =
        CelRuntimeFactory.standardCelRuntimeBuilder()
            .setTypeFactory(
                (typeName) ->
                    typeName.equals("google.protobuf.BoolValue")
                        ? DynamicMessage.newBuilder(BoolValue.getDescriptor())
                        : null)
            .build();

    CelAbstractSyntaxTree ast =
        celCompiler.compile("google.protobuf.BoolValue{value: false}").getAst();

    assertThat(celRuntime.createProgram(ast).eval()).isEqualTo(false);
  }

  @Test
  public void newWellKnownTypeMessage_inAnyMessage_withDifferentDescriptorInstance()
      throws Exception {
    FileDescriptorSet fds =
        FileDescriptorSet.newBuilder()
            // Copy the WKT descriptors
            .addFile(Any.getDescriptor().getFile().toProto())
            .addFile(BoolValue.getDescriptor().getFile().toProto())
            .build();
    CelCompiler celCompiler =
        CelCompilerFactory.standardCelCompilerBuilder().addFileTypes(fds).build();
    CelRuntime celRuntime =
        CelRuntimeFactory.standardCelRuntimeBuilder()
            .build();

    CelAbstractSyntaxTree ast =
        celCompiler
            .compile(
                "google.protobuf.Any{type_url: 'types.googleapis.com/google.protobuf.DoubleValue'}")
            .getAst();

    assertThat(celRuntime.createProgram(ast).eval()).isEqualTo(0.0d);
  }

  @Test
  public void newWellKnownTypeMessage_inAnyMessage_withSetTypeFactory() throws Exception {
    FileDescriptorSet fds =
        FileDescriptorSet.newBuilder()
            // Copy the WKT descriptors
            .addFile(Any.getDescriptor().getFile().toProto())
            .addFile(BoolValue.getDescriptor().getFile().toProto())
            .build();
    CelCompiler celCompiler =
        CelCompilerFactory.standardCelCompilerBuilder().addFileTypes(fds).build();
    CelRuntime celRuntime =
        CelRuntimeFactory.standardCelRuntimeBuilder()
            .setTypeFactory(
                (typeName) ->
                    typeName.equals("google.protobuf.Any")
                        ? Any.newBuilder().setTypeUrl("google.protobuf.DoubleValue")
                        : null)
            .build();

    CelAbstractSyntaxTree ast =
        celCompiler
            .compile(
                "google.protobuf.Any{type_url: 'types.googleapis.com/google.protobuf.DoubleValue'}")
            .getAst();

    assertThat(celRuntime.createProgram(ast).eval()).isEqualTo(0.0d);
  }

  @Test
  public void trace_callExpr_identifyFalseBranch() throws Exception {
    AtomicReference<CelExpr> capturedExpr = new AtomicReference<>();
    CelEvaluationListener listener =
        (expr, res) -> {
          if (res instanceof Boolean && !(boolean) res && capturedExpr.get() == null) {
            capturedExpr.set(expr);
          }
        };
    Cel cel =
        CelFactory.standardCelBuilder()
            .addVar("a", SimpleType.INT)
            .addVar("b", SimpleType.INT)
            .addVar("c", SimpleType.INT)
            .build();
    CelAbstractSyntaxTree ast = cel.compile("a < 0 && b < 0 && c < 0").getAst();

    boolean result =
        (boolean) cel.createProgram(ast).trace(ImmutableMap.of("a", -1, "b", 1, "c", -4), listener);

    assertThat(result).isFalse();
    // Demonstrate that "b < 0" is what caused the expression to be false
    CelAbstractSyntaxTree subtree =
        CelAbstractSyntaxTree.newParsedAst(capturedExpr.get(), CelSource.newBuilder().build());
    assertThat(CelUnparserFactory.newUnparser().unparse(subtree)).isEqualTo("b < 0");
  }

  @Test
  public void trace_constant() throws Exception {
    CelEvaluationListener listener =
        (expr, res) -> {
          assertThat(res).isEqualTo("hello world");
          assertThat(expr.constant().getKind()).isEqualTo(CelConstant.Kind.STRING_VALUE);
        };
    Cel cel = CelFactory.standardCelBuilder().build();
    CelAbstractSyntaxTree ast = cel.compile("'hello world'").getAst();

    String result = (String) cel.createProgram(ast).trace(listener);

    assertThat(result).isEqualTo("hello world");
  }

  @Test
  public void trace_ident() throws Exception {
    CelEvaluationListener listener =
        (expr, res) -> {
          assertThat(res).isEqualTo("test");
          assertThat(expr.ident().name()).isEqualTo("a");
        };
    Cel cel = CelFactory.standardCelBuilder().addVar("a", SimpleType.STRING).build();
    CelAbstractSyntaxTree ast = cel.compile("a").getAst();

    String result = (String) cel.createProgram(ast).trace(ImmutableMap.of("a", "test"), listener);

    assertThat(result).isEqualTo("test");
  }

  @Test
  public void trace_select() throws Exception {
    CelEvaluationListener listener =
        (expr, res) -> {
          if (expr.exprKind().getKind().equals(Kind.SELECT)) {
            assertThat(res).isEqualTo(3L);
            assertThat(expr.select().field()).isEqualTo("single_int64");
          }
        };
    Cel cel =
        CelFactory.standardCelBuilder()
            .addMessageTypes(TestAllTypes.getDescriptor())
            .setContainer("dev.cel.testing.testdata.proto3")
            .build();
    CelAbstractSyntaxTree ast = cel.compile("TestAllTypes{single_int64: 3}.single_int64").getAst();

    Long result = (Long) cel.createProgram(ast).trace(listener);

    assertThat(result).isEqualTo(3L);
  }

  @Test
  public void trace_createStruct() throws Exception {
    CelEvaluationListener listener =
        (expr, res) -> {
          assertThat(res).isEqualTo(TestAllTypes.getDefaultInstance());
          assertThat(expr.createStruct().messageName()).isEqualTo("TestAllTypes");
        };
    Cel cel =
        CelFactory.standardCelBuilder()
            .addMessageTypes(TestAllTypes.getDescriptor())
            .setContainer("dev.cel.testing.testdata.proto3")
            .build();
    CelAbstractSyntaxTree ast = cel.compile("TestAllTypes{}").getAst();

    TestAllTypes result = (TestAllTypes) cel.createProgram(ast).trace(listener);

    assertThat(result).isEqualTo(TestAllTypes.getDefaultInstance());
  }

  @Test
  @SuppressWarnings("unchecked") // Test only
  public void trace_createList() throws Exception {
    CelEvaluationListener listener =
        (expr, res) -> {
          if (expr.exprKind().getKind().equals(Kind.LIST)) {
            assertThat((List<Long>) res).containsExactly(1L, 2L, 3L);
            assertThat(expr.createList().elements()).hasSize(3);
          }
        };
    Cel cel = CelFactory.standardCelBuilder().build();
    CelAbstractSyntaxTree ast = cel.compile("[1, 2, 3]").getAst();

    List<Long> result = (List<Long>) cel.createProgram(ast).trace(listener);

    assertThat(result).containsExactly(1L, 2L, 3L);
  }

  @Test
  @SuppressWarnings("unchecked") // Test only
  public void trace_createMap() throws Exception {
    CelEvaluationListener listener =
        (expr, res) -> {
          if (expr.exprKind().getKind().equals(Kind.MAP)) {
            assertThat((Map<Long, String>) res).containsExactly(1L, "a");
            assertThat(expr.createMap().entries()).hasSize(1);
          }
        };
    Cel cel = CelFactory.standardCelBuilder().build();
    CelAbstractSyntaxTree ast = cel.compile("{1: 'a'}").getAst();

    Map<Long, String> result = (Map<Long, String>) cel.createProgram(ast).trace(listener);

    assertThat(result).containsExactly(1L, "a");
  }

  @Test
  public void trace_comprehension() throws Exception {
    CelEvaluationListener listener =
        (expr, res) -> {
          if (expr.exprKind().getKind().equals(Kind.COMPREHENSION)) {
            assertThat(expr.comprehension().iterVar()).isEqualTo("i");
          }
        };
    Cel cel =
        CelFactory.standardCelBuilder().setStandardMacros(CelStandardMacro.STANDARD_MACROS).build();
    CelAbstractSyntaxTree ast = cel.compile("[true].exists(i, i)").getAst();

    boolean result = (boolean) cel.createProgram(ast).trace(listener);

    assertThat(result).isTrue();
  }

  @Test
  public void trace_withMessageInput() throws Exception {
    CelEvaluationListener listener =
        (expr, res) -> {
          assertThat(res).isEqualTo(6L);
          assertThat(expr.ident().name()).isEqualTo("single_int64");
        };
    Cel cel =
        CelFactory.standardCelBuilder()
            .addMessageTypes(TestAllTypes.getDescriptor())
            .addVar("single_int64", SimpleType.INT)
            .build();
    CelAbstractSyntaxTree ast = cel.compile("single_int64").getAst();

    Long result =
        (Long)
            cel.createProgram(ast)
                .trace(TestAllTypes.newBuilder().setSingleInt64(6L).build(), listener);

    assertThat(result).isEqualTo(6L);
  }

  @Test
  public void trace_withVariableResolver() throws Exception {
    CelEvaluationListener listener =
        (expr, res) -> {
          assertThat(res).isEqualTo("hello");
          assertThat(expr.ident().name()).isEqualTo("variable");
        };
    Cel cel =
        CelFactory.standardCelBuilder()
            .addMessageTypes(TestAllTypes.getDescriptor())
            .addVar("variable", SimpleType.STRING)
            .build();
    CelAbstractSyntaxTree ast = cel.compile("variable").getAst();
    CelVariableResolver resolver =
        (name) -> name.equals("variable") ? Optional.of("hello") : Optional.empty();

    String result = (String) cel.createProgram(ast).trace(resolver, listener);

    assertThat(result).isEqualTo("hello");
  }

  @Test
  public void trace_shortCircuitingDisabled_logicalAndAllBranchesVisited(
      @TestParameter boolean first, @TestParameter boolean second, @TestParameter boolean third)
      throws Exception {
    String expression = String.format("%s && %s && %s", first, second, third);
    ImmutableList.Builder<Boolean> branchResults = ImmutableList.builder();
    CelEvaluationListener listener =
        (expr, res) -> {
          if (expr.constantOrDefault().getKind().equals(CelConstant.Kind.BOOLEAN_VALUE)) {
            branchResults.add((Boolean) res);
          }
        };
    Cel celWithShortCircuit =
        CelFactory.standardCelBuilder()
            .setOptions(CelOptions.current().enableShortCircuiting(true).build())
            .build();
    Cel cel =
        CelFactory.standardCelBuilder()
            .setOptions(CelOptions.current().enableShortCircuiting(false).build())
            .build();
    CelAbstractSyntaxTree ast = cel.compile(expression).getAst();

    boolean result = (boolean) cel.createProgram(ast).trace(listener);
    boolean shortCircuitedResult =
        (boolean)
            celWithShortCircuit
                .createProgram(celWithShortCircuit.compile(expression).getAst())
                .eval();

    assertThat(result).isEqualTo(shortCircuitedResult);
    assertThat(branchResults.build()).containsExactly(first, second, third).inOrder();
  }

  @Test
  @TestParameters("{source: 'false && false && x'}")
  @TestParameters("{source: 'false && x && false'}")
  @TestParameters("{source: 'x && false && false'}")
  public void trace_shortCircuitingDisabledWithUnknownsAndedToFalse_returnsFalse(String source)
      throws Exception {
    ImmutableList.Builder<Object> branchResults = ImmutableList.builder();
    CelEvaluationListener listener =
        (expr, res) -> {
          if (expr.constantOrDefault().getKind().equals(CelConstant.Kind.BOOLEAN_VALUE)
              || expr.identOrDefault().name().equals("x")) {
            if (InterpreterUtil.isUnknown(res)) {
              branchResults.add("x"); // Swap unknown result with a sentinel value for testing
            } else {
              branchResults.add(res);
            }
          }
        };
    Cel cel =
        CelFactory.standardCelBuilder()
            .addVar("x", SimpleType.BOOL)
            .setOptions(CelOptions.current().enableShortCircuiting(false).build())
            .build();
    CelAbstractSyntaxTree ast = cel.compile(source).getAst();

    boolean result = (boolean) cel.createProgram(ast).trace(listener);

    assertThat(result).isFalse();
    assertThat(branchResults.build()).containsExactly(false, false, "x");
  }

  @Test
  @TestParameters("{source: 'true && true && x'}")
  @TestParameters("{source: 'true && x && true'}")
  @TestParameters("{source: 'x && true && true'}")
  public void trace_shortCircuitingDisabledWithUnknownAndedToTrue_returnsUnknown(String source)
      throws Exception {
    ImmutableList.Builder<Object> branchResults = ImmutableList.builder();
    CelEvaluationListener listener =
        (expr, res) -> {
          if (expr.constantOrDefault().getKind().equals(CelConstant.Kind.BOOLEAN_VALUE)
              || expr.identOrDefault().name().equals("x")) {
            branchResults.add(res);
          }
        };
    Cel cel =
        CelFactory.standardCelBuilder()
            .addVar("x", SimpleType.BOOL)
            .setOptions(CelOptions.current().enableShortCircuiting(false).build())
            .build();
    CelAbstractSyntaxTree ast = cel.compile(source).getAst();

    Object unknownResult = cel.createProgram(ast).trace(listener);

    assertThat(InterpreterUtil.isUnknown(unknownResult)).isTrue();
    assertThat(branchResults.build()).containsExactly(true, true, unknownResult);
  }

  @Test
  public void trace_shortCircuitingDisabled_logicalOrAllBranchesVisited(
      @TestParameter boolean first, @TestParameter boolean second, @TestParameter boolean third)
      throws Exception {
    String expression = String.format("%s || %s || %s", first, second, third);
    ImmutableList.Builder<Boolean> branchResults = ImmutableList.builder();
    CelEvaluationListener listener =
        (expr, res) -> {
          if (expr.constantOrDefault().getKind().equals(CelConstant.Kind.BOOLEAN_VALUE)) {
            branchResults.add((Boolean) res);
          }
        };
    Cel celWithShortCircuit =
        CelFactory.standardCelBuilder()
            .setOptions(CelOptions.current().enableShortCircuiting(true).build())
            .build();
    Cel cel =
        CelFactory.standardCelBuilder()
            .setOptions(CelOptions.current().enableShortCircuiting(false).build())
            .build();
    CelAbstractSyntaxTree ast = cel.compile(expression).getAst();

    boolean result = (boolean) cel.createProgram(ast).trace(listener);
    boolean shortCircuitedResult =
        (boolean)
            celWithShortCircuit
                .createProgram(celWithShortCircuit.compile(expression).getAst())
                .eval();

    assertThat(result).isEqualTo(shortCircuitedResult);
    assertThat(branchResults.build()).containsExactly(first, second, third).inOrder();
  }

  @Test
  @TestParameters("{source: 'false || false || x'}")
  @TestParameters("{source: 'false || x || false'}")
  @TestParameters("{source: 'x || false || false'}")
  public void trace_shortCircuitingDisabledWithUnknownsOredToFalse_returnsUnknown(String source)
      throws Exception {
    ImmutableList.Builder<Object> branchResults = ImmutableList.builder();
    CelEvaluationListener listener =
        (expr, res) -> {
          if (expr.constantOrDefault().getKind().equals(CelConstant.Kind.BOOLEAN_VALUE)
              || expr.identOrDefault().name().equals("x")) {
            branchResults.add(res);
          }
        };
    Cel cel =
        CelFactory.standardCelBuilder()
            .addVar("x", SimpleType.BOOL)
            .setOptions(CelOptions.current().enableShortCircuiting(false).build())
            .build();
    CelAbstractSyntaxTree ast = cel.compile(source).getAst();

    Object unknownResult = cel.createProgram(ast).trace(listener);

    assertThat(InterpreterUtil.isUnknown(unknownResult)).isTrue();
    assertThat(branchResults.build()).containsExactly(false, false, unknownResult);
  }

  @Test
  @TestParameters("{source: 'true || true || x'}")
  @TestParameters("{source: 'true || x || true'}")
  @TestParameters("{source: 'x || true || true'}")
  public void trace_shortCircuitingDisabledWithUnknownOredToTrue_returnsTrue(String source)
      throws Exception {
    ImmutableList.Builder<Object> branchResults = ImmutableList.builder();
    CelEvaluationListener listener =
        (expr, res) -> {
          if (expr.constantOrDefault().getKind().equals(CelConstant.Kind.BOOLEAN_VALUE)
              || expr.identOrDefault().name().equals("x")) {
            if (InterpreterUtil.isUnknown(res)) {
              branchResults.add("x"); // Swap unknown result with a sentinel value for testing
            } else {
              branchResults.add(res);
            }
          }
        };
    Cel cel =
        CelFactory.standardCelBuilder()
            .addVar("x", SimpleType.BOOL)
            .setOptions(CelOptions.current().enableShortCircuiting(false).build())
            .build();
    CelAbstractSyntaxTree ast = cel.compile(source).getAst();

    boolean result = (boolean) cel.createProgram(ast).trace(listener);

    assertThat(result).isTrue();
    assertThat(branchResults.build()).containsExactly(true, true, "x");
  }

  @Test
  public void trace_shortCircuitingDisabled_ternaryAllBranchesVisited() throws Exception {
    ImmutableList.Builder<Boolean> branchResults = ImmutableList.builder();
    CelEvaluationListener listener =
        (expr, res) -> {
          if (expr.constantOrDefault().getKind().equals(CelConstant.Kind.BOOLEAN_VALUE)) {
            branchResults.add((Boolean) res);
          }
        };
    Cel cel =
        CelFactory.standardCelBuilder()
            .setOptions(CelOptions.current().enableShortCircuiting(false).build())
            .build();
    CelAbstractSyntaxTree ast = cel.compile("true ? false : true").getAst();

    boolean result = (boolean) cel.createProgram(ast).trace(listener);

    assertThat(result).isFalse();
    assertThat(branchResults.build()).containsExactly(true, false, true);
  }

  @Test
  @TestParameters("{source: 'false ? true : x'}")
  @TestParameters("{source: 'true ? x : false'}")
  @TestParameters("{source: 'x ? true : false'}")
  public void trace_shortCircuitingDisabled_ternaryWithUnknowns(String source) throws Exception {
    ImmutableList.Builder<Object> branchResults = ImmutableList.builder();
    CelEvaluationListener listener =
        (expr, res) -> {
          if (expr.constantOrDefault().getKind().equals(CelConstant.Kind.BOOLEAN_VALUE)
              || expr.identOrDefault().name().equals("x")) {
            branchResults.add(res);
          }
        };
    Cel cel =
        CelFactory.standardCelBuilder()
            .addVar("x", SimpleType.BOOL)
            .setOptions(CelOptions.current().enableShortCircuiting(false).build())
            .build();
    CelAbstractSyntaxTree ast = cel.compile(source).getAst();

    Object unknownResult = cel.createProgram(ast).trace(listener);

    assertThat(InterpreterUtil.isUnknown(unknownResult)).isTrue();
    assertThat(branchResults.build()).containsExactly(false, unknownResult, true);
  }

  @Test
  @TestParameters(
      "{expression: 'false ? (1 / 0) > 2 : false', firstVisited: false, secondVisited: false}")
  @TestParameters(
      "{expression: 'false ? (1 / 0) > 2 : true', firstVisited: false, secondVisited: true}")
  @TestParameters(
      "{expression: 'true ? false : (1 / 0) > 2', firstVisited: true, secondVisited: false}")
  @TestParameters(
      "{expression: 'true ? true : (1 / 0) > 2', firstVisited: true, secondVisited: true}")
  public void trace_shortCircuitingDisabled_ternaryWithError(
      String expression, boolean firstVisited, boolean secondVisited) throws Exception {
    ImmutableList.Builder<Object> branchResults = ImmutableList.builder();
    CelEvaluationListener listener =
        (expr, res) -> {
          if (expr.constantOrDefault().getKind().equals(CelConstant.Kind.BOOLEAN_VALUE)) {
            branchResults.add(res);
          }
        };
    Cel celWithShortCircuit =
        CelFactory.standardCelBuilder()
            .setOptions(CelOptions.current().enableShortCircuiting(true).build())
            .build();
    Cel cel =
        CelFactory.standardCelBuilder()
            .setOptions(CelOptions.current().enableShortCircuiting(false).build())
            .build();
    CelAbstractSyntaxTree ast = cel.compile(expression).getAst();

    boolean result = (boolean) cel.createProgram(ast).trace(listener);
    boolean shortCircuitedResult =
        (boolean)
            celWithShortCircuit
                .createProgram(celWithShortCircuit.compile(expression).getAst())
                .eval();

    assertThat(result).isEqualTo(shortCircuitedResult);
    assertThat(branchResults.build()).containsExactly(firstVisited, secondVisited).inOrder();
  }

  @Test
  public void standardEnvironmentDisabledForRuntime_throws() throws Exception {
    CelCompiler celCompiler =
        CelCompilerFactory.standardCelCompilerBuilder().setStandardEnvironmentEnabled(true).build();
    CelRuntime celRuntime =
        CelRuntimeFactory.standardCelRuntimeBuilder().setStandardEnvironmentEnabled(false).build();
    CelAbstractSyntaxTree ast = celCompiler.compile("size('hello')").getAst();

    CelEvaluationException e =
        assertThrows(CelEvaluationException.class, () -> celRuntime.createProgram(ast).eval());
    assertThat(e)
        .hasMessageThat()
        .contains("Unknown overload id 'size_string' for function 'size'");
  }
}
