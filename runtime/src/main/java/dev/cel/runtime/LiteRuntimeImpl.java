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

import static com.google.common.base.Preconditions.checkState;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import javax.annotation.concurrent.ThreadSafe;
import com.google.protobuf.MessageLiteOrBuilder;
import dev.cel.common.CelAbstractSyntaxTree;
import dev.cel.common.CelOptions;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

@ThreadSafe
final class LiteRuntimeImpl implements CelLiteRuntime {
  private final Interpreter interpreter;
  private final CelOptions celOptions;
  private final ImmutableList<CelFunctionBinding> customFunctionBindings;
  private final CelStandardFunctions celStandardFunctions;

  @Override
  public Program createProgram(CelAbstractSyntaxTree ast) {
    checkState(ast.isChecked(), "programs must be created from checked expressions");
    return LiteProgramImpl.plan(interpreter.createInterpretable(ast));
  }

  @Override
  public CelLiteRuntimeBuilder toRuntimeBuilder() {
    return new Builder()
        .setOptions(celOptions)
        .setStandardFunctions(celStandardFunctions)
        .addFunctionBindings(customFunctionBindings);
  }

  static final class Builder implements CelLiteRuntimeBuilder {

    // Following is visible to test `toBuilder`.
    @VisibleForTesting CelOptions celOptions;
    @VisibleForTesting final HashMap<String, CelFunctionBinding> customFunctionBindings;
    @VisibleForTesting CelStandardFunctions celStandardFunctions;

    @Override
    public CelLiteRuntimeBuilder setOptions(CelOptions celOptions) {
      this.celOptions = celOptions;
      return this;
    }

    @Override
    public CelLiteRuntimeBuilder setStandardFunctions(CelStandardFunctions standardFunctions) {
      this.celStandardFunctions = standardFunctions;
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
      ImmutableMap.Builder<String, CelFunctionBinding> functionBindingsBuilder =
          ImmutableMap.builder();
      if (celStandardFunctions != null) {
        RuntimeHelpers runtimeHelpers = RuntimeHelpers.create();
        RuntimeEquality runtimeEquality = RuntimeEquality.create(runtimeHelpers, celOptions);
        ImmutableSet<CelFunctionBinding> standardFunctionBinding =
            celStandardFunctions.newFunctionBindings(runtimeEquality, celOptions);
        for (CelFunctionBinding func : standardFunctionBinding) {
          functionBindingsBuilder.put(func.getOverloadId(), func);
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

      // TODO: provide implementations for dependencies
      Interpreter interpreter =
          new DefaultInterpreter(
              TypeResolver.create(),
              new RuntimeTypeProvider() {
                @Override
                public Object createMessage(String messageName, Map<String, Object> values) {
                  throw new UnsupportedOperationException("Not implemented yet");
                }

                @Override
                public Object selectField(Object message, String fieldName) {
                  throw new UnsupportedOperationException("Not implemented yet");
                }

                @Override
                public Object hasField(Object message, String fieldName) {
                  throw new UnsupportedOperationException("Not implemented yet");
                }

                @Override
                public Object adapt(Object message) {
                  if (message instanceof MessageLiteOrBuilder) {
                    throw new UnsupportedOperationException("Not implemented yet");
                  }

                  return message;
                }
              },
              dispatcher,
              celOptions);

      return new LiteRuntimeImpl(
          interpreter, celOptions, customFunctionBindings.values(), celStandardFunctions);
    }

    private Builder() {
      this.celOptions = CelOptions.current().enableCelValue(true).build();
      this.customFunctionBindings = new HashMap<>();
    }
  }

  static CelLiteRuntimeBuilder newBuilder() {
    return new Builder();
  }

  private LiteRuntimeImpl(
      Interpreter interpreter,
      CelOptions celOptions,
      Iterable<CelFunctionBinding> customFunctionBindings,
      CelStandardFunctions celStandardFunctions) {
    this.interpreter = interpreter;
    this.celOptions = celOptions;
    this.customFunctionBindings = ImmutableList.copyOf(customFunctionBindings);
    this.celStandardFunctions = celStandardFunctions;
  }
}
