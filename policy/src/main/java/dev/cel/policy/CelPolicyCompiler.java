package dev.cel.policy;

import dev.cel.common.CelAbstractSyntaxTree;
public interface CelPolicyCompiler {

  CelAbstractSyntaxTree compile(CelPolicy policy) throws CelPolicyValidationException;
}
