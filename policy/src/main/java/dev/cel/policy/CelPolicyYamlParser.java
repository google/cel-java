package dev.cel.policy;

import static dev.cel.policy.YamlHelper.getOrThrow;

import com.google.common.collect.ImmutableSet;
import dev.cel.common.CelSource;
import dev.cel.policy.CelPolicy.Rule;
import dev.cel.policy.CelPolicy.ValueString;
import dev.cel.policy.CelPolicy.Variable;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

public final class CelPolicyYamlParser implements CelPolicyParser {

  @Override
  public CelPolicy parse(CelPolicySource source) {
    YamlPolicyParserImpl yamlPolicyParserImpl = YamlPolicyParserImpl.newInstance();

    return yamlPolicyParserImpl.parsePolicy(source);
  }


  public static CelPolicyParser newInstance() {
    return new CelPolicyYamlParser();
  }

  private static final class YamlPolicyParserImpl {

    private long id;

    private Map<String, Object> parseYamlSource(CelPolicySource policySource) {
      Yaml yaml = new Yaml(new SafeConstructor(new LoaderOptions()));

      return yaml.load(policySource.content());
    }

    private CelPolicy parsePolicy(CelPolicySource source) {
      Map<String, Object> yamlMap = parseYamlSource(source);

      CelPolicy.Builder policyBuilder = CelPolicy.newBuilder(source)
          .setCelSource(fromPolicySource(source));
      String policyName = getOrThrow(yamlMap, "name", String.class);

      policyBuilder.setName(ValueString.of(nextId(), policyName));
      // TODO assert yaml type on map
      policyBuilder.setRule(parseRuleMap((Map<String, Object>) yamlMap.get("rule")));

      return policyBuilder.build();
    }

    private long nextId() {
      return ++id;
    }

    private CelPolicy.Rule parseRuleMap(Map<String, Object> ruleMap) {
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

    private CelPolicy.Variable parseVariable(Map<String, Object> variableMap) {
      return Variable.of(
          ValueString.of(nextId(), (String) variableMap.get("name")),
          ValueString.of(nextId(), (String) variableMap.get("expression"))
      );
    }

    private static CelSource fromPolicySource(CelPolicySource policySource) {
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

  private CelPolicyYamlParser() {
  }
}
