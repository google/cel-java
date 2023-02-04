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

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.ImmutableMap.toImmutableMap;
import static java.util.Arrays.stream;

import com.google.auto.value.AutoBuilder;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import com.google.errorprone.annotations.CheckReturnValue;
import com.google.errorprone.annotations.Immutable;
import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.DynamicMessage;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Message;
import dev.cel.common.CelDescriptorUtil;
import dev.cel.common.CelDescriptors;
import dev.cel.common.annotations.Internal;
import dev.cel.common.types.CelTypes;
import java.util.Map.Entry;
import java.util.Optional;
import org.jspecify.nullness.Nullable;

/**
 * The {@code DynamicProto} class supports the conversion of {@link Any} values to concrete {@code
 * Message} types based on provided descriptors.
 *
 * <p>CEL Library Internals. Do Not Use.
 */
@Immutable
@CheckReturnValue
@Internal
public final class DynamicProto {

  private static final ImmutableMap<String, Descriptor> WELL_KNOWN_DESCRIPTORS =
      stream(ProtoAdapter.WellKnownProto.values())
          .collect(toImmutableMap(d -> d.typeName(), d -> d.descriptor()));

  private final ImmutableMap<String, Descriptor> dynamicDescriptors;
  private final ImmutableMultimap<String, FieldDescriptor> dynamicExtensionDescriptors;
  private final ProtoMessageFactory protoMessageFactory;

  /** {@code ProtoMessageFactory} provides a method to create a protobuf builder objects by name. */
  @Immutable
  @FunctionalInterface
  public interface ProtoMessageFactory {
    Message.@Nullable Builder newBuilder(String messageName);
  }

  /** Builder for configuring the {@link DynamicProto}. */
  @AutoBuilder(ofClass = DynamicProto.class)
  public abstract static class Builder {

    /** Sets {@link CelDescriptors} to unpack any message types. */
    public abstract Builder setDynamicDescriptors(CelDescriptors celDescriptors);

    /** Sets a custom type factory to unpack any message types. */
    public abstract Builder setProtoMessageFactory(ProtoMessageFactory factory);

    /** Builds a new instance of {@link DynamicProto} */
    @CheckReturnValue
    public abstract DynamicProto build();
  }

  public static Builder newBuilder() {
    return new AutoBuilder_DynamicProto_Builder()
        .setDynamicDescriptors(CelDescriptors.builder().build())
        .setProtoMessageFactory((typeName) -> null);
  }

  DynamicProto(
      CelDescriptors dynamicDescriptors,
      ProtoMessageFactory protoMessageFactory) {
    ImmutableMap<String, Descriptor> messageTypeDescriptorMap =
        CelDescriptorUtil.descriptorCollectionToMap(dynamicDescriptors.messageTypeDescriptors());
    ImmutableMap<String, Descriptor> filteredDescriptors =
        messageTypeDescriptorMap.entrySet().stream()
            .filter(e -> !WELL_KNOWN_DESCRIPTORS.containsKey(e.getKey()))
            .collect(toImmutableMap(Entry::getKey, Entry::getValue));
    this.dynamicDescriptors =
        ImmutableMap.<String, Descriptor>builder()
            .putAll(WELL_KNOWN_DESCRIPTORS)
            .putAll(filteredDescriptors)
            .buildOrThrow();
    this.dynamicExtensionDescriptors = checkNotNull(dynamicDescriptors.extensionDescriptors());
    this.protoMessageFactory = checkNotNull(protoMessageFactory);
  }

  /**
   * Unpack an {@code Any} value to a concrete {@code Message} value.
   *
   * <p>For protobuf types which have been linked into the binary, the method will return an
   * instance of a derived {@code Message} type. However, for messages unpacked from the configured
   * descriptors, the result will be a {@link DynamicMessage} instance.
   */
  public Message unpack(Any any) throws InvalidProtocolBufferException {
    String messageTypeName =
        getTypeNameFromTypeUrl(any.getTypeUrl())
            .orElseThrow(
                () ->
                    new InvalidProtocolBufferException(
                        String.format("malformed type URL: %s", any.getTypeUrl())));

    Message.Builder builder =
        newMessageBuilder(messageTypeName)
            .orElseThrow(
                () ->
                    new InvalidProtocolBufferException(
                        String.format("no such descriptor for type: %s", messageTypeName)));

    return merge(builder, any.getValue());
  }

  /**
   * This method will attempt to adapt a {@code DynamicMessage} instance to a generated {@code
   * Message} instance if possible. This scenario can occur during field selection on a higher level
   * dynamic message whose type isn't linked in the binary, but the field's type is.
   */
  public Message maybeAdaptDynamicMessage(DynamicMessage input) {
    Optional<Message.Builder> maybeBuilder =
        newMessageBuilder(input.getDescriptorForType().getFullName());
    if (!maybeBuilder.isPresent() || maybeBuilder.get() instanceof DynamicMessage.Builder) {
      // Just return the same input if:
      // 1. We didn't get a builder back because there's no descriptor (nothing we can do)
      // 2. We got a DynamicBuilder back because a different descriptor was found (nothing we need
      // to do)
      return input;
    }

    return merge(maybeBuilder.get(), input.toByteString());
  }

  /**
   * This method instantiates a builder for the given {@code typeName} assuming one is configured
   * within the descriptor set provided to the {@code DynamicProto} constructor.
   *
   * <p>When the {@code useLinkedTypes} flag is set, the {@code Message.Builder} returned will be
   * the concrete builder instance linked into the binary if it is present; otherwise, the result
   * will be a {@code DynamicMessageBuilder}.
   */
  public Optional<Message.Builder> newMessageBuilder(String typeName) {
    if (!CelTypes.isWellKnownType(typeName)) {
      // Check if the message factory can produce a concrete message via custom type factory
      // first.
      Message.Builder builder = protoMessageFactory.newBuilder(typeName);
      if (builder != null) {
        return Optional.of(builder);
      }
    }

    Optional<Descriptor> descriptor = maybeGetDescriptor(typeName);
    if (!descriptor.isPresent()) {
      return Optional.empty();
    }
    // If the descriptor that's resolved does not match the descriptor instance in the message
    // factory, the call to fetch the prototype will return null, and a dynamic proto message
    // should be used as a fallback.
    Optional<Message> message =
        DefaultInstanceMessageFactory.getInstance().getPrototype(descriptor.get());
    if (message.isPresent()) {
      return Optional.of(message.get().toBuilder());
    }

    // Fallback to a dynamic proto instance.
    return Optional.of(DynamicMessage.newBuilder(descriptor.get()));
  }

  private Optional<Descriptor> maybeGetDescriptor(String typeName) {

    Descriptor descriptor = ProtoRegistryProvider.getTypeRegistry().find(typeName);
    return Optional.ofNullable(descriptor != null ? descriptor : dynamicDescriptors.get(typeName));
  }

  /** Gets the corresponding field descriptor for an extension field on a message. */
  public Optional<FieldDescriptor> maybeGetExtensionDescriptor(
      Descriptor containingDescriptor, String fieldName) {

    String typeName = containingDescriptor.getFullName();
    ImmutableCollection<FieldDescriptor> fieldDescriptors =
        dynamicExtensionDescriptors.get(typeName);
    if (fieldDescriptors == null) {
      return Optional.empty();
    }

    return fieldDescriptors.stream().filter(d -> d.getFullName().equals(fieldName)).findFirst();
  }

  /**
   * Merge takes in a Message builder and merges another message bytes into the builder. Some
   * example usages are:
   *
   * <ol>
   *   <li>1. Merging a DynamicMessage content into a concrete message
   *   <li>2. Merging an Any packed message content into a concrete message
   * </ol>
   */
  private Message merge(Message.Builder builder, ByteString inputBytes) {
    try {
      return builder.mergeFrom(inputBytes, ProtoRegistryProvider.getExtensionRegistry()).build();
    } catch (InvalidProtocolBufferException e) {
      throw new AssertionError("Failed to merge input message into the message builder", e);
    }
  }

  private static Optional<String> getTypeNameFromTypeUrl(String typeUrl) {
    int pos = typeUrl.lastIndexOf('/');
    if (pos != -1) {
      return Optional.of(typeUrl.substring(pos + 1));
    }
    return Optional.empty();
  }
}
