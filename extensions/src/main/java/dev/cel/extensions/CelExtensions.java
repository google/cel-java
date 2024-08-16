// Copyright 2024 Google LLC
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

package dev.cel.extensions;

import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static java.util.Arrays.stream;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Streams;
import dev.cel.common.CelOptions;
import java.util.Set;

/**
 * Collections of CEL Extensions.
 *
 * <p>To use, supply the desired extensions into {@code CelCompiler} and {@code CelRuntime} through
 * their builders: {@code CelCompilerBuilder#addLibraries} and {@code
 * CelRuntimeBuilder#addLibraries}.
 */
public final class CelExtensions {
  private static final CelStringExtensions STRING_EXTENSIONS_ALL = new CelStringExtensions();
  private static final CelProtoExtensions PROTO_EXTENSIONS = new CelProtoExtensions();
  private static final CelBindingsExtensions BINDINGS_EXTENSIONS = new CelBindingsExtensions();
  private static final CelEncoderExtensions ENCODER_EXTENSIONS = new CelEncoderExtensions();
  private static final CelListsExtensions LISTS_EXTENSIONS_ALL = new CelListsExtensions();

  /**
   * Extended functions for string manipulation.
   *
   * <p>Refer to README.md for available functions.
   *
   * <p>This will include all functions denoted in {@link CelStringExtensions.Function}, including
   * any future additions. To expose only a subset of functions, use {@link
   * #strings(CelStringExtensions.Function...)} instead.
   */
  public static CelStringExtensions strings() {
    return STRING_EXTENSIONS_ALL;
  }

  /**
   * Extended functions for string manipulation.
   *
   * <p>Refer to README.md for available functions.
   *
   * <p>This will include only the specific functions denoted by {@link
   * CelStringExtensions.Function}.
   */
  public static CelStringExtensions strings(CelStringExtensions.Function... functions) {
    return strings(ImmutableSet.copyOf(functions));
  }

  /**
   * Extended functions for string manipulation.
   *
   * <p>Refer to README.md for available functions.
   *
   * <p>This will include only the specific functions denoted by {@link
   * CelStringExtensions.Function}.
   */
  public static CelStringExtensions strings(Set<CelStringExtensions.Function> functions) {
    return new CelStringExtensions(functions);
  }

  /**
   * Extended macros for proto manipulation.
   *
   * <p>This adds {@code proto.getExt} and {@code proto.hasExt}. See README.md for their
   * documentation.
   *
   * <p>Note, these functions use the 'proto' namespace; however, at the time of macro expansion the
   * namespace looks just like any other identifier. If you are currently using a variable named
   * 'proto', the macro will likely work just as intended; however, there is some chance for
   * collision.
   */
  public static CelProtoExtensions protos() {
    return PROTO_EXTENSIONS;
  }

  /**
   * Extended math helper macros and functions.
   *
   * <p>This adds {@code math.greatest} and {@code math.least}. See README.md for their
   * documentation.
   *
   * <p>Note, all macros use the 'math' namespace; however, at the time of macro expansion the
   * namespace looks just like any other identifier. If you are currently using a variable named
   * 'math', the macro will likely work just as intended; however, there is some chance for
   * collision.
   *
   * <p>This will include all functions denoted in {@link CelMathExtensions.Function}, including any
   * future additions. To expose only a subset of these, use {@link #math(CelOptions,
   * CelMathExtensions.Function...)} instead.
   *
   * @param celOptions CelOptions to configure CelMathExtension with. This should be the same
   *     options object used to configure the compilation/runtime environments.
   */
  public static CelMathExtensions math(CelOptions celOptions) {
    return new CelMathExtensions(celOptions);
  }

  /**
   * Extended math helper macros and functions.
   *
   * <p>This adds {@code math.greatest} and {@code math.least}. See README.md for their
   * documentation.
   *
   * <p>Note, all macros use the 'math' namespace; however, at the time of macro * expansion the
   * namespace looks just like any other identifier. If you are currently using a variable named
   * 'math', the macro will likely work just as intended; however, there is some chance for
   * collision.
   *
   * <p>This will include only the specific functions denoted by {@link CelMathExtensions.Function}.
   *
   * @param celOptions CelOptions to configure CelMathExtension with. This should be the same
   *     options object used to configure the compilation/runtime environments.
   */
  public static CelMathExtensions math(
      CelOptions celOptions, CelMathExtensions.Function... functions) {
    return math(celOptions, ImmutableSet.copyOf(functions));
  }

  /**
   * Extended math helper macros and functions.
   *
   * <p>This adds {@code math.greatest} and {@code math.least}. See README.md for their
   * documentation.
   *
   * <p>Note, all macros use the 'math' namespace; however, at the time of macro * expansion the
   * namespace looks just like any other identifier. If you are currently using a variable named
   * 'math', the macro will likely work just as intended; however, there is some chance for
   * collision.
   *
   * <p>This will include only the specific functions denoted by {@link CelMathExtensions.Function}.
   *
   * @param celOptions CelOptions to configure CelMathExtension with. This should be the same
   *     options object used to configure the compilation/runtime environments.
   */
  public static CelMathExtensions math(
      CelOptions celOptions, Set<CelMathExtensions.Function> functions) {
    return new CelMathExtensions(celOptions, functions);
  }

  /**
   * Local variable binding macro(s).
   *
   * <p>This exposes {@code cel.bind} as a macro for use within expressions. See README.md for its
   * documentation.
   *
   * <p>This change does not introduce any new functions, but rather leverages the comprehension AST
   * to produce an expression which will introduce a new variable into the expression.
   */
  public static CelBindingsExtensions bindings() {
    return BINDINGS_EXTENSIONS;
  }

  /**
   * Extended functions for string, byte and object encodings.
   *
   * <p>This adds {@code base64.encode} and {@code base64.decode} functions. See README.md for their
   * documentation.
   */
  public static CelEncoderExtensions encoders() {
    return ENCODER_EXTENSIONS;
  }

  /**
   * @deprecated Use {@link #sets(CelOptions)} instead.
   */
  @Deprecated
  public static CelSetsExtensions sets() {
    return sets(CelOptions.DEFAULT);
  }

  /**
   * Extended functions for Set manipulation.
   *
   * <p>Refer to README.md for available functions.
   *
   * <p>This will include all functions denoted in {@link CelSetsExtensions.Function}, including any
   * future additions. To expose only a subset of functions, use {@link
   * #sets(CelOptions, CelSetsExtensions.Function...)} instead.
   */
  public static CelSetsExtensions sets(CelOptions celOptions) {
    return new CelSetsExtensions(celOptions);
  }

  /**
   * Extended functions for Set manipulation.
   *
   * <p>Refer to README.md for available functions.
   *
   * <p>This will include only the specific functions denoted by {@link CelSetsExtensions.Function}.
   */
  public static CelSetsExtensions sets(
      CelOptions celOptions, CelSetsExtensions.Function... functions) {
    return sets(celOptions, ImmutableSet.copyOf(functions));
  }

  /**
   * Extended functions for Set manipulation.
   *
   * <p>Refer to README.md for available functions.
   *
   * <p>This will include only the specific functions denoted by {@link CelSetsExtensions.Function}.
   */
  public static CelSetsExtensions sets(
      CelOptions celOptions, Set<CelSetsExtensions.Function> functions) {
    return new CelSetsExtensions(celOptions, functions);
  }

  /**
   * TODO
   */
  public static CelListsExtensions lists() {
    return LISTS_EXTENSIONS_ALL;
  }

  /**
   * TODo
   */
  public static CelListsExtensions lists(CelListsExtensions.Function... functions) {
    return lists(ImmutableSet.copyOf(functions));
  }

  /**
   * TODO
   */
  public static CelListsExtensions lists(
      Set<CelListsExtensions.Function> functions) {
    return new CelListsExtensions(functions);
  }
  /**
   * Retrieves all function names used by every extension libraries.
   *
   * <p>Note: Certain extensions such as {@link CelProtoExtensions} and {@link
   * CelBindingsExtensions} are implemented via macros, not functions, and those are not included
   * here.
   */
  public static ImmutableSet<String> getAllFunctionNames() {
    return Streams.concat(
            stream(CelMathExtensions.Function.values())
                .map(CelMathExtensions.Function::getFunction),
            stream(CelStringExtensions.Function.values())
                .map(CelStringExtensions.Function::getFunction),
            stream(CelSetsExtensions.Function.values())
                .map(CelSetsExtensions.Function::getFunction),
            stream(CelEncoderExtensions.Function.values())
                .map(CelEncoderExtensions.Function::getFunction))
        .collect(toImmutableSet());
  }

  private CelExtensions() {}
}
