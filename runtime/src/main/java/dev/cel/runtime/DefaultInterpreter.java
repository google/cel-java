// Copyright 2023 Google LLC
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

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.auto.value.AutoValue;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.errorprone.annotations.Immutable;
import javax.annotation.concurrent.ThreadSafe;
import dev.cel.common.CelAbstractSyntaxTree;
import dev.cel.common.CelErrorCode;
import dev.cel.common.CelException;
import dev.cel.common.CelOptions;
import dev.cel.common.annotations.Internal;
import dev.cel.common.ast.CelConstant;
import dev.cel.common.ast.CelExpr;
import dev.cel.common.ast.CelExpr.CelCall;
import dev.cel.common.ast.CelExpr.CelComprehension;
import dev.cel.common.ast.CelExpr.CelList;
import dev.cel.common.ast.CelExpr.CelMap;
import dev.cel.common.ast.CelExpr.CelSelect;
import dev.cel.common.ast.CelExpr.CelStruct;
import dev.cel.common.ast.CelExpr.ExprKind;
import dev.cel.common.ast.CelReference;
import dev.cel.common.types.CelKind;
import dev.cel.common.types.CelType;
import dev.cel.common.types.TypeType;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Default implementation of the CEL interpreter.
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

  /**
   * Creates a new interpreter
   *
   * @param typeProvider object which allows to construct and inspect messages.
   * @param dispatcher a method dispatcher.
   * @param celOptions the configurable flags for adjusting evaluation behavior.
   */
  public DefaultInterpreter(
      RuntimeTypeProvider typeProvider, Dispatcher dispatcher, CelOptions celOptions) {
    this.typeProvider = checkNotNull(typeProvider);
    this.dispatcher = checkNotNull(dispatcher);
    this.celOptions = celOptions;
  }

  @Override
  public Interpretable createInterpretable(CelAbstractSyntaxTree ast) {
    return new DefaultInterpretable(typeProvider, dispatcher, ast, celOptions);
  }

  @Immutable
  private static final class DefaultInterpretable
      implements Interpretable, UnknownTrackingInterpretable {
    private final RuntimeTypeProvider typeProvider;
    private final Dispatcher.ImmutableCopy dispatcher;
    private final Metadata metadata;
    private final CelAbstractSyntaxTree ast;
    private final CelOptions celOptions;

    DefaultInterpretable(
        RuntimeTypeProvider typeProvider,
        Dispatcher dispatcher,
        CelAbstractSyntaxTree ast,
        CelOptions celOptions) {
      this.typeProvider = checkNotNull(typeProvider);
      this.dispatcher = checkNotNull(dispatcher).immutableCopy();
      this.ast = checkNotNull(ast);
      this.metadata = new DefaultMetadata(ast);
      this.celOptions = checkNotNull(celOptions);
    }

    @Override
    public Object eval(GlobalResolver resolver) throws InterpreterException {
      // Result is already unwrapped from IntermediateResult.
      return eval(resolver, CelEvaluationListener.noOpListener());
    }

    @Override
    public Object eval(GlobalResolver resolver, CelEvaluationListener listener)
        throws InterpreterException {
      return evalTrackingUnknowns(
          RuntimeUnknownResolver.fromResolver(resolver), Optional.empty(), listener);
    }

    @Override
    public Object eval(
        GlobalResolver resolver,
        FunctionResolver lateBoundFunctionResolver,
        CelEvaluationListener listener)
        throws InterpreterException {
      return evalTrackingUnknowns(
          RuntimeUnknownResolver.fromResolver(resolver),
          Optional.of(lateBoundFunctionResolver),
          listener);
    }

    @Override
    public Object evalTrackingUnknowns(
        RuntimeUnknownResolver resolver,
        Optional<? extends FunctionResolver> functionResolver,
        CelEvaluationListener listener)
        throws InterpreterException {
      int comprehensionMaxIterations =
          celOptions.enableComprehension() ? celOptions.comprehensionMaxIterations() : 0;
      ExecutionFrame frame =
          new ExecutionFrame(listener, resolver, functionResolver, comprehensionMaxIterations);
      IntermediateResult internalResult = evalInternal(frame, ast.getExpr());
      return internalResult.value();
    }

    private IntermediateResult evalInternal(ExecutionFrame frame, CelExpr expr)
        throws InterpreterException {
      try {
        ExprKind.Kind exprKind = expr.exprKind().getKind();
        IntermediateResult result;
        switch (exprKind) {
          case CONSTANT:
            result = IntermediateResult.create(evalConstant(frame, expr, expr.constant()));
            break;
          case IDENT:
            result = evalIdent(frame, expr);
            break;
          case SELECT:
            result = evalSelect(frame, expr, expr.select());
            break;
          case CALL:
            result = evalCall(frame, expr, expr.call());
            break;
          case LIST:
            result = evalList(frame, expr, expr.list());
            break;
          case STRUCT:
            result = evalStruct(frame, expr, expr.struct());
            break;
          case MAP:
            result = evalMap(frame, expr.map());
            break;
          case COMPREHENSION:
            result = evalComprehension(frame, expr, expr.comprehension());
            break;
          default:
            throw new IllegalStateException(
                "unexpected expression kind: " + expr.exprKind().getKind());
        }
        frame.getEvaluationListener().callback(expr, result.value());
        return result;
      } catch (RuntimeException e) {
        throw new InterpreterException.Builder(e, e.getMessage())
            .setLocation(metadata, expr.id())
            .build();
      }
    }

    private static boolean isUnknownValue(Object value) {
      return value instanceof CelUnknownSet || InterpreterUtil.isUnknown(value);
    }

    private static boolean isUnknownOrError(Object value) {
      return isUnknownValue(value) || value instanceof Exception;
    }

    private Object evalConstant(
        ExecutionFrame unusedFrame, CelExpr unusedExpr, CelConstant constExpr) {
      switch (constExpr.getKind()) {
        case NULL_VALUE:
          return constExpr.nullValue();
        case BOOLEAN_VALUE:
          return constExpr.booleanValue();
        case INT64_VALUE:
          return constExpr.int64Value();
        case UINT64_VALUE:
          if (celOptions.enableUnsignedLongs()) {
            return constExpr.uint64Value();
          }
          // Legacy users without the unsigned longs option turned on
          return constExpr.uint64Value().longValue();
        case DOUBLE_VALUE:
          return constExpr.doubleValue();
        case STRING_VALUE:
          return constExpr.stringValue();
        case BYTES_VALUE:
          return constExpr.bytesValue();
        default:
          throw new IllegalStateException("unsupported constant case: " + constExpr.getKind());
      }
    }

    private IntermediateResult evalIdent(ExecutionFrame frame, CelExpr expr)
        throws InterpreterException {
      CelReference reference = ast.getReferenceOrThrow(expr.id());
      if (reference.value().isPresent()) {
        return IntermediateResult.create(evalConstant(frame, expr, reference.value().get()));
      }
      return resolveIdent(frame, expr, reference.name());
    }

    private IntermediateResult resolveIdent(ExecutionFrame frame, CelExpr expr, String name)
        throws InterpreterException {
      // Check whether the type exists in the type check map as a 'type'.
      Optional<CelType> checkedType = ast.getType(expr.id());
      if (checkedType.isPresent() && checkedType.get().kind() == CelKind.TYPE) {
        TypeType typeValue = CelTypeResolver.adaptType(checkedType.get());
        return IntermediateResult.create(typeValue);
      }

      IntermediateResult rawResult = frame.resolveSimpleName(name, expr.id());
      Object value = rawResult.value();
      boolean isLazyExpression = value instanceof LazyExpression;
      if (isLazyExpression) {
        value = evalInternal(frame, ((LazyExpression) value).celExpr).value();
      }

      // Value resolved from Binding, it could be Message, PartialMessage or unbound(null)
      value = InterpreterUtil.strict(typeProvider.adapt(value));
      IntermediateResult result = IntermediateResult.create(rawResult.attribute(), value);

      if (isLazyExpression) {
        frame.cacheLazilyEvaluatedResult(name, result);
      }

      return result;
    }

    private IntermediateResult evalSelect(ExecutionFrame frame, CelExpr expr, CelSelect selectExpr)
        throws InterpreterException {
      Optional<CelReference> referenceOptional = ast.getReference(expr.id());
      if (referenceOptional.isPresent()) {
        CelReference reference = referenceOptional.get();
        // This indicates it's a qualified name.
        if (reference.value().isPresent()) {
          // If the value is identified as a constant, skip attribute tracking.
          return IntermediateResult.create(evalConstant(frame, expr, reference.value().get()));
        }
        return resolveIdent(frame, expr, reference.name());
      }

      return evalFieldSelect(
          frame, expr, selectExpr.operand(), selectExpr.field(), selectExpr.testOnly());
    }

    private IntermediateResult evalFieldSelect(
        ExecutionFrame frame, CelExpr expr, CelExpr operandExpr, String field, boolean isTestOnly)
        throws InterpreterException {
      // This indicates this is a field selection on the operand.
      IntermediateResult operandResult = evalInternal(frame, operandExpr);
      Object operand = operandResult.value();

      CelAttribute attribute =
          operandResult.attribute().qualify(CelAttribute.Qualifier.ofString(field));

      Optional<Object> attrValue = frame.resolveAttribute(attribute);

      if (attrValue.isPresent()) {
        return IntermediateResult.create(attribute, attrValue.get());
      }

      // Nested message could be unknown
      if (isUnknownValue(operand)) {
        return IntermediateResult.create(attribute, operand);
      }

      if (isTestOnly) {
        return IntermediateResult.create(attribute, typeProvider.hasField(operand, field));
      }
      Object fieldValue = typeProvider.selectField(operand, field);

      return IntermediateResult.create(
          attribute, InterpreterUtil.valueOrUnknown(fieldValue, expr.id()));
    }

    private IntermediateResult evalCall(ExecutionFrame frame, CelExpr expr, CelCall callExpr)
        throws InterpreterException {
      CelReference reference = ast.getReferenceOrThrow(expr.id());
      Preconditions.checkState(!reference.overloadIds().isEmpty());

      // Handle cases with special semantics. Those cannot have overloads.
      switch (reference.overloadIds().get(0)) {
        case "identity":
          // Could be added as a binding to the dispatcher.  Handled here for parity
          // with FuturesInterpreter where the difference is slightly more significant.
          return evalInternal(frame, callExpr.args().get(0));
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
        case "optional_or_optional":
          return evalOptionalOr(frame, callExpr);
        case "optional_orValue_value":
          return evalOptionalOrValue(frame, callExpr);
        case "select_optional_field":
          Optional<IntermediateResult> result = maybeEvalOptionalSelectField(frame, expr, callExpr);
          if (result.isPresent()) {
            return result.get();
          }
          break;
        case "cel_block_list":
          return evalCelBlock(frame, expr, callExpr);
        default:
          break;
      }

      // Delegate handling of call to dispatcher.

      List<CelExpr> callArgs = new ArrayList<>();
      callExpr.target().ifPresent(callArgs::add);

      callArgs.addAll(callExpr.args());
      IntermediateResult[] argResults = new IntermediateResult[callArgs.size()];

      for (int i = 0; i < argResults.length; i++) {
        // Default evaluation is strict so errors will propagate (via thrown Java exception) before
        // unknowns.
        argResults[i] = evalInternal(frame, callArgs.get(i));
      }

      Optional<CelAttribute> indexAttr =
          maybeContainerIndexAttribute(reference.overloadIds().get(0), argResults);

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

      Object[] argArray = Arrays.stream(argResults).map(IntermediateResult::value).toArray();
      ImmutableList<String> overloadIds = reference.overloadIds();
      ResolvedOverload overload =
          findOverloadOrThrow(frame, expr, callExpr.function(), overloadIds, argArray);
      try {
        Object dispatchResult = overload.getDefinition().apply(argArray);
        if (celOptions.unwrapWellKnownTypesOnFunctionDispatch()) {
          dispatchResult = typeProvider.adapt(dispatchResult);
        }
        return IntermediateResult.create(attr, dispatchResult);
      } catch (CelException ce) {
        throw InterpreterException.wrapOrThrow(metadata, expr.id(), ce);
      } catch (RuntimeException e) {
        throw new InterpreterException.Builder(
                e,
                "Function '%s' failed with arg(s) '%s'",
                overload.getOverloadId(),
                Joiner.on(", ").join(argArray))
            .build();
      }
    }

    private ResolvedOverload findOverloadOrThrow(
        ExecutionFrame frame,
        CelExpr expr,
        String functionName,
        List<String> overloadIds,
        Object[] args)
        throws InterpreterException {
      try {
        Optional<ResolvedOverload> funcImpl =
            dispatcher.findOverload(functionName, overloadIds, args);
        if (funcImpl.isPresent()) {
          return funcImpl.get();
        }
        return frame
            .findOverload(functionName, overloadIds, args)
            .orElseThrow(
                () ->
                    new InterpreterException.Builder(
                            "No matching overload for function '%s'. Overload candidates: %s",
                            functionName, Joiner.on(",").join(overloadIds))
                        .setErrorCode(CelErrorCode.OVERLOAD_NOT_FOUND)
                        .setLocation(metadata, expr.id())
                        .build());
      } catch (CelException e) {
        throw InterpreterException.wrapOrThrow(metadata, expr.id(), e);
      }
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

    private IntermediateResult evalConditional(ExecutionFrame frame, CelCall callExpr)
        throws InterpreterException {
      IntermediateResult condition = evalBooleanStrict(frame, callExpr.args().get(0));
      if (celOptions.enableShortCircuiting()) {
        if (isUnknownValue(condition.value())) {
          return condition;
        }
        if ((boolean) condition.value()) {
          return evalInternal(frame, callExpr.args().get(1));
        }
        return evalInternal(frame, callExpr.args().get(2));
      } else {
        IntermediateResult lhs = evalNonstrictly(frame, callExpr.args().get(1));
        IntermediateResult rhs = evalNonstrictly(frame, callExpr.args().get(2));
        if (isUnknownValue(condition.value())) {
          return condition;
        }
        Object result =
            InterpreterUtil.strict((boolean) condition.value() ? lhs.value() : rhs.value());
        return IntermediateResult.create(result);
      }
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

    private enum ShortCircuitableOperators {
      LOGICAL_OR,
      LOGICAL_AND
    }

    private boolean canShortCircuit(IntermediateResult result, ShortCircuitableOperators operator) {
      if (!(result.value() instanceof Boolean)) {
        return false;
      }

      Boolean value = (Boolean) result.value();
      if (value && operator.equals(ShortCircuitableOperators.LOGICAL_OR)) {
        return true;
      }

      return !value && operator.equals(ShortCircuitableOperators.LOGICAL_AND);
    }

    private IntermediateResult evalLogicalOr(ExecutionFrame frame, CelCall callExpr)
        throws InterpreterException {
      IntermediateResult left;
      IntermediateResult right;
      if (celOptions.enableShortCircuiting()) {
        left = evalBooleanNonstrict(frame, callExpr.args().get(0));
        if (canShortCircuit(left, ShortCircuitableOperators.LOGICAL_OR)) {
          return left;
        }

        right = evalBooleanNonstrict(frame, callExpr.args().get(1));
        if (canShortCircuit(right, ShortCircuitableOperators.LOGICAL_OR)) {
          return right;
        }
      } else {
        left = evalBooleanNonstrict(frame, callExpr.args().get(0));
        right = evalBooleanNonstrict(frame, callExpr.args().get(1));
        if (canShortCircuit(left, ShortCircuitableOperators.LOGICAL_OR)) {
          return left;
        }
        if (canShortCircuit(right, ShortCircuitableOperators.LOGICAL_OR)) {
          return right;
        }
      }

      // both are booleans.
      if (right.value() instanceof Boolean && left.value() instanceof Boolean) {
        return IntermediateResult.create((Boolean) right.value() || (Boolean) left.value());
      }

      return mergeBooleanUnknowns(left, right);
    }

    private IntermediateResult evalLogicalAnd(ExecutionFrame frame, CelCall callExpr)
        throws InterpreterException {
      IntermediateResult left;
      IntermediateResult right;
      if (celOptions.enableShortCircuiting()) {
        left = evalBooleanNonstrict(frame, callExpr.args().get(0));
        if (canShortCircuit(left, ShortCircuitableOperators.LOGICAL_AND)) {
          return left;
        }

        right = evalBooleanNonstrict(frame, callExpr.args().get(1));
        if (canShortCircuit(right, ShortCircuitableOperators.LOGICAL_AND)) {
          return right;
        }
      } else {
        left = evalBooleanNonstrict(frame, callExpr.args().get(0));
        right = evalBooleanNonstrict(frame, callExpr.args().get(1));
        if (canShortCircuit(left, ShortCircuitableOperators.LOGICAL_AND)) {
          return left;
        }
        if (canShortCircuit(right, ShortCircuitableOperators.LOGICAL_AND)) {
          return right;
        }
      }

      // both are booleans.
      if (right.value() instanceof Boolean && left.value() instanceof Boolean) {
        return IntermediateResult.create((Boolean) right.value() && (Boolean) left.value());
      }

      return mergeBooleanUnknowns(left, right);
    }

    // Returns true unless the expression evaluates to false, in which case it returns false.
    // True is also returned if evaluation yields an error or an unknown set.
    private IntermediateResult evalNotStrictlyFalse(ExecutionFrame frame, CelCall callExpr) {
      try {
        IntermediateResult value = evalBooleanStrict(frame, callExpr.args().get(0));
        if (value.value() instanceof Boolean) {
          return value;
        }
      } catch (Exception e) {
        /*nothing to do*/
      }
      return IntermediateResult.create(true);
    }

    private IntermediateResult evalType(ExecutionFrame frame, CelCall callExpr)
        throws InterpreterException {
      CelExpr typeExprArg = callExpr.args().get(0);
      IntermediateResult argResult = evalInternal(frame, typeExprArg);
      // Type is a strict function. Early return if the argument is an error or an unknown.
      if (isUnknownOrError(argResult.value())) {
        return argResult;
      }

      CelType checkedType =
          ast.getType(typeExprArg.id())
              .orElseThrow(
                  () ->
                      new InterpreterException.Builder(
                              "expected a runtime type for '%s' from checked expression, but found"
                                  + " none.",
                              argResult.getClass().getSimpleName())
                          .setErrorCode(CelErrorCode.TYPE_NOT_FOUND)
                          .setLocation(metadata, typeExprArg.id())
                          .build());

      CelType checkedTypeValue = CelTypeResolver.adaptType(checkedType);
      return IntermediateResult.create(
          CelTypeResolver.resolveObjectType(argResult.value(), checkedTypeValue));
    }

    private IntermediateResult evalOptionalOr(ExecutionFrame frame, CelCall callExpr)
        throws InterpreterException {
      CelExpr lhsExpr = callExpr.target().get();
      IntermediateResult lhsResult = evalInternal(frame, lhsExpr);
      if (!(lhsResult.value() instanceof Optional)) {
        throw new InterpreterException.Builder(
                "expected optional value, found: %s", lhsResult.value())
            .setErrorCode(CelErrorCode.INVALID_ARGUMENT)
            .setLocation(metadata, lhsExpr.id())
            .build();
      }

      Optional<?> lhsOptionalValue = (Optional<?>) lhsResult.value();

      if (lhsOptionalValue.isPresent()) {
        // Short-circuit lhs if a value exists
        return lhsResult;
      }

      return evalInternal(frame, callExpr.args().get(0));
    }

    private IntermediateResult evalOptionalOrValue(ExecutionFrame frame, CelCall callExpr)
        throws InterpreterException {
      CelExpr lhsExpr = callExpr.target().get();
      IntermediateResult lhsResult = evalInternal(frame, lhsExpr);
      if (!(lhsResult.value() instanceof Optional)) {
        throw new InterpreterException.Builder(
                "expected optional value, found: %s", lhsResult.value())
            .setErrorCode(CelErrorCode.INVALID_ARGUMENT)
            .setLocation(metadata, lhsExpr.id())
            .build();
      }

      Optional<?> lhsOptionalValue = (Optional<?>) lhsResult.value();

      if (lhsOptionalValue.isPresent()) {
        // Short-circuit lhs if a value exists
        return IntermediateResult.create(lhsOptionalValue.get());
      }

      return evalInternal(frame, callExpr.args().get(0));
    }

    private Optional<IntermediateResult> maybeEvalOptionalSelectField(
        ExecutionFrame frame, CelExpr expr, CelCall callExpr) throws InterpreterException {
      CelExpr operand = callExpr.args().get(0);
      IntermediateResult lhsResult = evalInternal(frame, operand);
      if ((lhsResult.value() instanceof Map)) {
        // Let the function dispatch handle optional map indexing
        return Optional.empty();
      }

      String field = callExpr.args().get(1).constant().stringValue();
      boolean hasField = (boolean) typeProvider.hasField(lhsResult.value(), field);
      if (!hasField) {
        // Protobuf sets default (zero) values to uninitialized fields.
        // In case of CEL's optional values, we want to explicitly return Optional.none()
        // If the field is not set.
        return Optional.of(IntermediateResult.create(Optional.empty()));
      }

      IntermediateResult result = evalFieldSelect(frame, expr, operand, field, false);
      return Optional.of(
          IntermediateResult.create(result.attribute(), Optional.of(result.value())));
    }

    private IntermediateResult evalBoolean(ExecutionFrame frame, CelExpr expr, boolean strict)
        throws InterpreterException {
      IntermediateResult value = strict ? evalInternal(frame, expr) : evalNonstrictly(frame, expr);

      if (!(value.value() instanceof Boolean) && !isUnknownOrError(value.value())) {
        throw new InterpreterException.Builder("expected boolean value, found: %s", value.value())
            .setErrorCode(CelErrorCode.INVALID_ARGUMENT)
            .setLocation(metadata, expr.id())
            .build();
      }

      return value;
    }

    private IntermediateResult evalBooleanStrict(ExecutionFrame frame, CelExpr expr)
        throws InterpreterException {
      return evalBoolean(frame, expr, /* strict= */ true);
    }

    // Evaluate a non-strict boolean sub expression.
    // Behaves the same as non-strict eval, but throws an InterpreterException if the result
    // doesn't support CELs short-circuiting behavior (not an error, unknown or boolean).
    private IntermediateResult evalBooleanNonstrict(ExecutionFrame frame, CelExpr expr)
        throws InterpreterException {
      return evalBoolean(frame, expr, /* strict= */ false);
    }

    private IntermediateResult evalList(ExecutionFrame frame, CelExpr unusedExpr, CelList listExpr)
        throws InterpreterException {
      CallArgumentChecker argChecker = CallArgumentChecker.create(frame.getResolver());
      List<Object> result = new ArrayList<>(listExpr.elements().size());

      HashSet<Integer> optionalIndicesSet = new HashSet<>(listExpr.optionalIndices());
      ImmutableList<CelExpr> elements = listExpr.elements();
      for (int i = 0; i < elements.size(); i++) {
        CelExpr element = elements.get(i);
        IntermediateResult evaluatedElement = evalInternal(frame, element);

        argChecker.checkArg(evaluatedElement);
        Object value = evaluatedElement.value();
        if (!optionalIndicesSet
                .isEmpty() // Performance optimization to prevent autoboxing when there's no
            // optionals.
            && optionalIndicesSet.contains(i)
            && !isUnknownValue(value)) {
          Optional<?> optionalVal = (Optional<?>) value;
          if (!optionalVal.isPresent()) {
            continue;
          }

          value = optionalVal.get();
        }

        result.add(value);
      }

      return IntermediateResult.create(argChecker.maybeUnknowns().orElse(result));
    }

    private IntermediateResult evalMap(ExecutionFrame frame, CelMap mapExpr)
        throws InterpreterException {

      CallArgumentChecker argChecker = CallArgumentChecker.create(frame.getResolver());

      Map<Object, Object> result = new LinkedHashMap<>();

      for (CelMap.Entry entry : mapExpr.entries()) {
        IntermediateResult keyResult = evalInternal(frame, entry.key());
        argChecker.checkArg(keyResult);

        IntermediateResult valueResult = evalInternal(frame, entry.value());
        argChecker.checkArg(valueResult);

        if (celOptions.errorOnDuplicateMapKeys() && result.containsKey(keyResult.value())) {
          throw new InterpreterException.Builder("duplicate map key [%s]", keyResult.value())
              .setErrorCode(CelErrorCode.DUPLICATE_ATTRIBUTE)
              .setLocation(metadata, entry.id())
              .build();
        }

        Object value = valueResult.value();
        if (entry.optionalEntry() && !isUnknownValue(value)) {
          Optional<?> optionalVal = (Optional<?>) value;
          if (!optionalVal.isPresent()) {
            // This is a no-op currently but will be semantically correct when extended proto
            // support allows proto mutation.
            result.remove(keyResult.value());
            continue;
          }

          value = optionalVal.get();
        }
        result.put(keyResult.value(), value);
      }

      return IntermediateResult.create(argChecker.maybeUnknowns().orElse(result));
    }

    private IntermediateResult evalStruct(ExecutionFrame frame, CelExpr expr, CelStruct structExpr)
        throws InterpreterException {
      CelReference reference =
          ast.getReference(expr.id())
              .orElseThrow(
                  () ->
                      new IllegalStateException(
                          "Could not find a reference for CelStruct expression at ID: "
                              + expr.id()));

      // Message creation.
      CallArgumentChecker argChecker = CallArgumentChecker.create(frame.getResolver());
      Map<String, Object> fields = new HashMap<>();
      for (CelStruct.Entry entry : structExpr.entries()) {
        IntermediateResult fieldResult = evalInternal(frame, entry.value());
        argChecker.checkArg(fieldResult);

        Object value = fieldResult.value();
        if (entry.optionalEntry()) {
          Optional<?> optionalVal = (Optional<?>) value;
          if (!optionalVal.isPresent()) {
            // This is a no-op currently but will be semantically correct when extended proto
            // support allows proto mutation.
            fields.remove(entry.fieldKey());
            continue;
          }

          value = optionalVal.get();
        }

        fields.put(entry.fieldKey(), value);
      }

      return argChecker
          .maybeUnknowns()
          .map(IntermediateResult::create)
          .orElseGet(
              () ->
                  IntermediateResult.create(typeProvider.createMessage(reference.name(), fields)));
    }

    // Evaluates the expression and returns a value-or-throwable.
    // If evaluation results in a value, that value is returned directly.  If an exception
    // is thrown during evaluation, that exception itself is returned as the result.
    // Applying {@link #strict} to such a value-or-throwable recovers strict behavior.
    private IntermediateResult evalNonstrictly(ExecutionFrame frame, CelExpr expr) {
      try {
        return evalInternal(frame, expr);
      } catch (Exception e) {
        return IntermediateResult.create(e);
      }
    }

    @SuppressWarnings("unchecked")
    private IntermediateResult evalComprehension(
        ExecutionFrame frame, CelExpr unusedExpr, CelComprehension compre)
        throws InterpreterException {
      String accuVar = compre.accuVar();
      String iterVar = compre.iterVar();
      IntermediateResult iterRangeRaw = evalInternal(frame, compre.iterRange());
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
            .setErrorCode(CelErrorCode.INVALID_ARGUMENT)
            .setLocation(metadata, compre.iterRange().id())
            .build();
      }
      IntermediateResult accuValue;
      if (LazyExpression.isLazilyEvaluable(compre)) {
        accuValue = IntermediateResult.create(new LazyExpression(compre.accuInit()));
      } else {
        accuValue = evalNonstrictly(frame, compre.accuInit());
      }
      int i = 0;
      for (Object elem : iterRange) {
        frame.incrementIterations();

        CelAttribute iterAttr = CelAttribute.EMPTY;
        if (iterRange instanceof List) {
          iterAttr = iterRangeRaw.attribute().qualify(CelAttribute.Qualifier.ofInt(i));
        }
        i++;

        Map<String, IntermediateResult> loopVars = new HashMap<>();
        loopVars.put(
            iterVar, IntermediateResult.create(iterAttr, RuntimeHelpers.maybeAdaptPrimitive(elem)));
        loopVars.put(accuVar, accuValue);

        frame.pushScope(Collections.unmodifiableMap(loopVars));
        IntermediateResult evalObject = evalBooleanStrict(frame, compre.loopCondition());
        if (!isUnknownValue(evalObject.value()) && !(boolean) evalObject.value()) {
          frame.popScope();
          break;
        }
        accuValue = evalNonstrictly(frame, compre.loopStep());
        frame.popScope();
      }

      frame.pushScope(Collections.singletonMap(accuVar, accuValue));
      IntermediateResult result = evalInternal(frame, compre.result());
      frame.popScope();
      return result;
    }

    private IntermediateResult evalCelBlock(
        ExecutionFrame frame, CelExpr unusedExpr, CelCall blockCall) throws InterpreterException {
      CelList exprList = blockCall.args().get(0).list();
      Map<String, IntermediateResult> blockList = new HashMap<>();
      for (int index = 0; index < exprList.elements().size(); index++) {
        // Register the block indices as lazily evaluated expressions stored as unique identifiers.
        blockList.put(
            "@index" + index,
            IntermediateResult.create(new LazyExpression(exprList.elements().get(index))));
      }
      frame.pushScope(Collections.unmodifiableMap(blockList));

      return evalInternal(frame, blockCall.args().get(1));
    }
  }

  /** Contains a CelExpr that is to be lazily evaluated. */
  private static class LazyExpression {
    private final CelExpr celExpr;

    /**
     * Checks whether the provided expression can be evaluated lazily then cached. For example, the
     * accumulator initializer in `cel.bind` macro is a good candidate because it never needs to be
     * updated after being evaluated once.
     */
    private static boolean isLazilyEvaluable(CelComprehension comprehension) {
      // For now, just handle cel.bind. cel.block will be a future addition.
      return comprehension
              .loopCondition()
              .constantOrDefault()
              .getKind()
              .equals(CelConstant.Kind.BOOLEAN_VALUE)
          && !comprehension.loopCondition().constant().booleanValue()
          && comprehension.iterVar().equals("#unused")
          && comprehension.iterRange().exprKind().getKind().equals(ExprKind.Kind.LIST)
          && comprehension.iterRange().list().elements().isEmpty();
    }

    private LazyExpression(CelExpr celExpr) {
      this.celExpr = celExpr;
    }
  }

  /** This class tracks the state meaningful to a single evaluation pass. */
  private static class ExecutionFrame {
    private final CelEvaluationListener evaluationListener;
    private final int maxIterations;
    private final ArrayDeque<RuntimeUnknownResolver> resolvers;
    private final Optional<? extends FunctionResolver> lateBoundFunctionResolver;
    private RuntimeUnknownResolver currentResolver;
    private int iterations;

    private ExecutionFrame(
        CelEvaluationListener evaluationListener,
        RuntimeUnknownResolver resolver,
        Optional<? extends FunctionResolver> lateBoundFunctionResolver,
        int maxIterations) {
      this.evaluationListener = evaluationListener;
      this.resolvers = new ArrayDeque<>();
      this.resolvers.add(resolver);
      this.lateBoundFunctionResolver = lateBoundFunctionResolver;
      this.currentResolver = resolver;
      this.maxIterations = maxIterations;
    }

    private CelEvaluationListener getEvaluationListener() {
      return evaluationListener;
    }

    private RuntimeUnknownResolver getResolver() {
      return currentResolver;
    }

    private Optional<ResolvedOverload> findOverload(
        String function, List<String> overloadIds, Object[] args) throws CelException {
      if (lateBoundFunctionResolver.isPresent()) {
        return lateBoundFunctionResolver.get().findOverload(function, overloadIds, args);
      }
      return Optional.empty();
    }

    private void incrementIterations() throws InterpreterException {
      if (maxIterations < 0) {
        return;
      }
      if (++iterations > maxIterations) {
        throw new InterpreterException.Builder(
                String.format("Iteration budget exceeded: %d", maxIterations))
            .setErrorCode(CelErrorCode.ITERATION_BUDGET_EXCEEDED)
            .build();
      }
    }

    private IntermediateResult resolveSimpleName(String name, Long exprId) {
      return currentResolver.resolveSimpleName(name, exprId);
    }

    private Optional<Object> resolveAttribute(CelAttribute attr) {
      return currentResolver.resolveAttribute(attr);
    }

    private void cacheLazilyEvaluatedResult(
        String name, DefaultInterpreter.IntermediateResult result) {
      currentResolver.cacheLazilyEvaluatedResult(name, result);
    }

    /** Note: we utilize a HashMap instead of ImmutableMap to make lookups faster on string keys. */
    private void pushScope(Map<String, IntermediateResult> scope) {
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
