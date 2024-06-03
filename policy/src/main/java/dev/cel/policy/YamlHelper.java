package dev.cel.policy;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.util.List;
import java.util.Map;

final class YamlHelper {

  private static final ImmutableMap<Class<?>, String> YAML_TYPES = ImmutableMap.of(
      String.class, "!txt",
      Boolean.class, "tag:yaml.org,2002:bool",
      // "tag:yaml.org,2002:null":      yamlNull,
      // "tag:yaml.org,2002:str":       yamlString,
      Integer.class, "tag:yaml.org,2002:int",
      Double.class, "tag:yaml.org,2002:float",
      List.class, "tag:yaml.org,2002:seq",
      Map.class, "tag:yaml.org,2002:map"
      // Timestamp.class, "tag:yaml.org,2002:timestamp"
  );

  static <T> T getOrThrow(Map<String, Object> map, String key, Class<T> clazz) {
    checkRequiredAttributeExists(map, key);
    Object value = checkNotNull(map.get(key));
    if (!clazz.isInstance(value)) {
      throw new IllegalArgumentException(String.format("got yaml node type %s, wanted type(s) %s",
          YAML_TYPES.get(value.getClass()), YAML_TYPES.get(clazz)));
    }

    return clazz.cast(value);
  }

  static Map<String, Object> getMapOrThrow(Map<String, Object> map, String key) {
    checkRequiredAttributeExists(map, key);
    return (Map<String, Object>) map.get(key);
  }

  static List<Map<String, Object>> getListOfMapsOrThrow(Map<String, Object> map,
      String key) {
    checkRequiredAttributeExists(map, key);
    return (List<Map<String, Object>>) map.get(key);
  }

  static void checkRequiredAttributeExists(Map<String, Object> map, String key) {
    if (!map.containsKey(key)) {
      throw new IllegalArgumentException("Missing required attribute: " + key);
    }
  }

  static List<Map<String, Object>> getListOfMapsOrDefault(Map<String, Object> map,
      String key) {
    return (List<Map<String, Object>>) map.getOrDefault(key, ImmutableList.of());
  }


  private YamlHelper() {
  }
}
