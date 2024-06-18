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

import com.google.errorprone.annotations.Immutable;
import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.DynamicMessage;
import com.google.protobuf.Message;
import dev.cel.common.CelDescriptorUtil;
import dev.cel.common.CelDescriptors;
import dev.cel.common.CelOptions;
import dev.cel.common.internal.CelDescriptorPool;
import dev.cel.common.internal.DefaultDescriptorPool;
import dev.cel.common.internal.DefaultMessageFactory;
import dev.cel.common.internal.ProtoMessageFactory;
import java.util.Collection;
import org.jspecify.annotations.Nullable;

/**
 * The {@code DynamicMessageFactory} creates {@link DynamicMessage} instances by protobuf name.
 *
 * <p>Creating message with {@code DynamicMessage} is significantly slower than instantiating
 * messages directly as it uses Java reflection.
 *
 * @deprecated Do not use. CEL-Java users should leverage the Fluent APIs instead. See {@code
 *     CelRuntimeFactory}.
 */
@Immutable
@Deprecated
public final class DynamicMessageFactory implements MessageFactory {
  private final ProtoMessageFactory protoMessageFactory;

  /**
   * Create a {@link RuntimeTypeProvider} which can access only the types listed in the input {@code
   * descriptors} using the {@code CelOptions.LEGACY} settings.
   *
   * @deprecated Use CEL Fluent APIs instead. Directly instantiating DynamicMessageFactory's
   *     RuntimeTypeProvider is no longer needed.
   */
  @Deprecated
  public static RuntimeTypeProvider typeProvider(Collection<Descriptor> descriptors) {
    return new DescriptorMessageProvider(
        typeFactory(descriptors).toProtoMessageFactory(), CelOptions.LEGACY);
  }

  /**
   * Create a {@code MessageFactory} which can produce any protobuf type in the generated descriptor
   * pool or in the input {@code descriptors}.
   *
   * @deprecated Use CEL Fluent APIs instead. Directly instantiating DynamicMessageFactory is no
   *     longer needed.
   */
  @Deprecated
  public static MessageFactory typeFactory(Collection<Descriptor> descriptors) {
    CelDescriptors celDescriptors =
        CelDescriptorUtil.getAllDescriptorsFromFileDescriptor(
            CelDescriptorUtil.getFileDescriptorsForDescriptors(descriptors));

    return new DynamicMessageFactory(DefaultDescriptorPool.create(celDescriptors));
  }

  @Override
  public ProtoMessageFactory toProtoMessageFactory() {
    return protoMessageFactory;
  }

  private DynamicMessageFactory(CelDescriptorPool celDescriptorPool) {
    protoMessageFactory = DefaultMessageFactory.create(celDescriptorPool);
  }

  @Override
  public Message.@Nullable Builder newBuilder(String messageName) {
    return protoMessageFactory.newBuilder(messageName).orElse(null);
  }
}
