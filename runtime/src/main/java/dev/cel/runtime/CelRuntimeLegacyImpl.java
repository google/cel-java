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
import static com.google.common.collect.ImmutableSet.toImmutableSet;

import javax.annotation.concurrent.ThreadSafe;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.protobuf.DescriptorProtos.FileDescriptorSet;
import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.FileDescriptor;
import com.google.protobuf.Message;
import dev.cel.common.CelAbstractSyntaxTree;
import dev.cel.common.CelDescriptorUtil;
import dev.cel.common.CelDescriptors;
import dev.cel.common.CelOptions;
import dev.cel.common.internal.DynamicProto;
import java.util.Arrays;
import java.util.function.Function;
import org.jspecify.nullness.Nullable;

/** {@code CelRuntime} implementation based on the legacy CEL-Java stack. */
@ThreadSafe
public final class CelRuntimeLegacyImpl implements CelRuntime {

  private final Interpreter interpreter;
  private final CelOptions options;

  private CelRuntimeLegacyImpl(Interpreter interpreter, CelOptions options) {
    this.interpreter = interpreter;
    this.options = options;
  }

  @Override
  public CelRuntime.Program createProgram(CelAbstractSyntaxTree ast) throws CelEvaluationException {
    checkState(ast.isChecked(), "programs must be created from checked expressions");
    try {
      return CelRuntime.Program.from(interpreter.createInterpretable(ast.toCheckedExpr()), options);
    } catch (InterpreterException e) {
      throw new CelEvaluationException(e);
    }
  }

  /** Create a new builder for constructing a {@code CelRuntime} instance. */
  public static Builder newBuilder() {
    return new Builder();
  }

  /** Builder class for {@code CelRuntimeLegacyImpl}. */
  public static final class Builder implements CelRuntimeBuilder {

    @SuppressWarnings("unused")
    private CelOptions options;

    private final ImmutableSet.Builder<Descriptor> messageTypes;
    private final ImmutableSet.Builder<FileDescriptor> fileTypes;
    private final ImmutableMap.Builder<String, CelFunctionBinding> functionBindings;
    private final ImmutableSet.Builder<CelRuntimeLibrary> celRuntimeLibraries;
    private boolean standardEnvironmentEnabled;
    private Function<String, Message.Builder> customTypeFactory;

    private Builder() {
      this.options = CelOptions.newBuilder().build();
      this.fileTypes = ImmutableSet.builder();
      this.messageTypes = ImmutableSet.builder();
      this.functionBindings = ImmutableMap.builder();
      this.celRuntimeLibraries = ImmutableSet.builder();
      this.customTypeFactory = null;
    }

    /** {@inheritDoc} */
    @Override
    @CanIgnoreReturnValue
    public Builder setOptions(CelOptions options) {
      this.options = options;
      return this;
    }

    /** {@inheritDoc} */
    @Override
    @CanIgnoreReturnValue
    public Builder addFunctionBindings(CelFunctionBinding... bindings) {
      return addFunctionBindings(Arrays.asList(bindings));
    }

    /** {@inheritDoc} */
    @Override
    @CanIgnoreReturnValue
    public Builder addFunctionBindings(Iterable<CelFunctionBinding> bindings) {
      bindings.forEach(o -> functionBindings.put(o.getOverloadId(), o));
      return this;
    }

    /** {@inheritDoc} */
    @Override
    @CanIgnoreReturnValue
    public Builder addMessageTypes(Descriptor... descriptors) {
      return addMessageTypes(Arrays.asList(descriptors));
    }

    /** {@inheritDoc} */
    @Override
    @CanIgnoreReturnValue
    public Builder addMessageTypes(Iterable<Descriptor> descriptors) {
      return addFileTypes(CelDescriptorUtil.getFileDescriptorsForDescriptors(descriptors));
    }

    /** {@inheritDoc} */
    @Override
    @CanIgnoreReturnValue
    public Builder addFileTypes(FileDescriptor... fileDescriptors) {
      return addFileTypes(Arrays.asList(fileDescriptors));
    }

    /** {@inheritDoc} */
    @Override
    @CanIgnoreReturnValue
    public Builder addFileTypes(Iterable<FileDescriptor> fileDescriptors) {
      this.fileTypes.addAll(checkNotNull(fileDescriptors));
      return this;
    }

    /** {@inheritDoc} */
    @Override
    @CanIgnoreReturnValue
    public Builder addFileTypes(FileDescriptorSet fileDescriptorSet) {
      return addFileTypes(
          CelDescriptorUtil.getFileDescriptorsFromFileDescriptorSet(fileDescriptorSet));
    }

    /** {@inheritDoc} */
    @Override
    @CanIgnoreReturnValue
    public Builder setTypeFactory(Function<String, Message.Builder> typeFactory) {
      this.customTypeFactory = typeFactory;
      return this;
    }

    /** {@inheritDoc} */
    @Override
    @CanIgnoreReturnValue
    public Builder setStandardEnvironmentEnabled(boolean value) {
      standardEnvironmentEnabled = value;
      return this;
    }

    /** {@inheritDoc} */
    @Override
    @CanIgnoreReturnValue
    public Builder addLibraries(CelRuntimeLibrary... libraries) {
      checkNotNull(libraries);
      return this.addLibraries(Arrays.asList(libraries));
    }

    /** {@inheritDoc} */
    @Override
    @CanIgnoreReturnValue
    public Builder addLibraries(Iterable<? extends CelRuntimeLibrary> libraries) {
      checkNotNull(libraries);
      this.celRuntimeLibraries.addAll(libraries);
      return this;
    }

    /** Build a new {@code CelRuntimeLegacyImpl} instance from the builder config. */
    @Override
    @CanIgnoreReturnValue
    public CelRuntimeLegacyImpl build() {
      // Add libraries, such as extensions
      celRuntimeLibraries.build().forEach(celLibrary -> celLibrary.setRuntimeOptions(this));

      ImmutableSet<FileDescriptor> fileTypeSet = fileTypes.build();
      ImmutableSet<Descriptor> messageTypeSet = messageTypes.build();
      if (!messageTypeSet.isEmpty()) {
        fileTypeSet =
            new ImmutableSet.Builder<FileDescriptor>()
                .addAll(fileTypeSet)
                .addAll(messageTypeSet.stream().map(Descriptor::getFile).collect(toImmutableSet()))
                .build();
      }

      CelDescriptors celDescriptors =
          CelDescriptorUtil.getAllDescriptorsFromFileDescriptor(
              fileTypeSet, options.resolveTypeDependencies());

      // This lambda implements @Immutable interface 'MessageFactory', but 'Builder' has non-final
      // field 'customTypeFactory'
      @SuppressWarnings("Immutable")
      MessageFactory runtimeTypeFactory =
          customTypeFactory != null ? typeName -> customTypeFactory.apply(typeName) : null;

      runtimeTypeFactory =
          maybeCombineTypeFactory(
              runtimeTypeFactory,
              DynamicMessageFactory.typeFactory(celDescriptors));

      DynamicProto dynamicProto =
          DynamicProto.newBuilder()
              .setDynamicDescriptors(celDescriptors)
              .setProtoMessageFactory(runtimeTypeFactory::newBuilder)
              .build();

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
                      throw new InterpreterException.Builder(e.getMessage()).setCause(e).build();
                    }
                  }));

      return new CelRuntimeLegacyImpl(
          new DefaultInterpreter(
              new DescriptorMessageProvider(runtimeTypeFactory, dynamicProto, options),
              dispatcher,
              options),
          options);
    }

    @CanIgnoreReturnValue
    private static MessageFactory maybeCombineTypeFactory(
        @Nullable MessageFactory parentFactory, MessageFactory childFactory) {
      if (parentFactory == null) {
        return childFactory;
      }
      return new MessageFactory.CombinedMessageFactory(
          ImmutableList.of(parentFactory, childFactory));
    }
  }
}
