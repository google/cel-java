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
import com.google.protobuf.Any;
import com.google.protobuf.BoolValue;
import com.google.protobuf.BytesValue;
import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.DoubleValue;
import com.google.protobuf.Duration;
import com.google.protobuf.Empty;
import com.google.protobuf.ExtensionRegistry;
import com.google.protobuf.FieldMask;
import com.google.protobuf.FloatValue;
import com.google.protobuf.Int32Value;
import com.google.protobuf.Int64Value;
import com.google.protobuf.ListValue;
import com.google.protobuf.StringValue;
import com.google.protobuf.Struct;
import com.google.protobuf.Timestamp;
import com.google.protobuf.UInt32Value;
import com.google.protobuf.UInt64Value;
import com.google.protobuf.Value;
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

  private static final ImmutableMap<WellKnownProto, Descriptor> WELL_KNOWN_PROTO_TO_DESCRIPTORS =
      ImmutableMap.<WellKnownProto, Descriptor>builder()
          .put(WellKnownProto.ANY_VALUE, Any.getDescriptor())
          .put(WellKnownProto.BOOL_VALUE, BoolValue.getDescriptor())
          .put(WellKnownProto.BYTES_VALUE, BytesValue.getDescriptor())
          .put(WellKnownProto.DOUBLE_VALUE, DoubleValue.getDescriptor())
          .put(WellKnownProto.DURATION, Duration.getDescriptor())
          .put(WellKnownProto.FLOAT_VALUE, FloatValue.getDescriptor())
          .put(WellKnownProto.INT32_VALUE, Int32Value.getDescriptor())
          .put(WellKnownProto.INT64_VALUE, Int64Value.getDescriptor())
          .put(WellKnownProto.STRING_VALUE, StringValue.getDescriptor())
          .put(WellKnownProto.TIMESTAMP, Timestamp.getDescriptor())
          .put(WellKnownProto.UINT32_VALUE, UInt32Value.getDescriptor())
          .put(WellKnownProto.UINT64_VALUE, UInt64Value.getDescriptor())
          .put(WellKnownProto.JSON_LIST_VALUE, ListValue.getDescriptor())
          .put(WellKnownProto.JSON_STRUCT_VALUE, Struct.getDescriptor())
          .put(WellKnownProto.JSON_VALUE, Value.getDescriptor())
          .put(WellKnownProto.EMPTY, Empty.getDescriptor())
          .put(WellKnownProto.FIELD_MASK, FieldMask.getDescriptor())
          .buildOrThrow();

  private static final ImmutableMap<String, Descriptor> WELL_KNOWN_TYPE_NAME_TO_DESCRIPTORS =
      stream(WellKnownProto.values())
          .collect(toImmutableMap(WellKnownProto::typeName, WELL_KNOWN_PROTO_TO_DESCRIPTORS::get));

  /** A DefaultDescriptorPool instance with just well known types loaded. */
  public static final DefaultDescriptorPool INSTANCE =
      new DefaultDescriptorPool(
          WELL_KNOWN_TYPE_NAME_TO_DESCRIPTORS,
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
    Map<String, Descriptor> descriptorMap =
        new HashMap<>(WELL_KNOWN_TYPE_NAME_TO_DESCRIPTORS); // Using a hashmap to allow deduping

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
