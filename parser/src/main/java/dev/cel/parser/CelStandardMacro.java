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

package dev.cel.parser;

import com.google.common.collect.ImmutableSet;

/**
 * CelStandardMacro enum represents all of the macros defined as part of the CEL standard library.
 */
public enum CelStandardMacro {
  /** Field presence test macro */
  HAS(CelMacro.HAS),

  /**
   * Boolean comprehension which asserts that a predicate holds true for all elements in the input
   * range.
   */
  ALL(CelMacro.ALL),

  /**
   * Boolean comprehension which asserts that a predicate holds true for at least one element in the
   * input range.
   */
  EXISTS(CelMacro.EXISTS),

  /**
   * Boolean comprehension which asserts that a predicate holds true for exactly one element in the
   * input range.
   */
  EXISTS_ONE(CelMacro.EXISTS_ONE),

  /**
   * Comprehension which applies a transform to each element in the input range and produces a list
   * of equivalent size as output.
   */
  MAP(CelMacro.MAP),

  /**
   * Comprehension which conditionally applies a transform to elements in the list which satisfy the
   * filter predicate.
   */
  MAP_FILTER(CelMacro.MAP_FILTER),

  /**
   * Comprehension which produces a list containing elements in the input range which match the
   * filter.
   */
  FILTER(CelMacro.FILTER);

  /** Set of all standard macros supported by the CEL spec. */
  public static final ImmutableSet<CelStandardMacro> STANDARD_MACROS =
      ImmutableSet.of(HAS, ALL, EXISTS, EXISTS_ONE, MAP, MAP_FILTER, FILTER);

  private final CelMacro macro;

  CelStandardMacro(CelMacro macro) {
    this.macro = macro;
  }

  /** Returns the function name associated with the macro. */
  public String getFunction() {
    return macro.getFunction();
  }

  /** Returns the new-style {@code CelMacro} definition. */
  public CelMacro getDefinition() {
    return macro;
  }
}
