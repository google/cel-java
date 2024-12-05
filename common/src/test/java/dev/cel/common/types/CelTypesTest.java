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

import dev.cel.expr.Type;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.testing.junit.testparameterinjector.TestParameter;
import com.google.testing.junit.testparameterinjector.TestParameterInjector;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(TestParameterInjector.class)
public final class CelTypesTest {

  @Test
  public void isWellKnownType_true() {
    assertThat(CelTypes.isWellKnownType(CelTypes.ANY_MESSAGE)).isTrue();
  }

  @Test
  public void isWellKnownType_false() {
    assertThat(CelTypes.isWellKnownType("CustomType")).isFalse();
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
    Type type = CelProtoTypes.celTypeToType(testCase.celType);

    assertThat(CelProtoTypes.format(type)).isEqualTo(testCase.formattedString);
  }

  @Test
  public void formatFunction_isInstanceFunction() {
    String formattedString =
        CelTypes.formatFunction(
            SimpleType.INT, ImmutableList.of(SimpleType.STRING, SimpleType.UINT), true, false);

    assertThat(formattedString).isEqualTo("string.(uint) -> int");
  }
}
