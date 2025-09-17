package dev.cel.runtime.planner;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.errorprone.annotations.CheckReturnValue;
import com.google.errorprone.annotations.ThreadSafe;
import dev.cel.common.CelAbstractSyntaxTree;
import dev.cel.common.annotations.Internal;
import dev.cel.common.ast.CelConstant;
import dev.cel.common.ast.CelExpr;
import dev.cel.common.ast.CelExpr.CelCall;
import dev.cel.common.ast.CelExpr.CelList;
import dev.cel.common.ast.CelExpr.CelMap;
import dev.cel.common.ast.CelExpr.CelStruct;
import dev.cel.common.ast.CelExpr.CelStruct.Entry;
import dev.cel.common.ast.CelReference;
import dev.cel.common.types.CelKind;
import dev.cel.common.types.CelType;
import dev.cel.common.types.CelTypeProvider;
import dev.cel.common.values.CelValue;
import dev.cel.common.values.CelValueConverter;
import dev.cel.common.values.CelValueProvider;
import dev.cel.common.values.TypeValue;
import dev.cel.runtime.CelFunctionBinding;
import dev.cel.runtime.CelLiteRuntime.Program;
import dev.cel.runtime.DefaultDispatcher;

import java.util.HashMap;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

@ThreadSafe
@Internal
public final class ProgramPlanner {
  private final CelTypeProvider typeProvider;
  private final CelValueProvider valueProvider;
  private final CelValueConverter celValueConverter;
  private final DefaultDispatcher dispatcher;
  private final AttributeFactory attributeFactory;

  private CelValueInterpretable plan(
      CelExpr celExpr,
      PlannerContext ctx
      ) {
    switch (celExpr.getKind()) {
      case CONSTANT:
        return fromCelConstant(celExpr.constant());
      case IDENT:
        return planIdent(celExpr, ctx);
      case SELECT:
        break;
      case CALL:
        return planCall(celExpr, ctx);
      case LIST:
        return planCreateList(celExpr, ctx);
      case STRUCT:
        return planCreateStruct(celExpr, ctx);
      case MAP:
        return planCreateMap(celExpr, ctx);
      case COMPREHENSION:
        break;
      case NOT_SET:
        throw new UnsupportedOperationException("Unsupported kind: " + celExpr.getKind());
    }

    throw new IllegalArgumentException("Not yet implemented");
  }

  private CelValueInterpretable planIdent(
      CelExpr celExpr,
      PlannerContext ctx) {
    CelReference ref = ctx.referenceMap().get(celExpr.id());
    if (ref != null) {
      return planCheckedIdent(celExpr.id(), ref, ctx.typeMap());
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

  private CelValueInterpretable planCall(CelExpr expr, PlannerContext ctx) {
    ResolvedFunction resolvedFunction = resolveFunction(expr, ctx.referenceMap());
    int argCount = expr.call().args().size();
    ImmutableList.Builder<CelValueInterpretable> evaluatedArgBuilder = ImmutableList.builder();

    if (resolvedFunction.target().isPresent()) {
      argCount++;
      evaluatedArgBuilder.add(plan(resolvedFunction.target().get(), ctx));
    }

    for (CelExpr argExpr : expr.call().args()) {
      evaluatedArgBuilder.add(plan(argExpr, ctx));
    }
    ImmutableList<CelValueInterpretable> evaluatedArgs = evaluatedArgBuilder.build();

    // TODO: Handle specialized calls (logical operators, conditionals, equals etc)
    String functionName = resolvedFunction.functionName();
    switch (functionName) {
      // TODO: Move Operator.java out to common package and use that instead.
      case "_||_":
        return EvalOr.create(evaluatedArgs);
      case "_&&_":
        return EvalAnd.create(evaluatedArgs);
    }

    CelFunctionBinding resolvedOverload = null;

    if (resolvedFunction.overloadId().isPresent()) {
      resolvedOverload = dispatcher.findOverload(resolvedFunction.overloadId().get()).orElse(null);
    }

    if (resolvedOverload == null) {
      resolvedOverload = dispatcher.findOverload(functionName).orElseThrow(() -> new NoSuchElementException("TODO: Overload not found"));
    }

    switch (argCount) {
      case 0:
        return EvalZeroArity.create(resolvedOverload, celValueConverter);
      case 1:
        return EvalUnary.create(resolvedOverload, celValueConverter, evaluatedArgs.get(0));
        // TODO: Handle binary
      default:
        return EvalVarArgsCall.create(resolvedOverload, celValueConverter, evaluatedArgs);
    }
  }

  private CelValueInterpretable planCreateStruct(CelExpr celExpr, PlannerContext ctx) {
    CelStruct struct = celExpr.struct();
    // TODO: maybe perform the check via type provider?
    valueProvider.newValue(struct.messageName(), new HashMap<>())
        .orElseThrow(() -> new IllegalArgumentException("Undefined type name: " + struct.messageName()));

    List<Entry> entries = struct.entries();
    String[] keys = new String[entries.size()];
    CelValueInterpretable[] values = new CelValueInterpretable[entries.size()];

    for (int i = 0; i < entries.size(); i++) {
      Entry entry = entries.get(i);
      keys[i] = entry.fieldKey();
      values[i] = plan(entry.value(), ctx);
    }

    return EvalCreateStruct.create(valueProvider, struct.messageName(), keys, values);
  }

  private CelValueInterpretable planCreateList(CelExpr celExpr, PlannerContext ctx) {
    CelList list = celExpr.list();

    List<CelExpr> elements = list.elements();
    CelValueInterpretable[] values = new CelValueInterpretable[elements.size()];

    for (int i = 0; i < elements.size(); i++) {
      values[i] = plan(elements.get(i), ctx);
    }

    return EvalCreateList.create(values);
  }

  private CelValueInterpretable planCreateMap(CelExpr celExpr, PlannerContext ctx) {
    CelMap map = celExpr.map();

    List<CelMap.Entry> entries = map.entries();
    CelValueInterpretable[] keys = new CelValueInterpretable[entries.size()];
    CelValueInterpretable[] values = new CelValueInterpretable[entries.size()];

    for (int i = 0; i < entries.size(); i++) {
      CelMap.Entry entry = entries.get(i);
      keys[i] = plan(entry.key(), ctx);
      values[i] = plan(entry.value(), ctx);
    }

    return EvalCreateMap.create(keys, values);
  }

  /**
   * resolveFunction determines the call target, function name, and overload name (when unambiguous) from the given call expr.
   */
  private ResolvedFunction resolveFunction(CelExpr expr, ImmutableMap<Long, CelReference> referenceMap) {
    CelCall call = expr.call();
    Optional<CelExpr> target = call.target();
    String functionName = call.function();

    CelReference reference = referenceMap.get(expr.id());
    if (reference != null) {
      // Checked expression
      if (reference.overloadIds().size() == 1) {
        ResolvedFunction.Builder builder = ResolvedFunction.newBuilder()
                .setFunctionName(functionName)
                .setOverloadId(reference.overloadIds().get(0));

        target.ifPresent(builder::setTarget);

        return builder.build();
      }
    }

    // Parse-only from this point on
//    dispatcher.findOverload(functionName)
//        .orElseThrow(() -> new NoSuchElementException(String.format("Function %s not found", call.function())));

    if (!target.isPresent()) {
      // TODO: Handle containers.

      return ResolvedFunction.newBuilder()
          .setFunctionName(functionName)
          .build();
    } else {
      // TODO: Handle qualifications
      return ResolvedFunction.newBuilder()
          .setFunctionName(functionName)
          .setTarget(target.get())
          .build();
    }
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

  @AutoValue
  static abstract class PlannerContext {

    abstract ImmutableMap<Long, CelReference> referenceMap();
    abstract ImmutableMap<Long, CelType> typeMap();

    private static PlannerContext create(CelAbstractSyntaxTree ast) {
      return new AutoValue_ProgramPlanner_PlannerContext(ast.getReferenceMap(), ast.getTypeMap());
    }
  }

  public Program plan(CelAbstractSyntaxTree ast) {
    CelValueInterpretable plannedInterpretable = plan(ast.getExpr(), PlannerContext.create(ast));
    return CelValueProgram.create(plannedInterpretable, celValueConverter);
  }

  public static ProgramPlanner newPlanner(
      CelTypeProvider typeProvider,
      CelValueProvider valueProvider,
      CelValueConverter celValueConverter,
      DefaultDispatcher dispatcher
  ) {
    return new ProgramPlanner(typeProvider, valueProvider, celValueConverter, dispatcher);
  }

  private ProgramPlanner(
      CelTypeProvider typeProvider,
      CelValueProvider valueProvider,
      CelValueConverter celValueConverter,
      DefaultDispatcher dispatcher
  ) {
    this.typeProvider = typeProvider;
    this.valueProvider = valueProvider;
    this.celValueConverter = celValueConverter;
    this.dispatcher = dispatcher;
    this.attributeFactory = AttributeFactory.newAttributeFactory("", celValueConverter, typeProvider);
  }
}
