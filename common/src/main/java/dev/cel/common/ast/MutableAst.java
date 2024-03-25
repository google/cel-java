package dev.cel.common.ast;

import dev.cel.common.CelAbstractSyntaxTree;
import dev.cel.common.CelSource;

public final class MutableAst {
    private final MutableExpr mutatedExpr;
    private final CelSource.Builder source;

    public MutableExpr mutableExpr() {
      return mutatedExpr;
    }

    public CelSource.Builder source() {
      return source;
    }

    public CelAbstractSyntaxTree toParsedAst() {
      return CelAbstractSyntaxTree.newParsedAst(MutableExprConverter.fromMutableExpr(mutatedExpr), source.build());
    }

    public static MutableAst fromCelAst(CelAbstractSyntaxTree ast) {
      return of(MutableExprConverter.fromCelExpr(ast.getExpr()), ast.getSource().toBuilder());
    }

    public static MutableAst of(MutableExpr mutableExpr, CelSource.Builder sourceBuilder) {
      return new MutableAst(mutableExpr, sourceBuilder);
    }

    private MutableAst(MutableExpr mutatedExpr, CelSource.Builder source) {
      this.mutatedExpr = mutatedExpr;
      this.source = source;
    }
  }
