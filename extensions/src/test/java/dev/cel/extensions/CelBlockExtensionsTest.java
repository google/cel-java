package dev.cel.extensions;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.testing.junit.testparameterinjector.TestParameter;
import com.google.testing.junit.testparameterinjector.TestParameterInjector;
import com.google.testing.junit.testparameterinjector.TestParameters;
import dev.cel.bundle.Cel;
import dev.cel.bundle.CelFactory;
import dev.cel.common.CelAbstractSyntaxTree;
import dev.cel.common.CelFunctionDecl;
import dev.cel.common.CelOverloadDecl;
import dev.cel.common.CelValidationException;
import dev.cel.common.CelVarDecl;
import dev.cel.common.ast.CelExpr;
import dev.cel.common.ast.CelExpr.ExprKind.Kind;
import dev.cel.common.navigation.CelNavigableAst;
import dev.cel.common.navigation.CelNavigableExpr;
import dev.cel.common.types.ListType;
import dev.cel.common.types.SimpleType;
import dev.cel.common.types.StructTypeReference;
import dev.cel.optimizer.MutableAst;
import dev.cel.parser.CelStandardMacro;
import dev.cel.runtime.CelRuntime;
import dev.cel.runtime.CelRuntime.CelFunctionBinding;
import dev.cel.runtime.CelRuntimeFactory;
import dev.cel.testing.testdata.proto3.TestAllTypesProto.TestAllTypes;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(TestParameterInjector.class)

public class CelBlockExtensionsTest {
  private static final Cel CEL =
      CelFactory.standardCelBuilder()
          .setStandardMacros(CelStandardMacro.STANDARD_MACROS)
          .addFunctionDeclarations(
              // This is a test only declaration, as the actual function is made internal using @ symbol.
              // If the main function declaration needs updating, be sure to update the test declaration as well.
              CelFunctionDecl.newFunctionDeclaration("cel.block",
                  CelOverloadDecl.newGlobalOverload("block_test_only_overload", SimpleType.DYN, ListType.create(SimpleType.DYN), SimpleType.DYN)
              ),
              CelFunctionDecl.newFunctionDeclaration(
                  "get_true",
                  CelOverloadDecl.newGlobalOverload("get_true_overload", SimpleType.BOOL))
          )
          // Similarly, this is a test only decl (index0 -> @index0)
          .addVarDeclarations(
              CelVarDecl.newVarDeclaration("index0", SimpleType.DYN),
              CelVarDecl.newVarDeclaration("index1", SimpleType.DYN),
              CelVarDecl.newVarDeclaration("index2", SimpleType.DYN)
          )
          .addCompilerLibraries(CelExtensions.block())
          .addMessageTypes(TestAllTypes.getDescriptor())
          .addVar("msg", StructTypeReference.create(TestAllTypes.getDescriptor().getFullName()))
          .build();

  private enum BlockTestCase {
    BOOL_LITERAL("cel.block([true, false], index0 || index1)"),
    STRING_CONCAT("cel.block(['a' + 'b', index0 + 'c'], index1 + 'd') == 'abcd'"),

    BLOCK_WITH_EXISTS_TRUE("cel.block([[1, 2, 3], [3, 4, 5].exists(e, e in index0)], index1)"),
    BLOCK_WITH_EXISTS_FALSE("cel.block([[1, 2, 3], ![4, 5].exists(e, e in index0)], index1)"),
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

  @Test
  @SuppressWarnings("Immutable") // Test only
  public void lazyEval_blockIndexNeverReferenced() throws Exception {
    AtomicInteger invocation = new AtomicInteger();
    CelRuntime celRuntime =
        CelRuntimeFactory.standardCelRuntimeBuilder()
            .addMessageTypes(TestAllTypes.getDescriptor())
            .addFunctionBindings(
                CelFunctionBinding.from(
                    "get_true_overload",
                    ImmutableList.of(),
                    arg -> {
                      invocation.getAndIncrement();
                      return true;
                    }))
            .build();
    CelAbstractSyntaxTree ast =
      compileUsingInternalFunctions("cel.block([get_true()], has(msg.single_int64) ? index0 : false)");

    boolean result =
        (boolean)
            celRuntime
                .createProgram(ast)
                .eval(ImmutableMap.of("msg", TestAllTypes.getDefaultInstance()));

    assertThat(result).isFalse();
    assertThat(invocation.get()).isEqualTo(0);
  }

  @Test
  @SuppressWarnings("Immutable") // Test only
  public void lazyEval_blockIndexEvaluatedOnlyOnce() throws Exception {
    AtomicInteger invocation = new AtomicInteger();
    CelRuntime celRuntime =
        CelRuntimeFactory.standardCelRuntimeBuilder()
            .addMessageTypes(TestAllTypes.getDescriptor())
            .addFunctionBindings(
                CelFunctionBinding.from(
                    "get_true_overload",
                    ImmutableList.of(),
                    arg -> {
                      invocation.getAndIncrement();
                      return true;
                    }))
            .build();
    CelAbstractSyntaxTree ast =
        compileUsingInternalFunctions("cel.block([get_true()], index0 && index0 && index0)");

    boolean result = (boolean) celRuntime.createProgram(ast).eval();

    assertThat(result).isTrue();
    assertThat(invocation.get()).isEqualTo(1);
  }

  @Test
  @SuppressWarnings("Immutable") // Test only
  public void lazyEval_multipleBlockIndices_inResultExpr() throws Exception {
    AtomicInteger invocation = new AtomicInteger();
    CelRuntime celRuntime =
        CelRuntimeFactory.standardCelRuntimeBuilder()
            .addMessageTypes(TestAllTypes.getDescriptor())
            .addFunctionBindings(
                CelFunctionBinding.from(
                    "get_true_overload",
                    ImmutableList.of(),
                    arg -> {
                      invocation.getAndIncrement();
                      return true;
                    }))
            .build();
    CelAbstractSyntaxTree ast =
        compileUsingInternalFunctions("cel.block([get_true(), get_true(), get_true()], index0 && index0 && index1 && index1 && index2 && index2)");

    boolean result = (boolean) celRuntime.createProgram(ast).eval();

    assertThat(result).isTrue();
    assertThat(invocation.get()).isEqualTo(3);
  }

  @Test
  @SuppressWarnings("Immutable") // Test only
  public void lazyEval_multipleBlockIndices_cascaded() throws Exception {
    AtomicInteger invocation = new AtomicInteger();
    CelRuntime celRuntime =
        CelRuntimeFactory.standardCelRuntimeBuilder()
            .addMessageTypes(TestAllTypes.getDescriptor())
            .addFunctionBindings(
                CelFunctionBinding.from(
                    "get_true_overload",
                    ImmutableList.of(),
                    arg -> {
                      invocation.getAndIncrement();
                      return true;
                    }))
            .build();
    CelAbstractSyntaxTree ast =
        compileUsingInternalFunctions("cel.block([get_true(), index0, index1], index2)");

    boolean result = (boolean) celRuntime.createProgram(ast).eval();

    assertThat(result).isTrue();
    assertThat(invocation.get()).isEqualTo(1);
  }

  @Test
  @TestParameters("{source: 'cel.block([])'}")
  @TestParameters("{source: 'cel.block([1])'}")
  @TestParameters("{source: 'cel.block(1, 2)'}")
  @TestParameters("{source: 'cel.block(1, [1])'}")
  public void block_invalidArguments_throws(String source) {
    CelValidationException e = assertThrows(CelValidationException.class, () -> compileUsingInternalFunctions(source));

    assertThat(e).hasMessageThat().contains("found no matching overload for 'cel.block'");
  }

  @Test
  public void blockIndex_invalidArgument_throws() {
    CelValidationException e = assertThrows(CelValidationException.class, () -> compileUsingInternalFunctions("cel.block([1], index)"));

    assertThat(e).hasMessageThat().contains("undeclared reference");
  }

  // @Test
  // public void recursive() throws Exception {
  //   CelAbstractSyntaxTree ast = compileUsingInternalFunctions("cel.block([index1, true, false], index0 || index1)");
  //   CelRuntime.Program program = CEL.createProgram(ast);
  //
  //   boolean evaluatedResult = (boolean) program.eval();
  //
  //   assertThat(evaluatedResult).isTrue();
  // }

  /**
   * Converts AST containing cel.block related test functions to internal functions (e.g: cel.block -> cel.@block)
   */
  private static CelAbstractSyntaxTree compileUsingInternalFunctions(String expression)
      throws CelValidationException {
    MutableAst mutableAst = MutableAst.newInstance(1000);
    CelAbstractSyntaxTree astToModify = CEL.compile(expression).getAst();
    while (true) {
      CelExpr celExpr = CelNavigableAst.fromAst(astToModify)
          .getRoot()
          .allNodes()
          .filter(node -> node.getKind().equals(Kind.CALL))
          .map(CelNavigableExpr::expr)
          .filter(expr -> expr.call().function().equals("cel.block"))
          .findAny()
          .orElse(null);
      if (celExpr == null) {
        break;
      }
      astToModify = mutableAst.replaceSubtree(astToModify,
          celExpr.toBuilder().setCall(
              celExpr.call().toBuilder().setFunction("cel.@block").build()).build(),
          celExpr.id());
    }

    while (true) {
      CelExpr celExpr = CelNavigableAst.fromAst(astToModify)
          .getRoot()
          .allNodes()
          .filter(node -> node.getKind().equals(Kind.IDENT))
          .map(CelNavigableExpr::expr)
          .filter(expr -> expr.ident().name().startsWith("index"))
          .findAny()
          .orElse(null);
      if (celExpr == null) {
        break;
      }
      String internalIdentName = "@" + celExpr.ident().name();
      astToModify = mutableAst.replaceSubtree(astToModify,
          celExpr.toBuilder().setIdent(
              celExpr.ident().toBuilder().setName(internalIdentName).build())
          .build(),
          celExpr.id());
    }

    return CEL.check(astToModify).getAst();
  }
}
