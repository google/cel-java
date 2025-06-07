package dev.cel.runtime;

import static com.google.common.truth.Truth.assertThat;

import com.google.testing.junit.testparameterinjector.TestParameterInjector;
import dev.cel.common.CelAbstractSyntaxTree;
import dev.cel.compiler.CelCompiler;
import dev.cel.compiler.CelCompilerFactory;
import dev.cel.runtime.CelLiteRuntime.Program;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(TestParameterInjector.class)
public final class ProgramPlannerTest {
  private static final CelCompiler CEL_COMPILER = CelCompilerFactory.standardCelCompilerBuilder().build();

  @Test
  public void planConst() throws Exception {
    CelAbstractSyntaxTree ast = CEL_COMPILER.compile("1").getAst();
    Program program = ProgramPlanner.plan(ast);

    Object result = program.eval();

    assertThat(result).isEqualTo(1);
  }
}
