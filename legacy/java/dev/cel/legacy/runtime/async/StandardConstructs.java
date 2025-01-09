package dev.cel.legacy.runtime.async;

import static com.google.common.util.concurrent.MoreExecutors.directExecutor;
import static dev.cel.legacy.runtime.async.EvaluationHelpers.asBoolean;
import static dev.cel.legacy.runtime.async.EvaluationHelpers.asBooleanExpression;
import static dev.cel.legacy.runtime.async.EvaluationHelpers.immediateException;
import static dev.cel.legacy.runtime.async.EvaluationHelpers.immediateValue;

import dev.cel.expr.Value;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.re2j.Pattern;
import dev.cel.common.CelErrorCode;
import dev.cel.common.CelOptions;
import dev.cel.runtime.InterpreterException;
import dev.cel.runtime.Metadata;
import dev.cel.runtime.RuntimeHelpers;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;
import javax.annotation.Nullable;

/**
 * Provides implementations of all "standard" constructs that are typically inlined by the CEL
 * interpreter.
 */
public final class StandardConstructs {

  private final TypeResolver typeResolver;
  private final CelOptions celOptions;

  public StandardConstructs(TypeResolver typeResolver, CelOptions celOptions) {
    this.typeResolver = typeResolver;
    this.celOptions = celOptions;
  }

  /** Adds all standard constructs to the given registrar. */
  // This method reference implements @Immutable interface SimpleCallConstructor, but the
  // declaration of type
  // 'dev.cel.legacy.runtime.async.async.StandardConstructs' is not annotated
  // with @com.google.errorprone.annotations.Immutable
  @SuppressWarnings("Immutable")
  public void addAllTo(FunctionRegistrar registrar) {
    /** Identity function. Calls of this are completely short-circuited and are, thus, "free". */
    registrar.addCallConstructor("identity", (md, id, args) -> args.get(0).expression());

    /** CEL ternary conditional expression: _ ? _ : _ */
    registrar.addCallConstructor("conditional", StandardConstructs::constructConditional);

    /** CEL logical AND: _ && _ */
    registrar.addCallConstructor(
        "logical_and", (md, id, args) -> constructLogicalConnective(true, md, args));

    /** CEL logical OR: _ || _ */
    registrar.addCallConstructor(
        "logical_or", (md, id, args) -> constructLogicalConnective(false, md, args));

    /** Special internal binding used by certain comprehension macros. */
    registrar.addCallConstructor(
        "not_strictly_false", StandardConstructs::constructNotStrictlyFalse);

    /** CEL "type" function. */
    registrar.addCallConstructor("type", this::constructType);

    /**
     * CEL regexp matching. This pulls the construction of the matcher into the preparation phase if
     * the regexp argument is constant.
     */
    registrar.addCallConstructor("matches", this::constructRegexpMatch);
    registrar.addCallConstructor("matches_string", this::constructRegexpMatch);

    /**
     * CEL list membership: _ in _. This turns constant lists into Java hash sets during the
     * preparation phase, for improved performance.
     */
    registrar.addCallConstructor(
        "in_list",
        (md, id, args) ->
            constructCollectionMembershipTest(
                args,
                (element, listObject) -> ((List<?>) listObject).contains(element),
                listObject -> ImmutableSet.copyOf((List<?>) listObject)));

    /**
     * CEL map key membership: _ in _. This turns the keysets of constant maps into Java hash sets
     * during the preparation phase, for improved performance.
     */
    registrar.addCallConstructor(
        "in_map",
        (md, id, args) ->
            constructCollectionMembershipTest(
                args,
                (element, mapObject) -> ((Map<?, ?>) mapObject).containsKey(element),
                mapObject -> ImmutableSet.copyOf(((Map<?, ?>) mapObject).keySet())));
  }

  /** Compiles the conditional operator {@code _?_:_} */
  private static CompiledExpression constructConditional(
      Metadata metadata, long exprId, List<IdentifiedCompiledExpression> compiledArguments)
      throws InterpreterException {
    IdentifiedCompiledExpression compiledCondition = compiledArguments.get(0);
    CompiledExpression compiledThen = compiledArguments.get(1).expression();
    CompiledExpression compiledElse = compiledArguments.get(2).expression();
    return asBooleanExpression(compiledCondition.expression(), metadata, compiledCondition.id())
        .map(
            (executableCondition, conditionEffect) -> {
              ExecutableExpression executableThen = compiledThen.toExecutable();
              ExecutableExpression executableElse = compiledElse.toExecutable();
              return CompiledExpression.executable(
                  stack ->
                      executableCondition
                          .execute(stack)
                          .transformAsync(
                              condition ->
                                  asBoolean(condition)
                                      ? executableThen.execute(stack)
                                      : executableElse.execute(stack),
                              directExecutor()),
                  conditionEffect.meet(compiledThen.effect()).meet(compiledElse.effect()));
            },
            constantCondition -> asBoolean(constantCondition) ? compiledThen : compiledElse,
            t -> CompiledExpression.throwing(t));
  }

  /**
   * Compile a logical connective (AND or OR). This implements short-circuiting on the first operand
   * that finishes execution.
   *
   * <p>The unit value is the neutral element of the operation in question. If one operand's value
   * is unit, then the result is whatever the other operand does. On the other hand, if one operand
   * yields the opposite of unit (= !unit), then the overall outcome is !unit regardless of the
   * other operand's behavior.
   */
  private static CompiledExpression constructLogicalConnective(
      boolean unit, Metadata metadata, List<IdentifiedCompiledExpression> compiledArguments)
      throws InterpreterException {
    IdentifiedCompiledExpression identifiedLeft = compiledArguments.get(0);
    IdentifiedCompiledExpression identifiedRight = compiledArguments.get(1);
    CompiledExpression compiledLeft =
        asBooleanExpression(identifiedLeft.expression(), metadata, identifiedLeft.id());
    CompiledExpression compiledRight =
        asBooleanExpression(identifiedRight.expression(), metadata, identifiedRight.id());
    if (compiledLeft.isConstant()) {
      return (asBoolean(compiledLeft.constant()) == unit) ? compiledRight : compiledLeft;
    }
    if (compiledRight.isConstant()) {
      return (asBoolean(compiledRight.constant()) == unit) ? compiledLeft : compiledRight;
    }

    if (compiledLeft.isThrowing() && compiledRight.isThrowing()) {
      // Both operands are throwing: arbitrarily pick the left exception to be propagated.
      return compiledLeft;
    }

    // Neither operand is constant and not both are simultaneously throwing, so perform everything
    // at runtime.
    ExecutableExpression executableLeft = compiledLeft.toExecutable();
    ExecutableExpression executableRight = compiledRight.toExecutable();
    Effect effect = compiledLeft.effect().meet(compiledRight.effect());

    return CompiledExpression.executable(
        stack -> {
          ImmutableList<ListenableFuture<Object>> orderedFutures =
              Futures.inCompletionOrder(
                  ImmutableList.of(executableLeft.execute(stack), executableRight.execute(stack)));
          FluentFuture<Object> firstFuture = FluentFuture.from(orderedFutures.get(0));
          FluentFuture<Object> secondFuture = FluentFuture.from(orderedFutures.get(1));
          return firstFuture
              .transformAsync(
                  first -> (asBoolean(first) == unit) ? secondFuture : immediateValue(!unit),
                  directExecutor())
              .catchingAsync(
                  Exception.class,
                  firstExn ->
                      secondFuture.transformAsync(
                          second ->
                              (asBoolean(second) == unit)
                                  ? immediateException(firstExn)
                                  : immediateValue(!unit),
                          directExecutor()),
                  directExecutor());
        },
        effect);
  }

  /** Compiles the internal "not_strictly_false" function. */
  private static CompiledExpression constructNotStrictlyFalse(
      Metadata metadata, long id, List<IdentifiedCompiledExpression> compiledArguments)
      throws InterpreterException {
    IdentifiedCompiledExpression identified = compiledArguments.get(0);
    CompiledExpression argument =
        asBooleanExpression(identified.expression(), metadata, identified.id());
    return argument.map(
        (executableArgument, effect) ->
            CompiledExpression.executable(
                stack ->
                    executableArgument
                        .execute(stack)
                        .catchingAsync(
                            Exception.class, t -> immediateValue(true), directExecutor()),
                effect),
        CompiledExpression::constant,
        t -> CompiledExpression.constant(true));
  }

  /** Compiles the CEL "type" function. */
  // This lambda implements @Immutable interface 'ExecutableExpression', but accesses instance
  // method(s) 'resolveType' on 'StandardConstructs' which is not @Immutable.
  @SuppressWarnings("Immutable")
  private CompiledExpression constructType(
      Metadata metadata, long id, List<IdentifiedCompiledExpression> compiledArguments)
      throws InterpreterException {
    IdentifiedCompiledExpression identified = compiledArguments.get(0);
    long argId = identified.id();
    Value checkedTypeValue = typeResolver.adaptType(identified.type());
    return identified
        .expression()
        .mapNonThrowing(
            e ->
                stack ->
                    e.execute(stack)
                        .transformAsync(
                            t -> immediateValue(resolveType(metadata, argId, t, checkedTypeValue)),
                            directExecutor()),
            c -> CompiledExpression.constant(resolveType(metadata, argId, c, checkedTypeValue)));
  }

  /** Helper used by the CEL "type" function implementation. */
  private Object resolveType(
      Metadata metadata, long exprId, Object obj, @Nullable Value checkedTypeValue)
      throws InterpreterException {
    @Nullable Object typeValue = typeResolver.resolveObjectType(obj, checkedTypeValue);
    if (typeValue != null) {
      return typeValue;
    }
    throw new InterpreterException.Builder(
            "expected a runtime type for '%s', but found none.", obj.getClass().getSimpleName())
        .setErrorCode(CelErrorCode.TYPE_NOT_FOUND)
        .setLocation(metadata, exprId)
        .build();
  }

  /** Compiles regular expression matching. */
  private CompiledExpression constructRegexpMatch(
      Metadata metadata, long id, List<IdentifiedCompiledExpression> compiledArguments)
      throws InterpreterException {
    CompiledExpression compiledString = compiledArguments.get(0).expression();
    CompiledExpression compiledPattern = compiledArguments.get(1).expression();
    return compiledPattern.map(
        (executableRegexp, regexpEffect) -> {
          ExecutableExpression executableString = compiledString.toExecutable();
          return CompiledExpression.executable(
              stack ->
                  executableString
                      .execute(stack)
                      .transformAsync(
                          string ->
                              executableRegexp
                                  .execute(stack)
                                  .transformAsync(
                                      regexp ->
                                          immediateValue(
                                              RuntimeHelpers.matches(
                                                  (String) string, (String) regexp, celOptions)),
                                      directExecutor()),
                          directExecutor()),
              regexpEffect.meet(compiledString.effect()));
        },
        constantRegexp -> {
          Pattern pattern = RuntimeHelpers.compilePattern((String) constantRegexp);
          return compiledString.mapNonThrowing(
              executableString ->
                  stack ->
                      executableString
                          .execute(stack)
                          .transformAsync(
                              string -> {
                                if (!celOptions.enableRegexPartialMatch()) {
                                  return immediateValue(pattern.matches((String) string));
                                }
                                return immediateValue(pattern.matcher((String) string).find());
                              },
                              directExecutor()),
              constantString ->
                  CompiledExpression.constant(
                      !celOptions.enableRegexPartialMatch()
                          ? pattern.matches((String) constantString)
                          : pattern.matcher((String) constantString).find()));
        },
        t -> CompiledExpression.throwing(t));
  }

  /** Compiles collection membership tests. */
  // This lambda implements @Immutable interface 'ExecutableExpression', but the declaration of type
  // 'java.util.function.BiFunction<java.lang.Object,java.lang.Object,java.lang.Boolean>' is not
  // annotated with @com.google.errorprone.annotations.Immutable
  @SuppressWarnings("Immutable")
  private static CompiledExpression constructCollectionMembershipTest(
      List<IdentifiedCompiledExpression> compiledArguments,
      BiFunction<Object, Object, Boolean> dynamicTest, // (element, collection) -> boolean
      Function<Object, ImmutableSet<Object>> makeConstantSet)
      throws InterpreterException { // collection -> set
    CompiledExpression compiledElement = compiledArguments.get(0).expression();
    CompiledExpression compiledCollection = compiledArguments.get(1).expression();
    return compiledCollection.map(
        (executableCollection, collectionEffect) -> {
          ExecutableExpression executableElement = compiledElement.toExecutable();
          return CompiledExpression.executable(
              stack ->
                  executableElement
                      .execute(stack)
                      .transformAsync(
                          element ->
                              executableCollection
                                  .execute(stack)
                                  .transformAsync(
                                      collection ->
                                          immediateValue(dynamicTest.apply(element, collection)),
                                      directExecutor()),
                          directExecutor()),
              collectionEffect.meet(compiledElement.effect()));
        },
        constantCollectionObject -> {
          ImmutableSet<Object> constantSet = makeConstantSet.apply(constantCollectionObject);
          return compiledElement.mapNonThrowing(
              executableElement ->
                  stack ->
                      executableElement
                          .execute(stack)
                          .transformAsync(
                              element -> immediateValue(constantSet.contains(element)),
                              directExecutor()),
              constantElement ->
                  CompiledExpression.constant(constantSet.contains(constantElement)));
        },
        t -> CompiledExpression.throwing(t));
  }
}
