package dev.cel.policy;

import org.yaml.snakeyaml.nodes.Node;

public class CelPolicyParserFactory {

  /**
   * TODO
   */
  public static CelPolicyParserBuilder<Node> newYamlParserBuilder() {
    return CelPolicyYamlParser.newBuilder();
  }

  public static CelPolicyConfigParser newYamlConfigParser() {
    return CelPolicyYamlConfigParser.newInstance();
  }
}
