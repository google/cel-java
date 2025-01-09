package dev.cel.legacy.runtime.async;

import dev.cel.expr.Type;
import com.google.common.base.Preconditions;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.errorprone.annotations.Immutable;
import com.google.protobuf.Message;
import dev.cel.runtime.InterpreterException;
import dev.cel.runtime.Metadata;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents the mechanism for creating proto message objects and for accessing proto message
 * fields.
 */
public interface MessageProcessor {

  /** Represents the operation of creating a proto message of a specific known type. */
  @Immutable
  @FunctionalInterface
  interface MessageCreator {
    /**
     * Creates the message object using the given field values. Field values correspond by position
     * to the field names that were provided as {@code fieldNames} when the {@code MessageCreator}
     * was made using the {@code makeMessageCreator} method below.
     *
     * <p>Field values must be in canonical CEL runtime value representation.
     *
     * <p>Throws the exception if runtime field values cannot be adapted to their corresponding
     * field types. An implementation can try to perform these checks as much as possible during
     * {@code MessageCreator} construction, but if there are field values of dynamic type it is
     * still possible that the exception is thrown at message creation time.
     */
    Object createMessage(List<Object> fieldValues) throws InterpreterException;
  }

  /**
   * Represents the operation of getting the value of a specific field in a proto message of a
   * specific known type.
   */
  @Immutable
  @FunctionalInterface
  interface FieldGetter {
    /**
     * Retrieves the field's value or the default value if the field is not set. The returned value
     * will be in canonical CEL runtime representation.
     */
    Object getField(Object message);
  }

  /**
   * Represents the operation of checking for the presence of a specific field in a proto message of
   * a specific known type.
   */
  @Immutable
  @FunctionalInterface
  interface FieldTester {
    /** Checks for existence of the field. */
    boolean hasField(Object message);
  }

  /**
   * Represents the assignment of the given value (after a suitable representation change) to a
   * specific field of the given proto message builder. The builder must be a builder for the type
   * of message that the field in question is defined within.
   */
  @Immutable
  @FunctionalInterface
  interface FieldAssigner {
    /** Assigns the suitably converted value to the field. */
    @CanIgnoreReturnValue
    Message.Builder assign(Message.Builder builder, Object value);
  }

  /** Represents the clearing of a specific field in the given proto message builder. */
  @Immutable
  @FunctionalInterface
  interface FieldClearer {
    /** Clears the field. */
    @CanIgnoreReturnValue
    Message.Builder clear(Message.Builder builder);
  }

  /** Represents the creation of a new builder for a specific proto message type. */
  @Immutable
  @FunctionalInterface
  interface MessageBuilderCreator {
    /** Creates a new empty builder. */
    Message.Builder builder();
  }

  /**
   * Returns a {@link MessageCreator} for the named message type. When invoking the {@code
   * createMessage} method on the result, values for each of the named fields must be provided.
   * Field values correspond to field names by position.
   *
   * <p>Throws an exception if the message type is unknown or if any of the named fields is not
   * defined in it.
   */
  // This lambda implements @Immutable interface 'MessageCreator', but 'List' is mutable
  @SuppressWarnings("Immutable")
  default MessageCreator makeMessageCreator(
      Metadata metadata,
      long exprId,
      String messageName,
      List<String> fieldNames,
      List<Type> fieldTypes)
      throws InterpreterException {
    final int numFields = fieldNames.size();
    Preconditions.checkArgument(numFields == fieldTypes.size());
    MessageBuilderCreator builderCreator = makeMessageBuilderCreator(metadata, exprId, messageName);
    List<FieldAssigner> assigners = new ArrayList<>();
    for (int i = 0; i < numFields; ++i) {
      assigners.add(
          makeFieldAssigner(metadata, exprId, messageName, fieldNames.get(i), fieldTypes.get(i)));
    }
    return fieldValues -> {
      Preconditions.checkArgument(numFields == fieldValues.size());
      Message.Builder messageBuilder = builderCreator.builder();
      for (int i = 0; i < numFields; ++i) {
        try {
          assigners.get(i).assign(messageBuilder, fieldValues.get(i));
        } catch (RuntimeException e) {
          throw new InterpreterException.Builder(e, e.getMessage())
              .setLocation(metadata, exprId)
              .build();
        }
      }
      return messageBuilder.build();
    };
  }

  /**
   * Returns a {@link FieldGetter} for the named field in the named message type.
   *
   * <p>Throws an exception if the message type is unknown or if the field is not defined in that
   * message type.
   */
  FieldGetter makeFieldGetter(Metadata metadata, long exprId, String messageName, String fieldName)
      throws InterpreterException;

  /**
   * Returns a {@link FieldTester} for the named field in the named message type.
   *
   * <p>Throws an exception if the message type is unknown, if the field is not defined in that
   * message type, or if presence checks on that field are not supported.
   */
  FieldTester makeFieldTester(Metadata metadata, long exprId, String messageName, String fieldName)
      throws InterpreterException;

  /**
   * Returns a {@link FieldAssigner} for the named field in the named message type.
   *
   * <p>Throws an exception if the message type is unknown or if the field is not defined in that
   * message type.
   *
   * <p>May use the CEL type information for the value to be assigned by the returned assigner in
   * order to correctly implement the conversion from CEL runtime representation to the
   * representation required by the proto field.
   */
  FieldAssigner makeFieldAssigner(
      Metadata metadata, long exprId, String messageName, String fieldName, Type fieldType)
      throws InterpreterException;

  /**
   * Returns a {@link FieldClearer} for the named field in the named message type.
   *
   * <p>Throws an exception if the message type is unknown or if the field is not defined in that
   * message type.
   */
  FieldClearer makeFieldClearer(
      Metadata metadata, long exprId, String messageName, String fieldName)
      throws InterpreterException;

  /**
   * Returns a {@link MessageBuilderCreator} for the named field in the named message type.
   *
   * <p>Throws an exception if the message type is unknown.
   */
  MessageBuilderCreator makeMessageBuilderCreator(
      Metadata metadata, long exprId, String messageName) throws InterpreterException;

  /**
   * Finds the field and retrieves its value using only the runtime type of the given message
   * object. If the field is not set, the type-specific default value is returned according to
   * normal proto message semantics.
   *
   * <p>Throws an exception if the object is not a message object or if the field is not defined in
   * it.
   */
  Object dynamicGetField(Metadata metadata, long exprId, Object messageObject, String fieldName)
      throws InterpreterException;

  /**
   * Checks for the existence of the field using only the runtime type of the given message object.
   *
   * <p>Throws an exception if the object is not a message object or if the field is not defined in
   * it.
   */
  boolean dynamicHasField(Metadata metadata, long exprId, Object messageObject, String fieldName)
      throws InterpreterException;

  /**
   * Returns a {@link FieldGetter} for the named extension.
   *
   * <p>Throws an exception if the extension is unknown.
   */
  FieldGetter makeExtensionGetter(Metadata metadata, long exprId, String extensionName)
      throws InterpreterException;

  /**
   * Returns a {@link FieldTester} for the named extension.
   *
   * <p>Throws an exception if the extesion is unknown, or if presence checks on it are not
   * supported.
   */
  FieldTester makeExtensionTester(Metadata metadata, long exprId, String extensionName)
      throws InterpreterException;

  /**
   * Returns a {@link FieldAssigner} for the named extension.
   *
   * <p>Throws an exception if the extension is unknown.
   *
   * <p>May use the CEL type information for the value to be assigned by the returned assigner in
   * order to correctly implement the conversion from CEL runtime representation to the
   * representation required by the proto field.
   */
  FieldAssigner makeExtensionAssigner(
      Metadata metadata, long exprId, String extensionName, Type extensionType)
      throws InterpreterException;

  /**
   * Returns a {@link FieldClearer} for the named extension.
   *
   * <p>Throws an exception if the extension is unknown.
   */
  FieldClearer makeExtensionClearer(Metadata metadata, long exprId, String extensionName)
      throws InterpreterException;
}
