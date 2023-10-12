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

import com.google.common.collect.ImmutableList;
import com.google.errorprone.annotations.Immutable;
import com.google.protobuf.Message;
import java.util.Optional;

/** {@code ProtoMessageFactory} provides a method to create a protobuf builder objects by name. */
@Immutable
@FunctionalInterface
public interface ProtoMessageFactory {

  /**
   * Constructs a new {@link Message.Builder} for a fully qualified proto message type. An empty
   * result is returned if a descriptor is missing for the message type name.
   */
  Optional<Message.Builder> newBuilder(String messageName);

  /** Gets the underlying descriptor pool used to construct proto messages. */
  default CelDescriptorPool getDescriptorPool() {
    return DefaultDescriptorPool.INSTANCE;
  }

  /**
   * The {@link CombinedMessageFactory} takes one or more {@link ProtoMessageFactory} instances and
   * attempts to create a {@code Message.Builder} instance for a given message name by calling each
   * message factory in the order that they are provided to the constructor.
   */
  @Immutable
  final class CombinedMessageFactory implements ProtoMessageFactory {

    private final ImmutableList<ProtoMessageFactory> messageFactories;

    public CombinedMessageFactory(Iterable<ProtoMessageFactory> messageFactories) {
      this.messageFactories = ImmutableList.copyOf(messageFactories);
    }

    @Override
    public Optional<Message.Builder> newBuilder(String messageName) {
      for (ProtoMessageFactory messageFactory : messageFactories) {
        Optional<Message.Builder> builder = messageFactory.newBuilder(messageName);
        if (builder.isPresent()) {
          return builder;
        }
      }
      return Optional.empty();
    }
  }
}
