package dev.cel.policy;

import dev.cel.bundle.Cel;
import dev.cel.common.CelAbstractSyntaxTree;
public final class CelPolicyCompilerImpl implements CelPolicyCompiler {

  private final Cel cel;

  @Override
  public CelAbstractSyntaxTree compile(CelPolicy policy) {
    return null;
  }

  static CelPolicyCompiler newInstance(Cel cel) {
    return new CelPolicyCompilerImpl(cel);
  }

  private CelPolicyCompilerImpl(Cel cel) {
    this.cel = cel;
  }
}

