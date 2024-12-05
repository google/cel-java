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
public final class CelProtoTypesTest {

  @Test
  public void isOptionalType_true() {
    Type optionalType = CelProtoTypes.createOptionalType(CelProtoTypes.INT64);

    assertThat(CelProtoTypes.isOptionalType(optionalType)).isTrue();
  }

  @Test
  public void isOptionalType_false() {
    Type notOptionalType =
        Type.newBuilder()
            .setAbstractType(AbstractType.newBuilder().setName("notOptional").build())
            .build();

    assertThat(CelProtoTypes.isOptionalType(notOptionalType)).isFalse();
  }

  @Test
  public void createOptionalType() {
    Type optionalType = CelProtoTypes.createOptionalType(CelProtoTypes.INT64);

    assertThat(optionalType.hasAbstractType()).isTrue();
    assertThat(optionalType.getAbstractType().getName()).isEqualTo("optional_type");
    assertThat(optionalType.getAbstractType().getParameterTypesCount()).isEqualTo(1);
    assertThat(optionalType.getAbstractType().getParameterTypes(0)).isEqualTo(CelProtoTypes.INT64);
  }

  private enum TestCases {
    UNSPECIFIED(UnspecifiedType.create(), Type.getDefaultInstance()),
    STRING(SimpleType.STRING, CelProtoTypes.STRING),
    INT(NullableType.create(SimpleType.INT), CelProtoTypes.createWrapper(CelProtoTypes.INT64)),
    UINT(NullableType.create(SimpleType.UINT), CelProtoTypes.createWrapper(CelProtoTypes.UINT64)),
    DOUBLE(
        NullableType.create(SimpleType.DOUBLE), CelProtoTypes.createWrapper(CelProtoTypes.DOUBLE)),
    BOOL(NullableType.create(SimpleType.BOOL), CelProtoTypes.createWrapper(CelProtoTypes.BOOL)),
    BYTES(SimpleType.BYTES, CelProtoTypes.BYTES),
    ANY(SimpleType.ANY, CelProtoTypes.ANY),
    LIST(
        ListType.create(),
        Type.newBuilder().setListType(Type.ListType.getDefaultInstance()).build()),
    DYN(ListType.create(SimpleType.DYN), CelProtoTypes.createList(CelProtoTypes.DYN)),
    ENUM(EnumType.create("CustomEnum", ImmutableMap.of()), CelProtoTypes.INT64),
    STRUCT_TYPE_REF(
        StructTypeReference.create("MyCustomStruct"),
        CelProtoTypes.createMessage("MyCustomStruct")),
    OPAQUE(
        OpaqueType.create("vector", SimpleType.UINT),
        Type.newBuilder()
            .setAbstractType(
                AbstractType.newBuilder().setName("vector").addParameterTypes(CelProtoTypes.UINT64))
            .build()),
    TYPE_PARAM(TypeParamType.create("T"), CelProtoTypes.createTypeParam("T")),
    FUNCTION(
        CelTypes.createFunctionType(
            SimpleType.INT, ImmutableList.of(SimpleType.STRING, SimpleType.UINT)),
        Type.newBuilder()
            .setFunction(
                Type.FunctionType.newBuilder()
                    .setResultType(CelProtoTypes.INT64)
                    .addAllArgTypes(ImmutableList.of(CelProtoTypes.STRING, CelProtoTypes.UINT64)))
            .build()),
    OPTIONAL(
        OptionalType.create(SimpleType.INT), CelProtoTypes.createOptionalType(CelProtoTypes.INT64)),
    TYPE(
        TypeType.create(MapType.create(SimpleType.STRING, SimpleType.STRING)),
        CelProtoTypes.create(CelProtoTypes.createMap(CelProtoTypes.STRING, CelProtoTypes.STRING)));

    private final CelType celType;
    private final Type type;

    TestCases(CelType celType, Type type) {
      this.celType = celType;
      this.type = type;
    }
  }

  @Test
  public void celTypeToType(@TestParameter TestCases testCase) {
    assertThat(CelProtoTypes.celTypeToType(testCase.celType)).isEqualTo(testCase.type);
  }

  @Test
  public void typeToCelType(@TestParameter TestCases testCase) {
    if (testCase.celType instanceof EnumType) {
      // (b/178627883) Strongly typed enum is not supported yet
      return;
    }

    assertThat(CelProtoTypes.typeToCelType(testCase.type)).isEqualTo(testCase.celType);
  }
}
