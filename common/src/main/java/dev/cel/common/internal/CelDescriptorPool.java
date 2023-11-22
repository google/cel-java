// Copyright 2023 Google LLC
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

import com.google.errorprone.annotations.Immutable;
import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.ExtensionRegistry;
import dev.cel.common.annotations.Internal;
import java.util.Optional;

/**
 * {@link CelDescriptorPool} allows lookup of descriptors for message types and field descriptors
 * for Proto2 extension messages.
 *
 * <p>CEL Library Internals. Do Not Use.
 */
@Immutable
@Internal
public interface CelDescriptorPool {

  /** Finds the descriptor by fully qualified message type. */
  Optional<Descriptor> findDescriptor(String name);

  /**
   * Finds the corresponding field descriptor for an extension field on a message. The field name
   * must be fully-qualified.
   */
  Optional<FieldDescriptor> findExtensionDescriptor(
      Descriptor containingDescriptor, String fieldName);

  /**
   * Retrieves the registered extension registry. This is specifically needed to handle unpacking
   * Any messages containing Proto2 extension messages.
   */
  ExtensionRegistry getExtensionRegistry();
}
