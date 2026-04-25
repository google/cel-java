// Copyright 2026 Google LLC
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
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertThrows;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.primitives.UnsignedLong;
import com.google.testing.junit.testparameterinjector.TestParameter;
import com.google.testing.junit.testparameterinjector.TestParameterInjector;
import dev.cel.bundle.Cel;
import dev.cel.bundle.CelFactory;
import dev.cel.common.CelAbstractSyntaxTree;
import dev.cel.common.CelContainer;
import dev.cel.common.CelValidationException;
import dev.cel.common.exceptions.CelAttributeNotFoundException;
import dev.cel.common.types.CelType;
import dev.cel.common.types.ListType;
import dev.cel.common.types.MapType;
import dev.cel.common.types.OptionalType;
import dev.cel.common.types.SimpleType;
import dev.cel.common.types.StructType;
import dev.cel.common.types.StructTypeReference;
import dev.cel.common.values.CelByteString;
import dev.cel.common.values.CelValueProvider;
import dev.cel.compiler.CelCompiler;
import dev.cel.compiler.CelCompilerFactory;
import dev.cel.expr.conformance.proto3.TestAllTypes;
import dev.cel.runtime.CelEvaluationException;
import dev.cel.runtime.CelRuntime;
import dev.cel.runtime.CelRuntimeFactory;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(TestParameterInjector.class)
public final class CelNativeTypesExtensionsTest {

  @TestParameter boolean isParseOnly;

  private static final CelNativeTypesExtensions NATIVE_TYPE_EXTENSIONS =
      CelExtensions.nativeTypes(
          TestAllTypesPublicFieldsPojo.class,
          TestPrivateConstructorPojo.class,
          ComprehensiveTestAllTypes.class,
          TestGetterSetterPojo.class,
          TestMissingNoArgConstructorPojo.class,
          TestPrivateFieldPojo.class,
          TestDeepConversionPojo.class,
          TestPrecedencePojo.class,
          TestPrefixLessGetterPojo.class,
          TestChildPojo.class,
          TestPackagePrivatePojo.class,
          TestPackagePrivateWithGetterPojo.class,
          TestWildcardPojo.class,
          ComprehensiveTestNestedType.class,
          TestNestedSliceType.class,
          TestMapVal.class);

  private static final Cel CEL =
      CelFactory.plannerCelBuilder()
          .setContainer(CelContainer.ofName("dev.cel.extensions.CelNativeTypesExtensionsTest"))
          .addCompilerLibraries(NATIVE_TYPE_EXTENSIONS)
          .addRuntimeLibraries(NATIVE_TYPE_EXTENSIONS)
          .build();

  private Object eval(String expr) throws Exception {
    return eval(expr, ImmutableMap.of());
  }

  private Object eval(String expr, Map<String, ?> variables) throws Exception {
    CelAbstractSyntaxTree ast = isParseOnly ? CEL.parse(expr).getAst() : CEL.compile(expr).getAst();
    return CEL.createProgram(ast).eval(variables);
  }

  @Test
  public void nativeTypes_createStructAndSelect() throws Exception {
    Object result =
        eval(
            "TestAllTypesPublicFieldsPojo{boolVal:"
                + " true, stringVal: 'hello'}.stringVal == 'hello'");

    assertThat(result).isEqualTo(true);
  }

  @Test
  public void nativeTypes_createNestedStruct() throws Exception {
    Object result =
        eval(
            "TestAllTypesPublicFieldsPojo{nestedVal:"
                + " TestNestedType{value:"
                + " 'nested'}}.nestedVal.value == 'nested'");

    assertThat(result).isEqualTo(true);
  }

  @Test
  public void nativeTypes_resolveVariableWithNestedField() throws Exception {
    Cel cel =
        CelFactory.plannerCelBuilder()
            .setContainer(CelContainer.ofName("dev.cel.extensions.CelNativeTypesExtensionsTest"))
            .addVar(
                "pojo",
                StructTypeReference.create(TestAllTypesPublicFieldsPojo.class.getCanonicalName()))
            .addCompilerLibraries(NATIVE_TYPE_EXTENSIONS)
            .addRuntimeLibraries(NATIVE_TYPE_EXTENSIONS)
            .build();
    CelAbstractSyntaxTree ast =
        isParseOnly
            ? cel.parse("pojo.nestedVal.value == 'nested'").getAst()
            : cel.compile("pojo.nestedVal.value == 'nested'").getAst();
    CelRuntime.Program program = cel.createProgram(ast);
    TestAllTypesPublicFieldsPojo pojo = new TestAllTypesPublicFieldsPojo();
    TestNestedType nested = new TestNestedType();
    nested.value = "nested";
    pojo.nestedVal = nested;

    Object result = program.eval(ImmutableMap.of("pojo", pojo));

    assertThat(result).isEqualTo(true);
  }

  @Test
  public void nativeTypes_createStructWithComplexTypes() throws Exception {
    assertThat(
            eval(
                "TestAllTypesPublicFieldsPojo{"
                    + "  durationVal: duration('5s'),"
                    + "  listVal: ['a', 'b'],"
                    + "  mapVal: {'key': 'value'}"
                    + "}.durationVal == duration('5s')"))
        .isEqualTo(true);
  }

  @Test
  public void nativeTypes_createStructWithOptionalField() throws Exception {
    Cel cel =
        CelFactory.plannerCelBuilder()
            .setContainer(CelContainer.ofName("dev.cel.extensions.CelNativeTypesExtensionsTest"))
            .addCompilerLibraries(
                CelExtensions.nativeTypes(TestRefValFieldType.class), CelExtensions.optional())
            .addRuntimeLibraries(
                CelExtensions.nativeTypes(TestRefValFieldType.class), CelExtensions.optional())
            .build();
    CelAbstractSyntaxTree ast =
        cel.parse(
                "TestRefValFieldType{optionalName: optional.of('my name')}.optionalName.orValue('')"
                    + " == 'my name'")
            .getAst();
    CelRuntime.Program program = cel.createProgram(ast);

    Object result = program.eval();

    assertThat(result).isEqualTo(true);
  }

  @Test
  public void nativeTypes_createComprehensiveStruct() throws Exception {
    String expr =
        "ComprehensiveTestAllTypes{\n"
            + "  nestedVal: ComprehensiveTestNestedType{nestedMapVal: {1: false}},\n"
            + "  boolVal: true,\n"
            + "  bytesVal: b'hello',\n"
            + "  durationVal: duration('5s'),\n"
            + "  doubleVal: 1.5,\n"
            + "  floatVal: 2.5,\n"
            + "  int32Val: 10,\n"
            + "  int64Val: 20,\n"
            + "  stringVal: 'hello world',\n"
            + "  timestampVal: timestamp('2011-08-06T01:23:45Z'),\n"
            + "  uint32Val: 100,\n"
            + "  uint64Val: 200,\n"
            + "  listVal: [\n"
            + "    ComprehensiveTestNestedType{\n"
            + "      nestedListVal:['goodbye', 'cruel', 'world'],\n"
            + "      nestedMapVal: {42: true},\n"
            + "      customName: 'name'\n"
            + "    }\n"
            + "  ],\n"
            + "  arrayVal: [\n"
            + "    ComprehensiveTestNestedType{\n"
            + "      nestedListVal:['goodbye', 'cruel', 'world'],\n"
            + "      nestedMapVal: {42: true},\n"
            + "      customName: 'name'\n"
            + "    }\n"
            + "  ],\n"
            + "  mapVal: {'map-key': ComprehensiveTestAllTypes{boolVal: true}},\n"
            + "  customSliceVal: [TestNestedSliceType{value: 'none'}],\n"
            + "  customMapVal: {'even': TestMapVal{value: 'more'}},\n"
            + "  customName: 'name'\n"
            + "}";

    CelAbstractSyntaxTree ast = CEL.parse(expr).getAst();
    CelRuntime.Program program = CEL.createProgram(ast);
    Object result = program.eval();

    // Construct expected output
    ComprehensiveTestAllTypes expected = new ComprehensiveTestAllTypes();
    expected.boolVal = true;
    expected.bytesVal = "hello".getBytes(UTF_8);
    expected.durationVal = Duration.ofSeconds(5);
    expected.doubleVal = 1.5;
    expected.floatVal = 2.5f;
    expected.int32Val = 10;
    expected.int64Val = 20;
    expected.stringVal = "hello world";
    expected.timestampVal = Instant.parse("2011-08-06T01:23:45Z");
    expected.uint32Val = 100;
    expected.uint64Val = 200;
    expected.customName = "name";

    ComprehensiveTestNestedType nested1 = new ComprehensiveTestNestedType();
    nested1.nestedMapVal = ImmutableMap.of(1L, false);
    expected.nestedVal = nested1;

    ComprehensiveTestNestedType nested2 = new ComprehensiveTestNestedType();
    nested2.nestedListVal = ImmutableList.of("goodbye", "cruel", "world");
    nested2.nestedMapVal = ImmutableMap.of(42L, true);
    nested2.customName = "name";
    expected.listVal = ImmutableList.of(nested2);
    expected.arrayVal = ImmutableList.of(nested2);

    ComprehensiveTestAllTypes mapValElement = new ComprehensiveTestAllTypes();
    mapValElement.boolVal = true;
    expected.mapVal = ImmutableMap.of("map-key", mapValElement);

    TestNestedSliceType sliceElem = new TestNestedSliceType();
    sliceElem.value = "none";
    expected.customSliceVal = ImmutableList.of(sliceElem);

    TestMapVal mapValElem = new TestMapVal();
    mapValElem.value = "more";
    expected.customMapVal = ImmutableMap.of("even", mapValElem);

    assertThat(result).isEqualTo(expected);
  }

  @Test
  public void nativeTypes_staticErrors() throws Exception {
    // undeclared reference
    CelValidationException e =
        assertThrows(CelValidationException.class, () -> CEL.compile("UnknownType{}").getAst());
    assertThat(e).hasMessageThat().contains("reference");

    // undefined field
    e =
        assertThrows(
            CelValidationException.class,
            () -> CEL.compile("ComprehensiveTestAllTypes{undefinedField: true}").getAst());
    assertThat(e).hasMessageThat().contains("undefined field");
  }

  @Test
  public void nativeTypes_anonymousClass_throwsException() {
    Object anon = new Object() {};

    Class<?> clazz = anon.getClass();
    IllegalArgumentException exception =
        assertThrows(IllegalArgumentException.class, () -> CelExtensions.nativeTypes(clazz));
    assertThat(exception).hasMessageThat().contains("Anonymous or local classes are not supported");
  }

  @Test
  public void nativeTypes_createStruct_privateConstructor() throws Exception {
    Object result = eval("TestPrivateConstructorPojo{value:" + " 'hello'}");

    assertThat(result).isInstanceOf(TestPrivateConstructorPojo.class);
    assertThat(((TestPrivateConstructorPojo) result).value).isEqualTo("hello");
  }

  @Test
  public void nativeTypes_precedence_getterOverField() throws Exception {
    assertThat(eval("TestPrecedencePojo{}.value")).isEqualTo("hello");
  }

  @Test
  public void nativeTypes_protoPrecedence() throws Exception {
    CelValueProvider customProvider =
        (structType, fields) -> {
          if (structType.equals("cel.expr.conformance.proto3.TestAllTypes")) {
            return Optional.of("POJO_WINS");
          }
          return Optional.empty();
        };
    Cel cel =
        CelFactory.plannerCelBuilder()
            .setContainer(CelContainer.ofName("dev.cel.extensions.CelNativeTypesExtensionsTest"))
            .setValueProvider(customProvider)
            .addMessageTypes(TestAllTypes.getDescriptor())
            .build();
    CelAbstractSyntaxTree ast = cel.compile("cel.expr.conformance.proto3.TestAllTypes{}").getAst();

    Object result = cel.createProgram(ast).eval();

    assertThat(result).isNotEqualTo("POJO_WINS");
    assertThat(result).isInstanceOf(TestAllTypes.class);
  }

  @Test
  public void nativeTypes_createWithSetterAndSelectWithGetter() throws Exception {
    assertThat(eval("TestGetterSetterPojo{value: 'hello', active: true}.value == 'hello'"))
        .isEqualTo(true);
  }

  @Test
  public void nativeTypes_missingNoArgConstructor_throws() throws Exception {
    CelEvaluationException exception =
        assertThrows(
            CelEvaluationException.class,
            () -> eval("TestMissingNoArgConstructorPojo{value: 'hello'}"));

    assertThat(exception).hasMessageThat().contains("No public no-argument constructor found");
  }

  @Test
  public void nativeTypes_createWithDeepConversion() throws Exception {
    Object result = eval("TestDeepConversionPojo{ints: [1, 2], floats: {'a': 1.0, 'b': 2.0}}");

    assertThat(result).isInstanceOf(TestDeepConversionPojo.class);
    TestDeepConversionPojo pojo = (TestDeepConversionPojo) result;
    assertThat(pojo.ints.get(0)).isEqualTo(1);
    assertThat(pojo.floats).containsEntry("a", 1.0f);
  }

  @Test
  public void nativeTypes_wildcardList_success() throws Exception {
    assertThat(eval("TestWildcardPojo{values: ['hello']}.values[0] == 'hello'")).isEqualTo(true);
  }

  @Test
  public void nativeTypes_unsupportedTypeSet_throwsOnRegistration() throws Exception {
    IllegalArgumentException e =
        assertThrows(
            IllegalArgumentException.class,
            () -> CelExtensions.nativeTypes(TestUnsupportedSetPojo.class));
    assertThat(e).hasMessageThat().contains("Unsupported type for property 'strings'");
  }

  @Test
  public void nativeTypes_arrayType_throwsOnRegistration() throws Exception {
    IllegalArgumentException e =
        assertThrows(
            IllegalArgumentException.class, () -> CelExtensions.nativeTypes(TestArrayPojo.class));
    assertThat(e).hasMessageThat().contains("Unsupported type for property 'values'");
  }

  @Test
  public void nativeTypes_packagePrivateClass_fieldAccess_success() throws Exception {
    assertThat(eval("TestPackagePrivatePojo{value: 'hello'}.value == 'hello'")).isEqualTo(true);
  }

  @Test
  public void nativeTypes_packagePrivateClass_methodAccess_success() throws Exception {
    assertThat(eval("TestPackagePrivateWithGetterPojo{value: 'hello'}.value == 'hello'"))
        .isEqualTo(true);
  }

  @Test
  public void nativeTypes_privateField_notExposed() throws Exception {
    CelNativeTypesExtensions extensions = CelExtensions.nativeTypes(TestPrivateFieldPojo.class);
    CelCompiler compiler =
        CelCompilerFactory.standardCelCompilerBuilder()
            .setContainer(CelContainer.ofName("dev.cel.extensions.CelNativeTypesExtensionsTest"))
            .addLibraries(extensions)
            .build();

    CelValidationException e =
        assertThrows(
            CelValidationException.class,
            () -> compiler.compile("TestPrivateFieldPojo{secret: 'hello'}").getAst());
    assertThat(e).hasMessageThat().contains("undefined field");
  }

  @Test
  public void nativeTypes_inheritance_success() throws Exception {
    // Accessing child's prefix-less getter
    assertThat(eval("TestChildPojo{}.childValue")).isEqualTo("child");
    // Accessing parent's standard getter
    assertThat(eval("TestChildPojo{}.standardValue")).isEqualTo("standard");
    // Accessing parent's prefix-less getter
    assertThat(eval("TestChildPojo{}.parentValue")).isEqualTo("parent");
  }

  @Test
  public void nativeTypes_standardType_cannotBeConstructedAsStruct() throws Exception {
    CelValidationException e =
        assertThrows(
            CelValidationException.class, () -> CEL.compile("java.lang.String{}").getAst());
    assertThat(e).hasMessageThat().contains("undeclared reference");
  }

  @Test
  public void nativeTypes_doubleMapKey_throwsOnRegistration() throws Exception {
    IllegalArgumentException e =
        assertThrows(
            IllegalArgumentException.class,
            () -> CelExtensions.nativeTypes(TestDoubleMapKeyPojo.class));
    assertThat(e).hasCauseThat().hasMessageThat().contains("Decimals are not allowed as map keys");
  }

  @Test
  public void nativeTypes_optionalCustomStruct_registered() throws Exception {
    CelNativeTypesExtensions extensions = CelExtensions.nativeTypes(TestOptionalUrlPojo.class);
    CelNativeTypesExtensions.NativeTypeRegistry registry = extensions.getRegistry();

    Optional<CelType> type = registry.findType(TestURLPojo.class.getCanonicalName());

    assertThat(type).isPresent();
  }

  @Test
  public void nativeTypes_abstractClass_throwsOnConstruction() throws Exception {
    Cel cel =
        CelFactory.plannerCelBuilder()
            .setContainer(CelContainer.ofName("dev.cel.extensions.CelNativeTypesExtensionsTest"))
            .addCompilerLibraries(CelExtensions.nativeTypes(TestAbstractPojo.class))
            .addRuntimeLibraries(CelExtensions.nativeTypes(TestAbstractPojo.class))
            .build();
    CelAbstractSyntaxTree ast = cel.parse("TestAbstractPojo{}").getAst();
    CelRuntime.Program program = cel.createProgram(ast);

    CelEvaluationException e = assertThrows(CelEvaluationException.class, () -> program.eval());
    assertThat(e).hasMessageThat().contains("Failed to create instance of");
    assertThat(e).hasCauseThat().isInstanceOf(InstantiationException.class);
  }

  @Test
  public void nativeTypes_nestedList_registered() throws Exception {
    CelNativeTypesExtensions extensions =
        CelExtensions.nativeTypes(TestAllTypesPublicFieldsPojo.class);
    CelNativeTypesExtensions.NativeTypeRegistry registry = extensions.getRegistry();

    Optional<CelType> type =
        registry.findType(TestAllTypesPublicFieldsPojo.class.getCanonicalName());

    assertThat(type).isPresent();
    StructType structType = (StructType) type.get();
    assertThat(structType.findField("nestedListVal")).isPresent();
  }

  @Test
  public void nativeTypes_invalidGetters_notRegistered() throws Exception {
    ImmutableSet<String> properties =
        CelNativeTypesExtensions.NativeTypeScanner.getProperties(
            TestAllTypesPublicFieldsPojo.class);

    assertThat(properties).doesNotContain("invalidParam");
    assertThat(properties).doesNotContain("invalidString");
  }

  @Test
  public void nativeTypes_celByteString_success() throws Exception {
    assertThat(eval("TestAllTypesPublicFieldsPojo{}.celBytesVal" + " == b'\\x01\\x02\\x03'"))
        .isEqualTo(true);
  }

  @Test
  public void nativeTypes_celByteString_construction_success() throws Exception {
    assertThat(
            eval(
                "dev.cel.extensions.CelNativeTypesExtensionsTest.TestAllTypesPublicFieldsPojo{celBytesVal:"
                    + " b'\\x01\\x02\\x03'}.celBytesVal == b'\\x01\\x02\\x03'"))
        .isEqualTo(true);
  }

  @Test
  public void nativeTypes_singleLetterGetter_success() throws Exception {
    Object result = eval("TestAllTypesPublicFieldsPojo{}.a == 'a'");
    assertThat(result).isEqualTo(true);
  }

  @Test
  public void nativeTypes_getterNamedGet_rejected() throws Exception {
    CelValidationException e =
        assertThrows(
            CelValidationException.class,
            () -> CEL.compile("TestAllTypesPublicFieldsPojo{}.get").getAst());
    assertThat(e).hasMessageThat().contains("undefined field 'get'");
  }

  @Test
  public void nativeTypes_circularReference_success() throws Exception {
    CelNativeTypesExtensions extensions = CelExtensions.nativeTypes(TestCircularA.class);
    CelNativeTypesExtensions.NativeTypeRegistry registry = extensions.getRegistry();

    Optional<CelType> typeA = registry.findType(TestCircularA.class.getCanonicalName());
    Optional<CelType> typeB = registry.findType(TestCircularB.class.getCanonicalName());

    assertThat(typeA).isPresent();
    assertThat(typeB).isPresent();
  }

  @Test
  public void nativeTypes_specialDecapitalization_success() throws Exception {
    Cel cel =
        CelFactory.plannerCelBuilder()
            .setContainer(CelContainer.ofName("dev.cel.extensions.CelNativeTypesExtensionsTest"))
            .addCompilerLibraries(CelExtensions.nativeTypes(TestURLPojo.class))
            .addRuntimeLibraries(CelExtensions.nativeTypes(TestURLPojo.class))
            .build();
    CelAbstractSyntaxTree ast =
        cel.parse("dev.cel.extensions.CelNativeTypesExtensionsTest.TestURLPojo{}.URL").getAst();
    CelRuntime.Program program = cel.createProgram(ast);

    Object result = program.eval();

    assertThat(result).isEqualTo("https://google.com");
  }

  @Test
  public void nativeTypes_prefixLessGetter_success() throws Exception {
    CelNativeTypesExtensions extensions = CelExtensions.nativeTypes(TestPrefixLessGetterPojo.class);
    CelRuntime celRuntime =
        CelRuntimeFactory.plannerRuntimeBuilder()
            .setContainer(CelContainer.ofName("dev.cel.extensions.CelNativeTypesExtensionsTest"))
            .addLibraries(extensions)
            .build();
    CelCompiler celCompiler =
        CelCompilerFactory.standardCelCompilerBuilder()
            .setContainer(CelContainer.ofName("dev.cel.extensions.CelNativeTypesExtensionsTest"))
            .addLibraries(extensions)
            .build();
    CelAbstractSyntaxTree ast =
        celCompiler
            .compile(
                "dev.cel.extensions.CelNativeTypesExtensionsTest.TestPrefixLessGetterPojo{}.value")
            .getAst();
    CelRuntime.Program program = celRuntime.createProgram(ast);

    Object result = program.eval();

    assertThat(result).isEqualTo("hello");
  }

  @Test
  public void nativeTypes_isGetter_success() throws Exception {
    CelNativeTypesExtensions extensions = CelExtensions.nativeTypes(TestGetterSetterPojo.class);
    CelRuntime celRuntime =
        CelRuntimeFactory.plannerRuntimeBuilder()
            .setContainer(CelContainer.ofName("dev.cel.extensions.CelNativeTypesExtensionsTest"))
            .addLibraries(extensions)
            .build();
    CelCompiler celCompiler =
        CelCompilerFactory.standardCelCompilerBuilder()
            .setContainer(CelContainer.ofName("dev.cel.extensions.CelNativeTypesExtensionsTest"))
            .addLibraries(extensions)
            .build();
    CelAbstractSyntaxTree ast =
        celCompiler
            .compile(
                "dev.cel.extensions.CelNativeTypesExtensionsTest.TestGetterSetterPojo{active:"
                    + " true}.active")
            .getAst();
    CelRuntime.Program program = celRuntime.createProgram(ast);

    Object result = program.eval();

    assertThat(result).isEqualTo(true);
  }

  @Test
  public void nativeTypes_selectUndefinedField_parsedOnly_throwsException() throws Exception {

    CelNativeTypesExtensions extensions =
        CelExtensions.nativeTypes(TestAllTypesPublicFieldsPojo.class);

    CelRuntime celRuntime =
        CelRuntimeFactory.plannerRuntimeBuilder()
            .setContainer(CelContainer.ofName("dev.cel.extensions.CelNativeTypesExtensionsTest"))
            .addLibraries(extensions)
            .build();

    CelCompiler celCompiler =
        CelCompilerFactory.standardCelCompilerBuilder()
            .setContainer(CelContainer.ofName("dev.cel.extensions.CelNativeTypesExtensionsTest"))
            .addLibraries(extensions)
            .build();

    CelAbstractSyntaxTree ast = celCompiler.parse("pojo.undefinedField").getAst();
    CelRuntime.Program program = celRuntime.createProgram(ast);

    TestAllTypesPublicFieldsPojo pojo = new TestAllTypesPublicFieldsPojo();

    CelEvaluationException e =
        assertThrows(
            CelEvaluationException.class, () -> program.eval(ImmutableMap.of("pojo", pojo)));
    assertThat(e).hasCauseThat().isInstanceOf(CelAttributeNotFoundException.class);
  }

  @Test
  public void nativeTypes_createWithUint_fromUnsignedLong() throws Exception {
    CelNativeTypesExtensions extensions =
        CelExtensions.nativeTypes(TestAllTypesPublicFieldsPojo.class);
    CelRuntime celRuntime =
        CelRuntimeFactory.plannerRuntimeBuilder()
            .setContainer(CelContainer.ofName("dev.cel.extensions.CelNativeTypesExtensionsTest"))
            .addLibraries(extensions)
            .build();
    CelCompiler celCompiler =
        CelCompilerFactory.standardCelCompilerBuilder()
            .setContainer(CelContainer.ofName("dev.cel.extensions.CelNativeTypesExtensionsTest"))
            .addLibraries(extensions)
            .build();
    CelAbstractSyntaxTree ast =
        celCompiler
            .compile(
                "dev.cel.extensions.CelNativeTypesExtensionsTest.TestAllTypesPublicFieldsPojo{uintVal:"
                    + " 42u}")
            .getAst();
    CelRuntime.Program program = celRuntime.createProgram(ast);

    Object result = program.eval();

    assertThat(result).isInstanceOf(TestAllTypesPublicFieldsPojo.class);
    TestAllTypesPublicFieldsPojo pojo = (TestAllTypesPublicFieldsPojo) result;
    assertThat(pojo.uintVal).isEqualTo(UnsignedLong.fromLongBits(42L));
  }

  @Test
  public void nativeTypes_mapJavaTypeToCelType_allSupportedTypes() throws Exception {

    CelNativeTypesExtensions extensions =
        CelExtensions.nativeTypes(TestAllTypesPublicFieldsPojo.class);
    CelNativeTypesExtensions.NativeTypeRegistry registry = extensions.getRegistry();

    Optional<CelType> type =
        registry.findType(TestAllTypesPublicFieldsPojo.class.getCanonicalName());

    assertThat(type).isPresent();
    assertThat(type.get()).isInstanceOf(StructType.class);
    StructType structType = (StructType) type.get();

    assertThat(structType.findField("boolVal").map(StructType.Field::type))
        .hasValue(SimpleType.BOOL);
    assertThat(structType.findField("boolObjVal").map(StructType.Field::type))
        .hasValue(SimpleType.BOOL);
    assertThat(structType.findField("int32Val").map(StructType.Field::type))
        .hasValue(SimpleType.INT);
    assertThat(structType.findField("intObjVal").map(StructType.Field::type))
        .hasValue(SimpleType.INT);
    assertThat(structType.findField("int64Val").map(StructType.Field::type))
        .hasValue(SimpleType.INT);
    assertThat(structType.findField("longObjVal").map(StructType.Field::type))
        .hasValue(SimpleType.INT);
    assertThat(structType.findField("uintVal").map(StructType.Field::type))
        .hasValue(SimpleType.UINT);
    assertThat(structType.findField("floatVal").map(StructType.Field::type))
        .hasValue(SimpleType.DOUBLE);
    assertThat(structType.findField("floatObjVal").map(StructType.Field::type))
        .hasValue(SimpleType.DOUBLE);
    assertThat(structType.findField("doubleVal").map(StructType.Field::type))
        .hasValue(SimpleType.DOUBLE);
    assertThat(structType.findField("doubleObjVal").map(StructType.Field::type))
        .hasValue(SimpleType.DOUBLE);
    assertThat(structType.findField("stringVal").map(StructType.Field::type))
        .hasValue(SimpleType.STRING);
    assertThat(structType.findField("bytesVal").map(StructType.Field::type))
        .hasValue(SimpleType.BYTES);
    assertThat(structType.findField("durationVal").map(StructType.Field::type))
        .hasValue(SimpleType.DURATION);
    assertThat(structType.findField("timestampVal").map(StructType.Field::type))
        .hasValue(SimpleType.TIMESTAMP);

    assertThat(structType.findField("listVal").map(StructType.Field::type).get())
        .isInstanceOf(ListType.class);
    ListType listType =
        (ListType) structType.findField("listVal").map(StructType.Field::type).get();
    assertThat(listType.elemType()).isEqualTo(SimpleType.STRING);

    assertThat(structType.findField("mapIntVal").map(StructType.Field::type).get())
        .isInstanceOf(MapType.class);
    MapType mapType = (MapType) structType.findField("mapIntVal").map(StructType.Field::type).get();
    assertThat(mapType.keyType()).isEqualTo(SimpleType.STRING);
    assertThat(mapType.valueType()).isEqualTo(SimpleType.INT);

    assertThat(structType.findField("optionalVal").map(StructType.Field::type).get())
        .isInstanceOf(OptionalType.class);
    OptionalType optionalType =
        (OptionalType) structType.findField("optionalVal").map(StructType.Field::type).get();
    assertThat(optionalType.parameters().get(0)).isEqualTo(SimpleType.STRING);
  }

  @Test
  public void nativeTypes_objectMethods_notExposed() throws Exception {
    CelNativeTypesExtensions extensions =
        CelExtensions.nativeTypes(TestAllTypesPublicFieldsPojo.class);
    CelCompiler compiler =
        CelCompilerFactory.standardCelCompilerBuilder()
            .setContainer(CelContainer.ofName("dev.cel.extensions.CelNativeTypesExtensionsTest"))
            .addLibraries(extensions)
            .build();

    CelValidationException e =
        assertThrows(
            CelValidationException.class,
            () -> compiler.compile("TestAllTypesPublicFieldsPojo{}.toString").getAst());
    assertThat(e).hasMessageThat().contains("undefined field");
  }

  public static class TestAllTypesPublicFieldsPojo {
    public void doNothing() {}

    public String getA() {
      return "a";
    }

    public String get() {
      return "get";
    }

    public boolean boolVal;
    public String stringVal;
    public long int64Val;
    public int int32Val;
    public double doubleVal;
    public float floatVal;
    public byte[] bytesVal;
    public Duration durationVal;
    public Instant timestampVal;
    public TestNestedType nestedVal;
    public List<String> listVal;
    public Map<String, String> mapVal;

    public Boolean boolObjVal;
    public Integer intObjVal;
    public Long longObjVal;
    public UnsignedLong uintVal;
    public Float floatObjVal;
    public Double doubleObjVal;
    public Optional<String> optionalVal;
    public Optional<TestNestedType> optionalNestedVal;
    public Map<String, Integer> mapIntVal;
    public List<List<String>> nestedListVal;
    public CelByteString celBytesVal = CelByteString.of(new byte[] {1, 2, 3});

    public String getInvalidParam(String param) {
      return "invalid";
    }

    public String isInvalidString() {
      return "invalid";
    }
  }

  public static class TestNestedType {
    public String value;
  }

  static class TestPackagePrivatePojo {
    public String value;
  }

  static class TestPackagePrivateWithGetterPojo {
    private String value;

    public String getValue() {
      return value;
    }

    public void setValue(String value) {
      this.value = value;
    }
  }

  public static class TestPrivateConstructorPojo {
    public String value;

    private TestPrivateConstructorPojo() {
      this.value = "default";
    }
  }

  public static class TestPrecedencePojo {
    public int value = 1;

    public String getValue() {
      return "hello";
    }
  }

  static final class TestGetterSetterPojo {
    private String value;
    private boolean active;

    public String getValue() {
      return value;
    }

    public void setValue(String value) {
      this.value = value;
    }

    public boolean isActive() {
      return active;
    }

    public void setActive(boolean active) {
      this.active = active;
    }
  }

  public static final class TestUnsupportedSetPojo {
    public Set<String> strings;
  }

  public static final class TestDeepConversionPojo {
    public List<Integer> ints;
    public Map<String, Float> floats;
  }

  public static final class TestMissingNoArgConstructorPojo {
    public String value;

    public TestMissingNoArgConstructorPojo(String value) {
      this.value = value;
    }
  }

  public static class TestRefValFieldType {
    public Optional<String> optionalName;
    public int intVal;
    public Instant time;
  }

  public static class ComprehensiveTestNestedType {
    public List<String> nestedListVal;
    public Map<Long, Boolean> nestedMapVal;
    public String customName;

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (!(o instanceof ComprehensiveTestNestedType)) {
        return false;
      }
      ComprehensiveTestNestedType that = (ComprehensiveTestNestedType) o;
      return Objects.equals(nestedListVal, that.nestedListVal)
          && Objects.equals(nestedMapVal, that.nestedMapVal)
          && Objects.equals(customName, that.customName);
    }

    @Override
    public int hashCode() {
      return Objects.hash(nestedListVal, nestedMapVal, customName);
    }
  }

  public static class TestNestedSliceType {
    public String value;

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (!(o instanceof TestNestedSliceType)) {
        return false;
      }
      TestNestedSliceType that = (TestNestedSliceType) o;
      return Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
      return Objects.hashCode(value);
    }
  }

  public static class TestMapVal {
    public String value;

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (!(o instanceof TestMapVal)) {
        return false;
      }
      TestMapVal that = (TestMapVal) o;
      return Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
      return Objects.hashCode(value);
    }
  }

  public static class ComprehensiveTestAllTypes {
    public ComprehensiveTestNestedType nestedVal;
    public ComprehensiveTestNestedType nestedStructVal;
    public boolean boolVal;
    public byte[] bytesVal;
    public Duration durationVal;
    public double doubleVal;
    public float floatVal;
    public int int32Val;
    public long int64Val;
    public String stringVal;
    public Instant timestampVal;
    public long uint32Val;
    public long uint64Val;
    public List<ComprehensiveTestNestedType> listVal;
    public List<ComprehensiveTestNestedType> arrayVal;
    public byte[] bytesArrayVal;
    public Map<String, ComprehensiveTestAllTypes> mapVal;
    public List<TestNestedSliceType> customSliceVal;
    public Map<String, TestMapVal> customMapVal;
    public String customName;

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (!(o instanceof ComprehensiveTestAllTypes)) {
        return false;
      }
      ComprehensiveTestAllTypes that = (ComprehensiveTestAllTypes) o;
      return boolVal == that.boolVal
          && doubleVal == that.doubleVal
          && floatVal == that.floatVal
          && int32Val == that.int32Val
          && int64Val == that.int64Val
          && uint32Val == that.uint32Val
          && uint64Val == that.uint64Val
          && Objects.equals(nestedVal, that.nestedVal)
          && Objects.equals(nestedStructVal, that.nestedStructVal)
          && Arrays.equals(bytesVal, that.bytesVal)
          && Objects.equals(durationVal, that.durationVal)
          && Objects.equals(stringVal, that.stringVal)
          && Objects.equals(timestampVal, that.timestampVal)
          && Objects.equals(listVal, that.listVal)
          && Objects.equals(arrayVal, that.arrayVal)
          && Arrays.equals(bytesArrayVal, that.bytesArrayVal)
          && Objects.equals(mapVal, that.mapVal)
          && Objects.equals(customSliceVal, that.customSliceVal)
          && Objects.equals(customMapVal, that.customMapVal)
          && Objects.equals(customName, that.customName);
    }

    @Override
    public int hashCode() {
      int result =
          Objects.hash(
              nestedVal,
              nestedStructVal,
              boolVal,
              durationVal,
              doubleVal,
              floatVal,
              int32Val,
              int64Val,
              stringVal,
              timestampVal,
              uint32Val,
              uint64Val,
              listVal,
              arrayVal,
              mapVal,
              customSliceVal,
              customMapVal,
              customName);
      result = 31 * result + Arrays.hashCode(bytesVal);
      result = 31 * result + Arrays.hashCode(bytesArrayVal);
      return result;
    }
  }

  public static final class TestPrivateFieldPojo {
    // Intentionally unread to test private fields are not exposed
    @SuppressWarnings("UnusedVariable")
    private String secret;
  }

  public static class TestPrefixLessGetterPojo {
    private String value = "hello";

    public String value() {
      return value;
    }
  }

  public static class TestParentPojo {
    private String parentValue = "parent";
    private String standardValue = "standard";

    public String parentValue() {
      return parentValue;
    }

    public String getStandardValue() {
      return standardValue;
    }
  }

  public static class TestChildPojo extends TestParentPojo {
    private String childValue = "child";

    public String childValue() {
      return childValue;
    }
  }

  // Intentionally violating style guide to test special decapitalization.
  @SuppressWarnings("IdentifierName")
  public static class TestURLPojo {
    public String getURL() {
      return "https://google.com";
    }
  }

  public static class TestDoubleMapKeyPojo {
    public Map<Double, String> map;
  }

  public static class TestWildcardPojo {
    public List<? extends String> values;
  }

  public static class TestArrayPojo {
    public String[] values;
  }

  public static class TestOptionalUrlPojo {
    public Optional<TestURLPojo> optionalUrl;
  }

  public abstract static class TestAbstractPojo {
    public String value;
  }

  public static class TestCircularA {
    public TestCircularB b;
  }

  public static class TestCircularB {
    public TestCircularA a;
  }
}
