package dev.cel.runtime;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import com.google.testing.junit.testparameterinjector.TestParameterInjector;
import dev.cel.common.CelAbstractSyntaxTree;
import dev.cel.common.CelFunctionDecl;
import dev.cel.common.CelOptions;
import dev.cel.common.CelOverloadDecl;
import dev.cel.common.types.SimpleType;
import dev.cel.compiler.CelCompiler;
import dev.cel.compiler.CelCompilerFactory;
import dev.cel.parser.CelStandardMacro;
import dev.cel.runtime.DefaultInterpreter.DefaultInterpretable;
import dev.cel.runtime.DefaultInterpreter.ExecutionFrame;
import java.util.Map;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(TestParameterInjector.class)
public class DefaultInterpreterTest {

  @Test
  public void nestedComprehensions_accuVarContainsErrors_scopeLevelInvariantNotViolated() throws Exception {
    CelCompiler celCompiler = CelCompilerFactory.standardCelCompilerBuilder()
        .addFunctionDeclarations(CelFunctionDecl.newFunctionDeclaration(
            "error", CelOverloadDecl.newGlobalOverload("error_overload", SimpleType.DYN)
        ))
        .setStandardMacros(CelStandardMacro.STANDARD_MACROS).build();
    RuntimeTypeProvider emptyProvider = new RuntimeTypeProvider() {
      @Override
      public Object createMessage(String messageName, Map<String, Object> values) {
        return null;
      }
      @Override
      public Object selectField(Object message, String fieldName) {
        return null;
      }
      @Override
      public Object hasField(Object message, String fieldName) {
        return null;
      }
      @Override
      public Object adapt(Object message) {
        return message;
      }
    };
    CelAbstractSyntaxTree ast =
        celCompiler.compile("[1].all(x, [2].all(y, error()))").getAst();
    DefaultDispatcher dispatcher = DefaultDispatcher.create();
    dispatcher.add("error", long.class, (args) -> new IllegalArgumentException("Always throws"));
    DefaultInterpreter defaultInterpreter = new DefaultInterpreter(
        new TypeResolver(), emptyProvider, dispatcher, CelOptions.DEFAULT);
    DefaultInterpretable interpretable = (DefaultInterpretable) defaultInterpreter.createInterpretable(ast);

    ExecutionFrame frame = interpretable.newTestExecutionFrame(GlobalResolver.EMPTY);

    assertThrows(CelEvaluationException.class, () -> interpretable.populateExecutionFrame(frame));
    assertThat(frame.scopeLevel).isEqualTo(0);
  }
}
