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

package dev.cel.common.internal;

import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.FileDescriptor;
import com.google.protobuf.GeneratorNames;
import dev.cel.common.annotations.Internal;

/**
 * Helper class for constructing a fully qualified Java class name from a protobuf descriptor.
 *
 * <p>CEL Library Internals. Do Not Use.
 */
@Internal
public final class ProtoJavaQualifiedNames {
  /**
   * Retrieves the full Java class name from the given descriptor
   *
   * @return fully qualified class name.
   *     <p>Example 1: dev.cel.expr.Value
   *     <p>Example 2: com.google.rpc.context.AttributeContext$Resource (Nested classes)
   *     <p>Example 3: com.google.api.expr.cel.internal.testdata$SingleFileProto$SingleFile$Path
   *     (Nested class with java multiple files disabled)
   */
  public static String getFullyQualifiedJavaClassName(Descriptor descriptor) {
    return GeneratorNames.getBytecodeClassName(descriptor);
  }

  /**
   * Gets the java package name from the descriptor. See
   * https://developers.google.com/protocol-buffers/docs/reference/java-generated#package for rules
   * on package name generation
   */
  public static String getJavaPackageName(FileDescriptor fileDescriptor) {
    return GeneratorNames.getFileJavaPackage(fileDescriptor.toProto());
  }

  private ProtoJavaQualifiedNames() {}
}
