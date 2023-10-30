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

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.ImmutableMap.toImmutableMap;
import static java.util.Arrays.stream;

import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import com.google.errorprone.annotations.Immutable;
import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.ExtensionRegistry;
import dev.cel.common.CelDescriptors;
import dev.cel.common.annotations.Internal;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * A descriptor pool that has descriptors pre-loaded for well-known types defined by {@link
 * WellKnownProto}.
 *
 * <p>CEL Library Internals. Do Not Use.
 */
@Immutable
@Internal
public final class DefaultDescriptorPool implements CelDescriptorPool {
  private static final ImmutableMap<String, Descriptor> WELL_KNOWN_TYPE_DESCRIPTORS =
      stream(WellKnownProto.values())
          .collect(toImmutableMap(WellKnownProto::typeName, WellKnownProto::descriptor));

  /** A DefaultDescriptorPool instance with just well known types loaded. */
  public static final DefaultDescriptorPool INSTANCE =
      new DefaultDescriptorPool(
          WELL_KNOWN_TYPE_DESCRIPTORS,
          ImmutableMultimap.of(),
          ExtensionRegistry.getEmptyRegistry());

  // K: Fully qualified message type name, V: Message descriptor
  private final ImmutableMap<String, Descriptor> descriptorMap;

  // K: Fully qualified message type name (of containing descriptor)
  // V: Field descriptor for the extension message
  private final ImmutableMultimap<String, FieldDescriptor> extensionDescriptorMap;

  @SuppressWarnings("Immutable") // ExtensionRegistry is immutable, just not marked as such.
  private final ExtensionRegistry extensionRegistry;

  public static DefaultDescriptorPool create(CelDescriptors celDescriptors) {
    return create(celDescriptors, ExtensionRegistry.getEmptyRegistry());
  }

  public static DefaultDescriptorPool create(
      CelDescriptors celDescriptors, ExtensionRegistry extensionRegistry) {
    Map<String, Descriptor> descriptorMap = new HashMap<>(); // Using a hashmap to allow deduping
    stream(WellKnownProto.values()).forEach(d -> descriptorMap.put(d.typeName(), d.descriptor()));

    for (Descriptor descriptor : celDescriptors.messageTypeDescriptors()) {
      descriptorMap.putIfAbsent(descriptor.getFullName(), descriptor);
    }

    return new DefaultDescriptorPool(
        ImmutableMap.copyOf(descriptorMap),
        celDescriptors.extensionDescriptors(),
        extensionRegistry);
  }

  @Override
  public Optional<Descriptor> findDescriptor(String name) {
    return Optional.ofNullable(descriptorMap.get(name));
  }

  @Override
  public Optional<FieldDescriptor> findExtensionDescriptor(
      Descriptor containingDescriptor, String fieldName) {
    String typeName = containingDescriptor.getFullName();
    ImmutableCollection<FieldDescriptor> fieldDescriptors = extensionDescriptorMap.get(typeName);

    return fieldDescriptors.stream().filter(d -> d.getFullName().equals(fieldName)).findFirst();
  }

  @Override
  public ExtensionRegistry getExtensionRegistry() {
    return extensionRegistry;
  }

  private DefaultDescriptorPool(
      ImmutableMap<String, Descriptor> descriptorMap,
      ImmutableMultimap<String, FieldDescriptor> extensionDescriptorMap,
      ExtensionRegistry extensionRegistry) {
    this.descriptorMap = checkNotNull(descriptorMap);
    this.extensionDescriptorMap = checkNotNull(extensionDescriptorMap);
    this.extensionRegistry = checkNotNull(extensionRegistry);
  }
}
