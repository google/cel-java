package dev.cel.policy;

public interface CelPolicyParser {

  CelPolicy parse(CelPolicySource source) throws CelPolicyValidationException;
}
