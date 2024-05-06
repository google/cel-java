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

import static com.google.common.collect.ImmutableMap.toImmutableMap;

import dev.cel.expr.Constant;
import dev.cel.expr.Expr;
import dev.cel.expr.Expr.Call;
import dev.cel.expr.Expr.Comprehension;
import dev.cel.expr.Expr.CreateList;
import dev.cel.expr.Expr.CreateStruct;
import dev.cel.expr.Expr.CreateStruct.Entry;
import dev.cel.expr.Expr.CreateStruct.Entry.KeyKindCase;
import dev.cel.expr.Expr.Ident;
import dev.cel.expr.Expr.Select;
import dev.cel.expr.Reference;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.primitives.UnsignedLong;
import java.util.Map;
import java.util.Optional;

/**
 * Converts a proto-based Canonical Expression Tree {@link Expr} into the CEL native representation
 * of Expression tree {@link CelExpr} and vice versa.
 *
 * <p>Note: Keep this file in sync with {@code CelExprV1Alpha1Converter}
 */
// LINT.IfChange
public final class CelExprConverter {
  private CelExprConverter() {}

  /** Convert {@link CelExpr} into {@link Expr} */
  public static Expr fromCelExpr(CelExpr celExpr) {
    CelExpr.ExprKind celExprKind = celExpr.exprKind();
    Expr.Builder expr = newExprBuilder(celExpr);
    switch (celExprKind.getKind()) {
      case CONSTANT:
        return expr.setConstExpr(celConstantToExprConstant(celExprKind.constant())).build();
      case IDENT:
        return expr.setIdentExpr(Ident.newBuilder().setName(celExprKind.ident().name())).build();
      case SELECT:
        CelExpr.CelSelect celSelect = celExprKind.select();
        return expr.setSelectExpr(
                Select.newBuilder()
                    .setField(celSelect.field())
                    .setOperand(fromCelExpr(celSelect.operand()))
                    .setTestOnly(celSelect.testOnly()))
            .build();
      case CALL:
        CelExpr.CelCall celCall = celExprKind.call();
        Call.Builder callBuilder =
            Call.newBuilder()
                .setFunction(celCall.function())
                .addAllArgs(fromCelExprList(celCall.args()));
        celCall.target().ifPresent(target -> callBuilder.setTarget(fromCelExpr(target)));
        return expr.setCallExpr(callBuilder).build();
      case LIST:
        CelExpr.CelList celCreateList = celExprKind.list();
        return expr.setListExpr(
                CreateList.newBuilder()
                    .addAllElements(fromCelExprList(celCreateList.elements()))
                    .addAllOptionalIndices(celCreateList.optionalIndices()))
            .build();
      case STRUCT:
        return expr.setStructExpr(celStructToExprStruct(celExprKind.struct())).build();
      case MAP:
        return expr.setStructExpr(celMapToExprStruct(celExprKind.map())).build();
      case COMPREHENSION:
        CelExpr.CelComprehension celComprehension = celExprKind.comprehension();
        return expr.setComprehensionExpr(
                Comprehension.newBuilder()
                    .setIterVar(celComprehension.iterVar())
                    .setIterRange(fromCelExpr(celComprehension.iterRange()))
                    .setAccuVar(celComprehension.accuVar())
                    .setAccuInit(fromCelExpr(celComprehension.accuInit()))
                    .setLoopCondition(fromCelExpr(celComprehension.loopCondition()))
                    .setLoopStep(fromCelExpr(celComprehension.loopStep()))
                    .setResult(fromCelExpr(celComprehension.result())))
            .build();
      case NOT_SET:
        return expr.build();
    }
    throw new IllegalArgumentException(
        "Unexpected expression kind case: " + celExpr.exprKind().getKind());
  }

  /** Convert {@link Expr} into {@link CelExpr} */
  public static CelExpr fromExpr(Expr expr) {
    switch (expr.getExprKindCase()) {
      case CONST_EXPR:
        return CelExpr.ofConstant(expr.getId(), exprConstantToCelConstant(expr.getConstExpr()));
      case IDENT_EXPR:
        return CelExpr.ofIdent(expr.getId(), expr.getIdentExpr().getName());
      case SELECT_EXPR:
        Select selectExpr = expr.getSelectExpr();
        return CelExpr.ofSelect(
            expr.getId(),
            fromExpr(selectExpr.getOperand()),
            selectExpr.getField(),
            selectExpr.getTestOnly());
      case CALL_EXPR:
        Call callExpr = expr.getCallExpr();
        return CelExpr.ofCall(
            expr.getId(),
            callExpr.hasTarget() ? Optional.of(fromExpr(callExpr.getTarget())) : Optional.empty(),
            callExpr.getFunction(),
            fromExprList(callExpr.getArgsList()));
      case LIST_EXPR:
        CreateList createListExpr = expr.getListExpr();
        return CelExpr.ofList(
            expr.getId(),
            fromExprList(createListExpr.getElementsList()),
            ImmutableList.copyOf(createListExpr.getOptionalIndicesList()));
      case STRUCT_EXPR:
        return exprStructToCelStruct(expr.getId(), expr.getStructExpr());
      case COMPREHENSION_EXPR:
        Comprehension comprehensionExpr = expr.getComprehensionExpr();
        return CelExpr.ofComprehension(
            expr.getId(),
            comprehensionExpr.getIterVar(),
            fromExpr(comprehensionExpr.getIterRange()),
            comprehensionExpr.getAccuVar(),
            fromExpr(comprehensionExpr.getAccuInit()),
            fromExpr(comprehensionExpr.getLoopCondition()),
            fromExpr(comprehensionExpr.getLoopStep()),
            fromExpr(comprehensionExpr.getResult()));
      case EXPRKIND_NOT_SET:
        return CelExpr.ofNotSet(expr.getId());
    }

    throw new IllegalArgumentException(
        "Unexpected expression kind case: " + expr.getExprKindCase());
  }

  private static ImmutableList<CelExpr> fromExprList(Iterable<Expr> exprList) {
    ImmutableList.Builder<CelExpr> celExprListBuilder = ImmutableList.builder();
    for (Expr expr : exprList) {
      celExprListBuilder.add(fromExpr(expr));
    }
    return celExprListBuilder.build();
  }

  /**
   * Converts a proto-based {@link Reference} to CEL native representation of {@link CelReference}.
   */
  public static CelReference exprReferenceToCelReference(Reference reference) {
    CelReference.Builder builder =
        CelReference.newBuilder()
            .setName(reference.getName())
            .addOverloadIds(reference.getOverloadIdList());
    if (reference.hasValue()) {
      builder.setValue(exprConstantToCelConstant(reference.getValue()));
    }

    return builder.build();
  }

  /**
   * Converts a proto-based {@link Constant} to CEL native representation of {@link CelConstant}.
   */
  public static CelConstant exprConstantToCelConstant(Constant constExpr) {
    switch (constExpr.getConstantKindCase()) {
      case CONSTANTKIND_NOT_SET:
        return CelConstant.ofNotSet();
      case NULL_VALUE:
        return CelConstant.ofValue(constExpr.getNullValue());
      case BOOL_VALUE:
        return CelConstant.ofValue(constExpr.getBoolValue());
      case INT64_VALUE:
        return CelConstant.ofValue(constExpr.getInt64Value());
      case UINT64_VALUE:
        return CelConstant.ofValue(UnsignedLong.fromLongBits(constExpr.getUint64Value()));
      case DOUBLE_VALUE:
        return CelConstant.ofValue(constExpr.getDoubleValue());
      case STRING_VALUE:
        return CelConstant.ofValue(constExpr.getStringValue());
      case BYTES_VALUE:
        return CelConstant.ofValue(constExpr.getBytesValue());
      case DURATION_VALUE:
        return CelConstant.ofValue(constExpr.getDurationValue());
      case TIMESTAMP_VALUE:
        return CelConstant.ofValue(constExpr.getTimestampValue());
      default:
        throw new IllegalStateException(
            "unsupported constant case: " + constExpr.getConstantKindCase());
    }
  }

  private static CelExpr exprStructToCelStruct(long id, CreateStruct structExpr) {
    if (!structExpr.getMessageName().isEmpty()) {
      ImmutableList.Builder<CelExpr.CelStruct.Entry> entries = ImmutableList.builder();
      for (Entry structExprEntry : structExpr.getEntriesList()) {
        if (!structExprEntry.getKeyKindCase().equals(KeyKindCase.FIELD_KEY)) {
          throw new IllegalArgumentException(
              "Unexpected struct key kind case: " + structExprEntry.getKeyKindCase());
        }
        entries.add(
            CelExpr.ofStructEntry(
                structExprEntry.getId(),
                structExprEntry.getFieldKey(),
                fromExpr(structExprEntry.getValue()),
                structExprEntry.getOptionalEntry()));
      }

      return CelExpr.ofStruct(id, structExpr.getMessageName(), entries.build());
    } else {
      ImmutableList.Builder<CelExpr.CelMap.Entry> entries = ImmutableList.builder();
      for (Entry mapExprEntry : structExpr.getEntriesList()) {
        if (!mapExprEntry.getKeyKindCase().equals(KeyKindCase.MAP_KEY)) {
          throw new IllegalArgumentException(
              "Unexpected map key kind case: " + mapExprEntry.getKeyKindCase());
        }
        entries.add(
            CelExpr.ofMapEntry(
                mapExprEntry.getId(),
                fromExpr(mapExprEntry.getMapKey()),
                fromExpr(mapExprEntry.getValue()),
                mapExprEntry.getOptionalEntry()));
      }

      return CelExpr.ofMap(id, entries.build());
    }
  }

  private static Expr.Builder newExprBuilder(CelExpr expr) {
    return Expr.newBuilder().setId(expr.id());
  }

  /**
   * Converts a proto-based {@link Constant} to CEL native representation of {@link CelConstant}.
   */
  public static Constant celConstantToExprConstant(CelConstant celConstant) {
    switch (celConstant.getKind()) {
      case NOT_SET:
        return Constant.getDefaultInstance();
      case NULL_VALUE:
        return Constant.newBuilder().setNullValue(celConstant.nullValue()).build();
      case BOOLEAN_VALUE:
        return Constant.newBuilder().setBoolValue(celConstant.booleanValue()).build();
      case INT64_VALUE:
        return Constant.newBuilder().setInt64Value(celConstant.int64Value()).build();
      case UINT64_VALUE:
        return Constant.newBuilder().setUint64Value(celConstant.uint64Value().longValue()).build();
      case DOUBLE_VALUE:
        return Constant.newBuilder().setDoubleValue(celConstant.doubleValue()).build();
      case STRING_VALUE:
        return Constant.newBuilder().setStringValue(celConstant.stringValue()).build();
      case BYTES_VALUE:
        return Constant.newBuilder().setBytesValue(celConstant.bytesValue()).build();
      case DURATION_VALUE:
        return Constant.newBuilder().setDurationValue(celConstant.durationValue()).build();
      case TIMESTAMP_VALUE:
        return Constant.newBuilder().setTimestampValue(celConstant.timestampValue()).build();
    }

    throw new IllegalStateException("unsupported constant case: " + celConstant.getKind());
  }

  private static CreateStruct celStructToExprStruct(CelExpr.CelStruct celCreateStruct) {
    ImmutableList.Builder<CreateStruct.Entry> entries = ImmutableList.builder();
    for (CelExpr.CelStruct.Entry celStructExprEntry : celCreateStruct.entries()) {
      entries.add(
          CreateStruct.Entry.newBuilder()
              .setId(celStructExprEntry.id())
              .setFieldKey(celStructExprEntry.fieldKey())
              .setValue(fromCelExpr(celStructExprEntry.value()))
              .setOptionalEntry(celStructExprEntry.optionalEntry())
              .build());
    }

    return CreateStruct.newBuilder()
        .setMessageName(celCreateStruct.messageName())
        .addAllEntries(entries.build())
        .build();
  }

  private static CreateStruct celMapToExprStruct(CelExpr.CelMap celCreateMap) {
    ImmutableList.Builder<CreateStruct.Entry> entries = ImmutableList.builder();
    for (CelExpr.CelMap.Entry celMapEntry : celCreateMap.entries()) {
      CreateStruct.Entry exprMapEntry =
          CreateStruct.Entry.newBuilder()
              .setId(celMapEntry.id())
              .setMapKey(fromCelExpr(celMapEntry.key()))
              .setValue(fromCelExpr(celMapEntry.value()))
              .setOptionalEntry(celMapEntry.optionalEntry())
              .build();

      entries.add(exprMapEntry);
    }

    return CreateStruct.newBuilder().addAllEntries(entries.build()).build();
  }

  private static ImmutableList<Expr> fromCelExprList(Iterable<CelExpr> celExprList) {
    ImmutableList.Builder<Expr> celExprListBuilder = ImmutableList.builder();
    for (CelExpr celExpr : celExprList) {
      celExprListBuilder.add(fromCelExpr(celExpr));
    }
    return celExprListBuilder.build();
  }

  /**
   * Converts a proto-based {@link CelReference} to CEL native representation of {@link Reference}.
   */
  public static Reference celReferenceToExprReference(CelReference reference) {
    Reference.Builder builder =
        Reference.newBuilder().setName(reference.name()).addAllOverloadId(reference.overloadIds());
    reference
        .value()
        .ifPresent(celConstant -> builder.setValue(celConstantToExprConstant(celConstant)));

    return builder.build();
  }

  public static ImmutableMap<Long, CelExpr> exprMacroCallsToCelExprMacroCalls(
      Map<Long, Expr> macroCalls) {
    return macroCalls.entrySet().stream()
        .collect(toImmutableMap(Map.Entry::getKey, v -> fromExpr(v.getValue())));
  }
}
// LINT.ThenChange(CelExprV1Alpha1Converter.java)
