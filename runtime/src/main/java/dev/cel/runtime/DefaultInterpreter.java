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

import dev.cel.expr.CheckedExpr;
import dev.cel.expr.Value;
import com.google.auto.value.AutoValue;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.errorprone.annotations.Immutable;
import javax.annotation.concurrent.ThreadSafe;
import dev.cel.common.CelAbstractSyntaxTree;
import dev.cel.common.CelErrorCode;
import dev.cel.common.CelOptions;
import dev.cel.common.CelProtoAbstractSyntaxTree;
import dev.cel.common.annotations.Internal;
import dev.cel.common.ast.CelConstant;
import dev.cel.common.ast.CelExpr;
import dev.cel.common.ast.CelExpr.CelCall;
import dev.cel.common.ast.CelExpr.CelComprehension;
import dev.cel.common.ast.CelExpr.CelCreateList;
import dev.cel.common.ast.CelExpr.CelCreateStruct;
import dev.cel.common.ast.CelExpr.CelIdent;
import dev.cel.common.ast.CelExpr.CelSelect;
import dev.cel.common.ast.CelExpr.ExprKind;
import dev.cel.common.ast.CelReference;
import dev.cel.common.types.CelKind;
import dev.cel.common.types.CelType;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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

  /** {@inheritDoc} */
  @Override
  @Deprecated
  public Interpretable createInterpretable(CheckedExpr checkedExpr) {
    return createInterpretable(CelProtoAbstractSyntaxTree.fromCheckedExpr(checkedExpr).getAst());
  }

  /** {@inheritDoc} */
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
      this.typeProvider = Preconditions.checkNotNull(typeProvider);
      this.dispatcher = Preconditions.checkNotNull(dispatcher).immutableCopy();
      this.ast = Preconditions.checkNotNull(ast);
      this.metadata = new DefaultMetadata(ast);
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
      IntermediateResult internalResult = evalInternal(frame, ast.getExpr());
      Object result = internalResult.value();
      // TODO: remove support for IncompleteData.
      return InterpreterUtil.completeDataOnly(
          result, "Incomplete data cannot be returned as a result.");
    }

    private IntermediateResult evalInternal(ExecutionFrame frame, CelExpr expr)
        throws InterpreterException {
      try {
        ExprKind.Kind exprKind = expr.exprKind().getKind();
        switch (exprKind) {
          case CONSTANT:
            return IntermediateResult.create(evalConstant(frame, expr, expr.exprKind().constant()));
          case IDENT:
            return evalIdent(frame, expr, expr.exprKind().ident());
          case SELECT:
            return evalSelect(frame, expr, expr.exprKind().select());
          case CALL:
            return evalCall(frame, expr, expr.exprKind().call());
          case CREATE_LIST:
            return evalList(frame, expr, expr.exprKind().createList());
          case CREATE_STRUCT:
            return evalStruct(frame, expr, expr.exprKind().createStruct());
          case COMPREHENSION:
            return evalComprehension(frame, expr, expr.exprKind().comprehension());
          default:
            throw new IllegalStateException(
                "unexpected expression kind: " + expr.exprKind().getKind());
        }
      } catch (RuntimeException e) {
        throw new InterpreterException.Builder(e, e.getMessage())
            .setLocation(metadata, expr.id())
            .build();
      }
    }

    private boolean isUnknownValue(Object value) {
      return value instanceof CelUnknownSet || InterpreterUtil.isUnknown(value);
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

    private IntermediateResult evalIdent(ExecutionFrame frame, CelExpr expr, CelIdent unusedIdent)
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
        Object typeValue = typeProvider.adaptType(checkedType.get());
        return IntermediateResult.create(typeValue);
      }

      IntermediateResult rawResult = frame.resolveSimpleName(name, expr.id());

      // Value resolved from Binding, it could be Message, PartialMessage or unbound(null)
      Object value = InterpreterUtil.strict(typeProvider.adapt(rawResult.value()));
      return IntermediateResult.create(rawResult.attribute(), value);
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
        // TODO: remove support for IncompleteData after migrating users to attribute
        // tracking unknowns.
        InterpreterUtil.completeDataOnly(
            argResults[i].value(), "Incomplete data does not support function calls.");
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

      Object[] argArray = Arrays.stream(argResults).map(v -> v.value()).toArray();

      return IntermediateResult.create(
          attr,
          dispatcher.dispatch(
              metadata, expr.id(), callExpr.function(), reference.overloadIds(), argArray));
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
      if (isUnknownValue(condition.value())) {
        return condition;
      }
      if ((boolean) condition.value()) {
        return evalInternal(frame, callExpr.args().get(1));
      }
      return evalInternal(frame, callExpr.args().get(2));
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

    private IntermediateResult evalLogicalOr(ExecutionFrame frame, CelCall callExpr)
        throws InterpreterException {
      IntermediateResult left = evalBooleanNonstrict(frame, callExpr.args().get(0));
      if (left.value() instanceof Boolean && (Boolean) left.value()) {
        return left;
      }

      IntermediateResult right = evalBooleanNonstrict(frame, callExpr.args().get(1));
      if (right.value() instanceof Boolean && (Boolean) right.value()) {
        return right;
      }

      // both false.
      if (right.value() instanceof Boolean && left.value() instanceof Boolean) {
        return left;
      }

      return mergeBooleanUnknowns(left, right);
    }

    private IntermediateResult evalLogicalAnd(ExecutionFrame frame, CelCall callExpr)
        throws InterpreterException {
      IntermediateResult left = evalBooleanNonstrict(frame, callExpr.args().get(0));
      if (left.value() instanceof Boolean && !((Boolean) left.value())) {
        return left;
      }

      IntermediateResult right = evalBooleanNonstrict(frame, callExpr.args().get(1));
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

      Value checkedTypeValue = typeProvider.adaptType(checkedType);
      Object typeValue = typeProvider.resolveObjectType(argResult.value(), checkedTypeValue);
      return IntermediateResult.create(typeValue);
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

      String field = callExpr.args().get(1).exprKind().constant().stringValue();
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

      if (!(value.value() instanceof Boolean)
          && !isUnknownValue(value.value())
          && !(value.value() instanceof Exception)) {
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

    private IntermediateResult evalList(
        ExecutionFrame frame, CelExpr unusedExpr, CelCreateList listExpr)
        throws InterpreterException {

      CallArgumentChecker argChecker = CallArgumentChecker.create(frame.getResolver());
      List<Object> result = new ArrayList<>(listExpr.elements().size());

      for (CelExpr element : listExpr.elements()) {
        IntermediateResult evaluatedElement = evalInternal(frame, element);
        // TODO: remove support for IncompleteData.
        InterpreterUtil.completeDataOnly(
            evaluatedElement.value(), "Incomplete data cannot be an elem of a list.");

        argChecker.checkArg(evaluatedElement);
        result.add(evaluatedElement.value());
      }

      return IntermediateResult.create(argChecker.maybeUnknowns().orElse(result));
    }

    private IntermediateResult evalStructMap(ExecutionFrame frame, CelCreateStruct structExpr)
        throws InterpreterException {

      CallArgumentChecker argChecker = CallArgumentChecker.create(frame.getResolver());

      Map<Object, Object> result = new LinkedHashMap<>();

      for (CelCreateStruct.Entry entry : structExpr.entries()) {
        IntermediateResult keyResult = evalInternal(frame, entry.keyKind().mapKey());
        argChecker.checkArg(keyResult);

        IntermediateResult valueResult = evalInternal(frame, entry.value());
        // TODO: remove support for IncompleteData.
        InterpreterUtil.completeDataOnly(
            valueResult.value(), "Incomplete data cannot be a value of a map.");
        argChecker.checkArg(valueResult);

        if (celOptions.errorOnDuplicateMapKeys() && result.containsKey(keyResult.value())) {
          throw new InterpreterException.Builder("duplicate map key [%s]", keyResult.value())
              .setErrorCode(CelErrorCode.DUPLICATE_ATTRIBUTE)
              .setLocation(metadata, entry.id())
              .build();
        }
        result.put(keyResult.value(), valueResult.value());
      }

      return IntermediateResult.create(argChecker.maybeUnknowns().orElse(result));
    }

    private IntermediateResult evalStruct(
        ExecutionFrame frame, CelExpr expr, CelCreateStruct structExpr)
        throws InterpreterException {
      Optional<CelReference> reference = ast.getReference(expr.id());
      if (!reference.isPresent()) {
        return evalStructMap(frame, structExpr);
      }

      // Message creation.
      CallArgumentChecker argChecker = CallArgumentChecker.create(frame.getResolver());
      Map<String, Object> fields = new HashMap<>();
      for (CelCreateStruct.Entry entry : structExpr.entries()) {
        IntermediateResult fieldResult = evalInternal(frame, entry.value());
        // TODO: remove support for IncompleteData
        InterpreterUtil.completeDataOnly(
            fieldResult.value(), "Incomplete data cannot be a field of a message.");
        argChecker.checkArg(fieldResult);

        fields.put(entry.keyKind().fieldKey(), fieldResult.value());
      }

      Optional<Object> unknowns = argChecker.maybeUnknowns();
      if (unknowns.isPresent()) {
        return IntermediateResult.create(unknowns.get());
      }
      return IntermediateResult.create(typeProvider.createMessage(reference.get().name(), fields));
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
      IntermediateResult accuValue = evalNonstrictly(frame, compre.accuInit());
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
        IntermediateResult evalObject = evalBooleanStrict(frame, compre.loopCondition());
        if (!isUnknownValue(evalObject.value()) && !(boolean) evalObject.value()) {
          frame.popScope();
          break;
        }
        accuValue = evalNonstrictly(frame, compre.loopStep());
        frame.popScope();
      }

      frame.pushScope(ImmutableMap.of(accuVar, accuValue));
      IntermediateResult result = evalInternal(frame, compre.result());
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
