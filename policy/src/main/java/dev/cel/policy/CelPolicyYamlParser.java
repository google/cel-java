package dev.cel.policy;

import com.google.common.collect.ImmutableSet;
import dev.cel.common.CelSource;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.util.List;
import java.util.Map;

import static dev.cel.policy.YamlHelper.getOrThrow;

final class CelPolicyYamlParser implements CelPolicyParser {

  @Override
  public CelPolicy parse(CelPolicySource source) throws CelPolicyValidationException {
    Map<String, Object> yamlMap;
    try {
      yamlMap = parseYamlSource(source);
    } catch (Exception e) {
      throw new CelPolicyValidationException("Could not parse YAML document.", e);
    }

    try {
      CelPolicy.Builder policyBuilder = CelPolicy.newBuilder(source)
          .setCelSource(fromPolicySource(source));
      String policyName = getOrThrow(yamlMap, "name", String.class);

      policyBuilder.setName(CelPolicy.ValueString.of(nextId(), policyName));
      // TODO assert yaml type on map
      // policyBuilder.setRule(parseRuleMap((Map<String, Object>) yamlMap.get("rule")));
      policyBuilder.setRule(parseRuleMap(getOrThrow(yamlMap, "name", Map.class)));

      return policyBuilder.build();
    } catch (Exception e) {
      throw new CelPolicyValidationException(e.getMessage(), e);
    }
  }


  private long id;

  private Map<String, Object> parseYamlSource(CelPolicySource policySource) {
    Yaml yaml = new Yaml(new SafeConstructor(new LoaderOptions()));

    return yaml.load(policySource.content());
  }

  private long nextId() {
    return ++id;
  }

  private CelPolicy.Rule parseRuleMap(Map<String, Object> ruleMap) {
    CelPolicy.Rule.Builder ruleBuilder = CelPolicy.Rule.newBuilder();

    for (Map.Entry<String, Object> entry : ruleMap.entrySet()) {
      switch (entry.getKey()) {
        case "id":
          ruleBuilder.setId(CelPolicy.ValueString.of(nextId(), (String) entry.getValue()));
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

  private ImmutableSet<CelPolicy.Variable> parseVariables(List<Map<String, Object>> variableList) {
    ImmutableSet.Builder<CelPolicy.Variable> variableBuilder = ImmutableSet.builder();
    for (Map<String, Object> variableMap : variableList) {
      variableBuilder.add(parseVariable(variableMap));
    }

    return variableBuilder.build();
  }

  private CelPolicy.Variable parseVariable(Map<String, Object> variableMap) {
    return CelPolicy.Variable.of(
        CelPolicy.ValueString.of(nextId(), (String) variableMap.get("name")),
        CelPolicy.ValueString.of(nextId(), (String) variableMap.get("expression"))
    );
  }

  private static CelSource fromPolicySource(CelPolicySource policySource) {
    return CelSource.newBuilder(policySource.content())
        .setDescription(policySource.location())
        .build();
  }

  static CelPolicyParser newInstance() {
    return new CelPolicyYamlParser();
  }

  private CelPolicyYamlParser() {
  }
}
