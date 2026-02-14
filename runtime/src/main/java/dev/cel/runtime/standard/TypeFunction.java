// Copyright 2026 Google LLC
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

package dev.cel.runtime.standard;

import com.google.common.collect.ImmutableSet;
import dev.cel.common.CelOptions;
import dev.cel.common.types.SimpleType;
import dev.cel.common.types.TypeType;
import dev.cel.runtime.CelFunctionBinding;
import dev.cel.runtime.RuntimeEquality;
import dev.cel.runtime.TypeResolver;

/**
 * Standard function for the {@code type} function.
 *
 * <p>The {@code type} function returns the CEL type of its argument. It accepts a
 * {@link TypeResolver} so that different runtimes can supply the appropriate resolver (e.g. a
 * descriptor-based resolver for full proto, or a base resolver for lite proto).
 */
public final class TypeFunction extends CelStandardFunction {

  private final TypeResolver typeResolver;

  public static TypeFunction create(TypeResolver typeResolver) {
    return new TypeFunction(typeResolver);
  }

  /** Overloads for the standard {@code type} function. */
  public enum TypeOverload implements CelStandardOverload {
    TYPE;

    @Override
    public CelFunctionBinding newFunctionBinding(
        CelOptions celOptions, RuntimeEquality runtimeEquality) {
      // This overload is not used directly. The binding is created in TypeFunction via the
      // TypeResolver instance.
      // TODO: Instantiate from CelStandardFunctions.
      throw new UnsupportedOperationException(
          "TypeOverload bindings must be created through TypeFunction.create(TypeResolver)");
    }
  }

  @Override
  public ImmutableSet<CelFunctionBinding> newFunctionBindings(
      CelOptions celOptions, RuntimeEquality runtimeEquality) {
    CelFunctionBinding binding =
        CelFunctionBinding.from(
            "type",
            Object.class,
            arg -> typeResolver.resolveObjectType(arg, TypeType.create(SimpleType.DYN)));

    return CelFunctionBinding.fromOverloads("type", ImmutableSet.of(binding));
  }

  private TypeFunction(TypeResolver typeResolver) {
    super("type", ImmutableSet.copyOf(TypeOverload.values()));
    this.typeResolver = typeResolver;
  }
}