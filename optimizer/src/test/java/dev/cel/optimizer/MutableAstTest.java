// Copyright 2023 Google LLC
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

package dev.cel.optimizer;

import static com.google.common.collect.ImmutableMap.toImmutableMap;
import static com.google.common.truth.Truth.assertThat;
import static dev.cel.optimizer.MutableAst.newInstance;
import static org.junit.Assert.assertThrows;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.testing.junit.testparameterinjector.TestParameterInjector;
import com.google.testing.junit.testparameterinjector.TestParameters;
import dev.cel.bundle.Cel;
import dev.cel.bundle.CelFactory;
import dev.cel.common.CelAbstractSyntaxTree;
import dev.cel.common.CelFunctionDecl;
import dev.cel.common.CelOptions;
import dev.cel.common.CelOverloadDecl;
import dev.cel.common.CelSource;
import dev.cel.common.CelSource.Extension;
import dev.cel.common.CelSource.Extension.Version;
import dev.cel.common.ast.CelConstant;
import dev.cel.common.ast.CelExpr;
import dev.cel.common.ast.CelExpr.CelCall;
import dev.cel.common.ast.CelExpr.CelIdent;
import dev.cel.common.ast.CelExpr.ExprKind;
import dev.cel.common.ast.CelExpr.ExprKind.Kind;
import dev.cel.common.navigation.CelNavigableAst;
import dev.cel.common.navigation.CelNavigableExpr;
import dev.cel.common.navigation.MutableExpr;
import dev.cel.common.navigation.MutableExpr.MutableConstant;
import dev.cel.common.navigation.MutableExpr.MutableSelect;
import dev.cel.common.navigation.MutableExprConverter;
import dev.cel.common.types.SimpleType;
import dev.cel.common.types.StructTypeReference;
import dev.cel.extensions.CelExtensions;
import dev.cel.extensions.CelOptionalLibrary;
import dev.cel.optimizer.MutableAst.MutatedResult;
import dev.cel.parser.CelStandardMacro;
import dev.cel.parser.CelUnparser;
import dev.cel.parser.CelUnparserFactory;
import dev.cel.parser.Operator;
import dev.cel.testing.testdata.proto3.TestAllTypesProto.TestAllTypes;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(TestParameterInjector.class)
public class MutableAstTest {
  private static final Cel CEL =
      CelFactory.standardCelBuilder()
          .setStandardMacros(CelStandardMacro.STANDARD_MACROS)
          .setOptions(CelOptions.current().populateMacroCalls(true).build())
          .addMessageTypes(TestAllTypes.getDescriptor())
          .addCompilerLibraries(CelOptionalLibrary.INSTANCE, CelExtensions.bindings())
          .addRuntimeLibraries(CelOptionalLibrary.INSTANCE)
          .setContainer("dev.cel.testing.testdata.proto3")
          .addVar("msg", StructTypeReference.create(TestAllTypes.getDescriptor().getFullName()))
          .addVar("x", SimpleType.INT)
          .build();

  private static final CelUnparser CEL_UNPARSER = CelUnparserFactory.newUnparser();
  private static final MutableAst MUTABLE_AST = MutableAst.newInstance(1000);

  @Test
  public void replaceSubtree_replaceConst() throws Exception {
    CelAbstractSyntaxTree ast = CEL.compile("10").getAst();
    MutableExpr root = MutableExprConverter.fromCelExpr(ast.getExpr());
    MutableExpr newBooleanConst = MutableExpr.ofConstant(1, MutableConstant.ofValue(true));

    MutatedResult result = MUTABLE_AST.replaceSubtree(root, newBooleanConst, root.id());

    assertThat(result.toParsedAst().getExpr()).isEqualTo(CelExpr.ofConstantExpr(3, CelConstant.ofValue(true)));
  }

  @Test
  public void mutableAst_returnsParsedAst() throws Exception {
    CelAbstractSyntaxTree ast = CEL.compile("10").getAst();
    MutableExpr root = MutableExprConverter.fromCelExpr(ast.getExpr());
    MutableExpr newBooleanConst = MutableExpr.ofConstant(1, MutableConstant.ofValue(true));

    MutatedResult result = MUTABLE_AST.replaceSubtree(root, newBooleanConst, root.id());

    assertThat(ast.isChecked()).isTrue();
    assertThat(result.toParsedAst().isChecked()).isFalse();
  }

  @Test
  public void mutableAst_nonMacro_sourceCleared() throws Exception {
    CelAbstractSyntaxTree ast = CEL.compile("10").getAst();
    MutableExpr root = MutableExprConverter.fromCelExpr(ast.getExpr());
    MutableExpr newBooleanConst = MutableExpr.ofConstant(1, MutableConstant.ofValue(true));

    CelAbstractSyntaxTree mutatedAst =
        MUTABLE_AST.replaceSubtree(root, newBooleanConst, root.id()).toParsedAst();

    assertThat(mutatedAst.getSource().getDescription()).isEmpty();
    assertThat(mutatedAst.getSource().getLineOffsets()).isEmpty();
    assertThat(mutatedAst.getSource().getPositionsMap()).isEmpty();
    assertThat(mutatedAst.getSource().getExtensions()).isEmpty();
    assertThat(mutatedAst.getSource().getMacroCalls()).isEmpty();
  }

  @Test
  public void mutableAst_macro_sourceMacroCallsPopulated() throws Exception {
    CelAbstractSyntaxTree ast = CEL.compile("has(TestAllTypes{}.single_int32)").getAst();
    MutableExpr root = MutableExprConverter.fromCelExpr(ast.getExpr());
    MutableExpr newBooleanConst = MutableExpr.ofConstant(1, MutableConstant.ofValue(true));

    CelAbstractSyntaxTree mutatedAst = MUTABLE_AST.replaceSubtree(root, newBooleanConst, -1, ast.getSource().toBuilder()).toParsedAst(); // no_op

    assertThat(mutatedAst.getSource().getDescription()).isEmpty();
    assertThat(mutatedAst.getSource().getLineOffsets()).isEmpty();
    assertThat(mutatedAst.getSource().getPositionsMap()).isEmpty();
    assertThat(mutatedAst.getSource().getExtensions()).isEmpty();
    assertThat(mutatedAst.getSource().getMacroCalls()).isNotEmpty();
  }

  @Test
  public void replaceSubtree_astContainsTaggedExtension_retained() throws Exception {
    CelAbstractSyntaxTree ast = CEL.compile("has(TestAllTypes{}.single_int32)").getAst();
    Extension extension = Extension.create("test", Version.of(1, 1));
    CelSource celSource = ast.getSource().toBuilder().addAllExtensions(extension).build();
    ast =
        CelAbstractSyntaxTree.newCheckedAst(
            ast.getExpr(), celSource, ast.getReferenceMap(), ast.getTypeMap());

    CelAbstractSyntaxTree mutatedAst =
        MUTABLE_AST.replaceSubtree(
            ast, CelExpr.newBuilder().setConstant(CelConstant.ofValue(true)).build(), 1);

    assertThat(mutatedAst.getSource().getExtensions()).containsExactly(extension);
  }

  @Test
  public void replaceSubtreeWithNewAst_astsContainTaggedExtension_retained() throws Exception {
//    // Setup first AST with a test extension
//    CelAbstractSyntaxTree ast = CEL.compile("has(TestAllTypes{}.single_int32)").getAst();
//    Extension extension = Extension.create("test", Version.of(1, 1));
//    ast =
//        CelAbstractSyntaxTree.newCheckedAst(
//            ast.getExpr(),
//            ast.getSource().toBuilder().addAllExtensions(extension).build(),
//            ast.getReferenceMap(),
//            ast.getTypeMap());
//    // Setup second AST with another test extension
//    CelAbstractSyntaxTree astToReplaceWith = CEL.compile("cel.bind(a, true, a)").getAst();
//    Extension extension2 = Extension.create("test2", Version.of(2, 2));
//    astToReplaceWith =
//        CelAbstractSyntaxTree.newCheckedAst(
//            astToReplaceWith.getExpr(),
//            astToReplaceWith.getSource().toBuilder().addAllExtensions(extension2).build(),
//            astToReplaceWith.getReferenceMap(),
//            astToReplaceWith.getTypeMap());
//
//    // Mutate the original AST with the new AST at the root
//    CelAbstractSyntaxTree mutatedAst =
//        MUTABLE_AST.replaceSubtreeWithNewAst(ast, astToReplaceWith, ast.getExpr().id());
//
//    // Expect that both the extensions are merged
//    assertThat(mutatedAst.getSource().getExtensions()).containsExactly(extension, extension2);
  }

  @Test
  public void replaceSubtreeWithNewAst_astsContainSameExtensions_deduped() throws Exception {
//    // Setup first AST with a test extension
//    CelAbstractSyntaxTree ast = CEL.compile("has(TestAllTypes{}.single_int32)").getAst();
//    Extension extension = Extension.create("test", Version.of(1, 1));
//    ast =
//        CelAbstractSyntaxTree.newCheckedAst(
//            ast.getExpr(),
//            ast.getSource().toBuilder().addAllExtensions(extension).build(),
//            ast.getReferenceMap(),
//            ast.getTypeMap());
//    // Setup second AST with the same test extension as above
//    CelAbstractSyntaxTree astToReplaceWith = CEL.compile("cel.bind(a, true, a)").getAst();
//    Extension extension2 = Extension.create("test", Version.of(1, 1));
//    astToReplaceWith =
//        CelAbstractSyntaxTree.newCheckedAst(
//            astToReplaceWith.getExpr(),
//            astToReplaceWith.getSource().toBuilder().addAllExtensions(extension2).build(),
//            astToReplaceWith.getReferenceMap(),
//            astToReplaceWith.getTypeMap());
//
//    // Mutate the original AST with the new AST at the root
//    CelAbstractSyntaxTree mutatedAst =
//        MUTABLE_AST.replaceSubtreeWithNewAst(ast, astToReplaceWith, ast.getExpr().id());
//
//    // Expect that the extension is deduped
//    assertThat(mutatedAst.getSource().getExtensions()).containsExactly(extension);
  }

  @Test
  @TestParameters("{source: '[1].exists(x, x > 0)', expectedMacroCallSize: 1}")
  @TestParameters(
      "{source: '[1].exists(x, x > 0) && [2].exists(x, x > 0)', expectedMacroCallSize: 2}")
  @TestParameters(
      "{source: '[1].exists(x, [2].exists(y, x > 0 && y > x))', expectedMacroCallSize: 2}")
  public void replaceSubtree_rootReplacedWithMacro_macroCallPopulated(
      String source, int expectedMacroCallSize) throws Exception {
//    CelAbstractSyntaxTree ast = CEL.compile("1").getAst();
//    CelAbstractSyntaxTree ast2 = CEL.compile(source).getAst();
//
//    CelAbstractSyntaxTree mutatedAst =
//        MUTABLE_AST.replaceSubtreeWithNewAst(
//            ast, ast2, CelNavigableAst.fromAst(ast).getRoot().id());
//
//    assertThat(mutatedAst.getSource().getMacroCalls()).hasSize(expectedMacroCallSize);
//    assertThat(CEL_UNPARSER.unparse(mutatedAst)).isEqualTo(source);
//    assertThat(CEL.createProgram(CEL.check(mutatedAst).getAst()).eval()).isEqualTo(true);
  }

  @Test
  public void replaceSubtree_leftBranchReplacedWithMacro_macroCallPopulated() throws Exception {
    CelAbstractSyntaxTree ast = CEL.compile("true && false").getAst();
    CelAbstractSyntaxTree ast2 = CEL.compile("[1].exists(x, x > 0)").getAst();
    MutableExpr root1 = MutableExprConverter.fromCelExpr(ast.getExpr());
    MutableExpr root2 = MutableExprConverter.fromCelExpr(ast2.getExpr());

    CelAbstractSyntaxTree mutatedAst =
        MUTABLE_AST.replaceSubtree(root1, root2, 3, ast.getSource().toBuilder(), ast2.getSource().toBuilder()).toParsedAst(); // Replace false with the macro expr

    assertThat(mutatedAst.getSource().getMacroCalls()).hasSize(1);
    assertThat(CEL_UNPARSER.unparse(mutatedAst)).isEqualTo("true && [1].exists(x, x > 0)");
    assertThat(CEL.createProgram(CEL.check(mutatedAst).getAst()).eval()).isEqualTo(true);
  }

  @Test
  public void replaceSubtree_rightBranchReplacedWithMacro_macroCallPopulated() throws Exception {
    CelAbstractSyntaxTree ast = CEL.compile("true && false").getAst();
    CelAbstractSyntaxTree ast2 = CEL.compile("[1].exists(x, x > 0)").getAst();
    MutableExpr root1 = MutableExprConverter.fromCelExpr(ast.getExpr());
    MutableExpr root2 = MutableExprConverter.fromCelExpr(ast2.getExpr());

    CelAbstractSyntaxTree mutatedAst =
        MUTABLE_AST.replaceSubtree(root1, root2, 1, ast.getSource().toBuilder(), ast2.getSource().toBuilder()).toParsedAst(); // Replace true with the macro expr

    assertThat(mutatedAst.getSource().getMacroCalls()).hasSize(1);
    assertThat(CEL_UNPARSER.unparse(mutatedAst)).isEqualTo("[1].exists(x, x > 0) && false");
    assertThat(CEL.createProgram(CEL.check(mutatedAst).getAst()).eval()).isEqualTo(false);
  }

  @Test
  public void replaceSubtree_macroInsertedIntoExistingMacro_macroCallPopulated() throws Exception {
//    CelAbstractSyntaxTree ast = CEL.compile("[1].exists(x, x > 0 && true)").getAst();
//    CelAbstractSyntaxTree ast2 = CEL.compile("[2].exists(y, y > 0)").getAst();
//
//    CelAbstractSyntaxTree mutatedAst =
//        MUTABLE_AST.replaceSubtreeWithNewAst(ast, ast2, 9); // Replace true with the ast2 maro expr
//
//    assertThat(mutatedAst.getSource().getMacroCalls()).hasSize(2);
//    assertThat(CEL_UNPARSER.unparse(mutatedAst))
//        .isEqualTo("[1].exists(x, x > 0 && [2].exists(y, y > 0))");
//    assertThat(CEL.createProgram(CEL.check(mutatedAst).getAst()).eval()).isEqualTo(true);
  }

  @Test
  public void replaceSubtreeWithNewBindMacro_replaceRoot() throws Exception {
    CelAbstractSyntaxTree ast = CEL.compile("1 + 1").getAst();
    String variableName = "@r0";
    CelExpr resultExpr =
        CelExpr.newBuilder()
            .setCall(
                CelCall.newBuilder()
                    .setFunction(Operator.ADD.getFunction())
                    .addArgs(
                        CelExpr.ofIdentExpr(0, variableName), CelExpr.ofIdentExpr(0, variableName))
                    .build())
            .build();

    CelAbstractSyntaxTree mutatedAst =
        MUTABLE_AST.replaceSubtreeWithNewBindMacro(
            ast,
            variableName,
            CelExpr.ofConstantExpr(0, CelConstant.ofValue(3L)),
            resultExpr,
            CelNavigableAst.fromAst(ast).getRoot().id());

    assertThat(mutatedAst.getSource().getMacroCalls()).hasSize(1);
    assertThat(CEL_UNPARSER.unparse(mutatedAst)).isEqualTo("cel.bind(@r0, 3, @r0 + @r0)");
    assertThat(CEL.createProgram(CEL.check(mutatedAst).getAst()).eval()).isEqualTo(6);
    assertConsistentMacroCalls(mutatedAst);
  }

  @Test
  public void replaceSubtreeWithNewBindMacro_nestedBindMacro_replaceComprehensionResult()
      throws Exception {
    // Arrange
    CelAbstractSyntaxTree ast = CEL.compile("1 + 1").getAst();
    String variableName = "@r0";
    CelExpr resultExpr =
        CelExpr.newBuilder()
            .setCall(
                CelCall.newBuilder()
                    .setFunction(Operator.ADD.getFunction())
                    .addArgs(
                        CelExpr.ofIdentExpr(0, variableName), CelExpr.ofIdentExpr(0, variableName))
                    .build())
            .build();

    // Act
    // Perform the initial replacement. (1 + 1) -> cel.bind(@r0, 3, @r0 + @r0)
    CelAbstractSyntaxTree mutatedAst =
        MUTABLE_AST.replaceSubtreeWithNewBindMacro(
            ast,
            variableName,
            CelExpr.ofConstantExpr(0, CelConstant.ofValue(3L)),
            resultExpr,
            2); // Replace +
    String nestedVariableName = "@r1";
    // Construct a new result expression of the form @r0 + @r0 + @r1 + @r1
    resultExpr =
        CelExpr.newBuilder()
            .setCall(
                CelCall.newBuilder()
                    .setFunction(Operator.ADD.getFunction())
                    .addArgs(
                        CelExpr.newBuilder()
                            .setCall(
                                CelCall.newBuilder()
                                    .setFunction(Operator.ADD.getFunction())
                                    .addArgs(
                                        CelExpr.newBuilder()
                                            .setCall(
                                                CelCall.newBuilder()
                                                    .setFunction(Operator.ADD.getFunction())
                                                    .addArgs(
                                                        CelExpr.ofIdentExpr(0, variableName),
                                                        CelExpr.ofIdentExpr(0, variableName))
                                                    .build())
                                            .build(),
                                        CelExpr.ofIdentExpr(0, nestedVariableName))
                                    .build())
                            .build(),
                        CelExpr.ofIdentExpr(0, nestedVariableName))
                    .build())
            .build();
    // Find the call node (_+_) in the comprehension's result
    long exprIdToReplace =
        CelNavigableAst.fromAst(mutatedAst)
            .getRoot()
            .children()
            .filter(
                node ->
                    node.getKind().equals(Kind.CALL)
                        && node.parent().get().getKind().equals(Kind.COMPREHENSION))
            .findAny()
            .get()
            .expr()
            .id();
    // This should produce cel.bind(@r1, 1, cel.bind(@r0, 3, @r0 + @r0 + @r1 + @r1))
    mutatedAst =
        MUTABLE_AST.replaceSubtreeWithNewBindMacro(
            mutatedAst,
            nestedVariableName,
            CelExpr.ofConstantExpr(0, CelConstant.ofValue(1L)),
            resultExpr,
            exprIdToReplace); // Replace +

    assertThat(mutatedAst.getSource().getMacroCalls()).hasSize(2);
    assertThat(CEL.createProgram(CEL.check(mutatedAst).getAst()).eval()).isEqualTo(8);
    assertThat(CEL_UNPARSER.unparse(mutatedAst))
        .isEqualTo("cel.bind(@r0, 3, cel.bind(@r1, 1, @r0 + @r0 + @r1 + @r1))");
    assertConsistentMacroCalls(mutatedAst);
  }

  @Test
  public void replaceSubtreeWithNewBindMacro_replaceRootWithNestedBindMacro() throws Exception {
    // Arrange
    CelAbstractSyntaxTree ast = CEL.compile("1 + 1 + 3 + 3").getAst();
    String variableName = "@r0";
    CelExpr resultExpr =
        CelExpr.newBuilder()
            .setCall(
                CelCall.newBuilder()
                    .setFunction(Operator.ADD.getFunction())
                    .addArgs(
                        CelExpr.ofIdentExpr(0, variableName), CelExpr.ofIdentExpr(0, variableName))
                    .build())
            .build();

    // Act
    // Perform the initial replacement. (1 + 1 + 3 + 3) -> cel.bind(@r0, 1, @r0 + @r0) + 3 + 3
    CelAbstractSyntaxTree mutatedAst =
        MUTABLE_AST.replaceSubtreeWithNewBindMacro(
            ast,
            variableName,
            CelExpr.ofConstantExpr(0, CelConstant.ofValue(1L)),
            resultExpr,
            2); // Replace +
    // Construct a new result expression of the form:
    // cel.bind(@r1, 3, cel.bind(@r0, 1, @r0 + @r0) + @r1 + @r1)
    String nestedVariableName = "@r1";
    CelExpr bindMacro =
        CelNavigableAst.fromAst(mutatedAst)
            .getRoot()
            .descendants()
            .filter(node -> node.getKind().equals(Kind.COMPREHENSION))
            .findAny()
            .get()
            .expr();
    resultExpr =
        CelExpr.newBuilder()
            .setCall(
                CelCall.newBuilder()
                    .setFunction(Operator.ADD.getFunction())
                    .addArgs(
                        CelExpr.newBuilder()
                            .setCall(
                                CelCall.newBuilder()
                                    .setFunction(Operator.ADD.getFunction())
                                    .addArgs(bindMacro, CelExpr.ofIdentExpr(0, nestedVariableName))
                                    .build())
                            .build(),
                        CelExpr.ofIdentExpr(0, nestedVariableName))
                    .build())
            .build();
    // Replace the root with the new result and a bind macro inserted
    mutatedAst =
        MUTABLE_AST.replaceSubtreeWithNewBindMacro(
            mutatedAst,
            nestedVariableName,
            CelExpr.ofConstantExpr(0, CelConstant.ofValue(3L)),
            resultExpr,
            CelNavigableAst.fromAst(mutatedAst).getRoot().id());

    assertThat(mutatedAst.getSource().getMacroCalls()).hasSize(2);
    assertThat(CEL.createProgram(CEL.check(mutatedAst).getAst()).eval()).isEqualTo(8);
    assertThat(CEL_UNPARSER.unparse(mutatedAst))
        .isEqualTo("cel.bind(@r1, 3, cel.bind(@r0, 1, @r0 + @r0) + @r1 + @r1)");
    assertConsistentMacroCalls(mutatedAst);
  }

  @Test
  public void replaceSubtree_macroReplacedWithConstExpr_macroCallCleared() throws Exception {
//    CelAbstractSyntaxTree ast =
//        CEL.compile("[1].exists(x, x > 0) && [2].exists(x, x > 0)").getAst();
//    CelAbstractSyntaxTree ast2 = CEL.compile("1").getAst();
//
//    CelAbstractSyntaxTree mutatedAst =
//        MUTABLE_AST.replaceSubtreeWithNewAst(
//            ast, ast2, CelNavigableAst.fromAst(ast).getRoot().id());
//
//    assertThat(mutatedAst.getSource().getMacroCalls()).isEmpty();
//    assertThat(CEL_UNPARSER.unparse(mutatedAst)).isEqualTo("1");
//    assertThat(CEL.createProgram(CEL.check(mutatedAst).getAst()).eval()).isEqualTo(1);
  }

  @Test
  @SuppressWarnings("unchecked") // Test only
  public void replaceSubtree_replaceExtraneousListCreatedByMacro_unparseSuccess() throws Exception {
    // Certain macros such as `map` or `filter` generates an extraneous list_expr in the loop step's
    // argument that does not exist in the original expression.
    // For example, the loop step of this expression looks like:
    // CALL [10] {
    //    function: _+_
    //    args: {
    //      IDENT [8] {
    //        name: __result__
    //      }
    //      CREATE_LIST [9] {
    //        elements: {
    //          CONSTANT [5] { value: 1 }
    //        }
    //      }
    //    }
    //  }
    CelAbstractSyntaxTree ast = CEL.compile("[1].map(x, 1)").getAst();

    // These two mutation are equivalent.
    CelAbstractSyntaxTree mutatedAstWithList =
        MUTABLE_AST.replaceSubtree(
            ast,
            CelExpr.ofCreateListExpr(
                0,
                ImmutableList.of(CelExpr.newBuilder().setConstant(CelConstant.ofValue(2L)).build()),
                ImmutableList.of()),
            9L);
    CelAbstractSyntaxTree mutatedAstWithConstant =
        MUTABLE_AST.replaceSubtree(
            ast, CelExpr.newBuilder().setConstant(CelConstant.ofValue(2L)).build(), 5L);

    assertThat(CEL_UNPARSER.unparse(mutatedAstWithList)).isEqualTo("[1].map(x, 2)");
    assertThat(CEL_UNPARSER.unparse(mutatedAstWithConstant)).isEqualTo("[1].map(x, 2)");
    assertThat((List<Long>) CEL.createProgram(CEL.check(mutatedAstWithList).getAst()).eval())
        .containsExactly(2L);
  }

  @Test
  public void globalCallExpr_replaceRoot() throws Exception {
    // Tree shape (brackets are expr IDs):
    //           + [4]
    //      + [2]       x [5]
    //  1 [1]     2 [3]
    CelAbstractSyntaxTree ast = CEL.compile("1 + 2 + x").getAst();
    MutableExpr root = MutableExprConverter.fromCelExpr(ast.getExpr());
    MutableExpr newExpr = MutableExpr.ofConstant(MutableConstant.ofValue(10));

    MutatedResult result =
        MUTABLE_AST.replaceSubtree(root, newExpr, root.id());

    assertThat(result.toParsedAst().getExpr()).isEqualTo(CelExpr.ofConstantExpr(7, CelConstant.ofValue(10)));
  }

  @Test
  public void globalCallExpr_replaceLeaf() throws Exception {
    // Tree shape (brackets are expr IDs):
    //           + [4]
    //      + [2]       x [5]
    //  1 [1]     2 [3]
    CelAbstractSyntaxTree ast = CEL.compile("1 + 2 + x").getAst();
    MutableExpr root = MutableExprConverter.fromCelExpr(ast.getExpr());
    MutableExpr newExpr = MutableExpr.ofConstant(MutableConstant.ofValue(10));

    MutatedResult result =
            MUTABLE_AST.replaceSubtree(root, newExpr, 1);

    assertThat(CEL_UNPARSER.unparse(result.toParsedAst())).isEqualTo("10 + 2 + x");
  }

  @Test
  public void globalCallExpr_replaceMiddleBranch() throws Exception {
    // Tree shape (brackets are expr IDs):
    //           + [4]
    //      + [2]       x [5]
    //  1 [1]     2 [3]
    CelAbstractSyntaxTree ast = CEL.compile("1 + 2 + x").getAst();
    MutableExpr root = MutableExprConverter.fromCelExpr(ast.getExpr());
    MutableExpr newExpr = MutableExpr.ofConstant(MutableConstant.ofValue(10));

    MutatedResult result =
            MUTABLE_AST.replaceSubtree(root, newExpr, 2);

    assertThat(CEL_UNPARSER.unparse(result.toParsedAst())).isEqualTo("10 + x");
  }

  @Test
  public void globalCallExpr_replaceMiddleBranch_withCallExpr() throws Exception {
    // Tree shape (brackets are expr IDs):
    //           + [4]
    //      + [2]       x [5]
    //  1 [1]     2 [3]
    CelAbstractSyntaxTree ast = CEL.compile("1 + 2 + x").getAst();
    CelAbstractSyntaxTree ast2 = CEL.compile("4 + 5 + 6").getAst();
    MutableExpr root = MutableExprConverter.fromCelExpr(ast.getExpr());
    MutableExpr newExpr = MutableExprConverter.fromCelExpr(ast2.getExpr());

    MutatedResult result = MUTABLE_AST.replaceSubtree(root, newExpr, 2);

    assertThat(CEL_UNPARSER.unparse(result.toParsedAst())).isEqualTo("4 + 5 + 6 + x");
  }

  @Test
  public void memberCallExpr_replaceLeafTarget() throws Exception {
    // Tree shape (brackets are expr IDs):
    //           func [2]
    //      10 [1]       func [4]
    //                4 [3]       5 [5]
    Cel cel =
        CelFactory.standardCelBuilder()
            .addFunctionDeclarations(
                CelFunctionDecl.newFunctionDeclaration(
                    "func",
                    CelOverloadDecl.newMemberOverload(
                        "func_overload", SimpleType.INT, SimpleType.INT, SimpleType.INT)))
            .build();
    CelAbstractSyntaxTree ast = cel.compile("10.func(4.func(5))").getAst();
    MutableExpr root = MutableExprConverter.fromCelExpr(ast.getExpr());
    MutableExpr newExpr = MutableExpr.ofConstant(MutableConstant.ofValue(20));

    MutatedResult result = MUTABLE_AST.replaceSubtree(root, newExpr, 3);

    assertThat(CEL_UNPARSER.unparse(result.toParsedAst())).isEqualTo("10.func(20.func(5))");
  }

  @Test
  public void memberCallExpr_replaceLeafArgument() throws Exception {
    // Tree shape (brackets are expr IDs):
    //           func [2]
    //      10 [1]       func [4]
    //                4 [3]       5 [5]
    Cel cel =
        CelFactory.standardCelBuilder()
            .addFunctionDeclarations(
                CelFunctionDecl.newFunctionDeclaration(
                    "func",
                    CelOverloadDecl.newMemberOverload(
                        "func_overload", SimpleType.INT, SimpleType.INT, SimpleType.INT)))
            .build();
    CelAbstractSyntaxTree ast = cel.compile("10.func(4.func(5))").getAst();
    MutableExpr root = MutableExprConverter.fromCelExpr(ast.getExpr());
    MutableExpr newExpr = MutableExpr.ofConstant(MutableConstant.ofValue(20));

    MutatedResult result = MUTABLE_AST.replaceSubtree(root, newExpr, 5);

    assertThat(CEL_UNPARSER.unparse(result.toParsedAst())).isEqualTo("10.func(4.func(20))");
  }

  @Test
  public void memberCallExpr_replaceMiddleBranchTarget() throws Exception {
    // Tree shape (brackets are expr IDs):
    //           func [2]
    //      10 [1]       func [4]
    //                4 [3]       5 [5]
    Cel cel =
            CelFactory.standardCelBuilder()
                    .addFunctionDeclarations(
                            CelFunctionDecl.newFunctionDeclaration(
                                    "func",
                                    CelOverloadDecl.newMemberOverload(
                                            "func_overload", SimpleType.INT, SimpleType.INT, SimpleType.INT)))
                    .build();
    CelAbstractSyntaxTree ast = cel.compile("10.func(4.func(5))").getAst();
    MutableExpr root = MutableExprConverter.fromCelExpr(ast.getExpr());
    MutableExpr newExpr = MutableExpr.ofConstant(MutableConstant.ofValue(20));

    MutatedResult result = MUTABLE_AST.replaceSubtree(root, newExpr, 1);

    assertThat(CEL_UNPARSER.unparse(result.toParsedAst())).isEqualTo("20.func(4.func(5))");
  }

  @Test
  public void memberCallExpr_replaceMiddleBranchArgument() throws Exception {
    // Tree shape (brackets are expr IDs):
    //           func [2]
    //      10 [1]       func [4]
    //                4 [3]       5 [5]
    Cel cel =
            CelFactory.standardCelBuilder()
                    .addFunctionDeclarations(
                            CelFunctionDecl.newFunctionDeclaration(
                                    "func",
                                    CelOverloadDecl.newMemberOverload(
                                            "func_overload", SimpleType.INT, SimpleType.INT, SimpleType.INT)))
                    .build();
    CelAbstractSyntaxTree ast = cel.compile("10.func(4.func(5))").getAst();
    MutableExpr root = MutableExprConverter.fromCelExpr(ast.getExpr());
    MutableExpr newExpr = MutableExpr.ofConstant(MutableConstant.ofValue(20));

    MutatedResult result = MUTABLE_AST.replaceSubtree(root, newExpr, 4);

    assertThat(CEL_UNPARSER.unparse(result.toParsedAst())).isEqualTo("10.func(20)");
  }

  @Test
  public void select_replaceField() throws Exception {
    // Tree shape (brackets are expr IDs):
    //              + [2]
    //         5 [1]        select [4]
    //                msg [3]
    CelAbstractSyntaxTree ast = CEL.compile("5 + msg.single_int64").getAst();
    MutableExpr root = MutableExprConverter.fromCelExpr(ast.getExpr());
    MutableExpr newExpr = MutableExpr.ofSelect( MutableSelect.create(
            MutableExpr.ofIdent("test"), "single_sint32"
    ));

    MutatedResult result = MUTABLE_AST.replaceSubtree(root, newExpr, 4);

    assertThat(CEL_UNPARSER.unparse(result.toParsedAst())).isEqualTo("5 + test.single_sint32");
  }

  @Test
  public void select_replaceOperand() throws Exception {
    // Tree shape (brackets are expr IDs):
    //              + [2]
    //         5 [1]        select [4]
    //                msg [3]
    CelAbstractSyntaxTree ast = CEL.compile("5 + msg.single_int64").getAst();
    MutableExpr root = MutableExprConverter.fromCelExpr(ast.getExpr());
    MutableExpr newExpr = MutableExpr.ofIdent("test");

    MutatedResult result = MUTABLE_AST.replaceSubtree(root, newExpr, 3);

    assertThat(CEL_UNPARSER.unparse(result.toParsedAst())).isEqualTo("5 + test.single_int64");
  }

  @Test
  public void list_replaceElement() throws Exception {
    // Tree shape (brackets are expr IDs):
    //        list [1]
    //  2 [2]  3 [3]  4 [4]
    CelAbstractSyntaxTree ast = CEL.compile("[2, 3, 4]").getAst();
    MutableExpr root = MutableExprConverter.fromCelExpr(ast.getExpr());
    MutableExpr newExpr = MutableExpr.ofConstant(MutableConstant.ofValue(5));

    MutatedResult result = MUTABLE_AST.replaceSubtree(root, newExpr, 4);

    assertThat(CEL_UNPARSER.unparse(result.toParsedAst())).isEqualTo("[2, 3, 5]");
  }

  @Test
  public void createStruct_replaceValue() throws Exception {
    // Tree shape (brackets are expr IDs):
    //        TestAllTypes [1]
    //             single_int64 [2]
    //                  2 [3]
    CelAbstractSyntaxTree ast = CEL.compile("TestAllTypes{single_int64: 2}").getAst();
    MutableExpr root = MutableExprConverter.fromCelExpr(ast.getExpr());
    MutableExpr newExpr = MutableExpr.ofConstant(MutableConstant.ofValue(5));

    MutatedResult result = MUTABLE_AST.replaceSubtree(root, newExpr, 3);

    assertThat(CEL_UNPARSER.unparse(result.toParsedAst())).isEqualTo("TestAllTypes{single_int64: 5}");
  }

  @Test
  public void createMap_replaceKey() throws Exception {
    // Tree shape (brackets are expr IDs):
    //        map [1]
    //       map_entry [2]
    //     'a' [3] : 1 [4]
    CelAbstractSyntaxTree ast = CEL.compile("{'a': 1}").getAst();
    MutableExpr root = MutableExprConverter.fromCelExpr(ast.getExpr());
    MutableExpr newExpr = MutableExpr.ofConstant(MutableConstant.ofValue(5));

    MutatedResult result = MUTABLE_AST.replaceSubtree(root, newExpr, 3);

    assertThat(CEL_UNPARSER.unparse(result.toParsedAst())).isEqualTo("{5: 1}");
  }

  @Test
  public void createMap_replaceValue() throws Exception {
    // Tree shape (brackets are expr IDs):
    //        map [1]
    //       map_entry [2]
    //     'a' [3] : 1 [4]
    CelAbstractSyntaxTree ast = CEL.compile("{'a': 1}").getAst();
    MutableExpr root = MutableExprConverter.fromCelExpr(ast.getExpr());
    MutableExpr newExpr = MutableExpr.ofConstant(MutableConstant.ofValue(5));

    MutatedResult result = MUTABLE_AST.replaceSubtree(root, newExpr, 4);

    assertThat(CEL_UNPARSER.unparse(result.toParsedAst())).isEqualTo("{\"a\": 5}");
  }

  @Test
  public void comprehension_replaceIterRange() throws Exception {
    CelAbstractSyntaxTree ast = CEL.compile("[true].exists(i, i)").getAst();
    MutableExpr root = MutableExprConverter.fromCelExpr(ast.getExpr());
    MutableExpr newExpr = MutableExpr.ofConstant(MutableConstant.ofValue(false));

    CelAbstractSyntaxTree replacedAst = MUTABLE_AST.replaceSubtree(
            root,
            newExpr, 2,
            ast.getSource().toBuilder(),
            CelSource.newBuilder()
    ).toParsedAst();

    assertThat(CEL_UNPARSER.unparse(replacedAst)).isEqualTo("[false].exists(i, i)");
    assertThat(CEL.createProgram(CEL.check(replacedAst).getAst()).eval()).isEqualTo(false);
    assertConsistentMacroCalls(replacedAst);
  }

  @Test
  public void comprehension_replaceAccuInit() throws Exception {
    CelAbstractSyntaxTree ast = CEL.compile("[false].exists(i, i)").getAst();
    MutableExpr root = MutableExprConverter.fromCelExpr(ast.getExpr());
    MutableExpr newExpr = MutableExpr.ofConstant(MutableConstant.ofValue(true));

    CelAbstractSyntaxTree replacedAst = MUTABLE_AST.replaceSubtree(root, newExpr, 6, ast.getSource().toBuilder()).toParsedAst();

    assertThat(CEL_UNPARSER.unparse(replacedAst)).isEqualTo("[false].exists(i, i)");
    assertThat(CEL.createProgram(CEL.check(replacedAst).getAst()).eval()).isEqualTo(true);
    // Check that the init value of accumulator has actually been replaced.
    assertThat(ast.getExpr().comprehension().accuInit().constant().booleanValue()).isFalse();
    assertThat(replacedAst.getExpr().comprehension().accuInit().constant().booleanValue()).isTrue();
    assertConsistentMacroCalls(ast);
  }

  @Test
  public void comprehension_replaceLoopStep() throws Exception {
    CelAbstractSyntaxTree ast = CEL.compile("[false].exists(i, i)").getAst();
    MutableExpr root = MutableExprConverter.fromCelExpr(ast.getExpr());
    MutableExpr newExpr = MutableExpr.ofIdent("test");

    CelAbstractSyntaxTree replacedAst =
        MUTABLE_AST.replaceSubtree(
            root,
            newExpr,
            5,
            ast.getSource().toBuilder()
            ).toParsedAst();

    assertThat(CEL_UNPARSER.unparse(replacedAst)).isEqualTo("[false].exists(i, test)");
    assertConsistentMacroCalls(ast);
  }

  @Test
  public void mangleComprehensionVariable_singleMacro() throws Exception {
    CelAbstractSyntaxTree ast = CEL.compile("[false].exists(i, i)").getAst();
    MutableExpr root = MutableExprConverter.fromCelExpr(ast.getExpr());

    CelAbstractSyntaxTree mangledAst =
        MUTABLE_AST.mangleComprehensionIdentifierNames(ast, root, "@c", "@x").ast();

    assertThat(mangledAst.getExpr().toString())
        .isEqualTo(
            "COMPREHENSION [13] {\n"
                + "  iter_var: @c0:0\n"
                + "  iter_range: {\n"
                + "    CREATE_LIST [1] {\n"
                + "      elements: {\n"
                + "        CONSTANT [2] { value: false }\n"
                + "      }\n"
                + "    }\n"
                + "  }\n"
                + "  accu_var: @x0:0\n"
                + "  accu_init: {\n"
                + "    CONSTANT [6] { value: false }\n"
                + "  }\n"
                + "  loop_condition: {\n"
                + "    CALL [9] {\n"
                + "      function: @not_strictly_false\n"
                + "      args: {\n"
                + "        CALL [8] {\n"
                + "          function: !_\n"
                + "          args: {\n"
                + "            IDENT [7] {\n"
                + "              name: @x0:0\n"
                + "            }\n"
                + "          }\n"
                + "        }\n"
                + "      }\n"
                + "    }\n"
                + "  }\n"
                + "  loop_step: {\n"
                + "    CALL [11] {\n"
                + "      function: _||_\n"
                + "      args: {\n"
                + "        IDENT [10] {\n"
                + "          name: @x0:0\n"
                + "        }\n"
                + "        IDENT [5] {\n"
                + "          name: @c0:0\n"
                + "        }\n"
                + "      }\n"
                + "    }\n"
                + "  }\n"
                + "  result: {\n"
                + "    IDENT [12] {\n"
                + "      name: @x0:0\n"
                + "    }\n"
                + "  }\n"
                + "}");
    assertThat(CEL_UNPARSER.unparse(mangledAst)).isEqualTo("[false].exists(@c0:0, @c0:0)");
    assertThat(CEL.createProgram(CEL.check(mangledAst).getAst()).eval()).isEqualTo(false);
    assertConsistentMacroCalls(ast);
  }

  @Test
  public void mangleComprehensionVariable_nestedMacroWithShadowedVariables() throws Exception {
    CelAbstractSyntaxTree ast = CEL.compile("[x].exists(x, [x].exists(x, x == 1))").getAst();
    MutableExpr root = MutableExprConverter.fromCelExpr(ast.getExpr());

    CelAbstractSyntaxTree mangledAst =
        MUTABLE_AST.mangleComprehensionIdentifierNames(ast, root, "@c", "@x").ast();

    assertThat(mangledAst.getExpr().toString())
        .isEqualTo(
            "COMPREHENSION [27] {\n"
                + "  iter_var: @c0:0\n"
                + "  iter_range: {\n"
                + "    CREATE_LIST [1] {\n"
                + "      elements: {\n"
                + "        IDENT [2] {\n"
                + "          name: x\n"
                + "        }\n"
                + "      }\n"
                + "    }\n"
                + "  }\n"
                + "  accu_var: @x0:0\n"
                + "  accu_init: {\n"
                + "    CONSTANT [20] { value: false }\n"
                + "  }\n"
                + "  loop_condition: {\n"
                + "    CALL [23] {\n"
                + "      function: @not_strictly_false\n"
                + "      args: {\n"
                + "        CALL [22] {\n"
                + "          function: !_\n"
                + "          args: {\n"
                + "            IDENT [21] {\n"
                + "              name: @x0:0\n"
                + "            }\n"
                + "          }\n"
                + "        }\n"
                + "      }\n"
                + "    }\n"
                + "  }\n"
                + "  loop_step: {\n"
                + "    CALL [25] {\n"
                + "      function: _||_\n"
                + "      args: {\n"
                + "        IDENT [24] {\n"
                + "          name: @x0:0\n"
                + "        }\n"
                + "        COMPREHENSION [19] {\n"
                + "          iter_var: @c1:0\n"
                + "          iter_range: {\n"
                + "            CREATE_LIST [5] {\n"
                + "              elements: {\n"
                + "                IDENT [6] {\n"
                + "                  name: @c0:0\n"
                + "                }\n"
                + "              }\n"
                + "            }\n"
                + "          }\n"
                + "          accu_var: @x1:0\n"
                + "          accu_init: {\n"
                + "            CONSTANT [12] { value: false }\n"
                + "          }\n"
                + "          loop_condition: {\n"
                + "            CALL [15] {\n"
                + "              function: @not_strictly_false\n"
                + "              args: {\n"
                + "                CALL [14] {\n"
                + "                  function: !_\n"
                + "                  args: {\n"
                + "                    IDENT [13] {\n"
                + "                      name: @x1:0\n"
                + "                    }\n"
                + "                  }\n"
                + "                }\n"
                + "              }\n"
                + "            }\n"
                + "          }\n"
                + "          loop_step: {\n"
                + "            CALL [17] {\n"
                + "              function: _||_\n"
                + "              args: {\n"
                + "                IDENT [16] {\n"
                + "                  name: @x1:0\n"
                + "                }\n"
                + "                CALL [10] {\n"
                + "                  function: _==_\n"
                + "                  args: {\n"
                + "                    IDENT [9] {\n"
                + "                      name: @c1:0\n"
                + "                    }\n"
                + "                    CONSTANT [11] { value: 1 }\n"
                + "                  }\n"
                + "                }\n"
                + "              }\n"
                + "            }\n"
                + "          }\n"
                + "          result: {\n"
                + "            IDENT [18] {\n"
                + "              name: @x1:0\n"
                + "            }\n"
                + "          }\n"
                + "        }\n"
                + "      }\n"
                + "    }\n"
                + "  }\n"
                + "  result: {\n"
                + "    IDENT [26] {\n"
                + "      name: @x0:0\n"
                + "    }\n"
                + "  }\n"
                + "}");
    assertThat(CEL_UNPARSER.unparse(mangledAst))
        .isEqualTo("[x].exists(@c0:0, [@c0:0].exists(@c1:0, @c1:0 == 1))");
    assertThat(CEL.createProgram(CEL.check(mangledAst).getAst()).eval(ImmutableMap.of("x", 1)))
        .isEqualTo(true);
    assertConsistentMacroCalls(ast);
  }

  @Test
  public void mangleComprehensionVariable_hasMacro_noOp() throws Exception {
    CelAbstractSyntaxTree ast = CEL.compile("has(msg.single_int64)").getAst();

    CelAbstractSyntaxTree mangledAst =
        MUTABLE_AST.mangleComprehensionIdentifierNames(ast, "@c", "@x").ast();

    assertThat(CEL_UNPARSER.unparse(mangledAst)).isEqualTo("has(msg.single_int64)");
    assertThat(
            CEL.createProgram(CEL.check(mangledAst).getAst())
                .eval(ImmutableMap.of("msg", TestAllTypes.getDefaultInstance())))
        .isEqualTo(false);
    assertConsistentMacroCalls(ast);
  }

  @Test
  public void replaceSubtree_iterationLimitReached_throws() throws Exception {
    CelAbstractSyntaxTree ast = CEL.compile("true && false").getAst();
    MutableAst mutableAst = newInstance(1);

    IllegalStateException e =
        assertThrows(
            IllegalStateException.class,
            () ->
                mutableAst.replaceSubtree(
                    ast, CelExpr.ofConstantExpr(0, CelConstant.ofValue(false)), 1));

    assertThat(e).hasMessageThat().isEqualTo("Max iteration count reached.");
  }

  /**
   * Asserts that the expressions that appears in source_info's macro calls are consistent with the
   * actual expr nodes in the AST.
   */
  private void assertConsistentMacroCalls(CelAbstractSyntaxTree ast) {
    assertThat(ast.getSource().getMacroCalls()).isNotEmpty();
    ImmutableMap<Long, CelExpr> allExprs =
        CelNavigableAst.fromAst(ast)
            .getRoot()
            .allNodes()
            .map(CelNavigableExpr::expr)
            .collect(toImmutableMap(CelExpr::id, node -> node, (expr1, expr2) -> expr1));
    for (CelExpr macroCall : ast.getSource().getMacroCalls().values()) {
      assertThat(macroCall.id()).isEqualTo(0);
      CelNavigableExpr.fromExpr(macroCall)
          .descendants()
          .map(CelNavigableExpr::expr)
          .forEach(
              node -> {
                CelExpr e = allExprs.get(node.id());
                if (e != null) {
                  assertThat(node.id()).isEqualTo(e.id());
                  if (e.exprKind().getKind().equals(Kind.COMPREHENSION)) {
                    assertThat(node.exprKind().getKind()).isEqualTo(Kind.NOT_SET);
                  } else {
                    assertThat(node.exprKind().getKind()).isEqualTo(e.exprKind().getKind());
                  }
                }
              });
    }
  }
}
