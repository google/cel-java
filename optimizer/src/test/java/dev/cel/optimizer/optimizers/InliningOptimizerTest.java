package dev.cel.optimizer.optimizers;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import com.google.testing.junit.testparameterinjector.TestParameter;
import com.google.testing.junit.testparameterinjector.TestParameterInjector;
import dev.cel.bundle.Cel;
import dev.cel.bundle.CelFactory;
import dev.cel.common.CelAbstractSyntaxTree;
import dev.cel.common.CelOptions;
import dev.cel.common.CelSource;
import dev.cel.common.ast.CelExpr;
import dev.cel.common.types.SimpleType;
import dev.cel.extensions.CelExtensions;
import dev.cel.common.CelContainer;
import dev.cel.common.types.StructTypeReference;
import dev.cel.expr.conformance.proto3.TestAllTypes;
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

  private static final Cel CEL = CelFactory.standardCelBuilder()
      .setStandardMacros(CelStandardMacro.STANDARD_MACROS)
      .setContainer(CelContainer.ofName("google.expr.proto3.test"))
      .addFileTypes(TestAllTypes.getDescriptor().getFile())
      .addCompilerLibraries(CelExtensions.bindings())
      .addVar("int_var_to_inline", SimpleType.INT)
      .addVar("a", SimpleType.DYN)
      .addVar("msg", StructTypeReference.create(TestAllTypes.getDescriptor().getFullName()))
      .addVar("unpacked_wrapper", SimpleType.STRING)
      .addVar("wrapper_var", StructTypeReference.create("google.protobuf.Int64Value"))
      .addVar("child", StructTypeReference.create(TestAllTypes.NestedMessage.getDescriptor().getFullName()))
      .addVar("shadowed_ident", SimpleType.INT)
      .addVar("x", SimpleType.DYN)
      .setOptions(CelOptions.current().populateMacroCalls(true).build())
      .build();

  @Test
  public void inlining_success(@TestParameter SuccessTestCase testCase) throws Exception {
    CelAbstractSyntaxTree astToInline = CEL.compile(testCase.source).getAst();
    CelAbstractSyntaxTree replacementAst = CEL.compile(testCase.replacement).getAst();

    CelOptimizer optimizer = CelOptimizerFactory.standardCelOptimizerBuilder(CEL)
        .addAstOptimizers(InliningOptimizer.newInstance(
            InlineVariable.of(testCase.varName, replacementAst)))
        .build();

    CelAbstractSyntaxTree optimized = optimizer.optimize(astToInline);

    String unparsed = CelUnparserFactory.newUnparser().unparse(optimized);
    assertThat(unparsed).isEqualTo(testCase.expected);
  }

  @Test
  public void inlining_noop(@TestParameter NoOpTestCase testCase) throws Exception {
    CelAbstractSyntaxTree astToInline = CEL.compile(testCase.source).getAst();
    CelAbstractSyntaxTree replacementAst = CEL.compile(testCase.replacement).getAst();

    CelOptimizer optimizer = CelOptimizerFactory.standardCelOptimizerBuilder(CEL)
        .addAstOptimizers(InliningOptimizer.newInstance(
            InlineVariable.of(testCase.varName, replacementAst)))
        .build();

    CelAbstractSyntaxTree optimized = optimizer.optimize(astToInline);

    String unparsed = CelUnparserFactory.newUnparser().unparse(optimized);
    assertThat(unparsed).isEqualTo(testCase.source);
  }

  private enum SuccessTestCase {
    CONSTANT(
        "int_var_to_inline + 2 + int_var_to_inline",
        "int_var_to_inline",
        "1",
        "1 + 2 + 1"),
    REPEATED(
        "a + [a]",
        "a",
        "dyn([1, 2])",
        "dyn([1, 2]) + [dyn([1, 2])]"),
    SELECT(
        "has(msg.single_any)",
        "msg.single_any",
        "msg.single_int64_wrapper",
        "has(msg.single_int64_wrapper)"),
    PRESENCE(
        "has(msg.single_int64_wrapper)",
        "msg.single_int64_wrapper",
        "wrapper_var",
        "wrapper_var != null"),
    NESTED(
        "msg.standalone_message.bb",
        "msg.standalone_message",
        "child",
        "child.bb"),
        ;

    private final String source;
    private final String varName;
    private final String replacement;
    private final String expected;

    SuccessTestCase(String source, String varName, String replacement, String expected) {
      this.source = source;
      this.varName = varName;
      this.replacement = replacement;
      this.expected = expected;
    }
  }

  private enum NoOpTestCase {
    NO_INLINE_ITER_VAR(
        "[0].exists(shadowed_ident, shadowed_ident == 0)",
        "shadowed_ident",
        "1"),
    NO_INLINE_BIND_VAR(
        "cel.bind(shadowed_ident, 2, shadowed_ident + 1)",
        "shadowed_ident",
        "1"),
        ;

    private final String source;
    private final String varName;
    private final String replacement;

    NoOpTestCase(String source, String varName, String replacement) {
      this.source = source;
      this.varName = varName;
      this.replacement = replacement;
    }
  }

  @Test
  public void inline_exceededIterationLimit_throws() throws Exception {
    String expression = "int_var_to_inline + int_var_to_inline + int_var_to_inline";
    CelAbstractSyntaxTree astToInline = CEL.compile(expression).getAst();
    CelOptimizer optimizer = CelOptimizerFactory.standardCelOptimizerBuilder(CEL)
        .addAstOptimizers(InliningOptimizer.newInstance(
            InliningOptions.newBuilder().maxIterationLimit(2).build(),
            InlineVariable.of("int_var_to_inline", CEL.compile("1").getAst())))
        .build();

    CelOptimizationException e = assertThrows(CelOptimizationException.class, () -> optimizer.optimize(astToInline));
    assertThat(e).hasMessageThat().contains("Max iteration count reached.");
  }

  @Test
  public void inlineVariableDecl_internalVar_throws() throws Exception {
    IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> InlineVariable.of("@internal_var",
        CelAbstractSyntaxTree.newParsedAst(CelExpr.ofNotSet(0L), CelSource.newBuilder().build())));
    assertThat(e).hasMessageThat().contains("Internal variables cannot be inlined: @internal_var");
  }
}
