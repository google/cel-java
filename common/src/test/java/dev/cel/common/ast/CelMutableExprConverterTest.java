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
import com.google.testing.junit.testparameterinjector.TestParameter;
import com.google.testing.junit.testparameterinjector.TestParameterInjector;
import dev.cel.common.ast.CelExpr.CelCall;
import dev.cel.common.ast.CelExpr.CelList;
import dev.cel.common.ast.CelExpr.CelStruct;
import dev.cel.common.ast.CelMutableExpr.CelMutableCall;
import dev.cel.common.ast.CelMutableExpr.CelMutableComprehension;
import dev.cel.common.ast.CelMutableExpr.CelMutableList;
import dev.cel.common.ast.CelMutableExpr.CelMutableMap;
import dev.cel.common.ast.CelMutableExpr.CelMutableSelect;
import dev.cel.common.ast.CelMutableExpr.CelMutableStruct;
import dev.cel.common.values.CelByteString;
import dev.cel.common.values.NullValue;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(TestParameterInjector.class)
public class CelMutableExprConverterTest {
  @SuppressWarnings("Immutable") // Mutable by design
  private enum ConstantTestCase {
    NOT_SET(
        CelMutableExpr.ofConstant(1, CelConstant.ofNotSet()),
        CelExpr.ofConstant(1, CelConstant.ofNotSet())),
    NULL(
        CelMutableExpr.ofConstant(1, CelConstant.ofValue(NullValue.NULL_VALUE)),
        CelExpr.ofConstant(1, CelConstant.ofValue(NullValue.NULL_VALUE))),
    BOOLEAN(
        CelMutableExpr.ofConstant(1, CelConstant.ofValue(true)),
        CelExpr.ofConstant(1, CelConstant.ofValue(true))),
    INT64(
        CelMutableExpr.ofConstant(1, CelConstant.ofValue(10)),
        CelExpr.ofConstant(1, CelConstant.ofValue(10))),
    UINT64(
        CelMutableExpr.ofConstant(1, CelConstant.ofValue(UnsignedLong.valueOf(15))),
        CelExpr.ofConstant(1, CelConstant.ofValue(UnsignedLong.valueOf(15)))),
    DOUBLE(
        CelMutableExpr.ofConstant(1, CelConstant.ofValue(1.5)),
        CelExpr.ofConstant(1, CelConstant.ofValue(1.5))),
    STRING(
        CelMutableExpr.ofConstant(1, CelConstant.ofValue("Test")),
        CelExpr.ofConstant(1, CelConstant.ofValue("Test"))),
    BYTES(
        CelMutableExpr.ofConstant(1, CelConstant.ofValue(CelByteString.copyFromUtf8("TEST"))),
        CelExpr.ofConstant(1, CelConstant.ofValue(CelByteString.copyFromUtf8("TEST"))));

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

    assertThat(celExpr).isEqualTo(CelExpr.ofIdent(1L, "x"));
  }

  @Test
  public void convertCelIdent_toMutableIdent() {
    CelExpr celExpr = CelExpr.ofIdent(1L, "x");

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
        .isEqualTo(CelExpr.ofSelect(1L, CelExpr.ofIdent(2L, "x"), "field", /* isTestOnly= */ true));
  }

  @Test
  public void convertCelSelect_toMutableSelect() {
    CelExpr celExpr =
        CelExpr.ofSelect(1L, CelExpr.ofIdent(2L, "x"), "field", /* isTestOnly= */ true);

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
                        .setTarget(CelExpr.ofConstant(2L, CelConstant.ofValue("target")))
                        .addArgs(CelExpr.ofConstant(3L, CelConstant.ofValue("arg")))
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
                    .setTarget(CelExpr.ofConstant(2L, CelConstant.ofValue("target")))
                    .addArgs(CelExpr.ofConstant(3L, CelConstant.ofValue("arg")))
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
  public void convertMutableList_toCelList() {
    CelMutableExpr mutableExpr =
        CelMutableExpr.ofList(
            1L,
            CelMutableList.create(
                ImmutableList.of(
                    CelMutableExpr.ofConstant(2L, CelConstant.ofValue("element1")),
                    CelMutableExpr.ofConstant(3L, CelConstant.ofValue("element2"))),
                ImmutableList.of(0, 1)));

    CelExpr celExpr = CelMutableExprConverter.fromMutableExpr(mutableExpr);

    assertThat(celExpr)
        .isEqualTo(
            CelExpr.ofList(
                1L,
                ImmutableList.of(
                    CelExpr.ofConstant(2L, CelConstant.ofValue("element1")),
                    CelExpr.ofConstant(3L, CelConstant.ofValue("element2"))),
                ImmutableList.of(0, 1)));
  }

  @Test
  public void convertCelList_toMutableList() {
    CelExpr celExpr =
        CelExpr.ofList(
            1L,
            ImmutableList.of(
                CelExpr.ofConstant(2L, CelConstant.ofValue("element1")),
                CelExpr.ofConstant(3L, CelConstant.ofValue("element2"))),
            ImmutableList.of(0, 1));

    CelMutableExpr mutableExpr = CelMutableExprConverter.fromCelExpr(celExpr);

    assertThat(mutableExpr)
        .isEqualTo(
            CelMutableExpr.ofList(
                1L,
                CelMutableList.create(
                    ImmutableList.of(
                        CelMutableExpr.ofConstant(2L, CelConstant.ofValue("element1")),
                        CelMutableExpr.ofConstant(3L, CelConstant.ofValue("element2"))),
                    ImmutableList.of(0, 1))));
  }

  @Test
  public void convertMutableStruct_toCelStruct() {
    CelMutableExpr mutableExpr =
        CelMutableExpr.ofStruct(
            8L,
            CelMutableStruct.create(
                "message",
                ImmutableList.of(
                    CelMutableStruct.Entry.create(
                        9L,
                        "field",
                        CelMutableExpr.ofConstant(10L, CelConstant.ofValue("value")),
                        /* optionalEntry= */ true))));

    CelExpr celExpr = CelMutableExprConverter.fromMutableExpr(mutableExpr);

    assertThat(celExpr)
        .isEqualTo(
            CelExpr.ofStruct(
                8L,
                "message",
                ImmutableList.of(
                    CelStruct.Entry.newBuilder()
                        .setId(9L)
                        .setFieldKey("field")
                        .setValue(CelExpr.ofConstant(10L, CelConstant.ofValue("value")))
                        .setOptionalEntry(true)
                        .build())));
  }

  @Test
  public void convertCelStruct_toMutableStruct() {
    CelExpr celExpr =
        CelExpr.ofStruct(
            8L,
            "message",
            ImmutableList.of(
                CelStruct.Entry.newBuilder()
                    .setId(9L)
                    .setFieldKey("field")
                    .setValue(CelExpr.ofConstant(10L, CelConstant.ofValue("value")))
                    .setOptionalEntry(true)
                    .build()));

    CelMutableExpr mutableExpr = CelMutableExprConverter.fromCelExpr(celExpr);

    assertThat(mutableExpr)
        .isEqualTo(
            CelMutableExpr.ofStruct(
                8L,
                CelMutableStruct.create(
                    "message",
                    ImmutableList.of(
                        CelMutableStruct.Entry.create(
                            9L,
                            "field",
                            CelMutableExpr.ofConstant(10L, CelConstant.ofValue("value")),
                            /* optionalEntry= */ true)))));
  }

  @Test
  public void convertMutableMap_toCelMap() {
    CelMutableExpr mutableExpr =
        CelMutableExpr.ofMap(
            9L,
            CelMutableMap.create(
                ImmutableList.of(
                    CelMutableMap.Entry.create(
                        10L,
                        CelMutableExpr.ofConstant(11L, CelConstant.ofValue("key")),
                        CelMutableExpr.ofConstant(12L, CelConstant.ofValue("value")),
                        /* optionalEntry= */ true))));

    CelExpr celExpr = CelMutableExprConverter.fromMutableExpr(mutableExpr);

    assertThat(celExpr)
        .isEqualTo(
            CelExpr.ofMap(
                9L,
                ImmutableList.of(
                    CelExpr.ofMapEntry(
                        10L,
                        CelExpr.ofConstant(11L, CelConstant.ofValue("key")),
                        CelExpr.ofConstant(12L, CelConstant.ofValue("value")),
                        true))));
  }

  @Test
  public void convertCelMap_toMutableMap() {
    CelExpr celExpr =
        CelExpr.ofMap(
            9L,
            ImmutableList.of(
                CelExpr.ofMapEntry(
                    10L,
                    CelExpr.ofConstant(11L, CelConstant.ofValue("key")),
                    CelExpr.ofConstant(12L, CelConstant.ofValue("value")),
                    true)));

    CelMutableExpr mutableExpr = CelMutableExprConverter.fromCelExpr(celExpr);

    assertThat(mutableExpr)
        .isEqualTo(
            CelMutableExpr.ofMap(
                9L,
                CelMutableMap.create(
                    ImmutableList.of(
                        CelMutableMap.Entry.create(
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
                CelMutableExpr.ofList(
                    2L,
                    CelMutableList.create(
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
                    .setList(
                        CelList.newBuilder()
                            .addElements(CelExpr.ofConstant(3L, CelConstant.ofValue(true)))
                            .build())
                    .build(),
                "accuVar",
                CelExpr.ofConstant(4L, CelConstant.ofValue(true)),
                CelExpr.ofConstant(5L, CelConstant.ofValue(true)),
                CelExpr.ofConstant(6L, CelConstant.ofValue(true)),
                CelExpr.ofIdent(7L, "__result__")));
  }

  @Test
  public void convertCelComprehension_toMutableComprehension() {
    CelExpr celExpr =
        CelExpr.ofComprehension(
            1L,
            "iterVar",
            CelExpr.newBuilder()
                .setId(2L)
                .setList(
                    CelList.newBuilder()
                        .addElements(CelExpr.ofConstant(3L, CelConstant.ofValue(true)))
                        .build())
                .build(),
            "accuVar",
            CelExpr.ofConstant(4L, CelConstant.ofValue(true)),
            CelExpr.ofConstant(5L, CelConstant.ofValue(true)),
            CelExpr.ofConstant(6L, CelConstant.ofValue(true)),
            CelExpr.ofIdent(7L, "__result__"));

    CelMutableExpr mutableExpr = CelMutableExprConverter.fromCelExpr(celExpr);
    assertThat(mutableExpr)
        .isEqualTo(
            CelMutableExpr.ofComprehension(
                1L,
                CelMutableComprehension.create(
                    "iterVar",
                    CelMutableExpr.ofList(
                        2L,
                        CelMutableList.create(
                            CelMutableExpr.ofConstant(3L, CelConstant.ofValue(true)))),
                    "accuVar",
                    CelMutableExpr.ofConstant(4L, CelConstant.ofValue(true)),
                    CelMutableExpr.ofConstant(5L, CelConstant.ofValue(true)),
                    CelMutableExpr.ofConstant(6L, CelConstant.ofValue(true)),
                    CelMutableExpr.ofIdent(7L, "__result__"))));
  }
}
