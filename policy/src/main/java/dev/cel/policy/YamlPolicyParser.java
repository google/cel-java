package dev.cel.policy;

import com.google.common.collect.ImmutableSet;
import dev.cel.common.CelSource;
import dev.cel.policy.Policy.Rule;
import dev.cel.policy.Policy.ValueString;
import dev.cel.policy.Policy.Variable;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

public final class YamlPolicyParser implements PolicyParser {

  @Override
  public Policy parse(PolicySource source) {
    YamlPolicyParserImpl yamlPolicyParserImpl = YamlPolicyParserImpl.newInstance();

    return yamlPolicyParserImpl.toPolicy(source);
  }


  public static PolicyParser newInstance() {
    return new YamlPolicyParser();
  }

  private static final class YamlPolicyParserImpl {

    private long id;

    private Map<String, Object> parseYamlSource(PolicySource policySource) {
      Yaml yaml = new Yaml(new SafeConstructor(new LoaderOptions()));

      return yaml.load(policySource.content());
    }

    private Policy toPolicy(PolicySource source) {
      Map<String, Object> yamlMap = parseYamlSource(source);

      Policy.Builder policyBuilder = Policy.newBuilder(source)
          .setCelSource(fromPolicySource(source));
      String policyName = (String) yamlMap.computeIfAbsent("name", (unused) -> {
        throw new IllegalArgumentException("Missing required property: 'name'");
      });
      policyBuilder.setName(ValueString.of(nextId(), policyName));
      // TODO assert yaml type on map
      policyBuilder.setRule(parseRuleMap((Map<String, Object>) yamlMap.get("rule")));

      return policyBuilder.build();
    }

    private long nextId() {
      return ++id;
    }

    private Policy.Rule parseRuleMap(Map<String, Object> ruleMap) {
      Rule.Builder ruleBuilder = Rule.newBuilder();

      for (Entry<String, Object> entry : ruleMap.entrySet()) {
        switch (entry.getKey()) {
          case "id":
            ruleBuilder.setId(ValueString.of(nextId(), (String) entry.getValue()));
            break;
          case "description":
            break;
          case "variables":
            // TODO assert yaml type on list
            ruleBuilder.addVariables(
                parseVariables((List<Map<String, Object>>) entry.getValue()));
            // ruleBuilder.addVariables(parseVariableMap((Map<String, Object>) entry.getValue()));
            break;
          case "match":
            break;
          default:
            break;
        }
      }

      return ruleBuilder.build();
    }

    private ImmutableSet<Variable> parseVariables(List<Map<String, Object>> variableList) {
      ImmutableSet.Builder<Variable> variableBuilder = ImmutableSet.builder();
      for (Map<String, Object> variableMap : variableList) {
        variableBuilder.add(parseVariable(variableMap));
      }

      return variableBuilder.build();
    }

    private Policy.Variable parseVariable(Map<String, Object> variableMap) {
      return Variable.of(
          ValueString.of(nextId(), (String) variableMap.get("name")),
          ValueString.of(nextId(), (String) variableMap.get("expression"))
      );
    }

    private static CelSource fromPolicySource(PolicySource policySource) {
      return CelSource.newBuilder(policySource.content())
          .setDescription(policySource.location())
          .build();
    }

    private static YamlPolicyParserImpl newInstance() {
      return new YamlPolicyParserImpl();
    }

    private YamlPolicyParserImpl() {
    }
  }

  private YamlPolicyParser() {
  }
}
