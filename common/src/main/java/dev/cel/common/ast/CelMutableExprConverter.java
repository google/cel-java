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

import static com.google.common.collect.ImmutableList.toImmutableList;
import static java.util.stream.Collectors.toCollection;

import com.google.common.collect.ImmutableList;
import dev.cel.common.ast.CelExpr.CelCall;
import dev.cel.common.ast.CelExpr.CelComprehension;
import dev.cel.common.ast.CelExpr.CelCreateList;
import dev.cel.common.ast.CelExpr.CelCreateMap;
import dev.cel.common.ast.CelExpr.CelCreateStruct;
import dev.cel.common.ast.CelExpr.CelSelect;
import dev.cel.common.ast.CelMutableExpr.CelMutableCall;
import dev.cel.common.ast.CelMutableExpr.CelMutableComprehension;
import dev.cel.common.ast.CelMutableExpr.CelMutableCreateList;
import dev.cel.common.ast.CelMutableExpr.CelMutableCreateMap;
import dev.cel.common.ast.CelMutableExpr.CelMutableCreateStruct;
import dev.cel.common.ast.CelMutableExpr.CelMutableSelect;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Converts a mutable Expression Tree {@link CelMutableExpr} into the CEL native representation of
 * Expression tree {@link CelExpr} and vice versa.
 */
public final class CelMutableExprConverter {

  public static CelMutableExpr fromCelExpr(CelExpr celExpr) {
    CelExpr.ExprKind celExprKind = celExpr.exprKind();
    switch (celExprKind.getKind()) {
      case CONSTANT:
        return CelMutableExpr.ofConstant(celExpr.id(), celExpr.constant());
      case IDENT:
        return CelMutableExpr.ofIdent(celExpr.id(), celExpr.ident().name());
      case SELECT:
        CelSelect select = celExpr.select();
        CelMutableExpr operand = fromCelExpr(select.operand());
        return CelMutableExpr.ofSelect(
            celExpr.id(), CelMutableSelect.create(operand, select.field(), select.testOnly()));
      case CALL:
        CelCall celCall = celExprKind.call();
        List<CelMutableExpr> args =
            celCall.args().stream()
                .map(CelMutableExprConverter::fromCelExpr)
                .collect(toCollection(ArrayList::new));
        CelMutableCall mutableCall =
            celCall.target().isPresent()
                ? CelMutableCall.create(
                    fromCelExpr(celCall.target().get()), celCall.function(), args)
                : CelMutableCall.create(celCall.function(), args);

        return CelMutableExpr.ofCall(celExpr.id(), mutableCall);
      case CREATE_LIST:
        CelCreateList createList = celExpr.createList();
        return CelMutableExpr.ofCreateList(
            celExpr.id(),
            CelMutableCreateList.create(
                fromCelExprList(createList.elements()), createList.optionalIndices()));
      case CREATE_STRUCT:
        return CelMutableExpr.ofCreateStruct(
            celExpr.id(), fromCelStructToMutableStruct(celExpr.createStruct()));
      case CREATE_MAP:
        return CelMutableExpr.ofCreateMap(
            celExpr.id(), fromCelMapToMutableMap(celExpr.createMap()));
      case COMPREHENSION:
        CelComprehension celComprehension = celExprKind.comprehension();
        CelMutableComprehension mutableComprehension =
            CelMutableComprehension.create(
                celComprehension.iterVar(),
                fromCelExpr(celComprehension.iterRange()),
                celComprehension.accuVar(),
                fromCelExpr(celComprehension.accuInit()),
                fromCelExpr(celComprehension.loopCondition()),
                fromCelExpr(celComprehension.loopStep()),
                fromCelExpr(celComprehension.result()));
        return CelMutableExpr.ofComprehension(celExpr.id(), mutableComprehension);
      case NOT_SET:
        return CelMutableExpr.ofNotSet(celExpr.id());
    }

    throw new IllegalArgumentException(
        "Unexpected expression kind case: " + celExpr.exprKind().getKind());
  }

  private static List<CelMutableExpr> fromCelExprList(Iterable<CelExpr> celExprList) {
    ArrayList<CelMutableExpr> mutableExprList = new ArrayList<>();
    for (CelExpr celExpr : celExprList) {
      mutableExprList.add(fromCelExpr(celExpr));
    }
    return mutableExprList;
  }

  private static CelMutableCreateStruct fromCelStructToMutableStruct(
      CelCreateStruct celCreateStruct) {
    List<CelMutableCreateStruct.Entry> entries = new ArrayList<>();
    for (CelCreateStruct.Entry celStructExprEntry : celCreateStruct.entries()) {
      entries.add(
          CelMutableCreateStruct.Entry.create(
              celStructExprEntry.id(),
              celStructExprEntry.fieldKey(),
              fromCelExpr(celStructExprEntry.value()),
              celStructExprEntry.optionalEntry()));
    }

    return CelMutableCreateStruct.create(celCreateStruct.messageName(), entries);
  }

  private static CelMutableCreateMap fromCelMapToMutableMap(CelCreateMap celCreateMap) {
    List<CelMutableCreateMap.Entry> entries = new ArrayList<>();
    for (CelCreateMap.Entry celMapExprEntry : celCreateMap.entries()) {
      entries.add(
          CelMutableCreateMap.Entry.create(
              celMapExprEntry.id(),
              fromCelExpr(celMapExprEntry.key()),
              fromCelExpr(celMapExprEntry.value()),
              celMapExprEntry.optionalEntry()));
    }

    return CelMutableCreateMap.create(entries);
  }

  public static CelExpr fromMutableExpr(CelMutableExpr mutableExpr) {
    long id = mutableExpr.id();
    switch (mutableExpr.getKind()) {
      case CONSTANT:
        return CelExpr.ofConstant(id, mutableExpr.constant());
      case IDENT:
        return CelExpr.ofIdent(id, mutableExpr.ident().name());
      case SELECT:
        CelMutableSelect select = mutableExpr.select();
        CelExpr operand = fromMutableExpr(select.operand());
        return CelExpr.ofSelect(id, operand, select.field(), select.testOnly());
      case CALL:
        CelMutableCall mutableCall = mutableExpr.call();
        ImmutableList<CelExpr> args =
            mutableCall.args().stream()
                .map(CelMutableExprConverter::fromMutableExpr)
                .collect(toImmutableList());
        Optional<CelExpr> targetExpr =
            mutableCall.target().map(CelMutableExprConverter::fromMutableExpr);
        return CelExpr.ofCall(id, targetExpr, mutableCall.function(), args);
      case CREATE_LIST:
        CelMutableCreateList mutableCreateList = mutableExpr.createList();
        return CelExpr.ofCreateList(
            id,
            fromMutableExprList(mutableCreateList.elements()),
            ImmutableList.copyOf(mutableCreateList.optionalIndices()));
      case CREATE_STRUCT:
        CelMutableCreateStruct mutableCreateStruct = mutableExpr.createStruct();
        return CelExpr.newBuilder()
            .setId(id)
            .setCreateStruct(fromMutableStructToCelStruct(mutableCreateStruct))
            .build();
      case CREATE_MAP:
        CelMutableCreateMap mutableCreateMap = mutableExpr.createMap();
        return CelExpr.newBuilder()
            .setId(id)
            .setCreateMap(fromMutableMapToCelMap(mutableCreateMap))
            .build();
      case COMPREHENSION:
        CelMutableComprehension mutableComprehension = mutableExpr.comprehension();
        return CelExpr.ofComprehension(
            id,
            mutableComprehension.iterVar(),
            fromMutableExpr(mutableComprehension.iterRange()),
            mutableComprehension.accuVar(),
            fromMutableExpr(mutableComprehension.accuInit()),
            fromMutableExpr(mutableComprehension.loopCondition()),
            fromMutableExpr(mutableComprehension.loopStep()),
            fromMutableExpr(mutableComprehension.result()));
      case NOT_SET:
        return CelExpr.ofNotSet(id);
    }

    throw new IllegalArgumentException("Unexpected expression kind case: " + mutableExpr.getKind());
  }

  private static ImmutableList<CelExpr> fromMutableExprList(
      Iterable<CelMutableExpr> mutableExprList) {
    ImmutableList.Builder<CelExpr> celExprList = ImmutableList.builder();
    for (CelMutableExpr mutableExpr : mutableExprList) {
      celExprList.add(fromMutableExpr(mutableExpr));
    }
    return celExprList.build();
  }

  private static CelCreateStruct fromMutableStructToCelStruct(
      CelMutableCreateStruct mutableCreateStruct) {
    List<CelCreateStruct.Entry> entries = new ArrayList<>();
    for (CelMutableCreateStruct.Entry mutableStructEntry : mutableCreateStruct.entries()) {
      entries.add(
          CelExpr.ofCreateStructEntry(
              mutableStructEntry.id(),
              mutableStructEntry.fieldKey(),
              fromMutableExpr(mutableStructEntry.value()),
              mutableStructEntry.optionalEntry()));
    }

    return CelCreateStruct.newBuilder()
        .setMessageName(mutableCreateStruct.messageName())
        .addEntries(entries)
        .build();
  }

  private static CelCreateMap fromMutableMapToCelMap(CelMutableCreateMap mutableCreateMap) {
    List<CelCreateMap.Entry> entries = new ArrayList<>();
    for (CelMutableCreateMap.Entry mutableMapEntry : mutableCreateMap.entries()) {
      entries.add(
          CelExpr.ofCreateMapEntry(
              mutableMapEntry.id(),
              fromMutableExpr(mutableMapEntry.key()),
              fromMutableExpr(mutableMapEntry.value()),
              mutableMapEntry.optionalEntry()));
    }

    return CelCreateMap.newBuilder().addEntries(entries).build();
  }

  private CelMutableExprConverter() {}
}
