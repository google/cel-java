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
package dev.cel.testing.utils;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static dev.cel.testing.utils.ClassLoaderUtils.loadDescriptors;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.Files;
import com.google.protobuf.DescriptorProtos.FileDescriptorSet;
import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.FileDescriptor;
import com.google.protobuf.ExtensionRegistry;
import dev.cel.common.CelDescriptorUtil;
import dev.cel.common.CelDescriptors;
import java.io.File;
import java.io.IOException;

/** Utility class for working with proto descriptors. */
public final class ProtoDescriptorUtils {

  /**
   * Returns all the descriptors from the JVM.
   *
   * @return The {@link CelDescriptors} object containing all the descriptors.
   */
  public static CelDescriptors getAllDescriptorsFromJvm(String fileDescriptorSetPath)
      throws IOException {
    ImmutableList<Descriptor> compileTimeLoadedDescriptors = loadDescriptors();
    FileDescriptorSet fileDescriptorSet = getFileDescriptorSet(fileDescriptorSetPath);
    ImmutableSet<String> runtimeFileDescriptorNames =
        CelDescriptorUtil.getFileDescriptorsFromFileDescriptorSet(fileDescriptorSet).stream()
            .map(FileDescriptor::getFullName)
            .collect(toImmutableSet());

    // Get all the file descriptors from the descriptors which are loaded from the JVM and use the
    // ones which match the ones provided by the user in the file descriptor set.
    ImmutableList<FileDescriptor> userProvidedFileDescriptors =
        CelDescriptorUtil.getFileDescriptorsForDescriptors(compileTimeLoadedDescriptors).stream()
            .filter(
                fileDescriptor -> runtimeFileDescriptorNames.contains(fileDescriptor.getFullName()))
            .collect(toImmutableList());

    // Get all the descriptors from the file descriptors above which include nested, extension and
    // message type descriptors as well.
    return CelDescriptorUtil.getAllDescriptorsFromFileDescriptor(userProvidedFileDescriptors);
  }

  private static FileDescriptorSet getFileDescriptorSet(String fileDescriptorSetPath)
      throws IOException {
    // We can pass an empty extension registry here because extensions are recovered later when
    // creating the extension registry in {@link #createExtensionRegistry}.
    return FileDescriptorSet.parseFrom(
        Files.toByteArray(new File(fileDescriptorSetPath)), ExtensionRegistry.newInstance());
  }

  private ProtoDescriptorUtils() {}
}
