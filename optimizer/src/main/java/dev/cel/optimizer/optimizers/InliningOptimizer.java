package dev.cel.optimizer.optimizers;

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import dev.cel.bundle.Cel;
import dev.cel.common.CelAbstractSyntaxTree;
import dev.cel.common.CelMutableAst;
import dev.cel.common.ast.CelExpr.ExprKind.Kind;
import dev.cel.common.ast.CelMutableExpr.CelMutableComprehension;
import dev.cel.common.ast.CelMutableExprConverter;
import dev.cel.common.navigation.CelNavigableMutableAst;
import dev.cel.common.navigation.CelNavigableMutableExpr;
import dev.cel.optimizer.AstMutator;
import dev.cel.optimizer.CelAstOptimizer;
import dev.cel.optimizer.CelOptimizationException;

public final class InliningOptimizer implements CelAstOptimizer {

  private final ImmutableList<InlineVariable> inlineVariables;
  private final AstMutator astMutator;

  public static InliningOptimizer newInstance(InlineVariable... inlineVariables) {
    return newInstance(InliningOptions.newBuilder().build(), ImmutableList.copyOf(inlineVariables));
  }

  public static InliningOptimizer newInstance(InliningOptions options, InlineVariable... inlineVariables) {
    return newInstance(options, ImmutableList.copyOf(inlineVariables));
  }

  public static InliningOptimizer newInstance(InliningOptions options, Iterable<InlineVariable> inlineVariables) {
    return new InliningOptimizer(options, ImmutableList.copyOf(inlineVariables));
  }

  @Override
  public OptimizationResult optimize(CelAbstractSyntaxTree ast, Cel cel)
      throws CelOptimizationException {
    CelMutableAst mutableAst = CelMutableAst.fromCelAst(ast);
    for (InlineVariable inlineVariable : inlineVariables) {
        ImmutableList<CelNavigableMutableExpr> inlinableExprs =
          CelNavigableMutableAst.fromAst(mutableAst)
              .getRoot()
              .allNodes()
              .filter(node -> node.getKind().equals(Kind.IDENT))
              .filter(node -> node.expr().ident().name().equals(inlineVariable.name()))
              .filter(expr -> canInline(expr, inlineVariable.name()))
              .collect(toImmutableList());

      for (CelNavigableMutableExpr inlinableExpr : inlinableExprs) {
        mutableAst = astMutator.replaceSubtree(mutableAst, CelMutableExprConverter.fromCelExpr(inlineVariable.ast().getExpr()), inlinableExpr.id());
      }
    }

    return OptimizationResult.create(mutableAst.toParsedAst());
  }

  private static boolean canInline(CelNavigableMutableExpr expr, String identifier) {
    for (CelNavigableMutableExpr p = expr.parent().orElse(null); p != null; p = p.parent().orElse(null)) {
      if (p.getKind() != Kind.COMPREHENSION) {
        continue;
      }

      CelMutableComprehension comp = p.expr().comprehension();

      if (comp.iterVar().equals(identifier) || comp.iterVar2().equals(identifier) || comp.accuVar().equals(identifier)) {
        return false;
      }
    }
    return true;
  }

  @AutoValue
  public abstract static class InlineVariable {
    public abstract String name();
    public abstract CelAbstractSyntaxTree ast();

    public static InlineVariable of(String name, CelAbstractSyntaxTree ast) {
      if (name.startsWith("@")) {
        throw new IllegalArgumentException("Internal variables cannot be inlined: " + name);
      }
      return new AutoValue_InliningOptimizer_InlineVariable(name, ast);
    }
  }

  /**
   * TODO
   */
  @AutoValue
  public abstract static class InliningOptions {
    public abstract int maxIterationLimit();

    /** Builder for configuring the {@link InliningOptimizer.InliningOptions}. */
    @AutoValue.Builder
    public abstract static class Builder {

      /**
       * Limit the number of iteration while inlining variables. An exception is thrown if
       * the iteration count exceeds the set value.
       */
      public abstract InliningOptions.Builder maxIterationLimit(int value);

      public abstract InliningOptimizer.InliningOptions build();

      Builder() {}
    }

    /** Returns a new options builder with recommended defaults pre-configured. */
    public static InliningOptimizer.InliningOptions.Builder newBuilder() {
      return new AutoValue_InliningOptimizer_InliningOptions.Builder()
          .maxIterationLimit(400);
    }

    InliningOptions() {}
  }

  private InliningOptimizer(InliningOptions options, ImmutableList<InlineVariable> inlineVariables) {
    this.inlineVariables = inlineVariables;
    this.astMutator = AstMutator.newInstance(options.maxIterationLimit());
  }
}
