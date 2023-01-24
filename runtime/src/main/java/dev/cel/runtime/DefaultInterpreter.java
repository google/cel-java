// Copyright 2022 Google LLC
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

package dev.cel.runtime;

import com.google.api.expr.v1alpha1.CheckedExpr;
import com.google.api.expr.v1alpha1.Constant;
import com.google.api.expr.v1alpha1.Expr;
import com.google.api.expr.v1alpha1.Expr.Call;
import com.google.api.expr.v1alpha1.Expr.Comprehension;
import com.google.api.expr.v1alpha1.Expr.CreateList;
import com.google.api.expr.v1alpha1.Expr.CreateStruct;
import com.google.api.expr.v1alpha1.Expr.Ident;
import com.google.api.expr.v1alpha1.Expr.Select;
import com.google.api.expr.v1alpha1.Reference;
import com.google.api.expr.v1alpha1.Type;
import com.google.api.expr.v1alpha1.Type.TypeKindCase;
import com.google.api.expr.v1alpha1.Value;
import javax.annotation.concurrent.ThreadSafe;
import com.google.auto.value.AutoValue;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.common.primitives.UnsignedLong;
import com.google.errorprone.annotations.Immutable;
import dev.cel.common.CelOptions;
import dev.cel.common.annotations.Internal;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nullable;

/**
 * Default implementation of the CEL interpreter.
 *
 * <p>Use as in:
 *
 * <pre>
 *   MessageFactory messageFactory = new LinkedMessageFactory();
 *   RuntimeTypeProvider typeProvider = new DescriptorMessageProvider(messageFactory);
 *   Dispatcher dispatcher = DefaultDispatcher.create();
 *   Interpreter interpreter = new DefaultInterpreter(typeProvider, dispatcher);
 *   Interpretable interpretable = interpreter.createInterpretable(checkedExpr);
 *   Object result = interpretable.eval(Activation.of("name", value));
 * </pre>
 *
 * <p>Extensions functions can be added in addition to standard functions to the dispatcher as
 * needed.
 *
 * <p>Note: {MessageFactory} instances may be combined using the {@link
 * MessageFactory.CombinedMessageFactory}.
 *
 * <p>Note: On Android, the {@code DescriptorMessageProvider} is not supported as proto lite does
 * not support descriptors. Instead, implement the {@code MessageProvider} interface directly.
 *
 * <p>CEL Library Internals. Do Not Use.
 */
@ThreadSafe
@Internal
public final class DefaultInterpreter implements Interpreter {

  private final RuntimeTypeProvider typeProvider;
  private final Dispatcher dispatcher;
  private final CelOptions celOptions;

  /**
   * Internal representation of an intermediate evaluation result.
   *
   * <p>Represents a partial result of evaluating a sub expression and the corresponding CEL
   * attribute (or Empty if not applicable). Top level results of evaluation should unwrap the
   * underlying result (.value()).
   */
  @AutoValue
  abstract static class IntermediateResult {
    abstract CelAttribute attribute();

    abstract Object value();

    static IntermediateResult create(CelAttribute attr, Object value) {
      Preconditions.checkArgument(
          !(value instanceof IntermediateResult),
          "Recursive intermediate results are not supported.");
      return new AutoValue_DefaultInterpreter_IntermediateResult(attr, value);
    }

    static IntermediateResult create(Object value) {
      return create(CelAttribute.EMPTY, value);
    }
  }

  public DefaultInterpreter(RuntimeTypeProvider typeProvider, Dispatcher dispatcher) {
    this(typeProvider, dispatcher, CelOptions.LEGACY);
  }

  /**
   * Creates a new interpreter
   *
   * @param typeProvider object which allows to construct and inspect messages.
   * @param dispatcher a method dispatcher.
   * @param celOptions the configurable flags for adjusting evaluation behavior.
   */
  public DefaultInterpreter(
      RuntimeTypeProvider typeProvider, Dispatcher dispatcher, CelOptions celOptions) {
    this.typeProvider = Preconditions.checkNotNull(typeProvider);
    this.dispatcher = Preconditions.checkNotNull(dispatcher);
    this.celOptions = celOptions;
  }

  @Override
  public Interpretable createInterpretable(CheckedExpr checkedExpr) {
    return new DefaultInterpretable(typeProvider, dispatcher, checkedExpr, celOptions);
  }

  @Immutable
  private static final class DefaultInterpretable
      implements Interpretable, UnknownTrackingInterpretable {
    private final RuntimeTypeProvider typeProvider;
    private final Dispatcher.ImmutableCopy dispatcher;
    private final Metadata metadata;
    private final CheckedExpr checkedExpr;
    private final CelOptions celOptions;

    DefaultInterpretable(
        RuntimeTypeProvider typeProvider,
        Dispatcher dispatcher,
        CheckedExpr checkedExpr,
        CelOptions celOptions) {
      this.typeProvider = Preconditions.checkNotNull(typeProvider);
      this.dispatcher = Preconditions.checkNotNull(dispatcher).immutableCopy();
      this.metadata = new DefaultMetadata(checkedExpr);
      this.checkedExpr = Preconditions.checkNotNull(checkedExpr);
      this.celOptions = Preconditions.checkNotNull(celOptions);
    }

    @Override
    public Object eval(GlobalResolver resolver) throws InterpreterException {
      // Result is already unwrapped from IntermediateResult.
      return evalTrackingUnknowns(RuntimeUnknownResolver.fromResolver(resolver));
    }

    @Override
    public Object evalTrackingUnknowns(RuntimeUnknownResolver resolver)
        throws InterpreterException {
      ExecutionFrame frame = new ExecutionFrame(resolver, celOptions.comprehensionMaxIterations());
      IntermediateResult internalResult = evalInternal(frame, checkedExpr.getExpr());
      Object result = internalResult.value();
      // TODO: remove support for IncompleteData.
      return InterpreterUtil.completeDataOnly(
          result, "Incomplete data cannot be returned as a result.");
    }

    private IntermediateResult evalInternal(ExecutionFrame frame, Expr expr)
        throws InterpreterException {
      try {
        switch (expr.getExprKindCase()) {
          case CONST_EXPR:
            return IntermediateResult.create(evalConstant(frame, expr, expr.getConstExpr()));
          case IDENT_EXPR:
            return evalIdent(frame, expr, expr.getIdentExpr());
          case SELECT_EXPR:
            return evalSelect(frame, expr, expr.getSelectExpr());
          case CALL_EXPR:
            return evalCall(frame, expr, expr.getCallExpr());
          case LIST_EXPR:
            return evalList(frame, expr, expr.getListExpr());
          case STRUCT_EXPR:
            return evalStruct(frame, expr, expr.getStructExpr());
          case COMPREHENSION_EXPR:
            return evalComprehension(frame, expr, expr.getComprehensionExpr());
          default:
            throw new IllegalStateException(
                "unexpected expression kind: " + expr.getExprKindCase());
        }
      } catch (IllegalArgumentException e) {
        throw new InterpreterException.Builder(e.getMessage())
            .setCause(e)
            .setLocation(metadata, expr.getId())
            .build();
      }
    }

    private boolean isUnknownValue(Object value) {
      return value instanceof CelUnknownSet || InterpreterUtil.isUnknown(value);
    }

    private Object evalConstant(ExecutionFrame unusedFrame, Expr unusedExpr, Constant constExpr) {
      switch (constExpr.getConstantKindCase()) {
        case NULL_VALUE:
          return constExpr.getNullValue();
        case BOOL_VALUE:
          return constExpr.getBoolValue();
        case INT64_VALUE:
          return constExpr.getInt64Value();
        case UINT64_VALUE:
          if (celOptions.enableUnsignedLongs()) {
            return UnsignedLong.fromLongBits(constExpr.getUint64Value());
          }
          return constExpr.getUint64Value();
        case DOUBLE_VALUE:
          return constExpr.getDoubleValue();
        case STRING_VALUE:
          return constExpr.getStringValue();
        case BYTES_VALUE:
          return constExpr.getBytesValue();
        default:
          throw new IllegalStateException(
              "unsupported constant case: " + constExpr.getConstantKindCase());
      }
    }

    private Reference getReferenceOrThrow(long exprId) {
      return checkedExpr.getReferenceMapOrThrow(exprId);
    }

    @Nullable
    private Reference getReferenceOrDefault(long exprId, Reference defaultValue) {
      return checkedExpr.getReferenceMapOrDefault(exprId, defaultValue);
    }

    private IntermediateResult evalIdent(ExecutionFrame frame, Expr expr, Ident unusedIdent)
        throws InterpreterException {
      Reference reference = getReferenceOrThrow(expr.getId());
      if (reference.hasValue()) {
        return IntermediateResult.create(evalConstant(frame, expr, reference.getValue()));
      }
      return resolveIdent(frame, expr, reference.getName());
    }

    private IntermediateResult resolveIdent(ExecutionFrame frame, Expr expr, String name)
        throws InterpreterException {
      // Check whether the type exists in the type check map as a 'type'.
      Type checkedType = checkedExpr.getTypeMapMap().get(expr.getId());
      if (checkedType != null && checkedType.getTypeKindCase() == TypeKindCase.TYPE) {
        Object typeValue = typeProvider.adaptType(checkedType);
        if (typeValue != null) {
          return IntermediateResult.create(typeValue);
        }
        throw new InterpreterException.Builder(
                "expected a runtime type for '%s', but found none.", checkedType)
            .setLocation(metadata, expr.getId())
            .build();
      }

      IntermediateResult rawResult = frame.resolveSimpleName(name, expr.getId());

      // Value resolved from Binding, it could be Message, PartialMessage or unbound(null)
      Object value = InterpreterUtil.strict(typeProvider.adapt(rawResult.value()));
      return IntermediateResult.create(rawResult.attribute(), value);
    }

    private IntermediateResult evalSelect(ExecutionFrame frame, Expr expr, Select selectExpr)
        throws InterpreterException {
      Reference reference = getReferenceOrDefault(expr.getId(), null);
      if (reference == null) {
        // This indicates this is a field selection on the operand.
        IntermediateResult operandResult = evalInternal(frame, selectExpr.getOperand());
        Object operand = operandResult.value();

        CelAttribute attribute =
            operandResult
                .attribute()
                .qualify(CelAttribute.Qualifier.ofString(selectExpr.getField()));

        Optional<Object> attrValue = frame.resolveAttribute(attribute);

        if (attrValue.isPresent()) {
          return IntermediateResult.create(attribute, attrValue.get());
        }

        // Nested message could be unknown
        if (isUnknownValue(operand)) {
          return IntermediateResult.create(attribute, operand);
        }

        if (selectExpr.getTestOnly()) {
          return IntermediateResult.create(
              attribute, typeProvider.hasField(operand, selectExpr.getField()));
        }
        Object fieldValue = typeProvider.selectField(operand, selectExpr.getField());

        return IntermediateResult.create(
            attribute, InterpreterUtil.valueOrUnknown(fieldValue, expr.getId()));
      }
      // This indicates it's a qualified name.
      if (reference.hasValue()) {
        // If the value is identified as a constant, skip attribute tracking.
        return IntermediateResult.create(evalConstant(frame, expr, reference.getValue()));
      }
      return resolveIdent(frame, expr, reference.getName());
    }

    private IntermediateResult evalCall(ExecutionFrame frame, Expr expr, Call callExpr)
        throws InterpreterException {
      Reference reference = getReferenceOrThrow(expr.getId());
      Preconditions.checkState(reference.getOverloadIdCount() > 0);

      // Handle cases with special semantics. Those cannot have overloads.
      switch (reference.getOverloadId(0)) {
        case "identity":
          // Could be added as a binding to the dispatcher.  Handled here for parity
          // with FuturesInterpreter where the difference is slightly more significant.
          return evalInternal(frame, callExpr.getArgs(0));
        case "conditional":
          return evalConditional(frame, callExpr);
        case "logical_and":
          return evalLogicalAnd(frame, callExpr);
        case "logical_or":
          return evalLogicalOr(frame, callExpr);
        case "not_strictly_false":
          return evalNotStrictlyFalse(frame, callExpr);
        case "type":
          return evalType(frame, callExpr);
        default:
          break;
      }

      // Delegate handling of call to dispatcher.
      List<Expr> callArgs = new ArrayList<>();
      if (callExpr.hasTarget()) {
        callArgs.add(callExpr.getTarget());
      }
      callArgs.addAll(callExpr.getArgsList());
      IntermediateResult[] argResults = new IntermediateResult[callArgs.size()];

      for (int i = 0; i < argResults.length; i++) {
        // Default evaluation is strict so errors will propagate (via thrown Java exception) before
        // unknowns.
        argResults[i] = evalInternal(frame, callArgs.get(i));
        // TODO: remove support for IncompleteData after migrating users to attribute
        // tracking unknowns.
        InterpreterUtil.completeDataOnly(
            argResults[i].value(), "Incomplete data does not support function calls.");
      }

      Optional<CelAttribute> indexAttr =
          maybeContainerIndexAttribute(reference.getOverloadId(0), argResults);

      CelAttribute attr = indexAttr.orElse(CelAttribute.EMPTY);

      Optional<Object> resolved = frame.resolveAttribute(attr);
      if (resolved.isPresent()) {
        return IntermediateResult.create(attr, resolved.get());
      }

      CallArgumentChecker argChecker =
          indexAttr.isPresent()
              ? CallArgumentChecker.createAcceptingPartial(frame.getResolver())
              : CallArgumentChecker.create(frame.getResolver());
      for (DefaultInterpreter.IntermediateResult element : argResults) {
        argChecker.checkArg(element);
      }
      Optional<Object> unknowns = argChecker.maybeUnknowns();
      if (unknowns.isPresent()) {
        return IntermediateResult.create(attr, unknowns.get());
      }

      Object[] argArray = Arrays.stream(argResults).map(v -> v.value()).toArray();

      return IntermediateResult.create(
          attr,
          dispatcher.dispatch(
              metadata,
              expr.getId(),
              callExpr.getFunction(),
              reference.getOverloadIdList(),
              argArray));
    }

    private Optional<CelAttribute> maybeContainerIndexAttribute(
        String overloadId, IntermediateResult[] args) {
      switch (overloadId) {
        case "index_list":
          if (args.length == 2 && args[1].value() instanceof Long) {
            return Optional.of(
                args[0].attribute().qualify(CelAttribute.Qualifier.ofInt((Long) args[1].value())));
          }
          break;
        case "index_map":
          if (args.length == 2) {
            try {
              CelAttribute.Qualifier qualifier =
                  CelAttribute.Qualifier.fromGeneric(args[1].value());
              return Optional.of(args[0].attribute().qualify(qualifier));
            } catch (IllegalArgumentException unused) {
              // This indicates a key type that's not a supported attribute qualifier
              // (e.g. dyn(b'bytes')). Instead of throwing here, allow dispatch to the possibly
              // custom index overload and return an appropriate result.
            }
          }
          break;
        default:
          break;
      }
      return Optional.empty();
    }

    private IntermediateResult evalConditional(ExecutionFrame frame, Call callExpr)
        throws InterpreterException {
      IntermediateResult condition = evalBooleanStrict(frame, callExpr.getArgs(0));
      if (isUnknownValue(condition.value())) {
        return condition;
      }
      if ((boolean) condition.value()) {
        return evalInternal(frame, callExpr.getArgs(1));
      }
      return evalInternal(frame, callExpr.getArgs(2));
    }

    private IntermediateResult mergeBooleanUnknowns(IntermediateResult lhs, IntermediateResult rhs)
        throws InterpreterException {
      // TODO: migrate clients to a common type that reports both expr-id unknowns
      // and attribute sets.
      if (lhs.value() instanceof CelUnknownSet && rhs.value() instanceof CelUnknownSet) {
        return IntermediateResult.create(
            ((CelUnknownSet) lhs.value()).merge((CelUnknownSet) rhs.value()));
      } else if (lhs.value() instanceof CelUnknownSet) {
        return lhs;
      } else if (rhs.value() instanceof CelUnknownSet) {
        return rhs;
      }

      // Otherwise fallback to normal impl
      return IntermediateResult.create(
          InterpreterUtil.shortcircuitUnknownOrThrowable(lhs.value(), rhs.value()));
    }

    private IntermediateResult evalLogicalOr(ExecutionFrame frame, Call callExpr)
        throws InterpreterException {
      IntermediateResult left = evalBooleanNonstrict(frame, callExpr.getArgs(0));
      if (left.value() instanceof Boolean && (Boolean) left.value()) {
        return left;
      }

      IntermediateResult right = evalBooleanNonstrict(frame, callExpr.getArgs(1));
      if (right.value() instanceof Boolean && (Boolean) right.value()) {
        return right;
      }

      // both false.
      if (right.value() instanceof Boolean && left.value() instanceof Boolean) {
        return left;
      }

      return mergeBooleanUnknowns(left, right);
    }

    private IntermediateResult evalLogicalAnd(ExecutionFrame frame, Call callExpr)
        throws InterpreterException {
      IntermediateResult left = evalBooleanNonstrict(frame, callExpr.getArgs(0));
      if (left.value() instanceof Boolean && !((Boolean) left.value())) {
        return left;
      }

      IntermediateResult right = evalBooleanNonstrict(frame, callExpr.getArgs(1));
      if (right.value() instanceof Boolean && !((Boolean) right.value())) {
        return right;
      }

      // both true.
      if (right.value() instanceof Boolean && left.value() instanceof Boolean) {
        return left;
      }

      return mergeBooleanUnknowns(left, right);
    }

    // Returns true unless the expression evaluates to false, in which case it returns false.
    // True is also returned if evaluation yields an error or an unknown set.
    private IntermediateResult evalNotStrictlyFalse(ExecutionFrame frame, Call callExpr) {
      try {
        IntermediateResult value = evalBooleanStrict(frame, callExpr.getArgs(0));
        if (value.value() instanceof Boolean) {
          return value;
        }
      } catch (Exception e) {
        /*nothing to do*/
      }
      return IntermediateResult.create(true);
    }

    private IntermediateResult evalType(ExecutionFrame frame, Call callExpr)
        throws InterpreterException {
      Expr typeExprArg = callExpr.getArgs(0);
      IntermediateResult argResult = evalInternal(frame, typeExprArg);
      Type checkedType = checkedExpr.getTypeMapMap().get(typeExprArg.getId());
      Value checkedTypeValue = typeProvider.adaptType(checkedType);
      Object typeValue = typeProvider.resolveObjectType(argResult.value(), checkedTypeValue);
      if (typeValue != null) {
        return IntermediateResult.create(typeValue);
      }
      throw new InterpreterException.Builder(
              "expected a runtime type for '%s', but found none.",
              argResult.getClass().getSimpleName())
          .setLocation(metadata, typeExprArg.getId())
          .build();
    }

    private IntermediateResult evalBoolean(ExecutionFrame frame, Expr expr, boolean strict)
        throws InterpreterException {
      IntermediateResult value = strict ? evalInternal(frame, expr) : evalNonstrictly(frame, expr);

      if (!(value.value() instanceof Boolean)
          && !isUnknownValue(value.value())
          && !(value.value() instanceof Exception)) {
        throw new InterpreterException.Builder("expected boolean value, found: %s", value.value())
            .setLocation(metadata, expr.getId())
            .build();
      }

      return value;
    }

    private IntermediateResult evalBooleanStrict(ExecutionFrame frame, Expr expr)
        throws InterpreterException {
      return evalBoolean(frame, expr, /* strict= */ true);
    }

    // Evaluate a non-strict boolean sub expression.
    // Behaves the same as non-strict eval, but throws an InterpreterException if the result
    // doesn't support CELs short-circuiting behavior (not an error, unknown or boolean).
    private IntermediateResult evalBooleanNonstrict(ExecutionFrame frame, Expr expr)
        throws InterpreterException {
      return evalBoolean(frame, expr, /* strict= */ false);
    }

    private IntermediateResult evalList(ExecutionFrame frame, Expr unusedExpr, CreateList listExpr)
        throws InterpreterException {

      CallArgumentChecker argChecker = CallArgumentChecker.create(frame.getResolver());
      List<Object> result = new ArrayList<>(listExpr.getElementsCount());

      for (int i = 0; i < listExpr.getElementsCount(); i++) {
        IntermediateResult element = evalInternal(frame, listExpr.getElements(i));
        // TODO: remove support for IncompleteData.
        InterpreterUtil.completeDataOnly(
            element.value(), "Incomplete data cannot be an elem of a list.");

        argChecker.checkArg(element);
        result.add(element.value());
      }

      return IntermediateResult.create(argChecker.maybeUnknowns().orElse(result));
    }

    private IntermediateResult evalStructMap(ExecutionFrame frame, CreateStruct structExpr)
        throws InterpreterException {

      CallArgumentChecker argChecker = CallArgumentChecker.create(frame.getResolver());

      Map<Object, Object> result = new LinkedHashMap<>();

      for (CreateStruct.Entry entry : structExpr.getEntriesList()) {
        IntermediateResult keyResult = evalInternal(frame, entry.getMapKey());
        argChecker.checkArg(keyResult);

        IntermediateResult valueResult = evalInternal(frame, entry.getValue());
        // TODO: remove support for IncompleteData.
        InterpreterUtil.completeDataOnly(
            valueResult.value(), "Incomplete data cannot be a value of a map.");
        argChecker.checkArg(valueResult);

        if (celOptions.errorOnDuplicateMapKeys() && result.containsKey(keyResult.value())) {
          throw new InterpreterException.Builder("duplicate map key [%s]", keyResult.value())
              .setLocation(metadata, entry.getId())
              .build();
        }
        result.put(keyResult.value(), valueResult.value());
      }

      return IntermediateResult.create(argChecker.maybeUnknowns().orElse(result));
    }

    private IntermediateResult evalStruct(ExecutionFrame frame, Expr expr, CreateStruct structExpr)
        throws InterpreterException {
      Reference reference = getReferenceOrDefault(expr.getId(), null);
      if (reference == null) {
        return evalStructMap(frame, structExpr);
      }

      // Message creation.
      CallArgumentChecker argChecker = CallArgumentChecker.create(frame.getResolver());
      Map<String, Object> fields = new HashMap<>();
      for (CreateStruct.Entry entry : structExpr.getEntriesList()) {
        IntermediateResult fieldResult = evalInternal(frame, entry.getValue());
        // TODO: remove support for IncompleteData
        InterpreterUtil.completeDataOnly(
            fieldResult.value(), "Incomplete data cannot be a field of a message.");
        argChecker.checkArg(fieldResult);

        fields.put(entry.getFieldKey(), fieldResult.value());
      }

      Optional<Object> unknowns = argChecker.maybeUnknowns();
      if (unknowns.isPresent()) {
        return IntermediateResult.create(unknowns.get());
      }
      return IntermediateResult.create(typeProvider.createMessage(reference.getName(), fields));
    }

    // Evaluates the expression and returns a value-or-throwable.
    // If evaluation results in a value, that value is returned directly.  If an exception
    // is thrown during evaluation, that exception itself is returned as the result.
    // Applying {@link #strict} to such a value-or-throwable recovers strict behavior.
    private IntermediateResult evalNonstrictly(ExecutionFrame frame, Expr expr) {
      try {
        return evalInternal(frame, expr);
      } catch (Exception e) {
        return IntermediateResult.create(e);
      }
    }

    @SuppressWarnings("unchecked")
    private IntermediateResult evalComprehension(
        ExecutionFrame frame, Expr unusedExpr, Comprehension compre) throws InterpreterException {
      String accuVar = compre.getAccuVar();
      String iterVar = compre.getIterVar();
      IntermediateResult iterRangeRaw = evalInternal(frame, compre.getIterRange());
      Collection<Object> iterRange;
      if (isUnknownValue(iterRangeRaw.value())) {
        return iterRangeRaw;
      }
      if (iterRangeRaw.value() instanceof List) {
        iterRange = (List<Object>) iterRangeRaw.value();
      } else if (iterRangeRaw.value() instanceof Map) {
        iterRange = ((Map<Object, Object>) iterRangeRaw.value()).keySet();
      } else {
        throw new InterpreterException.Builder(
                "expected a list or a map for iteration range but got '%s'",
                iterRangeRaw.value().getClass().getSimpleName())
            .setLocation(metadata, compre.getIterRange().getId())
            .build();
      }
      IntermediateResult accuValue = evalNonstrictly(frame, compre.getAccuInit());
      int i = 0;
      for (Object elem : iterRange) {
        frame.incrementIterations();

        CelAttribute iterAttr = CelAttribute.EMPTY;
        if (iterRange instanceof List) {
          iterAttr = iterRangeRaw.attribute().qualify(CelAttribute.Qualifier.ofInt(i));
        }
        i++;

        ImmutableMap<String, IntermediateResult> loopVars =
            ImmutableMap.of(
                iterVar,
                IntermediateResult.create(iterAttr, RuntimeHelpers.maybeAdaptPrimitive(elem)),
                accuVar,
                accuValue);

        frame.pushScope(loopVars);
        IntermediateResult evalObject = evalBooleanStrict(frame, compre.getLoopCondition());
        if (!isUnknownValue(evalObject.value()) && !(boolean) evalObject.value()) {
          frame.popScope();
          break;
        }
        accuValue = evalNonstrictly(frame, compre.getLoopStep());
        frame.popScope();
      }

      frame.pushScope(ImmutableMap.of(accuVar, accuValue));
      IntermediateResult result = evalInternal(frame, compre.getResult());
      frame.popScope();
      return result;
    }
  }

  /** This class tracks the state meaningful to a single evaluation pass. */
  private static class ExecutionFrame {
    private final int maxIterations;
    private final ArrayDeque<RuntimeUnknownResolver> resolvers;
    private RuntimeUnknownResolver currentResolver;
    private int iterations;

    private ExecutionFrame(RuntimeUnknownResolver resolver, int maxIterations) {
      this.resolvers = new ArrayDeque<>();
      this.resolvers.add(resolver);
      this.currentResolver = resolver;
      this.maxIterations = maxIterations;
    }

    private RuntimeUnknownResolver getResolver() {
      return currentResolver;
    }

    private void incrementIterations() throws InterpreterException {
      if (maxIterations < 0) {
        return;
      }
      if (++iterations > maxIterations) {
        throw new InterpreterException.Builder(
                String.format("Iteration budget exceeded: %d", maxIterations))
            .build();
      }
    }

    private IntermediateResult resolveSimpleName(String name, Long exprId) {
      return currentResolver.resolveSimpleName(name, exprId);
    }

    private Optional<Object> resolveAttribute(CelAttribute attr) {
      return currentResolver.resolveAttribute(attr);
    }

    private void pushScope(ImmutableMap<String, IntermediateResult> scope) {
      RuntimeUnknownResolver scopedResolver = currentResolver.withScope(scope);
      currentResolver = scopedResolver;
      resolvers.addLast(scopedResolver);
    }

    private void popScope() {
      if (resolvers.isEmpty()) {
        throw new IllegalStateException("Execution frame error: more scopes popped than pushed");
      }
      resolvers.removeLast();
      currentResolver = resolvers.getLast();
    }
  }
}
