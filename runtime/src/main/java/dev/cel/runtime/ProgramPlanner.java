package dev.cel.runtime;

import com.google.errorprone.annotations.Immutable;
import dev.cel.common.CelAbstractSyntaxTree;
import dev.cel.common.ast.CelExpr;
import dev.cel.common.values.CelValue;
import dev.cel.common.values.CelValueConverter;

@Immutable
final class ProgramPlanner {
  private static final CelValueConverter DEFAULT_VALUE_CONVERTER = new CelValueConverter();

  private static CelValueInterpretable plan(CelExpr celExpr) {
    switch (celExpr.getKind()) {
      case CONSTANT:
        CelValue constValue = DEFAULT_VALUE_CONVERTER.fromJavaObjectToCelValue(celExpr.constant().objectValue());
        return new EvalConstant(constValue);
      case NOT_SET:
        break;
      case IDENT:
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
    }

    throw new IllegalArgumentException("foo");
  }

  static CelLiteRuntime.Program plan(CelAbstractSyntaxTree ast) {
    return CelValueProgram.create(plan(ast.getExpr()));
  }
}
