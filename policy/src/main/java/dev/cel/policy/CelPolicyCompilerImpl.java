package dev.cel.policy;

public final class CelPolicyCompilerImpl implements CelPolicyCompiler {

  static CelPolicyCompiler newInstance() {
    return new CelPolicyCompilerImpl();
  }

  private CelPolicyCompilerImpl() {
  }
}

