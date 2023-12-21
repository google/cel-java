package dev.cel.common.navigation;

import dev.cel.common.ast.CelConstant;
import dev.cel.common.ast.CelExpr;
import dev.cel.common.ast.CelExpr.CelCall;
import dev.cel.common.ast.CelExpr.CelComprehension;
import dev.cel.common.ast.CelExpr.CelCreateMap;
import dev.cel.common.ast.CelExpr.CelCreateStruct;
import dev.cel.common.ast.CelExpr.CelSelect;
import dev.cel.common.navigation.MutableExpr.MutableCall;
import dev.cel.common.navigation.MutableExpr.MutableComprehension;
import dev.cel.common.navigation.MutableExpr.MutableConstant;
import dev.cel.common.navigation.MutableExpr.MutableCreateList;
import dev.cel.common.navigation.MutableExpr.MutableCreateMap;
import dev.cel.common.navigation.MutableExpr.MutableCreateStruct;
import dev.cel.common.navigation.MutableExpr.MutableIdent;
import dev.cel.common.navigation.MutableExpr.MutableSelect;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public final class MutableExprConverter {

  private static MutableConstant fromCelConstant(CelConstant celConstant) {
    switch (celConstant.getKind()) {
      case NULL_VALUE:
        return new MutableConstant(celConstant.nullValue());
      case BOOLEAN_VALUE:
        return new MutableConstant(celConstant.booleanValue());
      case INT64_VALUE:
        return new MutableConstant(celConstant.int64Value());
      case UINT64_VALUE:
        return new MutableConstant(celConstant.uint64Value());
      case DOUBLE_VALUE:
        return new MutableConstant(celConstant.doubleValue());
      case STRING_VALUE:
        return new MutableConstant(celConstant.stringValue());
      case BYTES_VALUE:
        return new MutableConstant(celConstant.bytesValue());
      default:
        throw new UnsupportedOperationException("Unsupported constant kind: " + celConstant.getKind());
    }
  }

  static MutableExpr fromCelExpr(CelExpr celExpr) {
    CelExpr.ExprKind celExprKind = celExpr.exprKind();
    switch (celExprKind.getKind()) {
      case CONSTANT:
        return MutableExpr.ofConstant(fromCelConstant(celExpr.constant()));
      case IDENT:
        return MutableExpr.ofIdent(MutableIdent.create(celExpr.ident().name()));
      case SELECT:
        CelSelect select = celExpr.select();
        MutableExpr operand = fromCelExpr(select.operand());
        return MutableExpr.ofSelect(MutableSelect.create(operand, select.field(), select.testOnly()));
      case CALL:
        CelCall celCall = celExprKind.call();
        List<MutableExpr> args = celCall.args().stream()
            .map(MutableExprConverter::fromCelExpr)
            .collect(Collectors.toList());
        MutableCall mutableCall = celCall.target().isPresent() ?
            MutableCall.create(fromCelExpr(celCall.target().get()), celCall.function(), args) :
            MutableCall.create(celCall.function(), args);

        return MutableExpr.ofCall(mutableCall);
      case CREATE_LIST:
        return MutableExpr.ofCreateList(
            MutableCreateList.create(fromCelExprList(celExpr.createList().elements()))
        );
      case CREATE_STRUCT:
        return MutableExpr.ofCreateStruct(fromCelStructToMutableStruct(celExpr.createStruct()));
      case CREATE_MAP:
        return MutableExpr.ofCreateMap(fromCelMapToMutableMap(celExpr.createMap()));
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
        return MutableExpr.ofComprehension(mutableComprehension);
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
    List<MutableCreateStruct.Entry> entries = new ArrayList<MutableCreateStruct.Entry>();
    for (CelExpr.CelCreateStruct.Entry celStructExprEntry : celCreateStruct.entries()) {
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
    for (CelExpr.CelCreateMap.Entry celMapExprEntry : celCreateMap.entries()) {
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

  public static CelExpr fromMutableExpr(MutableExpr mutableExpr) {
    return null;
  }

  private MutableExprConverter() {}
}
