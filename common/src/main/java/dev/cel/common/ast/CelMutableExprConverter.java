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
import dev.cel.common.ast.CelExpr.CelList;
import dev.cel.common.ast.CelExpr.CelMap;
import dev.cel.common.ast.CelExpr.CelSelect;
import dev.cel.common.ast.CelExpr.CelStruct;
import dev.cel.common.ast.CelMutableExpr.CelMutableCall;
import dev.cel.common.ast.CelMutableExpr.CelMutableComprehension;
import dev.cel.common.ast.CelMutableExpr.CelMutableList;
import dev.cel.common.ast.CelMutableExpr.CelMutableMap;
import dev.cel.common.ast.CelMutableExpr.CelMutableSelect;
import dev.cel.common.ast.CelMutableExpr.CelMutableStruct;
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
      case LIST:
        CelList createList = celExpr.createList();
        return CelMutableExpr.ofCreateList(
            celExpr.id(),
            CelMutableList.create(
                fromCelExprList(createList.elements()), createList.optionalIndices()));
      case STRUCT:
        return CelMutableExpr.ofCreateStruct(
            celExpr.id(), fromCelStructToMutableStruct(celExpr.createStruct()));
      case MAP:
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

  private static CelMutableStruct fromCelStructToMutableStruct(CelStruct celCreateStruct) {
    List<CelMutableStruct.Entry> entries = new ArrayList<>();
    for (CelStruct.Entry celStructExprEntry : celCreateStruct.entries()) {
      entries.add(
          CelMutableStruct.Entry.create(
              celStructExprEntry.id(),
              celStructExprEntry.fieldKey(),
              fromCelExpr(celStructExprEntry.value()),
              celStructExprEntry.optionalEntry()));
    }

    return CelMutableStruct.create(celCreateStruct.messageName(), entries);
  }

  private static CelMutableMap fromCelMapToMutableMap(CelMap celCreateMap) {
    List<CelMutableMap.Entry> entries = new ArrayList<>();
    for (CelMap.Entry celMapExprEntry : celCreateMap.entries()) {
      entries.add(
          CelMutableMap.Entry.create(
              celMapExprEntry.id(),
              fromCelExpr(celMapExprEntry.key()),
              fromCelExpr(celMapExprEntry.value()),
              celMapExprEntry.optionalEntry()));
    }

    return CelMutableMap.create(entries);
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
      case LIST:
        CelMutableList mutableCreateList = mutableExpr.createList();
        return CelExpr.ofCreateList(
            id,
            fromMutableExprList(mutableCreateList.elements()),
            ImmutableList.copyOf(mutableCreateList.optionalIndices()));
      case STRUCT:
        CelMutableStruct mutableCreateStruct = mutableExpr.createStruct();
        return CelExpr.newBuilder()
            .setId(id)
            .setCreateStruct(fromMutableStructToCelStruct(mutableCreateStruct))
            .build();
      case MAP:
        CelMutableMap mutableCreateMap = mutableExpr.createMap();
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

  private static CelStruct fromMutableStructToCelStruct(CelMutableStruct mutableCreateStruct) {
    List<CelStruct.Entry> entries = new ArrayList<>();
    for (CelMutableStruct.Entry mutableStructEntry : mutableCreateStruct.entries()) {
      entries.add(
          CelExpr.ofCreateStructEntry(
              mutableStructEntry.id(),
              mutableStructEntry.fieldKey(),
              fromMutableExpr(mutableStructEntry.value()),
              mutableStructEntry.optionalEntry()));
    }

    return CelStruct.newBuilder()
        .setMessageName(mutableCreateStruct.messageName())
        .addEntries(entries)
        .build();
  }

  private static CelMap fromMutableMapToCelMap(CelMutableMap mutableCreateMap) {
    List<CelMap.Entry> entries = new ArrayList<>();
    for (CelMutableMap.Entry mutableMapEntry : mutableCreateMap.entries()) {
      entries.add(
          CelExpr.ofCreateMapEntry(
              mutableMapEntry.id(),
              fromMutableExpr(mutableMapEntry.key()),
              fromMutableExpr(mutableMapEntry.value()),
              mutableMapEntry.optionalEntry()));
    }

    return CelMap.newBuilder().addEntries(entries).build();
  }

  private CelMutableExprConverter() {}
}
