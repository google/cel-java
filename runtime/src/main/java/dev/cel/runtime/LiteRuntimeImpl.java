// Copyright 2025 Google LLC
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
import static com.google.common.base.Preconditions.checkState;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import dev.cel.runtime.DefaultInterpreter.DefaultInterpretable;
import javax.annotation.concurrent.ThreadSafe;
import dev.cel.common.CelAbstractSyntaxTree;
import dev.cel.common.CelOptions;
import dev.cel.common.values.CelValueProvider;
import dev.cel.runtime.standard.CelStandardFunction;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Optional;

@ThreadSafe
final class LiteRuntimeImpl implements CelLiteRuntime {
  private final Interpreter interpreter;
  private final CelOptions celOptions;
  private final ImmutableList<CelFunctionBinding> customFunctionBindings;
  private final ImmutableSet<CelStandardFunction> celStandardFunctions;
  private final CelValueProvider celValueProvider;
  private final boolean enablePlanner;

  // This does not affect the evaluation behavior in any manner.
  // CEL-Internal-4
  private final ImmutableSet<CelLiteRuntimeLibrary> runtimeLibraries;

  @Override
  public Program createProgram(CelAbstractSyntaxTree ast) {
    checkState(ast.isChecked(), "programs must be created from checked expressions");
    if (enablePlanner) {
      return ProgramPlanner.plan(ast);
    } else {
      return LiteProgramImpl.create(interpreter.createInterpretable(ast));
    }
  }

  @Override
  public CelLiteRuntimeBuilder toRuntimeBuilder() {
    CelLiteRuntimeBuilder builder =
        new Builder()
            .setOptions(celOptions)
            .setStandardFunctions(celStandardFunctions)
            .addFunctionBindings(customFunctionBindings)
            .addLibraries(runtimeLibraries);

    if (celValueProvider != null) {
      builder.setValueProvider(celValueProvider);
    }

    return builder;
  }

  static final class Builder implements CelLiteRuntimeBuilder {

    // Following is visible to test `toBuilder`.
    @VisibleForTesting CelOptions celOptions;
    @VisibleForTesting final HashMap<String, CelFunctionBinding> customFunctionBindings;
    @VisibleForTesting final ImmutableSet.Builder<CelLiteRuntimeLibrary> runtimeLibrariesBuilder;
    @VisibleForTesting final ImmutableSet.Builder<CelStandardFunction> standardFunctionBuilder;
    @VisibleForTesting CelValueProvider celValueProvider;
    @VisibleForTesting boolean enablePlanner;

    @Override
    public CelLiteRuntimeBuilder setOptions(CelOptions celOptions) {
      this.celOptions = celOptions;
      return this;
    }

    @Override
    public CelLiteRuntimeBuilder setStandardFunctions(CelStandardFunction... standardFunctions) {
      return setStandardFunctions(Arrays.asList(standardFunctions));
    }

    @Override
    public CelLiteRuntimeBuilder setStandardFunctions(
        Iterable<? extends CelStandardFunction> standardFunctions) {
      standardFunctionBuilder.addAll(standardFunctions);
      return this;
    }

    @Override
    public CelLiteRuntimeBuilder addFunctionBindings(CelFunctionBinding... bindings) {
      return addFunctionBindings(Arrays.asList(bindings));
    }

    @Override
    public CelLiteRuntimeBuilder addFunctionBindings(Iterable<CelFunctionBinding> bindings) {
      bindings.forEach(o -> customFunctionBindings.putIfAbsent(o.getOverloadId(), o));
      return this;
    }

    @Override
    public CelLiteRuntimeBuilder setValueProvider(CelValueProvider celValueProvider) {
      this.celValueProvider = celValueProvider;
      return this;
    }

    @Override
    public CelLiteRuntimeBuilder addLibraries(CelLiteRuntimeLibrary... libraries) {
      return addLibraries(Arrays.asList(checkNotNull(libraries)));
    }

    @Override
    public CelLiteRuntimeBuilder addLibraries(Iterable<? extends CelLiteRuntimeLibrary> libraries) {
      this.runtimeLibrariesBuilder.addAll(checkNotNull(libraries));
      return this;
    }
    @Override
    public CelLiteRuntimeBuilder enablePlanner(boolean value) {
      this.enablePlanner = value;
      return this;
    }

    /** Throws if an unsupported flag in CelOptions is toggled. */
    private static void assertAllowedCelOptions(CelOptions celOptions) {
      String prefix = "Misconfigured CelOptions: ";
      if (!celOptions.enableCelValue()) {
        throw new IllegalArgumentException(prefix + "enableCelValue must be enabled.");
      }
      if (!celOptions.enableUnsignedLongs()) {
        throw new IllegalArgumentException(prefix + "enableUnsignedLongs cannot be disabled.");
      }
      if (!celOptions.unwrapWellKnownTypesOnFunctionDispatch()) {
        throw new IllegalArgumentException(
            prefix + "unwrapWellKnownTypesOnFunctionDispatch cannot be disabled.");
      }
      if (!celOptions.enableStringConcatenation()) {
        throw new IllegalArgumentException(
            prefix
                + "enableStringConcatenation cannot be disabled. Subset the environment instead"
                + " using setStandardFunctions method.");
      }
      if (!celOptions.enableStringConversion()) {
        throw new IllegalArgumentException(
            prefix
                + "enableStringConversion cannot be disabled. Subset the environment instead using"
                + " setStandardFunctions method.");
      }
      if (!celOptions.enableListConcatenation()) {
        throw new IllegalArgumentException(
            prefix
                + "enableListConcatenation cannot be disabled. Subset the environment instead using"
                + " setStandardFunctions method.");
      }
    }

    @Override
    public CelLiteRuntime build() {
      assertAllowedCelOptions(celOptions);
      ImmutableSet<CelLiteRuntimeLibrary> runtimeLibs = runtimeLibrariesBuilder.build();
      runtimeLibs.forEach(lib -> lib.setRuntimeOptions(this));

      ImmutableMap.Builder<String, CelFunctionBinding> functionBindingsBuilder =
          ImmutableMap.builder();

      ImmutableSet<CelStandardFunction> standardFunctions = standardFunctionBuilder.build();
      if (!standardFunctions.isEmpty()) {
        RuntimeHelpers runtimeHelpers = RuntimeHelpers.create();
        RuntimeEquality runtimeEquality = RuntimeEquality.create(runtimeHelpers, celOptions);
        for (CelStandardFunction standardFunction : standardFunctions) {
          ImmutableSet<CelFunctionBinding> standardFunctionBinding =
              standardFunction.newFunctionBindings(celOptions, runtimeEquality);
          for (CelFunctionBinding func : standardFunctionBinding) {
            functionBindingsBuilder.put(func.getOverloadId(), func);
          }
        }
      }

      functionBindingsBuilder.putAll(customFunctionBindings);

      DefaultDispatcher dispatcher = DefaultDispatcher.create();
      functionBindingsBuilder
          .buildOrThrow()
          .forEach(
              (String overloadId, CelFunctionBinding func) ->
                  dispatcher.add(
                      overloadId, func.getArgTypes(), (args) -> func.getDefinition().apply(args)));

      Interpreter interpreter =
          new DefaultInterpreter(
              TypeResolver.create(),
              CelValueRuntimeTypeProvider.newInstance(celValueProvider),
              dispatcher,
              celOptions);

      return new LiteRuntimeImpl(
          interpreter,
          celOptions,
          customFunctionBindings.values(),
          standardFunctions,
          runtimeLibs,
          celValueProvider,
          enablePlanner);
    }

    private Builder() {
      this.celOptions =
          CelOptions.current()
              .enableCelValue(true)
              .evaluateCanonicalTypesToNativeValues(true)
              .build();
      this.celValueProvider = (structType, fields) -> Optional.empty();
      this.customFunctionBindings = new HashMap<>();
      this.standardFunctionBuilder = ImmutableSet.builder();
      this.runtimeLibrariesBuilder = ImmutableSet.builder();
    }
  }

  static CelLiteRuntimeBuilder newBuilder() {
    return new Builder();
  }

  private LiteRuntimeImpl(
      Interpreter interpreter,
      CelOptions celOptions,
      Iterable<CelFunctionBinding> customFunctionBindings,
      ImmutableSet<CelStandardFunction> celStandardFunctions,
      ImmutableSet<CelLiteRuntimeLibrary> runtimeLibraries,
      CelValueProvider celValueProvider,
      boolean enablePlanner) {
    this.interpreter = interpreter;
    this.celOptions = celOptions;
    this.customFunctionBindings = ImmutableList.copyOf(customFunctionBindings);
    this.celStandardFunctions = celStandardFunctions;
    this.runtimeLibraries = runtimeLibraries;
    this.celValueProvider = celValueProvider;
    this.enablePlanner = enablePlanner;
  }
}
