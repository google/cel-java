package dev.cel.optimizer.optimizers;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import com.google.testing.junit.testparameterinjector.TestParameterInjector;
import dev.cel.bundle.Cel;
import dev.cel.bundle.CelFactory;
import dev.cel.common.CelAbstractSyntaxTree;
import dev.cel.common.CelOptions;
import dev.cel.common.CelSource;
import dev.cel.common.ast.CelExpr;
import dev.cel.common.types.SimpleType;
import dev.cel.extensions.CelExtensions;
import dev.cel.optimizer.CelOptimizationException;
import dev.cel.optimizer.CelOptimizer;
import dev.cel.optimizer.CelOptimizerFactory;
import dev.cel.optimizer.optimizers.InliningOptimizer.InlineVariable;
import dev.cel.optimizer.optimizers.InliningOptimizer.InliningOptions;
import dev.cel.parser.CelStandardMacro;
import dev.cel.parser.CelUnparserFactory;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(TestParameterInjector.class)
public class InliningOptimizerTest {

  @Test
  public void inlineConstant() throws Exception {
    Cel cel = CelFactory.standardCelBuilder().addVar("ident_to_inline", SimpleType.INT).build();
    String expression = "ident_to_inline + 2 + ident_to_inline";
    CelAbstractSyntaxTree astToInline = cel.compile(expression).getAst();
    CelOptimizer optimizer = CelOptimizerFactory.standardCelOptimizerBuilder(cel)
        .addAstOptimizers(InliningOptimizer.newInstance(
            InlineVariable.of("ident_to_inline", cel.compile("1").getAst())
        ))
        .build();

    CelAbstractSyntaxTree optimized = optimizer.optimize(astToInline);

    String unparsed = CelUnparserFactory.newUnparser().unparse(optimized);
    assertThat(unparsed).isEqualTo("1 + 2 + 1");
  }

  @Test
  public void inline_doesNotInlineIterVar() throws Exception {
    Cel cel = CelFactory.standardCelBuilder()
        .setStandardMacros(CelStandardMacro.STANDARD_MACROS)
        .setOptions(CelOptions.current().populateMacroCalls(true).build())
        .addVar("shadowed_ident", SimpleType.INT)
        .build();
    String expression = "[0].exists(shadowed_ident, shadowed_ident == 0)";
    CelAbstractSyntaxTree astToInline = cel.compile(expression).getAst();
    CelOptimizer optimizer = CelOptimizerFactory.standardCelOptimizerBuilder(cel)
        .addAstOptimizers(InliningOptimizer.newInstance(
            InlineVariable.of("shadowed_ident", cel.compile("1").getAst())
        ))
        .build();

    CelAbstractSyntaxTree optimized = optimizer.optimize(astToInline);

    String unparsed = CelUnparserFactory.newUnparser().unparse(optimized);
    assertThat(unparsed).isEqualTo("[0].exists(shadowed_ident, shadowed_ident == 0)");
  }

  @Test
  public void inline_bindMacro_doesNotInlineVarName() throws Exception {
    Cel cel = CelFactory.standardCelBuilder()
        .setStandardMacros(CelStandardMacro.STANDARD_MACROS)
        .addCompilerLibraries(CelExtensions.bindings())
        .setOptions(CelOptions.current().populateMacroCalls(true).build())
        .addVar("shadowed_ident", SimpleType.INT)
        .build();
    String expression = "cel.bind(shadowed_ident, 2, shadowed_ident + 1)";
    CelAbstractSyntaxTree astToInline = cel.compile(expression).getAst();
    CelOptimizer optimizer = CelOptimizerFactory.standardCelOptimizerBuilder(cel)
        .addAstOptimizers(InliningOptimizer.newInstance(
            InlineVariable.of("shadowed_ident", cel.compile("1").getAst())
        ))
        .build();

    CelAbstractSyntaxTree optimized = optimizer.optimize(astToInline);

    String unparsed = CelUnparserFactory.newUnparser().unparse(optimized);
    assertThat(unparsed).isEqualTo("cel.bind(shadowed_ident, 2, shadowed_ident + 1)");
  }

  @Test
  public void inline_exceededIterationLimit_throws() throws Exception {
    Cel cel = CelFactory.standardCelBuilder()
        .setStandardMacros(CelStandardMacro.STANDARD_MACROS)
        .addCompilerLibraries(CelExtensions.bindings())
        .addVar("ident_to_replace", SimpleType.INT)
        .build();
    String expression = "ident_to_replace + ident_to_replace + ident_to_replace";
    CelAbstractSyntaxTree astToInline = cel.compile(expression).getAst();
    CelOptimizer optimizer = CelOptimizerFactory.standardCelOptimizerBuilder(cel)
        .addAstOptimizers(InliningOptimizer.newInstance(
            InliningOptions.newBuilder().maxIterationLimit(2).build(),
            InlineVariable.of("ident_to_replace", cel.compile("1").getAst())
        ))
        .build();

    CelOptimizationException e = assertThrows(CelOptimizationException.class, () -> optimizer.optimize(astToInline));
    assertThat(e).hasMessageThat().contains("Max iteration count reached.");
  }

  @Test
  public void inlineVariableDecl_internalVar_throws() throws Exception {
    IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () ->
            InlineVariable.of("@internal_var", CelAbstractSyntaxTree.newParsedAst(CelExpr.ofNotSet(0L), CelSource.newBuilder().build())));
    assertThat(e).hasMessageThat().contains("Internal variables cannot be inlined: @internal_var" );
  }
}
