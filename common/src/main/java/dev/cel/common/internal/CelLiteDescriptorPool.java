package dev.cel.common.internal;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.errorprone.annotations.Immutable;
import dev.cel.common.annotations.Internal;
import dev.cel.protobuf.CelLiteDescriptor;
import dev.cel.protobuf.CelLiteDescriptor.FieldInfo;
import dev.cel.protobuf.CelLiteDescriptor.FieldInfo.ValueType;
import dev.cel.protobuf.CelLiteDescriptor.MessageInfo;
import java.util.Optional;

@Immutable
@Internal
public final class CelLiteDescriptorPool {
  private final ImmutableMap<String, MessageInfo> protoFqnToMessageInfo;
  private final ImmutableMap<String, MessageInfo> protoJavaClassNameToMessageInfo;

  public static CelLiteDescriptorPool newInstance(ImmutableSet<CelLiteDescriptor> descriptors) {
    return new CelLiteDescriptorPool(descriptors);
  }

  public Optional<MessageInfo> findMessageInfoByTypeName(String protoFqn) {
    return Optional.ofNullable(protoFqnToMessageInfo.get(protoFqn));
  }

  public Optional<MessageInfo> findMessageInfoByClassName(String javaClassName) {
    return Optional.ofNullable(protoJavaClassNameToMessageInfo.get(javaClassName));
  }

  private static MessageInfo newMessageInfo(WellKnownProto wellKnownProto) {
    ImmutableMap.Builder<String, FieldInfo> fieldInfoMap = ImmutableMap.builder();
    switch (wellKnownProto) {
      case BOOL_VALUE:
        fieldInfoMap.put("value", newPrimitiveFieldInfo(
                "google.protobuf.BoolValue.value",
                "BOOLEAN",
                ValueType.SCALAR,
                FieldInfo.Type.BOOL
            )
        );
        break;
      case BYTES_VALUE:
        fieldInfoMap.put("value", newPrimitiveFieldInfo(
                "google.protobuf.BytesValue.value",
                "BYTE_STRING",
                ValueType.SCALAR,
                FieldInfo.Type.BYTES
            ));
        break;
      case DOUBLE_VALUE:
        fieldInfoMap.put("value", newPrimitiveFieldInfo(
                "google.protobuf.DoubleValue.value",
                "DOUBLE",
                ValueType.SCALAR,
                FieldInfo.Type.DOUBLE
            ));
        break;
      case FLOAT_VALUE:
        fieldInfoMap.put("value", newPrimitiveFieldInfo(
                "google.protobuf.FloatValue.value",
                "FLOAT",
                ValueType.SCALAR,
                FieldInfo.Type.FLOAT
            ));
        break;
      case INT32_VALUE:
        fieldInfoMap.put("value", newPrimitiveFieldInfo(
                "google.protobuf.Int32Value.value",
                "INT",
                ValueType.SCALAR,
                FieldInfo.Type.INT32
            ));
        break;
      case INT64_VALUE:
        fieldInfoMap.put("value", newPrimitiveFieldInfo(
                "google.protobuf.Int64Value.value",
                "LONG",
                ValueType.SCALAR,
                FieldInfo.Type.INT64
            ));
        break;
      case STRING_VALUE:
        fieldInfoMap.put("value", newPrimitiveFieldInfo(
                "google.protobuf.StringValue.value",
                "STRING",
                ValueType.SCALAR,
                FieldInfo.Type.STRING
            ));
        break;
      case UINT32_VALUE:
        fieldInfoMap.put("value", newPrimitiveFieldInfo(
                "google.protobuf.UInt32Value.value",
                "INT",
                ValueType.SCALAR,
                FieldInfo.Type.UINT32
            ));
        break;
      case UINT64_VALUE:
        fieldInfoMap.put("value", newPrimitiveFieldInfo(
                "google.protobuf.UInt64Value.value",
                "LONG",
                ValueType.SCALAR,
                FieldInfo.Type.UINT64
            ));
        break;
    }

    return new MessageInfo(
        wellKnownProto.typeName(),
        wellKnownProto.javaClassName(),
        fieldInfoMap.buildOrThrow()
    );
  }

  private static FieldInfo newPrimitiveFieldInfo(String fullyQualifiedProtoName, String javaTypeName, ValueType valueType, FieldInfo.Type protoFieldType) {
    return new FieldInfo(
        fullyQualifiedProtoName,
        javaTypeName,
        "Value",
        "",
        valueType.toString(),
        protoFieldType.toString(),
        String.valueOf(false));
  }

  private CelLiteDescriptorPool(ImmutableSet<CelLiteDescriptor> descriptors) {
    ImmutableMap.Builder<String, MessageInfo> protoFqnMapBuilder = ImmutableMap.builder();
    ImmutableMap.Builder<String, MessageInfo> protoJavaClassNameMapBuilder = ImmutableMap.builder();
    for (WellKnownProto wellKnownProto : WellKnownProto.values()) {
      MessageInfo wktMessageInfo = newMessageInfo(wellKnownProto);
      protoFqnMapBuilder.put(wellKnownProto.typeName(), wktMessageInfo);
      protoJavaClassNameMapBuilder.put(wellKnownProto.javaClassName(), wktMessageInfo);
    }

    for (CelLiteDescriptor descriptor : descriptors) {
      protoFqnMapBuilder.putAll(descriptor.getProtoFqnToMessageInfo());
      protoJavaClassNameMapBuilder.putAll(descriptor.getProtoJavaClassNameToMessageInfo());
    }

    this.protoFqnToMessageInfo = protoFqnMapBuilder.buildOrThrow();
    this.protoJavaClassNameToMessageInfo = protoJavaClassNameMapBuilder.buildOrThrow();
  }
}
