package dev.cel.policy;
import dev.cel.bundle.Cel;
import dev.cel.bundle.CelFactory;
import dev.cel.checker.CelChecker;
import dev.cel.compiler.CelCompiler;
import dev.cel.compiler.CelCompilerFactory;
import dev.cel.parser.CelParser;
import dev.cel.runtime.CelRuntime;

public final class CelPolicyCompilerFactory {

  public static CelPolicyCompilerBuilder newPolicyCompiler(Cel cel) {
    return CelPolicyCompilerImpl.newBuilder(cel);
  }

  public static CelPolicyCompilerBuilder newPolicyCompiler(CelCompiler celCompiler, CelRuntime celRuntime) {
    return newPolicyCompiler(CelFactory.combine(celCompiler, celRuntime));
  }

  public static CelPolicyCompilerBuilder newPolicyCompiler(CelParser celParser, CelChecker celChecker, CelRuntime celRuntime) {
    return newPolicyCompiler(CelCompilerFactory.combine(celParser, celChecker), celRuntime);
  }

  private CelPolicyCompilerFactory() {}
}
