// Copyright 2024 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      https://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package dev.cel.optimizer.optimizers;

import static com.google.common.truth.Truth.assertThat;
import static dev.cel.common.CelOverloadDecl.newGlobalOverload;
import static org.junit.Assert.assertThrows;

import com.google.common.base.VerifyException;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.testing.junit.testparameterinjector.TestParameter;
import com.google.testing.junit.testparameterinjector.TestParameterInjector;
import com.google.testing.junit.testparameterinjector.TestParameters;
import dev.cel.bundle.Cel;
import dev.cel.bundle.CelBuilder;
import dev.cel.bundle.CelFactory;
import dev.cel.common.CelAbstractSyntaxTree;
import dev.cel.common.CelFunctionDecl;
import dev.cel.common.CelOptions;
import dev.cel.common.CelOverloadDecl;
import dev.cel.common.CelSource.Extension;
import dev.cel.common.CelSource.Extension.Component;
import dev.cel.common.CelSource.Extension.Version;
import dev.cel.common.CelValidationException;
import dev.cel.common.CelVarDecl;
import dev.cel.common.ast.CelConstant;
import dev.cel.common.ast.CelExpr;
import dev.cel.common.ast.CelExpr.ExprKind.Kind;
import dev.cel.common.ast.MutableAst;
import dev.cel.common.ast.MutableExpr;
import dev.cel.common.navigation.CelNavigableAst;
import dev.cel.common.navigation.CelNavigableExpr;
import dev.cel.common.types.ListType;
import dev.cel.common.types.OptionalType;
import dev.cel.common.types.SimpleType;
import dev.cel.common.types.StructTypeReference;
import dev.cel.extensions.CelExtensions;
import dev.cel.extensions.CelOptionalLibrary;
import dev.cel.optimizer.AstMutator;
import dev.cel.optimizer.CelOptimizationException;
import dev.cel.optimizer.CelOptimizer;
import dev.cel.optimizer.CelOptimizerFactory;
import dev.cel.optimizer.optimizers.SubexpressionOptimizer.SubexpressionOptimizerOptions;
import dev.cel.parser.CelStandardMacro;
import dev.cel.parser.CelUnparser;
import dev.cel.parser.CelUnparserFactory;
import dev.cel.parser.Operator;
import dev.cel.runtime.CelRuntime;
import dev.cel.runtime.CelRuntime.CelFunctionBinding;
import dev.cel.runtime.CelRuntimeFactory;
import dev.cel.testing.testdata.proto3.TestAllTypesProto.TestAllTypes;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(TestParameterInjector.class)
public class SubexpressionOptimizerTest {

  private static final Cel CEL = newCelBuilder().build();

  private static final Cel CEL_FOR_EVALUATING_BLOCK =
      CelFactory.standardCelBuilder()
          .setStandardMacros(CelStandardMacro.STANDARD_MACROS)
          .addFunctionDeclarations(
              // These are test only declarations, as the actual function is made internal using @
              // symbol.
              // If the main function declaration needs updating, be sure to update the test
              // declaration as well.
              CelFunctionDecl.newFunctionDeclaration(
                  "cel.block",
                  CelOverloadDecl.newGlobalOverload(
                      "block_test_only_overload",
                      SimpleType.DYN,
                      ListType.create(SimpleType.DYN),
                      SimpleType.DYN)),
              SubexpressionOptimizer.newCelBlockFunctionDecl(SimpleType.DYN),
              CelFunctionDecl.newFunctionDeclaration(
                  "get_true",
                  CelOverloadDecl.newGlobalOverload("get_true_overload", SimpleType.BOOL)))
          // Similarly, this is a test only decl (index0 -> @index0)
          .addVarDeclarations(
              CelVarDecl.newVarDeclaration("c0", SimpleType.DYN),
              CelVarDecl.newVarDeclaration("c1", SimpleType.DYN),
              CelVarDecl.newVarDeclaration("index0", SimpleType.DYN),
              CelVarDecl.newVarDeclaration("index1", SimpleType.DYN),
              CelVarDecl.newVarDeclaration("index2", SimpleType.DYN),
              CelVarDecl.newVarDeclaration("@index0", SimpleType.DYN),
              CelVarDecl.newVarDeclaration("@index1", SimpleType.DYN),
              CelVarDecl.newVarDeclaration("@index2", SimpleType.DYN))
          .addMessageTypes(TestAllTypes.getDescriptor())
          .addVar("msg", StructTypeReference.create(TestAllTypes.getDescriptor().getFullName()))
          .build();

  private static final CelUnparser CEL_UNPARSER = CelUnparserFactory.newUnparser();

  private static CelBuilder newCelBuilder() {
    return CelFactory.standardCelBuilder()
        .addMessageTypes(TestAllTypes.getDescriptor())
        .setStandardMacros(CelStandardMacro.STANDARD_MACROS)
        .addCompilerLibraries(CelOptionalLibrary.INSTANCE)
        .addRuntimeLibraries(CelOptionalLibrary.INSTANCE)
        .setOptions(
            CelOptions.current().enableTimestampEpoch(true).populateMacroCalls(true).build())
        .addCompilerLibraries(CelExtensions.bindings())
        .addFunctionDeclarations(
            CelFunctionDecl.newFunctionDeclaration(
                "non_pure_custom_func",
                newGlobalOverload("non_pure_custom_func_overload", SimpleType.INT, SimpleType.INT)))
        .addVar("x", SimpleType.DYN)
        .addVar("opt_x", OptionalType.create(SimpleType.DYN))
        .addVar("msg", StructTypeReference.create(TestAllTypes.getDescriptor().getFullName()));
  }

  private static CelOptimizer newCseOptimizer(SubexpressionOptimizerOptions options) {
    return CelOptimizerFactory.standardCelOptimizerBuilder(CEL)
        .addAstOptimizers(SubexpressionOptimizer.newInstance(options))
        .build();
  }

  @Test
  public void cse_resultTypeSet_celBlockOptimizationSuccess() throws Exception {
    Cel cel = newCelBuilder().setResultType(SimpleType.BOOL).build();
    CelOptimizer celOptimizer =
        CelOptimizerFactory.standardCelOptimizerBuilder(cel)
            .addAstOptimizers(
                SubexpressionOptimizer.newInstance(
                    SubexpressionOptimizerOptions.newBuilder().enableCelBlock(true).build()))
            .build();
    CelAbstractSyntaxTree ast = CEL.compile("size('a') + size('a') == 2").getAst();

    CelAbstractSyntaxTree optimizedAst = celOptimizer.optimize(ast);

    assertThat(CEL.createProgram(optimizedAst).eval()).isEqualTo(true);
    assertThat(CEL_UNPARSER.unparse(optimizedAst))
        .isEqualTo("cel.@block([size(\"a\")], @index0 + @index0 == 2)");
  }

  private enum CseNoOpTestCase {
    // Nothing to optimize
    NO_COMMON_SUBEXPR("size(\"hello\")"),
    // Constants and identifiers
    INT_CONST_ONLY("2 + 2 + 2 + 2"),
    IDENT_ONLY("x + x + x + x"),
    BOOL_CONST_ONLY("true == true && false == false"),
    // Constants and identifiers within a function
    CONST_WITHIN_FUNCTION("size(\"hello\" + \"hello\" + \"hello\")"),
    IDENT_WITHIN_FUNCTION("string(x + x + x)"),
    // Non-standard functions that have not been explicitly added as a candidate are not
    // optimized.
    NON_STANDARD_FUNCTION_1("non_pure_custom_func(1) + non_pure_custom_func(1)"),
    NON_STANDARD_FUNCTION_2("1 + non_pure_custom_func(1) + 1 + non_pure_custom_func(1)"),
    // Duplicated but nested calls.
    NESTED_FUNCTION("int(timestamp(int(timestamp(1000000000))))"),
    // This cannot be optimized. Extracting the common subexpression would presence test
    // the bound identifier (e.g: has(@r0)), which is not valid.
    UNOPTIMIZABLE_TERNARY("has(msg.single_any) ? msg.single_any : 10");

    private final String source;

    CseNoOpTestCase(String source) {
      this.source = source;
    }
  }

  @Test
  public void cse_withCelBind_noop(@TestParameter CseNoOpTestCase testCase) throws Exception {
    CelAbstractSyntaxTree ast = CEL.compile(testCase.source).getAst();

    CelAbstractSyntaxTree optimizedAst =
        newCseOptimizer(
                SubexpressionOptimizerOptions.newBuilder()
                    .populateMacroCalls(true)
                    .enableCelBlock(false)
                    .build())
            .optimize(ast);

    assertThat(ast.getExpr()).isEqualTo(optimizedAst.getExpr());
    assertThat(CEL_UNPARSER.unparse(optimizedAst)).isEqualTo(testCase.source);
  }

  @Test
  public void cse_withCelBlock_noop(@TestParameter CseNoOpTestCase testCase) throws Exception {
    CelAbstractSyntaxTree ast = CEL.compile(testCase.source).getAst();

    CelAbstractSyntaxTree optimizedAst =
        newCseOptimizer(
                SubexpressionOptimizerOptions.newBuilder()
                    .populateMacroCalls(true)
                    .enableCelBlock(true)
                    .build())
            .optimize(ast);

    assertThat(ast.getExpr()).isEqualTo(optimizedAst.getExpr());
    assertThat(CEL_UNPARSER.unparse(optimizedAst)).isEqualTo(testCase.source);
  }

  @Test
  public void cse_applyConstFoldingAfter() throws Exception {
    CelAbstractSyntaxTree ast =
        CEL.compile("size([1+1+1]) + size([1+1+1]) + size([1,1+1+1]) + size([1,1+1+1]) + x")
            .getAst();
    CelOptimizer optimizer =
        CelOptimizerFactory.standardCelOptimizerBuilder(CEL)
            .addAstOptimizers(
                SubexpressionOptimizer.newInstance(
                    SubexpressionOptimizerOptions.newBuilder().build()),
                ConstantFoldingOptimizer.getInstance())
            .build();

    CelAbstractSyntaxTree optimizedAst = optimizer.optimize(ast);

    assertThat(optimizedAst.getExpr())
        .isEqualTo(
            CelExpr.ofCallExpr(
                1L,
                Optional.empty(),
                Operator.ADD.getFunction(),
                ImmutableList.of(
                    CelExpr.ofConstantExpr(2L, CelConstant.ofValue(6L)),
                    CelExpr.ofIdentExpr(3L, "x"))));
    assertThat(CEL_UNPARSER.unparse(optimizedAst)).isEqualTo("6 + x");
  }

  @Test
  @TestParameters("{enableCelBlock: false, unparsed: 'cel.bind(@r0, size(x), @r0 + @r0)'}")
  @TestParameters("{enableCelBlock: true, unparsed: 'cel.@block([size(x)], @index0 + @index0)'}")
  public void cse_applyConstFoldingAfter_nothingToFold(boolean enableCelBlock, String unparsed)
      throws Exception {
    CelAbstractSyntaxTree ast = CEL.compile("size(x) + size(x)").getAst();
    CelOptimizer optimizer =
        CelOptimizerFactory.standardCelOptimizerBuilder(CEL)
            .addAstOptimizers(
                SubexpressionOptimizer.newInstance(
                    SubexpressionOptimizerOptions.newBuilder()
                        .populateMacroCalls(true)
                        .enableCelBlock(enableCelBlock)
                        .build()),
                ConstantFoldingOptimizer.getInstance())
            .build();

    CelAbstractSyntaxTree optimizedAst = optimizer.optimize(ast);

    assertThat(CEL_UNPARSER.unparse(optimizedAst)).isEqualTo(unparsed);
  }

  @Test
  @TestParameters("{enableCelBlock: false}")
  @TestParameters("{enableCelBlock: true}")
  public void iterationLimitReached_throws(boolean enableCelBlock) throws Exception {
    StringBuilder largeExprBuilder = new StringBuilder();
    int iterationLimit = 100;
    for (int i = 0; i < iterationLimit; i++) {
      largeExprBuilder.append("[1,2]");
      if (i < iterationLimit - 1) {
        largeExprBuilder.append("+");
      }
    }
    CelAbstractSyntaxTree ast = CEL.compile(largeExprBuilder.toString()).getAst();

    CelOptimizationException e =
        assertThrows(
            CelOptimizationException.class,
            () ->
                newCseOptimizer(
                        SubexpressionOptimizerOptions.newBuilder()
                            .iterationLimit(iterationLimit)
                            .enableCelBlock(enableCelBlock)
                            .build())
                    .optimize(ast));
    assertThat(e).hasMessageThat().isEqualTo("Optimization failure: Max iteration count reached.");
  }

  @Test
  public void celBlock_astExtensionTagged() throws Exception {
    CelAbstractSyntaxTree ast = CEL.compile("size(x) + size(x)").getAst();
    CelOptimizer optimizer =
        CelOptimizerFactory.standardCelOptimizerBuilder(CEL)
            .addAstOptimizers(
                SubexpressionOptimizer.newInstance(
                    SubexpressionOptimizerOptions.newBuilder()
                        .populateMacroCalls(true)
                        .enableCelBlock(true)
                        .build()),
                ConstantFoldingOptimizer.getInstance())
            .build();

    CelAbstractSyntaxTree optimizedAst = optimizer.optimize(ast);

    assertThat(optimizedAst.getSource().getExtensions())
        .containsExactly(
            Extension.create("cel_block", Version.of(1L, 1L), Component.COMPONENT_RUNTIME));
  }

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

    Object evaluatedResult = CEL_FOR_EVALUATING_BLOCK.createProgram(ast).eval();

    assertThat(evaluatedResult).isNotNull();
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
        compileUsingInternalFunctions(
            "cel.block([get_true()], has(msg.single_int64) ? index0 : false)");

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
        compileUsingInternalFunctions(
            "cel.block([get_true(), get_true(), get_true()], index0 && index0 && index1 && index1"
                + " && index2 && index2)");

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
  @SuppressWarnings("Immutable") // Test only
  public void lazyEval_nestedComprehension_indexReferencedInNestedScopes() throws Exception {
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
    // Equivalent of [true, false, true].map(c0, [c0].map(c1, [c0, c1, true]))
    CelAbstractSyntaxTree ast =
        compileUsingInternalFunctions(
            "cel.block([c0, c1, get_true()], [index2, false, index2].map(c0, [c0].map(c1, [index0,"
                + " index1, index2]))) == [[[true, true, true]], [[false, false, true]], [[true,"
                + " true, true]]]");

    boolean result = (boolean) celRuntime.createProgram(ast).eval();

    assertThat(result).isTrue();
    // Even though the function get_true() is referenced across different comprehension scopes,
    // it still gets memoized only once.
    assertThat(invocation.get()).isEqualTo(1);
  }

  @Test
  @TestParameters("{source: 'cel.block([])'}")
  @TestParameters("{source: 'cel.block([1])'}")
  @TestParameters("{source: 'cel.block(1, 2)'}")
  @TestParameters("{source: 'cel.block(1, [1])'}")
  public void block_invalidArguments_throws(String source) {
    CelValidationException e =
        assertThrows(CelValidationException.class, () -> compileUsingInternalFunctions(source));

    assertThat(e).hasMessageThat().contains("found no matching overload for 'cel.block'");
  }

  @Test
  public void blockIndex_invalidArgument_throws() {
    CelValidationException e =
        assertThrows(
            CelValidationException.class,
            () -> compileUsingInternalFunctions("cel.block([1], index)"));

    assertThat(e).hasMessageThat().contains("undeclared reference");
  }

  @Test
  public void verifyOptimizedAstCorrectness_twoCelBlocks_throws() throws Exception {
    CelAbstractSyntaxTree ast =
        compileUsingInternalFunctions("cel.block([1, 2], cel.block([2], 3))");

    VerifyException e =
        assertThrows(
            VerifyException.class, () -> SubexpressionOptimizer.verifyOptimizedAstCorrectness(ast));
    assertThat(e)
        .hasMessageThat()
        .isEqualTo("Expected 1 cel.block function to be present but found 2");
  }

  @Test
  public void verifyOptimizedAstCorrectness_celBlockNotAtRoot_throws() throws Exception {
    CelAbstractSyntaxTree ast = compileUsingInternalFunctions("1 + cel.block([1, 2], index0)");

    VerifyException e =
        assertThrows(
            VerifyException.class, () -> SubexpressionOptimizer.verifyOptimizedAstCorrectness(ast));
    assertThat(e).hasMessageThat().isEqualTo("Expected cel.block to be present at root");
  }

  @Test
  public void verifyOptimizedAstCorrectness_blockContainsNoIndexResult_throws() throws Exception {
    CelAbstractSyntaxTree ast = compileUsingInternalFunctions("cel.block([1, index0], 2)");

    VerifyException e =
        assertThrows(
            VerifyException.class, () -> SubexpressionOptimizer.verifyOptimizedAstCorrectness(ast));
    assertThat(e)
        .hasMessageThat()
        .isEqualTo("Expected at least one reference of index in cel.block result");
  }

  @Test
  @TestParameters("{source: 'cel.block([], index0)'}")
  @TestParameters("{source: 'cel.block([1, 2], index2)'}")
  public void verifyOptimizedAstCorrectness_indexOutOfBounds_throws(String source)
      throws Exception {
    CelAbstractSyntaxTree ast = compileUsingInternalFunctions(source);

    VerifyException e =
        assertThrows(
            VerifyException.class, () -> SubexpressionOptimizer.verifyOptimizedAstCorrectness(ast));
    assertThat(e)
        .hasMessageThat()
        .contains("Illegal block index found. The index value must be less than");
  }

  @Test
  public void smokeTest() throws Exception {
//    String expression = "size([1,2]) + size([1,2]) + 1 == 5";
    String expression = "has(msg.single_any) && has(msg.single_any.test)" ;
    Cel cel = newCelBuilder().build();
    CelOptimizer celOptimizer =
            CelOptimizerFactory.standardCelOptimizerBuilder(cel)
                    .addAstOptimizers(
                            SubexpressionOptimizer.newInstance(
                                    SubexpressionOptimizerOptions.newBuilder()
                                            .populateMacroCalls(true)
//                                            .subexpressionMaxRecursionDepth(2)
                                            .enableCelBlock(true).build()))
                    .build();
    CelAbstractSyntaxTree ast = CEL.compile(expression).getAst();

    CelAbstractSyntaxTree optimizedAst = celOptimizer.optimize(ast);

//    assertThat(CEL.createProgram(optimizedAst).eval()).isEqualTo(true);
    assertThat(CEL_UNPARSER.unparse(optimizedAst))
            .isEqualTo("cel.@block([{\"e\": {\"b\": 1}}, {\"b\": 1}], {\"a\": @index1, \"c\": @index1, \"d\": @index0, \"e\": @index0})");
  }

  @Test
  @TestParameters("{source: 'cel.block([index0], index0)'}")
  @TestParameters("{source: 'cel.block([1, index1, 2], index2)'}")
  @TestParameters("{source: 'cel.block([1, 2, index2], index2)'}")
  @TestParameters("{source: 'cel.block([index2, 1, 2], index2)'}")
  public void verifyOptimizedAstCorrectness_indexIsNotForwardReferencing_throws(String source)
      throws Exception {
    CelAbstractSyntaxTree ast = compileUsingInternalFunctions(source);

    VerifyException e =
        assertThrows(
            VerifyException.class, () -> SubexpressionOptimizer.verifyOptimizedAstCorrectness(ast));
    assertThat(e)
        .hasMessageThat()
        .contains("Illegal block index found. The index value must be less than");
  }

  /**
   * Converts AST containing cel.block related test functions to internal functions (e.g: cel.block
   * -> cel.@block)
   */
  private static CelAbstractSyntaxTree compileUsingInternalFunctions(String expression)
      throws CelValidationException {
    AstMutator astMutator = AstMutator.newInstance(1000);
    CelAbstractSyntaxTree astToModify = CEL_FOR_EVALUATING_BLOCK.compile(expression).getAst();
    MutableAst mutableAst = MutableAst.fromCelAst(astToModify);
    while (true) {
      MutableExpr celBlockExpr =
          CelNavigableAst.fromMutableAst(mutableAst)
              .getRoot()
              .allNodes()
              .filter(node -> node.getKind().equals(Kind.CALL))
              .map(CelNavigableExpr::mutableExpr)
              .filter(expr -> expr.call().function().equals("cel.block"))
              .findAny()
              .orElse(null);
      if (celBlockExpr == null) {
        break;
      }

      celBlockExpr.call().setFunction("cel.@block");
      // astToModify =
      //     astMutator.replaceSubtree(
      //         mutableAst.mutableExpr(),
      //         celBlockExpr.toBuilder()
      //             .setCall(celBlockExpr.call().toBuilder().setFunction("cel.@block").build())
      //             .build(),
      //         celBlockExpr.id(),
      //         mutableAst.source(),
      //         );
    }

    while (true) {
      MutableExpr indexExpr =
          CelNavigableAst.fromMutableAst(mutableAst)
              .getRoot()
              .allNodes()
              .filter(node -> node.getKind().equals(Kind.IDENT))
              .map(CelNavigableExpr::mutableExpr)
              .filter(expr -> expr.ident().name().startsWith("index"))
              .findAny()
              .orElse(null);
      if (indexExpr == null) {
        break;
      }
      String internalIdentName = "@" + indexExpr.ident().name();
      indexExpr.ident().setName(internalIdentName);
      // astToModify =
      //     astMutator.replaceSubtree(
      //         astToModify,
      //         indexExpr.toBuilder()
      //             .setIdent(indexExpr.ident().toBuilder().setName(internalIdentName).build())
      //             .build(),
      //         indexExpr.id());
    }

    return CEL_FOR_EVALUATING_BLOCK.check(mutableAst.toParsedAst()).getAst();
  }
}
