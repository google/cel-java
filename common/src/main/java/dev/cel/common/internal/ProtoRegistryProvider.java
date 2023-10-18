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

import com.google.protobuf.ExtensionRegistry;
import com.google.protobuf.TypeRegistry;
import dev.cel.common.annotations.Internal;

/**
 * ProtoRegistryProvider provides Extension and Type registries for handling Protobuf.Any messages.
 *
 * <p>CEL Library Internals. Do Not Use.
 */
@Internal
public final class ProtoRegistryProvider {

  public static ExtensionRegistry getExtensionRegistry() {
    return ExtensionRegistry.getEmptyRegistry();
  }

  static TypeRegistry getTypeRegistry() {
    return TypeRegistry.getEmptyTypeRegistry();
  }

  private ProtoRegistryProvider() {}
}
