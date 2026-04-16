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



import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.DynamicMessage;
import com.google.protobuf.ExtensionRegistry;
import com.google.protobuf.Message;
import com.google.protobuf.TypeRegistry;
import dev.cel.common.CelDescriptors;

/** Utility class for creating registries from a file descriptor set. */
public final class RegistryUtils {

  /** Returns the {@link TypeRegistry} for the given file descriptor set. */
  public static TypeRegistry getTypeRegistry(CelDescriptors descriptors) {
    return TypeRegistry.newBuilder().add(descriptors.messageTypeDescriptors()).build();
  }

  /** Returns the {@link ExtensionRegistry} for the given file descriptor set. */
  public static ExtensionRegistry getExtensionRegistry(CelDescriptors descriptors) {
    ExtensionRegistry extensionRegistry = ExtensionRegistry.newInstance();

    descriptors
        .extensionDescriptors()
        .forEach(
            (descriptorName, descriptor) -> {
              if (descriptor.getType().equals(FieldDescriptor.Type.MESSAGE)) {
                Message output = DynamicMessage.getDefaultInstance(descriptor.getMessageType());
                extensionRegistry.add(descriptor, output);
              } else {
                extensionRegistry.add(descriptor);
              }
            });
    return extensionRegistry;
  }

  private RegistryUtils() {}
}
