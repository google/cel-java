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

import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.DynamicMessage;
import com.google.protobuf.Message;
import dev.cel.common.annotations.Internal;
import java.util.Optional;

/** DefaultMessageFactory produces {@link Message.Builder} instances by protobuf name. */
@Internal
public final class DefaultMessageFactory implements ProtoMessageFactory {

  /** A default message factory instance that can construct well known typed messages. */
  public static final DefaultMessageFactory INSTANCE = create(DefaultDescriptorPool.INSTANCE);

  private final CelDescriptorPool celDescriptorPool;

  public static DefaultMessageFactory create(CelDescriptorPool celDescriptorPool) {
    return new DefaultMessageFactory(celDescriptorPool);
  }

  @Override
  public CelDescriptorPool getDescriptorPool() {
    return celDescriptorPool;
  }

  @Override
  public Optional<Message.Builder> newBuilder(String messageName) {
    Optional<Descriptor> descriptor = celDescriptorPool.findDescriptor(messageName);
    if (!descriptor.isPresent()) {
      return Optional.empty();
    }

    // If the descriptor that's resolved does not match the descriptor instance in the message
    // factory, the call to fetch the prototype will return null, and a dynamic proto message
    // should be used as a fallback.
    Optional<Message> message =
        DefaultInstanceMessageFactory.getInstance().getPrototype(descriptor.get());

    if (message.isPresent()) {
      return message.map(Message::toBuilder);
    }

    return Optional.of(DynamicMessage.newBuilder(descriptor.get()));
  }

  private DefaultMessageFactory(CelDescriptorPool celDescriptorPool) {
    this.celDescriptorPool = celDescriptorPool;
  }
}
