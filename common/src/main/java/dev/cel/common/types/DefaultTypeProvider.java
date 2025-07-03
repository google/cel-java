package dev.cel.common.types;

import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;

import java.util.Map;
import java.util.Optional;

public class DefaultTypeProvider implements CelTypeProvider {

  private static final ImmutableMap<String, CelType> COMMON_TYPES =
      ImmutableMap.<String, CelType>builder()
          .put("bool", TypeType.create(SimpleType.BOOL))
          .put("bytes", TypeType.create(SimpleType.BYTES))
          .put("double", TypeType.create(SimpleType.DOUBLE))
          .put("int", TypeType.create(SimpleType.INT))
          .put("uint", TypeType.create(SimpleType.UINT))
          .put("string", TypeType.create(SimpleType.STRING))
          .put("null_type", TypeType.create(SimpleType.NULL_TYPE))
          .put("dyn",TypeType.create(SimpleType.DYN))
          .put("list", TypeType.create(ListType.create(SimpleType.DYN)))
          .put("map", TypeType.create(MapType.create(SimpleType.DYN, SimpleType.DYN)))
          .put("google.protobuf.Duration", TypeType.create(SimpleType.DURATION))
          .put("google.protobuf.Timestamp", TypeType.create(SimpleType.TIMESTAMP))
          .put("optional_type", TypeType.create(OptionalType.create(SimpleType.DYN))) // TODO: Move to CelOptionalLibrary
          .buildOrThrow();

  // private static final ImmutableMap<Class<?>, TypeType> EXTENDABLE_TYPES =
  //     ImmutableMap.<Class<?>, TypeType>builder()
  //         .put(Collection.class, TypeType.create(ListType.create(SimpleType.DYN)))
  //         .put(ByteString.class, TypeType.create(SimpleType.BYTES))
  //         .put(Map.class, TypeType.create(MapType.create(SimpleType.DYN, SimpleType.DYN)))
  //         .buildOrThrow();

  private static Map.Entry<String, TypeType> newTypeMapEntry(CelType type) {
    return Maps.immutableEntry(type.name(), TypeType.create(type));
  }

  @Override
  public ImmutableCollection<CelType> types() {
    return COMMON_TYPES.values();
  }
  @Override
  public Optional<CelType> findType(String typeName) {
    return Optional.ofNullable(COMMON_TYPES.get(typeName));
  }

  public static DefaultTypeProvider create() {
    return new DefaultTypeProvider();
  }

  private DefaultTypeProvider() {}
}
