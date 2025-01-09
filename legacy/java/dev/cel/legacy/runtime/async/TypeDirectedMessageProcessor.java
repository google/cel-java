package dev.cel.legacy.runtime.async;

import static dev.cel.legacy.runtime.async.Canonicalization.asMessage;
import static dev.cel.legacy.runtime.async.Canonicalization.fieldHasWrapperType;
import static dev.cel.legacy.runtime.async.Canonicalization.fieldValueCanonicalizer;

import dev.cel.expr.Type;
import com.google.auto.value.AutoValue;
import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.ExtensionRegistry.ExtensionInfo;
import com.google.protobuf.Message;
import com.google.protobuf.MessageOrBuilder;
import com.google.protobuf.NullValue;
import dev.cel.common.CelOptions;
import dev.cel.legacy.runtime.async.Canonicalization.Canonicalizer;
import dev.cel.runtime.InterpreterException;
import dev.cel.runtime.Metadata;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * An implementation of {@link MessageProcessor} that performs as much work as possible during the
 * static phase by taking advantage of available proto descriptor information as well as CEL type
 * information during the first phase (i.e., during the construction of {@link MessageCreator},
 * {@link FieldGetter}, and {@link FieldTester} objects).
 */
public final class TypeDirectedMessageProcessor implements MessageProcessor {

  /**
   * Information about a message consisting of the message {@link Descriptor} and a supplier of
   * builders for constructing new message instances.
   */
  @AutoValue
  public abstract static class MessageInfo {
    public abstract Descriptor descriptor();

    public abstract Supplier<Message.Builder> messageBuilderSupplier();

    public static MessageInfo of(
        Descriptor descriptor, Supplier<Message.Builder> messageBuilderSupplier) {
      return new AutoValue_TypeDirectedMessageProcessor_MessageInfo(
          descriptor, messageBuilderSupplier);
    }
  }

  private final Function<String, Optional<MessageInfo>> messageInfoLookup;
  private final Function<String, Optional<ExtensionInfo>> extensionLookup;
  private final CelOptions celOptions;

  public TypeDirectedMessageProcessor(
      Function<String, Optional<MessageInfo>> messageInfoLookup,
      Function<String, Optional<ExtensionInfo>> extensionLookup) {
    this(messageInfoLookup, extensionLookup, CelOptions.LEGACY);
  }

  public TypeDirectedMessageProcessor(
      Function<String, Optional<MessageInfo>> messageInfoLookup,
      Function<String, Optional<ExtensionInfo>> extensionLookup,
      CelOptions celOptions) {
    this.messageInfoLookup = messageInfoLookup;
    this.extensionLookup = extensionLookup;
    this.celOptions = celOptions;
  }

  // This lambda implements @Immutable interface 'FieldGetter', but 'Object' is mutable
  @SuppressWarnings("Immutable")
  private FieldGetter getterFromDescriptor(
      FieldDescriptor fd, Optional<Object> maybeDefaultInstance) {
    Canonicalizer canonicalizer = fieldValueCanonicalizer(fd, celOptions);
    if (fieldHasWrapperType(fd)) {
      return value -> {
        MessageOrBuilder message = asMessage(value);
        return message.hasField(fd)
            ? canonicalizer.canonicalize(message.getField(fd))
            : NullValue.NULL_VALUE;
      };
    }
    if (!fd.isRepeated() && maybeDefaultInstance.isPresent()) {
      // If the field is an extension field but is not present,
      // then message.getField(fd) will return an instance of
      // DynamicMessage.  To avoid that, this code explicitly
      // uses the default instance from the registry instead.
      Object defaultInstance = maybeDefaultInstance.get();
      return value -> {
        MessageOrBuilder message = asMessage(value);
        return message.hasField(fd)
            ? canonicalizer.canonicalize(message.getField(fd))
            : defaultInstance;
      };
    }
    return value -> canonicalizer.canonicalize(asMessage(value).getField(fd));
  }

  @Override
  public FieldGetter makeFieldGetter(
      Metadata metadata, long exprId, String messageName, String fieldName)
      throws InterpreterException {
    Descriptor descriptor = getDescriptor(metadata, exprId, messageName);
    FieldDescriptor fd = getFieldDescriptor(metadata, exprId, descriptor, fieldName);
    if (fd.getType() == FieldDescriptor.Type.MESSAGE) {
      // Check to see whether the message descriptor for the field's type uses
      // the "canonical" descriptor for that type (as obtained by messageInfoLookup).
      Descriptor fieldValueDescriptor = fd.getMessageType();
      MessageInfo fieldMessageInfo =
          getMessageInfo(metadata, exprId, fieldValueDescriptor.getFullName());
      Descriptor canonicalDescriptor = fieldMessageInfo.descriptor();
      if (fieldValueDescriptor != canonicalDescriptor) { // pointer inequality!
        // The descriptor is not canonical, so use an explicit presence test
        // and use the canonical default instance.  Otherwise the default instance
        // would use the wrong (non-canonical) descriptor and cause problems down
        // the road.
        return getterFromDescriptor(
            fd, Optional.of(fieldMessageInfo.messageBuilderSupplier().get().build()));
      }
    }
    return getterFromDescriptor(fd, Optional.empty());
  }

  @Override
  public FieldTester makeFieldTester(
      Metadata metadata, long exprId, String messageName, String fieldName)
      throws InterpreterException {
    Descriptor descriptor = getDescriptor(metadata, exprId, messageName);
    FieldDescriptor fd = getFieldDescriptor(metadata, exprId, descriptor, fieldName);
    return fd.isRepeated()
        ? value -> asMessage(value).getRepeatedFieldCount(fd) > 0
        : value -> asMessage(value).hasField(fd);
  }

  @Override
  public FieldAssigner makeFieldAssigner(
      Metadata metadata, long exprId, String messageName, String fieldName, Type fieldType)
      throws InterpreterException {
    Descriptor descriptor = getDescriptor(metadata, exprId, messageName);
    FieldDescriptor fd = getFieldDescriptor(metadata, exprId, descriptor, fieldName);
    return ProtoFieldAssignment.fieldValueAssigner(metadata, exprId, fd, fieldType);
  }

  // This method reference implements @Immutable interface MessageBuilderCreator, but the
  // declaration of type 'java.util.function.Supplier<com.google.protobuf.Message.Builder>' is not
  // annotated with @com.google.errorprone.annotations.Immutable
  @SuppressWarnings("Immutable")
  @Override
  public MessageBuilderCreator makeMessageBuilderCreator(
      Metadata metadata, long exprId, String messageName) throws InterpreterException {
    Supplier<Message.Builder> builderSupplier =
        getMessageInfo(metadata, exprId, messageName).messageBuilderSupplier();
    return builderSupplier::get;
  }

  @Override
  public FieldClearer makeFieldClearer(
      Metadata metadata, long exprId, String messageName, String fieldName)
      throws InterpreterException {
    Descriptor descriptor = getDescriptor(metadata, exprId, messageName);
    FieldDescriptor fd = getFieldDescriptor(metadata, exprId, descriptor, fieldName);
    return builder -> builder.clearField(fd);
  }

  @Override
  public Object dynamicGetField(
      Metadata metadata, long exprId, Object messageObject, String fieldName)
      throws InterpreterException {
    MessageOrBuilder message = expectMessage(metadata, exprId, messageObject);
    Descriptor descriptor = message.getDescriptorForType();
    FieldDescriptor fieldDescriptor = getFieldDescriptor(metadata, exprId, descriptor, fieldName);
    if (fieldHasWrapperType(fieldDescriptor) && !message.hasField(fieldDescriptor)) {
      return NullValue.NULL_VALUE;
    }
    Object value = message.getField(fieldDescriptor);
    return fieldValueCanonicalizer(fieldDescriptor, celOptions).canonicalize(value);
  }

  @Override
  public boolean dynamicHasField(
      Metadata metadata, long exprId, Object messageObject, String fieldName)
      throws InterpreterException {
    MessageOrBuilder message = expectMessage(metadata, exprId, messageObject);
    Descriptor descriptor = message.getDescriptorForType();
    FieldDescriptor fieldDescriptor = getFieldDescriptor(metadata, exprId, descriptor, fieldName);
    return message.hasField(fieldDescriptor);
  }

  @Override
  public FieldGetter makeExtensionGetter(Metadata metadata, long exprId, String extensionName)
      throws InterpreterException {
    ExtensionInfo extensionInfo = getExtensionInfo(metadata, exprId, extensionName);
    if (extensionInfo.defaultInstance != null) {
      Descriptor fieldValueDescriptor = extensionInfo.defaultInstance.getDescriptorForType();
      MessageInfo fieldMessageInfo =
          getMessageInfo(metadata, exprId, fieldValueDescriptor.getFullName());
      Descriptor canonicalDescriptor = fieldMessageInfo.descriptor();
      // If the default instance provided by the extension info does not use the
      // "canonical" descriptor (as obtained by messageInfoLookup), then generate
      // a new canonical default instance instead.
      if (fieldValueDescriptor != canonicalDescriptor) {
        return getterFromDescriptor(
            extensionInfo.descriptor,
            Optional.of(fieldMessageInfo.messageBuilderSupplier().get().build()));
      } else {
        return getterFromDescriptor(
            extensionInfo.descriptor, Optional.of(extensionInfo.defaultInstance));
      }
    }
    return getterFromDescriptor(extensionInfo.descriptor, Optional.empty());
  }

  @Override
  public FieldTester makeExtensionTester(Metadata metadata, long exprId, String extensionName)
      throws InterpreterException {
    FieldDescriptor fd = getExtensionInfo(metadata, exprId, extensionName).descriptor;
    return fd.isRepeated()
        ? value -> asMessage(value).getRepeatedFieldCount(fd) > 0
        : value -> asMessage(value).hasField(fd);
  }

  @Override
  public FieldAssigner makeExtensionAssigner(
      Metadata metadata, long exprId, String extensionName, Type extensionType)
      throws InterpreterException {
    FieldDescriptor fd = getExtensionInfo(metadata, exprId, extensionName).descriptor;
    return ProtoFieldAssignment.fieldValueAssigner(metadata, exprId, fd, extensionType);
  }

  @Override
  public FieldClearer makeExtensionClearer(Metadata metadata, long exprId, String extensionName)
      throws InterpreterException {
    FieldDescriptor fd = getExtensionInfo(metadata, exprId, extensionName).descriptor;
    return builder -> builder.clearField(fd);
  }

  private MessageInfo getMessageInfo(Metadata metadata, long id, String messageName)
      throws InterpreterException {
    return messageInfoLookup
        .apply(messageName)
        .orElseThrow(
            () ->
                new InterpreterException.Builder("cannot resolve '%s' as a message", messageName)
                    .setLocation(metadata, id)
                    .build());
  }

  private Descriptor getDescriptor(Metadata metadata, long id, String messageName)
      throws InterpreterException {
    return getMessageInfo(metadata, id, messageName).descriptor();
  }

  private static MessageOrBuilder expectMessage(
      Metadata metadata, long exprId, Object messageObject) throws InterpreterException {
    if (messageObject instanceof MessageOrBuilder) {
      return (MessageOrBuilder) messageObject;
    }
    throw new InterpreterException.Builder(
            "expected an instance of 'com.google.protobuf.MessageOrBuilder' " + "but found '%s'",
            messageObject.getClass().getName())
        .setLocation(metadata, exprId)
        .build();
  }

  private static FieldDescriptor getFieldDescriptor(
      Metadata metadata, long exprId, Descriptor descriptor, String fieldName)
      throws InterpreterException {
    FieldDescriptor fieldDescriptor = descriptor.findFieldByName(fieldName);
    if (fieldDescriptor == null) {
      throw new InterpreterException.Builder(
              "field '%s' is not declared in message '%s'", fieldName, descriptor.getName())
          .setLocation(metadata, exprId)
          .build();
    }
    return fieldDescriptor;
  }

  private ExtensionInfo getExtensionInfo(Metadata metadata, long exprId, String extensionName)
      throws InterpreterException {
    return extensionLookup
        .apply(extensionName)
        .orElseThrow(
            () ->
                new InterpreterException.Builder(
                        "cannot resolve '%s' as message extension", extensionName)
                    .setLocation(metadata, exprId)
                    .build());
  }
}
