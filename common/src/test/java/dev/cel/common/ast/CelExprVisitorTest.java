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

package dev.cel.common.ast;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.common.truth.Truth8;
import dev.cel.common.CelAbstractSyntaxTree;
import dev.cel.common.ast.CelExpr.CelCall;
import dev.cel.common.ast.CelExpr.CelComprehension;
import dev.cel.common.ast.CelExpr.CelCreateList;
import dev.cel.common.ast.CelExpr.CelCreateMap;
import dev.cel.common.ast.CelExpr.CelCreateStruct;
import dev.cel.common.ast.CelExpr.CelCreateStruct.Entry;
import dev.cel.common.ast.CelExpr.CelIdent;
import dev.cel.common.ast.CelExpr.CelSelect;
import dev.cel.common.types.SimpleType;
import dev.cel.compiler.CelCompiler;
import dev.cel.compiler.CelCompilerFactory;
import dev.cel.parser.CelStandardMacro;
import dev.cel.parser.Operator;
import dev.cel.testing.testdata.proto3.TestAllTypesProto.TestAllTypes;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class CelExprVisitorTest {

  @AutoValue
  public abstract static class VisitedReference {
    public abstract Optional<CelConstant> constant();

    public abstract Optional<CelIdent> identifier();

    public abstract Optional<CelSelect> select();

    public abstract Optional<CelCall> call();

    public abstract Optional<CelCreateStruct> createStruct();

    public abstract Optional<CelCreateMap> createMap();

    public abstract Optional<CelCreateList> createList();

    public abstract Optional<CelComprehension> comprehension();

    public abstract ImmutableList<CelExpr> arguments();

    @AutoValue.Builder
    public abstract static class Builder {
      public abstract Builder setConstant(CelConstant value);

      public abstract Builder setIdentifier(CelIdent value);

      public abstract Builder setSelect(CelSelect value);

      public abstract Builder setCall(CelCall value);

      public abstract Builder setCreateStruct(CelCreateStruct value);

      public abstract Builder setCreateMap(CelCreateMap value);

      public abstract Builder setCreateList(CelCreateList value);

      public abstract Builder setComprehension(CelComprehension value);

      public abstract Builder setArguments(ImmutableList<CelExpr> value);

      abstract ImmutableList.Builder<CelExpr> argumentsBuilder();

      public Builder addArguments(CelExpr... arguments) {
        argumentsBuilder().add(arguments);
        return this;
      }

      public abstract VisitedReference build();
    }

    public static Builder newBuilder() {
      return new AutoValue_CelExprVisitorTest_VisitedReference.Builder();
    }
  }

  private static class CelExprVisitorTestImpl extends CelExprVisitor {
    private final VisitedReference.Builder visitedReference = VisitedReference.newBuilder();

    @Override
    protected void visit(CelExpr expr, CelConstant constant) {
      visitedReference.setConstant(constant);
    }

    @Override
    protected void visit(CelExpr expr, CelIdent ident) {
      visitedReference.setIdentifier(ident);
    }

    @Override
    protected void visit(CelExpr expr, CelSelect select) {
      visitedReference.setSelect(select);
      super.visit(expr, select);
    }

    @Override
    protected void visit(CelExpr expr, CelCall call) {
      visitedReference.setCall(call);
      super.visit(expr, call);
    }

    @Override
    protected void visit(CelExpr expr, CelCreateStruct createStruct) {
      visitedReference.setCreateStruct(createStruct);
      super.visit(expr, createStruct);
    }

    @Override
    protected void visit(CelExpr expr, CelCreateMap createMap) {
      visitedReference.setCreateMap(createMap);
      super.visit(expr, createMap);
    }

    @Override
    protected void visit(CelExpr expr, CelCreateList createList) {
      visitedReference.setCreateList(createList);
      super.visit(expr, createList);
    }

    @Override
    protected void visit(CelExpr expr, CelComprehension comprehension) {
      visitedReference.setComprehension(comprehension);
      super.visit(expr, comprehension);
    }

    @Override
    protected void visitArg(CelExpr expr, CelExpr arg, int argNum) {
      visitedReference.addArguments(arg);
      super.visitArg(expr, arg, argNum);
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
        .isEqualTo(VisitedReference.newBuilder().setConstant(CelConstant.ofValue("a")).build());
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
                .setIdentifier(CelIdent.newBuilder().setName("ident").build())
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
                    CelCreateStruct.newBuilder().setMessageName("TestAllTypes").build())
                .setSelect(
                    CelSelect.newBuilder()
                        .setOperand(
                            CelExpr.newBuilder()
                                .setId(1)
                                .setCreateStruct(
                                    CelCreateStruct.newBuilder()
                                        .setMessageName("TestAllTypes")
                                        .build())
                                .build())
                        .setField("single_int64")
                        .build())
                .build());
  }

  @Test
  public void visitCall() throws Exception {
    CelCompiler celCompiler = CelCompilerFactory.standardCelCompilerBuilder().build();
    CelAbstractSyntaxTree ast = celCompiler.compile("'hi'.contains('h')").getAst();

    exprVisitorImpl.visit(ast);

    VisitedReference visited = exprVisitorImpl.visitedReference.build();
    CelConstant stringVal = CelConstant.ofValue("h");
    assertThat(visited)
        .isEqualTo(
            VisitedReference.newBuilder()
                .setConstant(stringVal)
                .setCall(
                    CelCall.newBuilder()
                        .setFunction("contains")
                        .setTarget(CelExpr.ofConstantExpr(1, CelConstant.ofValue("hi")))
                        .addArgs(CelExpr.ofConstantExpr(3, stringVal))
                        .build())
                .addArguments(CelExpr.ofConstantExpr(3, stringVal))
                .build());
  }

  @Test
  public void visitCreateStruct_fieldkey() throws Exception {
    CelCompiler celCompiler =
        CelCompilerFactory.standardCelCompilerBuilder()
            .addMessageTypes(TestAllTypes.getDescriptor())
            .setContainer(TestAllTypes.getDescriptor().getFullName())
            .build();
    CelAbstractSyntaxTree ast = celCompiler.compile("TestAllTypes{single_int64: 1}").getAst();

    exprVisitorImpl.visit(ast);
    VisitedReference visited = exprVisitorImpl.visitedReference.build();

    CelConstant longConstant = CelConstant.ofValue(1);
    assertThat(visited)
        .isEqualTo(
            VisitedReference.newBuilder()
                .setConstant(longConstant)
                .setCreateStruct(
                    CelCreateStruct.newBuilder()
                        .addEntries(
                            Entry.newBuilder()
                                .setId(2)
                                .setFieldKey("single_int64")
                                .setValue(CelExpr.ofConstantExpr(3, longConstant))
                                .build())
                        .setMessageName("TestAllTypes")
                        .build())
                .build());
  }

  @Test
  public void visitCreateMap() throws Exception {
    CelCompiler celCompiler = CelCompilerFactory.standardCelCompilerBuilder().build();
    CelAbstractSyntaxTree ast = celCompiler.compile("{'a': 'b'}").getAst();

    exprVisitorImpl.visit(ast);
    VisitedReference visited = exprVisitorImpl.visitedReference.build();

    assertThat(visited)
        .isEqualTo(
            VisitedReference.newBuilder()
                .setConstant(CelConstant.ofValue("b"))
                .setCreateMap(
                    CelCreateMap.newBuilder()
                        .addEntries(
                            CelCreateMap.Entry.newBuilder()
                                .setId(2)
                                .setKey(CelExpr.ofConstantExpr(3, CelConstant.ofValue("a")))
                                .setValue(CelExpr.ofConstantExpr(4, CelConstant.ofValue("b")))
                                .build())
                        .build())
                .build());
  }

  @Test
  public void visitCreateList() throws Exception {
    CelCompiler celCompiler = CelCompilerFactory.standardCelCompilerBuilder().build();
    CelAbstractSyntaxTree ast = celCompiler.compile("[1, 1]").getAst();

    exprVisitorImpl.visit(ast.getExpr());

    VisitedReference visited = exprVisitorImpl.visitedReference.build();
    CelConstant integerVal = CelConstant.ofValue(1);
    assertThat(visited)
        .isEqualTo(
            VisitedReference.newBuilder()
                .setConstant(integerVal)
                .setCreateList(
                    CelCreateList.newBuilder()
                        .addElements(CelExpr.newBuilder().setId(2).setConstant(integerVal).build())
                        .addElements(CelExpr.newBuilder().setId(3).setConstant(integerVal).build())
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

    exprVisitorImpl.visit(ast.getExpr());

    VisitedReference visitedReference = exprVisitorImpl.visitedReference.build();
    CelComprehension comprehension = visitedReference.comprehension().get();
    ImmutableList<CelExpr> iterRangeElements =
        ImmutableList.of(
            CelExpr.ofConstantExpr(2, CelConstant.ofValue(1)),
            CelExpr.ofConstantExpr(3, CelConstant.ofValue(1)));

    assertThat(comprehension.iterVar()).isEqualTo("x");
    assertThat(comprehension.iterRange().createList().elements()).isEqualTo(iterRangeElements);
    assertThat(comprehension.accuInit().constant()).isEqualTo(CelConstant.ofValue(true));
    assertThat(comprehension.loopCondition().call().function())
        .isEqualTo(Operator.NOT_STRICTLY_FALSE.getFunction());
    assertThat(comprehension.loopStep().call().function())
        .isEqualTo(Operator.LOGICAL_AND.getFunction());
    assertThat(comprehension.loopStep().call().args()).hasSize(2);
    assertThat(visitedReference.createList().get().elements()).isEqualTo(iterRangeElements);
    Truth8.assertThat(visitedReference.identifier())
        .hasValue(CelIdent.newBuilder().setName("__result__").build());
    assertThat(visitedReference.arguments()).hasSize(10);
  }

  @Test
  public void visitNotSet_throwsException() {
    IllegalArgumentException e =
        assertThrows(
            IllegalArgumentException.class,
            () -> exprVisitorImpl.visit(CelExpr.newBuilder().build()));

    assertThat(e).hasMessageThat().contains("unexpected expr kind: NOT_SET");
  }
}
