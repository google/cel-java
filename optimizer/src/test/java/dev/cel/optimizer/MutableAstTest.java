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
import dev.cel.bundle.Cel;
import dev.cel.bundle.CelFactory;
import dev.cel.common.CelAbstractSyntaxTree;
import dev.cel.common.CelFunctionDecl;
import dev.cel.common.CelOptions;
import dev.cel.common.CelOverloadDecl;
import dev.cel.common.ast.CelConstant;
import dev.cel.common.ast.CelExpr;
import dev.cel.common.ast.CelExpr.CelIdent;
import dev.cel.common.ast.CelExpr.CelSelect;
import dev.cel.common.navigation.CelNavigableAst;
import dev.cel.common.navigation.CelNavigableExpr;
import dev.cel.common.types.SimpleType;
import dev.cel.common.types.StructTypeReference;
import dev.cel.extensions.CelOptionalLibrary;
import dev.cel.parser.CelStandardMacro;
import dev.cel.parser.CelUnparser;
import dev.cel.parser.CelUnparserFactory;
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
          .addCompilerLibraries(CelOptionalLibrary.INSTANCE)
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
    assertConsistentMacroCalls(ast);
    assertThat(CEL.createProgram(CEL.check(replacedAst).getAst()).eval()).isEqualTo(false);
  }

  @Test
  public void comprehension_replaceAccuInit() throws Exception {
    CelAbstractSyntaxTree ast = CEL.compile("[false].exists(i, i)").getAst();

    CelAbstractSyntaxTree replacedAst =
        MutableAst.replaceSubtree(
            ast, CelExpr.newBuilder().setConstant(CelConstant.ofValue(true)).build(), 6);

    assertThat(CEL_UNPARSER.unparse(replacedAst)).isEqualTo("[false].exists(i, i)");
    assertConsistentMacroCalls(ast);
    assertThat(CEL.createProgram(CEL.check(replacedAst).getAst()).eval()).isEqualTo(true);
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

  @Test
  public void comprehension_astContainsDuplicateNodes() throws Exception {
    CelAbstractSyntaxTree ast = CEL.compile("[{\"a\": 1}].map(i, i)").getAst();

    // AST contains two duplicate expr (ID: 9). Just ensure that it doesn't throw.
    CelAbstractSyntaxTree replacedAst =
        MutableAst.replaceSubtree(ast, CelExpr.newBuilder().build(), -1);

    assertThat(CEL_UNPARSER.unparse(replacedAst)).isEqualTo("[{\"a\": 1}].map(i, i)");
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
