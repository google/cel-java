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

import com.google.common.collect.ImmutableSet;
import com.google.errorprone.annotations.Immutable;
import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.DynamicMessage;
import com.google.protobuf.Message;
import dev.cel.common.CelDescriptorUtil;
import dev.cel.common.CelOptions;
import dev.cel.common.ExprFeatures;
import dev.cel.common.annotations.Internal;
import dev.cel.common.internal.DynamicProto;
import java.util.Collection;
import org.jspecify.nullness.Nullable;

/**
 * The {@code DynamicMessageFactory} creates {@link DynamicMessage} instances by protobuf name.
 *
 * <p>Creating message with {@code DynamicMessage} is significantly slower than instantiating
 * messages directly as it uses Java reflection.
 *
 * <p>CEL Library Internals. Do Not Use.
 */
@Immutable
@Internal
public final class DynamicMessageFactory implements MessageFactory {

  /**
   * Create a {@link RuntimeTypeProvider} which can access only the types listed in the input {@code
   * descriptors} using the {@code CelOptions.LEGACY} settings.
   */
  public static RuntimeTypeProvider typeProvider(Collection<Descriptor> descriptors) {
    return typeProvider(descriptors, CelOptions.LEGACY);
  }

  /**
   * Create a {@link RuntimeTypeProvider} which can access only the types listed in the input {@code
   * descriptors} using a custom set of {@code features}.
   */
  public static RuntimeTypeProvider typeProvider(
      Collection<Descriptor> descriptors, ImmutableSet<ExprFeatures> features) {
    return typeProvider(descriptors, CelOptions.fromExprFeatures(features));
  }

  /**
   * Create a {@link RuntimeTypeProvider} which can access only the types listed in the input {@code
   * descriptors} and configured set of {@code celOptions}.
   */
  public static RuntimeTypeProvider typeProvider(
      Collection<Descriptor> descriptors, CelOptions celOptions) {
    return new DescriptorMessageProvider(
        typeFactory(descriptors),
        DynamicProto.newBuilder()
            .setDynamicDescriptors(CelDescriptorUtil.descriptorCollectionToMap(descriptors))
            .build(),
        celOptions);
  }

  /**
   * Create a {@code MessageFactory} which can produce any protobuf type in the generated descriptor
   * pool or in the input {@code descriptors}.
   */
  public static MessageFactory typeFactory(Collection<Descriptor> descriptors) {

    return new DynamicMessageFactory(descriptors);
  }

  private final DynamicProto dynamicProto;

  private DynamicMessageFactory(Collection<Descriptor> descriptors) {
    this.dynamicProto =
        DynamicProto.newBuilder()
            .setDynamicDescriptors(CelDescriptorUtil.descriptorCollectionToMap(descriptors))
            .build();
  }

  @Override
  public Message.@Nullable Builder newBuilder(String messageName) {
    return dynamicProto.newMessageBuilder(messageName).orElse(null);
  }
}
