package dev.cel.legacy.runtime.async;

import dev.cel.expr.Type;
import com.google.common.collect.ImmutableMap;
import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.Message;
import dev.cel.runtime.InterpreterException;
import dev.cel.runtime.MessageProvider;
import dev.cel.runtime.Metadata;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/** Adapts a {@link MessageProvider} to act as a {@link MessageProcessor}, using delegation. */
final class MessageProcessorAdapter implements MessageProcessor {

  private final Function<String, Optional<Descriptor>> messageLookup;
  private final MessageProvider messageProvider;

  MessageProcessorAdapter(
      Function<String, Optional<Descriptor>> messageLookup, MessageProvider messageProvider) {
    this.messageLookup = messageLookup;
    this.messageProvider = messageProvider;
  }

  // Overrides the default implementation since doing so is more efficient here.
  // (The default implementation is based on FieldAssigners, and those are not efficient
  // when adapting a MessageProvider.)
  // This lambda implements @Immutable interface 'MessageCreator', but 'List' is mutable
  @SuppressWarnings("Immutable")
  @Override
  public MessageCreator makeMessageCreator(
      Metadata metadata,
      long exprId,
      String messageName,
      List<String> fieldNames,
      List<Type> fieldTypes) // ignored in this implementation; adaptation errors occur at runtime
      throws InterpreterException {
    Descriptor messageDescriptor = verifyMessageType(metadata, exprId, messageName);
    for (String fieldName : fieldNames) {
      verifyField(metadata, exprId, messageDescriptor, fieldName);
    }
    return fieldValues -> {
      try {
        return messageProvider.createMessage(messageName, makeValueMap(fieldNames, fieldValues));
      } catch (RuntimeException e) {
        throw new InterpreterException.Builder(e, e.getMessage())
            .setLocation(metadata, exprId)
            .build();
      }
    };
  }

  // This lambda implements @Immutable interface 'FieldGetter', but 'MessageProcessorAdapter' has
  // field 'messageProvider' of type
  // 'com.google.api.tools.contract.runtime.interpreter.MessageProvider', the declaration of type
  // 'com.google.api.tools.contract.runtime.interpreter.MessageProvider' is not annotated with
  // @com.google.errorprone.annotations.Immutable
  @SuppressWarnings("Immutable")
  @Override
  public FieldGetter makeFieldGetter(
      Metadata metadata, long exprId, String messageName, String fieldName)
      throws InterpreterException {
    Descriptor messageDescriptor = verifyMessageType(metadata, exprId, messageName);
    verifyField(metadata, exprId, messageDescriptor, fieldName);
    return message -> {
      try {
        return messageProvider.selectField(message, fieldName);
      } catch (IllegalArgumentException e) {
        throw new IllegalStateException("[internal] field selection unexpectedly failed", e);
      }
    };
  }

  // This lambda implements @Immutable interface 'FieldTester', but 'MessageProcessorAdapter' has
  // field 'messageProvider' of type
  // 'com.google.api.tools.contract.runtime.interpreter.MessageProvider', the declaration of type
  // 'com.google.api.tools.contract.runtime.interpreter.MessageProvider' is not annotated with
  // @com.google.errorprone.annotations.Immutable
  @SuppressWarnings("Immutable")
  @Override
  public FieldTester makeFieldTester(
      Metadata metadata, long exprId, String messageName, String fieldName)
      throws InterpreterException {
    Descriptor messageDescriptor = verifyMessageType(metadata, exprId, messageName);
    verifyField(metadata, exprId, messageDescriptor, fieldName);
    return message -> {
      try {
        return messageProvider.hasField(message, fieldName).equals(true);
      } catch (IllegalArgumentException e) {
        throw new IllegalStateException("[internal] field presence test unexpectedly failed", e);
      }
    };
  }

  // This lambda implements @Immutable interface 'FieldAssigner', but 'MessageProcessorAdapter' has
  // field 'messageProvider' of type
  // 'com.google.api.tools.contract.runtime.interpreter.MessageProvider', the declaration of type
  // 'com.google.api.tools.contract.runtime.interpreter.MessageProvider' is not annotated with
  // @com.google.errorprone.annotations.Immutable
  @SuppressWarnings("Immutable")
  @Override
  public FieldAssigner makeFieldAssigner(
      Metadata metadata, long exprId, String messageName, String fieldName, Type fieldType)
      throws InterpreterException {
    Descriptor messageDescriptor = verifyMessageType(metadata, exprId, messageName);
    verifyField(metadata, exprId, messageDescriptor, fieldName);
    FieldDescriptor fd = messageDescriptor.findFieldByName(fieldName);
    return (builder, value) -> {
      try {
        Message singleton =
            (Message) messageProvider.createMessage(messageName, ImmutableMap.of(fieldName, value));
        return builder.clearField(fd).mergeFrom(singleton);
      } catch (IllegalArgumentException e) {
        throw new IllegalStateException("[internal] field assignment unexpectedly failed", e);
      }
    };
  }

  @Override
  public FieldClearer makeFieldClearer(
      Metadata metadata, long exprId, String messageName, String fieldName)
      throws InterpreterException {
    Descriptor messageDescriptor = verifyMessageType(metadata, exprId, messageName);
    verifyField(metadata, exprId, messageDescriptor, fieldName);
    FieldDescriptor fd = messageDescriptor.findFieldByName(fieldName);
    return builder -> builder.clearField(fd);
  }

  @Override
  public MessageBuilderCreator makeMessageBuilderCreator(
      Metadata metadata, long exprId, String messageName) throws InterpreterException {
    try {
      Message emptyProto = (Message) messageProvider.createMessage(messageName, ImmutableMap.of());
      return emptyProto::toBuilder;
    } catch (RuntimeException e) {
      throw new InterpreterException.Builder(e, e.getMessage())
          .setLocation(metadata, exprId)
          .build();
    }
  }

  @Override
  public Object dynamicGetField(
      Metadata metadata, long exprId, Object messageObject, String fieldName)
      throws InterpreterException {
    try {
      return messageProvider.selectField(messageObject, fieldName);
    } catch (IllegalArgumentException e) {
      throw new InterpreterException.Builder(e.getMessage()).setLocation(metadata, exprId).build();
    }
  }

  @Override
  public boolean dynamicHasField(
      Metadata metadata, long exprId, Object messageObject, String fieldName)
      throws InterpreterException {
    try {
      return messageProvider.hasField(messageObject, fieldName).equals(true);
    } catch (IllegalArgumentException e) {
      throw new InterpreterException.Builder(e.getMessage()).setLocation(metadata, exprId).build();
    }
  }

  @Override
  public FieldGetter makeExtensionGetter(Metadata metadata, long exprId, String extensionName)
      throws InterpreterException {
    throw unsupportedProtoExtensions(metadata, exprId);
  }

  @Override
  public FieldTester makeExtensionTester(Metadata metadata, long exprId, String extensionName)
      throws InterpreterException {
    throw unsupportedProtoExtensions(metadata, exprId);
  }

  @Override
  public FieldAssigner makeExtensionAssigner(
      Metadata metadata, long exprId, String extensionName, Type extensionType)
      throws InterpreterException {
    throw unsupportedProtoExtensions(metadata, exprId);
  }

  @Override
  public FieldClearer makeExtensionClearer(Metadata metadata, long exprId, String extensionName)
      throws InterpreterException {
    throw unsupportedProtoExtensions(metadata, exprId);
  }

  private InterpreterException unsupportedProtoExtensions(Metadata metadata, long exprId) {
    return new InterpreterException.Builder("proto extensions not supported")
        .setLocation(metadata, exprId)
        .build();
  }

  private Descriptor verifyMessageType(Metadata metadata, long id, String messageName)
      throws InterpreterException {
    return messageLookup
        .apply(messageName)
        .orElseThrow(
            () ->
                new InterpreterException.Builder("cannot resolve '%s' as a message", messageName)
                    .setLocation(metadata, id)
                    .build());
  }

  private static void verifyField(
      Metadata metadata, long id, Descriptor messageDescriptor, String field)
      throws InterpreterException {
    FieldDescriptor fieldDescriptor = messageDescriptor.findFieldByName(field);
    if (fieldDescriptor == null) {
      throw new InterpreterException.Builder(
              "field '%s' is not declared in message '%s'", field, messageDescriptor.getName())
          .setLocation(metadata, id)
          .build();
    }
  }

  private static ImmutableMap<String, Object> makeValueMap(
      List<String> fieldNames, Iterable<Object> fieldValues) {
    int i = 0;
    ImmutableMap.Builder<String, Object> builder = ImmutableMap.builder();
    for (Object value : fieldValues) {
      builder.put(fieldNames.get(i), value);
      ++i;
    }
    return builder.buildOrThrow();
  }
}
