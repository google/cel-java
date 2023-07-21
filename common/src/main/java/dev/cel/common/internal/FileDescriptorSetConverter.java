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

package dev.cel.common.internal;

import com.google.common.base.VerifyException;
import com.google.common.collect.ImmutableSet;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.errorprone.annotations.CheckReturnValue;
import com.google.protobuf.DescriptorProtos.FileDescriptorProto;
import com.google.protobuf.DescriptorProtos.FileDescriptorSet;
import com.google.protobuf.Descriptors.DescriptorValidationException;
import com.google.protobuf.Descriptors.FileDescriptor;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Utility class for converting from a {@code FileDescriptorSet} to a collection of {@code
 * FileDescriptor} instances.
 *
 * <p>CEL Library Internals. Do Not Use.
 */
@CheckReturnValue
public final class FileDescriptorSetConverter {

  /**
   * Extracts all FileDescriptors from the FileDescriptorSet
   *
   * <p>Warning: This will produce a unique FileDescriptor instances. Use with care especially in
   * hermetic environments.
   */
  public static ImmutableSet<FileDescriptor> convert(FileDescriptorSet fileDescriptorSet) {
    Map<String, FileDescriptorProto> descriptorProtos = new HashMap<>();
    for (FileDescriptorProto fileProto : fileDescriptorSet.getFileList()) {
      descriptorProtos.put(fileProto.getName(), fileProto);
    }
    Map<String, FileDescriptor> fileDescriptors = new HashMap<>();
    for (FileDescriptorProto fileProto : fileDescriptorSet.getFileList()) {
      readDescriptor(fileProto.getName(), descriptorProtos, fileDescriptors);
    }
    return ImmutableSet.copyOf(fileDescriptors.values());
  }

  /**
   * Helper method for the DescriptorTypeProvider constructor which takes a file descriptor set
   * proto as produced by tools like the protocol compiler. In order to turn the proto into an
   * in-memory descriptor, we need to call {@link FileDescriptor#buildFrom(FileDescriptorProto,
   * FileDescriptor[])} in the expected way, having all dependencies already recursively built.
   */
  @CanIgnoreReturnValue
  private static FileDescriptor readDescriptor(
      String fileName,
      Map<String, FileDescriptorProto> descriptorProtos,
      Map<String, FileDescriptor> descriptors) {
    if (descriptors.containsKey(fileName)) {
      return descriptors.get(fileName);
    }
    if (!descriptorProtos.containsKey(fileName)) {
      throw new IllegalArgumentException(
          "file descriptor set with unresolved proto file: " + fileName);
    }
    FileDescriptorProto fileProto = descriptorProtos.get(fileName);
    // Read dependencies first, they are needed to create the logical descriptor from the proto.
    List<FileDescriptor> deps = new ArrayList<>();
    for (String dep : fileProto.getDependencyList()) {
      deps.add(readDescriptor(dep, descriptorProtos, descriptors));
    }
    // Create the file descriptor, cache, and return.
    try {
      FileDescriptor result =
          FileDescriptor.buildFrom(fileProto, deps.toArray(new FileDescriptor[0]));
      descriptors.put(fileName, result);
      return result;
    } catch (DescriptorValidationException e) {
      throw new VerifyException(e);
    }
  }

  private FileDescriptorSetConverter() {}
}
