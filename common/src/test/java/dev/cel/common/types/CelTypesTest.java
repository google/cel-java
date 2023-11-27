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

package dev.cel.common.types;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.extensions.proto.ProtoTruth.assertThat;

import dev.cel.expr.Type;
import dev.cel.expr.Type.AbstractType;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.testing.junit.testparameterinjector.TestParameter;
import com.google.testing.junit.testparameterinjector.TestParameterInjector;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(TestParameterInjector.class)
public final class CelTypesTest {

  private enum TestCases {
    UNSPECIFIED(UnspecifiedType.create(), Type.getDefaultInstance()),
    STRING(SimpleType.STRING, CelTypes.STRING),
    INT(NullableType.create(SimpleType.INT), CelTypes.createWrapper(CelTypes.INT64)),
    UINT(NullableType.create(SimpleType.UINT), CelTypes.createWrapper(CelTypes.UINT64)),
    DOUBLE(NullableType.create(SimpleType.DOUBLE), CelTypes.createWrapper(CelTypes.DOUBLE)),
    BOOL(NullableType.create(SimpleType.BOOL), CelTypes.createWrapper(CelTypes.BOOL)),
    BYTES(SimpleType.BYTES, CelTypes.BYTES),
    ANY(SimpleType.ANY, CelTypes.ANY),
    LIST(
        ListType.create(),
        Type.newBuilder().setListType(Type.ListType.getDefaultInstance()).build()),
    DYN(ListType.create(SimpleType.DYN), CelTypes.createList(CelTypes.DYN)),
    ENUM(EnumType.create("CustomEnum", ImmutableMap.of()), CelTypes.INT64),
    STRUCT_TYPE_REF(
        StructTypeReference.create("MyCustomStruct"), CelTypes.createMessage("MyCustomStruct")),
    OPAQUE(
        OpaqueType.create("vector", SimpleType.UINT),
        Type.newBuilder()
            .setAbstractType(
                AbstractType.newBuilder().setName("vector").addParameterTypes(CelTypes.UINT64))
            .build()),
    TYPE_PARAM(TypeParamType.create("T"), CelTypes.createTypeParam("T")),
    FUNCTION(
        CelTypes.createFunctionType(
            SimpleType.INT, ImmutableList.of(SimpleType.STRING, SimpleType.UINT)),
        Type.newBuilder()
            .setFunction(
                Type.FunctionType.newBuilder()
                    .setResultType(CelTypes.INT64)
                    .addAllArgTypes(ImmutableList.of(CelTypes.STRING, CelTypes.UINT64)))
            .build()),
    OPTIONAL(OptionalType.create(SimpleType.INT), CelTypes.createOptionalType(CelTypes.INT64)),
    TYPE(
        TypeType.create(MapType.create(SimpleType.STRING, SimpleType.STRING)),
        CelTypes.create(CelTypes.createMap(CelTypes.STRING, CelTypes.STRING)));

    private final CelType celType;
    private final Type type;

    TestCases(CelType celType, Type type) {
      this.celType = celType;
      this.type = type;
    }
  }

  @Test
  public void isWellKnownType_true() {
    assertThat(CelTypes.isWellKnownType(CelTypes.ANY_MESSAGE)).isTrue();
  }

  @Test
  public void isWellKnownType_false() {
    assertThat(CelTypes.isWellKnownType("CustomType")).isFalse();
  }

  @Test
  public void createOptionalType() {
    Type optionalType = CelTypes.createOptionalType(CelTypes.INT64);

    assertThat(optionalType.hasAbstractType()).isTrue();
    assertThat(optionalType.getAbstractType().getName()).isEqualTo("optional_type");
    assertThat(optionalType.getAbstractType().getParameterTypesCount()).isEqualTo(1);
    assertThat(optionalType.getAbstractType().getParameterTypes(0)).isEqualTo(CelTypes.INT64);
  }

  @Test
  public void isOptionalType_true() {
    Type optionalType = CelTypes.createOptionalType(CelTypes.INT64);

    assertThat(CelTypes.isOptionalType(optionalType)).isTrue();
  }

  @Test
  public void isOptionalType_false() {
    Type notOptionalType =
        Type.newBuilder()
            .setAbstractType(AbstractType.newBuilder().setName("notOptional").build())
            .build();

    assertThat(CelTypes.isOptionalType(notOptionalType)).isFalse();
  }

  @Test
  public void celTypeToType(@TestParameter TestCases testCase) {
    assertThat(CelTypes.celTypeToType(testCase.celType)).isEqualTo(testCase.type);
  }

  @Test
  public void typeToCelType(@TestParameter TestCases testCase) {
    if (testCase.celType instanceof EnumType) {
      // (b/178627883) Strongly typed enum is not supported yet
      return;
    }

    assertThat(CelTypes.typeToCelType(testCase.type)).isEqualTo(testCase.celType);
  }

  private enum FormatTestCases {
    UNSPECIFIED(UnspecifiedType.create(), "<unknown type>"),
    STRING(SimpleType.STRING, "string"),
    INT(NullableType.create(SimpleType.INT), "wrapper(int)"),
    UINT(NullableType.create(SimpleType.UINT), "wrapper(uint)"),
    DOUBLE(NullableType.create(SimpleType.DOUBLE), "wrapper(double)"),
    BOOL(NullableType.create(SimpleType.BOOL), "wrapper(bool)"),
    NULL_TYPE(SimpleType.NULL_TYPE, "null"),
    BYTES(SimpleType.BYTES, "bytes"),
    ANY(SimpleType.ANY, "any"),
    LIST_DYN(ListType.create(SimpleType.DYN), "list(dyn)"),
    ENUM(EnumType.create("CustomEnum", ImmutableMap.of()), "int"),
    STRUCT_TYPE_REF(StructTypeReference.create("MyCustomStruct"), "MyCustomStruct"),
    OPAQUE(OpaqueType.create("vector", SimpleType.UINT), "vector(uint)"),
    TYPE_PARAM(TypeParamType.create("T"), "T"),
    FUNCTION(
        CelTypes.createFunctionType(
            SimpleType.INT, ImmutableList.of(SimpleType.STRING, SimpleType.UINT)),
        "(string, uint) -> int"),
    OPTIONAL(OptionalType.create(SimpleType.INT), "optional_type(int)"),
    MAP(MapType.create(SimpleType.INT, SimpleType.STRING), "map(int, string)"),
    TYPE(
        TypeType.create(MapType.create(SimpleType.STRING, SimpleType.STRING)),
        "type(map(string, string))"),
    TIMESTAMP(SimpleType.TIMESTAMP, "google.protobuf.Timestamp"),
    DURATION(SimpleType.DURATION, "google.protobuf.Duration"),
    ERROR(SimpleType.ERROR, "*error*");

    private final CelType celType;
    private final String formattedString;

    FormatTestCases(CelType celType, String formattedString) {
      this.celType = celType;
      this.formattedString = formattedString;
    }
  }

  @Test
  public void format_withCelType(@TestParameter FormatTestCases testCase) {
    assertThat(CelTypes.format(testCase.celType)).isEqualTo(testCase.formattedString);
  }

  @Test
  public void format_withType(@TestParameter FormatTestCases testCase) {
    Type type = CelTypes.celTypeToType(testCase.celType);

    assertThat(CelTypes.format(type)).isEqualTo(testCase.formattedString);
  }

  @Test
  public void formatFunction_isInstanceFunction() {
    String formattedString =
        CelTypes.formatFunction(
            SimpleType.INT, ImmutableList.of(SimpleType.STRING, SimpleType.UINT), true, false);

    assertThat(formattedString).isEqualTo("string.(uint) -> int");
  }
}
