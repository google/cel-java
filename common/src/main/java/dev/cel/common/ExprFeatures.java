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

import com.google.common.collect.ImmutableSet;

/**
 * ExprFeatures are flags that alter how the CEL Java parser, checker, and interpreter behave.
 *
 * @deprecated Use {@code CelOptions} instead.
 */
@Deprecated
public enum ExprFeatures {

  /**
   * Require overloads to resolve (narrow to a single candidate) during type checking.
   *
   * <p>This eliminates run-time overload dispatch and avoids implicit coercions of the result type
   * to type dyn.
   */
  COMPILE_TIME_OVERLOAD_RESOLUTION,

  /**
   * When enabled use Java equality for (in)equality tests.
   *
   * <p>This feature is how the legacy CEL-Java APIs were originally configured, and in most cases
   * will yield identical results to CEL equality, with the exception that equality between
   * well-known protobuf types (wrapper types, {@code protobuf.Value}, {@code protobuf.Any}) may not
   * compare correctly to simple and aggregate CEL types.
   *
   * <p>Additionally, Java equality across numeric types such as {@code double} and {@code long},
   * will be trivially false, whereas CEL equality will compare the values as though they exist on a
   * continuous number line.
   */
  LEGACY_JAVA_EQUALITY,

  /**
   * During type checking require list-, and map literals to be type-homogeneous in their element-,
   * key-, and value types, respectively.
   *
   * <p>Without this flag one can use type-mismatched elements, keys, and values, and the type
   * checker will implicitly coerce them to type dyn.
   */
  HOMOGENEOUS_LITERALS,

  /**
   * Treat regex {@code matches} calls as substring (unanchored) match patterns.
   *
   * <p>The default treatment for pattern matching within RE2 is full match within Java; however,
   * the CEL standarda specifies that the matches() function is a substring match.
   */
  REGEX_PARTIAL_MATCH,

  /**
   * Check for use of reserved identifiers during parsing.
   *
   * <p>See <a href="https://github.com/google/cel-spec/blob/master/doc/langdef.md">the language
   * spec</a> for a list of reserved identifiers.
   */
  RESERVED_IDS,

  /**
   * Retain all invocations of unary '-' and '!' that occur in source in the abstract syntax.
   *
   * <p>By default the parser collapses towers of repeated unary '-' and '!' into zero or one
   * instance by assuming these operators to be inverses of themselves. This behavior may not always
   * be desirable.
   */
  RETAIN_REPEATED_UNARY_OPERATORS,

  /**
   * Retain the original grouping of logical connectives '&&' and '||' without attempting to
   * rebalance them in the abstract syntax.
   *
   * <p>The default rebalancing can reduce the overall nesting depth of the generated protos
   * representing abstract syntax, but it relies on associativity of the operations themselves. This
   * behavior may not always be desirable.
   */
  RETAIN_UNBALANCED_LOGICAL_EXPRESSIONS,

  /**
   * Treat unsigned integers as unsigned when doing arithmetic and comparisons.
   *
   * <p>Prior to turning on this feature, attempts to perform arithmetic or comparisons on unsigned
   * integers larger than 2^63-1 may result in a runtime exception in the form of an {@link
   * java.lang.IllegalArgumentException}.
   */
  UNSIGNED_COMPARISON_AND_ARITHMETIC_IS_UNSIGNED,

  /**
   * Throw errors when ints or uints wrap.
   *
   * <p>Prior to this feature, int and uint arithmetic wrapped, i.e. was coerced into range via mod
   * 2^64. The spec settled on throwing an error instead. Note that this makes arithmetic non-
   * associative.
   */
  ERROR_ON_WRAP,

  /** Error on duplicate keys in map literals. */
  ERROR_ON_DUPLICATE_KEYS,

  /** Populate macro_calls map in source_info with macro calls parsed from the expression. */
  POPULATE_MACRO_CALLS,

  /**
   * Enable the timestamp from epoch overload. This will automatically move to CURRENT after a two
   * month notice to consumers.
   *
   * <p>TODO: Remove this feature once it has been auto-enabled.
   */
  ENABLE_TIMESTAMP_EPOCH,

  /**
   * Enable numeric comparisons across types. This will automatically move to CURRENT after a two
   * month notice to consumers.
   *
   * <p>TODO: Remove this feature once it has been auto-enabled.
   */
  ENABLE_HETEROGENEOUS_NUMERIC_COMPARISONS,

  /**
   * Enable the using of {@code UnsignedLong} values in place of {@code Long} values for unsigned
   * integers.
   *
   * <p>Note, users must be careful not to supply {@code Long} values when {@code UnsignedLong}
   * values are intended.
   */
  ENABLE_UNSIGNED_LONGS,

  /**
   * Enable proto differencer based equality for messages. This is in place of Message.equals()
   * which has a slightly different behavior.
   */
  PROTO_DIFFERENCER_EQUALITY;

  /** Feature flags that enable the current best practices for CEL. */
  public static final ImmutableSet<ExprFeatures> CURRENT =
      ImmutableSet.of(
          REGEX_PARTIAL_MATCH,
          RESERVED_IDS,
          UNSIGNED_COMPARISON_AND_ARITHMETIC_IS_UNSIGNED,
          ERROR_ON_WRAP,
          ERROR_ON_DUPLICATE_KEYS);

  public static final ImmutableSet<ExprFeatures> LEGACY = ImmutableSet.of(LEGACY_JAVA_EQUALITY);
}
