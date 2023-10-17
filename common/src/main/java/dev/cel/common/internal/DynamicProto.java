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

import com.google.errorprone.annotations.CheckReturnValue;
import com.google.errorprone.annotations.Immutable;
import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import com.google.protobuf.DynamicMessage;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Message;
import dev.cel.common.annotations.Internal;
import java.util.Optional;

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
  private final ProtoMessageFactory protoMessageFactory;

  public static DynamicProto create(ProtoMessageFactory protoMessageFactory) {
    return new DynamicProto(protoMessageFactory);
  }

  DynamicProto(ProtoMessageFactory protoMessageFactory) {
    this.protoMessageFactory = checkNotNull(protoMessageFactory);
  }

  /** Attempts to unpack an Any message. */
  public Optional<Message> maybeUnpackAny(Message msg) {
    try {
      Any any =
          msg instanceof Any
              ? (Any) msg
              : Any.parseFrom(
                  msg.toByteString(),
                  protoMessageFactory.getDescriptorPool().getExtensionRegistry());

      return Optional.of(unpack(any));
    } catch (InvalidProtocolBufferException e) {
      return Optional.empty();
    }
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
        protoMessageFactory
            .newBuilder(messageTypeName)
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
        protoMessageFactory.newBuilder(input.getDescriptorForType().getFullName());
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
      return builder
          .mergeFrom(inputBytes, protoMessageFactory.getDescriptorPool().getExtensionRegistry())
          .build();
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
