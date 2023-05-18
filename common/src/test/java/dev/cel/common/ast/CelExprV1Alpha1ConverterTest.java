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

package dev.cel.common.ast;

import static com.google.common.truth.Truth.assertThat;

import com.google.api.expr.v1alpha1.Constant;
import com.google.api.expr.v1alpha1.Expr;
import com.google.api.expr.v1alpha1.Expr.Call;
import com.google.api.expr.v1alpha1.Expr.Comprehension;
import com.google.api.expr.v1alpha1.Expr.CreateList;
import com.google.api.expr.v1alpha1.Expr.CreateStruct;
import com.google.api.expr.v1alpha1.Expr.Ident;
import com.google.api.expr.v1alpha1.Expr.Select;
import com.google.api.expr.v1alpha1.Reference;
import com.google.common.collect.ImmutableList;
import com.google.common.primitives.UnsignedLong;
import com.google.protobuf.ByteString;
import com.google.protobuf.NullValue;
import com.google.testing.junit.testparameterinjector.TestParameter;
import com.google.testing.junit.testparameterinjector.TestParameterInjector;
import com.google.testing.junit.testparameterinjector.TestParameters;
import java.util.Optional;
import org.junit.Test;
import org.junit.runner.RunWith;

// LINT.IfChange
@RunWith(TestParameterInjector.class)
public class CelExprV1Alpha1ConverterTest {
  private enum ConstantTestCase {
    NULL(
        Expr.newBuilder()
            .setId(1)
            .setConstExpr(Constant.newBuilder().setNullValue(NullValue.NULL_VALUE).build())
            .build(),
        CelExpr.ofConstantExpr(1, CelConstant.ofValue(NullValue.NULL_VALUE))),
    BOOLEAN(
        Expr.newBuilder()
            .setId(1)
            .setConstExpr(Constant.newBuilder().setBoolValue(true).build())
            .build(),
        CelExpr.ofConstantExpr(1, CelConstant.ofValue(true))),
    INT64(
        Expr.newBuilder()
            .setId(1)
            .setConstExpr(Constant.newBuilder().setInt64Value(10).build())
            .build(),
        CelExpr.ofConstantExpr(1, CelConstant.ofValue(10))),
    UINT64(
        Expr.newBuilder()
            .setId(1)
            .setConstExpr(Constant.newBuilder().setUint64Value(15).build())
            .build(),
        CelExpr.ofConstantExpr(1, CelConstant.ofValue(UnsignedLong.valueOf(15)))),
    DOUBLE(
        Expr.newBuilder()
            .setId(1)
            .setConstExpr(Constant.newBuilder().setDoubleValue(1.5).build())
            .build(),
        CelExpr.ofConstantExpr(1, CelConstant.ofValue(1.5))),
    STRING(
        Expr.newBuilder()
            .setId(1)
            .setConstExpr(Constant.newBuilder().setStringValue("Test").build())
            .build(),
        CelExpr.ofConstantExpr(1, CelConstant.ofValue("Test"))),
    BYTES(
        Expr.newBuilder()
            .setId(1)
            .setConstExpr(
                Constant.newBuilder().setBytesValue(ByteString.copyFromUtf8("TEST")).build())
            .build(),
        CelExpr.ofConstantExpr(1, CelConstant.ofValue(ByteString.copyFromUtf8("TEST"))));

    final Expr protoExpr;
    final CelExpr celExpr;

    ConstantTestCase(Expr expr, CelExpr celExpr) {
      this.protoExpr = expr;
      this.celExpr = celExpr;
    }
  }

  @Test
  public void convertConstant_bidirectional(@TestParameter ConstantTestCase constantTestCase) {
    CelExpr celExpr = CelExprV1Alpha1Converter.fromExpr(constantTestCase.protoExpr);
    Expr expr = CelExprV1Alpha1Converter.fromCelExpr(constantTestCase.celExpr);

    assertThat(celExpr).isEqualTo(constantTestCase.celExpr);
    assertThat(expr).isEqualTo(constantTestCase.protoExpr);
  }

  @Test
  public void convertExprNotSet_toCelNotSet() {
    Expr expr = Expr.newBuilder().setId(1).build();

    CelExpr celExpr = CelExprV1Alpha1Converter.fromExpr(expr);

    assertThat(celExpr).isEqualTo(CelExpr.ofNotSet(1));
  }

  @Test
  public void convertExprIdent_toCelIdent() {
    Expr expr =
        Expr.newBuilder().setId(2).setIdentExpr(Ident.newBuilder().setName("Test").build()).build();

    CelExpr celExpr = CelExprV1Alpha1Converter.fromExpr(expr);

    assertThat(celExpr).isEqualTo(CelExpr.ofIdentExpr(2, "Test"));
  }

  @Test
  @TestParameters("{isTestOnly: true}")
  @TestParameters("{isTestOnly: false}")
  public void convertExprSelect_toCelSelect(boolean isTestOnly) {
    Expr expr =
        Expr.newBuilder()
            .setId(3)
            .setSelectExpr(
                Select.newBuilder()
                    .setField("field")
                    .setOperand(
                        Expr.newBuilder()
                            .setId(4)
                            .setConstExpr(Constant.newBuilder().setBoolValue(true).build()))
                    .setTestOnly(isTestOnly))
            .build();

    CelExpr celExpr = CelExprV1Alpha1Converter.fromExpr(expr);

    assertThat(celExpr)
        .isEqualTo(
            CelExpr.ofSelectExpr(
                3, CelExpr.ofConstantExpr(4, CelConstant.ofValue(true)), "field", isTestOnly));
  }

  @Test
  public void convertExprCall_toCelCall() {
    Expr expr =
        Expr.newBuilder()
            .setId(1)
            .setCallExpr(
                Call.newBuilder()
                    .setTarget(newInt64ConstantExpr(2, 10))
                    .setFunction("func")
                    .addArgs(newInt64ConstantExpr(3, 20))
                    .build())
            .build();

    CelExpr celExpr = CelExprV1Alpha1Converter.fromExpr(expr);

    assertThat(celExpr)
        .isEqualTo(
            CelExpr.ofCallExpr(
                1,
                Optional.of(CelExpr.ofConstantExpr(2, CelConstant.ofValue(10))),
                "func",
                ImmutableList.of(CelExpr.ofConstantExpr(3, CelConstant.ofValue(20)))));
  }

  @Test
  public void convertExprList_toCelList() {
    Expr expr =
        Expr.newBuilder()
            .setId(1)
            .setListExpr(
                CreateList.newBuilder()
                    .addElements(newInt64ConstantExpr(2, 10))
                    .addElements(newInt64ConstantExpr(3, 15))
                    .addOptionalIndices(1)
                    .build())
            .build();

    CelExpr celExpr = CelExprV1Alpha1Converter.fromExpr(expr);

    assertThat(celExpr)
        .isEqualTo(
            CelExpr.ofCreateListExpr(
                1,
                ImmutableList.of(
                    CelExpr.ofConstantExpr(2, CelConstant.ofValue(10)),
                    CelExpr.ofConstantExpr(3, CelConstant.ofValue(15))),
                ImmutableList.of(1)));
  }

  @Test
  public void convertExprStructExpr_toCelStruct_withFieldKey() {
    Expr expr =
        Expr.newBuilder()
            .setId(1)
            .setStructExpr(
                CreateStruct.newBuilder()
                    .setMessageName("messageName")
                    .addEntries(
                        CreateStruct.Entry.newBuilder()
                            .setId(2)
                            .setFieldKey("fieldKey")
                            .setValue(newInt64ConstantExpr(3, 10))
                            .setOptionalEntry(true)
                            .build())
                    .build())
            .build();

    CelExpr celExpr = CelExprV1Alpha1Converter.fromExpr(expr);

    assertThat(celExpr)
        .isEqualTo(
            CelExpr.ofCreateStructExpr(
                1,
                "messageName",
                ImmutableList.of(
                    CelExpr.ofCreateStructFieldEntryExpr(
                        2, "fieldKey", CelExpr.ofConstantExpr(3, CelConstant.ofValue(10)), true))));
  }

  @Test
  public void convertExprStructExpr_toCelStruct_withMapKey() {
    Expr expr =
        Expr.newBuilder()
            .setId(1)
            .setStructExpr(
                CreateStruct.newBuilder()
                    .setMessageName("messageName")
                    .addEntries(
                        CreateStruct.Entry.newBuilder()
                            .setId(2)
                            .setMapKey(newInt64ConstantExpr(3, 15))
                            .setValue(newInt64ConstantExpr(4, 10))
                            .setOptionalEntry(true)
                            .build())
                    .build())
            .build();

    CelExpr celExpr = CelExprV1Alpha1Converter.fromExpr(expr);

    assertThat(celExpr)
        .isEqualTo(
            CelExpr.ofCreateStructExpr(
                1,
                "messageName",
                ImmutableList.of(
                    CelExpr.ofCreateStructMapEntryExpr(
                        2,
                        CelExpr.ofConstantExpr(3, CelConstant.ofValue(15)),
                        CelExpr.ofConstantExpr(4, CelConstant.ofValue(10)),
                        true))));
  }

  @Test
  public void convertExprComprehensionExpr_toCelComprehension() {
    Expr expr =
        Expr.newBuilder()
            .setId(1)
            .setComprehensionExpr(
                Comprehension.newBuilder()
                    .setIterVar("iterVar")
                    .setIterRange(newInt64ConstantExpr(2, 10))
                    .setAccuVar("accuVar")
                    .setAccuInit(newInt64ConstantExpr(3, 20))
                    .setLoopCondition(
                        Expr.newBuilder()
                            .setId(4)
                            .setCallExpr(Call.newBuilder().setFunction("testCondition").build())
                            .build())
                    .setLoopStep(
                        Expr.newBuilder()
                            .setId(5)
                            .setCallExpr(Call.newBuilder().setFunction("testStep").build())
                            .build())
                    .setResult(newInt64ConstantExpr(6, 30))
                    .build())
            .build();

    CelExpr celExpr = CelExprV1Alpha1Converter.fromExpr(expr);

    assertThat(celExpr)
        .isEqualTo(
            CelExpr.ofComprehension(
                1,
                "iterVar",
                CelExpr.ofConstantExpr(2, CelConstant.ofValue(10)),
                "accuVar",
                CelExpr.ofConstantExpr(3, CelConstant.ofValue(20)),
                CelExpr.ofCallExpr(4, Optional.empty(), "testCondition", ImmutableList.of()),
                CelExpr.ofCallExpr(5, Optional.empty(), "testStep", ImmutableList.of()),
                CelExpr.ofConstantExpr(6, CelConstant.ofValue(30))));
  }

  @Test
  public void convertExprReference_toCelReference_withOverloadIds() {
    Reference reference =
        Reference.newBuilder().setName("ref").addAllOverloadId(ImmutableList.of("a", "b")).build();

    CelReference celReference = CelExprV1Alpha1Converter.exprReferenceToCelReference(reference);

    assertThat(celReference)
        .isEqualTo(
            CelReference.newBuilder()
                .setName("ref")
                .addOverloadIds(ImmutableList.of("a", "b"))
                .build());
  }

  @Test
  public void convertExprReference_toCelReference_withValue() {
    Reference reference =
        Reference.newBuilder()
            .setName("ref")
            .setValue(Constant.newBuilder().setInt64Value(1).build())
            .build();

    CelReference celReference = CelExprV1Alpha1Converter.exprReferenceToCelReference(reference);

    assertThat(celReference)
        .isEqualTo(
            CelReference.newBuilder().setName("ref").setValue(CelConstant.ofValue(1)).build());
  }

  @Test
  public void convertCelNotSet_toExprNotSet() {
    CelExpr celExpr = CelExpr.ofNotSet(1);

    Expr expr = CelExprV1Alpha1Converter.fromCelExpr(celExpr);

    assertThat(expr).isEqualTo(Expr.newBuilder().setId(1).build());
  }

  @Test
  public void convertCelIdent_toExprIdent() {
    CelExpr celExpr = CelExpr.ofIdentExpr(2, "Test");

    Expr expr = CelExprV1Alpha1Converter.fromCelExpr(celExpr);

    assertThat(expr)
        .isEqualTo(
            Expr.newBuilder()
                .setId(2)
                .setIdentExpr(Ident.newBuilder().setName("Test").build())
                .build());
  }

  @Test
  @TestParameters("{isTestOnly: true}")
  @TestParameters("{isTestOnly: false}")
  public void convertCelSelect_toExprSelect(boolean isTestOnly) {
    CelExpr celExpr =
        CelExpr.ofSelectExpr(
            3, CelExpr.ofConstantExpr(4, CelConstant.ofValue(true)), "field", isTestOnly);

    Expr expr = CelExprV1Alpha1Converter.fromCelExpr(celExpr);

    assertThat(expr)
        .isEqualTo(
            Expr.newBuilder()
                .setId(3)
                .setSelectExpr(
                    Select.newBuilder()
                        .setField("field")
                        .setOperand(
                            Expr.newBuilder()
                                .setId(4)
                                .setConstExpr(Constant.newBuilder().setBoolValue(true).build()))
                        .setTestOnly(isTestOnly))
                .build());
  }

  @Test
  public void convertCelCall_toExprCall() {
    CelExpr celExpr =
        CelExpr.ofCallExpr(
            1,
            Optional.of(CelExpr.ofConstantExpr(2, CelConstant.ofValue(10))),
            "func",
            ImmutableList.of(CelExpr.ofConstantExpr(3, CelConstant.ofValue(20))));

    Expr expr = CelExprV1Alpha1Converter.fromCelExpr(celExpr);

    assertThat(expr)
        .isEqualTo(
            Expr.newBuilder()
                .setId(1)
                .setCallExpr(
                    Call.newBuilder()
                        .setTarget(newInt64ConstantExpr(2, 10))
                        .setFunction("func")
                        .addArgs(newInt64ConstantExpr(3, 20))
                        .build())
                .build());
  }

  @Test
  public void convertCelList_toExprList() {
    CelExpr celExpr =
        CelExpr.ofCreateListExpr(
            1,
            ImmutableList.of(
                CelExpr.ofConstantExpr(2, CelConstant.ofValue(10)),
                CelExpr.ofConstantExpr(3, CelConstant.ofValue(15))),
            ImmutableList.of(1));

    Expr expr = CelExprV1Alpha1Converter.fromCelExpr(celExpr);

    assertThat(expr)
        .isEqualTo(
            Expr.newBuilder()
                .setId(1)
                .setListExpr(
                    CreateList.newBuilder()
                        .addElements(newInt64ConstantExpr(2, 10))
                        .addElements(newInt64ConstantExpr(3, 15))
                        .addOptionalIndices(1)
                        .build())
                .build());
  }

  @Test
  public void convertCelStructExpr_toExprStruct_withFieldKey() {
    CelExpr celExpr =
        CelExpr.ofCreateStructExpr(
            1,
            "messageName",
            ImmutableList.of(
                CelExpr.ofCreateStructFieldEntryExpr(
                    2, "fieldKey", CelExpr.ofConstantExpr(3, CelConstant.ofValue(10)), true)));

    Expr expr = CelExprV1Alpha1Converter.fromCelExpr(celExpr);

    assertThat(expr)
        .isEqualTo(
            Expr.newBuilder()
                .setId(1)
                .setStructExpr(
                    CreateStruct.newBuilder()
                        .setMessageName("messageName")
                        .addEntries(
                            CreateStruct.Entry.newBuilder()
                                .setId(2)
                                .setFieldKey("fieldKey")
                                .setValue(newInt64ConstantExpr(3, 10))
                                .setOptionalEntry(true)
                                .build())
                        .build())
                .build());
  }

  @Test
  public void convertCelStructExpr_toExprStruct_withMapKey() {
    CelExpr celExpr =
        CelExpr.ofCreateStructExpr(
            1,
            "messageName",
            ImmutableList.of(
                CelExpr.ofCreateStructMapEntryExpr(
                    2,
                    CelExpr.ofConstantExpr(3, CelConstant.ofValue(15)),
                    CelExpr.ofConstantExpr(4, CelConstant.ofValue(10)),
                    true)));

    Expr expr = CelExprV1Alpha1Converter.fromCelExpr(celExpr);

    assertThat(expr)
        .isEqualTo(
            Expr.newBuilder()
                .setId(1)
                .setStructExpr(
                    CreateStruct.newBuilder()
                        .setMessageName("messageName")
                        .addEntries(
                            CreateStruct.Entry.newBuilder()
                                .setId(2)
                                .setMapKey(newInt64ConstantExpr(3, 15))
                                .setValue(newInt64ConstantExpr(4, 10))
                                .setOptionalEntry(true)
                                .build())
                        .build())
                .build());
  }

  @Test
  public void convertCelComprehensionExpr_toExprComprehension() {
    CelExpr celExpr =
        CelExpr.ofComprehension(
            1,
            "iterVar",
            CelExpr.ofConstantExpr(2, CelConstant.ofValue(10)),
            "accuVar",
            CelExpr.ofConstantExpr(3, CelConstant.ofValue(20)),
            CelExpr.ofCallExpr(4, Optional.empty(), "testCondition", ImmutableList.of()),
            CelExpr.ofCallExpr(5, Optional.empty(), "testStep", ImmutableList.of()),
            CelExpr.ofConstantExpr(6, CelConstant.ofValue(30)));

    Expr expr = CelExprV1Alpha1Converter.fromCelExpr(celExpr);

    assertThat(expr)
        .isEqualTo(
            Expr.newBuilder()
                .setId(1)
                .setComprehensionExpr(
                    Comprehension.newBuilder()
                        .setIterVar("iterVar")
                        .setIterRange(newInt64ConstantExpr(2, 10))
                        .setAccuVar("accuVar")
                        .setAccuInit(newInt64ConstantExpr(3, 20))
                        .setLoopCondition(
                            Expr.newBuilder()
                                .setId(4)
                                .setCallExpr(Call.newBuilder().setFunction("testCondition").build())
                                .build())
                        .setLoopStep(
                            Expr.newBuilder()
                                .setId(5)
                                .setCallExpr(Call.newBuilder().setFunction("testStep").build())
                                .build())
                        .setResult(newInt64ConstantExpr(6, 30))
                        .build())
                .build());
  }

  @Test
  public void convertCelReference_toExprReference_withOverloadIds() {
    CelReference celReference =
        CelReference.newBuilder().setName("ref").addOverloadIds(ImmutableList.of("a", "b")).build();

    Reference reference = CelExprV1Alpha1Converter.celReferenceToExprReference(celReference);

    assertThat(reference)
        .isEqualTo(
            Reference.newBuilder()
                .setName("ref")
                .addAllOverloadId(ImmutableList.of("a", "b"))
                .build());
  }

  @Test
  public void convertCelReference_toExprReference_withValue() {
    CelReference celReference =
        CelReference.newBuilder().setName("ref").setValue(CelConstant.ofValue(1)).build();

    Reference reference = CelExprV1Alpha1Converter.celReferenceToExprReference(celReference);

    assertThat(reference)
        .isEqualTo(
            Reference.newBuilder()
                .setName("ref")
                .setValue(Constant.newBuilder().setInt64Value(1).build())
                .build());
  }

  private Expr newInt64ConstantExpr(long id, long value) {
    return Expr.newBuilder()
        .setId(id)
        .setConstExpr(Constant.newBuilder().setInt64Value(value).build())
        .build();
  }
}
// LINT.ThenChange(CelExprConverterTest.java)
