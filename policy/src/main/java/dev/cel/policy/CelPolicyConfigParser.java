package dev.cel.policy;

public interface CelPolicyConfigParser {
  CelPolicyConfig parse(CelPolicySource policyConfigSource) throws CelPolicyValidationException;
}
