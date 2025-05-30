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
package dev.cel.testing.testrunner;

import com.google.common.io.Files;
import com.google.protobuf.DescriptorProtos.FileDescriptorSet;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.Descriptors.FileDescriptor;
import com.google.protobuf.ExtensionRegistry;
import com.google.protobuf.Message;
import com.google.protobuf.TypeRegistry;
import dev.cel.common.CelDescriptorUtil;
import dev.cel.common.CelDescriptors;
import dev.cel.common.internal.CelDescriptorPool;
import dev.cel.common.internal.DefaultDescriptorPool;
import dev.cel.common.internal.DefaultMessageFactory;
import java.io.File;
import java.io.IOException;
import java.util.NoSuchElementException;
import java.util.Set;

/** Utility class for creating registries from a file descriptor set. */
final class RegistryUtils {

  private RegistryUtils() {}

  /** Returns the {@link FileDescriptorSet} for the given file descriptor set path. */
  static FileDescriptorSet getFileDescriptorSet(String fileDescriptorSetPath) throws IOException {
    return FileDescriptorSet.parseFrom(
        Files.toByteArray(new File(fileDescriptorSetPath)), ExtensionRegistry.newInstance());
  }

  /** Returns the {@link TypeRegistry} for the given file descriptor set. */
  static TypeRegistry getTypeRegistry(Set<FileDescriptor> fileDescriptors) throws IOException {
    return createTypeRegistry(fileDescriptors);
  }

  /** Returns the {@link ExtensionRegistry} for the given file descriptor set. */
  static ExtensionRegistry getExtensionRegistry(Set<FileDescriptor> fileDescriptors)
      throws IOException {
    return createExtensionRegistry(fileDescriptors);
  }

  private static TypeRegistry createTypeRegistry(Set<FileDescriptor> fileDescriptors) {
    CelDescriptors allDescriptors =
        CelDescriptorUtil.getAllDescriptorsFromFileDescriptor(fileDescriptors);
    return TypeRegistry.newBuilder().add(allDescriptors.messageTypeDescriptors()).build();
  }

  private static ExtensionRegistry createExtensionRegistry(Set<FileDescriptor> fileDescriptors) {
    ExtensionRegistry extensionRegistry = ExtensionRegistry.newInstance();

    CelDescriptors allDescriptors =
        CelDescriptorUtil.getAllDescriptorsFromFileDescriptor(fileDescriptors);

    CelDescriptorPool pool = DefaultDescriptorPool.create(allDescriptors);

    // We need to create a default message factory because there would always be difference in
    // reference between the default instance's descriptor and the descriptor in the pool if the
    // file descriptor set is created at runtime, therefore
    // we need to create a default message factory to get the default instance for each descriptor
    // because it falls back to the DynamicMessages.
    //
    // For more details, see: b/292174333
    DefaultMessageFactory defaultMessageFactory = DefaultMessageFactory.create(pool);

    allDescriptors
        .extensionDescriptors()
        .forEach(
            (descriptorName, descriptor) -> {
              if (descriptor.getType().equals(FieldDescriptor.Type.MESSAGE)) {
                Message.Builder defaultInstance =
                    defaultMessageFactory
                        .newBuilder(descriptor.getMessageType().getFullName())
                        .orElseThrow(
                            () ->
                                new NoSuchElementException(
                                    "Could not find a default message for: "
                                        + descriptor.getFullName()));
                extensionRegistry.add(descriptor, defaultInstance.build());
              } else {
                extensionRegistry.add(descriptor);
              }
            });

    return extensionRegistry;
  }
}
