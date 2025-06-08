package dev.cel.runtime;

import com.google.common.collect.ImmutableMap;
import com.google.errorprone.annotations.Immutable;
import dev.cel.common.CelAbstractSyntaxTree;
import dev.cel.common.ast.CelConstant;
import dev.cel.common.ast.CelExpr;
import dev.cel.common.ast.CelReference;
import dev.cel.common.values.CelValue;
import dev.cel.common.values.CelValueConverter;

@Immutable
final class ProgramPlanner {
  private static final CelValueConverter DEFAULT_VALUE_CONVERTER = new CelValueConverter();

  private static CelValueInterpretable plan(CelExpr celExpr,
      ImmutableMap<Long, CelReference> referenceMap) {
    switch (celExpr.getKind()) {
      case CONSTANT:
        return fromConstExpr(celExpr.constant());
      case IDENT:
        return planIdent(celExpr, referenceMap);
      case SELECT:
        break;
      case CALL:
        break;
      case LIST:
        break;
      case STRUCT:
        break;
      case MAP:
        break;
      case COMPREHENSION:
        break;
      case NOT_SET:
        throw new UnsupportedOperationException("Unsupported kind: " + celExpr.getKind());
    }

    throw new IllegalArgumentException("foo");
  }

  private static CelValueInterpretable planIdent(CelExpr celExpr,
      ImmutableMap<Long, CelReference> referenceMap) {
    CelReference ref = referenceMap.get(celExpr.id());
    if (ref != null) {
      if (ref.value().isPresent()) {
        return fromConstExpr(ref.value().get());
      }
    }

    return null;
  }

  static CelLiteRuntime.Program plan(CelAbstractSyntaxTree ast) {
    CelValueInterpretable plannedInterpretable = plan(ast.getExpr(), ast.getReferenceMap());
    return CelValueProgram.create(plannedInterpretable);
  }

  private static EvalConstant fromConstExpr(CelConstant celConstant) {
    CelValue celValue = DEFAULT_VALUE_CONVERTER.fromJavaObjectToCelValue(celConstant.objectValue());
    return new EvalConstant(celValue);
  }
}
