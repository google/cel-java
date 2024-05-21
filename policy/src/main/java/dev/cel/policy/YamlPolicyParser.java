package dev.cel.policy;

import dev.cel.policy.Policy.ValueString;
import java.util.Map;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

public final class YamlPolicyParser implements PolicyParser {

  private static final class YamlPolicyParserImpl {

    private int id;

    private Policy fromYamlMap(Map<String, Object> yamlMap) {
      Policy.Builder policyBuilder = Policy.newBuilder();
      if (!yamlMap.containsKey("name")) {
        throw new IllegalArgumentException("Missing required property: 'name'");
      }
      policyBuilder.setName(
          ValueString.of(++id, (String) yamlMap.get("name"))
      );

      return policyBuilder.build();
    }


    private static YamlPolicyParserImpl newInstance() {
      return new YamlPolicyParserImpl();
    }

    private YamlPolicyParserImpl() {
    }
  }

  @Override
  public Policy parse(String source) {
    Yaml yaml = new Yaml(new SafeConstructor(new LoaderOptions()));

    YamlPolicyParserImpl yamlPolicyParserImpl = YamlPolicyParserImpl.newInstance();

    return yamlPolicyParserImpl.fromYamlMap(yaml.load(source));
  }


  public static PolicyParser newInstance() {
    return new YamlPolicyParser();
  }

  private YamlPolicyParser() {
  }

}
