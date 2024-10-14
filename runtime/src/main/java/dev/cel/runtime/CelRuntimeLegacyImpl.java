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

package dev.cel.runtime;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import javax.annotation.concurrent.ThreadSafe;
import com.google.protobuf.DescriptorProtos.FileDescriptorSet;
import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.FileDescriptor;
import com.google.protobuf.ExtensionRegistry;
import com.google.protobuf.Message;
import dev.cel.common.CelAbstractSyntaxTree;
import dev.cel.common.CelDescriptorUtil;
import dev.cel.common.CelDescriptors;
import dev.cel.common.CelOptions;
import dev.cel.common.annotations.Internal;
import dev.cel.common.internal.CelDescriptorPool;
import dev.cel.common.internal.CombinedDescriptorPool;
import dev.cel.common.internal.DefaultDescriptorPool;
import dev.cel.common.internal.DefaultMessageFactory;
import dev.cel.common.internal.DynamicProto;
// CEL-Internal-3
import dev.cel.common.internal.ProtoMessageFactory;
import dev.cel.common.types.CelTypes;
import dev.cel.common.values.CelValueProvider;
import dev.cel.common.values.ProtoMessageValueProvider;
import dev.cel.runtime.CelStandardFunctions.StandardFunction.Overload.Comparison;
import dev.cel.runtime.CelStandardFunctions.StandardFunction.Overload.Conversions;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;

/**
 * {@code CelRuntime} implementation based on the legacy CEL-Java stack.
 *
 * <p>CEL Library Internals. Do Not Use. Consumers should use factories, such as {@link
 * CelRuntimeFactory} instead to instantiate a runtime.
 */
@ThreadSafe
@Internal
public final class CelRuntimeLegacyImpl implements CelRuntime {

  private final Interpreter interpreter;
  private final CelOptions options;

  // Builder is mutable by design. APIs must guarantee a new instance to be returned.
  // CEL-Internal-4
  private final Builder runtimeBuilder;

  @Override
  public CelRuntime.Program createProgram(CelAbstractSyntaxTree ast) {
    checkState(ast.isChecked(), "programs must be created from checked expressions");
    return CelRuntime.Program.from(interpreter.createInterpretable(ast), options);
  }

  @Override
  public CelRuntimeBuilder toRuntimeBuilder() {
    return new Builder(runtimeBuilder);
  }

  /** Create a new builder for constructing a {@code CelRuntime} instance. */
  public static CelRuntimeBuilder newBuilder() {
    return new Builder();
  }

  /** Builder class for {@code CelRuntimeLegacyImpl}. */
  public static final class Builder implements CelRuntimeBuilder {

    private final ImmutableSet.Builder<FileDescriptor> fileTypes;
    private final HashMap<String, CelFunctionBinding> customFunctionBindings;
    private final ImmutableSet.Builder<CelRuntimeLibrary> celRuntimeLibraries;

    @SuppressWarnings("unused")
    private CelOptions options;

    private boolean standardEnvironmentEnabled;
    private Function<String, Message.Builder> customTypeFactory;
    private ExtensionRegistry extensionRegistry;
    private CelValueProvider celValueProvider;
    private CelStandardFunctions overriddenStandardFunctions;

    @Override
    public CelRuntimeBuilder setOptions(CelOptions options) {
      this.options = options;
      return this;
    }

    @Override
    public CelRuntimeBuilder addFunctionBindings(CelFunctionBinding... bindings) {
      return addFunctionBindings(Arrays.asList(bindings));
    }

    @Override
    public CelRuntimeBuilder addFunctionBindings(Iterable<CelFunctionBinding> bindings) {
      bindings.forEach(o -> customFunctionBindings.putIfAbsent(o.getOverloadId(), o));
      return this;
    }

    @Override
    public CelRuntimeBuilder addMessageTypes(Descriptor... descriptors) {
      return addMessageTypes(Arrays.asList(descriptors));
    }

    @Override
    public CelRuntimeBuilder addMessageTypes(Iterable<Descriptor> descriptors) {
      return addFileTypes(CelDescriptorUtil.getFileDescriptorsForDescriptors(descriptors));
    }

    @Override
    public CelRuntimeBuilder addFileTypes(FileDescriptor... fileDescriptors) {
      return addFileTypes(Arrays.asList(fileDescriptors));
    }

    @Override
    public CelRuntimeBuilder addFileTypes(Iterable<FileDescriptor> fileDescriptors) {
      this.fileTypes.addAll(checkNotNull(fileDescriptors));
      return this;
    }

    @Override
    public CelRuntimeBuilder addFileTypes(FileDescriptorSet fileDescriptorSet) {
      return addFileTypes(
          CelDescriptorUtil.getFileDescriptorsFromFileDescriptorSet(fileDescriptorSet));
    }

    @Override
    public CelRuntimeBuilder setTypeFactory(Function<String, Message.Builder> typeFactory) {
      this.customTypeFactory = typeFactory;
      return this;
    }

    @Override
    public CelRuntimeBuilder setValueProvider(CelValueProvider celValueProvider) {
      this.celValueProvider = celValueProvider;
      return this;
    }

    @Override
    public CelRuntimeBuilder setStandardEnvironmentEnabled(boolean value) {
      standardEnvironmentEnabled = value;
      return this;
    }

    @Override
    public CelRuntimeBuilder setStandardFunctions(CelStandardFunctions standardFunctions) {
      this.overriddenStandardFunctions = standardFunctions;
      return this;
    }

    @Override
    public CelRuntimeBuilder addLibraries(CelRuntimeLibrary... libraries) {
      checkNotNull(libraries);
      return this.addLibraries(Arrays.asList(libraries));
    }

    @Override
    public CelRuntimeBuilder addLibraries(Iterable<? extends CelRuntimeLibrary> libraries) {
      checkNotNull(libraries);
      this.celRuntimeLibraries.addAll(libraries);
      return this;
    }

    @Override
    public CelRuntimeBuilder setExtensionRegistry(ExtensionRegistry extensionRegistry) {
      checkNotNull(extensionRegistry);
      this.extensionRegistry = extensionRegistry.getUnmodifiable();
      return this;
    }

    // The following getters exist for asserting immutability for collections held by this builder,
    // and shouldn't be exposed to the public.
    @VisibleForTesting
    Map<String, CelFunctionBinding> getFunctionBindings() {
      return this.customFunctionBindings;
    }

    @VisibleForTesting
    ImmutableSet.Builder<CelRuntimeLibrary> getRuntimeLibraries() {
      return this.celRuntimeLibraries;
    }

    @VisibleForTesting
    ImmutableSet.Builder<FileDescriptor> getFileTypes() {
      return this.fileTypes;
    }

    /** Build a new {@code CelRuntimeLegacyImpl} instance from the builder config. */
    @Override
    public CelRuntimeLegacyImpl build() {
      if (standardEnvironmentEnabled && overriddenStandardFunctions != null) {
        throw new IllegalArgumentException(
            "setStandardEnvironmentEnabled must be set to false to override standard function"
                + " bindings.");
      }
      // Add libraries, such as extensions
      celRuntimeLibraries.build().forEach(celLibrary -> celLibrary.setRuntimeOptions(this));

      CelDescriptors celDescriptors =
          CelDescriptorUtil.getAllDescriptorsFromFileDescriptor(
              fileTypes.build(), options.resolveTypeDependencies());

      CelDescriptorPool celDescriptorPool =
          newDescriptorPool(
              celDescriptors,
              extensionRegistry);

      @SuppressWarnings("Immutable")
      ProtoMessageFactory runtimeTypeFactory =
          customTypeFactory != null
              ? messageName ->
                  CelTypes.isWellKnownType(
                          messageName) // Let DefaultMessageFactory handle WKT constructions
                      ? Optional.empty()
                      : Optional.ofNullable(customTypeFactory.apply(messageName))
              : null;
      runtimeTypeFactory =
          maybeCombineMessageFactory(
              runtimeTypeFactory, DefaultMessageFactory.create(celDescriptorPool));

      DynamicProto dynamicProto = DynamicProto.create(runtimeTypeFactory);

      ImmutableMap.Builder<String, CelFunctionBinding> functionBindingsBuilder =
          ImmutableMap.builder();
      for (CelFunctionBinding standardFunctionBinding : newStandardFunctionBindings(dynamicProto)) {
        functionBindingsBuilder.put(
            standardFunctionBinding.getOverloadId(), standardFunctionBinding);
      }

      functionBindingsBuilder.putAll(customFunctionBindings);

      DefaultDispatcher dispatcher = DefaultDispatcher.create();
      functionBindingsBuilder
          .buildOrThrow()
          .forEach(
              (String overloadId, CelFunctionBinding func) ->
                  dispatcher.add(
                      overloadId,
                      func.getArgTypes(),
                      (args) -> {
                        try {
                          return func.getDefinition().apply(args);
                        } catch (CelEvaluationException e) {
                          throw new InterpreterException.Builder(e.getMessage())
                              .setCause(e)
                              .setErrorCode(e.getErrorCode())
                              .build();
                        }
                      }));

      RuntimeTypeProvider runtimeTypeProvider;

      if (options.enableCelValue()) {
        CelValueProvider messageValueProvider =
            ProtoMessageValueProvider.newInstance(dynamicProto, options);
        if (celValueProvider != null) {
          messageValueProvider =
              new CelValueProvider.CombinedCelValueProvider(celValueProvider, messageValueProvider);
        }

        runtimeTypeProvider =
            new RuntimeTypeProviderLegacyImpl(
                options, messageValueProvider, celDescriptorPool, dynamicProto);
      } else {
        runtimeTypeProvider = new DescriptorMessageProvider(runtimeTypeFactory, options);
      }

      return new CelRuntimeLegacyImpl(
          new DefaultInterpreter(runtimeTypeProvider, dispatcher, options), options, this);
    }

    private ImmutableSet<CelFunctionBinding> newStandardFunctionBindings(
        DynamicProto dynamicProto) {
      CelStandardFunctions celStandardFunctions;
      if (standardEnvironmentEnabled) {
        celStandardFunctions =
            CelStandardFunctions.newBuilder()
                .filterFunctions(
                    (standardFunction, standardOverload) -> {
                      switch (standardFunction) {
                        case INT:
                          if (standardOverload.equals(Conversions.INT64_TO_INT64)) {
                            // Note that we require UnsignedLong flag here to avoid ambiguous
                            // overloads against "uint64_to_int64", because they both use the same
                            // Java Long class. We skip adding this identity function if the flag is
                            // disabled.
                            return options.enableUnsignedLongs();
                          }
                          break;
                        case TIMESTAMP:
                          // TODO: Remove this flag guard once the feature has been
                          // auto-enabled.
                          if (standardOverload.equals(Conversions.INT64_TO_TIMESTAMP)) {
                            return options.enableTimestampEpoch();
                          }
                          break;
                        default:
                          if (standardOverload instanceof Comparison
                              && !options.enableHeterogeneousNumericComparisons()) {
                            Comparison comparison = (Comparison) standardOverload;
                            if (comparison.isHeterogeneousComparison()) {
                              return false;
                            }
                          }
                          break;
                      }

                      return true;
                    })
                .build();
      } else if (overriddenStandardFunctions != null) {
        celStandardFunctions = overriddenStandardFunctions;
      } else {
        return ImmutableSet.of();
      }

      return celStandardFunctions.newFunctionBindings(dynamicProto, options);
    }

    private static CelDescriptorPool newDescriptorPool(
        CelDescriptors celDescriptors,
        ExtensionRegistry extensionRegistry) {
      ImmutableList.Builder<CelDescriptorPool> descriptorPools = new ImmutableList.Builder<>();

      descriptorPools.add(DefaultDescriptorPool.create(celDescriptors, extensionRegistry));

      return CombinedDescriptorPool.create(descriptorPools.build());
    }

    @CanIgnoreReturnValue
    private static ProtoMessageFactory maybeCombineMessageFactory(
        @Nullable ProtoMessageFactory parentFactory, ProtoMessageFactory childFactory) {
      if (parentFactory == null) {
        return childFactory;
      }
      return new ProtoMessageFactory.CombinedMessageFactory(
          ImmutableList.of(parentFactory, childFactory));
    }

    private Builder() {
      this.options = CelOptions.newBuilder().build();
      this.fileTypes = ImmutableSet.builder();
      this.customFunctionBindings = new HashMap<>();
      this.celRuntimeLibraries = ImmutableSet.builder();
      this.extensionRegistry = ExtensionRegistry.getEmptyRegistry();
      this.customTypeFactory = null;
    }

    private Builder(Builder builder) {
      // The following properties are either immutable or simple primitives, thus can be assigned
      // directly.
      this.options = builder.options;
      this.extensionRegistry = builder.extensionRegistry;
      this.customTypeFactory = builder.customTypeFactory;
      this.standardEnvironmentEnabled = builder.standardEnvironmentEnabled;
      this.celValueProvider = builder.celValueProvider;
      // The following needs to be deep copied as they are collection builders
      this.fileTypes = deepCopy(builder.fileTypes);
      this.celRuntimeLibraries = deepCopy(builder.celRuntimeLibraries);
      this.customFunctionBindings = new HashMap<>(builder.customFunctionBindings);
    }

    private static <T> ImmutableSet.Builder<T> deepCopy(ImmutableSet.Builder<T> builderToCopy) {
      ImmutableSet.Builder<T> newBuilder = ImmutableSet.builder();
      newBuilder.addAll(builderToCopy.build());
      return newBuilder;
    }
  }

  private CelRuntimeLegacyImpl(
      Interpreter interpreter, CelOptions options, Builder runtimeBuilder) {
    this.interpreter = interpreter;
    this.options = options;
    this.runtimeBuilder = new Builder(runtimeBuilder);
  }
}
