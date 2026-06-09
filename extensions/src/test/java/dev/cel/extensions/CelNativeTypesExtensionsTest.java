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
import dev.cel.common.exceptions.CelInvalidArgumentException;
import dev.cel.common.types.CelType;
import dev.cel.common.types.ListType;
import dev.cel.common.types.MapType;
import dev.cel.common.types.OptionalType;
import dev.cel.common.types.SimpleType;
import dev.cel.common.types.StructType;
import dev.cel.common.types.StructTypeReference;
import dev.cel.common.values.CelByteString;
import dev.cel.common.values.CelValueProvider;
import dev.cel.common.values.StructValue;
import dev.cel.compiler.CelCompiler;
import dev.cel.compiler.CelCompilerFactory;
import dev.cel.expr.conformance.proto3.TestAllTypes;
import dev.cel.parser.CelStandardMacro;
import dev.cel.runtime.CelEvaluationException;
import dev.cel.runtime.CelRuntime;
import dev.cel.runtime.CelRuntimeFactory;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
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
          TestMapVal.class,
          TestCustomCollectionPojo.class,
          TestNestedGenericsPojo.class,
          TestNestedSimplePojo.class,
          TestGetterFieldTypeMismatchPojo.class,
          TestAbstractPojo.class,
          TestURLPojo.class,
          PojoWithEnum.class,
          TestArrayPojo.class);

  private static final Cel CEL =
      CelFactory.plannerCelBuilder()
          .setContainer(CelContainer.ofName("dev.cel.extensions.CelNativeTypesExtensionsTest"))
          .addCompilerLibraries(NATIVE_TYPE_EXTENSIONS)
          .addRuntimeLibraries(NATIVE_TYPE_EXTENSIONS)
          .setStandardMacros(CelStandardMacro.STANDARD_MACROS)
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
  public void nativeTypes_transitiveDiscoveryThroughMap() throws Exception {
    PojoWithCustomMap pojo = new PojoWithCustomMap();
    HashMap<String, TestNestedType> map = new HashMap<>();
    TestNestedType nested = new TestNestedType();
    nested.value = "hello";
    map.put("key", nested);
    pojo.mapVal = map;

    CelNativeTypesExtensions extensions =
        CelNativeTypesExtensions.nativeTypes(PojoWithCustomMap.class);
    Cel cel =
        CelFactory.plannerCelBuilder()
            .setContainer(CelContainer.ofName("dev.cel.extensions.CelNativeTypesExtensionsTest"))
            .addVar("pojo", StructTypeReference.create(PojoWithCustomMap.class.getCanonicalName()))
            .addCompilerLibraries(extensions)
            .addRuntimeLibraries(extensions)
            .build();

    CelAbstractSyntaxTree ast = cel.compile("pojo.mapVal['key'].value == 'hello'").getAst();
    Object result = cel.createProgram(ast).eval(ImmutableMap.of("pojo", pojo));

    assertThat(result).isEqualTo(true);
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
    TestPrivateConstructorPojo result =
        (TestPrivateConstructorPojo) eval("TestPrivateConstructorPojo{value:" + " 'hello'}");

    assertThat(result.value).isEqualTo("hello");
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
    TestDeepConversionPojo pojo =
        (TestDeepConversionPojo)
            eval("TestDeepConversionPojo{ints: [1, 2], floats: {'a': 1.0, 'b': 2.0}}");
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
  public void nativeTypes_arrayType_construction() throws Exception {
    String expr =
        "TestArrayPojo{"
            + "  strings: ['a', 'b'],"
            + "  ints: [1, 2],"
            + "  nesteds: [TestNestedType{value: 'nested'}],"
            + "  matrix: [[1, 2], [3, 4]],"
            + "  nestedMatrix: [[TestNestedType{value: 'm1'}], [TestNestedType{value: 'm2'}]],"
            + "  byteArrays: [b'foo', b'bar']"
            + "}";

    TestArrayPojo pojo = (TestArrayPojo) eval(expr);

    assertThat(pojo.strings).isEqualTo(new String[] {"a", "b"});
    assertThat(pojo.ints).isEqualTo(new int[] {1, 2});
    assertThat(pojo.nesteds).hasLength(1);
    assertThat(pojo.nesteds[0].value).isEqualTo("nested");
    assertThat(pojo.matrix).hasLength(2);
    assertThat(pojo.matrix[0]).isEqualTo(new int[] {1, 2});
    assertThat(pojo.matrix[1]).isEqualTo(new int[] {3, 4});
    assertThat(pojo.nestedMatrix).hasLength(2);
    assertThat(pojo.nestedMatrix[0][0].value).isEqualTo("m1");
    assertThat(pojo.nestedMatrix[1][0].value).isEqualTo("m2");
    assertThat(pojo.byteArrays).hasLength(2);
    assertThat(pojo.byteArrays[0]).isEqualTo("foo".getBytes(UTF_8));
    assertThat(pojo.byteArrays[1]).isEqualTo("bar".getBytes(UTF_8));
  }

  @Test
  public void nativeTypes_arrayType_selection() throws Exception {
    CelNativeTypesExtensions extensions = CelExtensions.nativeTypes(TestArrayPojo.class);
    Cel cel =
        CelFactory.plannerCelBuilder()
            .setContainer(CelContainer.ofName("dev.cel.extensions.CelNativeTypesExtensionsTest"))
            .addCompilerLibraries(extensions)
            .addRuntimeLibraries(extensions)
            .addVar("pojo", StructTypeReference.create(TestArrayPojo.class.getCanonicalName()))
            .build();
    String expr =
        "pojo.strings[1] == 'b'"
            + "  && pojo.ints[0] == 1"
            + "  && pojo.nesteds[0].value == 'nested'"
            + "  && pojo.matrix[1][0] == 3"
            + "  && pojo.nestedMatrix[1][0].value == 'm2'"
            + "  && pojo.byteArrays[1] == b'bar'";
    CelAbstractSyntaxTree ast = cel.compile(expr).getAst();
    CelRuntime.Program program = cel.createProgram(ast);

    TestArrayPojo input = new TestArrayPojo();
    input.strings = new String[] {"a", "b"};
    input.ints = new int[] {1, 2};
    TestNestedType nested = new TestNestedType();
    nested.value = "nested";
    input.nesteds = new TestNestedType[] {nested};
    input.matrix = new int[][] {{1, 2}, {3, 4}};
    TestNestedType m1 = new TestNestedType();
    m1.value = "m1";
    TestNestedType m2 = new TestNestedType();
    m2.value = "m2";
    input.nestedMatrix = new TestNestedType[][] {{m1}, {m2}};
    input.byteArrays = new byte[][] {"foo".getBytes(UTF_8), "bar".getBytes(UTF_8)};

    assertThat(program.eval(ImmutableMap.of("pojo", input))).isEqualTo(true);
  }

  @Test
  public void nativeTypes_arrayWithNullElement_throws() throws Exception {
    CelNativeTypesExtensions extensions = CelExtensions.nativeTypes(TestArrayPojo.class);
    Cel cel =
        CelFactory.plannerCelBuilder()
            .setContainer(CelContainer.ofName("dev.cel.extensions.CelNativeTypesExtensionsTest"))
            .addCompilerLibraries(extensions)
            .addRuntimeLibraries(extensions)
            .addVar("pojo", StructTypeReference.create(TestArrayPojo.class.getCanonicalName()))
            .build();
    CelAbstractSyntaxTree ast = cel.compile("pojo.strings").getAst();
    CelRuntime.Program program = cel.createProgram(ast);

    TestArrayPojo input = new TestArrayPojo();
    input.strings = new String[] {"a", null, "c"};

    CelEvaluationException e =
        assertThrows(
            CelEvaluationException.class, () -> program.eval(ImmutableMap.of("pojo", input)));
    assertThat(e).hasCauseThat().isInstanceOf(CelInvalidArgumentException.class);
    assertThat(e).hasCauseThat().hasMessageThat().contains("Element at index 1 is null.");
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
    CelAbstractSyntaxTree ast = CEL.parse("TestAbstractPojo{}").getAst();
    CelRuntime.Program program = CEL.createProgram(ast);

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
    Object result = eval("dev.cel.extensions.CelNativeTypesExtensionsTest.TestURLPojo{}.URL");

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
    CelAbstractSyntaxTree valueAst =
        celCompiler
            .compile(
                "dev.cel.extensions.CelNativeTypesExtensionsTest.TestPrefixLessGetterPojo{}.value")
            .getAst();
    CelAbstractSyntaxTree nameAst =
        celCompiler
            .compile(
                "dev.cel.extensions.CelNativeTypesExtensionsTest.TestPrefixLessGetterPojo{}.name")
            .getAst();
    CelRuntime.Program valueProgram = celRuntime.createProgram(valueAst);
    CelRuntime.Program nameProgram = celRuntime.createProgram(nameAst);

    Object valueResult = valueProgram.eval();
    Object nameResult = nameProgram.eval();

    assertThat(valueResult).isEqualTo("hello");
    assertThat(nameResult).isEqualTo("my_name");
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

    TestAllTypesPublicFieldsPojo pojo = (TestAllTypesPublicFieldsPojo) program.eval();
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
  public void nativeTypes_mapJavaTypeToCelType_customCollectionSubclasses() throws Exception {
    CelNativeTypesExtensions extensions = CelExtensions.nativeTypes(TestCustomCollectionPojo.class);
    CelNativeTypesExtensions.NativeTypeRegistry registry = extensions.getRegistry();

    Optional<CelType> type = registry.findType(TestCustomCollectionPojo.class.getCanonicalName());
    StructType structType = (StructType) type.get();

    assertThat(structType.findField("customList").map(StructType.Field::type))
        .hasValue(ListType.create(SimpleType.STRING));
    assertThat(structType.findField("customMap").map(StructType.Field::type))
        .hasValue(MapType.create(SimpleType.STRING, SimpleType.INT));
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

  @Test
  public void nativeTypes_nullSafeTraversal() throws Exception {
    Cel cel =
        CelFactory.plannerCelBuilder()
            .setContainer(CelContainer.ofName("dev.cel.extensions.CelNativeTypesExtensionsTest"))
            .addCompilerLibraries(NATIVE_TYPE_EXTENSIONS)
            .addRuntimeLibraries(NATIVE_TYPE_EXTENSIONS)
            .addVar(
                "pojo",
                StructTypeReference.create(TestAllTypesPublicFieldsPojo.class.getCanonicalName()))
            .build();

    TestAllTypesPublicFieldsPojo pojo = new TestAllTypesPublicFieldsPojo();
    ImmutableMap<String, Object> vars = ImmutableMap.of("pojo", pojo);

    assertThat(cel.createProgram(cel.compile("pojo.stringVal").getAst()).eval(vars)).isEqualTo("");
    assertThat(cel.createProgram(cel.compile("pojo.int64Val").getAst()).eval(vars)).isEqualTo(0L);
    assertThat(cel.createProgram(cel.compile("pojo.nestedVal.value").getAst()).eval(vars))
        .isEqualTo("");
    assertThat(cel.createProgram(cel.compile("size(pojo.arrayVal) == 0").getAst()).eval(vars))
        .isEqualTo(true);
    CelAbstractSyntaxTree abstractPojoAst = cel.compile("pojo.abstractPojo.value").getAst();
    CelRuntime.Program abstractPojoProgram = cel.createProgram(abstractPojoAst);
    CelEvaluationException e =
        assertThrows(CelEvaluationException.class, () -> abstractPojoProgram.eval(vars));
    assertThat(e).hasMessageThat().contains("Failed to instantiate default instance");
  }

  @Test
  public void nativeTypes_presenceTest() throws Exception {
    Cel cel =
        CelFactory.plannerCelBuilder()
            .setContainer(CelContainer.ofName("dev.cel.extensions.CelNativeTypesExtensionsTest"))
            .addCompilerLibraries(NATIVE_TYPE_EXTENSIONS)
            .addRuntimeLibraries(NATIVE_TYPE_EXTENSIONS)
            .setStandardMacros(CelStandardMacro.STANDARD_MACROS)
            .addVar(
                "pojo",
                StructTypeReference.create(TestAllTypesPublicFieldsPojo.class.getCanonicalName()))
            .build();

    TestAllTypesPublicFieldsPojo pojo = new TestAllTypesPublicFieldsPojo();
    ImmutableMap<String, Object> nullVars = ImmutableMap.of("pojo", pojo);

    TestAllTypesPublicFieldsPojo pojoWithValues = new TestAllTypesPublicFieldsPojo();
    pojoWithValues.stringVal = "hello";
    ImmutableMap<String, Object> valueVars = ImmutableMap.of("pojo", pojoWithValues);

    boolean hasPopulatedString =
        (boolean) cel.createProgram(cel.compile("has(pojo.stringVal)").getAst()).eval(valueVars);
    assertThat(hasPopulatedString).isTrue();

    boolean hasNullString =
        (boolean) cel.createProgram(cel.compile("has(pojo.stringVal)").getAst()).eval(nullVars);
    assertThat(hasNullString).isFalse();

    assertThrows(
        CelValidationException.class, () -> cel.compile("has(pojo.nonExistentField)").getAst());
  }

  @Test
  public void nativeTypes_zeroValue_collections_comprehensions() throws Exception {
    assertThat(eval("TestAllTypesPublicFieldsPojo{}.listVal.filter(x, true) == []"))
        .isEqualTo(true);
    assertThat(eval("TestAllTypesPublicFieldsPojo{}.listVal.map(x, x + 'foo') == []"))
        .isEqualTo(true);
    assertThat(eval("TestAllTypesPublicFieldsPojo{}.listVal.exists(x, true)")).isEqualTo(false);
    assertThat(eval("TestAllTypesPublicFieldsPojo{}.listVal.all(x, true)")).isEqualTo(true);
    assertThat(eval("TestAllTypesPublicFieldsPojo{}.mapVal.exists(k, true)")).isEqualTo(false);
    assertThat(eval("TestAllTypesPublicFieldsPojo{}.mapVal.all(k, true)")).isEqualTo(true);
  }

  @Test
  public void nativeTypes_customStructValue_optionalOfNonZeroValue() throws Exception {
    CelNativeTypesExtensions extensions =
        CelExtensions.nativeTypes(TestCustomStructValuePojo.class);
    Cel cel =
        CelFactory.plannerCelBuilder()
            .setContainer(CelContainer.ofName("dev.cel.extensions.CelNativeTypesExtensionsTest"))
            .addCompilerLibraries(extensions, CelExtensions.optional())
            .addRuntimeLibraries(extensions, CelExtensions.optional())
            .addVar(
                "pojo",
                StructTypeReference.create(TestCustomStructValuePojo.class.getCanonicalName()))
            .build();

    TestCustomStructValuePojo emptyPojo =
        new TestCustomStructValuePojo(ImmutableMap.of("value", ""));
    ImmutableMap<String, Object> emptyVars = ImmutableMap.of("pojo", emptyPojo);
    boolean isEmptyNone =
        (boolean)
            cel.createProgram(cel.compile("!optional.ofNonZeroValue(pojo).hasValue()").getAst())
                .eval(emptyVars);
    assertThat(isEmptyNone).isTrue();

    TestCustomStructValuePojo populatedPojo =
        new TestCustomStructValuePojo(ImmutableMap.of("value", "hello"));
    ImmutableMap<String, Object> populatedVars = ImmutableMap.of("pojo", populatedPojo);
    boolean isPopulatedPresent =
        (boolean)
            cel.createProgram(cel.compile("optional.ofNonZeroValue(pojo).hasValue()").getAst())
                .eval(populatedVars);
    assertThat(isPopulatedPresent).isTrue();
  }

  @Test
  public void nativeTypes_staticMembers_skipped() throws Exception {
    ImmutableSet<String> properties =
        CelNativeTypesExtensions.NativeTypeScanner.getProperties(TestStaticMembersPojo.class);

    assertThat(properties).contains("instanceField");
    assertThat(properties).doesNotContain("STATIC_FIELD");
    assertThat(properties).doesNotContain("staticGetter");
    assertThat(properties).doesNotContain("staticProperty");
  }

  @Test
  public void nativeTypes_deeplyNestedGenerics_discovered() throws Exception {
    CelNativeTypesExtensions extensions = CelExtensions.nativeTypes(TestNestedGenericsPojo.class);
    Cel cel =
        CelFactory.plannerCelBuilder()
            .setContainer(CelContainer.ofName("dev.cel.extensions.CelNativeTypesExtensionsTest"))
            .addCompilerLibraries(extensions)
            .addRuntimeLibraries(extensions)
            .addVar(
                "pojo", StructTypeReference.create(TestNestedGenericsPojo.class.getCanonicalName()))
            .build();

    TestNestedSimplePojo simplePojo = new TestNestedSimplePojo();
    TestNestedGenericsPojo pojo = new TestNestedGenericsPojo();
    pojo.nestedList = ImmutableList.of(ImmutableList.of(simplePojo));

    boolean result =
        (boolean)
            cel.createProgram(cel.compile("pojo.nestedList[0][0].value == 'nested'").getAst())
                .eval(ImmutableMap.of("pojo", pojo));

    assertThat(result).isTrue();
  }

  @Test
  public void nativeTypes_concreteCollectionInstantiation_success() throws Exception {
    TestCustomCollectionPojo result =
        (TestCustomCollectionPojo)
            eval("TestCustomCollectionPojo{customList: ['a', 'b'], customMap: {'key': 1}}");

    assertThat(result).isNotNull();
    assertThat(result.customList).containsExactly("a", "b");
    assertThat(result.customMap).containsEntry("key", 1L);
  }

  @Test
  public void nativeTypes_getterFieldTypeMismatch_readOnly() throws Exception {
    CelAbstractSyntaxTree ast =
        CEL.compile("TestGetterFieldTypeMismatchPojo{mismatchField: 'hello'}").getAst();

    CelRuntime.Program program = CEL.createProgram(ast);
    CelEvaluationException exception =
        assertThrows(CelEvaluationException.class, () -> program.eval(ImmutableMap.of()));

    assertThat(exception.getMessage()).contains("Failed to create instance");
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
    public String[] arrayVal;
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
    public TestAbstractPojo abstractPojo;

    public String getInvalidParam(String param) {
      return "invalid";
    }

    public String isInvalidString() {
      return "invalid";
    }
  }

  public static class PojoWithCustomMap {
    public Map<String, TestNestedType> mapVal;
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
    private String name = "my_name";

    public String value() {
      return value;
    }

    public String name() {
      return name;
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
    public String[] strings;
    public int[] ints;
    public TestNestedType[] nesteds;
    public int[][] matrix;
    public TestNestedType[][] nestedMatrix;
    public byte[][] byteArrays;
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

  public static class CustomListImplementation<C, E> extends ArrayList<E> {}

  public static class CustomMapImplementation<C, V, K> extends HashMap<K, V> {}

  public static class TestCustomCollectionPojo {
    public CustomListImplementation<Integer, String> customList;
    public CustomMapImplementation<Boolean, Long, String> customMap;
  }

  @SuppressWarnings("Immutable")
  static final class TestCustomStructValuePojo extends StructValue<String, Object> {
    private final ImmutableMap<String, Object> fields;

    public TestCustomStructValuePojo(ImmutableMap<String, Object> fields) {
      this.fields = fields;
    }

    @Override
    public Object value() {
      return this;
    }

    @Override
    public boolean isZeroValue() {
      for (Object val : fields.values()) {
        if (val != null && !val.equals("") && !val.equals(0L)) {
          return false;
        }
      }
      return true;
    }

    @Override
    public CelType celType() {
      return StructTypeReference.create(TestCustomStructValuePojo.class.getCanonicalName());
    }

    @Override
    public Optional<Object> find(String field) {
      return Optional.ofNullable(fields.get(field));
    }

    @Override
    public Object select(String field) {
      Object val = fields.get(field);
      if (val == null) {
        throw new NoSuchElementException("Field not found: " + field);
      }
      return val;
    }
  }

  public static class TestStaticMembersPojo {
    public static final String STATIC_FIELD = "static_value";

    public static String getStaticGetter() {
      return "static_getter_value";
    }

    public static String staticProperty() {
      return "static_property_value";
    }

    public String instanceField = "instance_value";
  }

  public static class TestNestedGenericsPojo {
    public List<List<TestNestedSimplePojo>> nestedList;
    public Map<String, List<TestNestedSimplePojo>> nestedMap;
  }

  public static class TestNestedSimplePojo {
    public String value = "nested";
  }

  public static class TestGetterFieldTypeMismatchPojo {
    public int mismatchField = 10;

    public String getMismatchField() {
      return "mismatch";
    }
  }

  public enum TestEnum {
    FOO,
    BAR;
  }

  public static class PojoWithEnum {
    private TestEnum enumVal = TestEnum.FOO;

    public TestEnum getEnumVal() {
      return enumVal;
    }

    public void setEnumVal(TestEnum val) {
      this.enumVal = val;
    }
  }

  @Test
  public void nativeTypes_enumSafelyIgnored() throws Exception {
    assertThat(eval("PojoWithEnum{}.enumVal")).isNotNull();
  }

}
