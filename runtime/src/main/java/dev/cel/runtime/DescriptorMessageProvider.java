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

import dev.cel.expr.Type;
import dev.cel.expr.Value;
import com.google.common.collect.ImmutableSet;
import com.google.errorprone.annotations.Immutable;
import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.Message;
import com.google.protobuf.MessageOrBuilder;
import com.google.protobuf.NullValue;
import dev.cel.common.CelErrorCode;
import dev.cel.common.CelOptions;
import dev.cel.common.CelRuntimeException;
import dev.cel.common.ExprFeatures;
import dev.cel.common.annotations.Internal;
import dev.cel.common.internal.DynamicProto;
import dev.cel.common.internal.ProtoAdapter;
import dev.cel.common.types.CelType;
import java.util.Map;
import java.util.Optional;
import org.jspecify.nullness.Nullable;

/**
 * An implementation of {@link RuntimeTypeProvider} which relies on proto descriptors.
 *
 * <p>This can handle all messages providable by the given {@link MessageFactory}. In addition, one
 * can provide message descriptors for messages external to the program which are provided via a
 * {@link DynamicMessageFactory}.
 *
 * <p>CEL Library Internals. Do Not Use.
 */
@Immutable
@Internal
public final class DescriptorMessageProvider implements RuntimeTypeProvider {
  private final MessageFactory messageFactory;
  private final DynamicProto dynamicProto;
  private final TypeResolver typeResolver;

  @SuppressWarnings("Immutable")
  private final ProtoAdapter protoAdapter;

  /** Creates a new message provider with the given message factory. */
  public DescriptorMessageProvider(MessageFactory messageFactory) {
    this(messageFactory, DynamicProto.newBuilder().build(), CelOptions.LEGACY);
  }

  /**
   * Creates a new message provider with the given message factory and a set of customized {@code
   * features}.
   */
  public DescriptorMessageProvider(
      MessageFactory messageFactory, ImmutableSet<ExprFeatures> features) {
    this(messageFactory, DynamicProto.newBuilder().build(), CelOptions.fromExprFeatures(features));
  }

  /**
   * Create a new message provider with a given message factory and custom descriptor set to use
   * when adapting from proto to CEL and vice versa.
   */
  public DescriptorMessageProvider(
      MessageFactory messageFactory, DynamicProto dynamicProto, CelOptions celOptions) {
    this.dynamicProto = dynamicProto;
    // Dedupe the descriptors while indexing by name.
    this.typeResolver = StandardTypeResolver.getInstance(celOptions);
    this.messageFactory = messageFactory;
    this.protoAdapter = new ProtoAdapter(dynamicProto, celOptions.enableUnsignedLongs());
  }

  @Override
  @Nullable
  public Value resolveObjectType(Object obj, @Nullable Value checkedTypeValue) {
    return typeResolver.resolveObjectType(obj, checkedTypeValue);
  }

  /** {@inheritDoc} */
  @Override
  public Value adaptType(CelType type) {
    return typeResolver.adaptType(type);
  }

  @Nullable
  @Override
  @Deprecated
  /** {@inheritDoc} */
  public Value adaptType(@Nullable Type type) {
    return typeResolver.adaptType(type);
  }

  @Nullable
  @Override
  public Object createMessage(String messageName, Map<String, Object> values) {
    Message.Builder builder = messageFactory.newBuilder(messageName);
    if (builder == null) {
      throw new CelRuntimeException(
          new IllegalArgumentException(
              String.format("cannot resolve '%s' as a message", messageName)),
          CelErrorCode.ATTRIBUTE_NOT_FOUND);
    }
    try {
      Descriptor descriptor = builder.getDescriptorForType();
      for (Map.Entry<String, Object> entry : values.entrySet()) {
        FieldDescriptor fieldDescriptor = findField(descriptor, entry.getKey());
        Optional<Object> fieldValue =
            protoAdapter.adaptValueToFieldType(fieldDescriptor, entry.getValue());
        fieldValue.ifPresent(o -> builder.setField(fieldDescriptor, o));
      }
      return protoAdapter.adaptProtoToValue(builder.build());
    } catch (ArrayIndexOutOfBoundsException e) {
      throw new IllegalArgumentException(e.getMessage());
    }
  }

  @Override
  @Nullable
  public Object selectField(Object message, String fieldName) {
    if (message instanceof Map) {
      Map<?, ?> map = (Map<?, ?>) message;
      if (map.containsKey(fieldName)) {
        return map.get(fieldName);
      }
      throw new CelRuntimeException(
          new IllegalArgumentException(String.format("key '%s' is not present in map.", fieldName)),
          CelErrorCode.ATTRIBUTE_NOT_FOUND);
    }
    MessageOrBuilder typedMessage = assertFullProtoMessage(message);
    FieldDescriptor fieldDescriptor = findField(typedMessage.getDescriptorForType(), fieldName);
    // check whether the field is a wrapper type, then test has and return null
    if (isWrapperType(fieldDescriptor) && !typedMessage.hasField(fieldDescriptor)) {
      return NullValue.NULL_VALUE;
    }
    Object value = typedMessage.getField(fieldDescriptor);
    return protoAdapter.adaptFieldToValue(fieldDescriptor, value).orElse(null);
  }

  /** Adapt object to its message value. */
  @Override
  public Object adapt(Object message) {
    if (message instanceof Message) {
      return protoAdapter.adaptProtoToValue((Message) message);
    }
    return message;
  }

  @Override
  public Object hasField(Object message, String fieldName) {
    if (message instanceof Map) {
      Map<?, ?> map = (Map<?, ?>) message;
      return map.containsKey(fieldName);
    }
    MessageOrBuilder typedMessage = assertFullProtoMessage(message);
    FieldDescriptor fieldDescriptor = findField(typedMessage.getDescriptorForType(), fieldName);
    if (fieldDescriptor.isRepeated()) {
      return typedMessage.getRepeatedFieldCount(fieldDescriptor) > 0;
    }
    return typedMessage.hasField(fieldDescriptor);
  }

  private FieldDescriptor findField(Descriptor descriptor, String fieldName) {
    FieldDescriptor fieldDescriptor = descriptor.findFieldByName(fieldName);
    if (fieldDescriptor == null) {
      Optional<FieldDescriptor> maybeFieldDescriptor =
          dynamicProto.maybeGetExtensionDescriptor(descriptor, fieldName);
      if (maybeFieldDescriptor.isPresent()) {
        fieldDescriptor = maybeFieldDescriptor.get();
      }
    }

    if (fieldDescriptor == null) {
      throw new IllegalArgumentException(
          String.format(
              "field '%s' is not declared in message '%s'", fieldName, descriptor.getFullName()));
    }
    return fieldDescriptor;
  }

  private static MessageOrBuilder assertFullProtoMessage(Object candidate) {
    if (!(candidate instanceof MessageOrBuilder)) {
      // This is an internal error. It should not happen for type checked expressions.
      throw new CelRuntimeException(
          new IllegalStateException(
              String.format(
                  "[internal] expected an instance of 'com.google.protobuf.MessageOrBuilder' "
                      + "but found '%s'",
                  candidate.getClass().getName())),
          CelErrorCode.INTERNAL_ERROR);
    }
    return (MessageOrBuilder) candidate;
  }

  private static boolean isWrapperType(FieldDescriptor field) {
    if (field.getType() != FieldDescriptor.Type.MESSAGE) {
      return false;
    }
    String fieldTypeName = field.getMessageType().getFullName();
    switch (fieldTypeName) {
      case "google.protobuf.BoolValue":
      case "google.protobuf.BytesValue":
      case "google.protobuf.DoubleValue":
      case "google.protobuf.FloatValue":
      case "google.protobuf.Int32Value":
      case "google.protobuf.Int64Value":
      case "google.protobuf.StringValue":
      case "google.protobuf.UInt32Value":
      case "google.protobuf.UInt64Value":
        return true;
      default:
        return false;
    }
  }
}
