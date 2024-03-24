package dev.cel.common.ast;

import dev.cel.common.CelAbstractSyntaxTree;
import dev.cel.common.CelSource;

public final class MutableAst {
    private final MutableExpr mutatedExpr;
    private final CelSource.Builder sourceBuilder;

    public MutableExpr mutableExpr() {
      return mutatedExpr;
    }

    public CelSource.Builder sourceBuilder() {
      return sourceBuilder;
    }

    public CelAbstractSyntaxTree toParsedAst() {
      return CelAbstractSyntaxTree.newParsedAst(MutableExprConverter.fromMutableExpr(mutatedExpr), sourceBuilder.build());
    }

    public static MutableAst fromCelAst(CelAbstractSyntaxTree ast) {
      return of(MutableExprConverter.fromCelExpr(ast.getExpr()), ast.getSource().toBuilder());
    }

    public static MutableAst of(MutableExpr mutableExpr, CelSource.Builder sourceBuilder) {
      return new MutableAst(mutableExpr, sourceBuilder);
    }

    private MutableAst(MutableExpr mutatedExpr, CelSource.Builder sourceBuilder) {
      this.mutatedExpr = mutatedExpr;
      this.sourceBuilder = sourceBuilder;
    }
  }
