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

package dev.cel.common.ast;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.common.primitives.UnsignedLong;
import com.google.protobuf.ByteString;
import com.google.protobuf.NullValue;
import com.google.testing.junit.testparameterinjector.TestParameter;
import com.google.testing.junit.testparameterinjector.TestParameterInjector;
import dev.cel.common.ast.CelExpr.CelCall;
import dev.cel.common.ast.CelExpr.CelCreateList;
import dev.cel.common.ast.CelExpr.CelCreateStruct;
import dev.cel.common.ast.CelMutableExpr.CelMutableCall;
import dev.cel.common.ast.CelMutableExpr.CelMutableComprehension;
import dev.cel.common.ast.CelMutableExpr.CelMutableCreateList;
import dev.cel.common.ast.CelMutableExpr.CelMutableCreateMap;
import dev.cel.common.ast.CelMutableExpr.CelMutableCreateStruct;
import dev.cel.common.ast.CelMutableExpr.CelMutableSelect;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(TestParameterInjector.class)
public class CelMutableExprConverterTest {
  @SuppressWarnings("Immutable") // Mutable by design
  private enum ConstantTestCase {
    NOT_SET(
        CelMutableExpr.ofConstant(1, CelConstant.ofNotSet()),
        CelExpr.ofConstantExpr(1, CelConstant.ofNotSet())),
    NULL(
        CelMutableExpr.ofConstant(1, CelConstant.ofValue(NullValue.NULL_VALUE)),
        CelExpr.ofConstantExpr(1, CelConstant.ofValue(NullValue.NULL_VALUE))),
    BOOLEAN(
        CelMutableExpr.ofConstant(1, CelConstant.ofValue(true)),
        CelExpr.ofConstantExpr(1, CelConstant.ofValue(true))),
    INT64(
        CelMutableExpr.ofConstant(1, CelConstant.ofValue(10)),
        CelExpr.ofConstantExpr(1, CelConstant.ofValue(10))),
    UINT64(
        CelMutableExpr.ofConstant(1, CelConstant.ofValue(UnsignedLong.valueOf(15))),
        CelExpr.ofConstantExpr(1, CelConstant.ofValue(UnsignedLong.valueOf(15)))),
    DOUBLE(
        CelMutableExpr.ofConstant(1, CelConstant.ofValue(1.5)),
        CelExpr.ofConstantExpr(1, CelConstant.ofValue(1.5))),
    STRING(
        CelMutableExpr.ofConstant(1, CelConstant.ofValue("Test")),
        CelExpr.ofConstantExpr(1, CelConstant.ofValue("Test"))),
    BYTES(
        CelMutableExpr.ofConstant(1, CelConstant.ofValue(ByteString.copyFromUtf8("TEST"))),
        CelExpr.ofConstantExpr(1, CelConstant.ofValue(ByteString.copyFromUtf8("TEST"))));

    final CelMutableExpr mutableExpr;
    final CelExpr celExpr;

    ConstantTestCase(CelMutableExpr mutableExpr, CelExpr celExpr) {
      this.mutableExpr = mutableExpr;
      this.celExpr = celExpr;
    }
  }

  @Test
  public void convertConstant_bidirectional(@TestParameter ConstantTestCase constantTestCase) {
    CelExpr convertedCelExpr =
        CelMutableExprConverter.fromMutableExpr(constantTestCase.mutableExpr);
    CelMutableExpr convertedMutableExpr =
        CelMutableExprConverter.fromCelExpr(constantTestCase.celExpr);

    assertThat(convertedCelExpr).isEqualTo(constantTestCase.celExpr);
    assertThat(convertedMutableExpr).isEqualTo(constantTestCase.mutableExpr);
  }

  @Test
  public void convertMutableNotSet_toCelNotSet() {
    CelMutableExpr mutableExpr = CelMutableExpr.ofNotSet(1L);

    CelExpr celExpr = CelMutableExprConverter.fromMutableExpr(mutableExpr);

    assertThat(celExpr).isEqualTo(CelExpr.ofNotSet(1L));
  }

  @Test
  public void convertCelNotSet_toMutableNotSet() {
    CelExpr celExpr = CelExpr.ofNotSet(1L);

    CelMutableExpr mutableExpr = CelMutableExprConverter.fromCelExpr(celExpr);

    assertThat(mutableExpr).isEqualTo(CelMutableExpr.ofNotSet(1L));
  }

  @Test
  public void convertMutableIdent_toCelIdent() {
    CelMutableExpr mutableExpr = CelMutableExpr.ofIdent(1L, "x");

    CelExpr celExpr = CelMutableExprConverter.fromMutableExpr(mutableExpr);

    assertThat(celExpr).isEqualTo(CelExpr.ofIdentExpr(1L, "x"));
  }

  @Test
  public void convertCelIdent_toMutableIdent() {
    CelExpr celExpr = CelExpr.ofIdentExpr(1L, "x");

    CelMutableExpr mutableExpr = CelMutableExprConverter.fromCelExpr(celExpr);

    assertThat(mutableExpr).isEqualTo(CelMutableExpr.ofIdent(1L, "x"));
  }

  @Test
  public void convertMutableSelect_toCelSelect() {
    CelMutableExpr mutableExpr =
        CelMutableExpr.ofSelect(
            1L,
            CelMutableSelect.create(
                CelMutableExpr.ofIdent(2L, "x"), "field", /* testOnly= */ true));

    CelExpr celExpr = CelMutableExprConverter.fromMutableExpr(mutableExpr);

    assertThat(celExpr)
        .isEqualTo(
            CelExpr.ofSelectExpr(
                1L, CelExpr.ofIdentExpr(2L, "x"), "field", /* isTestOnly= */ true));
  }

  @Test
  public void convertCelSelect_toMutableSelect() {
    CelExpr celExpr =
        CelExpr.ofSelectExpr(1L, CelExpr.ofIdentExpr(2L, "x"), "field", /* isTestOnly= */ true);

    CelMutableExpr mutableExpr = CelMutableExprConverter.fromCelExpr(celExpr);

    assertThat(mutableExpr)
        .isEqualTo(
            CelMutableExpr.ofSelect(
                1L,
                CelMutableSelect.create(
                    CelMutableExpr.ofIdent(2L, "x"), "field", /* testOnly= */ true)));
  }

  @Test
  public void convertMutableCall_toCelCall() {
    CelMutableExpr mutableExpr =
        CelMutableExpr.ofCall(
            1L,
            CelMutableCall.create(
                CelMutableExpr.ofConstant(2L, CelConstant.ofValue("target")),
                "function",
                CelMutableExpr.ofConstant(3L, CelConstant.ofValue("arg"))));

    CelExpr celExpr = CelMutableExprConverter.fromMutableExpr(mutableExpr);

    assertThat(celExpr)
        .isEqualTo(
            CelExpr.newBuilder()
                .setId(1L)
                .setCall(
                    CelCall.newBuilder()
                        .setFunction("function")
                        .setTarget(CelExpr.ofConstantExpr(2L, CelConstant.ofValue("target")))
                        .addArgs(CelExpr.ofConstantExpr(3L, CelConstant.ofValue("arg")))
                        .build())
                .build());
  }

  @Test
  public void convertCelCall_toMutableCall() {
    CelExpr celExpr =
        CelExpr.newBuilder()
            .setId(1L)
            .setCall(
                CelCall.newBuilder()
                    .setFunction("function")
                    .setTarget(CelExpr.ofConstantExpr(2L, CelConstant.ofValue("target")))
                    .addArgs(CelExpr.ofConstantExpr(3L, CelConstant.ofValue("arg")))
                    .build())
            .build();

    CelMutableExpr mutableExpr = CelMutableExprConverter.fromCelExpr(celExpr);

    assertThat(mutableExpr)
        .isEqualTo(
            CelMutableExpr.ofCall(
                1L,
                CelMutableCall.create(
                    CelMutableExpr.ofConstant(2L, CelConstant.ofValue("target")),
                    "function",
                    CelMutableExpr.ofConstant(3L, CelConstant.ofValue("arg")))));
  }

  @Test
  public void convertMutableCreateList_toCelCreateList() {
    CelMutableExpr mutableExpr =
        CelMutableExpr.ofCreateList(
            1L,
            CelMutableCreateList.create(
                ImmutableList.of(
                    CelMutableExpr.ofConstant(2L, CelConstant.ofValue("element1")),
                    CelMutableExpr.ofConstant(3L, CelConstant.ofValue("element2"))),
                ImmutableList.of(0, 1)));

    CelExpr celExpr = CelMutableExprConverter.fromMutableExpr(mutableExpr);

    assertThat(celExpr)
        .isEqualTo(
            CelExpr.ofCreateListExpr(
                1L,
                ImmutableList.of(
                    CelExpr.ofConstantExpr(2L, CelConstant.ofValue("element1")),
                    CelExpr.ofConstantExpr(3L, CelConstant.ofValue("element2"))),
                ImmutableList.of(0, 1)));
  }

  @Test
  public void convertCelCreateList_toMutableCreateList() {
    CelExpr celExpr =
        CelExpr.ofCreateListExpr(
            1L,
            ImmutableList.of(
                CelExpr.ofConstantExpr(2L, CelConstant.ofValue("element1")),
                CelExpr.ofConstantExpr(3L, CelConstant.ofValue("element2"))),
            ImmutableList.of(0, 1));

    CelMutableExpr mutableExpr = CelMutableExprConverter.fromCelExpr(celExpr);

    assertThat(mutableExpr)
        .isEqualTo(
            CelMutableExpr.ofCreateList(
                1L,
                CelMutableCreateList.create(
                    ImmutableList.of(
                        CelMutableExpr.ofConstant(2L, CelConstant.ofValue("element1")),
                        CelMutableExpr.ofConstant(3L, CelConstant.ofValue("element2"))),
                    ImmutableList.of(0, 1))));
  }

  @Test
  public void convertMutableCreateStruct_toCelCreateStruct() {
    CelMutableExpr mutableExpr =
        CelMutableExpr.ofCreateStruct(
            8L,
            CelMutableCreateStruct.create(
                "message",
                ImmutableList.of(
                    CelMutableCreateStruct.Entry.create(
                        9L,
                        "field",
                        CelMutableExpr.ofConstant(10L, CelConstant.ofValue("value")),
                        /* optionalEntry= */ true))));

    CelExpr celExpr = CelMutableExprConverter.fromMutableExpr(mutableExpr);

    assertThat(celExpr)
        .isEqualTo(
            CelExpr.ofCreateStructExpr(
                8L,
                "message",
                ImmutableList.of(
                    CelCreateStruct.Entry.newBuilder()
                        .setId(9L)
                        .setFieldKey("field")
                        .setValue(CelExpr.ofConstantExpr(10L, CelConstant.ofValue("value")))
                        .setOptionalEntry(true)
                        .build())));
  }

  @Test
  public void convertCelCreateStruct_toMutableCreateStruct() {
    CelExpr celExpr =
        CelExpr.ofCreateStructExpr(
            8L,
            "message",
            ImmutableList.of(
                CelCreateStruct.Entry.newBuilder()
                    .setId(9L)
                    .setFieldKey("field")
                    .setValue(CelExpr.ofConstantExpr(10L, CelConstant.ofValue("value")))
                    .setOptionalEntry(true)
                    .build()));

    CelMutableExpr mutableExpr = CelMutableExprConverter.fromCelExpr(celExpr);

    assertThat(mutableExpr)
        .isEqualTo(
            CelMutableExpr.ofCreateStruct(
                8L,
                CelMutableCreateStruct.create(
                    "message",
                    ImmutableList.of(
                        CelMutableCreateStruct.Entry.create(
                            9L,
                            "field",
                            CelMutableExpr.ofConstant(10L, CelConstant.ofValue("value")),
                            /* optionalEntry= */ true)))));
  }

  @Test
  public void convertMutableCreateMap_toCelCreateMap() {
    CelMutableExpr mutableExpr =
        CelMutableExpr.ofCreateMap(
            9L,
            CelMutableCreateMap.create(
                ImmutableList.of(
                    CelMutableCreateMap.Entry.create(
                        10L,
                        CelMutableExpr.ofConstant(11L, CelConstant.ofValue("key")),
                        CelMutableExpr.ofConstant(12L, CelConstant.ofValue("value")),
                        /* optionalEntry= */ true))));

    CelExpr celExpr = CelMutableExprConverter.fromMutableExpr(mutableExpr);

    assertThat(celExpr)
        .isEqualTo(
            CelExpr.ofCreateMapExpr(
                9L,
                ImmutableList.of(
                    CelExpr.ofCreateMapEntryExpr(
                        10L,
                        CelExpr.ofConstantExpr(11L, CelConstant.ofValue("key")),
                        CelExpr.ofConstantExpr(12L, CelConstant.ofValue("value")),
                        true))));
  }

  @Test
  public void convertCelCreateMap_toMutableCreateMap() {
    CelExpr celExpr =
        CelExpr.ofCreateMapExpr(
            9L,
            ImmutableList.of(
                CelExpr.ofCreateMapEntryExpr(
                    10L,
                    CelExpr.ofConstantExpr(11L, CelConstant.ofValue("key")),
                    CelExpr.ofConstantExpr(12L, CelConstant.ofValue("value")),
                    true)));

    CelMutableExpr mutableExpr = CelMutableExprConverter.fromCelExpr(celExpr);

    assertThat(mutableExpr)
        .isEqualTo(
            CelMutableExpr.ofCreateMap(
                9L,
                CelMutableCreateMap.create(
                    ImmutableList.of(
                        CelMutableCreateMap.Entry.create(
                            10L,
                            CelMutableExpr.ofConstant(11L, CelConstant.ofValue("key")),
                            CelMutableExpr.ofConstant(12L, CelConstant.ofValue("value")),
                            /* optionalEntry= */ true)))));
  }

  @Test
  public void convertMutableComprehension_toCelComprehension() {
    CelMutableExpr mutableExpr =
        CelMutableExpr.ofComprehension(
            1L,
            CelMutableComprehension.create(
                "iterVar",
                CelMutableExpr.ofCreateList(
                    2L,
                    CelMutableCreateList.create(
                        CelMutableExpr.ofConstant(3L, CelConstant.ofValue(true)))),
                "accuVar",
                CelMutableExpr.ofConstant(4L, CelConstant.ofValue(true)),
                CelMutableExpr.ofConstant(5L, CelConstant.ofValue(true)),
                CelMutableExpr.ofConstant(6L, CelConstant.ofValue(true)),
                CelMutableExpr.ofIdent(7L, "__result__")));

    CelExpr celExpr = CelMutableExprConverter.fromMutableExpr(mutableExpr);

    assertThat(celExpr)
        .isEqualTo(
            CelExpr.ofComprehension(
                1L,
                "iterVar",
                CelExpr.newBuilder()
                    .setId(2L)
                    .setCreateList(
                        CelCreateList.newBuilder()
                            .addElements(CelExpr.ofConstantExpr(3L, CelConstant.ofValue(true)))
                            .build())
                    .build(),
                "accuVar",
                CelExpr.ofConstantExpr(4L, CelConstant.ofValue(true)),
                CelExpr.ofConstantExpr(5L, CelConstant.ofValue(true)),
                CelExpr.ofConstantExpr(6L, CelConstant.ofValue(true)),
                CelExpr.ofIdentExpr(7L, "__result__")));
  }

  @Test
  public void convertCelComprehension_toMutableComprehension() {
    CelExpr celExpr =
        CelExpr.ofComprehension(
            1L,
            "iterVar",
            CelExpr.newBuilder()
                .setId(2L)
                .setCreateList(
                    CelCreateList.newBuilder()
                        .addElements(CelExpr.ofConstantExpr(3L, CelConstant.ofValue(true)))
                        .build())
                .build(),
            "accuVar",
            CelExpr.ofConstantExpr(4L, CelConstant.ofValue(true)),
            CelExpr.ofConstantExpr(5L, CelConstant.ofValue(true)),
            CelExpr.ofConstantExpr(6L, CelConstant.ofValue(true)),
            CelExpr.ofIdentExpr(7L, "__result__"));

    CelMutableExpr mutableExpr = CelMutableExprConverter.fromCelExpr(celExpr);

    assertThat(mutableExpr)
        .isEqualTo(
            CelMutableExpr.ofComprehension(
                1L,
                CelMutableComprehension.create(
                    "iterVar",
                    CelMutableExpr.ofCreateList(
                        2L,
                        CelMutableCreateList.create(
                            CelMutableExpr.ofConstant(3L, CelConstant.ofValue(true)))),
                    "accuVar",
                    CelMutableExpr.ofConstant(4L, CelConstant.ofValue(true)),
                    CelMutableExpr.ofConstant(5L, CelConstant.ofValue(true)),
                    CelMutableExpr.ofConstant(6L, CelConstant.ofValue(true)),
                    CelMutableExpr.ofIdent(7L, "__result__"))));
  }
}
