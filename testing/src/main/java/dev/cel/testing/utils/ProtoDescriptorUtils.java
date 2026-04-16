// Copyright 2026 Google LLC
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

import com.google.common.io.Files;
import com.google.protobuf.DescriptorProtos.FileDescriptorSet;
import com.google.protobuf.ExtensionRegistry;
import dev.cel.common.CelDescriptorUtil;
import dev.cel.common.CelDescriptors;
import java.io.File;
import java.io.IOException;

/** Utility class for working with proto descriptors. */
public final class ProtoDescriptorUtils {

  /**
   * Returns all the descriptors from the file descriptor set file.
   *
   * @return The {@link CelDescriptors} object containing all the descriptors.
   */
  public static CelDescriptors getDescriptorsFromFile(String fileDescriptorSetPath)
      throws IOException {
    FileDescriptorSet fileDescriptorSet = getFileDescriptorSet(fileDescriptorSetPath);
    return CelDescriptorUtil.getAllDescriptorsFromFileDescriptor(
        CelDescriptorUtil.getFileDescriptorsFromFileDescriptorSet(fileDescriptorSet));
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
