package dev.cel.runtime.planner;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableMap;
import com.google.errorprone.annotations.CheckReturnValue;
import com.google.errorprone.annotations.ThreadSafe;
import dev.cel.common.CelAbstractSyntaxTree;
import dev.cel.common.annotations.Internal;
import dev.cel.common.ast.CelConstant;
import dev.cel.common.ast.CelExpr;
import dev.cel.common.ast.CelExpr.CelCall;
import dev.cel.common.ast.CelReference;
import dev.cel.common.types.CelKind;
import dev.cel.common.types.CelType;
import dev.cel.common.types.CelTypeProvider;
import dev.cel.common.values.CelValue;
import dev.cel.common.values.CelValueConverter;
import dev.cel.common.values.TypeValue;
import dev.cel.runtime.CelLiteRuntime.Program;
import dev.cel.runtime.DefaultDispatcher;
import dev.cel.runtime.ResolvedOverload;

import java.util.NoSuchElementException;
import java.util.Optional;

@ThreadSafe
@Internal
public final class ProgramPlanner {
  private final CelTypeProvider typeProvider;
  private final CelValueConverter celValueConverter;
  private final DefaultDispatcher dispatcher;
  private final AttributeFactory attributeFactory;

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
        return planCall(celExpr, referenceMap);
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

    return EvalAttribute.create(
        celExpr.id(),
        celValueConverter,
        attributeFactory.newMaybeAttribute(celExpr.ident().name())
    );
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

    return EvalAttribute.create(id, celValueConverter, attributeFactory.newAbsoluteAttribute(identRef.name()));
  }

  private EvalConstant fromCelConstant(CelConstant celConstant) {
    CelValue celValue = celValueConverter.fromJavaObjectToCelValue(celConstant.objectValue());
    return EvalConstant.create(celValue);
  }

  private EvalZeroArity planCall(CelExpr expr, ImmutableMap<Long, CelReference> referenceMap) {
    ResolvedFunction resolvedFunction = resolveFunction(expr, referenceMap);
    // TODO: Handle args
    int argCount = expr.call().args().size();

    // TODO: Handle specialized calls (logical operators, index, conditionals, equals etc)

    ResolvedOverload resolvedOverload = null;

    if (resolvedFunction.overloadId().isPresent()) {
      resolvedOverload = dispatcher.findOverload(resolvedFunction.overloadId().get()).orElse(null);
    }

    if (resolvedOverload == null) {
      resolvedOverload = dispatcher.findOverload(resolvedFunction.functionName()).orElseThrow(() -> new NoSuchElementException("TODO: Overload not found"));
    }

    switch (argCount) {
      case 0:
        return EvalZeroArity.create(resolvedOverload, celValueConverter);
      default:
        break;
    }

    throw new UnsupportedOperationException("Unimplemented");
  }

  /**
   * resolveFunction determines the call target, function name, and overload name (when unambiguous) from the given call expr.
   */
  private ResolvedFunction resolveFunction(CelExpr expr, ImmutableMap<Long, CelReference> referenceMap) {
    CelCall call = expr.call();

    CelReference reference = referenceMap.get(expr.id());
    if (reference != null) {
      if (reference.overloadIds().size() == 1) {
        ResolvedFunction.Builder builder = ResolvedFunction.newBuilder()
                .setFunctionName(call.function())
                .setOverloadId(reference.overloadIds().get(0));

        call.target().ifPresent(builder::setTarget);

        return builder.build();
      }
    }

    throw new UnsupportedOperationException("Unimplemented");
  }

  @AutoValue
  static abstract class ResolvedFunction {

    abstract String functionName();

    abstract Optional<CelExpr> target();

    abstract Optional<String> overloadId();

    @AutoValue.Builder
    abstract static class Builder {
      abstract Builder setFunctionName(String functionName);
      abstract Builder setTarget(CelExpr target);
      abstract Builder setOverloadId(String overloadId);

      @CheckReturnValue
      abstract ResolvedFunction build();
    }

    private static Builder newBuilder() {
      return new AutoValue_ProgramPlanner_ResolvedFunction.Builder();
    }
  }

  public Program plan(CelAbstractSyntaxTree ast) {
    CelValueInterpretable plannedInterpretable = plan(ast.getExpr(), ast.getTypeMap(), ast.getReferenceMap());
    return CelValueProgram.create(plannedInterpretable, celValueConverter);
  }

  public static ProgramPlanner newPlanner(
      CelTypeProvider typeProvider,
      CelValueConverter celValueConverter,
      DefaultDispatcher dispatcher
  ) {
    return new ProgramPlanner(typeProvider, celValueConverter, dispatcher);
  }

  private ProgramPlanner(
      CelTypeProvider typeProvider,
      CelValueConverter celValueConverter,
      DefaultDispatcher dispatcher
  ) {
    this.typeProvider = typeProvider;
    this.celValueConverter = celValueConverter;
    this.dispatcher = dispatcher;
    this.attributeFactory = AttributeFactory.newAttributeFactory("", celValueConverter, typeProvider);
  }
}
