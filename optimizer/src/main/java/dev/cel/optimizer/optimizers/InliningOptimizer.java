// Copyright 2026 Google LLC
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

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import dev.cel.bundle.Cel;
import dev.cel.common.CelAbstractSyntaxTree;
import dev.cel.common.CelMutableAst;
import dev.cel.common.Operator;
import dev.cel.common.ast.CelConstant;
import dev.cel.common.ast.CelExpr.ExprKind.Kind;
import dev.cel.common.ast.CelMutableExpr;
import dev.cel.common.ast.CelMutableExpr.CelMutableCall;
import dev.cel.common.ast.CelMutableExpr.CelMutableComprehension;
import dev.cel.common.navigation.CelNavigableMutableAst;
import dev.cel.common.navigation.CelNavigableMutableExpr;
import dev.cel.common.types.CelKind;
import dev.cel.common.types.CelType;
import dev.cel.common.types.SimpleType;
import dev.cel.common.values.NullValue;
import dev.cel.optimizer.AstMutator;
import dev.cel.optimizer.CelAstOptimizer;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Performs optimization for inlining variables within function calls and select statements with
 * their associated AST.
 */
public final class InliningOptimizer implements CelAstOptimizer {

  private final ImmutableList<InlineVariable> inlineVariables;
  private final AstMutator astMutator;

  public static InliningOptimizer newInstance(InlineVariable... inlineVariables) {
    return newInstance(InliningOptions.newBuilder().build(), ImmutableList.copyOf(inlineVariables));
  }

  public static InliningOptimizer newInstance(
      InliningOptions options, InlineVariable... inlineVariables) {
    return newInstance(options, ImmutableList.copyOf(inlineVariables));
  }

  public static InliningOptimizer newInstance(
      InliningOptions options, Iterable<InlineVariable> inlineVariables) {
    return new InliningOptimizer(options, ImmutableList.copyOf(inlineVariables));
  }

  @Override
  public OptimizationResult optimize(CelAbstractSyntaxTree ast, Cel cel) {
    CelMutableAst mutableAst = CelMutableAst.fromCelAst(ast);
    for (InlineVariable inlineVariable : inlineVariables) {
      ImmutableList<CelNavigableMutableExpr> inlinableExprs =
          CelNavigableMutableAst.fromAst(mutableAst)
              .getRoot()
              .allNodes()
              .filter(node -> canInline(node, inlineVariable.name()))
              .collect(toImmutableList());

      for (CelNavigableMutableExpr inlinableExpr : inlinableExprs) {
        CelMutableAst inlineVariableAst = CelMutableAst.fromCelAst(inlineVariable.ast());
        CelMutableExpr replacementExpr = inlineVariableAst.expr();

        if (inlinableExpr.getKind().equals(Kind.SELECT)
            && inlinableExpr.expr().select().testOnly()) {
          replacementExpr = rewritePresenceExpr(inlineVariable, replacementExpr);
        }

        mutableAst =
            astMutator.replaceSubtree(
                mutableAst,
                CelMutableAst.of(replacementExpr, inlineVariableAst.source()),
                inlinableExpr.id());
      }
    }

    return OptimizationResult.create(astMutator.renumberIdsConsecutively(mutableAst).toParsedAst());
  }

  private static CelMutableExpr rewritePresenceExpr(
      InlineVariable inlineVariable, CelMutableExpr replacementExpr) {
    if (replacementExpr.getKind().equals(Kind.SELECT)) {
      // Preserve testOnly property for Select replacements (has(A) -> has(B))
      replacementExpr.select().setTestOnly(true);
      return replacementExpr;
    }

    CelType replacementType =
        inlineVariable
            .ast()
            .getType(replacementExpr.id())
            .orElseThrow(() -> new NoSuchElementException("Type is not present."));
    if (isSizerType(replacementType)) {
      // has(X) -> X.size() != 0
      return CelMutableExpr.ofCall(
          CelMutableCall.create(
              Operator.NOT_EQUALS.getFunction(),
              CelMutableExpr.ofCall(CelMutableCall.create(replacementExpr, "size")),
              CelMutableExpr.ofConstant(CelConstant.ofValue(0))));
    } else if (replacementType.isAssignableFrom(SimpleType.NULL_TYPE)) {
      // has(X) -> X != null
      // This covers well-known wrapper types
      return CelMutableExpr.ofCall(
          CelMutableCall.create(
              Operator.NOT_EQUALS.getFunction(),
              replacementExpr,
              CelMutableExpr.ofConstant(CelConstant.ofValue(NullValue.NULL_VALUE))));
    }

    throw new IllegalArgumentException(
        String.format(
            "Unable to inline expression type %s into presence test", replacementType.name()));
  }

  private static boolean isSizerType(CelType type) {
    return type.kind().equals(CelKind.LIST)
        || type.kind().equals(CelKind.MAP)
        || type.equals(SimpleType.STRING)
        || type.equals(SimpleType.BYTES);
  }

  private static boolean canInline(CelNavigableMutableExpr node, String identifier) {
    boolean matches = maybeToQualifiedName(node).map(name -> name.equals(identifier)).orElse(false);

    if (!matches) {
      return false;
    }

    for (CelNavigableMutableExpr p = node.parent().orElse(null);
        p != null;
        p = p.parent().orElse(null)) {
      if (p.getKind() != Kind.COMPREHENSION) {
        continue;
      }

      CelMutableComprehension comp = p.expr().comprehension();
      boolean shadows =
          Stream.of(comp.iterVar(), comp.iterVar2(), comp.accuVar()).anyMatch(identifier::equals);

      if (shadows) {
        return false;
      }
    }

    return true;
  }

  private static Optional<String> maybeToQualifiedName(CelNavigableMutableExpr node) {
    if (node.getKind().equals(Kind.IDENT)) {
      return Optional.of(node.expr().ident().name());
    }

    if (node.getKind().equals(Kind.SELECT)) {
      return node.children()
          .findFirst()
          .flatMap(InliningOptimizer::maybeToQualifiedName)
          .map(operandName -> operandName + "." + node.expr().select().field());
    }

    return Optional.empty();
  }

  /** Represents a variable to be inlined. */
  @AutoValue
  public abstract static class InlineVariable {
    public abstract String name();

    public abstract CelAbstractSyntaxTree ast();

    /**
     * Creates a new {@link InlineVariable} with the given name and AST.
     *
     * <p>The name must be a simple identifier or a qualified name (e.g. "a.b.c") and cannot be an
     * internal variable (starting with @).
     */
    public static InlineVariable of(String name, CelAbstractSyntaxTree ast) {
      if (name.startsWith("@")) {
        throw new IllegalArgumentException("Internal variables cannot be inlined: " + name);
      }
      return new AutoValue_InliningOptimizer_InlineVariable(name, ast);
    }
  }

  /** Options to configure how Inlining behaves. */
  @AutoValue
  public abstract static class InliningOptions {
    public abstract int maxIterationLimit();

    /** Builder for configuring the {@link InliningOptimizer.InliningOptions}. */
    @AutoValue.Builder
    public abstract static class Builder {

      /**
       * Limit the number of iteration while inlining variables. An exception is thrown if the
       * iteration count exceeds the set value.
       */
      public abstract InliningOptions.Builder maxIterationLimit(int value);

      public abstract InliningOptimizer.InliningOptions build();

      Builder() {}
    }

    /** Returns a new options builder with recommended defaults pre-configured. */
    public static InliningOptimizer.InliningOptions.Builder newBuilder() {
      return new AutoValue_InliningOptimizer_InliningOptions.Builder().maxIterationLimit(400);
    }

    InliningOptions() {}
  }

  private InliningOptimizer(
      InliningOptions options, ImmutableList<InlineVariable> inlineVariables) {
    this.inlineVariables = inlineVariables;
    this.astMutator = AstMutator.newInstance(options.maxIterationLimit());
  }
}
