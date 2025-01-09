package dev.cel.legacy.runtime.async;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.primitives.UnsignedInts;
import com.google.common.primitives.UnsignedLong;
import com.google.errorprone.annotations.Immutable;
import com.google.protobuf.Any;
import com.google.protobuf.BoolValue;
import com.google.protobuf.BytesValue;
import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.EnumValueDescriptor;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.DoubleValue;
import com.google.protobuf.DynamicMessage;
import com.google.protobuf.ExtensionRegistry;
import com.google.protobuf.FloatValue;
import com.google.protobuf.Int32Value;
import com.google.protobuf.Int64Value;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.ListValue;
import com.google.protobuf.MapEntry;
import com.google.protobuf.Message;
import com.google.protobuf.MessageFactories;
import com.google.protobuf.MessageOrBuilder;
import com.google.protobuf.Parser;
import com.google.protobuf.StringValue;
import com.google.protobuf.Struct;
import com.google.protobuf.UInt32Value;
import com.google.protobuf.UInt64Value;
import com.google.protobuf.Value;
import com.google.protobuf.contrib.AnyUtil;
import com.google.protobuf.contrib.descriptor.pool.GeneratedDescriptorPool;
import dev.cel.common.CelOptions;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Descriptor-directed value canonicalization.
 *
 * <p>This class provides tools for creating {@link Canonicalizer} instances. A {@code
 * Canonicalizer} is the mechanism that translates values fetched from proto fields to canonical CEL
 * runtime values.
 */
public final class Canonicalization {

  /**
   * Represents the transformations from various proto values to their corresponding canonical CEL
   * runtime representations.
   */
  @Immutable
  @FunctionalInterface
  public interface Canonicalizer {
    Object canonicalize(Object value);
  }

  /**
   * Returns the {@link Canonicalizer} for the field described by the give {@link FieldDescriptor}.
   */
  public static Canonicalizer fieldValueCanonicalizer(FieldDescriptor fd, CelOptions celOptions) {
    if (fd.isMapField()) {
      return mapCanonicalizer(fd, celOptions);
    }
    if (fd.isRepeated()) {
      return listCanonicalizer(fd, celOptions);
    }
    return singleFieldCanonicalizer(fd, celOptions);
  }

  /** Returns the canonicalizer for a list field. */
  private static Canonicalizer listCanonicalizer(FieldDescriptor fd, CelOptions celOptions) {
    Canonicalizer elementCanonicalizer = singleFieldCanonicalizer(fd, celOptions);
    if (elementCanonicalizer == IDENTITY) {
      return IDENTITY;
    }
    return l ->
        Lists.transform((List<?>) asInstanceOf(List.class, l), elementCanonicalizer::canonicalize);
  }

  /**
   * Returns the canonicalizer for a map field. It constructs an actual instance of {@link Map} from
   * a list of map entries. The argument descriptor describes a map entry. Key and value descriptors
   * can be obtained from it.
   */
  @SuppressWarnings("unchecked")
  private static Canonicalizer mapCanonicalizer(FieldDescriptor fd, CelOptions celOptions) {
    Descriptor entryDescriptor = fd.getMessageType();
    FieldDescriptor keyDescriptor = entryDescriptor.findFieldByNumber(1);
    FieldDescriptor valueDescriptor = entryDescriptor.findFieldByNumber(2);
    Canonicalizer keyCanonicalizer = singleFieldCanonicalizer(keyDescriptor, celOptions);
    Canonicalizer valueCanonicalizer = singleFieldCanonicalizer(valueDescriptor, celOptions);
    // Map fields aren't fetched as native Java maps but as lists of map entries, so they cannot
    // simply be transformed but must be built.  In any case, even if they were maps, since there is
    // no off-the-shelf transform that also translates keys, it would still be necessary to
    // rebuild the map.  (A general transform that translates keys would have to deal with
    // key collisions, which is probably why no general mechanism exists.)
    return entries -> {
      Map<Object, Object> map = new HashMap<>();
      for (MapEntry<?, ?> entry : (List<MapEntry<?, ?>>) entries) {
        map.put(
            keyCanonicalizer.canonicalize(entry.getKey()),
            valueCanonicalizer.canonicalize(entry.getValue()));
      }
      return map;
    };
  }

  /**
   * Canonicalizer for individual values of non-map fields.
   *
   * <p>Returns {@code IDENTITY} if the canonicalizer is the identity function. (Using this specific
   * instance rather than any old identity function makes it possible for callers to detect the
   * situation, as is done in {@code fieldValueCanonicalizer()} above).
   *
   * <p>For repeated fields the fetched value is a {@link List} and the constructed {@link
   * Canonicalizer} must be applied to each element.
   *
   * <p>For map fields two {@link Canonicalizer} instances must be created, one for the key and one
   * for the value of a map entry, and they must subsequently be applied entry-by-entry to the
   * entire map.
   */
  private static Canonicalizer singleFieldCanonicalizer(FieldDescriptor fd, CelOptions celOptions) {
    switch (fd.getType()) {
      case SFIXED32:
      case SINT32:
      case INT32:
        return value -> ((Number) value).longValue();
      case FIXED32:
      case UINT32:
        return value -> UnsignedInts.toLong(((Number) value).intValue());
      case ENUM:
        return value -> (long) ((EnumValueDescriptor) value).getNumber();
      case FLOAT:
        return value -> ((Number) value).doubleValue();
      case MESSAGE:
        return protoCanonicalizer(fd.getMessageType(), celOptions);
      default:
        return IDENTITY;
    }
  }

  /**
   * Returns the {@link Canonicalizer} for arbitrary proto messages. Most messages represent
   * themselves, so canonicalization is the identity. This situation is indicated by returning the
   * special {@code IDENTITY} canonicalizer.
   *
   * <p>Certain well-known proto types are treated specially: those representing JSON values and
   * those representing wrapped values.
   *
   * <p>JSON values are recursively converted into Java {@link List} and {@link Map} types.
   * Primitive JSON values are unwrapped into their canonical CEL (i.e., Java) equivalents.
   *
   * <p>Wrapped values are simply unwrapped. Notice that all floating point values are represented
   * using {@link Double} and all fixed point values are represented using {@link Long}.
   */
  private static Canonicalizer protoCanonicalizer(Descriptor d, CelOptions celOptions) {
    Canonicalizer deDynamicalizer = deDynamicalizerFor(d);
    switch (d.getFullName()) {
      case "google.protobuf.Any":
        return value ->
            canonicalizeAny(
                asInstanceOf(Any.class, deDynamicalizer.canonicalize(value)), celOptions);
      case "google.protobuf.Value":
        return value ->
            canonicalizeJsonValue(asInstanceOf(Value.class, deDynamicalizer.canonicalize(value)));
      case "google.protobuf.ListValue":
        return value ->
            canonicalizeJsonList(
                asInstanceOf(ListValue.class, deDynamicalizer.canonicalize(value)));
      case "google.protobuf.Struct":
        return value ->
            canonicalizeJsonStruct(asInstanceOf(Struct.class, deDynamicalizer.canonicalize(value)));
      case "google.protobuf.Int64Value":
        return value ->
            asInstanceOf(Int64Value.class, deDynamicalizer.canonicalize(value)).getValue();
      case "google.protobuf.UInt64Value":
        if (celOptions.enableUnsignedLongs()) {
          return value ->
              UnsignedLong.fromLongBits(
                  asInstanceOf(UInt64Value.class, deDynamicalizer.canonicalize(value)).getValue());
        }
        return value ->
            asInstanceOf(UInt64Value.class, deDynamicalizer.canonicalize(value)).getValue();
      case "google.protobuf.Int32Value":
        return value ->
            (long) asInstanceOf(Int32Value.class, deDynamicalizer.canonicalize(value)).getValue();
      case "google.protobuf.UInt32Value":
        if (celOptions.enableUnsignedLongs()) {
          return value ->
              UnsignedLong.fromLongBits(
                  Integer.toUnsignedLong(
                      asInstanceOf(UInt32Value.class, deDynamicalizer.canonicalize(value))
                          .getValue()));
        }
        return value ->
            (long) asInstanceOf(UInt32Value.class, deDynamicalizer.canonicalize(value)).getValue();
      case "google.protobuf.DoubleValue":
        return value ->
            asInstanceOf(DoubleValue.class, deDynamicalizer.canonicalize(value)).getValue();
      case "google.protobuf.FloatValue":
        return value ->
            (double) asInstanceOf(FloatValue.class, deDynamicalizer.canonicalize(value)).getValue();
      case "google.protobuf.BoolValue":
        return value ->
            asInstanceOf(BoolValue.class, deDynamicalizer.canonicalize(value)).getValue();
      case "google.protobuf.StringValue":
        return value ->
            asInstanceOf(StringValue.class, deDynamicalizer.canonicalize(value)).getValue();
      case "google.protobuf.BytesValue":
        return value ->
            asInstanceOf(BytesValue.class, deDynamicalizer.canonicalize(value)).getValue();
      case "google.protobuf.Timestamp":
      case "google.protobuf.Duration":
        return deDynamicalizer;
      default:
        return IDENTITY;
    }
  }

  /** Converts an arbitrary message object into its canonical CEL equivalent. */
  public static Object canonicalizeProto(Message value, CelOptions celOptions) {
    return protoCanonicalizer(value.getDescriptorForType(), celOptions).canonicalize(value);
  }

  /** Converts an instance of {@link Any} into its canonical CEL equivalent. */
  private static Object canonicalizeAny(Any any, CelOptions celOptions) {
    try {
      return canonicalizeProto(AnyUtil.unpack(any), celOptions);
    } catch (InvalidProtocolBufferException e) {
      throw new IllegalArgumentException(e);
    }
  }

  /** Recursively converts a JSON {@link Value} into its canonical CEL equivalent. */
  private static Object canonicalizeJsonValue(Value v) {
    switch (v.getKindCase()) {
      case NULL_VALUE:
        return v.getNullValue();
      case NUMBER_VALUE:
        return v.getNumberValue();
      case STRING_VALUE:
        return v.getStringValue();
      case BOOL_VALUE:
        return v.getBoolValue();
      case STRUCT_VALUE:
        return canonicalizeJsonStruct(v.getStructValue());
      case LIST_VALUE:
        return canonicalizeJsonList(v.getListValue());
      default:
        throw new IllegalArgumentException("Invalid JSON value type: " + v.getKindCase());
    }
  }

  /**
   * Converts a JSON {@link ListValue} into the corresponding canonical Java {@link List} by
   * transforming all values recursively.
   */
  private static Object canonicalizeJsonList(ListValue lv) {
    return Lists.transform(lv.getValuesList(), Canonicalization::canonicalizeJsonValue);
  }

  /**
   * Converts a JSON {@link Struct} into the corresponding canonical Java {@link Map}. Keys are
   * always strings, and values are converted recursively.
   */
  private static Object canonicalizeJsonStruct(Struct s) {
    return Maps.transformValues(s.getFieldsMap(), Canonicalization::canonicalizeJsonValue);
  }

  /**
   * Interprets the given value as an instance of the given class, throwing an exception if that
   * cannot be done.
   */
  static <T> T asInstanceOf(Class<T> clazz, Object value) {
    if (clazz.isInstance(value)) {
      return clazz.cast(value);
    }
    throw new IllegalStateException("[internal] not a value of " + clazz + ": " + value);
  }

  /** Interprets the given value as an instance of {@link MessageOrBuilder}. */
  static MessageOrBuilder asMessage(Object value) {
    return asInstanceOf(MessageOrBuilder.class, value);
  }

  /** Determines whether the type of the described field is a wrapper type. */
  static boolean fieldHasWrapperType(FieldDescriptor fd) {
    return fd.getType() == FieldDescriptor.Type.MESSAGE
        && WRAPPER_TYPE_NAMES.contains(fd.getMessageType().getFullName());
  }

  /**
   * Takes a {@link DynamicMessage} object and converts it into its corresponding generated message,
   * if possible.
   */
  // This lambda implements @Immutable interface 'Canonicalizer', but the declaration of type
  // 'com.google.protobuf.Parser<? extends com.google.protobuf.Message>' is not annotated with
  // @com.google.errorprone.annotations.Immutable
  @SuppressWarnings("Immutable")
  private static Canonicalizer deDynamicalizerFor(Descriptor d) {
    String messageName = d.getFullName();
    Descriptor generatedDescriptor =
        GeneratedDescriptorPool.getInstance().getDescriptorForTypeName(messageName);
    if (generatedDescriptor == null) {
      return IDENTITY;
    }
    Message prototype =
        MessageFactories.getImmutableMessageFactory().getPrototype(generatedDescriptor);
    if (prototype == null) {
      return IDENTITY;
    }
    Parser<? extends Message> parser = prototype.getParserForType();
    return object -> {
      if (!(object instanceof DynamicMessage)) {
        return object;
      }
      try {
        return parser.parseFrom(
            ((DynamicMessage) object).toByteArray(), ExtensionRegistry.getGeneratedRegistry());
      } catch (InvalidProtocolBufferException e) {
        throw new AssertionError("Failed to convert DynamicMessage to " + messageName, e);
      }
    };
  }

  private static final ImmutableSet<String> WRAPPER_TYPE_NAMES =
      ImmutableSet.of(
          "google.protobuf.BoolValue",
          "google.protobuf.BytesValue",
          "google.protobuf.DoubleValue",
          "google.protobuf.FloatValue",
          "google.protobuf.Int32Value",
          "google.protobuf.Int64Value",
          "google.protobuf.StringValue",
          "google.protobuf.UInt32Value",
          "google.protobuf.UInt64Value");

  /**
   * The identity canonicalizer. Return this value rather than an on-the-fly lambda when
   * canonicalization does nothing. The identity of IDENTITY (pun intended) is used to short-circuit
   * canonicalization on lists when the element canonicalizer is IDENTITY.
   */
  private static final Canonicalizer IDENTITY = x -> x;

  private Canonicalization() {}
}
