// Copyright 2025 Google LLC
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

import com.google.protobuf.Message;
import com.google.protobuf.MessageLiteOrBuilder;
import com.google.protobuf.MessageOrBuilder;
import dev.cel.common.CelOptions;
import dev.cel.common.annotations.Internal;
import dev.cel.common.internal.DynamicProto;
import dev.cel.common.internal.ProtoAdapter;

/**
 * Helper methods for common CEL related routines that require a full protobuf dependency.
 *
 * <p>CEL Library Internals. Do Not Use.
 */
@Internal
public final class ProtoMessageRuntimeHelpers extends RuntimeHelpers {

  private final ProtoAdapter protoAdapter;

  @Internal
  public static ProtoMessageRuntimeHelpers create(
      DynamicProto dynamicProto, CelOptions celOptions) {
    return new ProtoMessageRuntimeHelpers(
        new ProtoAdapter(dynamicProto, celOptions.enableUnsignedLongs()));
  }

  /**
   * Adapts a {@code protobuf.Message} to a plain old Java object.
   *
   * <p>Well-known protobuf types (wrappers, JSON types) are unwrapped to Java native object
   * representations.
   *
   * <p>If the incoming {@code obj} is of type {@code google.protobuf.Any} the object is unpacked
   * and the proto within is passed to the {@code adaptProtoToValue} method again to ensure the
   * message contained within the Any is properly unwrapped if it is a well-known protobuf type.
   */
  @Override
  Object adaptProtoToValue(MessageLiteOrBuilder obj) {
    if (obj instanceof Message) {
      return protoAdapter.adaptProtoToValue((MessageOrBuilder) obj);
    }
    if (obj instanceof Message.Builder) {
      return protoAdapter.adaptProtoToValue(((Message.Builder) obj).build());
    }
    return obj;
  }

  private ProtoMessageRuntimeHelpers(ProtoAdapter protoAdapter) {
    this.protoAdapter = protoAdapter;
  }
}
