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
import java.util.Arrays;
import java.util.Optional;
import java.util.function.Function;
import org.jspecify.nullness.Nullable;

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

  @Override
  public CelRuntime.Program createProgram(CelAbstractSyntaxTree ast) {
    checkState(ast.isChecked(), "programs must be created from checked expressions");
    return CelRuntime.Program.from(interpreter.createInterpretable(ast), options);
  }

  /** Create a new builder for constructing a {@code CelRuntime} instance. */
  public static Builder newBuilder() {
    return new Builder();
  }

  /** Builder class for {@code CelRuntimeLegacyImpl}. */
  public static final class Builder implements CelRuntimeBuilder {

    private final ImmutableSet.Builder<FileDescriptor> fileTypes;
    private final ImmutableMap.Builder<String, CelFunctionBinding> functionBindings;
    private final ImmutableSet.Builder<CelRuntimeLibrary> celRuntimeLibraries;

    @SuppressWarnings("unused")
    private CelOptions options;

    private boolean standardEnvironmentEnabled;
    private Function<String, Message.Builder> customTypeFactory;
    private ExtensionRegistry extensionRegistry;
    private CelValueProvider celValueProvider;

    @Override
    @CanIgnoreReturnValue
    public Builder setOptions(CelOptions options) {
      this.options = options;
      return this;
    }

    @Override
    @CanIgnoreReturnValue
    public Builder addFunctionBindings(CelFunctionBinding... bindings) {
      return addFunctionBindings(Arrays.asList(bindings));
    }

    @Override
    @CanIgnoreReturnValue
    public Builder addFunctionBindings(Iterable<CelFunctionBinding> bindings) {
      bindings.forEach(o -> functionBindings.put(o.getOverloadId(), o));
      return this;
    }

    @Override
    @CanIgnoreReturnValue
    public Builder addMessageTypes(Descriptor... descriptors) {
      return addMessageTypes(Arrays.asList(descriptors));
    }

    @Override
    @CanIgnoreReturnValue
    public Builder addMessageTypes(Iterable<Descriptor> descriptors) {
      return addFileTypes(CelDescriptorUtil.getFileDescriptorsForDescriptors(descriptors));
    }

    @Override
    @CanIgnoreReturnValue
    public Builder addFileTypes(FileDescriptor... fileDescriptors) {
      return addFileTypes(Arrays.asList(fileDescriptors));
    }

    @Override
    @CanIgnoreReturnValue
    public Builder addFileTypes(Iterable<FileDescriptor> fileDescriptors) {
      this.fileTypes.addAll(checkNotNull(fileDescriptors));
      return this;
    }

    @Override
    @CanIgnoreReturnValue
    public Builder addFileTypes(FileDescriptorSet fileDescriptorSet) {
      return addFileTypes(
          CelDescriptorUtil.getFileDescriptorsFromFileDescriptorSet(fileDescriptorSet));
    }

    @Override
    @CanIgnoreReturnValue
    public Builder setTypeFactory(Function<String, Message.Builder> typeFactory) {
      this.customTypeFactory = typeFactory;
      return this;
    }

    @Override
    @CanIgnoreReturnValue
    public CelRuntimeBuilder setValueProvider(CelValueProvider celValueProvider) {
      this.celValueProvider = celValueProvider;
      return this;
    }

    @Override
    @CanIgnoreReturnValue
    public Builder setStandardEnvironmentEnabled(boolean value) {
      standardEnvironmentEnabled = value;
      return this;
    }

    @Override
    @CanIgnoreReturnValue
    public Builder addLibraries(CelRuntimeLibrary... libraries) {
      checkNotNull(libraries);
      return this.addLibraries(Arrays.asList(libraries));
    }

    @Override
    @CanIgnoreReturnValue
    public Builder addLibraries(Iterable<? extends CelRuntimeLibrary> libraries) {
      checkNotNull(libraries);
      this.celRuntimeLibraries.addAll(libraries);
      return this;
    }

    @Override
    @CanIgnoreReturnValue
    public Builder setExtensionRegistry(ExtensionRegistry extensionRegistry) {
      checkNotNull(extensionRegistry);
      this.extensionRegistry = extensionRegistry.getUnmodifiable();
      return this;
    }

    /** Build a new {@code CelRuntimeLegacyImpl} instance from the builder config. */
    @Override
    @CanIgnoreReturnValue
    public CelRuntimeLegacyImpl build() {
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

      DefaultDispatcher dispatcher = DefaultDispatcher.create(options, dynamicProto);
      if (standardEnvironmentEnabled) {
        StandardFunctions.add(dispatcher, dynamicProto, options);
      }

      ImmutableMap<String, CelFunctionBinding> functionBindingMap = functionBindings.buildOrThrow();
      functionBindingMap.forEach(
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
          new DefaultInterpreter(runtimeTypeProvider, dispatcher, options), options);
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
      this.functionBindings = ImmutableMap.builder();
      this.celRuntimeLibraries = ImmutableSet.builder();
      this.extensionRegistry = ExtensionRegistry.getEmptyRegistry();
      this.customTypeFactory = null;
    }
  }

  private CelRuntimeLegacyImpl(Interpreter interpreter, CelOptions options) {
    this.interpreter = interpreter;
    this.options = options;
  }
}
