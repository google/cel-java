// Copyright 2022 Google LLC
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

package dev.cel.checker;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.extensions.proto.ProtoTruth.assertThat;

import dev.cel.expr.Constant;
import dev.cel.expr.Expr;
import com.google.auto.value.AutoValue;
import dev.cel.common.CelAbstractSyntaxTree;
import dev.cel.common.types.SimpleType;
import dev.cel.compiler.CelCompiler;
import dev.cel.compiler.CelCompilerFactory;
import dev.cel.expr.conformance.proto3.TestAllTypes;
import dev.cel.parser.CelStandardMacro;
import dev.cel.parser.Operator;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class CelProtoExprVisitorTest {

  @AutoValue
  public abstract static class VisitedReference {
    public abstract Optional<Constant> constant();

    public abstract Optional<Expr.Ident> identifier();

    public abstract Optional<Expr.Select> select();

    public abstract Optional<Expr.Call> call();

    public abstract Optional<Expr.CreateStruct> createStruct();

    public abstract Optional<Expr.CreateList> createList();

    public abstract Optional<Expr.Comprehension> comprehension();

    @AutoValue.Builder
    public abstract static class Builder {
      public abstract Builder setConstant(Constant value);

      public abstract Builder setIdentifier(Expr.Ident value);

      public abstract Builder setSelect(Expr.Select value);

      public abstract Builder setCall(Expr.Call value);

      public abstract Builder setCreateStruct(Expr.CreateStruct value);

      public abstract Builder setCreateList(Expr.CreateList value);

      public abstract Builder setComprehension(Expr.Comprehension value);

      public abstract VisitedReference build();
    }

    public static VisitedReference.Builder newBuilder() {
      return new AutoValue_CelProtoExprVisitorTest_VisitedReference.Builder();
    }
  }

  private static class CelExprVisitorTestImpl extends CelProtoExprVisitor {
    private final VisitedReference.Builder visitedReference = VisitedReference.newBuilder();

    @Override
    protected void visit(Expr expr, Constant constant) {
      visitedReference.setConstant(constant);
    }

    @Override
    protected void visit(Expr expr, Expr.Ident ident) {
      visitedReference.setIdentifier(ident);
    }

    @Override
    protected void visit(Expr expr, Expr.Select select) {
      visitedReference.setSelect(select);
      super.visit(expr, select);
    }

    @Override
    protected void visit(Expr expr, Expr.Call call) {
      visitedReference.setCall(call);
      super.visit(expr, call);
    }

    @Override
    protected void visit(Expr expr, Expr.CreateStruct createStruct) {
      visitedReference.setCreateStruct(createStruct);
      super.visit(expr, createStruct);
    }

    @Override
    protected void visit(Expr expr, Expr.CreateList createList) {
      visitedReference.setCreateList(createList);
      super.visit(expr, createList);
    }

    @Override
    protected void visit(Expr expr, Expr.Comprehension comprehension) {
      visitedReference.setComprehension(comprehension);
      super.visit(expr, comprehension);
    }
  }

  private CelExprVisitorTestImpl exprVisitorImpl;

  @Before
  public void setUp() {
    exprVisitorImpl = new CelExprVisitorTestImpl();
  }

  @Test
  public void visitConstant() throws Exception {
    CelCompiler celCompiler = CelCompilerFactory.standardCelCompilerBuilder().build();
    CelAbstractSyntaxTree ast = celCompiler.compile("'a'").getAst();

    exprVisitorImpl.visit(ast);

    VisitedReference visited = exprVisitorImpl.visitedReference.build();
    assertThat(visited)
        .isEqualTo(
            VisitedReference.newBuilder()
                .setConstant(Constant.newBuilder().setStringValue("a").build())
                .build());
  }

  @Test
  public void visitIdentifier() throws Exception {
    CelCompiler celCompiler =
        CelCompilerFactory.standardCelCompilerBuilder().addVar("ident", SimpleType.INT).build();
    CelAbstractSyntaxTree ast = celCompiler.compile("ident").getAst();

    exprVisitorImpl.visit(ast);

    VisitedReference visited = exprVisitorImpl.visitedReference.build();
    assertThat(visited)
        .isEqualTo(
            VisitedReference.newBuilder()
                .setIdentifier(Expr.Ident.newBuilder().setName("ident").build())
                .build());
  }

  @Test
  public void visitSelect() throws Exception {
    CelCompiler celCompiler =
        CelCompilerFactory.standardCelCompilerBuilder()
            .addMessageTypes(TestAllTypes.getDescriptor())
            .setContainer(TestAllTypes.getDescriptor().getFullName())
            .build();
    CelAbstractSyntaxTree ast = celCompiler.compile("TestAllTypes{}.single_int64").getAst();

    exprVisitorImpl.visit(ast);

    VisitedReference visited = exprVisitorImpl.visitedReference.build();
    assertThat(visited)
        .isEqualTo(
            VisitedReference.newBuilder()
                .setCreateStruct(
                    Expr.CreateStruct.newBuilder().setMessageName("TestAllTypes").build())
                .setSelect(
                    Expr.Select.newBuilder()
                        .setOperand(
                            Expr.newBuilder()
                                .setId(1)
                                .setStructExpr(
                                    Expr.CreateStruct.newBuilder()
                                        .setMessageName("TestAllTypes")
                                        .build()))
                        .setField("single_int64")
                        .build())
                .build());
  }

  @Test
  public void visitCall() throws Exception {
    CelCompiler celCompiler = CelCompilerFactory.standardCelCompilerBuilder().build();
    CelAbstractSyntaxTree ast = celCompiler.compile("size('a')").getAst();

    exprVisitorImpl.visit(ast);

    VisitedReference visited = exprVisitorImpl.visitedReference.build();
    Constant stringVal = Constant.newBuilder().setStringValue("a").build();
    assertThat(visited)
        .isEqualTo(
            VisitedReference.newBuilder()
                .setConstant(stringVal)
                .setCall(
                    Expr.Call.newBuilder()
                        .setFunction("size")
                        .addArgs(Expr.newBuilder().setId(2).setConstExpr(stringVal))
                        .build())
                .build());
  }

  @Test
  public void visitCreateStruct() throws Exception {
    CelCompiler celCompiler =
        CelCompilerFactory.standardCelCompilerBuilder()
            .addMessageTypes(TestAllTypes.getDescriptor())
            .setContainer(TestAllTypes.getDescriptor().getFullName())
            .build();
    CelAbstractSyntaxTree ast = celCompiler.compile("TestAllTypes{}").getAst();

    exprVisitorImpl.visit(ast);

    VisitedReference visited = exprVisitorImpl.visitedReference.build();
    assertThat(visited)
        .isEqualTo(
            VisitedReference.newBuilder()
                .setCreateStruct(
                    Expr.CreateStruct.newBuilder().setMessageName("TestAllTypes").build())
                .build());
  }

  @Test
  public void visitCreateList() throws Exception {
    CelCompiler celCompiler = CelCompilerFactory.standardCelCompilerBuilder().build();
    CelAbstractSyntaxTree ast = celCompiler.compile("[1, 1]").getAst();

    exprVisitorImpl.visit(ast);

    VisitedReference visited = exprVisitorImpl.visitedReference.build();
    Constant integerVal = Constant.newBuilder().setInt64Value(1).build();
    assertThat(visited)
        .isEqualTo(
            VisitedReference.newBuilder()
                .setConstant(integerVal)
                .setCreateList(
                    Expr.CreateList.newBuilder()
                        .addElements(Expr.newBuilder().setId(2).setConstExpr(integerVal))
                        .addElements(Expr.newBuilder().setId(3).setConstExpr(integerVal))
                        .build())
                .build());
  }

  @Test
  public void visitComprehension() throws Exception {
    CelCompiler celCompiler =
        CelCompilerFactory.standardCelCompilerBuilder()
            .setStandardMacros(CelStandardMacro.ALL)
            .build();
    CelAbstractSyntaxTree ast = celCompiler.compile("[1, 1].all(x, x == 1)").getAst();

    exprVisitorImpl.visit(ast);

    Expr.Comprehension comprehension =
        exprVisitorImpl.visitedReference.build().comprehension().get();
    assertThat(comprehension.getIterVar()).isEqualTo("x");
    assertThat(comprehension.getIterRange().getListExpr().getElementsList())
        .containsExactly(
            Expr.newBuilder()
                .setId(2)
                .setConstExpr(Constant.newBuilder().setInt64Value(1).build())
                .build(),
            Expr.newBuilder()
                .setId(3)
                .setConstExpr(Constant.newBuilder().setInt64Value(1).build())
                .build());
    assertThat(comprehension.getAccuInit().getConstExpr())
        .isEqualTo(Constant.newBuilder().setBoolValue(true).build());
    assertThat(comprehension.getLoopCondition().getCallExpr().getFunction())
        .isEqualTo(Operator.NOT_STRICTLY_FALSE.getFunction());
    assertThat(comprehension.getLoopStep().getCallExpr().getFunction())
        .isEqualTo(Operator.LOGICAL_AND.getFunction());
    assertThat(comprehension.getLoopStep().getCallExpr().getArgsCount()).isEqualTo(2);
  }
}
