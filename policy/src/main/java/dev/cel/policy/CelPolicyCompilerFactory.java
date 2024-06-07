package dev.cel.policy;
public final class CelPolicyCompilerFactory {

  public static CelPolicyCompiler newPolicyCompiler() {
    return CelPolicyCompilerImpl.newInstance();
  }

  private CelPolicyCompilerFactory() {}
}
