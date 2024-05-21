package dev.cel.policy;

public final class PolicyCompilerImpl implements PolicyCompiler {

  public static PolicyCompiler newInstance() {
    return new PolicyCompilerImpl();
  }

  private PolicyCompilerImpl() {
  }
}
