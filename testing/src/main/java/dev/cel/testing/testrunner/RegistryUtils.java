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

import static dev.cel.testing.utils.ProtoDescriptorUtils.getAllDescriptorsFromJvm;

import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.ExtensionRegistry;
import com.google.protobuf.Message;
import com.google.protobuf.TypeRegistry;
import dev.cel.common.internal.DefaultInstanceMessageFactory;
import java.io.IOException;
import java.util.NoSuchElementException;

/** Utility class for creating registries from a file descriptor set. */
public final class RegistryUtils {

  /** Returns the {@link TypeRegistry} for the given file descriptor set. */
  public static TypeRegistry getTypeRegistry() throws IOException {
    return TypeRegistry.newBuilder()
        .add(getAllDescriptorsFromJvm().messageTypeDescriptors())
        .build();
  }

  /** Returns the {@link ExtensionRegistry} for the given file descriptor set. */
  public static ExtensionRegistry getExtensionRegistry() throws IOException {
    ExtensionRegistry extensionRegistry = ExtensionRegistry.newInstance();

    getAllDescriptorsFromJvm()
        .extensionDescriptors()
        .forEach(
            (descriptorName, descriptor) -> {
              if (descriptor.getType().equals(FieldDescriptor.Type.MESSAGE)) {
                Message output =
                    DefaultInstanceMessageFactory.getInstance()
                        .getPrototype(descriptor.getMessageType())
                        .orElseThrow(
                            () ->
                                new NoSuchElementException(
                                    "Could not find a default message for: "
                                        + descriptor.getFullName()));
                extensionRegistry.add(descriptor, output);
              } else {
                extensionRegistry.add(descriptor);
              }
            });
    return extensionRegistry;
  }

  private RegistryUtils() {}
}
