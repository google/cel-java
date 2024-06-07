package dev.cel.policy;

import org.yaml.snakeyaml.nodes.Node;

public final class CelPolicyParserFactory {

  /**
   * TODO
   */
  public static CelPolicyParserBuilder<Node> newYamlParserBuilder() {
    return CelPolicyYamlParser.newBuilder();
  }

  public static CelPolicyConfigParser newYamlConfigParser() {
    return CelPolicyYamlConfigParser.newInstance();
  }

  private CelPolicyParserFactory() {}
}
