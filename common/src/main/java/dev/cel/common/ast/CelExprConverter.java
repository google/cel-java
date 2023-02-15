// Copyright 2023 Google LLC
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

import dev.cel.expr.Constant;
import dev.cel.expr.Expr;
import dev.cel.expr.Expr.Call;
import dev.cel.expr.Expr.Comprehension;
import dev.cel.expr.Expr.CreateList;
import dev.cel.expr.Expr.CreateStruct;
import dev.cel.expr.Expr.CreateStruct.Entry;
import dev.cel.expr.Expr.Select;
import dev.cel.expr.Reference;
import com.google.common.collect.ImmutableList;
import com.google.common.primitives.UnsignedLong;
import java.util.Optional;

/**
 * Converts a proto-based Expression Tree {@link Expr} into the CEL native representation of
 * Expression tree {@link CelExpr}.
 */
public final class CelExprConverter {
  private CelExprConverter() {}

  /** Convert {@link Expr} into {@link CelExpr} */
  public static CelExpr fromExpr(Expr expr) {
    switch (expr.getExprKindCase()) {
      case CONST_EXPR:
        return CelExpr.ofConstantExpr(expr.getId(), exprConstantToCelConstant(expr.getConstExpr()));
      case IDENT_EXPR:
        return CelExpr.ofIdentExpr(expr.getId(), expr.getIdentExpr().getName());
      case SELECT_EXPR:
        Select selectExpr = expr.getSelectExpr();
        return CelExpr.ofSelectExpr(
            expr.getId(),
            fromExpr(selectExpr.getOperand()),
            selectExpr.getField(),
            selectExpr.getTestOnly());
      case CALL_EXPR:
        Call callExpr = expr.getCallExpr();
        return CelExpr.ofCallExpr(
            expr.getId(),
            callExpr.hasTarget() ? Optional.of(fromExpr(callExpr.getTarget())) : Optional.empty(),
            callExpr.getFunction(),
            fromExprList(callExpr.getArgsList()));
      case LIST_EXPR:
        CreateList createListExpr = expr.getListExpr();
        return CelExpr.ofCreateListExpr(
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
      builder.setValue(CelExprConverter.exprConstantToCelConstant(reference.getValue()));
    }

    return builder.build();
  }

  /**
   * Converts a proto-based {@link Constant} to CEL native representation of {@link CelConstant}.
   */
  public static CelConstant exprConstantToCelConstant(Constant constExpr) {
    switch (constExpr.getConstantKindCase()) {
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
      default:
        throw new IllegalStateException(
            "unsupported constant case: " + constExpr.getConstantKindCase());
    }
  }

  private static CelExpr exprStructToCelStruct(long id, CreateStruct structExpr) {
    ImmutableList.Builder<CelExpr.CelCreateStruct.Entry> entries = ImmutableList.builder();
    for (Entry structExprEntry : structExpr.getEntriesList()) {
      CelExpr.CelCreateStruct.Entry celStructEntry;

      switch (structExprEntry.getKeyKindCase()) {
        case FIELD_KEY:
          celStructEntry =
              CelExpr.ofCreateStructFieldEntryExpr(
                  structExprEntry.getId(),
                  structExprEntry.getFieldKey(),
                  fromExpr(structExprEntry.getValue()),
                  structExprEntry.getOptionalEntry());
          break;
        case MAP_KEY:
          celStructEntry =
              CelExpr.ofCreateStructMapEntryExpr(
                  structExprEntry.getId(),
                  fromExpr(structExprEntry.getMapKey()),
                  fromExpr(structExprEntry.getValue()),
                  structExprEntry.getOptionalEntry());
          break;
        default:
          throw new IllegalArgumentException(
              "Unexpected struct key kind case: " + structExprEntry.getKeyKindCase());
      }

      entries.add(celStructEntry);
    }
    return CelExpr.ofCreateStructExpr(id, structExpr.getMessageName(), entries.build());
  }
}
