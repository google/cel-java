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

package dev.cel.checker;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import dev.cel.checker.TypeInferencer.UnificationResult;
import dev.cel.common.types.CelType;
import dev.cel.common.types.JsonType;
import dev.cel.common.types.ListType;
import dev.cel.common.types.MapType;
import dev.cel.common.types.NullableType;
import dev.cel.common.types.OpaqueType;
import dev.cel.common.types.SimpleType;
import dev.cel.common.types.StructType;
import dev.cel.common.types.TypeParamType;
import dev.cel.common.types.TypeType;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class TypeInferencerTest {

  private static final ImmutableList<CelType> UNION_TYPES =
      ImmutableList.of(
          NullableType.create(SimpleType.BOOL),
          NullableType.create(SimpleType.BYTES),
          NullableType.create(SimpleType.DOUBLE),
          NullableType.create(SimpleType.INT),
          NullableType.create(SimpleType.STRING),
          NullableType.create(SimpleType.UINT),
          JsonType.JSON,
          SimpleType.DYN);

  private TypeInferencer inferencer;
  private TypeVarGenerator typeVarGenerator;

  @Before
  public void setUp() {
    typeVarGenerator = new TypeVarGenerator();
    inferencer = new TypeInferencer(UNION_TYPES);
  }

  @Test
  public void unify_success_parameterizedMap() {
    MapType mapParam = MapType.create(TypeParamType.create("K"), TypeParamType.create("V"));
    MapType mapInst = MapType.create(SimpleType.STRING, ListType.create(TypeParamType.create("V")));
    MapType mapParamFresh = (MapType) mapParam.withFreshTypeParamVariables(typeVarGenerator);
    Optional<UnificationResult> result = inferencer.unify(mapInst, mapParamFresh);
    assertThat(result).isPresent();
    assertThat(result.get().substitutions())
        .containsExactly(
            mapParamFresh.keyType().name(),
            SimpleType.STRING,
            mapParamFresh.valueType().name(),
            ListType.create(TypeParamType.create("V")));
    inferencer.recordSubstitutions(result.get().substitutions());
    assertThat(inferencer.specialize(mapParamFresh)).isEqualTo(mapInst);
  }

  @Test
  public void unify_success_parameterizedMapReversedArgs() {
    MapType mapParam = MapType.create(TypeParamType.create("K"), TypeParamType.create("V"));
    MapType mapInst = MapType.create(SimpleType.STRING, ListType.create(TypeParamType.create("V")));
    MapType mapParamFresh = (MapType) mapParam.withFreshTypeParamVariables(typeVarGenerator);
    Optional<UnificationResult> result = inferencer.unify(mapParamFresh, mapInst);
    assertThat(result).isPresent();
    assertThat(result.get().unifiedType()).hasValue(mapInst);
    assertThat(result.get().substitutions())
        .containsExactly(
            mapParamFresh.keyType().name(),
            SimpleType.STRING,
            mapParamFresh.valueType().name(),
            ListType.create(TypeParamType.create("V")));
    inferencer.recordSubstitutions(result.get().substitutions());
    assertThat(inferencer.specialize(mapParamFresh)).isEqualTo(mapInst);
  }

  @Test
  public void unify_success_jsonEqualsDoubleOneByOne() {
    TypeParamType equalsArg = TypeParamType.create("T");
    CelType equalsArgFresh = equalsArg.withFreshTypeParamVariables(typeVarGenerator);
    Optional<UnificationResult> firstArg = inferencer.unify(JsonType.JSON, equalsArgFresh);
    assertThat(firstArg).isPresent();
    assertThat(firstArg.get().unifiedType()).hasValue(JsonType.JSON);
    inferencer.recordSubstitutions(firstArg.get().substitutions());
    Optional<UnificationResult> secondArg = inferencer.unify(SimpleType.DOUBLE, equalsArgFresh);
    assertThat(secondArg).isPresent();
    assertThat(secondArg.get().unifiedType()).hasValue(JsonType.JSON);
    inferencer.recordSubstitutions(secondArg.get().substitutions());
    assertThat(inferencer.specialize(equalsArgFresh)).isEqualTo(JsonType.JSON);
  }

  @Test
  public void unify_success_jsonEqualsDoubleOneByOneDifferentInferencer() {
    TypeParamType equalsArg = TypeParamType.create("T");
    CelType equalsArgFresh = equalsArg.withFreshTypeParamVariables(typeVarGenerator);
    Optional<UnificationResult> firstArg = inferencer.unify(JsonType.JSON, equalsArgFresh);
    assertThat(firstArg).isPresent();
    assertThat(firstArg.get().unifiedType()).hasValue(JsonType.JSON);
    TypeInferencer inferencer2 = new TypeInferencer(UNION_TYPES, firstArg.get().substitutions());
    Optional<UnificationResult> secondArg = inferencer2.unify(SimpleType.DOUBLE, equalsArgFresh);
    assertThat(secondArg).isPresent();
    assertThat(secondArg.get().unifiedType()).hasValue(JsonType.JSON);
    inferencer2.recordSubstitutions(secondArg.get().substitutions());
    assertThat(inferencer2.specialize(equalsArgFresh)).isEqualTo(JsonType.JSON);
  }

  @Test
  public void unify_success_jsonToDouble() {
    Optional<UnificationResult> result = inferencer.unify(JsonType.JSON, SimpleType.DOUBLE);
    assertThat(result).isPresent();
    assertThat(result.get().unifiedType()).hasValue(JsonType.JSON);
  }

  @Test
  public void unify_success_doubleToJson() {
    Optional<UnificationResult> result = inferencer.unify(SimpleType.DOUBLE, JsonType.JSON);
    assertThat(result).isPresent();
    assertThat(result.get().unifiedType()).hasValue(JsonType.JSON);
  }

  @Test
  public void unify_false_nestedTypeParamReference() {
    TypeParamType typeParamArg = TypeParamType.create("T");
    ListType listType = ListType.create(typeParamArg);
    Optional<UnificationResult> result = inferencer.unify(typeParamArg, listType);
    assertThat(result).isEmpty();
  }

  @Test
  public void unify_success_dynOrErrorYieldsError() {
    Optional<UnificationResult> result = inferencer.unify(SimpleType.DYN, SimpleType.ERROR);
    assertThat(result).isPresent();
    assertThat(result.get().unifiedType()).hasValue(SimpleType.ERROR);
  }

  @Test
  public void unify_success_doubleNullToNullableDoubleWithTypeParam() {
    CelType outputType = TypeParamType.create("O").withFreshTypeParamVariables(typeVarGenerator);
    Optional<UnificationResult> result =
        inferencer.unify(ImmutableList.of(SimpleType.DOUBLE, SimpleType.NULL_TYPE), outputType);
    assertThat(result).isPresent();
    assertThat(result.get().unifiedType()).hasValue(NullableType.create(SimpleType.DOUBLE));
  }

  @Test
  public void unify_success_doubleNullToNullableDoubleWithConcreteType() {
    CelType outputType = NullableType.create(SimpleType.DOUBLE);
    Optional<UnificationResult> result =
        inferencer.unify(ImmutableList.of(SimpleType.DOUBLE, SimpleType.NULL_TYPE), outputType);
    assertThat(result).isPresent();
    assertThat(result.get().unifiedType()).hasValue(outputType);
  }

  @Test
  public void unify_success_doubleNullStringToJson() {
    CelType outputType = TypeParamType.create("O").withFreshTypeParamVariables(typeVarGenerator);
    Optional<UnificationResult> result =
        inferencer.unify(
            ImmutableList.of(SimpleType.DOUBLE, SimpleType.NULL_TYPE, SimpleType.STRING),
            outputType);
    assertThat(result).isPresent();
    assertThat(result.get().unifiedType()).hasValue(JsonType.JSON);
  }

  @Test
  public void unify_success_doubleNullStringToDyn() {
    TypeInferencer inferencer = new TypeInferencer(ImmutableList.of(SimpleType.DYN));
    CelType outputType = TypeParamType.create("O").withFreshTypeParamVariables(typeVarGenerator);
    Optional<UnificationResult> result =
        inferencer.unify(
            ImmutableList.of(SimpleType.DOUBLE, SimpleType.NULL_TYPE, SimpleType.STRING),
            outputType);
    assertThat(result).isPresent();
    assertThat(result.get().unifiedType()).hasValue(SimpleType.DYN);
  }

  @Test
  public void unify_false_doubleNullStringNoJsonNoTopType() {
    TypeInferencer inferencer = new TypeInferencer(ImmutableList.of());
    CelType outputType = TypeParamType.create("O").withFreshTypeParamVariables(typeVarGenerator);
    Optional<UnificationResult> result =
        inferencer.unify(
            ImmutableList.of(SimpleType.DOUBLE, SimpleType.NULL_TYPE, SimpleType.STRING),
            outputType);
    assertThat(result).isEmpty();
  }

  @Test
  public void unify_success_abstractTypeListToJsonParam() {
    OpaqueType setListDouble = OpaqueType.create("set", ListType.create(SimpleType.DOUBLE));
    OpaqueType setString = OpaqueType.create("set", SimpleType.STRING);
    CelType outputType = TypeParamType.create("O").withFreshTypeParamVariables(typeVarGenerator);
    Optional<UnificationResult> result =
        inferencer.unify(ImmutableList.of(setListDouble, setString), outputType);
    assertThat(result).isPresent();
    assertThat(result.get().unifiedType()).hasValue(OpaqueType.create("set", JsonType.JSON));
  }

  @Test
  public void unify_success_abstractTypeMapToMapJsonParam() {
    OpaqueType setListDouble =
        OpaqueType.create("set", MapType.create(SimpleType.STRING, SimpleType.DOUBLE));
    OpaqueType setString =
        OpaqueType.create("set", MapType.create(SimpleType.STRING, SimpleType.STRING));
    CelType outputType = TypeParamType.create("O").withFreshTypeParamVariables(typeVarGenerator);
    Optional<UnificationResult> result =
        inferencer.unify(ImmutableList.of(setListDouble, setString), outputType);
    assertThat(result).isPresent();
    assertThat(result.get().unifiedType())
        .hasValue(OpaqueType.create("set", MapType.create(SimpleType.STRING, JsonType.JSON)));
  }

  @Test
  public void unify_success_abstractTypeMapToJsonParam() {
    OpaqueType setListDouble =
        OpaqueType.create("set", MapType.create(SimpleType.STRING, SimpleType.DOUBLE));
    OpaqueType setString = OpaqueType.create("set", SimpleType.STRING);
    CelType outputType = TypeParamType.create("0").withFreshTypeParamVariables(typeVarGenerator);
    Optional<UnificationResult> result =
        inferencer.unify(ImmutableList.of(setListDouble, setString), outputType);
    assertThat(result).isPresent();
    assertThat(result.get().unifiedType()).hasValue(OpaqueType.create("set", JsonType.JSON));
  }

  @Test
  public void unify_success_opaqueTypeMapToDynParam() {
    TypeInferencer inferencer = new TypeInferencer(ImmutableList.of(SimpleType.DYN));
    OpaqueType setListDouble =
        OpaqueType.create("set", MapType.create(SimpleType.STRING, SimpleType.DOUBLE));
    OpaqueType setString = OpaqueType.create("set", SimpleType.STRING);
    CelType outputType = TypeParamType.create("O").withFreshTypeParamVariables(typeVarGenerator);
    // The current ExprChecker would normally return DYN as the result for the overall type,
    // but this type inferencer is able to be more fine-grained about where it allows DYN
    // to enter into the type resolution.
    Optional<UnificationResult> result =
        inferencer.unify(ImmutableList.of(setListDouble, setString), outputType);
    assertThat(result).isPresent();
    assertThat(result.get().unifiedType()).hasValue(OpaqueType.create("set", SimpleType.DYN));
  }

  @Test
  public void unify_success_jsonIntSetsToDynSet() {
    OpaqueType setJson = OpaqueType.create("set", JsonType.JSON);
    OpaqueType setInt = OpaqueType.create("set", SimpleType.INT);
    CelType outputType = TypeParamType.create("O").withFreshTypeParamVariables(typeVarGenerator);
    Optional<UnificationResult> result =
        inferencer.unify(ImmutableList.of(setJson, setInt), outputType);
    assertThat(result).isPresent();
    assertThat(result.get().unifiedType()).hasValue(OpaqueType.create("set", SimpleType.DYN));
  }

  @Test
  public void unify_success_nullableStruct() {
    StructType structType =
        StructType.create("my.struct.Type", ImmutableSet.of(), (fieldName) -> Optional.empty());
    NullableType nullableStructType = NullableType.create(structType);
    CelType outputType = TypeParamType.create("O").withFreshTypeParamVariables(typeVarGenerator);
    Optional<UnificationResult> result =
        inferencer.unify(ImmutableList.of(nullableStructType, structType), outputType);
    assertThat(result).isPresent();
    assertThat(result.get().unifiedType()).hasValue(nullableStructType);
  }

  @Test
  public void unify_false_differentKinds() {
    OpaqueType vectorType = OpaqueType.create("vector", SimpleType.STRING);
    assertThat(inferencer.unify(JsonType.JSON, vectorType)).isEmpty();
  }

  @Test
  public void unify_false_sameKindDifferentTypeName() {
    OpaqueType setType = OpaqueType.create("set", SimpleType.STRING);
    OpaqueType vectorType = OpaqueType.create("vector", SimpleType.STRING);
    assertThat(inferencer.unify(setType, vectorType)).isEmpty();
  }

  @Test
  public void unify_false_sameTypeDifferentParameterTypes() {
    OpaqueType setType = OpaqueType.create("set", SimpleType.INT);
    OpaqueType vectorType = OpaqueType.create("set", SimpleType.STRING);
    assertThat(inferencer.unify(setType, vectorType)).isEmpty();
  }

  @Test
  public void unify_false_sameTypeDifferentParameterCounts() {
    OpaqueType setType = OpaqueType.create("set", SimpleType.INT);
    OpaqueType vectorType = OpaqueType.create("set", SimpleType.INT, SimpleType.INT);
    assertThat(inferencer.unify(setType, vectorType)).isEmpty();
  }

  @Test
  public void isAssignable_success_jsonSelect() {
    MapType mapParam = MapType.create(TypeParamType.create("K"), TypeParamType.create("V"));
    MapType mapInst = MapType.create(SimpleType.STRING, JsonType.JSON);
    MapType mapParamFresh = (MapType) mapParam.withFreshTypeParamVariables(typeVarGenerator);
    Optional<ImmutableMap<String, CelType>> result =
        inferencer.isAssignable(
            ImmutableList.of(mapInst, SimpleType.STRING),
            ImmutableList.of(mapParamFresh, mapParamFresh.keyType()));
    assertThat(result).isPresent();
    assertThat(result.get())
        .containsExactly(
            mapParamFresh.keyType().name(),
            SimpleType.STRING,
            mapParamFresh.valueType().name(),
            JsonType.JSON);
    inferencer.recordSubstitutions(result.get());
    assertThat(inferencer.specialize(mapParamFresh.valueType())).isEqualTo(JsonType.JSON);
  }

  @Test
  public void isAssignable_success_jsonEqualsDoubleAsList() {
    TypeParamType equalsArg = TypeParamType.create("T");
    CelType equalsArgFresh = equalsArg.withFreshTypeParamVariables(typeVarGenerator);
    Optional<ImmutableMap<String, CelType>> result =
        inferencer.isAssignable(
            ImmutableList.of(JsonType.JSON, SimpleType.DOUBLE),
            ImmutableList.of(equalsArgFresh, equalsArgFresh));
    assertThat(result).isPresent();
    inferencer.recordSubstitutions(result.get());
    assertThat(inferencer.specialize(equalsArgFresh)).isEqualTo(JsonType.JSON);
  }

  @Test
  public void isAssignable_success_doubleEqualsJsonAsList() {
    TypeParamType equalsArg = TypeParamType.create("T");
    CelType equalsArgFresh = equalsArg.withFreshTypeParamVariables(typeVarGenerator);
    Optional<ImmutableMap<String, CelType>> result =
        inferencer.isAssignable(
            ImmutableList.of(SimpleType.DOUBLE, JsonType.JSON),
            ImmutableList.of(equalsArgFresh, equalsArgFresh));
    assertThat(result).isPresent();
    inferencer.recordSubstitutions(result.get());
    assertThat(inferencer.specialize(equalsArgFresh)).isEqualTo(JsonType.JSON);
  }

  @Test
  public void isAssignable_false_doubleEqualsString() {
    TypeParamType equalsArg = TypeParamType.create("T");
    CelType equalsArgFresh = equalsArg.withFreshTypeParamVariables(typeVarGenerator);
    Optional<ImmutableMap<String, CelType>> result =
        inferencer.isAssignable(
            ImmutableList.of(SimpleType.DOUBLE, SimpleType.STRING),
            ImmutableList.of(equalsArgFresh, equalsArgFresh));
    assertThat(result).isEmpty();
  }

  @Test
  public void isAssignable_false_sameTypeDifferentParameterCounts() {
    OpaqueType setType = OpaqueType.create("set", SimpleType.STRING);
    OpaqueType setType2 = OpaqueType.create("set", SimpleType.STRING, SimpleType.INT);
    Optional<ImmutableMap<String, CelType>> result =
        inferencer.isAssignable(setType.parameters(), setType2.parameters());
    assertThat(result).isEmpty();
  }

  @Test
  public void finalize_success_typeFinalize() {
    StructType structType =
        StructType.create("my.struct.Type", ImmutableSet.of(), (fieldName) -> Optional.empty());
    TypeType typeOfStructType = TypeType.create(structType);
    TypeType typeOfType = TypeType.create(TypeParamType.create("T"));
    CelType typeOfTypeWithFreshVars = typeOfType.withFreshTypeParamVariables(typeVarGenerator);
    Optional<UnificationResult> result =
        inferencer.unify(typeOfStructType, typeOfTypeWithFreshVars);
    assertThat(result).isPresent();
    assertThat(result.get().unifiedType()).hasValue(typeOfStructType);
    inferencer.recordSubstitutions(result.get().substitutions());
    assertThat(inferencer.finalize(typeOfTypeWithFreshVars, SimpleType.DYN))
        .isEqualTo(typeOfStructType);
  }

  @Test
  public void finalize_success_dynFromTypeParam() {
    assertThat(inferencer.finalize(TypeParamType.create("T"), SimpleType.DYN))
        .isEqualTo(SimpleType.DYN);
  }

  private static class TypeVarGenerator implements Function<String, String> {
    private int typeVarId = 1;
    Map<String, String> generatedNames = new HashMap<>();

    @Override
    public String apply(String name) {
      return generatedNames.computeIfAbsent(
          name, (unused) -> String.format("@type_var_%d", typeVarId++));
    }
  }
}
