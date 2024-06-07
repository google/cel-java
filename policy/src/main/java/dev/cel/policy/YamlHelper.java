package dev.cel.policy;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.io.StringReader;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.ScalarNode;

final class YamlHelper {
  static final String ERROR = "*error*";

  enum YamlNodeType {
    MAP("tag:yaml.org,2002:map"),
    STRING("tag:yaml.org,2002:str"),
    BOOLEAN("tag:yaml.org,2002:bool"),
    INTEGER("tag:yaml.org,2002:int"),
    DOUBLE("tag:yaml.org,2002:float"),
    TEXT("!txt"),
    LIST("tag:yaml.org,2002:seq"),
    ;

    private final String tag;

    String tag() {
      return tag;
    }

    YamlNodeType(String tag) {
      this.tag = tag;
    }
  }

  private static final ImmutableMap<Class<?>, String> YAML_TYPES = ImmutableMap.of(
      String.class, "tag:yaml.org,2002:str !txt",
      Boolean.class, "tag:yaml.org,2002:bool",
      // "tag:yaml.org,2002:null":      yamlNull,
      // "tag:yaml.org,2002:str":       yamlString,
      Integer.class, "",
      Double.class, "",
      List.class, "tag:yaml.org,2002:seq",
      LinkedHashMap.class, "tag:yaml.org,2002:map"
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
    Class<Map<String, Object>> clazz = (Class<Map<String, Object>>) (Class) Map.class;
    return getOrThrow(map, key, clazz);
  }

  static List<Map<String, Object>> getListOfMapsOrThrow(Map<String, Object> map,
      String key) {
    Class<List<Map<String, Object>>> clazz = (Class<List<Map<String, Object>>>) (Class) List.class;
    return getOrThrow(map, key, clazz);
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

  static Node parseYamlSource(CelPolicySource policySource) {
    Yaml yaml = new Yaml(new SafeConstructor(new LoaderOptions()));

    return yaml.compose(new StringReader(policySource.content().toString()));
  }

  static boolean assertRequiredFields(ParserContext<Node> ctx, long id, List<String> missingRequiredFields) {
    if (missingRequiredFields.isEmpty()) {
      return true;
    }

    ctx.reportError(id, String.format("Missing required attribute(s): %s", Joiner.on(", ").join(missingRequiredFields)));
    return false;
  }

  static boolean assertYamlType(ParserContext<Node> ctx, long id, Node node,
      YamlNodeType... expectedNodeTypes) {
    String nodeTag = node.getTag().getValue();
    for (YamlNodeType expectedNodeType : expectedNodeTypes) {
      if (expectedNodeType.tag().equals(nodeTag)) {
        return true;
      }
    }
    ctx.reportError(id, String.format("Got yaml node type %s, wanted type(s) [%s]", nodeTag,
        Arrays.stream(expectedNodeTypes).map(YamlNodeType::tag)
            .collect(Collectors.joining(" "))));
    return false;
  }

  static Integer newInteger(ParserContext<Node> ctx, Node node) {
    long id = ctx.collectMetadata(node);
    if (!assertYamlType(ctx, id, node, YamlNodeType.INTEGER)) {
      return 0;
    }

    return Integer.parseInt(((ScalarNode)node).getValue());
  }

  static boolean newBoolean(ParserContext<Node> ctx, Node node) {
    long id = ctx.collectMetadata(node);
    if (!assertYamlType(ctx, id, node, YamlNodeType.BOOLEAN)) {
      return false;
    }

    return Boolean.parseBoolean(((ScalarNode)node).getValue());
  }

  static String newString(ParserContext<Node> ctx, Node node) {
    return YamlHelper.newValueString(ctx, node).value();
  }

  static ValueString newValueString(ParserContext<Node> ctx, Node node) {
    long id = ctx.collectMetadata(node);
    if (!assertYamlType(ctx, id, node, YamlNodeType.STRING, YamlNodeType.TEXT)) {
      return ValueString.of(id, ERROR);
    }

    return ValueString.of(id, ((ScalarNode) node).getValue());
  }

  private YamlHelper() {
  }
}
