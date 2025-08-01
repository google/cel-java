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

package dev.cel.extensions;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.primitives.UnsignedLong;
import com.google.protobuf.ByteString;
import com.google.protobuf.DoubleValue;
import com.google.protobuf.NullValue;
import com.google.testing.junit.testparameterinjector.TestParameter;
import com.google.testing.junit.testparameterinjector.TestParameterInjector;
import com.google.testing.junit.testparameterinjector.TestParameters;
import dev.cel.bundle.Cel;
import dev.cel.bundle.CelBuilder;
import dev.cel.bundle.CelFactory;
import dev.cel.common.CelAbstractSyntaxTree;
import dev.cel.common.CelContainer;
import dev.cel.common.CelFunctionDecl;
import dev.cel.common.CelOptions;
import dev.cel.common.CelOverloadDecl;
import dev.cel.common.CelValidationException;
import dev.cel.common.CelVarDecl;
import dev.cel.common.internal.ProtoTimeUtils;
import dev.cel.common.types.CelType;
import dev.cel.common.types.ListType;
import dev.cel.common.types.MapType;
import dev.cel.common.types.OptionalType;
import dev.cel.common.types.SimpleType;
import dev.cel.common.types.StructTypeReference;
import dev.cel.common.types.TypeType;
import dev.cel.expr.conformance.proto3.TestAllTypes;
import dev.cel.expr.conformance.proto3.TestAllTypes.NestedMessage;
import dev.cel.parser.CelMacro;
import dev.cel.parser.CelStandardMacro;
import dev.cel.runtime.CelEvaluationException;
import dev.cel.runtime.CelFunctionBinding;
import dev.cel.runtime.CelRuntime;
import dev.cel.runtime.InterpreterUtil;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(TestParameterInjector.class)
@SuppressWarnings({"unchecked", "SingleTestParameter"})
public class CelOptionalLibraryTest {

  @SuppressWarnings("ImmutableEnumChecker") // Test only
  private enum ConstantTestCases {
    INT("5", "0", SimpleType.INT, 5L),
    STRING("'hello'", "''", SimpleType.STRING, "hello"),
    DOUBLE("5.2", "0.0", SimpleType.DOUBLE, 5.2d),
    UINT("5u", "0u", SimpleType.UINT, UnsignedLong.valueOf(5)),
    BOOL("true", "false", SimpleType.BOOL, true),
    BYTES("b'abc'", "b''", SimpleType.BYTES, ByteString.copyFromUtf8("abc")),
    DURATION(
        "duration('180s')",
        "duration('0s')",
        SimpleType.DURATION,
        ProtoTimeUtils.fromSecondsToDuration(180)),
    TIMESTAMP(
        "timestamp(1685552643)",
        "timestamp(0)",
        SimpleType.TIMESTAMP,
        ProtoTimeUtils.fromSecondsToTimestamp(1685552643));

    private final String sourceWithNonZeroValue;
    private final String sourceWithZeroValue;
    private final CelType type;
    private final Object value;

    ConstantTestCases(
        String sourceWithNonZeroValue, String sourceWithZeroValue, CelType type, Object value) {
      this.sourceWithNonZeroValue = sourceWithNonZeroValue;
      this.sourceWithZeroValue = sourceWithZeroValue;
      this.type = type;
      this.value = value;
    }
  }

  private static CelBuilder newCelBuilder() {
    return newCelBuilder(Integer.MAX_VALUE);
  }

  private static CelBuilder newCelBuilder(int version) {
    return CelFactory.standardCelBuilder()
        .setOptions(CelOptions.current().enableTimestampEpoch(true).build())
        .setStandardMacros(CelStandardMacro.STANDARD_MACROS)
        .setContainer(CelContainer.ofName("cel.expr.conformance.proto3"))
        .addMessageTypes(TestAllTypes.getDescriptor())
        .addRuntimeLibraries(CelExtensions.optional(version))
        .addCompilerLibraries(CelExtensions.optional(version));
  }

  @Test
  public void library() {
    CelExtensionLibrary<?> library =
        CelExtensions.getExtensionLibrary("optional", CelOptions.DEFAULT);
    assertThat(library.name()).isEqualTo("optional");
    assertThat(library.latest().version()).isEqualTo(2);

    // Version 0
    assertThat(library.version(0).functions().stream().map(CelFunctionDecl::name))
        .containsExactly(
            "optional.of",
            "optional.ofNonZeroValue",
            "optional.none",
            "value",
            "hasValue",
            "optional.unwrap",
            "or",
            "orValue",
            "_?._",
            "_[?_]",
            "_[_]");
    assertThat(library.version(0).macros().stream().map(CelMacro::getFunction))
        .containsExactly("optMap");
    assertThat(library.version(0).variables().stream().map(CelVarDecl::name))
        .containsExactly("optional_type");

    // Version 1
    assertThat(library.version(1).functions().stream().map(CelFunctionDecl::name))
        .containsExactly(
            "optional.of",
            "optional.ofNonZeroValue",
            "optional.none",
            "value",
            "hasValue",
            "optional.unwrap",
            "or",
            "orValue",
            "_?._",
            "_[?_]",
            "_[_]");
    assertThat(library.version(1).macros().stream().map(CelMacro::getFunction))
        .containsExactly("optMap", "optFlatMap");
    assertThat(library.version(1).variables().stream().map(CelVarDecl::name))
        .containsExactly("optional_type");

    // Version 2
    assertThat(library.version(2).functions().stream().map(CelFunctionDecl::name))
        .containsExactly(
            "optional.of",
            "optional.ofNonZeroValue",
            "optional.none",
            "value",
            "hasValue",
            "optional.unwrap",
            "or",
            "orValue",
            "_?._",
            "_[?_]",
            "_[_]",
            "first",
            "last");
    assertThat(library.version(2).macros().stream().map(CelMacro::getFunction))
        .containsExactly("optMap", "optFlatMap");
    assertThat(library.version(2).variables().stream().map(CelVarDecl::name))
        .containsExactly("optional_type");
  }

  @Test
  public void optionalOf_constant_success(@TestParameter ConstantTestCases testCase)
      throws Exception {
    Cel cel = newCelBuilder().setResultType(OptionalType.create(testCase.type)).build();
    String expression = String.format("optional.of(%s)", testCase.sourceWithNonZeroValue);
    CelAbstractSyntaxTree ast = cel.compile(expression).getAst();

    Object result = cel.createProgram(ast).eval();

    assertThat(result).isInstanceOf(Optional.class);
    assertThat(result).isEqualTo(Optional.of(testCase.value));
  }

  @Test
  public void optionalType_runtimeEquality(@TestParameter ConstantTestCases testCase)
      throws Exception {
    Cel cel =
        newCelBuilder()
            .addVar("a", OptionalType.create(testCase.type))
            .addVar("b", OptionalType.create(testCase.type))
            .setResultType(SimpleType.BOOL)
            .build();
    CelAbstractSyntaxTree ast = cel.compile("a == b").getAst();

    boolean result =
        (boolean)
            cel.createProgram(ast)
                .eval(
                    ImmutableMap.of(
                        "a",
                        testCase.sourceWithNonZeroValue,
                        "b",
                        testCase.sourceWithNonZeroValue));

    assertThat(result).isTrue();
  }

  @Test
  public void optionalType_adaptsIntegerToLong_success() throws Exception {
    Cel cel = newCelBuilder().addVar("a", OptionalType.create(SimpleType.INT)).build();
    CelAbstractSyntaxTree ast = cel.compile("a").getAst();

    Optional<Long> result =
        (Optional<Long>) cel.createProgram(ast).eval(ImmutableMap.of("a", Optional.of(5)));

    assertThat(result).hasValue(5L);
  }

  @Test
  public void optionalType_adaptsFloatToLong_success() throws Exception {
    Cel cel = newCelBuilder().addVar("a", OptionalType.create(SimpleType.DOUBLE)).build();
    CelAbstractSyntaxTree ast = cel.compile("a").getAst();

    Optional<Long> result =
        (Optional<Long>) cel.createProgram(ast).eval(ImmutableMap.of("a", Optional.of(5.5f)));

    assertThat(result).hasValue(5.5d);
  }

  @Test
  public void optionalOf_nullValue_success() throws Exception {
    Cel cel = newCelBuilder().setResultType(SimpleType.DYN).build();
    String expression = "optional.of(TestAllTypes{}.single_value)";
    CelAbstractSyntaxTree ast = cel.compile(expression).getAst();

    Object result = cel.createProgram(ast).eval();

    assertThat(result).isInstanceOf(Optional.class);
    assertThat(result).isEqualTo(Optional.of(NullValue.NULL_VALUE));
  }

  @Test
  public void optionalOfNonZeroValue_withZeroValue_returnsEmptyOptionalValue(
      @TestParameter ConstantTestCases testCase) throws Exception {
    Cel cel = newCelBuilder().setResultType(OptionalType.create(testCase.type)).build();
    String expression = String.format("optional.ofNonZeroValue(%s)", testCase.sourceWithZeroValue);
    CelAbstractSyntaxTree ast = cel.compile(expression).getAst();

    Object result = cel.createProgram(ast).eval();

    assertThat(result).isInstanceOf(Optional.class);
    assertThat(result).isEqualTo(Optional.empty());
  }

  @Test
  public void optionalOfNonZeroValue_withNonZeroValue_returnsOptionalValue(
      @TestParameter ConstantTestCases testCase) throws Exception {
    Cel cel = newCelBuilder().setResultType(OptionalType.create(testCase.type)).build();
    String expression =
        String.format("optional.ofNonZeroValue(%s)", testCase.sourceWithNonZeroValue);
    CelAbstractSyntaxTree ast = cel.compile(expression).getAst();

    Object result = cel.createProgram(ast).eval();

    assertThat(result).isInstanceOf(Optional.class);
    assertThat(result).isEqualTo(Optional.of(testCase.value));
  }

  @Test
  public void optionalOfNonZeroValue_withNullValue_returnsEmptyOptionalValue() throws Exception {
    Cel cel = newCelBuilder().setResultType(SimpleType.DYN).build();
    CelAbstractSyntaxTree ast =
        cel.compile("optional.ofNonZeroValue(TestAllTypes{}.single_value)").getAst();

    Object result = cel.createProgram(ast).eval();

    assertThat(result).isInstanceOf(Optional.class);
    assertThat(result).isEqualTo(Optional.empty());
  }

  @Test
  public void optionalOfNonZeroValue_withEmptyMessage_returnsEmptyOptionalValue() throws Exception {
    Cel cel = newCelBuilder().setResultType(SimpleType.DYN).build();
    CelAbstractSyntaxTree ast = cel.compile("optional.ofNonZeroValue(TestAllTypes{})").getAst();

    Object result = cel.createProgram(ast).eval();

    assertThat(result).isInstanceOf(Optional.class);
    assertThat(result).isEqualTo(Optional.empty());
  }

  @Test
  public void optionalNone_success() throws Exception {
    Cel cel = newCelBuilder().setResultType(SimpleType.DYN).build();
    CelAbstractSyntaxTree ast = cel.compile("optional.none()").getAst();

    Object result = cel.createProgram(ast).eval();

    assertThat(result).isInstanceOf(Optional.class);
    assertThat(result).isEqualTo(Optional.empty());
  }

  @Test
  public void optionalValue_success(@TestParameter ConstantTestCases testCase) throws Exception {
    Cel cel = newCelBuilder().setResultType(testCase.type).build();
    String expression = String.format("optional.of(%s).value()", testCase.sourceWithNonZeroValue);
    CelAbstractSyntaxTree ast = cel.compile(expression).getAst();

    Object result = cel.createProgram(ast).eval();

    assertThat(result).isEqualTo(testCase.value);
  }

  @Test
  public void optionalValue_whenOptionalValueEmpty_throws() throws Exception {
    Cel cel = newCelBuilder().build();
    CelAbstractSyntaxTree ast = cel.compile("optional.none().value()").getAst();

    assertThrows(CelEvaluationException.class, () -> cel.createProgram(ast).eval());
  }

  @Test
  public void optionalHasValue_whenOptionalValuePresent_returnsTrue(
      @TestParameter ConstantTestCases testCase) throws Exception {
    Cel cel = newCelBuilder().setResultType(SimpleType.BOOL).build();
    String expression =
        String.format("optional.of(%s).hasValue()", testCase.sourceWithNonZeroValue);
    CelAbstractSyntaxTree ast = cel.compile(expression).getAst();

    Object result = cel.createProgram(ast).eval();

    assertThat(result).isEqualTo(true);
  }

  @Test
  public void optionalHasValue_whenOptionalValueEmpty_returnsFalse(
      @TestParameter ConstantTestCases testCase) throws Exception {
    Cel cel = newCelBuilder().setResultType(SimpleType.BOOL).build();
    String expression =
        String.format("optional.ofNonZeroValue(%s).hasValue()", testCase.sourceWithZeroValue);
    CelAbstractSyntaxTree ast = cel.compile(expression).getAst();

    Object result = cel.createProgram(ast).eval();

    assertThat(result).isEqualTo(false);
  }

  @Test
  public void optionalOr_success() throws Exception {
    Cel cel =
        newCelBuilder()
            .addVar("x", OptionalType.create(SimpleType.INT))
            .addVar("y", OptionalType.create(SimpleType.INT))
            .setResultType(OptionalType.create(SimpleType.INT))
            .build();
    CelRuntime.Program program = cel.createProgram(cel.compile("x.or(y)").getAst());

    Object resultLhs = program.eval(ImmutableMap.of("x", Optional.of(5), "y", Optional.empty()));
    Object resultRhs = program.eval(ImmutableMap.of("x", Optional.empty(), "y", Optional.of(10)));

    assertThat(resultLhs).isEqualTo(Optional.of(5L));
    assertThat(resultRhs).isEqualTo(Optional.of(10L));
  }

  @Test
  public void optionalOr_shortCircuits() throws Exception {
    Cel cel =
        newCelBuilder()
            .addVar("x", OptionalType.create(SimpleType.INT))
            .addFunctionDeclarations(
                CelFunctionDecl.newFunctionDeclaration(
                    "errorFunc",
                    CelOverloadDecl.newGlobalOverload(
                        "error_overload", OptionalType.create(SimpleType.INT))))
            .addFunctionBindings(
                CelFunctionBinding.from(
                    "error_overload",
                    ImmutableList.of(),
                    val -> {
                      throw new IllegalStateException("This function should not have been called!");
                    }))
            .setResultType(OptionalType.create(SimpleType.INT))
            .build();
    CelRuntime.Program program = cel.createProgram(cel.compile("x.or(errorFunc())").getAst());

    Object resultLhs = program.eval(ImmutableMap.of("x", Optional.of(5)));

    assertThat(resultLhs).isEqualTo(Optional.of(5L));
  }

  @Test
  public void optionalOr_producesNonOptionalValue_throws() throws Exception {
    Cel cel =
        CelFactory.standardCelBuilder()
            .addFunctionDeclarations(
                CelFunctionDecl.newFunctionDeclaration(
                    "or",
                    CelOverloadDecl.newMemberOverload(
                        "optional_or_optional", SimpleType.INT, SimpleType.INT, SimpleType.INT)))
            .addFunctionBindings(
                CelFunctionBinding.from("optional_or_optional", Long.class, Long.class, Long::sum))
            .build();

    CelAbstractSyntaxTree ast = cel.compile("5.or(10)").getAst();

    CelEvaluationException e =
        assertThrows(CelEvaluationException.class, () -> cel.createProgram(ast).eval());
    assertThat(e).hasMessageThat().contains("expected optional value, found: 5");
  }

  @Test
  public void optionalOrValue_lhsHasValue_success(@TestParameter ConstantTestCases testCase)
      throws Exception {
    Cel cel = newCelBuilder().setResultType(testCase.type).build();
    String expression =
        String.format(
            "optional.of(%s).orValue(%s)",
            testCase.sourceWithNonZeroValue, testCase.sourceWithZeroValue);
    CelAbstractSyntaxTree ast = cel.compile(expression).getAst();

    Object result = cel.createProgram(ast).eval();

    assertThat(result).isEqualTo(testCase.value);
  }

  @Test
  public void optionalOrValue_shortCircuits() throws Exception {
    Cel cel =
        newCelBuilder()
            .addVar("x", OptionalType.create(SimpleType.INT))
            .addFunctionDeclarations(
                CelFunctionDecl.newFunctionDeclaration(
                    "errorFunc",
                    CelOverloadDecl.newGlobalOverload("error_overload", SimpleType.INT)))
            .addFunctionBindings(
                CelFunctionBinding.from(
                    "error_overload",
                    ImmutableList.of(),
                    val -> {
                      throw new IllegalStateException("This function should not have been called!");
                    }))
            .setResultType(SimpleType.INT)
            .build();
    CelRuntime.Program program = cel.createProgram(cel.compile("x.orValue(errorFunc())").getAst());

    Object resultLhs = program.eval(ImmutableMap.of("x", Optional.of(5)));

    assertThat(resultLhs).isEqualTo(5);
  }

  @Test
  public void optionalOrValue_rhsHasValue_success(@TestParameter ConstantTestCases testCase)
      throws Exception {
    Cel cel = newCelBuilder().setResultType(testCase.type).build();
    String expression =
        String.format("optional.none().orValue(%s)", testCase.sourceWithNonZeroValue);
    CelAbstractSyntaxTree ast = cel.compile(expression).getAst();

    Object result = cel.createProgram(ast).eval();

    assertThat(result).isEqualTo(testCase.value);
  }

  @Test
  @TestParameters("{source: optional.of(5).orValue(optional.of(10))}")
  @TestParameters("{source: optional.of(5).orValue(optional.none())}")
  @TestParameters("{source: 5.orValue(optional.of(10))}")
  @TestParameters("{source: 5.orValue(optional.none())}")
  public void optionalOrValue_unmatchingTypes_throwsCompilationException(String source) {
    Cel cel = newCelBuilder().build();

    CelValidationException e =
        assertThrows(CelValidationException.class, () -> cel.compile(source).getAst());
    assertThat(e).hasMessageThat().contains("found no matching overload for 'orValue'");
  }

  @Test
  public void optionalOrValue_producesNonOptionalValue_throws() throws Exception {
    Cel cel =
        CelFactory.standardCelBuilder()
            .addFunctionDeclarations(
                CelFunctionDecl.newFunctionDeclaration(
                    "orValue",
                    CelOverloadDecl.newMemberOverload(
                        "optional_orValue_value", SimpleType.INT, SimpleType.INT, SimpleType.INT)))
            .addFunctionBindings(
                CelFunctionBinding.from(
                    "optional_orValue_value", Long.class, Long.class, Long::sum))
            .build();

    CelAbstractSyntaxTree ast = cel.compile("5.orValue(10)").getAst();

    CelEvaluationException e =
        assertThrows(CelEvaluationException.class, () -> cel.createProgram(ast).eval());
    assertThat(e).hasMessageThat().contains("expected optional value, found: 5");
  }

  @Test
  @TestParameters("{source: optional.ofNonZeroValue(42).or(optional.of(10)).value() == 42}")
  @TestParameters("{source: optional.none().or(optional.none()).orValue(42) == 42}")
  public void optionalChainedFunctions_constants_success(String source) throws Exception {
    Cel cel = newCelBuilder().setResultType(SimpleType.BOOL).build();
    CelAbstractSyntaxTree ast = cel.compile(source).getAst();

    boolean result = (boolean) cel.createProgram(ast).eval();

    assertThat(result).isTrue();
  }

  @Test
  public void optionalChainedFunctions_nestedMaps_success() throws Exception {
    Cel cel =
        newCelBuilder()
            .addVar(
                "m",
                MapType.create(
                    SimpleType.STRING, MapType.create(SimpleType.STRING, SimpleType.STRING)))
            .setResultType(SimpleType.STRING)
            .build();
    String expression =
        "optional.ofNonZeroValue('').or(optional.of(m.c['dashed-index'])).orValue('default value')";
    CelAbstractSyntaxTree ast = cel.compile(expression).getAst();

    String result =
        (String)
            cel.createProgram(ast)
                .eval(
                    ImmutableMap.of(
                        "m", ImmutableMap.of("c", ImmutableMap.of("dashed-index", "goodbye"))));

    assertThat(result).isEqualTo("goodbye");
  }

  @Test
  public void optionalChainedFunctions_nestedMapsInvalidAccess_throws() throws Exception {
    Cel cel =
        newCelBuilder()
            .addVar(
                "m",
                MapType.create(
                    SimpleType.STRING, MapType.create(SimpleType.STRING, SimpleType.STRING)))
            .setResultType(SimpleType.STRING)
            .build();
    String expression = "optional.ofNonZeroValue(m.a.z).orValue(m.c['dashed-index'])";
    CelAbstractSyntaxTree ast = cel.compile(expression).getAst();

    CelEvaluationException e =
        assertThrows(
            CelEvaluationException.class,
            () ->
                cel.createProgram(ast)
                    .eval(
                        ImmutableMap.of(
                            "m",
                            ImmutableMap.of("c", ImmutableMap.of("dashed-index", "goodbye")))));
    assertThat(e).hasMessageThat().contains("key 'a' is not present in map.");
  }

  @Test
  public void optionalFieldSelection_onMap_returnsOptionalEmpty() throws Exception {
    Cel cel = newCelBuilder().setResultType(OptionalType.create(SimpleType.INT)).build();
    CelAbstractSyntaxTree ast = cel.compile("{'a': 2}.?x").getAst();

    Object result = cel.createProgram(ast).eval();

    assertThat(result).isEqualTo(Optional.empty());
  }

  @Test
  public void optionalFieldSelection_onMap_returnsOptionalValue() throws Exception {
    Cel cel = newCelBuilder().setResultType(OptionalType.create(SimpleType.INT)).build();
    CelAbstractSyntaxTree ast = cel.compile("{'a': 2}.?a").getAst();

    Optional<Long> result = (Optional<Long>) cel.createProgram(ast).eval();

    assertThat(result).hasValue(2L);
  }

  @Test
  public void optionalFieldSelection_onProtoMessage_returnsOptionalEmpty() throws Exception {
    Cel cel =
        newCelBuilder()
            .setResultType(OptionalType.create(SimpleType.INT))
            .addVar("msg", StructTypeReference.create(TestAllTypes.getDescriptor().getFullName()))
            .build();
    CelAbstractSyntaxTree ast = cel.compile("msg.?single_int32").getAst();

    Optional<Long> result =
        (Optional<Long>)
            cel.createProgram(ast).eval(ImmutableMap.of("msg", TestAllTypes.getDefaultInstance()));

    assertThat(result).isEmpty();
  }

  @Test
  public void optionalFieldSelection_onProtoMessage_returnsOptionalValue() throws Exception {
    Cel cel =
        newCelBuilder()
            .setResultType(OptionalType.create(SimpleType.INT))
            .addVar("msg", StructTypeReference.create(TestAllTypes.getDescriptor().getFullName()))
            .build();
    CelAbstractSyntaxTree ast = cel.compile("msg.?single_int32").getAst();

    Optional<Long> result =
        (Optional<Long>)
            cel.createProgram(ast)
                .eval(ImmutableMap.of("msg", TestAllTypes.newBuilder().setSingleInt32(5).build()));

    assertThat(result).hasValue(5L);
  }

  @Test
  public void optionalFieldSelection_onProtoMessage_listValue() throws Exception {
    Cel cel = newCelBuilder().build();
    CelAbstractSyntaxTree ast =
        cel.compile("optional.of(TestAllTypes{repeated_string: ['foo']}).?repeated_string.value()")
            .getAst();

    List<String> result = (List<String>) cel.createProgram(ast).eval();

    assertThat(result).containsExactly("foo");
  }

  @Test
  public void optionalFieldSelection_onProtoMessage_indexValue() throws Exception {
    Cel cel = newCelBuilder().build();
    CelAbstractSyntaxTree ast =
        cel.compile(
                "optional.of(TestAllTypes{repeated_string: ['foo']}).?repeated_string[0].value()")
            .getAst();

    String result = (String) cel.createProgram(ast).eval();

    assertThat(result).isEqualTo("foo");
  }

  @Test
  public void optionalFieldSelection_onProtoMessage_chainedSuccess() throws Exception {
    Cel cel =
        newCelBuilder()
            .setResultType(OptionalType.create(SimpleType.INT))
            .addVar(
                "m",
                MapType.create(
                    SimpleType.STRING,
                    MapType.create(
                        SimpleType.STRING,
                        StructTypeReference.create(TestAllTypes.getDescriptor().getFullName()))))
            .build();
    CelAbstractSyntaxTree ast =
        cel.compile("m.?c.missing.or(m.?c['dashed-index']).value().?single_int32").getAst();

    Optional<Long> result =
        (Optional<Long>)
            cel.createProgram(ast)
                .eval(
                    ImmutableMap.of(
                        "m",
                        ImmutableMap.of(
                            "c",
                            ImmutableMap.of(
                                "dashed-index",
                                TestAllTypes.newBuilder().setSingleInt32(5).build()))));

    assertThat(result).hasValue(5L);
  }

  @Test
  public void optionalFieldSelection_indexerOnProtoMessage_throwsException() {
    Cel cel =
        newCelBuilder()
            .setResultType(OptionalType.create(SimpleType.INT))
            .addVar("msg", StructTypeReference.create(TestAllTypes.getDescriptor().getFullName()))
            .build();

    CelValidationException e =
        assertThrows(
            CelValidationException.class, () -> cel.compile("msg[?single_int32]").getAst());

    assertThat(e).hasMessageThat().contains("undeclared reference to 'single_int32'");
  }

  @Test
  public void optionalFieldSelection_onProtoMessage_presenceTest() throws Exception {
    Cel cel =
        newCelBuilder()
            .addVar("msg", StructTypeReference.create(TestAllTypes.getDescriptor().getFullName()))
            .setResultType(SimpleType.BOOL)
            .build();
    CelAbstractSyntaxTree ast =
        cel.compile("!has(msg.?single_nested_message.bb) && has(msg.?standalone_message.bb)")
            .getAst();

    boolean result =
        (boolean)
            cel.createProgram(ast)
                .eval(
                    ImmutableMap.of(
                        "msg",
                        TestAllTypes.newBuilder()
                            .setStandaloneMessage(NestedMessage.newBuilder().setBb(5).build())
                            .build()));

    assertThat(result).isTrue();
  }

  @Test
  @TestParameters("{source: \"{'a': 2}.?a.hasValue()\", expectedResult: true}")
  @TestParameters("{source: \"{'a': 2}.?x.hasValue()\", expectedResult: false}")
  public void optionalFieldSelection_onMap_hasValueReturnsBoolean(
      String source, boolean expectedResult) throws Exception {
    Cel cel = newCelBuilder().setResultType(SimpleType.BOOL).build();
    CelAbstractSyntaxTree ast = cel.compile(source).getAst();

    boolean result = (boolean) cel.createProgram(ast).eval();

    assertThat(result).isEqualTo(expectedResult);
  }

  @Test
  public void optionalFieldSelection_onMap_hasMacroReturnsTrue() throws Exception {
    Cel cel =
        newCelBuilder()
            .addVar(
                "m",
                MapType.create(
                    SimpleType.STRING, MapType.create(SimpleType.STRING, SimpleType.STRING)))
            .setResultType(SimpleType.BOOL)
            .build();
    CelAbstractSyntaxTree ast = cel.compile("has(m.?x.y)").getAst();

    boolean result =
        (boolean)
            cel.createProgram(ast)
                .eval(ImmutableMap.of("m", ImmutableMap.of("x", ImmutableMap.of("y", "z"))));

    assertThat(result).isTrue();
  }

  @Test
  public void optionalFieldSelection_onMap_hasMacroReturnsFalse() throws Exception {
    Cel cel =
        newCelBuilder()
            .addVar(
                "m",
                MapType.create(
                    SimpleType.STRING, MapType.create(SimpleType.STRING, SimpleType.STRING)))
            .setResultType(SimpleType.BOOL)
            .build();
    CelAbstractSyntaxTree ast = cel.compile("has(m.?x.y)").getAst();

    boolean result = (boolean) cel.createProgram(ast).eval(ImmutableMap.of("m", ImmutableMap.of()));

    assertThat(result).isFalse();
  }

  @Test
  public void optionalFieldSelection_onOptionalMap_presenceTest() throws Exception {
    Cel cel =
        newCelBuilder()
            .addVar(
                "optm",
                OptionalType.create(
                    MapType.create(
                        SimpleType.STRING, MapType.create(SimpleType.STRING, SimpleType.STRING))))
            .setResultType(SimpleType.BOOL)
            .build();
    CelAbstractSyntaxTree ast = cel.compile("has(optm.c) && !has(optm.c.missing)").getAst();

    boolean result =
        (boolean)
            cel.createProgram(ast)
                .eval(
                    ImmutableMap.of(
                        "optm",
                        Optional.of(
                            ImmutableMap.of("c", ImmutableMap.of("dashed-index", "goodbye")))));

    assertThat(result).isTrue();
  }

  @Test
  public void optionalIndex_onOptionalMap_returnsOptionalValue() throws Exception {
    Cel cel =
        newCelBuilder()
            .addVar(
                "optm",
                OptionalType.create(
                    MapType.create(
                        SimpleType.STRING, MapType.create(SimpleType.STRING, SimpleType.STRING))))
            .setResultType(OptionalType.create(SimpleType.STRING))
            .build();
    CelAbstractSyntaxTree ast = cel.compile("optm.c[?'dashed-index']").getAst();

    Object result =
        cel.createProgram(ast)
            .eval(
                ImmutableMap.of(
                    "optm",
                    Optional.of(ImmutableMap.of("c", ImmutableMap.of("dashed-index", "goodbye")))));

    assertThat(result).isEqualTo(Optional.of("goodbye"));
  }

  @Test
  public void optionalIndex_onOptionalMap_returnsOptionalEmpty() throws Exception {
    Cel cel =
        newCelBuilder()
            .addVar(
                "optm",
                OptionalType.create(
                    MapType.create(
                        SimpleType.STRING, MapType.create(SimpleType.STRING, SimpleType.STRING))))
            .setResultType(OptionalType.create(SimpleType.STRING))
            .build();
    CelAbstractSyntaxTree ast = cel.compile("optm.c[?'dashed-index']").getAst();

    Object result =
        cel.createProgram(ast)
            .eval(ImmutableMap.of("optm", Optional.of(ImmutableMap.of("c", ImmutableMap.of()))));

    assertThat(result).isEqualTo(Optional.empty());
  }

  @Test
  public void optionalIndex_onMap_returnsOptionalEmpty() throws Exception {
    Cel cel =
        newCelBuilder()
            .addVar(
                "m",
                MapType.create(
                    SimpleType.STRING, MapType.create(SimpleType.STRING, SimpleType.STRING)))
            .setResultType(OptionalType.create(SimpleType.STRING))
            .build();
    CelAbstractSyntaxTree ast = cel.compile("m.c[?'dashed-index']").getAst();

    Object result =
        cel.createProgram(ast).eval(ImmutableMap.of("m", ImmutableMap.of("c", ImmutableMap.of())));

    assertThat(result).isEqualTo(Optional.empty());
  }

  @Test
  public void optionalIndex_onMap_returnsOptionalValue() throws Exception {
    Cel cel =
        newCelBuilder()
            .addVar(
                "m",
                MapType.create(
                    SimpleType.STRING, MapType.create(SimpleType.STRING, SimpleType.STRING)))
            .setResultType(OptionalType.create(SimpleType.STRING))
            .build();
    CelAbstractSyntaxTree ast = cel.compile("m.c[?'dashed-index']").getAst();

    Object result =
        cel.createProgram(ast)
            .eval(
                ImmutableMap.of(
                    "m", ImmutableMap.of("c", ImmutableMap.of("dashed-index", "goodbye"))));

    assertThat(result).isEqualTo(Optional.of("goodbye"));
  }

  @Test
  @TestParameters("{source: '{?x: optional.of(1)}'}")
  @TestParameters("{source: '{?1: x}'}")
  @TestParameters("{source: '{?x: x}'}")
  public void optionalIndex_onMapWithUnknownInput_returnsUnknownResult(String source)
      throws Exception {
    Cel cel = newCelBuilder().addVar("x", OptionalType.create(SimpleType.INT)).build();
    CelAbstractSyntaxTree ast = cel.compile(source).getAst();

    Object result = cel.createProgram(ast).eval();

    assertThat(InterpreterUtil.isUnknown(result)).isTrue();
  }

  @Test
  public void optionalIndex_onOptionalMapUsingFieldSelection_returnsOptionalValue()
      throws Exception {
    Cel cel =
        newCelBuilder()
            .addVar(
                "m",
                MapType.create(
                    SimpleType.STRING, MapType.create(SimpleType.STRING, SimpleType.STRING)))
            .setResultType(OptionalType.create(SimpleType.STRING))
            .build();
    CelAbstractSyntaxTree ast = cel.compile("{?'key': optional.of('test')}.?key").getAst();

    Object result = cel.createProgram(ast).eval();

    assertThat(result).isEqualTo(Optional.of("test"));
  }

  @Test
  public void optionalIndex_onList_returnsOptionalEmpty() throws Exception {
    Cel cel =
        newCelBuilder()
            .addVar("l", ListType.create(SimpleType.STRING))
            .setResultType(OptionalType.create(SimpleType.STRING))
            .build();
    CelAbstractSyntaxTree ast = cel.compile("l[?0]").getAst();
    CelRuntime.Program program = cel.createProgram(ast);

    assertThat(program.eval(ImmutableMap.of("l", ImmutableList.of()))).isEqualTo(Optional.empty());
  }

  @Test
  public void optionalIndex_onList_returnsOptionalValue() throws Exception {
    Cel cel =
        newCelBuilder()
            .addVar("l", ListType.create(SimpleType.STRING))
            .setResultType(OptionalType.create(SimpleType.STRING))
            .build();
    CelAbstractSyntaxTree ast = cel.compile("l[?0]").getAst();

    Object result = cel.createProgram(ast).eval(ImmutableMap.of("l", ImmutableList.of("hello")));

    assertThat(result).isEqualTo(Optional.of("hello"));
  }

  @Test
  public void optionalIndex_onOptionalList_returnsOptionalEmpty() throws Exception {
    Cel cel =
        newCelBuilder()
            .addVar("optl", OptionalType.create(ListType.create(SimpleType.STRING)))
            .setResultType(OptionalType.create(SimpleType.STRING))
            .build();
    CelAbstractSyntaxTree ast = cel.compile("optl[?0]").getAst();
    CelRuntime.Program program = cel.createProgram(ast);

    assertThat(program.eval(ImmutableMap.of("optl", Optional.empty()))).isEqualTo(Optional.empty());
    assertThat(program.eval(ImmutableMap.of("optl", Optional.of(ImmutableList.of()))))
        .isEqualTo(Optional.empty());
  }

  @Test
  public void optionalIndex_onOptionalList_returnsOptionalValue() throws Exception {
    Cel cel =
        newCelBuilder()
            .addVar("optl", OptionalType.create(ListType.create(SimpleType.STRING)))
            .setResultType(OptionalType.create(SimpleType.STRING))
            .build();
    CelAbstractSyntaxTree ast = cel.compile("optl[?0]").getAst();

    Object result =
        cel.createProgram(ast)
            .eval(ImmutableMap.of("optl", Optional.of(ImmutableList.of("hello"))));

    assertThat(result).isEqualTo(Optional.of("hello"));
  }

  @Test
  public void optionalIndex_onListWithUnknownInput_returnsUnknownResult() throws Exception {
    Cel cel =
        newCelBuilder()
            .addVar("x", OptionalType.create(SimpleType.INT))
            .setResultType(ListType.create(SimpleType.INT))
            .build();
    CelAbstractSyntaxTree ast = cel.compile("[?x]").getAst();

    Object result = cel.createProgram(ast).eval();

    assertThat(InterpreterUtil.isUnknown(result)).isTrue();
  }

  @Test
  public void traditionalIndex_onOptionalList_returnsOptionalEmpty() throws Exception {
    Cel cel =
        newCelBuilder()
            .addVar("optl", OptionalType.create(ListType.create(SimpleType.STRING)))
            .setResultType(OptionalType.create(SimpleType.STRING))
            .build();
    CelAbstractSyntaxTree ast = cel.compile("optl[0]").getAst();

    Object result = cel.createProgram(ast).eval(ImmutableMap.of("optl", Optional.empty()));

    assertThat(result).isEqualTo(Optional.empty());
  }

  @Test
  // LHS
  @TestParameters("{expression: 'optx.or(optional.of(1))'}")
  @TestParameters("{expression: 'optx.orValue(1)'}")
  // RHS
  @TestParameters("{expression: 'optional.none().or(optx)'}")
  @TestParameters("{expression: 'optional.none().orValue(optx)'}")
  public void optionalChainedFunctions_lhsIsUnknown_returnsUnknown(String expression)
      throws Exception {
    Cel cel =
        newCelBuilder()
            .addVar("optx", OptionalType.create(SimpleType.INT))
            .addVar("x", SimpleType.INT)
            .build();
    CelAbstractSyntaxTree ast = cel.compile(expression).getAst();

    Object result = cel.createProgram(ast).eval();

    assertThat(InterpreterUtil.isUnknown(result)).isTrue();
  }

  @Test
  // LHS
  @TestParameters("{expression: 'optional.of(1/0).or(optional.of(1))'}")
  @TestParameters("{expression: 'optional.of(1/0).orValue(1)'}")
  // RHS
  @TestParameters("{expression: 'optional.none().or(optional.of(1/0))'}")
  @TestParameters("{expression: 'optional.none().orValue(1/0)'}")
  public void optionalChainedFunctions_lhsIsError_returnsError(String expression) throws Exception {
    Cel cel =
        newCelBuilder()
            .addVar("optx", OptionalType.create(SimpleType.INT))
            .addVar("x", SimpleType.INT)
            .build();
    CelAbstractSyntaxTree ast = cel.compile(expression).getAst();

    assertThrows(CelEvaluationException.class, () -> cel.createProgram(ast).eval());
  }

  @Test
  public void traditionalIndex_onOptionalList_returnsOptionalValue() throws Exception {
    Cel cel =
        newCelBuilder()
            .addVar("optl", OptionalType.create(ListType.create(SimpleType.STRING)))
            .setResultType(OptionalType.create(SimpleType.STRING))
            .build();
    CelAbstractSyntaxTree ast = cel.compile("optl[0]").getAst();

    Object result =
        cel.createProgram(ast)
            .eval(ImmutableMap.of("optl", Optional.of(ImmutableList.of("list-value"))));

    assertThat(result).isEqualTo(Optional.of("list-value"));
  }

  @Test
  @TestParameters("{source: \"m.?c.missing.or(m.?c['dashed-index']).orValue('').size() == 7\"}")
  @TestParameters("{source: \"m.c[?'dashed-index'].orValue('default value') == 'goodbye'\"}")
  @TestParameters("{source: \"m.c[?'missing-index'].orValue('default value') == 'default value'\"}")
  public void optionalFieldSelection_onMap_chainedWithSelectorAndIndexer(String source)
      throws Exception {
    Cel cel =
        newCelBuilder()
            .addVar(
                "m",
                MapType.create(
                    SimpleType.STRING, MapType.create(SimpleType.STRING, SimpleType.STRING)))
            .setResultType(SimpleType.BOOL)
            .build();
    CelAbstractSyntaxTree ast = cel.compile(source).getAst();

    boolean result =
        (boolean)
            cel.createProgram(ast)
                .eval(
                    ImmutableMap.of(
                        "m", ImmutableMap.of("c", ImmutableMap.of("dashed-index", "goodbye"))));

    assertThat(result).isTrue();
  }

  @Test
  @TestParameters("{source: \"optm.c.index.orValue('default value') == 'goodbye'\"}")
  @TestParameters("{source: \"optm.c['index'].orValue('default value') == 'goodbye'\"}")
  @TestParameters("{source: \"optm.c.missing.orValue('default value') == 'default value'\"}")
  @TestParameters("{source: \"optm.c['missing'].orValue('default value') == 'default value'\"}")
  public void traditionalIndexSelection_onOptionalMap_chainedOperatorSuccess(String source)
      throws Exception {
    Cel cel =
        newCelBuilder()
            .addVar(
                "optm",
                OptionalType.create(
                    MapType.create(
                        SimpleType.STRING, MapType.create(SimpleType.STRING, SimpleType.STRING))))
            .setResultType(SimpleType.BOOL)
            .build();
    CelAbstractSyntaxTree ast = cel.compile(source).getAst();

    boolean result =
        (boolean)
            cel.createProgram(ast)
                .eval(
                    ImmutableMap.of(
                        "optm",
                        Optional.of(ImmutableMap.of("c", ImmutableMap.of("index", "goodbye")))));

    assertThat(result).isTrue();
  }

  @Test
  public void traditionalIndexSelection_onOptionalMap_orChainedList() throws Exception {
    Cel cel =
        newCelBuilder()
            .addVar(
                "optm",
                OptionalType.create(
                    MapType.create(
                        SimpleType.STRING, MapType.create(SimpleType.STRING, SimpleType.STRING))))
            .addVar("optl", OptionalType.create(ListType.create(SimpleType.STRING)))
            .setResultType(SimpleType.STRING)
            .build();

    CelAbstractSyntaxTree ast =
        cel.compile("optm.c.missing.or(optl[0]).orValue('default value')").getAst();

    String result =
        (String)
            cel.createProgram(ast)
                .eval(
                    ImmutableMap.of(
                        "optm",
                        Optional.of(ImmutableMap.of("c", ImmutableMap.of("index", "goodbye"))),
                        "optl",
                        Optional.of(ImmutableList.of("list-value"))));

    assertThat(result).isEqualTo("list-value");
  }

  @Test
  public void optionalMapCreation_valueIsEmpty_returnsEmptyMap() throws Exception {
    Cel cel = newCelBuilder().build();
    CelAbstractSyntaxTree ast = cel.compile("{?'key': optional.none()}").getAst();

    Map<String, Object> result = (Map<String, Object>) cel.createProgram(ast).eval();

    assertThat(result).isEmpty();
  }

  @Test
  public void optionalMapCreation_valueIsPresent_returnsMap() throws Exception {
    Cel cel = newCelBuilder().build();
    CelAbstractSyntaxTree ast = cel.compile("{?'key': optional.of(5)}").getAst();

    Map<String, Object> result = (Map<String, Object>) cel.createProgram(ast).eval();

    assertThat(result).containsExactly("key", 5L);
  }

  @Test
  public void optionalMapCreation_withNestedMap_returnsNestedMap() throws Exception {
    Cel cel =
        newCelBuilder()
            .setResultType(
                MapType.create(
                    SimpleType.STRING,
                    MapType.create(
                        SimpleType.STRING, MapType.create(SimpleType.STRING, SimpleType.STRING))))
            .addVar(
                "m",
                MapType.create(
                    SimpleType.STRING, MapType.create(SimpleType.STRING, SimpleType.STRING)))
            .build();
    CelAbstractSyntaxTree ast =
        cel.compile("{?'nested_map': optional.ofNonZeroValue({?'map': m.?c})}").getAst();

    Map<String, Map<String, Map<String, String>>> result =
        (Map<String, Map<String, Map<String, String>>>)
            cel.createProgram(ast)
                .eval(
                    ImmutableMap.of(
                        "m", ImmutableMap.of("c", ImmutableMap.of("dashed-index", "goodbye"))));

    assertThat(result)
        .containsExactly(
            "nested_map", ImmutableMap.of("map", ImmutableMap.of("dashed-index", "goodbye")));
  }

  @Test
  public void optionalMapCreation_withNestedMapContainingEmptyValue_emptyValueStripped()
      throws Exception {
    Cel cel =
        newCelBuilder()
            .setResultType(
                MapType.create(
                    SimpleType.STRING, MapType.create(SimpleType.STRING, SimpleType.STRING)))
            .addVar(
                "m",
                MapType.create(
                    SimpleType.STRING, MapType.create(SimpleType.STRING, SimpleType.STRING)))
            .build();

    CelAbstractSyntaxTree ast =
        cel.compile("{?'nested_map': optional.ofNonZeroValue({?'map': m.?c}), 'singleton': true}")
            .getAst();

    Object result = cel.createProgram(ast).eval(ImmutableMap.of("m", ImmutableMap.of()));

    assertThat(result).isEqualTo(ImmutableMap.of("singleton", true));
  }

  @Test
  public void optionalMapCreation_valueIsNonOptional_throws() {
    Cel cel = newCelBuilder().build();

    CelValidationException e =
        assertThrows(CelValidationException.class, () -> cel.compile("{?'hi': 'world'}").getAst());

    assertThat(e)
        .hasMessageThat()
        .contains("expected type 'optional_type(string)' but found 'string'");
  }

  @Test
  public void optionalMessageCreation_fieldValueIsEmpty_returnsEmptyMessage() throws Exception {
    Cel cel =
        newCelBuilder()
            .setResultType(StructTypeReference.create(TestAllTypes.getDescriptor().getFullName()))
            .build();
    CelAbstractSyntaxTree ast =
        cel.compile("TestAllTypes{?single_double_wrapper: optional.ofNonZeroValue(0.0)}").getAst();

    TestAllTypes result = (TestAllTypes) cel.createProgram(ast).eval();

    assertThat(result).isEqualTo(TestAllTypes.getDefaultInstance());
  }

  @Test
  public void optionalMessageCreation_fieldValueIsPresent_returnsMessage() throws Exception {
    Cel cel = newCelBuilder().build();
    CelAbstractSyntaxTree ast =
        cel.compile("TestAllTypes{?single_double_wrapper: optional.ofNonZeroValue(5.0)}").getAst();

    TestAllTypes result = (TestAllTypes) cel.createProgram(ast).eval();

    assertThat(result)
        .isEqualTo(
            TestAllTypes.newBuilder()
                .setSingleDoubleWrapper(DoubleValue.newBuilder().setValue(5.0))
                .build());
  }

  @Test
  @TestParameters("{source: \"TestAllTypes{?map_string_string: m[?'nested']}\"}")
  @TestParameters(
      "{source: \"TestAllTypes{?map_string_string:"
          + " optional.ofNonZeroValue(m[?'nested'].orValue({}))}\"}")
  public void optionalMessageCreation_fieldValueContainsEmptyMap_returnsEmptyMessage(String source)
      throws Exception {
    Cel cel =
        newCelBuilder()
            .addVar(
                "m",
                MapType.create(
                    SimpleType.STRING, MapType.create(SimpleType.STRING, SimpleType.STRING)))
            .build();
    CelAbstractSyntaxTree ast = cel.compile(source).getAst();

    TestAllTypes result =
        (TestAllTypes) cel.createProgram(ast).eval(ImmutableMap.of("m", ImmutableMap.of()));

    assertThat(result).isEqualTo(TestAllTypes.getDefaultInstance());
  }

  @Test
  public void optionalMessageCreation_fieldValueContainsMap_returnsEmptyMessage() throws Exception {
    Cel cel =
        newCelBuilder()
            .addVar(
                "m",
                MapType.create(
                    SimpleType.STRING, MapType.create(SimpleType.STRING, SimpleType.STRING)))
            .build();
    CelAbstractSyntaxTree ast =
        cel.compile("TestAllTypes{?map_string_string: m[?'nested']}").getAst();

    TestAllTypes result =
        (TestAllTypes)
            cel.createProgram(ast)
                .eval(
                    ImmutableMap.of(
                        "m", ImmutableMap.of("nested", ImmutableMap.of("hello", "world"))));

    assertThat(result)
        .isEqualTo(TestAllTypes.newBuilder().putMapStringString("hello", "world").build());
  }

  @Test
  public void optionalListCreation_allElementsAreEmpty_returnsEmptyList() throws Exception {
    Cel cel =
        newCelBuilder()
            .setResultType(ListType.create(SimpleType.DYN))
            .addVar("m", MapType.create(SimpleType.STRING, SimpleType.DYN))
            .addVar("x", OptionalType.create(SimpleType.INT))
            .addVar("y", OptionalType.create(SimpleType.DYN))
            .build();
    CelAbstractSyntaxTree ast = cel.compile("[?m.?c, ?x, ?y]").getAst();

    List<?> result =
        (List<?>)
            cel.createProgram(ast)
                .eval(
                    ImmutableMap.of(
                        "m", ImmutableMap.of(), "x", Optional.empty(), "y", Optional.empty()));

    assertThat(result).isEmpty();
  }

  @Test
  public void optionalListCreation_containsEmptyElements_emptyElementsAreStripped()
      throws Exception {
    Cel cel =
        newCelBuilder()
            .setResultType(ListType.create(SimpleType.DYN))
            .addVar("m", MapType.create(SimpleType.STRING, SimpleType.DYN))
            .addVar("x", OptionalType.create(SimpleType.INT))
            .addVar("y", OptionalType.create(SimpleType.DYN))
            .build();
    CelAbstractSyntaxTree ast = cel.compile("[?m.?c, ?x, ?y]").getAst();

    List<?> result =
        (List<?>)
            cel.createProgram(ast)
                .eval(
                    ImmutableMap.of(
                        "m", ImmutableMap.of(), "x", Optional.of(42), "y", Optional.empty()));

    assertThat(result).containsExactly(42L);
  }

  @Test
  public void optionalListCreation_containsMixedTypeElements_success() throws Exception {
    Cel cel =
        newCelBuilder()
            .setResultType(ListType.create(SimpleType.DYN))
            .addVar(
                "m",
                MapType.create(
                    SimpleType.STRING, MapType.create(SimpleType.STRING, SimpleType.STRING)))
            .addVar("x", OptionalType.create(SimpleType.INT))
            .addVar("y", OptionalType.create(SimpleType.DYN))
            .addVar("z", SimpleType.STRING)
            .build();
    CelAbstractSyntaxTree ast = cel.compile("[?m.?c, ?x, ?y, z]").getAst();

    List<?> result =
        (List<?>)
            cel.createProgram(ast)
                .eval(
                    ImmutableMap.of(
                        "m",
                        ImmutableMap.of("c", ImmutableMap.of("dashed-value", "goodbye")),
                        "x",
                        Optional.of(42),
                        "y",
                        Optional.of(10),
                        "z",
                        "hello world"));

    assertThat(result)
        .containsExactly(ImmutableMap.of("dashed-value", "goodbye"), 42L, 10L, "hello world");
  }

  @Test
  public void
      optionalListCreation_containsMixedTypeElements_throwsWhenHomogeneousLiteralsEnabled() {
    Cel cel =
        newCelBuilder()
            .setOptions(CelOptions.current().enableHomogeneousLiterals(true).build())
            .addVar(
                "m",
                MapType.create(
                    SimpleType.STRING, MapType.create(SimpleType.STRING, SimpleType.STRING)))
            .addVar("x", OptionalType.create(SimpleType.INT))
            .addVar("y", OptionalType.create(SimpleType.DYN))
            .build();

    CelValidationException e =
        assertThrows(CelValidationException.class, () -> cel.compile("[?m.?c, ?x, ?y]").getAst());

    assertThat(e).hasMessageThat().contains("expected type 'map(string, string)' but found 'int'");
  }

  @Test
  public void optionalListCreation_withinProtoMessage_success() throws Exception {
    Cel cel =
        newCelBuilder()
            .addVar("m", MapType.create(SimpleType.STRING, SimpleType.DYN))
            .addVar("x", OptionalType.create(SimpleType.DYN))
            .addVar("y", OptionalType.create(SimpleType.DYN))
            .setResultType(StructTypeReference.create(TestAllTypes.getDescriptor().getFullName()))
            .build();
    String expression =
        "TestAllTypes{repeated_string: ['greetings', ?m.nested.?hello], ?repeated_int32:"
            + " optional.ofNonZeroValue([?x, ?y])}";
    CelAbstractSyntaxTree ast = cel.compile(expression).getAst();

    TestAllTypes result =
        (TestAllTypes)
            cel.createProgram(ast)
                .eval(
                    ImmutableMap.of(
                        "m", ImmutableMap.of("nested", ImmutableMap.of("hello", "world")),
                        "x", Optional.empty(),
                        "y", Optional.empty()));

    assertThat(result)
        .isEqualTo(
            TestAllTypes.newBuilder()
                .addRepeatedString("greetings")
                .addRepeatedString("world")
                .build());
  }

  @Test
  public void optionalMapMacro_receiverIsEmpty_returnsOptionalEmpty() throws Exception {
    Cel cel =
        newCelBuilder()
            .addVar("x", OptionalType.create(SimpleType.INT))
            .setResultType(OptionalType.create(SimpleType.INT))
            .build();
    CelAbstractSyntaxTree ast = cel.compile("x.optMap(y, y + 1)").getAst();

    Optional<?> result =
        (Optional<?>) cel.createProgram(ast).eval(ImmutableMap.of("x", Optional.empty()));

    assertThat(result).isEmpty();
  }

  @Test
  public void optionalMapMacro_receiverHasValue_returnsOptionalValue() throws Exception {
    Cel cel =
        newCelBuilder()
            .addVar("x", OptionalType.create(SimpleType.INT))
            .setResultType(OptionalType.create(SimpleType.INT))
            .build();
    CelAbstractSyntaxTree ast = cel.compile("x.optMap(y, y + 1)").getAst();

    Optional<?> result =
        (Optional<?>) cel.createProgram(ast).eval(ImmutableMap.of("x", Optional.of(42L)));

    assertThat(result).hasValue(43L);
  }

  @Test
  public void optionalMapMacro_withNonIdent_throws() {
    Cel cel =
        newCelBuilder()
            .addVar("x", OptionalType.create(SimpleType.INT))
            .setResultType(OptionalType.create(SimpleType.INT))
            .build();

    CelValidationException e =
        assertThrows(
            CelValidationException.class, () -> cel.compile("x.optMap(y.z, y.z + 1)").getAst());

    assertThat(e).hasMessageThat().contains("optMap() variable name must be a simple identifier");
  }

  @Test
  public void optionalFlatMapMacro_receiverIsEmpty_returnsOptionalEmpty() throws Exception {
    Cel cel =
        newCelBuilder()
            .addVar("x", OptionalType.create(SimpleType.INT))
            .setResultType(OptionalType.create(SimpleType.INT))
            .build();
    CelAbstractSyntaxTree ast = cel.compile("x.optFlatMap(y, optional.of(y + 1))").getAst();

    Optional<?> result =
        (Optional<?>) cel.createProgram(ast).eval(ImmutableMap.of("x", Optional.empty()));

    assertThat(result).isEmpty();
  }

  @Test
  public void optionalFlatMapMacro_receiverHasValue_returnsOptionalValue() throws Exception {
    Cel cel =
        newCelBuilder()
            .addVar("x", OptionalType.create(SimpleType.INT))
            .setResultType(OptionalType.create(SimpleType.INT))
            .build();
    CelAbstractSyntaxTree ast = cel.compile("x.optFlatMap(y, optional.of(y + 1))").getAst();

    Optional<?> result =
        (Optional<?>) cel.createProgram(ast).eval(ImmutableMap.of("x", Optional.of(42L)));

    assertThat(result).hasValue(43L);
  }

  @Test
  public void optionalFlatMapMacro_withOptionalOfNonZeroValue_optionalEmptyWhenValueIsZero()
      throws Exception {
    Cel cel =
        newCelBuilder()
            .addVar("x", OptionalType.create(SimpleType.INT))
            .setResultType(OptionalType.create(SimpleType.INT))
            .build();
    CelAbstractSyntaxTree ast =
        cel.compile("x.optFlatMap(y, optional.ofNonZeroValue(y - 1))").getAst();

    Optional<?> result =
        (Optional<?>) cel.createProgram(ast).eval(ImmutableMap.of("x", Optional.of(1L)));

    assertThat(result).isEmpty();
  }

  @Test
  public void optionalFlatMapMacro_withOptionalOfNonZeroValue_optionalValueWhenValueExists()
      throws Exception {
    Cel cel =
        newCelBuilder()
            .addVar("x", OptionalType.create(SimpleType.INT))
            .setResultType(OptionalType.create(SimpleType.INT))
            .build();
    CelAbstractSyntaxTree ast =
        cel.compile("x.optFlatMap(y, optional.ofNonZeroValue(y + 1))").getAst();

    Optional<?> result =
        (Optional<?>) cel.createProgram(ast).eval(ImmutableMap.of("x", Optional.of(1L)));

    assertThat(result).hasValue(2L);
  }

  @Test
  public void optionalFlatMapMacro_mappingExprIsNonOptional_throws() {
    Cel cel =
        newCelBuilder()
            .addVar("x", OptionalType.create(SimpleType.INT))
            .setResultType(OptionalType.create(SimpleType.INT))
            .build();
    CelValidationException e =
        assertThrows(
            CelValidationException.class, () -> cel.compile("x.optFlatMap(y, y + 1)").getAst());

    assertThat(e).hasMessageThat().contains("found no matching overload for '_?_:_'");
  }

  @Test
  public void optionalFlatMapMacro_withNonIdent_throws() {
    Cel cel =
        newCelBuilder()
            .addVar("x", OptionalType.create(SimpleType.INT))
            .setResultType(OptionalType.create(SimpleType.INT))
            .build();

    CelValidationException e =
        assertThrows(
            CelValidationException.class, () -> cel.compile("x.optFlatMap(y.z, y.z + 1)").getAst());

    assertThat(e)
        .hasMessageThat()
        .contains("optFlatMap() variable name must be a simple identifier");
  }

  @Test
  public void optionalType_typeResolution() throws Exception {
    Cel cel = newCelBuilder().build();
    CelAbstractSyntaxTree ast = cel.compile("optional_type").getAst();

    TypeType optionalRuntimeType = (TypeType) cel.createProgram(ast).eval();

    assertThat(optionalRuntimeType.name()).isEqualTo("type");
    assertThat(optionalRuntimeType.containingTypeName()).isEqualTo("optional_type");
  }

  @Test
  public void optionalType_typeComparison() throws Exception {
    Cel cel = newCelBuilder().build();

    CelAbstractSyntaxTree ast = cel.compile("type(optional.none()) == optional_type").getAst();

    assertThat(cel.createProgram(ast).eval()).isEqualTo(true);
  }

  @Test
  @TestParameters("{expression: '[].first().hasValue() == false'}")
  @TestParameters("{expression: '[\"a\",\"b\",\"c\"].first().value() == \"a\"'}")
  public void listFirst_success(String expression) throws Exception {
    Cel cel = newCelBuilder().build();
    boolean result = (boolean) cel.createProgram(cel.compile(expression).getAst()).eval();
    assertThat(result).isTrue();
  }

  @Test
  @TestParameters("{expression: '[].last().hasValue() == false'}")
  @TestParameters("{expression: '[1, 2, 3].last().value() == 3'}")
  public void listLast_success(String expression) throws Exception {
    Cel cel = newCelBuilder().build();
    boolean result = (boolean) cel.createProgram(cel.compile(expression).getAst()).eval();
    assertThat(result).isTrue();
  }

  @Test
  @TestParameters("{expression: '[1].first()', expectedError: 'undeclared reference to ''first'''}")
  @TestParameters("{expression: '[2].last()', expectedError: 'undeclared reference to ''last'''}")
  public void listFirstAndLast_throws_earlyVersion(String expression, String expectedError)
      throws Exception {
    // Configure Cel with an earlier version of the 'optional' library, which did not support
    // 'first' and 'last'
    Cel cel = newCelBuilder(1).build();
    assertThat(
            assertThrows(
                CelValidationException.class,
                () -> {
                  cel.createProgram(cel.compile(expression).getAst()).eval();
                }))
        .hasMessageThat()
        .contains(expectedError);
  }
}
