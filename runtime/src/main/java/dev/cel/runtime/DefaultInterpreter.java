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
  public Interpretable createInterpretable(CheckedExpr checkedExpr) throws InterpreterException {
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
      IntermediateResult internalResult = evalInternal(resolver, checkedExpr.getExpr());
      Object result = internalResult.value();
      // TODO: remove support for IncompleteData.
      return InterpreterUtil.completeDataOnly(
          result, "Incomplete data cannot be returned as a result.");
    }

    private IntermediateResult evalInternal(RuntimeUnknownResolver resolver, Expr expr)
        throws InterpreterException {
      try {
        switch (expr.getExprKindCase()) {
          case CONST_EXPR:
            return IntermediateResult.create(evalConstant(resolver, expr, expr.getConstExpr()));
          case IDENT_EXPR:
            return evalIdent(resolver, expr, expr.getIdentExpr());
          case SELECT_EXPR:
            return evalSelect(resolver, expr, expr.getSelectExpr());
          case CALL_EXPR:
            return evalCall(resolver, expr, expr.getCallExpr());
          case LIST_EXPR:
            return evalList(resolver, expr, expr.getListExpr());
          case STRUCT_EXPR:
            return evalStruct(resolver, expr, expr.getStructExpr());
          case COMPREHENSION_EXPR:
            return evalComprehension(resolver, expr, expr.getComprehensionExpr());
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

    private Object evalConstant(
        RuntimeUnknownResolver unusedResolver, Expr unusedExpr, Constant constExpr) {
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

    private IntermediateResult evalIdent(
        RuntimeUnknownResolver resolver, Expr expr, Ident unusedIdent) throws InterpreterException {
      Reference reference = getReferenceOrThrow(expr.getId());
      if (reference.hasValue()) {
        return IntermediateResult.create(evalConstant(resolver, expr, reference.getValue()));
      }
      return resolveIdent(resolver, expr, reference.getName());
    }

    private IntermediateResult resolveIdent(RuntimeUnknownResolver resolver, Expr expr, String name)
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

      IntermediateResult rawResult = resolver.resolveSimpleName(name, expr.getId());

      // Value resolved from Binding, it could be Message, PartialMessage or unbound(null)
      Object value = InterpreterUtil.strict(typeProvider.adapt(rawResult.value()));
      return IntermediateResult.create(rawResult.attribute(), value);
    }

    private IntermediateResult evalSelect(
        RuntimeUnknownResolver resolver, Expr expr, Select selectExpr) throws InterpreterException {
      Reference reference = getReferenceOrDefault(expr.getId(), null);
      if (reference == null) {
        // This indicates this is a field selection on the operand.
        IntermediateResult operandResult = evalInternal(resolver, selectExpr.getOperand());
        Object operand = operandResult.value();

        CelAttribute attribute =
            operandResult
                .attribute()
                .qualify(CelAttribute.Qualifier.ofString(selectExpr.getField()));

        Optional<Object> attrValue = resolver.resolveAttribute(attribute);

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
        return IntermediateResult.create(evalConstant(resolver, expr, reference.getValue()));
      }
      return resolveIdent(resolver, expr, reference.getName());
    }

    private IntermediateResult evalCall(RuntimeUnknownResolver resolver, Expr expr, Call callExpr)
        throws InterpreterException {
      Reference reference = getReferenceOrThrow(expr.getId());
      Preconditions.checkState(reference.getOverloadIdCount() > 0);

      // Handle cases with special semantics. Those cannot have overloads.
      switch (reference.getOverloadId(0)) {
        case "identity":
          // Could be added as a binding to the dispatcher.  Handled here for parity
          // with FuturesInterpreter where the difference is slightly more significant.
          return evalInternal(resolver, callExpr.getArgs(0));
        case "conditional":
          return evalConditional(resolver, callExpr);
        case "logical_and":
          return evalLogicalAnd(resolver, callExpr);
        case "logical_or":
          return evalLogicalOr(resolver, callExpr);
        case "not_strictly_false":
          return evalNotStrictlyFalse(resolver, callExpr);
        case "type":
          return evalType(resolver, callExpr);
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
        argResults[i] = evalInternal(resolver, callArgs.get(i));
        // TODO: remove support for IncompleteData after migrating users to attribute
        // tracking unknowns.
        InterpreterUtil.completeDataOnly(
            argResults[i].value(), "Incomplete data does not support function calls.");
      }

      Optional<CelAttribute> indexAttr =
          maybeContainerIndexAttribute(reference.getOverloadId(0), argResults);

      CelAttribute attr = indexAttr.orElse(CelAttribute.EMPTY);

      Optional<Object> resolved = resolver.resolveAttribute(attr);
      if (resolved.isPresent()) {
        return IntermediateResult.create(attr, resolved.get());
      }

      CallArgumentChecker argChecker =
          indexAttr.isPresent()
              ? CallArgumentChecker.createAcceptingPartial(resolver)
              : CallArgumentChecker.create(resolver);
      for (int i = 0; i < argResults.length; i++) {
        argChecker.checkArg(argResults[i]);
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

    private IntermediateResult evalConditional(RuntimeUnknownResolver resolver, Call callExpr)
        throws InterpreterException {
      IntermediateResult condition = evalBooleanStrict(resolver, callExpr.getArgs(0));
      if (isUnknownValue(condition.value())) {
        return condition;
      }
      if ((boolean) condition.value()) {
        return evalInternal(resolver, callExpr.getArgs(1));
      }
      return evalInternal(resolver, callExpr.getArgs(2));
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

    private IntermediateResult evalLogicalOr(RuntimeUnknownResolver resolver, Call callExpr)
        throws InterpreterException {
      IntermediateResult left = evalBooleanNonstrict(resolver, callExpr.getArgs(0));
      if (left.value() instanceof Boolean && (Boolean) left.value()) {
        return left;
      }

      IntermediateResult right = evalBooleanNonstrict(resolver, callExpr.getArgs(1));
      if (right.value() instanceof Boolean && (Boolean) right.value()) {
        return right;
      }

      // both false.
      if (right.value() instanceof Boolean && left.value() instanceof Boolean) {
        return left;
      }

      return mergeBooleanUnknowns(left, right);
    }

    private IntermediateResult evalLogicalAnd(RuntimeUnknownResolver resolver, Call callExpr)
        throws InterpreterException {
      IntermediateResult left = evalBooleanNonstrict(resolver, callExpr.getArgs(0));
      if (left.value() instanceof Boolean && !((Boolean) left.value())) {
        return left;
      }

      IntermediateResult right = evalBooleanNonstrict(resolver, callExpr.getArgs(1));
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
    private IntermediateResult evalNotStrictlyFalse(
        RuntimeUnknownResolver resolver, Call callExpr) {
      try {
        IntermediateResult value = evalBooleanStrict(resolver, callExpr.getArgs(0));
        if (value.value() instanceof Boolean) {
          return value;
        }
      } catch (Exception e) {
        /*nothing to do*/
      }
      return IntermediateResult.create(true);
    }

    private IntermediateResult evalType(RuntimeUnknownResolver resolver, Call callExpr)
        throws InterpreterException {
      Expr typeExprArg = callExpr.getArgs(0);
      IntermediateResult argResult = evalInternal(resolver, typeExprArg);
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

    private IntermediateResult evalBoolean(
        RuntimeUnknownResolver resolver, Expr expr, boolean strict) throws InterpreterException {
      IntermediateResult value =
          strict ? evalInternal(resolver, expr) : evalNonstrictly(resolver, expr);

      if (!(value.value() instanceof Boolean)
          && !isUnknownValue(value.value())
          && !(value.value() instanceof Exception)) {
        throw new InterpreterException.Builder("expected boolean value, found: %s", value.value())
            .setLocation(metadata, expr.getId())
            .build();
      }

      return value;
    }

    private IntermediateResult evalBooleanStrict(RuntimeUnknownResolver resolver, Expr expr)
        throws InterpreterException {
      return evalBoolean(resolver, expr, /* strict= */ true);
    }

    // Evaluate a non-strict boolean sub expression.
    // Behaves the same as non-strict eval, but throws an InterpreterException if the result
    // doesn't support CELs short-circuiting behavior (not an error, unknown or boolean).
    private IntermediateResult evalBooleanNonstrict(RuntimeUnknownResolver resolver, Expr expr)
        throws InterpreterException {
      return evalBoolean(resolver, expr, /* strict= */ false);
    }

    private IntermediateResult evalList(
        RuntimeUnknownResolver resolver, Expr unusedExpr, CreateList listExpr)
        throws InterpreterException {

      CallArgumentChecker argChecker = CallArgumentChecker.create(resolver);
      List<Object> result = new ArrayList<>(listExpr.getElementsCount());

      for (int i = 0; i < listExpr.getElementsCount(); i++) {
        IntermediateResult element = evalInternal(resolver, listExpr.getElements(i));
        // TODO: remove support for IncompleteData.
        InterpreterUtil.completeDataOnly(
            element.value(), "Incomplete data cannot be an elem of a list.");

        argChecker.checkArg(element);
        result.add(element.value());
      }

      return IntermediateResult.create(argChecker.maybeUnknowns().orElse(result));
    }

    private IntermediateResult evalStructMap(
        RuntimeUnknownResolver resolver, CreateStruct structExpr) throws InterpreterException {

      CallArgumentChecker argChecker = CallArgumentChecker.create(resolver);

      Map<Object, Object> result = new LinkedHashMap<>();

      for (CreateStruct.Entry entry : structExpr.getEntriesList()) {
        IntermediateResult keyResult = evalInternal(resolver, entry.getMapKey());
        argChecker.checkArg(keyResult);

        IntermediateResult valueResult = evalInternal(resolver, entry.getValue());
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

    private IntermediateResult evalStruct(
        RuntimeUnknownResolver resolver, Expr expr, CreateStruct structExpr)
        throws InterpreterException {
      Reference reference = getReferenceOrDefault(expr.getId(), null);
      if (reference == null) {
        return evalStructMap(resolver, structExpr);
      }

      // Message creation.
      CallArgumentChecker argChecker = CallArgumentChecker.create(resolver);
      Map<String, Object> fields = new HashMap<>();
      for (CreateStruct.Entry entry : structExpr.getEntriesList()) {
        IntermediateResult fieldResult = evalInternal(resolver, entry.getValue());
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
    private IntermediateResult evalNonstrictly(RuntimeUnknownResolver resolver, Expr expr) {
      try {
        return evalInternal(resolver, expr);
      } catch (Exception e) {
        return IntermediateResult.create(e);
      }
    }

    @SuppressWarnings("unchecked")
    private IntermediateResult evalComprehension(
        RuntimeUnknownResolver resolver, Expr unusedExpr, Comprehension compre)
        throws InterpreterException {
      String accuVar = compre.getAccuVar();
      String iterVar = compre.getIterVar();
      IntermediateResult iterRangeRaw = evalInternal(resolver, compre.getIterRange());
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
      IntermediateResult accuValue = evalNonstrictly(resolver, compre.getAccuInit());
      int i = 0;
      for (Object elem : iterRange) {
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

        RuntimeUnknownResolver loopResolver = resolver.withScope(loopVars);
        IntermediateResult evalObject = evalBooleanStrict(loopResolver, compre.getLoopCondition());

        if (!isUnknownValue(evalObject.value()) && !(boolean) evalObject.value()) {
          break;
        }
        accuValue = evalNonstrictly(loopResolver, compre.getLoopStep());
      }

      RuntimeUnknownResolver resultResolver =
          resolver.withScope(ImmutableMap.of(accuVar, accuValue));

      return evalInternal(resultResolver, compre.getResult());
    }
  }
}
