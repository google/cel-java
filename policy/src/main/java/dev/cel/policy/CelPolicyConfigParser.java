package dev.cel.policy;

public interface CelPolicyConfigParser {
    CelPolicyConfig parse(String content) throws CelPolicyValidationException;
}
