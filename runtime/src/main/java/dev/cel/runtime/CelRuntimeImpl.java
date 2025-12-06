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

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableSet;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.errorprone.annotations.Immutable;
import com.google.protobuf.DescriptorProtos;
import com.google.protobuf.Descriptors;
import com.google.protobuf.ExtensionRegistry;
import com.google.protobuf.Message;
import dev.cel.common.CelAbstractSyntaxTree;
import dev.cel.common.CelContainer;
import dev.cel.common.CelDescriptorUtil;
import dev.cel.common.CelDescriptors;
import dev.cel.common.CelOptions;
import dev.cel.common.annotations.Internal;
import dev.cel.common.internal.CelDescriptorPool;
import dev.cel.common.internal.DefaultDescriptorPool;
import dev.cel.common.internal.DefaultMessageFactory;
import dev.cel.common.internal.DynamicProto;
import dev.cel.common.types.CelTypeProvider;
import dev.cel.common.types.DefaultTypeProvider;
import dev.cel.common.types.ProtoMessageTypeProvider;
import dev.cel.common.values.CelValueConverter;
import dev.cel.common.values.CelValueProvider;
import dev.cel.common.values.CombinedCelValueProvider;
import dev.cel.common.values.ProtoMessageValueProvider;
import dev.cel.runtime.planner.ProgramPlanner;
import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;

@AutoValue
@Internal
@Immutable
abstract class CelRuntimeImpl implements CelRuntime {

  abstract ProgramPlanner planner();

  abstract CelOptions options();

  abstract ImmutableSet<CelFunctionBinding> functionBindings();

  abstract ImmutableSet<Descriptors.FileDescriptor> fileDescriptors();

  // Callers must guarantee that a custom runtime library is immutable. CEL provided ones are
  // immutable by default.
  @SuppressWarnings("Immutable")
  @AutoValue.CopyAnnotations
  abstract ImmutableSet<CelRuntimeLibrary> runtimeLibraries();

  abstract CelStandardFunctions standardFunctions();

  abstract @Nullable CelValueProvider valueProvider();

  // Extension registry is unmodifiable. Just not marked as such from Protobuf's implementation.
  @SuppressWarnings("Immutable")
  @AutoValue.CopyAnnotations
  abstract @Nullable ExtensionRegistry extensionRegistry();

  public Program createProgram(CelAbstractSyntaxTree ast) throws CelEvaluationException {
    return toRuntimeProgram(planner().plan(ast));
  }

  public Program toRuntimeProgram(dev.cel.runtime.Program program) {
    return new Program() {

      @Override
      public Object eval() throws CelEvaluationException {
        return program.eval();
      }

      @Override
      public Object eval(Map<String, ?> mapValue) throws CelEvaluationException {
        return program.eval(mapValue);
      }

      @Override
      public Object eval(Map<String, ?> mapValue, CelFunctionResolver lateBoundFunctionResolver)
          throws CelEvaluationException {
        return program.eval(mapValue, lateBoundFunctionResolver);
      }

      @Override
      public Object eval(Message message) throws CelEvaluationException {
        throw new UnsupportedOperationException("Not yet supported.");
      }

      @Override
      public Object eval(CelVariableResolver resolver) throws CelEvaluationException {
        return program.eval(resolver);
      }

      @Override
      public Object eval(
          CelVariableResolver resolver, CelFunctionResolver lateBoundFunctionResolver)
          throws CelEvaluationException {
        throw new UnsupportedOperationException("Not yet supported.");
      }

      @Override
      public Object trace(CelEvaluationListener listener) throws CelEvaluationException {
        throw new UnsupportedOperationException("Trace is not yet supported.");
      }

      @Override
      public Object trace(Map<String, ?> mapValue, CelEvaluationListener listener)
          throws CelEvaluationException {
        throw new UnsupportedOperationException("Trace is not yet supported.");
      }

      @Override
      public Object trace(Message message, CelEvaluationListener listener)
          throws CelEvaluationException {
        throw new UnsupportedOperationException("Trace is not yet supported.");
      }

      @Override
      public Object trace(CelVariableResolver resolver, CelEvaluationListener listener)
          throws CelEvaluationException {
        throw new UnsupportedOperationException("Trace is not yet supported.");
      }

      @Override
      public Object trace(
          CelVariableResolver resolver,
          CelFunctionResolver lateBoundFunctionResolver,
          CelEvaluationListener listener)
          throws CelEvaluationException {
        throw new UnsupportedOperationException("Trace is not yet supported.");
      }

      @Override
      public Object trace(
          Map<String, ?> mapValue,
          CelFunctionResolver lateBoundFunctionResolver,
          CelEvaluationListener listener)
          throws CelEvaluationException {
        throw new UnsupportedOperationException("Trace is not yet supported.");
      }

      @Override
      public Object advanceEvaluation(UnknownContext context) throws CelEvaluationException {
        throw new UnsupportedOperationException("Unsupported operation.");
      }
    };
  }

  public abstract Builder toRuntimeBuilder();

  static Builder newBuilder() {
    return new AutoValue_CelRuntimeImpl.Builder();
  }

  @AutoValue.Builder
  abstract static class Builder implements CelRuntimeBuilder {

    public abstract Builder setPlanner(ProgramPlanner planner);

    public abstract Builder setOptions(CelOptions options);

    public abstract Builder setStandardFunctions(CelStandardFunctions standardFunctions);

    public abstract Builder setExtensionRegistry(ExtensionRegistry extensionRegistry);

    public abstract Builder setValueProvider(CelValueProvider celValueProvider);

    abstract CelOptions options();

    abstract CelValueProvider valueProvider();

    abstract CelStandardFunctions standardFunctions();

    abstract ImmutableSet.Builder<CelFunctionBinding> functionBindingsBuilder();

    abstract ImmutableSet.Builder<Descriptors.FileDescriptor> fileDescriptorsBuilder();

    abstract ImmutableSet.Builder<CelRuntimeLibrary> runtimeLibrariesBuilder();

    @CanIgnoreReturnValue
    public Builder addFunctionBindings(CelFunctionBinding... bindings) {
      checkNotNull(bindings);
      return addFunctionBindings(Arrays.asList(bindings));
    }

    @CanIgnoreReturnValue
    public Builder addFunctionBindings(Iterable<CelFunctionBinding> bindings) {
      checkNotNull(bindings);
      this.functionBindingsBuilder().addAll(bindings);
      return this;
    }

    @CanIgnoreReturnValue
    public Builder addMessageTypes(Descriptors.Descriptor... descriptors) {
      checkNotNull(descriptors);
      return addMessageTypes(Arrays.asList(descriptors));
    }

    @CanIgnoreReturnValue
    public Builder addMessageTypes(Iterable<Descriptors.Descriptor> descriptors) {
      checkNotNull(descriptors);
      return addFileTypes(CelDescriptorUtil.getFileDescriptorsForDescriptors(descriptors));
    }

    @CanIgnoreReturnValue
    public Builder addFileTypes(DescriptorProtos.FileDescriptorSet fileDescriptorSet) {
      checkNotNull(fileDescriptorSet);
      return addFileTypes(
          CelDescriptorUtil.getFileDescriptorsFromFileDescriptorSet(fileDescriptorSet));
    }

    @CanIgnoreReturnValue
    public Builder addFileTypes(Descriptors.FileDescriptor... fileDescriptors) {
      checkNotNull(fileDescriptors);
      return addFileTypes(Arrays.asList(fileDescriptors));
    }

    @CanIgnoreReturnValue
    public Builder addFileTypes(Iterable<Descriptors.FileDescriptor> fileDescriptors) {
      checkNotNull(fileDescriptors);
      this.fileDescriptorsBuilder().addAll(fileDescriptors);
      return this;
    }

    @CanIgnoreReturnValue
    public Builder addLibraries(CelRuntimeLibrary... libraries) {
      checkNotNull(libraries);
      return this.addLibraries(Arrays.asList(libraries));
    }

    @CanIgnoreReturnValue
    public Builder addLibraries(Iterable<? extends CelRuntimeLibrary> libraries) {
      checkNotNull(libraries);
      this.runtimeLibrariesBuilder().addAll(libraries);
      return this;
    }

    public Builder setTypeFactory(Function<String, Message.Builder> typeFactory) {
      throw new UnsupportedOperationException("Unsupported. Use a custom value provider instead.");
    }

    public Builder setStandardEnvironmentEnabled(boolean value) {
      throw new UnsupportedOperationException(
          "Unsupported. Subset the environment using setStandardFunctions instead.");
    }

    /** Throws if an unsupported flag in CelOptions is toggled. */
    private static void assertAllowedCelOptions(CelOptions celOptions) {
      String prefix = "Misconfigured CelOptions: ";
      if (!celOptions.enableUnsignedLongs()) {
        throw new IllegalArgumentException(prefix + "enableUnsignedLongs cannot be disabled.");
      }
      if (!celOptions.unwrapWellKnownTypesOnFunctionDispatch()) {
        throw new IllegalArgumentException(
            prefix + "unwrapWellKnownTypesOnFunctionDispatch cannot be disabled.");
      }

      // Disallowed options in favor of subsetting
      String subsettingError = "Subset the environment instead using setStandardFunctions method.";
      if (!celOptions.enableStringConcatenation()) {
        throw new IllegalArgumentException(
            prefix + "enableStringConcatenation cannot be disabled. " + subsettingError);
      }

      if (!celOptions.enableStringConversion()) {
        throw new IllegalArgumentException(
            prefix + "enableStringConversion cannot be disabled. " + subsettingError);
      }

      if (!celOptions.enableListConcatenation()) {
        throw new IllegalArgumentException(
            prefix + "enableListConcatenation cannot be disabled. " + subsettingError);
      }

      if (!celOptions.enableTimestampEpoch()) {
        throw new IllegalArgumentException(
            prefix + "enableTimestampEpoch cannot be disabled. " + subsettingError);
      }

      if (!celOptions.enableHeterogeneousNumericComparisons()) {
        throw new IllegalArgumentException(
            prefix
                + "enableHeterogeneousNumericComparisons cannot be disabled. "
                + subsettingError);
      }
    }

    abstract CelRuntimeImpl autoBuild();

    private static DefaultDispatcher newDispatcher(
        CelStandardFunctions standardFunctions,
        ImmutableSet<CelFunctionBinding> customFunctionBindings,
        RuntimeEquality runtimeEquality,
        CelOptions options) {
      DefaultDispatcher.Builder builder = DefaultDispatcher.newBuilder();
      for (CelFunctionBinding binding :
          standardFunctions.newFunctionBindings(runtimeEquality, options)) {
        builder.addOverload(
            binding.getOverloadId(),
            binding.getArgTypes(),
            binding.isStrict(),
            args ->
                guardedOp(
                    // TODO: FunctionName
                    "temp_func", args, binding));
      }

      for (CelFunctionBinding binding : customFunctionBindings) {
        builder.addOverload(
            binding.getOverloadId(),
            binding.getArgTypes(),
            binding.isStrict(),
            args ->
                guardedOp(
                    // TODO: FunctionName
                    "cust_func", args, binding));
      }

      return builder.build();
    }

    /** Creates an invocation guard around the overload definition. */
    private static Object guardedOp(
        String functionName, Object[] args, CelFunctionBinding singleBinding)
        throws CelEvaluationException {
      if (!CelResolvedOverload.canHandle(
          args, singleBinding.getArgTypes(), singleBinding.isStrict())) {
        throw new IllegalArgumentException("No matching overload for function: " + functionName);
      }

      return singleBinding.getDefinition().apply(args);
    }

    public CelRuntime build() {
      assertAllowedCelOptions(options());

      CelDescriptors celDescriptors =
          CelDescriptorUtil.getAllDescriptorsFromFileDescriptor(fileDescriptorsBuilder().build());

      CelDescriptorPool descriptorPool = DefaultDescriptorPool.create(celDescriptors);
      DefaultMessageFactory defaultMessageFactory = DefaultMessageFactory.create(descriptorPool);
      DynamicProto dynamicProto = DynamicProto.create(defaultMessageFactory);
      CelValueProvider protoMessageValueProvider =
          ProtoMessageValueProvider.newInstance(options(), dynamicProto);
      CelValueConverter celValueConverter = protoMessageValueProvider.celValueConverter();
      if (valueProvider() != null) {
        protoMessageValueProvider =
            CombinedCelValueProvider.combine(protoMessageValueProvider, valueProvider());
      }

      RuntimeEquality runtimeEquality =
          RuntimeEquality.create(
              ProtoMessageRuntimeHelpers.create(dynamicProto, options()), options());
      ImmutableSet<CelRuntimeLibrary> runtimeLibraries = runtimeLibrariesBuilder().build();
      // Add libraries, such as extensions
      for (CelRuntimeLibrary celLibrary : runtimeLibraries) {
        if (celLibrary instanceof CelInternalRuntimeLibrary) {
          ((CelInternalRuntimeLibrary) celLibrary)
              .setRuntimeOptions(this, runtimeEquality, options());
        } else {
          celLibrary.setRuntimeOptions(this);
        }
      }

      CelTypeProvider combinedTypeProvider =
          new CelTypeProvider.CombinedCelTypeProvider(
              new ProtoMessageTypeProvider(celDescriptors), DefaultTypeProvider.getInstance());

      DefaultDispatcher dispatcher =
          newDispatcher(
              standardFunctions(), functionBindingsBuilder().build(), runtimeEquality, options());

      ProgramPlanner planner =
          ProgramPlanner.newPlanner(
              combinedTypeProvider,
              protoMessageValueProvider,
              dispatcher,
              celValueConverter,
              CelContainer.newBuilder().build() // TODO: Accept CEL container
              );
      setPlanner(planner);

      return autoBuild();
    }
  }
}
