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

import com.google.protobuf.BoolValue;
import com.google.protobuf.BytesValue;
import com.google.protobuf.DoubleValue;
import com.google.protobuf.FloatValue;
import com.google.protobuf.Int32Value;
import com.google.protobuf.Int64Value;
import com.google.protobuf.StringValue;
import com.google.protobuf.UInt32Value;
import com.google.protobuf.UInt64Value;
import dev.cel.common.values.ProtoMessageLiteValueProvider;
import dev.cel.expr.CheckedExpr;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.Resources;
import com.google.common.primitives.UnsignedLong;
import com.google.protobuf.ByteString;
import com.google.protobuf.ExtensionRegistryLite;
import com.google.testing.junit.testparameterinjector.TestParameter;
import com.google.testing.junit.testparameterinjector.TestParameterInjector;
import dev.cel.common.CelAbstractSyntaxTree;
import dev.cel.common.CelOptions;
import dev.cel.common.CelProtoAbstractSyntaxTree;
import dev.cel.common.CelSource;
import dev.cel.common.ast.CelConstant;
import dev.cel.common.ast.CelExpr;
import dev.cel.common.types.SimpleType;
import dev.cel.expr.conformance.proto3.TestAllTypes;
import dev.cel.expr.conformance.proto3.TestAllTypesCelLiteDescriptor;
import dev.cel.runtime.CelLiteRuntime.Program;
import java.net.URL;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(TestParameterInjector.class)
public class CelLiteRuntimeTest {

  @Test
  public void runtimeConstruction() {
    CelLiteRuntimeBuilder builder = CelLiteRuntimeFactory.newLiteRuntimeBuilder();

    CelLiteRuntime runtime = builder.build();

    assertThat(runtime).isNotNull();
  }

  @Test
  public void programConstruction() throws Exception {
    CelLiteRuntime runtime = CelLiteRuntimeFactory.newLiteRuntimeBuilder().build();
    CelAbstractSyntaxTree ast =
        CelAbstractSyntaxTree.newCheckedAst(
            CelExpr.ofConstant(1L, CelConstant.ofValue("hello")),
            CelSource.newBuilder("hello").build(),
            ImmutableMap.of(),
            ImmutableMap.of(1L, SimpleType.STRING));

    Program program = runtime.createProgram(ast);

    assertThat(program).isNotNull();
  }

  @Test
  public void toRuntimeBuilder_isNewInstance() {
    CelLiteRuntimeBuilder runtimeBuilder = CelLiteRuntimeFactory.newLiteRuntimeBuilder();
    CelLiteRuntime runtime = runtimeBuilder.build();

    CelLiteRuntimeBuilder newRuntimeBuilder = runtime.toRuntimeBuilder();

    assertThat(newRuntimeBuilder).isNotEqualTo(runtimeBuilder);
  }

  @Test
  public void toRuntimeBuilder_propertiesCopied() {
    CelOptions celOptions = CelOptions.current().enableCelValue(true).build();
    CelStandardFunctions celStandardFunctions = CelStandardFunctions.newBuilder().build();
    CelLiteRuntimeBuilder runtimeBuilder =
        CelLiteRuntimeFactory.newLiteRuntimeBuilder()
            .setOptions(celOptions)
            .setStandardFunctions(celStandardFunctions)
            .addFunctionBindings(
                CelFunctionBinding.from("string_isEmpty", String.class, String::isEmpty));
    CelLiteRuntime runtime = runtimeBuilder.build();

    LiteRuntimeImpl.Builder newRuntimeBuilder =
        (LiteRuntimeImpl.Builder) runtime.toRuntimeBuilder();

    assertThat(newRuntimeBuilder.celOptions).isEqualTo(celOptions);
    assertThat(newRuntimeBuilder.celStandardFunctions).isEqualTo(celStandardFunctions);
    assertThat(newRuntimeBuilder.customFunctionBindings).hasSize(1);
    assertThat(newRuntimeBuilder.customFunctionBindings).containsKey("string_isEmpty");
  }

  @Test
  public void setCelOptions_unallowedOptionsSet_throws(@TestParameter CelOptionsTestCase testCase) {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            CelLiteRuntimeFactory.newLiteRuntimeBuilder().setOptions(testCase.celOptions).build());
  }

  @Test
  public void standardEnvironment_disabledByDefault() throws Exception {
    CelLiteRuntime runtime = CelLiteRuntimeFactory.newLiteRuntimeBuilder().build();
    // Expr: 1 + 2
    CelAbstractSyntaxTree ast = readCheckedExpr("compiled_one_plus_two");

    CelEvaluationException e =
        assertThrows(CelEvaluationException.class, () -> runtime.createProgram(ast).eval());
    assertThat(e)
        .hasMessageThat()
        .contains(
            "evaluation error at <input>:2: No matching overload for function '_+_'. Overload"
                + " candidates: add_int64");
  }

  @Test
  public void eval_add() throws Exception {
    CelLiteRuntime runtime =
        CelLiteRuntimeFactory.newLiteRuntimeBuilder()
            .setStandardFunctions(CelStandardFunctions.newBuilder().build())
            .build();
    // Expr: 1 + 2
    CelAbstractSyntaxTree ast = readCheckedExpr("compiled_one_plus_two");

    assertThat(runtime.createProgram(ast).eval()).isEqualTo(3L);
  }

  @Test
  public void eval_stringLiteral() throws Exception {
    CelLiteRuntime runtime = CelLiteRuntimeFactory.newLiteRuntimeBuilder().build();
    // Expr: 'hello world'
    CelAbstractSyntaxTree ast = readCheckedExpr("compiled_hello_world");
    Program program = runtime.createProgram(ast);

    String result = (String) program.eval();

    assertThat(result).isEqualTo("hello world");
  }

  @Test
  @SuppressWarnings("unchecked")
  public void eval_listLiteral() throws Exception {
    CelLiteRuntime runtime = CelLiteRuntimeFactory.newLiteRuntimeBuilder().build();
    // Expr: ['a', 1, 2u, 3.5]
    CelAbstractSyntaxTree ast = readCheckedExpr("compiled_list_literal");
    Program program = runtime.createProgram(ast);

    List<Object> result = (List<Object>) program.eval();

    assertThat(result).containsExactly("a", 1L, UnsignedLong.valueOf(2L), 3.5d).inOrder();
  }

  @Test
  public void eval_comprehensionExists() throws Exception {
    CelLiteRuntime runtime =
        CelLiteRuntimeFactory.newLiteRuntimeBuilder()
            .setStandardFunctions(CelStandardFunctions.newBuilder().build())
            .build();
    // Expr: [1,2,3].exists(x, x == 3)
    CelAbstractSyntaxTree ast = readCheckedExpr("compiled_comprehension_exists");
    Program program = runtime.createProgram(ast);

    boolean result = (boolean) program.eval();

    assertThat(result).isTrue();
  }

  @Test
  public void eval_primitiveVariables() throws Exception {
    CelLiteRuntime runtime =
        CelLiteRuntimeFactory.newLiteRuntimeBuilder()
            .setStandardFunctions(CelStandardFunctions.newBuilder().build())
            .build();
    // Expr: bool_var && bytes_var == b'abc' && double_var == 1.0 && int_var == 42 && uint_var ==
    //       42u && str_var == 'foo'
    CelAbstractSyntaxTree ast = readCheckedExpr("compiled_primitive_variables");
    Program program = runtime.createProgram(ast);

    boolean result =
        (boolean)
            program.eval(
                ImmutableMap.of(
                    "bool_var",
                    true,
                    "bytes_var",
                    ByteString.copyFromUtf8("abc"),
                    "double_var",
                    1.0,
                    "int_var",
                    42L,
                    "uint_var",
                    UnsignedLong.valueOf(42L),
                    "str_var",
                    "foo"));

    assertThat(result).isTrue();
  }

  @Test
  @SuppressWarnings("rawtypes")
  public void eval_customFunctions() throws Exception {
    CelLiteRuntime runtime =
        CelLiteRuntimeFactory.newLiteRuntimeBuilder()
            .addFunctionBindings(
                CelFunctionBinding.from("string_isEmpty", String.class, String::isEmpty),
                CelFunctionBinding.from("list_isEmpty", List.class, List::isEmpty))
            .setStandardFunctions(CelStandardFunctions.newBuilder().build())
            .build();
    // Expr: ''.isEmpty() && [].isEmpty()
    CelAbstractSyntaxTree ast = readCheckedExpr("compiled_custom_functions");
    Program program = runtime.createProgram(ast);

    boolean result = (boolean) program.eval();

    assertThat(result).isTrue();
  }

  @Test
  public void eval_proto3Message_unknowns() throws Exception {
    CelLiteRuntime runtime =
        CelLiteRuntimeFactory.newLiteRuntimeBuilder()
            .setStandardFunctions(CelStandardFunctions.newBuilder().build())
            .setValueProvider(ProtoMessageLiteValueProvider.newInstance(
                TestAllTypesCelLiteDescriptor.getDescriptor()))
            .build();
    CelAbstractSyntaxTree ast = readCheckedExpr("compiled_proto3_select_primitives");
    Program program = runtime.createProgram(ast);

    CelUnknownSet result = (CelUnknownSet) program.eval();

    assertThat(result.unknownExprIds()).hasSize(15);
  }

  @Test
  public void eval_proto3Message_primitiveWithDefaults() throws Exception {
    CelLiteRuntime runtime =
        CelLiteRuntimeFactory.newLiteRuntimeBuilder()
            .setStandardFunctions(CelStandardFunctions.newBuilder().build())
            .setValueProvider(ProtoMessageLiteValueProvider.newInstance(
                TestAllTypesCelLiteDescriptor.getDescriptor()))
            .build();
    // Ensures that all branches of the OR conditions are evaluated, and that appropriate defaults are
    // returned for primitives.
    CelAbstractSyntaxTree ast = readCheckedExpr("compiled_proto3_select_primitives_all_ored");
    Program program = runtime.createProgram(ast);

    boolean result = (boolean) program.eval(ImmutableMap.of("proto3", TestAllTypes.newBuilder().build()));

    assertThat(result).isFalse();
  }

  @Test
  public void eval_protoMessage_primitives() throws Exception {
    CelLiteRuntime runtime =
        CelLiteRuntimeFactory.newLiteRuntimeBuilder()
            .setStandardFunctions(CelStandardFunctions.newBuilder().build())
            .setValueProvider(ProtoMessageLiteValueProvider.newInstance(
                TestAllTypesCelLiteDescriptor.getDescriptor()))
            .build();
    CelAbstractSyntaxTree ast = readCheckedExpr("compiled_proto3_select_primitives");
    Program program = runtime.createProgram(ast);

    boolean result = (boolean) program.eval(
        ImmutableMap.of("proto3",
            TestAllTypes.newBuilder()
                .setSingleInt32(1)
                .setSingleInt64(2L)
                .setSingleUint32(3)
                .setSingleUint64(4L)
                .setSingleSint32(5)
                .setSingleSint64(6L)
                .setSingleFixed32(7)
                .setSingleFixed64(8L)
                .setSingleSfixed32(9)
                .setSingleSfixed64(10L)
                .setSingleFloat(1.5f)
                .setSingleDouble(2.5d)
                .setSingleBool(true)
                .setSingleString("hello world")
                .setSingleBytes(ByteString.copyFromUtf8("abc"))
            .build()));

    assertThat(result).isTrue();
  }

  @Test
  public void eval_protoMessage_wrappers() throws Exception {
    CelLiteRuntime runtime =
        CelLiteRuntimeFactory.newLiteRuntimeBuilder()
            .setStandardFunctions(CelStandardFunctions.newBuilder().build())
            .setValueProvider(ProtoMessageLiteValueProvider.newInstance(
                TestAllTypesCelLiteDescriptor.getDescriptor()))
            .build();
    CelAbstractSyntaxTree ast = readCheckedExpr("compiled_proto3_select_wrappers");
    Program program = runtime.createProgram(ast);

    boolean result = (boolean) program.eval(
        ImmutableMap.of("proto3", TestAllTypes.newBuilder()
            .setSingleInt32Wrapper(Int32Value.of(1))
            .setSingleInt64Wrapper(Int64Value.of(2L))
            .setSingleUint32Wrapper(UInt32Value.of(3))
            .setSingleUint64Wrapper(UInt64Value.of(4L))
            .setSingleFloatWrapper(FloatValue.of(1.5f))
            .setSingleDoubleWrapper(DoubleValue.of(2.5d))
            .setSingleBoolWrapper(BoolValue.of(true))
            .setSingleStringWrapper(StringValue.of("hello world"))
            .setSingleBytesWrapper(BytesValue.of(ByteString.copyFromUtf8("abc")))
            .build()));

    assertThat(result).isTrue();
  }

  private static CelAbstractSyntaxTree readCheckedExpr(String compiledCelTarget) throws Exception {
    URL url = Resources.getResource(CelLiteRuntimeTest.class, compiledCelTarget + ".binarypb");
    byte[] checkedExprBytes = Resources.toByteArray(url);
    CheckedExpr checkedExpr =
        CheckedExpr.parseFrom(checkedExprBytes, ExtensionRegistryLite.getEmptyRegistry());
    return CelProtoAbstractSyntaxTree.fromCheckedExpr(checkedExpr).getAst();
  }

  private enum CelOptionsTestCase {
    CEL_VALUE_DISABLED(newBaseTestOptions().enableCelValue(false).build()),
    UNSIGNED_LONG_DISABLED(newBaseTestOptions().enableUnsignedLongs(false).build()),
    UNWRAP_WKT_DISABLED(newBaseTestOptions().unwrapWellKnownTypesOnFunctionDispatch(false).build()),
    STRING_CONCAT_DISABLED(newBaseTestOptions().enableStringConcatenation(false).build()),
    STRING_CONVERSION_DISABLED(newBaseTestOptions().enableStringConversion(false).build()),
    LIST_CONCATENATION_DISABLED(newBaseTestOptions().enableListConcatenation(false).build()),
    ;

    private final CelOptions celOptions;

    private static CelOptions.Builder newBaseTestOptions() {
      return CelOptions.current().enableCelValue(true);
    }

    CelOptionsTestCase(CelOptions celOptions) {
      this.celOptions = celOptions;
    }
  }
}
