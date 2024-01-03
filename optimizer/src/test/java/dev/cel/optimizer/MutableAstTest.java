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

import com.google.common.collect.ImmutableMap;
import com.google.testing.junit.testparameterinjector.TestParameterInjector;
import com.google.testing.junit.testparameterinjector.TestParameters;
import dev.cel.bundle.Cel;
import dev.cel.bundle.CelFactory;
import dev.cel.common.CelAbstractSyntaxTree;
import dev.cel.common.CelFunctionDecl;
import dev.cel.common.CelOptions;
import dev.cel.common.CelOverloadDecl;
import dev.cel.common.ast.CelConstant;
import dev.cel.common.ast.CelExpr;
import dev.cel.common.ast.CelExpr.CelCall;
import dev.cel.common.ast.CelExpr.CelIdent;
import dev.cel.common.ast.CelExpr.CelSelect;
import dev.cel.common.ast.CelExpr.ExprKind.Kind;
import dev.cel.common.navigation.CelNavigableAst;
import dev.cel.common.navigation.CelNavigableExpr;
import dev.cel.common.types.SimpleType;
import dev.cel.common.types.StructTypeReference;
import dev.cel.extensions.CelExtensions;
import dev.cel.extensions.CelOptionalLibrary;
import dev.cel.parser.CelStandardMacro;
import dev.cel.parser.CelUnparser;
import dev.cel.parser.CelUnparserFactory;
import dev.cel.parser.Operator;
import dev.cel.testing.testdata.proto3.TestAllTypesProto.TestAllTypes;
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

  @Test
  public void constExpr() throws Exception {
    CelAbstractSyntaxTree ast = CEL.compile("10").getAst();

    CelAbstractSyntaxTree mutatedAst =
        MutableAst.replaceSubtree(
            ast, CelExpr.newBuilder().setConstant(CelConstant.ofValue(true)).build(), 1);

    assertThat(mutatedAst.getExpr())
        .isEqualTo(CelExpr.ofConstantExpr(1, CelConstant.ofValue(true)));
  }

  @Test
  public void mutableAst_returnsParsedAst() throws Exception {
    CelAbstractSyntaxTree ast = CEL.compile("10").getAst();

    CelAbstractSyntaxTree mutatedAst =
        MutableAst.replaceSubtree(
            ast, CelExpr.newBuilder().setConstant(CelConstant.ofValue(true)).build(), 1);

    assertThat(ast.isChecked()).isTrue();
    assertThat(mutatedAst.isChecked()).isFalse();
  }

  @Test
  public void mutableAst_nonMacro_sourceCleared() throws Exception {
    CelAbstractSyntaxTree ast = CEL.compile("10").getAst();

    CelAbstractSyntaxTree mutatedAst =
        MutableAst.replaceSubtree(
            ast, CelExpr.newBuilder().setConstant(CelConstant.ofValue(true)).build(), 1);

    assertThat(mutatedAst.getSource().getDescription()).isEmpty();
    assertThat(mutatedAst.getSource().getLineOffsets()).isEmpty();
    assertThat(mutatedAst.getSource().getPositionsMap()).isEmpty();
    assertThat(mutatedAst.getSource().getMacroCalls()).isEmpty();
  }

  @Test
  public void mutableAst_macro_sourceMacroCallsPopulated() throws Exception {
    CelAbstractSyntaxTree ast = CEL.compile("has(TestAllTypes{}.single_int32)").getAst();

    CelAbstractSyntaxTree mutatedAst =
        MutableAst.replaceSubtree(
            ast, CelExpr.newBuilder().setConstant(CelConstant.ofValue(true)).build(), 1);

    assertThat(mutatedAst.getSource().getDescription()).isEmpty();
    assertThat(mutatedAst.getSource().getLineOffsets()).isEmpty();
    assertThat(mutatedAst.getSource().getPositionsMap()).isEmpty();
    assertThat(mutatedAst.getSource().getMacroCalls()).isNotEmpty();
  }

  @Test
  @TestParameters("{source: '[1].exists(x, x > 0)', expectedMacroCallSize: 1}")
  @TestParameters(
      "{source: '[1].exists(x, x > 0) && [2].exists(x, x > 0)', expectedMacroCallSize: 2}")
  @TestParameters(
      "{source: '[1].exists(x, [2].exists(y, x > 0 && y > x))', expectedMacroCallSize: 2}")
  public void replaceSubtree_rootReplacedWithMacro_macroCallPopulated(
      String source, int expectedMacroCallSize) throws Exception {
    CelAbstractSyntaxTree ast = CEL.compile("1").getAst();
    CelAbstractSyntaxTree ast2 = CEL.compile(source).getAst();

    CelAbstractSyntaxTree mutatedAst =
        MutableAst.replaceSubtree(ast, ast2, CelNavigableAst.fromAst(ast).getRoot().expr().id());

    assertThat(mutatedAst.getSource().getMacroCalls()).hasSize(expectedMacroCallSize);
    assertThat(CEL_UNPARSER.unparse(mutatedAst)).isEqualTo(source);
    assertThat(CEL.createProgram(CEL.check(mutatedAst).getAst()).eval()).isEqualTo(true);
  }

  @Test
  public void replaceSubtree_branchReplacedWithMacro_macroCallPopulated() throws Exception {
    CelAbstractSyntaxTree ast = CEL.compile("true && false").getAst();
    CelAbstractSyntaxTree ast2 = CEL.compile("[1].exists(x, x > 0)").getAst();

    CelAbstractSyntaxTree mutatedAst =
        MutableAst.replaceSubtree(ast, ast2, 3); // Replace false with the macro expr
    CelAbstractSyntaxTree mutatedAst2 =
        MutableAst.replaceSubtree(ast, ast2, 1); // Replace true with the macro expr

    assertThat(mutatedAst.getSource().getMacroCalls()).hasSize(1);
    assertThat(CEL_UNPARSER.unparse(mutatedAst)).isEqualTo("true && [1].exists(x, x > 0)");
    assertThat(CEL.createProgram(CEL.check(mutatedAst).getAst()).eval()).isEqualTo(true);
    assertThat(mutatedAst2.getSource().getMacroCalls()).hasSize(1);
    assertThat(CEL_UNPARSER.unparse(mutatedAst2)).isEqualTo("[1].exists(x, x > 0) && false");
    assertThat(CEL.createProgram(CEL.check(mutatedAst2).getAst()).eval()).isEqualTo(false);
  }

  @Test
  public void replaceSubtree_macroInsertedIntoExistingMacro_macroCallPopulated() throws Exception {
    CelAbstractSyntaxTree ast = CEL.compile("[1].exists(x, x > 0 && true)").getAst();
    CelAbstractSyntaxTree ast2 = CEL.compile("[2].exists(y, y > 0)").getAst();

    CelAbstractSyntaxTree mutatedAst =
        MutableAst.replaceSubtree(ast, ast2, 9); // Replace true with the ast2 maro expr

    assertThat(mutatedAst.getSource().getMacroCalls()).hasSize(2);
    assertThat(CEL_UNPARSER.unparse(mutatedAst))
        .isEqualTo("[1].exists(x, x > 0 && [2].exists(y, y > 0))");
    assertThat(CEL.createProgram(CEL.check(mutatedAst).getAst()).eval()).isEqualTo(true);
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
        MutableAst.replaceSubtreeWithNewBindMacro(
            ast,
            variableName,
            CelExpr.ofConstantExpr(0, CelConstant.ofValue(3L)),
            resultExpr,
            CelNavigableAst.fromAst(ast).getRoot().expr().id());

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
        MutableAst.replaceSubtreeWithNewBindMacro(
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
        MutableAst.replaceSubtreeWithNewBindMacro(
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
        MutableAst.replaceSubtreeWithNewBindMacro(
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
        MutableAst.replaceSubtreeWithNewBindMacro(
            mutatedAst,
            nestedVariableName,
            CelExpr.ofConstantExpr(0, CelConstant.ofValue(3L)),
            resultExpr,
            1);

    assertThat(mutatedAst.getSource().getMacroCalls()).hasSize(2);
    assertThat(CEL.createProgram(CEL.check(mutatedAst).getAst()).eval()).isEqualTo(8);
    assertThat(CEL_UNPARSER.unparse(mutatedAst))
        .isEqualTo("cel.bind(@r1, 3, cel.bind(@r0, 1, @r0 + @r0) + @r1 + @r1)");
    assertConsistentMacroCalls(mutatedAst);
  }

  @Test
  public void replaceSubtree_macroReplacedWithConstExpr_macroCallCleared() throws Exception {
    CelAbstractSyntaxTree ast =
        CEL.compile("[1].exists(x, x > 0) && [2].exists(x, x > 0)").getAst();
    CelAbstractSyntaxTree ast2 = CEL.compile("1").getAst();

    CelAbstractSyntaxTree mutatedAst =
        MutableAst.replaceSubtree(ast, ast2, CelNavigableAst.fromAst(ast).getRoot().expr().id());

    assertThat(mutatedAst.getSource().getMacroCalls()).isEmpty();
    assertThat(CEL_UNPARSER.unparse(mutatedAst)).isEqualTo("1");
    assertThat(CEL.createProgram(CEL.check(mutatedAst).getAst()).eval()).isEqualTo(1);
  }

  @Test
  public void globalCallExpr_replaceRoot() throws Exception {
    // Tree shape (brackets are expr IDs):
    //           + [4]
    //      + [2]       x [5]
    //  1 [1]     2 [3]
    CelAbstractSyntaxTree ast = CEL.compile("1 + 2 + x").getAst();

    CelAbstractSyntaxTree replacedAst =
        MutableAst.replaceSubtree(
            ast, CelExpr.newBuilder().setConstant(CelConstant.ofValue(10)).build(), 4);

    assertThat(replacedAst.getExpr()).isEqualTo(CelExpr.ofConstantExpr(1, CelConstant.ofValue(10)));
  }

  @Test
  public void globalCallExpr_replaceLeaf() throws Exception {
    // Tree shape (brackets are expr IDs):
    //           + [4]
    //      + [2]       x [5]
    //  1 [1]     2 [3]
    CelAbstractSyntaxTree ast = CEL.compile("1 + 2 + x").getAst();

    CelAbstractSyntaxTree replacedAst =
        MutableAst.replaceSubtree(
            ast, CelExpr.newBuilder().setConstant(CelConstant.ofValue(10)).build(), 1);

    assertThat(CEL_UNPARSER.unparse(replacedAst)).isEqualTo("10 + 2 + x");
  }

  @Test
  public void globalCallExpr_replaceMiddleBranch() throws Exception {
    // Tree shape (brackets are expr IDs):
    //           + [4]
    //      + [2]       x [5]
    //  1 [1]     2 [3]
    CelAbstractSyntaxTree ast = CEL.compile("1 + 2 + x").getAst();

    CelAbstractSyntaxTree replacedAst =
        MutableAst.replaceSubtree(
            ast, CelExpr.newBuilder().setConstant(CelConstant.ofValue(10)).build(), 2);

    assertThat(CEL_UNPARSER.unparse(replacedAst)).isEqualTo("10 + x");
  }

  @Test
  public void globalCallExpr_replaceMiddleBranch_withCallExpr() throws Exception {
    // Tree shape (brackets are expr IDs):
    //           + [4]
    //      + [2]       x [5]
    //  1 [1]     2 [3]
    CelAbstractSyntaxTree ast = CEL.compile("1 + 2 + x").getAst();
    CelAbstractSyntaxTree ast2 = CEL.compile("4 + 5 + 6").getAst();

    CelAbstractSyntaxTree replacedAst = MutableAst.replaceSubtree(ast, ast2.getExpr(), 2);

    assertThat(CEL_UNPARSER.unparse(replacedAst)).isEqualTo("4 + 5 + 6 + x");
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

    CelAbstractSyntaxTree replacedAst =
        MutableAst.replaceSubtree(
            ast, CelExpr.newBuilder().setConstant(CelConstant.ofValue(20)).build(), 3);

    assertThat(CEL_UNPARSER.unparse(replacedAst)).isEqualTo("10.func(20.func(5))");
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

    CelAbstractSyntaxTree replacedAst =
        MutableAst.replaceSubtree(
            ast, CelExpr.newBuilder().setConstant(CelConstant.ofValue(20)).build(), 5);

    assertThat(CEL_UNPARSER.unparse(replacedAst)).isEqualTo("10.func(4.func(20))");
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

    CelAbstractSyntaxTree replacedAst =
        MutableAst.replaceSubtree(
            ast, CelExpr.newBuilder().setConstant(CelConstant.ofValue(20)).build(), 1);

    assertThat(CEL_UNPARSER.unparse(replacedAst)).isEqualTo("20.func(4.func(5))");
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

    CelAbstractSyntaxTree replacedAst =
        MutableAst.replaceSubtree(
            ast, CelExpr.newBuilder().setConstant(CelConstant.ofValue(20)).build(), 4);

    assertThat(CEL_UNPARSER.unparse(replacedAst)).isEqualTo("10.func(20)");
  }

  @Test
  public void select_replaceField() throws Exception {
    // Tree shape (brackets are expr IDs):
    //              + [2]
    //         5 [1]        select [4]
    //                msg [3]
    CelAbstractSyntaxTree ast = CEL.compile("5 + msg.single_int64").getAst();

    CelAbstractSyntaxTree replacedAst =
        MutableAst.replaceSubtree(
            ast,
            CelExpr.newBuilder()
                .setSelect(
                    CelSelect.newBuilder()
                        .setField("single_sint32")
                        .setOperand(
                            CelExpr.newBuilder()
                                .setIdent(CelIdent.newBuilder().setName("test").build())
                                .build())
                        .build())
                .build(),
            4);

    assertThat(CEL_UNPARSER.unparse(replacedAst)).isEqualTo("5 + test.single_sint32");
  }

  @Test
  public void select_replaceOperand() throws Exception {
    // Tree shape (brackets are expr IDs):
    //              + [2]
    //         5 [1]        select [4]
    //                msg [3]
    CelAbstractSyntaxTree ast = CEL.compile("5 + msg.single_int64").getAst();

    CelAbstractSyntaxTree replacedAst =
        MutableAst.replaceSubtree(
            ast,
            CelExpr.newBuilder().setIdent(CelIdent.newBuilder().setName("test").build()).build(),
            3);

    assertThat(CEL_UNPARSER.unparse(replacedAst)).isEqualTo("5 + test.single_int64");
  }

  @Test
  public void list_replaceElement() throws Exception {
    // Tree shape (brackets are expr IDs):
    //        list [1]
    //  2 [2]  3 [3]  4 [4]
    CelAbstractSyntaxTree ast = CEL.compile("[2, 3, 4]").getAst();

    CelAbstractSyntaxTree replacedAst =
        MutableAst.replaceSubtree(
            ast, CelExpr.newBuilder().setConstant(CelConstant.ofValue(5)).build(), 4);

    assertThat(CEL_UNPARSER.unparse(replacedAst)).isEqualTo("[2, 3, 5]");
  }

  @Test
  public void createStruct_replaceValue() throws Exception {
    // Tree shape (brackets are expr IDs):
    //        TestAllTypes [1]
    //             single_int64 [2]
    //                  2 [3]
    CelAbstractSyntaxTree ast = CEL.compile("TestAllTypes{single_int64: 2}").getAst();

    CelAbstractSyntaxTree replacedAst =
        MutableAst.replaceSubtree(
            ast, CelExpr.newBuilder().setConstant(CelConstant.ofValue(5)).build(), 3);

    assertThat(CEL_UNPARSER.unparse(replacedAst)).isEqualTo("TestAllTypes{single_int64: 5}");
  }

  @Test
  public void createMap_replaceKey() throws Exception {
    // Tree shape (brackets are expr IDs):
    //        map [1]
    //       map_entry [2]
    //     'a' [3] : 1 [4]
    CelAbstractSyntaxTree ast = CEL.compile("{'a': 1}").getAst();

    CelAbstractSyntaxTree replacedAst =
        MutableAst.replaceSubtree(
            ast, CelExpr.newBuilder().setConstant(CelConstant.ofValue(5)).build(), 3);

    assertThat(CEL_UNPARSER.unparse(replacedAst)).isEqualTo("{5: 1}");
  }

  @Test
  public void createMap_replaceValue() throws Exception {
    // Tree shape (brackets are expr IDs):
    //        map [1]
    //       map_entry [2]
    //     'a' [3] : 1 [4]
    CelAbstractSyntaxTree ast = CEL.compile("{'a': 1}").getAst();

    CelAbstractSyntaxTree replacedAst =
        MutableAst.replaceSubtree(
            ast, CelExpr.newBuilder().setConstant(CelConstant.ofValue(5)).build(), 4);

    assertThat(CEL_UNPARSER.unparse(replacedAst)).isEqualTo("{\"a\": 5}");
  }

  @Test
  public void comprehension_replaceIterRange() throws Exception {
    CelAbstractSyntaxTree ast = CEL.compile("[true].exists(i, i)").getAst();

    CelAbstractSyntaxTree replacedAst =
        MutableAst.replaceSubtree(
            ast, CelExpr.newBuilder().setConstant(CelConstant.ofValue(false)).build(), 2);

    assertThat(CEL_UNPARSER.unparse(replacedAst)).isEqualTo("[false].exists(i, i)");
    assertThat(CEL.createProgram(CEL.check(replacedAst).getAst()).eval()).isEqualTo(false);
    assertConsistentMacroCalls(ast);
  }

  @Test
  public void comprehension_replaceAccuInit() throws Exception {
    CelAbstractSyntaxTree ast = CEL.compile("[false].exists(i, i)").getAst();

    CelAbstractSyntaxTree replacedAst =
        MutableAst.replaceSubtree(
            ast, CelExpr.newBuilder().setConstant(CelConstant.ofValue(true)).build(), 6);

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

    CelAbstractSyntaxTree replacedAst =
        MutableAst.replaceSubtree(
            ast,
            CelExpr.newBuilder().setIdent(CelIdent.newBuilder().setName("test").build()).build(),
            5);

    assertThat(CEL_UNPARSER.unparse(replacedAst)).isEqualTo("[false].exists(i, test)");
    assertConsistentMacroCalls(ast);
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
                  assertThat(node).isEqualTo(e);
                }
              });
    }
  }
}
