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
import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.errorprone.annotations.Immutable;
import java.util.Optional;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public final class CelTypesTest {

  @AutoValue
  @Immutable
  abstract static class TestCase {
    abstract CelType celType();

    abstract Type type();

    @Override
    public final String toString() {
      return String.format("CelType: %s => Type: %s", celType(), type());
    }
  }

  static TestCase testCase(CelType celType, Type type) {
    return new AutoValue_CelTypesTest_TestCase(celType, type);
  }

  private static final ImmutableList<TestCase> TEST_CASES =
      ImmutableList.of(
          testCase(SimpleType.STRING, CelTypes.STRING),
          testCase(NullableType.create(SimpleType.INT), CelTypes.createWrapper(CelTypes.INT64)),
          testCase(ListType.create(SimpleType.DYN), CelTypes.createList(CelTypes.DYN)),
          testCase(EnumType.create("CustomEnum", ImmutableMap.of()), CelTypes.INT64),
          testCase(
              StructType.create("MyCustomStruct", ImmutableSet.of(), (name) -> Optional.empty()),
              CelTypes.createMessage("MyCustomStruct")),
          testCase(
              OpaqueType.create("vector", SimpleType.UINT),
              Type.newBuilder()
                  .setAbstractType(
                      AbstractType.newBuilder()
                          .setName("vector")
                          .addParameterTypes(CelTypes.UINT64))
                  .build()),
          testCase(TypeParamType.create("T"), CelTypes.createTypeParam("T")),
          testCase(
              TypeType.create(MapType.create(SimpleType.STRING, SimpleType.STRING)),
              CelTypes.create(CelTypes.createMap(CelTypes.STRING, CelTypes.STRING))));

  @Parameters(name = "{index}: {0}")
  public static ImmutableList<TestCase> data() {
    return TEST_CASES;
  }

  @Parameter public TestCase testCase;

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
    assertThat(optionalType.getAbstractType().getName()).isEqualTo("optional");
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
  public void celTypeToType() {
    assertThat(CelTypes.celTypeToType(testCase.celType())).isEqualTo(testCase.type());
  }
}
