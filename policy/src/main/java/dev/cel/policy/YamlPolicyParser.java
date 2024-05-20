package dev.cel.policy;

import java.util.Map;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

public final class YamlPolicyParser implements PolicyParser {

  @Override
  public Policy parse(String source) {
    Yaml yaml = new Yaml(new SafeConstructor(new LoaderOptions()));
    Map<String, Object> deserializedYamlMap = yaml.load(source);
    return null;
  }

  private static Policy fromYamlMap(Map<String, Object> yamlMap) {
    return null;
  }

  public static PolicyParser newInstance() {
    return new YamlPolicyParser();
  }

  private YamlPolicyParser() {
  }

}
