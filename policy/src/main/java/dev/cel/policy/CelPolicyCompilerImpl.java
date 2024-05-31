package dev.cel.policy;

public final class CelPolicyCompilerImpl implements CelPolicyCompiler {

  public static CelPolicyCompiler newInstance() {
    return new CelPolicyCompilerImpl();
  }

  private CelPolicyCompilerImpl() {
  }
}
