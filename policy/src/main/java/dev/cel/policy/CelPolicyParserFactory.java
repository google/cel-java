package dev.cel.policy;

public class CelPolicyParserFactory {

  /**
   * TODO
   */
  public static CelPolicyParser newYamlParser() {
    return CelPolicyYamlParser.newInstance();
  }

  public static CelPolicyConfigParser newYamlConfigParser() {
    return CelPolicyYamlConfigParser.newInstance();
  }
}
