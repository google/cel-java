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

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import com.google.testing.junit.testparameterinjector.TestParameterInjector;
import dev.cel.bundle.Cel;
import dev.cel.bundle.CelFactory;
import dev.cel.common.CelAbstractSyntaxTree;
import dev.cel.common.CelFunctionDecl;
import dev.cel.common.CelOverloadDecl;
import dev.cel.common.CelSource;
import dev.cel.common.ast.CelConstant;
import dev.cel.common.ast.CelExpr;
import dev.cel.common.ast.CelExpr.CelIdent;
import dev.cel.common.ast.CelExpr.CelSelect;
import dev.cel.common.types.SimpleType;
import dev.cel.common.types.StructTypeReference;
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
          .addMessageTypes(TestAllTypes.getDescriptor())
          .setContainer("dev.cel.testing.testdata.proto3")
          .addVar("msg", StructTypeReference.create(TestAllTypes.getDescriptor().getFullName()))
          .addVar("x", SimpleType.INT)
          .build();

  private static final CelUnparser CEL_UNPARSER = CelUnparserFactory.newUnparser();

  @Test
  public void constExpr() throws Exception {
    CelExpr root = CEL.compile("10").getAst().getExpr();

    CelExpr replacedExpr =
        MutableAst.replaceSubtree(
            root, CelExpr.newBuilder().setConstant(CelConstant.ofValue(true)).build(), 1);

    assertThat(replacedExpr).isEqualTo(CelExpr.ofConstantExpr(1, CelConstant.ofValue(true)));
  }

  @Test
  public void globalCallExpr_replaceRoot() throws Exception {
    // Tree shape (brackets are expr IDs):
    //           + [4]
    //      + [2]       x [5]
    //  1 [1]     2 [3]
    CelExpr root = CEL.compile("1 + 2 + x").getAst().getExpr();

    CelExpr replacedRoot =
        MutableAst.replaceSubtree(
            root, CelExpr.newBuilder().setConstant(CelConstant.ofValue(10)).build(), 4);

    assertThat(replacedRoot).isEqualTo(CelExpr.ofConstantExpr(1, CelConstant.ofValue(10)));
  }

  @Test
  public void globalCallExpr_replaceLeaf() throws Exception {
    // Tree shape (brackets are expr IDs):
    //           + [4]
    //      + [2]       x [5]
    //  1 [1]     2 [3]
    CelExpr root = CEL.compile("1 + 2 + x").getAst().getExpr();

    CelExpr replacedRoot =
        MutableAst.replaceSubtree(
            root, CelExpr.newBuilder().setConstant(CelConstant.ofValue(10)).build(), 1);

    assertThat(getUnparsedExpression(replacedRoot)).isEqualTo("10 + 2 + x");
  }

  @Test
  public void globalCallExpr_replaceMiddleBranch() throws Exception {
    // Tree shape (brackets are expr IDs):
    //           + [4]
    //      + [2]       x [5]
    //  1 [1]     2 [3]
    CelExpr root = CEL.compile("1 + 2 + x").getAst().getExpr();

    CelExpr replacedRoot =
        MutableAst.replaceSubtree(
            root, CelExpr.newBuilder().setConstant(CelConstant.ofValue(10)).build(), 2);

    assertThat(getUnparsedExpression(replacedRoot)).isEqualTo("10 + x");
  }

  @Test
  public void globalCallExpr_replaceMiddleBranch_withCallExpr() throws Exception {
    // Tree shape (brackets are expr IDs):
    //           + [4]
    //      + [2]       x [5]
    //  1 [1]     2 [3]
    CelExpr root = CEL.compile("1 + 2 + x").getAst().getExpr();
    CelExpr root2 = CEL.compile("4 + 5 + 6").getAst().getExpr();

    CelExpr replacedRoot = MutableAst.replaceSubtree(root, root2, 2);

    assertThat(getUnparsedExpression(replacedRoot)).isEqualTo("4 + 5 + 6 + x");
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

    CelExpr replacedRoot =
        MutableAst.replaceSubtree(
            ast.getExpr(), CelExpr.newBuilder().setConstant(CelConstant.ofValue(20)).build(), 3);

    assertThat(getUnparsedExpression(replacedRoot)).isEqualTo("10.func(20.func(5))");
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

    CelExpr replacedRoot =
        MutableAst.replaceSubtree(
            ast.getExpr(), CelExpr.newBuilder().setConstant(CelConstant.ofValue(20)).build(), 5);

    assertThat(getUnparsedExpression(replacedRoot)).isEqualTo("10.func(4.func(20))");
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

    CelExpr replacedRoot =
        MutableAst.replaceSubtree(
            ast.getExpr(), CelExpr.newBuilder().setConstant(CelConstant.ofValue(20)).build(), 1);

    assertThat(getUnparsedExpression(replacedRoot)).isEqualTo("20.func(4.func(5))");
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

    CelExpr replacedRoot =
        MutableAst.replaceSubtree(
            ast.getExpr(), CelExpr.newBuilder().setConstant(CelConstant.ofValue(20)).build(), 4);

    assertThat(getUnparsedExpression(replacedRoot)).isEqualTo("10.func(20)");
  }

  @Test
  public void select_replaceField() throws Exception {
    // Tree shape (brackets are expr IDs):
    //              + [2]
    //         5 [1]        select [4]
    //                msg [3]
    CelAbstractSyntaxTree ast = CEL.compile("5 + msg.single_int64").getAst();

    CelExpr replacedRoot =
        MutableAst.replaceSubtree(
            ast.getExpr(),
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

    assertThat(getUnparsedExpression(replacedRoot)).isEqualTo("5 + test.single_sint32");
  }

  @Test
  public void select_replaceOperand() throws Exception {
    // Tree shape (brackets are expr IDs):
    //              + [2]
    //         5 [1]        select [4]
    //                msg [3]
    CelAbstractSyntaxTree ast = CEL.compile("5 + msg.single_int64").getAst();

    CelExpr replacedRoot =
        MutableAst.replaceSubtree(
            ast.getExpr(),
            CelExpr.newBuilder().setIdent(CelIdent.newBuilder().setName("test").build()).build(),
            3);

    assertThat(getUnparsedExpression(replacedRoot)).isEqualTo("5 + test.single_int64");
  }

  @Test
  public void list_replaceElement() throws Exception {
    // Tree shape (brackets are expr IDs):
    //        list [1]
    //  2 [2]  3 [3]  4 [4]
    CelAbstractSyntaxTree ast = CEL.compile("[2, 3, 4]").getAst();

    CelExpr replacedRoot =
        MutableAst.replaceSubtree(
            ast.getExpr(), CelExpr.newBuilder().setConstant(CelConstant.ofValue(5)).build(), 4);

    assertThat(getUnparsedExpression(replacedRoot)).isEqualTo("[2, 3, 5]");
  }

  @Test
  public void createStruct_replaceValue() throws Exception {
    // Tree shape (brackets are expr IDs):
    //        TestAllTypes [1]
    //             single_int64 [2]
    //                  2 [3]
    CelAbstractSyntaxTree ast = CEL.compile("TestAllTypes{single_int64: 2}").getAst();

    CelExpr replacedRoot =
        MutableAst.replaceSubtree(
            ast.getExpr(), CelExpr.newBuilder().setConstant(CelConstant.ofValue(5)).build(), 3);

    assertThat(getUnparsedExpression(replacedRoot)).isEqualTo("TestAllTypes{single_int64: 5}");
  }

  @Test
  public void createMap_replaceKey() throws Exception {
    // Tree shape (brackets are expr IDs):
    //        map [1]
    //       map_entry [2]
    //     'a' [3] : 1 [4]
    CelAbstractSyntaxTree ast = CEL.compile("{'a': 1}").getAst();

    CelExpr replacedRoot =
        MutableAst.replaceSubtree(
            ast.getExpr(), CelExpr.newBuilder().setConstant(CelConstant.ofValue(5)).build(), 3);

    assertThat(getUnparsedExpression(replacedRoot)).isEqualTo("{5: 1}");
  }

  @Test
  public void createMap_replaceValue() throws Exception {
    // Tree shape (brackets are expr IDs):
    //        map [1]
    //       map_entry [2]
    //     'a' [3] : 1 [4]
    CelAbstractSyntaxTree ast = CEL.compile("{'a': 1}").getAst();

    CelExpr replacedRoot =
        MutableAst.replaceSubtree(
            ast.getExpr(), CelExpr.newBuilder().setConstant(CelConstant.ofValue(5)).build(), 4);

    assertThat(getUnparsedExpression(replacedRoot)).isEqualTo("{\"a\": 5}");
  }

  @Test
  public void invalidCelExprKind_throwsException() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            MutableAst.replaceSubtree(
                CelExpr.ofConstantExpr(1, CelConstant.ofValue("test")), CelExpr.ofNotSet(1), 1));
  }

  private static String getUnparsedExpression(CelExpr expr) {
    CelAbstractSyntaxTree ast =
        CelAbstractSyntaxTree.newParsedAst(expr, CelSource.newBuilder().build());
    return CEL_UNPARSER.unparse(ast);
  }
}
