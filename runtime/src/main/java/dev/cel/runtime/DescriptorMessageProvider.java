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
import dev.cel.common.internal.ProtoMessageFactory;
import dev.cel.common.types.CelType;
import dev.cel.common.types.CelTypes;
import java.util.Map;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

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
  private final ProtoMessageFactory protoMessageFactory;
  private final TypeResolver typeResolver;

  @SuppressWarnings("Immutable")
  private final ProtoAdapter protoAdapter;

  /**
   * Creates a new message provider with the given message factory.
   *
   * @deprecated Migrate to the CEL-Java fluent APIs. See {@code CelRuntimeFactory}.
   */
  @Deprecated
  public DescriptorMessageProvider(MessageFactory messageFactory) {
    this(messageFactory.toProtoMessageFactory(), CelOptions.LEGACY);
  }

  /**
   * Creates a new message provider with the given message factory and a set of customized {@code
   * features}.
   *
   * @deprecated Migrate to the CEL-Java fluent APIs. See {@code CelRuntimeFactory}.
   */
  @Deprecated
  public DescriptorMessageProvider(
      MessageFactory messageFactory, ImmutableSet<ExprFeatures> features) {
    this(messageFactory.toProtoMessageFactory(), CelOptions.fromExprFeatures(features));
  }

  /**
   * Create a new message provider with a given message factory and custom descriptor set to use
   * when adapting from proto to CEL and vice versa.
   */
  public DescriptorMessageProvider(ProtoMessageFactory protoMessageFactory, CelOptions celOptions) {
    this.typeResolver = StandardTypeResolver.getInstance(celOptions);
    this.protoMessageFactory = protoMessageFactory;
    this.protoAdapter =
        new ProtoAdapter(
            DynamicProto.create(protoMessageFactory), celOptions.enableUnsignedLongs());
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
    Message.Builder builder =
        protoMessageFactory
            .newBuilder(messageName)
            .orElseThrow(
                () ->
                    new CelRuntimeException(
                        new IllegalArgumentException(
                            String.format("cannot resolve '%s' as a message", messageName)),
                        CelErrorCode.ATTRIBUTE_NOT_FOUND));

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
  @SuppressWarnings("unchecked")
  public Object selectField(Object message, String fieldName) {
    boolean isOptionalMessage = false;
    if (message instanceof Optional) {
      isOptionalMessage = true;
      Optional<Object> optionalMessage = (Optional<Object>) message;
      if (!optionalMessage.isPresent()) {
        return Optional.empty();
      }

      message = optionalMessage.get();
    }

    if (message instanceof Map) {
      Map<?, ?> map = (Map<?, ?>) message;
      if (map.containsKey(fieldName)) {
        Object mapValue = map.get(fieldName);
        return isOptionalMessage ? Optional.of(mapValue) : mapValue;
      }

      if (isOptionalMessage) {
        return Optional.empty();
      } else {
        throw new CelRuntimeException(
            new IllegalArgumentException(
                String.format("key '%s' is not present in map.", fieldName)),
            CelErrorCode.ATTRIBUTE_NOT_FOUND);
      }
    }

    MessageOrBuilder typedMessage = assertFullProtoMessage(message, fieldName);
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
    if (message instanceof Optional<?>) {
      Optional<?> optionalMessage = (Optional<?>) message;
      if (!optionalMessage.isPresent()) {
        return false;
      }
      message = optionalMessage.get();
    }

    if (message instanceof Map) {
      Map<?, ?> map = (Map<?, ?>) message;
      return map.containsKey(fieldName);
    }

    MessageOrBuilder typedMessage = assertFullProtoMessage(message, fieldName);
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
          protoMessageFactory.getDescriptorPool().findExtensionDescriptor(descriptor, fieldName);
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

  private static MessageOrBuilder assertFullProtoMessage(Object candidate, String fieldName) {
    if (!(candidate instanceof MessageOrBuilder)) {
      // This can happen when the field selection is done on dyn, and it is not a message.
      throw new CelRuntimeException(
          new IllegalArgumentException(
              String.format(
                  "Error resolving field '%s'. Field selections must be performed on messages or"
                      + " maps.",
                  fieldName)),
          CelErrorCode.ATTRIBUTE_NOT_FOUND);
    }
    return (MessageOrBuilder) candidate;
  }

  private static boolean isWrapperType(FieldDescriptor field) {
    if (field.getType() != FieldDescriptor.Type.MESSAGE) {
      return false;
    }
    String fieldTypeName = field.getMessageType().getFullName();

    return CelTypes.isWrapperType(fieldTypeName);
  }
}
