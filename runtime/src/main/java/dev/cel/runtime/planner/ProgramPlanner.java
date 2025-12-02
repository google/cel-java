// Copyright 2025 Google LLC
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

package dev.cel.runtime.planner;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.errorprone.annotations.CheckReturnValue;
import javax.annotation.concurrent.ThreadSafe;
import dev.cel.common.CelAbstractSyntaxTree;
import dev.cel.common.CelContainer;
import dev.cel.common.Operator;
import dev.cel.common.annotations.Internal;
import dev.cel.common.ast.CelConstant;
import dev.cel.common.ast.CelExpr;
import dev.cel.common.ast.CelExpr.CelCall;
import dev.cel.common.ast.CelExpr.CelList;
import dev.cel.common.ast.CelExpr.CelMap;
import dev.cel.common.ast.CelExpr.CelSelect;
import dev.cel.common.ast.CelExpr.CelStruct;
import dev.cel.common.ast.CelExpr.CelStruct.Entry;
import dev.cel.common.ast.CelReference;
import dev.cel.common.types.CelKind;
import dev.cel.common.types.CelType;
import dev.cel.common.types.CelTypeProvider;
import dev.cel.common.types.StructType;
import dev.cel.common.types.TypeType;
import dev.cel.common.values.CelValueProvider;
import dev.cel.runtime.CelEvaluationException;
import dev.cel.runtime.CelEvaluationExceptionBuilder;
import dev.cel.runtime.CelResolvedOverload;
import dev.cel.runtime.DefaultDispatcher;
import dev.cel.runtime.Interpretable;
import dev.cel.runtime.Program;
import java.util.NoSuchElementException;
import java.util.Optional;

/**
 * {@code ProgramPlanner} resolves functions, types, and identifiers at plan time given a
 * parsed-only or a type-checked expression.
 */
@ThreadSafe
@Internal
public final class ProgramPlanner {

  private final CelTypeProvider typeProvider;
  private final CelValueProvider valueProvider;
  private final DefaultDispatcher dispatcher;
  private final AttributeFactory attributeFactory;
  private final CelContainer container;

  /**
   * Plans a {@link Program} from the provided parsed-only or type-checked {@link
   * CelAbstractSyntaxTree}.
   */
  public Program plan(CelAbstractSyntaxTree ast) throws CelEvaluationException {
    Interpretable plannedInterpretable;
    try {
      plannedInterpretable = plan(ast.getExpr(), PlannerContext.create(ast));
    } catch (RuntimeException e) {
      throw CelEvaluationExceptionBuilder.newBuilder(e.getMessage()).setCause(e).build();
    }

    return PlannedProgram.create(plannedInterpretable);
  }

  private Interpretable plan(CelExpr celExpr, PlannerContext ctx) {
    switch (celExpr.getKind()) {
      case CONSTANT:
        return planConstant(celExpr.constant());
      case IDENT:
        return planIdent(celExpr, ctx);
      case CALL:
        return planCall(celExpr, ctx);
      case LIST:
        return planCreateList(celExpr, ctx);
      case STRUCT:
        return planCreateStruct(celExpr, ctx);
      case MAP:
        return planCreateMap(celExpr, ctx);
      case NOT_SET:
        throw new UnsupportedOperationException("Unsupported kind: " + celExpr.getKind());
      default:
        throw new IllegalArgumentException("Not yet implemented kind: " + celExpr.getKind());
    }
  }

  private Interpretable planConstant(CelConstant celConstant) {
    switch (celConstant.getKind()) {
      case NULL_VALUE:
        return EvalConstant.create(celConstant.nullValue());
      case BOOLEAN_VALUE:
        return EvalConstant.create(celConstant.booleanValue());
      case INT64_VALUE:
        return EvalConstant.create(celConstant.int64Value());
      case UINT64_VALUE:
        return EvalConstant.create(celConstant.uint64Value());
      case DOUBLE_VALUE:
        return EvalConstant.create(celConstant.doubleValue());
      case STRING_VALUE:
        return EvalConstant.create(celConstant.stringValue());
      case BYTES_VALUE:
        return EvalConstant.create(celConstant.bytesValue());
      default:
        throw new IllegalStateException("Unsupported kind: " + celConstant.getKind());
    }
  }

  private Interpretable planIdent(CelExpr celExpr, PlannerContext ctx) {
    CelReference ref = ctx.referenceMap().get(celExpr.id());
    if (ref != null) {
      return planCheckedIdent(celExpr.id(), ref, ctx.typeMap());
    }

    return EvalAttribute.create(attributeFactory.newMaybeAttribute(celExpr.ident().name()));
  }

  private Interpretable planCheckedIdent(
      long id, CelReference identRef, ImmutableMap<Long, CelType> typeMap) {
    if (identRef.value().isPresent()) {
      return planConstant(identRef.value().get());
    }

    CelType type = typeMap.get(id);
    if (type.kind().equals(CelKind.TYPE)) {
      TypeType identType =
          typeProvider
              .findType(identRef.name())
              .map(TypeType::create)
              .orElseThrow(
                  () ->
                      new NoSuchElementException(
                          "Reference to an undefined type: " + identRef.name()));
      return EvalConstant.create(identType);
    }

    return EvalAttribute.create(attributeFactory.newAbsoluteAttribute(identRef.name()));
  }

  private Interpretable planCall(CelExpr expr, PlannerContext ctx) {
    ResolvedFunction resolvedFunction = resolveFunction(expr, ctx.referenceMap());
    CelExpr target = resolvedFunction.target().orElse(null);
    int argCount = expr.call().args().size();
    if (target != null) {
      argCount++;
    }

    Interpretable[] evaluatedArgs = new Interpretable[argCount];

    int offset = 0;
    if (target != null) {
      evaluatedArgs[0] = plan(target, ctx);
      offset++;
    }

    ImmutableList<CelExpr> args = expr.call().args();
    for (int argIndex = 0; argIndex < args.size(); argIndex++) {
      evaluatedArgs[argIndex + offset] = plan(args.get(argIndex), ctx);
    }

    String functionName = resolvedFunction.functionName();
    Operator operator = Operator.findReverse(functionName).orElse(null);
    if (operator != null) {
      switch (operator) {
        case LOGICAL_OR:
          return EvalOr.create(evaluatedArgs);
        case LOGICAL_AND:
          return EvalAnd.create(evaluatedArgs);
        case CONDITIONAL:
          return EvalConditional.create(evaluatedArgs);
        default:
          // fall-through
      }
    }

    CelResolvedOverload resolvedOverload = null;
    if (resolvedFunction.overloadId().isPresent()) {
      resolvedOverload = dispatcher.findOverload(resolvedFunction.overloadId().get()).orElse(null);
    }

    if (resolvedOverload == null) {
      // Parsed-only function dispatch
      resolvedOverload =
          dispatcher
              .findOverload(functionName)
              .orElseThrow(() -> new NoSuchElementException("Overload not found: " + functionName));
    }

    switch (argCount) {
      case 0:
        return EvalZeroArity.create(resolvedOverload);
      case 1:
        return EvalUnary.create(resolvedOverload, evaluatedArgs[0]);
      default:
        return EvalVarArgsCall.create(resolvedOverload, evaluatedArgs);
    }
  }

  private Interpretable planCreateStruct(CelExpr celExpr, PlannerContext ctx) {
    CelStruct struct = celExpr.struct();
    StructType structType = resolveStructType(struct);

    ImmutableList<Entry> entries = struct.entries();
    String[] keys = new String[entries.size()];
    Interpretable[] values = new Interpretable[entries.size()];

    for (int i = 0; i < entries.size(); i++) {
      Entry entry = entries.get(i);
      keys[i] = entry.fieldKey();
      values[i] = plan(entry.value(), ctx);
    }

    return EvalCreateStruct.create(valueProvider, structType, keys, values);
  }

  private Interpretable planCreateList(CelExpr celExpr, PlannerContext ctx) {
    CelList list = celExpr.list();

    ImmutableList<CelExpr> elements = list.elements();
    Interpretable[] values = new Interpretable[elements.size()];

    for (int i = 0; i < elements.size(); i++) {
      values[i] = plan(elements.get(i), ctx);
    }

    return EvalCreateList.create(values);
  }

  private Interpretable planCreateMap(CelExpr celExpr, PlannerContext ctx) {
    CelMap map = celExpr.map();

    ImmutableList<CelMap.Entry> entries = map.entries();
    Interpretable[] keys = new Interpretable[entries.size()];
    Interpretable[] values = new Interpretable[entries.size()];

    for (int i = 0; i < entries.size(); i++) {
      CelMap.Entry entry = entries.get(i);
      keys[i] = plan(entry.key(), ctx);
      values[i] = plan(entry.value(), ctx);
    }

    return EvalCreateMap.create(keys, values);
  }

  /**
   * resolveFunction determines the call target, function name, and overload name (when unambiguous)
   * from the given call expr.
   */
  private ResolvedFunction resolveFunction(
      CelExpr expr, ImmutableMap<Long, CelReference> referenceMap) {
    CelCall call = expr.call();
    Optional<CelExpr> maybeTarget = call.target();
    String functionName = call.function();

    CelReference reference = referenceMap.get(expr.id());
    if (reference != null) {
      // Checked expression
      if (reference.overloadIds().size() == 1) {
        ResolvedFunction.Builder builder =
            ResolvedFunction.newBuilder()
                .setFunctionName(functionName)
                .setOverloadId(reference.overloadIds().get(0));

        maybeTarget.ifPresent(builder::setTarget);

        return builder.build();
      }
    }

    // Parsed-only function resolution.
    //
    // There are two distinct cases we must handle:
    //
    // 1. Non-qualified function calls. This will resolve into either:
    //    - A simple global call foo()
    //    - A fully qualified global call through normal container resolution foo.bar.qux()
    // 2. Qualified function calls:
    //    - A member call on an identifier foo.bar()
    //    - A fully qualified global call, through normal container resolution or abbreviations
    //      foo.bar.qux()
    if (!maybeTarget.isPresent()) {
      for (String cand : container.resolveCandidateNames(functionName)) {
        CelResolvedOverload overload = dispatcher.findOverload(cand).orElse(null);
        if (overload != null) {
          return ResolvedFunction.newBuilder().setFunctionName(cand).build();
        }
      }

      // Normal global call
      return ResolvedFunction.newBuilder().setFunctionName(functionName).build();
    }

    CelExpr target = maybeTarget.get();
    String qualifiedPrefix = toQualifiedName(target).orElse(null);
    if (qualifiedPrefix != null) {
      String qualifiedName = qualifiedPrefix + "." + functionName;
      for (String cand : container.resolveCandidateNames(qualifiedName)) {
        CelResolvedOverload overload = dispatcher.findOverload(cand).orElse(null);
        if (overload != null) {
          return ResolvedFunction.newBuilder().setFunctionName(cand).build();
        }
      }
    }

    // Normal member call
    return ResolvedFunction.newBuilder().setFunctionName(functionName).setTarget(target).build();
  }

  private StructType resolveStructType(CelStruct struct) {
    String messageName = struct.messageName();
    for (String typeName : container.resolveCandidateNames(messageName)) {
      CelType structType = typeProvider.findType(typeName).orElse(null);
      if (structType == null) {
        continue;
      }

      if (!structType.kind().equals(CelKind.STRUCT)) {
        throw new IllegalArgumentException(
            String.format(
                "Expected struct type for %s, got %s", structType.name(), structType.kind()));
      }

      return (StructType) structType;
    }

    throw new IllegalArgumentException("Undefined type name: " + messageName);
  }

  /** Converts a given expression into a qualified name, if possible. */
  private Optional<String> toQualifiedName(CelExpr operand) {
    switch (operand.getKind()) {
      case IDENT:
        return Optional.of(operand.ident().name());
      case SELECT:
        CelSelect select = operand.select();
        String maybeQualified = toQualifiedName(select.operand()).orElse(null);
        if (maybeQualified != null) {
          return Optional.of(maybeQualified + "." + select.field());
        }

        break;
      default:
        // fall-through
    }

    return Optional.empty();
  }

  @AutoValue
  abstract static class ResolvedFunction {

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
  abstract static class PlannerContext {

    abstract ImmutableMap<Long, CelReference> referenceMap();

    abstract ImmutableMap<Long, CelType> typeMap();

    private static PlannerContext create(CelAbstractSyntaxTree ast) {
      return new AutoValue_ProgramPlanner_PlannerContext(ast.getReferenceMap(), ast.getTypeMap());
    }
  }

  public static ProgramPlanner newPlanner(
      CelTypeProvider typeProvider,
      CelValueProvider valueProvider,
      DefaultDispatcher dispatcher,
      CelContainer container) {
    return new ProgramPlanner(typeProvider, valueProvider, dispatcher, container);
  }

  private ProgramPlanner(
      CelTypeProvider typeProvider,
      CelValueProvider valueProvider,
      DefaultDispatcher dispatcher,
      CelContainer container) {
    this.typeProvider = typeProvider;
    this.valueProvider = valueProvider;
    this.dispatcher = dispatcher;
    this.container = container;
    this.attributeFactory = AttributeFactory.newAttributeFactory(container, typeProvider);
  }
}
