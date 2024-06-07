package dev.cel.policy;
import dev.cel.bundle.Cel;
public final class CelPolicyCompilerFactory {

  public static CelPolicyCompiler newPolicyCompiler(Cel cel) {
    return CelPolicyCompilerImpl.newInstance(cel);
  }

  private CelPolicyCompilerFactory() {}
}
