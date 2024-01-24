package dev.cel.extensions;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableMap;
import com.google.testing.junit.testparameterinjector.TestParameter;
import com.google.testing.junit.testparameterinjector.TestParameterInjector;
import dev.cel.bundle.Cel;
import dev.cel.bundle.CelFactory;
import dev.cel.common.CelAbstractSyntaxTree;
import dev.cel.common.CelFunctionDecl;
import dev.cel.common.CelOverloadDecl;
import dev.cel.common.CelValidationException;
import dev.cel.common.ast.CelExpr;
import dev.cel.common.ast.CelExpr.ExprKind.Kind;
import dev.cel.common.navigation.CelNavigableAst;
import dev.cel.common.navigation.CelNavigableExpr;
import dev.cel.common.types.ListType;
import dev.cel.common.types.SimpleType;
import dev.cel.optimizer.MutableAst;
import dev.cel.parser.CelStandardMacro;
import dev.cel.runtime.CelRuntime;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(TestParameterInjector.class)

public class CelBlockExtensionsTest {
  private static final Cel CEL =
      CelFactory.standardCelBuilder()
          .setStandardMacros(CelStandardMacro.STANDARD_MACROS)
          .addFunctionDeclarations(
              CelFunctionDecl.newFunctionDeclaration("cel.block",
                  CelOverloadDecl.newGlobalOverload("block_test_only_overload", SimpleType.DYN, ListType.create(SimpleType.DYN), SimpleType.DYN)
              ),
              CelFunctionDecl.newFunctionDeclaration("cel.index",
                  CelOverloadDecl.newGlobalOverload("index_test_only_overload", SimpleType.DYN, SimpleType.INT)
              )
          )
          .addCompilerLibraries(CelExtensions.block())
          .build();

  private enum BlockTestCase {
    BOOL_LITERAL("cel.block([true, false], cel.index(0) || cel.index(1))"),
    STRING_CONCAT("cel.block(['a' + 'b', cel.index(0) + 'c'], cel.index(1) + 'd') == 'abcd'"),

    BLOCK_WITH_EXISTS_TRUE("cel.block([[1, 2, 3], [3, 4, 5].exists(e, e in cel.index(0))], cel.index(1))"),
    BLOCK_WITH_EXISTS_FALSE("cel.block([[1, 2, 3], ![4, 5].exists(e, e in cel.index(0))], cel.index(1))"),
    ;

    private final String source;

    BlockTestCase(String source) {
      this.source = source;
    }
  }

  @Test
  public void block_success(@TestParameter BlockTestCase testCase) throws Exception {
    CelAbstractSyntaxTree ast = compileUsingInternalFunctions(testCase.source);
    CelRuntime.Program program = CEL.createProgram(ast);

    boolean evaluatedResult = (boolean) program.eval();

    assertThat(evaluatedResult).isTrue();
  }

  /**
   * Converts AST containing cel.block related test functions to internal functions (e.g: cel.block -> cel.@block)
   */
  private static CelAbstractSyntaxTree compileUsingInternalFunctions(String expression)
      throws CelValidationException {
    MutableAst mutableAst = MutableAst.newInstance(1000);
    CelAbstractSyntaxTree astToModify = CEL.compile(expression).getAst();
    ImmutableMap<String, String> functionConversionMap =
        ImmutableMap.of("cel.block", "cel.@block", "cel.index", "cel.@index");
    while (true) {
      CelExpr celExpr = CelNavigableAst.fromAst(astToModify)
          .getRoot()
          .allNodes()
          .filter(node -> node.getKind().equals(Kind.CALL))
          .map(CelNavigableExpr::expr)
          .filter(expr ->
              expr.call().function().equals("cel.block") ||
              expr.call().function().equals("cel.index"))
          .findAny()
          .orElse(null);
      if (celExpr == null) {
        break;
      }
      String internalFunctionName = functionConversionMap.get(celExpr.call().function());
      astToModify = mutableAst.replaceSubtree(astToModify,
          celExpr.toBuilder().setCall(
              celExpr.call().toBuilder().setFunction(internalFunctionName).build()).build(),
          celExpr.id());
    }

    return CEL.check(astToModify).getAst();
  }
}
