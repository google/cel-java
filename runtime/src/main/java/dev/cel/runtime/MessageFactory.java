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

package dev.cel.runtime;

import com.google.common.collect.ImmutableList;
import com.google.errorprone.annotations.Immutable;
import com.google.protobuf.Message;
import dev.cel.common.annotations.Internal;
import org.jspecify.nullness.Nullable;

/**
 * The {@code MessageFactory} provides a method to create a protobuf builder objects by name.
 *
 * <p>CEL Library Internals. Do Not Use.
 */
@Immutable
@Internal
@FunctionalInterface
public interface MessageFactory {

  /**
   * Create a {@code Message.Builder} instance for the protobuf by {@code messageName}.
   *
   * <p>Returns {@code null} if the builder could not be created.
   */
  Message.@Nullable Builder newBuilder(String messageName);

  /**
   * The {@code CombinedMessageFactory} takes one or more {@code MessageFactory} instances and
   * attempts to create a {@code Message.Builder} instance for a given {@code messageName} by
   * calling each {@code MessageFactory} in the order that they are provided to the constructor.
   */
  @Immutable
  final class CombinedMessageFactory implements MessageFactory {

    private final ImmutableList<MessageFactory> messageFactories;

    public CombinedMessageFactory(Iterable<MessageFactory> messageFactories) {
      this.messageFactories = ImmutableList.copyOf(messageFactories);
    }

    @Override
    public Message.@Nullable Builder newBuilder(String messageName) {
      for (MessageFactory messageFactory : messageFactories) {
        Message.Builder builder = messageFactory.newBuilder(messageName);
        if (builder != null) {
          return builder;
        }
      }
      return null;
    }
  }
}
