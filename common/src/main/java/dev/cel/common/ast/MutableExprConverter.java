package dev.cel.common.ast;

import com.google.common.collect.ImmutableList;
import dev.cel.common.ast.CelExpr.CelCall;
import dev.cel.common.ast.CelExpr.CelComprehension;
import dev.cel.common.ast.CelExpr.CelCreateList;
import dev.cel.common.ast.CelExpr.CelCreateMap;
import dev.cel.common.ast.CelExpr.CelCreateStruct;
import dev.cel.common.ast.CelExpr.CelSelect;
import dev.cel.common.ast.MutableExpr.MutableCall;
import dev.cel.common.ast.MutableExpr.MutableComprehension;
import dev.cel.common.ast.MutableExpr.MutableCreateList;
import dev.cel.common.ast.MutableExpr.MutableCreateMap;
import dev.cel.common.ast.MutableExpr.MutableCreateStruct;
import dev.cel.common.ast.MutableExpr.MutableSelect;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.google.common.collect.ImmutableList.toImmutableList;

public final class MutableExprConverter {


  public static MutableExpr fromCelExpr(CelExpr celExpr) {
    CelExpr.ExprKind celExprKind = celExpr.exprKind();
    switch (celExprKind.getKind()) {
      case CONSTANT:
        return MutableExpr.ofConstant(celExpr.id(), celExpr.constant());
      case IDENT:
        return MutableExpr.ofIdent(celExpr.id(), celExpr.ident().name());
      case SELECT:
        CelSelect select = celExpr.select();
        MutableExpr operand = fromCelExpr(select.operand());
        return MutableExpr.ofSelect(celExpr.id(),
                MutableSelect.create(operand, select.field(), select.testOnly()));
      case CALL:
        CelCall celCall = celExprKind.call();
        List<MutableExpr> args = celCall.args().stream()
                .map(MutableExprConverter::fromCelExpr)
                .collect(Collectors.toList());
        MutableCall mutableCall = celCall.target().isPresent() ?
                MutableCall.create(fromCelExpr(celCall.target().get()), celCall.function(), args) :
                MutableCall.create(celCall.function(), args);

        return MutableExpr.ofCall(celExpr.id(), mutableCall);
      case CREATE_LIST:
        CelCreateList createList = celExpr.createList();
        return MutableExpr.ofCreateList(celExpr.id(),
                MutableCreateList.create(fromCelExprList(createList.elements()), createList.optionalIndices())
        );
      case CREATE_STRUCT:
        return MutableExpr.ofCreateStruct(celExpr.id(), fromCelStructToMutableStruct(celExpr.createStruct()));
      case CREATE_MAP:
        return MutableExpr.ofCreateMap(celExpr.id(), fromCelMapToMutableMap(celExpr.createMap()));
      case COMPREHENSION:
        CelComprehension celComprehension = celExprKind.comprehension();
        MutableComprehension mutableComprehension = MutableComprehension.create(
                celComprehension.iterVar(),
                fromCelExpr(celComprehension.iterRange()),
                celComprehension.accuVar(),
                fromCelExpr(celComprehension.accuInit()),
                fromCelExpr(celComprehension.loopCondition()),
                fromCelExpr(celComprehension.loopStep()),
                fromCelExpr(celComprehension.result())
        );
        return MutableExpr.ofComprehension(celExpr.id(), mutableComprehension);
      case NOT_SET:
        return MutableExpr.ofNotSet(celExpr.id());
      default:
        throw new IllegalArgumentException(
                "Unexpected expression kind case: " + celExpr.exprKind().getKind());
    }
  }

  private static List<MutableExpr> fromCelExprList(Iterable<CelExpr> celExprList) {
    ArrayList<MutableExpr> mutableExprList = new ArrayList<>();
    for (CelExpr celExpr : celExprList) {
      mutableExprList.add(fromCelExpr(celExpr));
    }
    return mutableExprList;
  }

  private static MutableCreateStruct fromCelStructToMutableStruct(CelCreateStruct celCreateStruct) {
    List<MutableCreateStruct.Entry> entries = new ArrayList<>();
    for (CelCreateStruct.Entry celStructExprEntry : celCreateStruct.entries()) {
      entries.add(
          MutableCreateStruct.Entry.create(
              celStructExprEntry.id(),
              celStructExprEntry.fieldKey(),
              fromCelExpr(celStructExprEntry.value()),
              celStructExprEntry.optionalEntry())
      );
    }

    return MutableCreateStruct.create(celCreateStruct.messageName(), entries);
  }


  private static MutableCreateMap fromCelMapToMutableMap(CelCreateMap celCreateMap) {
    List<MutableCreateMap.Entry> entries = new ArrayList<MutableCreateMap.Entry>();
    for (CelCreateMap.Entry celMapExprEntry : celCreateMap.entries()) {
      entries.add(
          MutableCreateMap.Entry.create(
              celMapExprEntry.id(),
              fromCelExpr(celMapExprEntry.key()),
              fromCelExpr(celMapExprEntry.value()),
              celMapExprEntry.optionalEntry())
      );
    }

    return MutableCreateMap.create(entries);
  }



  ///////////////////////


  public static CelExpr fromMutableExpr(MutableExpr mutableExpr) {
    long id = mutableExpr.id();
    switch (mutableExpr.exprKind()) {
      case CONSTANT:
        return CelExpr.ofConstantExpr(id, mutableExpr.constant());
      case IDENT:
        return CelExpr.ofIdentExpr(id, mutableExpr.ident().name());
      case SELECT:
        MutableSelect select = mutableExpr.select();
        CelExpr operand = fromMutableExpr(select.operand());
        return CelExpr.ofSelectExpr(id, operand, select.field(), select.isTestOnly());
      case CALL:
        MutableCall mutableCall = mutableExpr.call();
        ImmutableList<CelExpr> args = mutableCall.args().stream()
                .map(MutableExprConverter::fromMutableExpr)
                .collect(toImmutableList());
        Optional<CelExpr> targetExpr = mutableCall.target().map(MutableExprConverter::fromMutableExpr);
        return CelExpr.ofCallExpr(id, targetExpr, mutableCall.function(), args);
      case CREATE_LIST:
        MutableCreateList mutableCreateList = mutableExpr.createList();
        return CelExpr.ofCreateListExpr(id, fromMutableExprList(mutableCreateList.elements()), ImmutableList.copyOf(mutableCreateList.optionalIndices()));
      case CREATE_STRUCT:
        MutableCreateStruct mutableCreateStruct = mutableExpr.createStruct();
        return CelExpr.newBuilder().setId(id).setCreateStruct(fromMutableStructToCelStruct(mutableCreateStruct)).build();
      case CREATE_MAP:
        MutableCreateMap mutableCreateMap = mutableExpr.createMap();
        return CelExpr.newBuilder().setId(id).setCreateMap(fromMutableMapToCelMap(mutableCreateMap)).build();
      case COMPREHENSION:
        MutableComprehension mutableComprehension = mutableExpr.comprehension();
        return CelExpr.ofComprehension(
                id,
                mutableComprehension.iterVar(),
                fromMutableExpr(mutableComprehension.iterRange()),
                mutableComprehension.accuVar(),
                fromMutableExpr(mutableComprehension.accuInit()),
                fromMutableExpr(mutableComprehension.loopCondition()),
                fromMutableExpr(mutableComprehension.loopStep()),
                fromMutableExpr(mutableComprehension.result())
        );
      case NOT_SET:
        return CelExpr.ofNotSet(id);
      default:
        throw new IllegalArgumentException(
                "Unexpected expression kind case: " + mutableExpr.exprKind());
    }
  }

  private static ImmutableList<CelExpr> fromMutableExprList(Iterable<MutableExpr> mutableExprList) {
    ImmutableList.Builder<CelExpr> celExprList = ImmutableList.builder();
    for (MutableExpr mutableExpr : mutableExprList) {
      celExprList.add(fromMutableExpr(mutableExpr));
    }
    return celExprList.build();
  }

  private static CelCreateStruct fromMutableStructToCelStruct(MutableCreateStruct mutableCreateStruct) {
    List<CelCreateStruct.Entry> entries = new ArrayList<>();
    for (MutableCreateStruct.Entry mutableStructEntry : mutableCreateStruct.entries()) {
      entries.add(
              CelExpr.ofCreateStructEntryExpr(
                      mutableStructEntry.id(),
                      mutableStructEntry.fieldKey(),
                      fromMutableExpr(mutableStructEntry.value()),
                      mutableStructEntry.optionalEntry()));
    }

    return CelCreateStruct.newBuilder().setMessageName(mutableCreateStruct.messageName()).addEntries(entries).build();
  }

  private static CelCreateMap fromMutableMapToCelMap(MutableCreateMap mutableCreateMap) {
    List<CelCreateMap.Entry> entries = new ArrayList<>();
    for (MutableCreateMap.Entry mutableMapEntry : mutableCreateMap.entries()) {
      entries.add(
              CelExpr.ofCreateMapEntryExpr(
                      mutableMapEntry.id(),
                      fromMutableExpr(mutableMapEntry.key()),
                      fromMutableExpr(mutableMapEntry.value()),
                      mutableMapEntry.optionalEntry())
      );
    }

    return CelCreateMap.newBuilder().addEntries(entries).build();
  }

  private MutableExprConverter() {}
}
