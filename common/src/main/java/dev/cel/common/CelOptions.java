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

package dev.cel.common;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableSet;
import com.google.errorprone.annotations.CheckReturnValue;
import com.google.errorprone.annotations.Immutable;

/**
 * Options to configure how the CEL parser, type-checker, and evaluator behave.
 *
 * <p>Users are strongly encouraged to use {@link #current} to ensure that the overall CEL stack
 * behaves in the manner most consistent with the CEL specification.
 */
@CheckReturnValue
@AutoValue
@Immutable
public abstract class CelOptions {

  public static final CelOptions DEFAULT = current().build();

  public static final CelOptions LEGACY = newBuilder().disableCelStandardEquality(true).build();

  // Package-private constructor to prevent extension.
  CelOptions() {}

  // Parser related options

  public abstract boolean enableReservedIds();

  public abstract boolean enableOptionalSyntax();

  public abstract int maxExpressionCodePointSize();

  public abstract int maxParseErrorRecoveryLimit();

  public abstract int maxParseRecursionDepth();

  public abstract boolean populateMacroCalls();

  public abstract boolean retainRepeatedUnaryOperators();

  public abstract boolean retainUnbalancedLogicalExpressions();

  // Type-Checker related options

  public abstract boolean enableCompileTimeOverloadResolution();

  public abstract boolean enableHomogeneousLiterals();

  public abstract boolean enableTimestampEpoch();

  public abstract boolean enableHeterogeneousNumericComparisons();

  public abstract boolean enableNamespacedDeclarations();

  // Evaluation related options

  public abstract boolean disableCelStandardEquality();

  public abstract boolean enableShortCircuiting();

  public abstract boolean enableRegexPartialMatch();

  public abstract boolean enableUnsignedComparisonAndArithmeticIsUnsigned();

  public abstract boolean enableUnsignedLongs();

  public abstract boolean enableProtoDifferencerEquality();

  public abstract boolean errorOnDuplicateMapKeys();

  public abstract boolean errorOnIntWrap();

  public abstract boolean resolveTypeDependencies();

  public abstract boolean enableUnknownTracking();

  public abstract boolean enableCelValue();

  public abstract int comprehensionMaxIterations();

  public abstract boolean unwrapWellKnownTypesOnFunctionDispatch();

  public abstract Builder toBuilder();

  public ImmutableSet<ExprFeatures> toExprFeatures() {
    ImmutableSet.Builder<ExprFeatures> features = ImmutableSet.builder();
    if (enableCompileTimeOverloadResolution()) {
      features.add(ExprFeatures.COMPILE_TIME_OVERLOAD_RESOLUTION);
    }
    if (disableCelStandardEquality()) {
      features.add(ExprFeatures.LEGACY_JAVA_EQUALITY);
    }
    if (enableHomogeneousLiterals()) {
      features.add(ExprFeatures.HOMOGENEOUS_LITERALS);
    }
    if (enableRegexPartialMatch()) {
      features.add(ExprFeatures.REGEX_PARTIAL_MATCH);
    }
    if (enableReservedIds()) {
      features.add(ExprFeatures.RESERVED_IDS);
    }
    if (enableUnsignedComparisonAndArithmeticIsUnsigned()) {
      features.add(ExprFeatures.UNSIGNED_COMPARISON_AND_ARITHMETIC_IS_UNSIGNED);
    }
    if (retainRepeatedUnaryOperators()) {
      features.add(ExprFeatures.RETAIN_REPEATED_UNARY_OPERATORS);
    }
    if (retainUnbalancedLogicalExpressions()) {
      features.add(ExprFeatures.RETAIN_UNBALANCED_LOGICAL_EXPRESSIONS);
    }
    if (errorOnIntWrap()) {
      features.add(ExprFeatures.ERROR_ON_WRAP);
    }
    if (errorOnDuplicateMapKeys()) {
      features.add(ExprFeatures.ERROR_ON_DUPLICATE_KEYS);
    }
    if (populateMacroCalls()) {
      features.add(ExprFeatures.POPULATE_MACRO_CALLS);
    }
    if (enableTimestampEpoch()) {
      features.add(ExprFeatures.ENABLE_TIMESTAMP_EPOCH);
    }
    if (enableHeterogeneousNumericComparisons()) {
      features.add(ExprFeatures.ENABLE_HETEROGENEOUS_NUMERIC_COMPARISONS);
    }
    if (enableNamespacedDeclarations()) {
      features.add(ExprFeatures.ENABLE_NAMESPACED_DECLARATIONS);
    }
    if (enableUnsignedLongs()) {
      features.add(ExprFeatures.ENABLE_UNSIGNED_LONGS);
    }
    if (enableProtoDifferencerEquality()) {
      features.add(ExprFeatures.PROTO_DIFFERENCER_EQUALITY);
    }

    return features.build();
  }

  /**
   * Return an unconfigured {@code Builder}. This is equivalent to preserving all legacy behaviors,
   * both good and bad, of the original CEL implementation.
   */
  public static Builder newBuilder() {
    return new AutoValue_CelOptions.Builder()
        // Parser options
        .enableReservedIds(false)
        .enableOptionalSyntax(false)
        .maxExpressionCodePointSize(100_000)
        .maxParseErrorRecoveryLimit(30)
        .maxParseRecursionDepth(250)
        .populateMacroCalls(false)
        .retainRepeatedUnaryOperators(false)
        .retainUnbalancedLogicalExpressions(false)
        // Type-Checker options
        .enableCompileTimeOverloadResolution(false)
        .enableHomogeneousLiterals(false)
        .enableTimestampEpoch(false)
        .enableHeterogeneousNumericComparisons(false)
        .enableNamespacedDeclarations(true)
        // Evaluation options
        .disableCelStandardEquality(true)
        .enableShortCircuiting(true)
        .enableRegexPartialMatch(false)
        .enableUnsignedComparisonAndArithmeticIsUnsigned(false)
        .enableUnsignedLongs(false)
        .enableProtoDifferencerEquality(false)
        .errorOnIntWrap(false)
        .errorOnDuplicateMapKeys(false)
        .resolveTypeDependencies(true)
        .enableUnknownTracking(false)
        .enableCelValue(false)
        .comprehensionMaxIterations(-1)
        .unwrapWellKnownTypesOnFunctionDispatch(true);
  }

  /**
   * Return a {@code Builder} configured with the most current set of {@code CelOptions}
   * (recommended).
   */
  public static Builder current() {
    return newBuilder()
        .enableReservedIds(true)
        .enableUnsignedComparisonAndArithmeticIsUnsigned(true)
        .enableRegexPartialMatch(true)
        .errorOnDuplicateMapKeys(true)
        .errorOnIntWrap(true)
        .resolveTypeDependencies(true)
        .disableCelStandardEquality(false);
  }

  public static CelOptions fromExprFeatures(ImmutableSet<ExprFeatures> features) {
    return newBuilder()
        .enableCompileTimeOverloadResolution(
            features.contains(ExprFeatures.COMPILE_TIME_OVERLOAD_RESOLUTION))
        .disableCelStandardEquality(features.contains(ExprFeatures.LEGACY_JAVA_EQUALITY))
        .enableHomogeneousLiterals(features.contains(ExprFeatures.HOMOGENEOUS_LITERALS))
        .enableRegexPartialMatch(features.contains(ExprFeatures.REGEX_PARTIAL_MATCH))
        .enableReservedIds(features.contains(ExprFeatures.RESERVED_IDS))
        .enableUnsignedComparisonAndArithmeticIsUnsigned(
            features.contains(ExprFeatures.UNSIGNED_COMPARISON_AND_ARITHMETIC_IS_UNSIGNED))
        .retainRepeatedUnaryOperators(
            features.contains(ExprFeatures.RETAIN_REPEATED_UNARY_OPERATORS))
        .retainUnbalancedLogicalExpressions(
            features.contains(ExprFeatures.RETAIN_UNBALANCED_LOGICAL_EXPRESSIONS))
        .errorOnIntWrap(features.contains(ExprFeatures.ERROR_ON_WRAP))
        .errorOnDuplicateMapKeys(features.contains(ExprFeatures.ERROR_ON_DUPLICATE_KEYS))
        .populateMacroCalls(features.contains(ExprFeatures.POPULATE_MACRO_CALLS))
        .enableTimestampEpoch(features.contains(ExprFeatures.ENABLE_TIMESTAMP_EPOCH))
        .enableHeterogeneousNumericComparisons(
            features.contains(ExprFeatures.ENABLE_HETEROGENEOUS_NUMERIC_COMPARISONS))
        .enableNamespacedDeclarations(
            features.contains(ExprFeatures.ENABLE_NAMESPACED_DECLARATIONS))
        .enableUnsignedLongs(features.contains(ExprFeatures.ENABLE_UNSIGNED_LONGS))
        .enableProtoDifferencerEquality(features.contains(ExprFeatures.PROTO_DIFFERENCER_EQUALITY))
        .build();
  }

  /** Builder for configuring the {@code CelOptions}. */
  @AutoValue.Builder
  public abstract static class Builder {
    // Package-private constructor to prevent extension.
    Builder() {}

    // Parser related builder options.

    /**
     * Check for use of reserved identifiers during parsing.
     *
     * <p>See <a href="https://github.com/google/cel-spec/blob/master/doc/langdef.md">the language
     * spec</a> for a list of reserved identifiers.
     */
    public abstract Builder enableReservedIds(boolean value);

    /**
     * EnableOptionalSyntax enables syntax for optional field and index selection (e.g: msg.?field).
     *
     * <p>Note: This option is automatically enabled for the parser by adding {@code
     * CelOptionalLibrary} to the environment.
     */
    public abstract Builder enableOptionalSyntax(boolean value);

    /**
     * Set a limit on the size of the expression string which may be parsed in terms of the number
     * of code points contained within the string.
     */
    public abstract Builder maxExpressionCodePointSize(int value);

    /**
     * Limit the number of error recovery attempts which may be made by the parser for a given
     * syntax error before terminating the parse completely.
     */
    public abstract Builder maxParseErrorRecoveryLimit(int value);

    /** Limit the amount of recursion within parse expressions. */
    public abstract Builder maxParseRecursionDepth(int value);

    /** Populate macro_calls map in source_info with macro calls parsed from the expression. */
    public abstract Builder populateMacroCalls(boolean value);

    /**
     * Retain all invocations of unary '-' and '!' that occur in source in the abstract syntax.
     *
     * <p>By default the parser collapses towers of repeated unary '-' and '!' into zero or one
     * instance by assuming these operators to be inverses of themselves. This behavior may not
     * always be desirable.
     */
    public abstract Builder retainRepeatedUnaryOperators(boolean value);

    /**
     * Retain the original grouping of logical connectives '&&' and '||' without attempting to
     * rebalance them in the abstract syntax.
     *
     * <p>The default rebalancing can reduce the overall nesting depth of the generated protos
     * representing abstract syntax, but it relies on associativity of the operations themselves.
     * This behavior may not always be desirable.
     */
    public abstract Builder retainUnbalancedLogicalExpressions(boolean value);

    // Type-Checker related options

    /**
     * Require overloads to resolve (narrow to a single candidate) during type checking.
     *
     * <p>This eliminates run-time overload dispatch and avoids implicit coercions of the result
     * type to type dyn. However, this configuration should only be used if your application is not
     * handling any form of dynamic data such as JSON or {@code google.protobuf.Any}.
     */
    public abstract Builder enableCompileTimeOverloadResolution(boolean value);

    /**
     * During type checking require list-, and map literals to be type-homogeneous in their
     * element-, key-, and value types, respectively.
     *
     * <p>Without this flag one can use type-mismatched elements, keys, and values, and the type
     * checker will implicitly coerce them to type dyn.
     *
     * <p>This flag is recommended for all new uses of CEL.
     *
     * @deprecated Use standalone {@code dev.cel.validators.validator.HomogeneousLiteralValidator}
     *     instead.
     */
    @Deprecated
    public abstract Builder enableHomogeneousLiterals(boolean value);

    /**
     * Enable the {@code int64_to_timestamp} overload which creates a timestamp from Uxix epoch
     * seconds.
     *
     * <p>This option will be automatically enabled after a sufficient period of time has elapsed to
     * ensure that all runtimes support the implementation.
     *
     * <p>TODO: Remove this feature once it has been auto-enabled.
     */
    public abstract Builder enableTimestampEpoch(boolean value);

    /**
     * Enable the {@code less_equals_double_int64} and other cross-type numeric comparisons for
     * {@code double}, {@code int64}, and {@code uint64} values.
     *
     * <p>This feature adds the declarations for these overloads to the CEL standard environment,
     * but the runtime functions are enabled by default.
     *
     * <p>TODO: Remove this feature once it has been auto-enabled.
     */
    public abstract Builder enableHeterogeneousNumericComparisons(boolean value);

    /**
     * Enables the usage of namespaced functions and identifiers. This causes the type-checker to
     * rewrite the AST to support namespacing (e.g: a field selector of form `namespace.msg.field`
     * gets rewritten as a fully qualified identifier name of `namespace.msg.field`).
     *
     * <p>TODO: Remove this feature once it has been auto-enabled.
     *
     * @deprecated This will be removed in the future. Please update your codebase to not rely on
     *     existence of certain expression nodes that would be collapsed/removed with this feature
     *     enabled.
     */
    @Deprecated
    public abstract Builder enableNamespacedDeclarations(boolean value);

    // Evaluation related options

    /**
     * Disable standard CEL equality in favor of the legacy Java equality calls for (in)equality
     * tests.
     *
     * <p>This feature is how the legacy CEL-Java APIs were originally configured, and in most cases
     * will yield identical results to CEL equality, with the exception that equality between
     * well-known protobuf types (wrapper types, {@code protobuf.Value}, {@code protobuf.Any}) may
     * not compare correctly to simple and aggregate CEL types.
     *
     * <p>Additionally, Java equality across numeric types such as {@code double} and {@code long},
     * will be trivially false, whereas CEL equality will compare the values as though they exist on
     * a continuous number line.
     */
    public abstract Builder disableCelStandardEquality(boolean value);

    /**
     * Enable short-circuiting of the logical operator evaluation. If enabled, AND, OR, and TERNARY
     * do not evaluate the entire expression once the resulting value is known from the left-hand
     * side.
     *
     * <p>This option is enabled by default. In most cases, this should not be disabled except for
     * debugging purposes or collecting results for all evaluated branches through {@link
     * dev.cel.runtime.CelEvaluationListener}.
     */
    public abstract Builder enableShortCircuiting(boolean value);

    /**
     * Treat regex {@code matches} calls as substring (unanchored) match patterns.
     *
     * <p>The default treatment for pattern matching within RE2 is full match within Java; however,
     * the CEL standard specifies that the matches() function is a substring match.
     */
    public abstract Builder enableRegexPartialMatch(boolean value);

    /**
     * Treat unsigned integers as unsigned when doing arithmetic and comparisons.
     *
     * <p>Prior to turning on this feature, attempts to perform arithmetic or comparisons on
     * unsigned integers larger than 2^63-1 may result in a runtime exception in the form of an
     * {@link java.lang.IllegalArgumentException}.
     */
    public abstract Builder enableUnsignedComparisonAndArithmeticIsUnsigned(boolean value);

    /**
     * Use {@code UnsignedLong} values to represent unsigned integers within CEL instead of the
     * nearest Java equivalent of {@code Long}.
     */
    public abstract Builder enableUnsignedLongs(boolean value);

    /**
     * Perform equality using {@code ProtoEquality} proto equality.
     *
     * <p>CEL's alternative implementation to the {@code Message#equals} comes with a few key
     * differences:
     *
     * <ul>
     *   <li/>NaN is not equal to itself.
     *   <li/>Any values are unpacked before comparison.
     *   <li/>If two Any values cannot be unpacked, they are compared by bytes.
     * </ul>
     */
    public abstract Builder enableProtoDifferencerEquality(boolean value);

    /** Throw errors on duplicate keys in map literals. */
    public abstract Builder errorOnDuplicateMapKeys(boolean value);

    /**
     * Throw errors when ints or uints wrap.
     *
     * <p>Prior to this feature, int and uint arithmetic wrapped, i.e. was coerced into range via
     * mod 2^64. The spec settled on throwing an error instead. Note that this makes arithmetic non-
     * associative.
     */
    public abstract Builder errorOnIntWrap(boolean value);

    /**
     * Enable or disable the resolution of {@code Descriptor} type dependencies as part of the CEL
     * environment setup. Defaults to disabled.
     *
     * <p>Disabling this feature should only be done when you know that only the types provided will
     * be referenced within the CEL expression. This means that either the type set provided was
     * complete, or that the type set is only what is referenced within expressions.
     */
    public abstract Builder resolveTypeDependencies(boolean value);

    /**
     * Enable tracking unknown attributes and function invocations encountered during evaluation.
     *
     * <p>When enabled, the evaluator will track unknown attributes (leaf values in the activations)
     * and function results (a particular invocation that was identified as unknown).
     */
    public abstract Builder enableUnknownTracking(boolean value);

    /**
     * Enables the usage of {@code CelValue} for the runtime. It is a native value representation of
     * CEL that wraps Java native objects, and comes with extended capabilities, such as allowing
     * value constructs not understood by CEL (ex: POJOs).
     *
     * <p>Warning: This option is experimental.
     */
    public abstract Builder enableCelValue(boolean value);

    /**
     * Limit the total number of iterations permitted within comprehension loops.
     *
     * <p>If the limit is reached, then an evaluation exception will be thrown. This limit also
     * affects nested comprehension interactions as well.
     *
     * <p>A negative {@code value} will disable max iteration checks.
     *
     * <p>Note: comprehension limits are not supported within the async CEL interpreter.
     */
    public abstract Builder comprehensionMaxIterations(int value);

    /**
     * If disabled, CEL runtime will no longer adapt the function dispatch results for protobuf's
     * well known types to other types. This option is enabled by default.
     *
     * @deprecated This will be removed in the future. Please update your codebase to be conformant
     *     with CEL specification.
     */
    @Deprecated
    public abstract Builder unwrapWellKnownTypesOnFunctionDispatch(boolean value);

    public abstract CelOptions build();
  }
}
