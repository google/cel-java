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

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.errorprone.annotations.CheckReturnValue;
import com.google.protobuf.DescriptorProtos.FileDescriptorSet;
import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.FileDescriptor;
import com.google.protobuf.ExtensionRegistry;
import com.google.protobuf.Message;
import dev.cel.common.CelOptions;
import dev.cel.common.values.CelValueProvider;
import java.util.function.Function;

/** Interface for building an instance of CelRuntime */
public interface CelRuntimeBuilder {

  /** Set the {@code CelOptions} used to enable fixes and features for this CEL instance. */
  @CanIgnoreReturnValue
  CelRuntimeBuilder setOptions(CelOptions options);

  /**
   * Add one or more {@link CelRuntime.CelFunctionBinding} objects to the CEL runtime.
   *
   * <p>Functions with duplicate overload ids will be replaced in favor of the new overload.
   */
  @CanIgnoreReturnValue
  CelRuntimeBuilder addFunctionBindings(CelRuntime.CelFunctionBinding... bindings);

  /**
   * Bind a collection of {@link CelRuntime.CelFunctionBinding} objects to the runtime.
   *
   * <p>Functions with duplicate overload ids will be replaced in favor of the new overload.
   */
  @CanIgnoreReturnValue
  CelRuntimeBuilder addFunctionBindings(Iterable<CelRuntime.CelFunctionBinding> bindings);

  /**
   * Add message {@link Descriptor}s to the builder for type-checking and object creation at
   * interpretation time.
   *
   * <p>Note, it is valid to combine type factory methods within the runtime. Only the options which
   * have been configured will be used. The type creation search order is as follows:
   *
   * <ul>
   *   <li/>Custom type factory ({@link #setTypeFactory})
   *   <li/>Custom descriptor set {{@code #addMessageTypes})
   * </ul>
   */
  @CanIgnoreReturnValue
  CelRuntimeBuilder addMessageTypes(Descriptor... descriptors);

  /**
   * Add message {@link Descriptor}s to the use for type-checking and object creation at
   * interpretation time.
   *
   * <p>Note, it is valid to combine type factory methods within the runtime. Only the options which
   * have been configured will be used. The type creation search order is as follows:
   *
   * <ul>
   *   <li/>Custom type factory ({@link #setTypeFactory})
   *   <li/>Custom descriptor set {{@code #addMessageTypes})
   * </ul>
   */
  @CanIgnoreReturnValue
  CelRuntimeBuilder addMessageTypes(Iterable<Descriptor> descriptors);

  /**
   * Add {@link FileDescriptor}s to the use for type-checking, and for object creation at
   * interpretation time.
   *
   * <p>Note, it is valid to combine type factory methods within the runtime. Only the options which
   * have been configured will be used. The type creation search order is as follows:
   *
   * <ul>
   *   <li/>Custom type factory ({@link #setTypeFactory})
   *   <li/>Custom descriptor set {{@link #addMessageTypes})
   * </ul>
   */
  @CanIgnoreReturnValue
  CelRuntimeBuilder addFileTypes(FileDescriptor... fileDescriptors);

  /**
   * Add {@link FileDescriptor}s to the use for type-checking, and for object creation at
   * interpretation time.
   *
   * <p>Note, it is valid to combine type factory methods within the runtime. Only the options which
   * have been configured will be used. The type creation search order is as follows:
   *
   * <ul>
   *   <li/>Custom type factory ({@link #setTypeFactory})
   *   <li/>Custom descriptor set {{@link #addMessageTypes})
   * </ul>
   */
  @CanIgnoreReturnValue
  CelRuntimeBuilder addFileTypes(Iterable<FileDescriptor> fileDescriptors);

  /**
   * Add all of the {@link FileDescriptor}s in a {@code FileDescriptorSet} to the use for
   * type-checking, and for object creation at interpretation time.
   *
   * <p>Note, it is valid to combine type factory methods within the runtime. Only the options which
   * have been configured will be used. The type creation search order is as follows:
   *
   * <ul>
   *   <li/>Custom type factory ({@link #setTypeFactory})
   *   <li/>Custom descriptor set {{@link #addMessageTypes})
   * </ul>
   */
  @CanIgnoreReturnValue
  CelRuntimeBuilder addFileTypes(FileDescriptorSet fileDescriptorSet);

  /**
   * Set a custom type factory for the runtime.
   *
   * <p>Note: it is valid to combine type factory methods within the runtime. Only the options which
   * have been configured will be used.
   *
   * <p>The type creation search order is as follows:
   *
   * <ul>
   *   <li/>Custom type factory ({@code #setTypeFactory})
   *   <li/>Custom descriptor set {{@link #addMessageTypes})
   * </ul>
   */
  @CanIgnoreReturnValue
  CelRuntimeBuilder setTypeFactory(Function<String, Message.Builder> typeFactory);

  /**
   * Sets the {@code celValueProvider} for resolving values during evaluation. The provided value
   * provider will be used first before falling back to the built-in {@link
   * dev.cel.common.values.ProtoMessageValueProvider} for resolving protobuf messages.
   *
   * <p>Note {@link CelOptions#enableCelValue()} must be enabled or this method will be a no-op.
   */
  @CanIgnoreReturnValue
  CelRuntimeBuilder setValueProvider(CelValueProvider celValueProvider);

  /** Enable or disable the standard CEL library functions and variables. */
  @CanIgnoreReturnValue
  CelRuntimeBuilder setStandardEnvironmentEnabled(boolean value);

  /**
   * Override the standard functions for the runtime. This can be used to subset the standard
   * environment to only expose the desired function overloads to the runtime. {@link
   * #setStandardEnvironmentEnabled(boolean)} must be set to false for this to take effect.
   */
  @CanIgnoreReturnValue
  CelRuntimeBuilder setStandardFunctions(CelStandardFunctions standardFunctions);

  /** Adds one or more libraries for runtime. */
  @CanIgnoreReturnValue
  CelRuntimeBuilder addLibraries(CelRuntimeLibrary... libraries);

  /** Adds a collection of libraries for runtime. */
  @CanIgnoreReturnValue
  CelRuntimeBuilder addLibraries(Iterable<? extends CelRuntimeLibrary> libraries);

  /**
   * Sets a proto ExtensionRegistry to assist with unpacking Any messages containing a proto2
   extension field.
   */
  @CanIgnoreReturnValue
  CelRuntimeBuilder setExtensionRegistry(ExtensionRegistry extensionRegistry);

  /** Build a new instance of the {@code CelRuntime}. */
  @CheckReturnValue
  CelRuntime build();
}
