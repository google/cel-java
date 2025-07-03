package dev.cel.runtime.planner;

import com.google.common.collect.ImmutableMap;
import com.google.errorprone.annotations.Immutable;
import dev.cel.common.CelAbstractSyntaxTree;
import dev.cel.common.annotations.Internal;
import dev.cel.common.ast.CelConstant;
import dev.cel.common.ast.CelExpr;
import dev.cel.common.ast.CelReference;
import dev.cel.common.types.CelKind;
import dev.cel.common.types.CelType;
import dev.cel.common.types.CelTypeProvider;
import dev.cel.common.values.CelValue;
import dev.cel.common.values.CelValueConverter;
import dev.cel.common.values.TypeValue;
import dev.cel.runtime.CelLiteRuntime.Program;
import java.util.NoSuchElementException;

@Immutable
@Internal
public final class ProgramPlanner {
  private final CelTypeProvider typeProvider;
  private final CelValueConverter celValueConverter;

  private CelValueInterpretable plan(CelExpr celExpr,
      ImmutableMap<Long, CelType> typeMap,
      ImmutableMap<Long, CelReference> referenceMap) {
    switch (celExpr.getKind()) {
      case CONSTANT:
        return fromCelConstant(celExpr.constant());
      case IDENT:
        return planIdent(celExpr, typeMap, referenceMap);
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

  private CelValueInterpretable planIdent(
      CelExpr celExpr,
      ImmutableMap<Long, CelType> typeMap,
      ImmutableMap<Long, CelReference> referenceMap) {
    CelReference ref = referenceMap.get(celExpr.id());
    if (ref != null) {
      return planCheckedIdent(celExpr.id(), ref, typeMap);
    }

    return EvalAttribute.newMaybeAttribute(celExpr.id(), celValueConverter, "", celExpr.ident().name());
  }

  private CelValueInterpretable planCheckedIdent(
      long id,
      CelReference identRef,
      ImmutableMap<Long, CelType> typeMap) {
    if (identRef.value().isPresent()) {
      return fromCelConstant(identRef.value().get());
    }

    CelType type = typeMap.get(id);
    if (type.kind().equals(CelKind.TYPE)) {
      CelType identType = typeProvider.findType(identRef.name()).orElseThrow(() -> new NoSuchElementException("Reference to undefined type: " + identRef.name()));
      return EvalConstant.create(TypeValue.create(identType));
    }

    return EvalAttribute.newAbsoluteAttribute(id, celValueConverter, identRef.name());
  }

  private EvalConstant fromCelConstant(CelConstant celConstant) {
    CelValue celValue = celValueConverter.fromJavaObjectToCelValue(celConstant.objectValue());
    return EvalConstant.create(celValue);
  }

  public Program plan(CelAbstractSyntaxTree ast) {
    CelValueInterpretable plannedInterpretable = plan(ast.getExpr(), ast.getTypeMap(), ast.getReferenceMap());
    return CelValueProgram.create(plannedInterpretable, celValueConverter);
  }

  public static ProgramPlanner newPlanner(
      CelTypeProvider typeProvider,
      CelValueConverter celValueConverter
  ) {
    return new ProgramPlanner(typeProvider, celValueConverter);
  }

  private ProgramPlanner(
      CelTypeProvider typeProvider,
      CelValueConverter celValueConverter
  ) {
    this.typeProvider = typeProvider;
    this.celValueConverter = celValueConverter;
  }
}
