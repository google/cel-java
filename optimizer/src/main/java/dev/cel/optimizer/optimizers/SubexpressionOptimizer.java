// Copyright 2024 Google LLC
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

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static java.util.stream.Collectors.toCollection;

import com.google.auto.value.AutoValue;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.base.Verify;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Streams;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import dev.cel.bundle.Cel;
import dev.cel.bundle.CelBuilder;
import dev.cel.common.CelAbstractSyntaxTree;
import dev.cel.common.CelFunctionDecl;
import dev.cel.common.CelMutableAst;
import dev.cel.common.CelMutableSource;
import dev.cel.common.CelOverloadDecl;
import dev.cel.common.CelSource;
import dev.cel.common.CelSource.Extension;
import dev.cel.common.CelSource.Extension.Component;
import dev.cel.common.CelSource.Extension.Version;
import dev.cel.common.CelValidationException;
import dev.cel.common.CelVarDecl;
import dev.cel.common.ast.CelExpr;
import dev.cel.common.ast.CelExpr.CelCall;
import dev.cel.common.ast.CelExpr.ExprKind.Kind;
import dev.cel.common.ast.CelMutableExpr;
import dev.cel.common.ast.CelMutableExprConverter;
import dev.cel.common.navigation.CelNavigableExpr;
import dev.cel.common.navigation.CelNavigableMutableAst;
import dev.cel.common.navigation.CelNavigableMutableExpr;
import dev.cel.common.navigation.TraversalOrder;
import dev.cel.common.types.CelType;
import dev.cel.common.types.ListType;
import dev.cel.common.types.SimpleType;
import dev.cel.optimizer.AstMutator;
import dev.cel.optimizer.AstMutator.MangledComprehensionAst;
import dev.cel.optimizer.CelAstOptimizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Performs Common Subexpression Elimination.
 *
 * <pre>
 * Subexpressions are extracted into `cel.bind` calls. For example, the expression below:
 *
 * {@code
 *    message.child.text_map[x].startsWith("hello") && message.child.text_map[x].endsWith("world")
 * }
 *
 * will be optimized into the following form:
 *
 * {@code
 *    cel.bind(@r0, message.child.text_map[x],
 *        @r0.startsWith("hello") && @r0.endsWith("world"))
 * }
 *
 * Or, using the equivalent form of cel.@block (requires special runtime support):
 * {@code
 *    cel.block([message.child.text_map[x]],
 *        @index0.startsWith("hello") && @index1.endsWith("world"))
 * }
 * </pre>
 */
public class SubexpressionOptimizer implements CelAstOptimizer {

  private static final SubexpressionOptimizer INSTANCE =
      new SubexpressionOptimizer(SubexpressionOptimizerOptions.newBuilder().build());
  private static final String BIND_IDENTIFIER_PREFIX = "@r";
  private static final String MANGLED_COMPREHENSION_ITER_VAR_PREFIX = "@it";
  private static final String MANGLED_COMPREHENSION_ACCU_VAR_PREFIX = "@ac";
  private static final String CEL_BLOCK_FUNCTION = "cel.@block";
  private static final String BLOCK_INDEX_PREFIX = "@index";
  private static final Extension CEL_BLOCK_AST_EXTENSION_TAG =
      Extension.create("cel_block", Version.of(1L, 1L), Component.COMPONENT_RUNTIME);

  private final SubexpressionOptimizerOptions cseOptions;
  private final AstMutator astMutator;
  private final ImmutableSet<String> cseEliminableFunctions;

  /**
   * Returns a default instance of common subexpression elimination optimizer with preconfigured
   * defaults.
   */
  public static SubexpressionOptimizer getInstance() {
    return INSTANCE;
  }

  /**
   * Returns a new instance of common subexpression elimination optimizer configured with the
   * provided {@link SubexpressionOptimizerOptions}.
   */
  public static SubexpressionOptimizer newInstance(SubexpressionOptimizerOptions cseOptions) {
    return new SubexpressionOptimizer(cseOptions);
  }

  @Override
  public OptimizationResult optimize(CelAbstractSyntaxTree ast, Cel cel) {
    OptimizationResult result =
        cseOptions.enableCelBlock() ? optimizeUsingCelBlock(ast, cel) : optimizeUsingCelBind(ast);

    verifyOptimizedAstCorrectness(result.optimizedAst());

    return result;
  }

  private OptimizationResult optimizeUsingCelBlock(CelAbstractSyntaxTree ast, Cel cel) {
    CelMutableAst astToModify = CelMutableAst.fromCelAst(ast);
    if (!cseOptions.populateMacroCalls()) {
      astToModify.source().clearMacroCalls();
    }

    MangledComprehensionAst mangledComprehensionAst =
        astMutator.mangleComprehensionIdentifierNames(
            astToModify,
            MANGLED_COMPREHENSION_ITER_VAR_PREFIX,
            MANGLED_COMPREHENSION_ACCU_VAR_PREFIX,
            /* incrementSerially= */ false);
    astToModify = mangledComprehensionAst.mutableAst();
    CelMutableSource sourceToModify = astToModify.source();

    int blockIdentifierIndex = 0;
    int iterCount;
    ArrayList<CelMutableExpr> subexpressions = new ArrayList<>();

    for (iterCount = 0; iterCount < cseOptions.iterationLimit(); iterCount++) {
      CelNavigableMutableAst navAst = CelNavigableMutableAst.fromAst(astToModify);
      List<CelMutableExpr> cseCandidates = getCseCandidates(navAst);
      if (cseCandidates.isEmpty()) {
        break;
      }

      subexpressions.add(cseCandidates.get(0));

      String blockIdentifier = BLOCK_INDEX_PREFIX + blockIdentifierIndex++;

      // Replace all CSE candidates with new block index identifier
      for (CelMutableExpr cseCandidate : cseCandidates) {
        iterCount++;

        astToModify =
            astMutator.replaceSubtree(
                navAst,
                CelNavigableMutableAst.fromAst(
                    CelMutableAst.of(
                        CelMutableExpr.ofIdent(blockIdentifier), navAst.getAst().source())),
                cseCandidate.id());

        // Retain the existing macro calls in case if the block identifiers are replacing a subtree
        // that contains a comprehension.
        sourceToModify.addAllMacroCalls(astToModify.source().getMacroCalls());
        astToModify = CelMutableAst.of(astToModify.expr(), sourceToModify);
      }
    }

    if (iterCount >= cseOptions.iterationLimit()) {
      throw new IllegalStateException("Max iteration count reached.");
    }

    if (iterCount == 0) {
      // No modification has been made.
      return OptimizationResult.create(astToModify.toParsedAst());
    }

    ImmutableList.Builder<CelVarDecl> newVarDecls = ImmutableList.builder();

    // Add all mangled comprehension identifiers to the environment, so that the subexpressions can
    // retain context to them.
    mangledComprehensionAst
        .mangledComprehensionMap()
        .forEach(
            (name, type) -> {
              type.iterVarType()
                  .ifPresent(
                      iterVarType ->
                          newVarDecls.add(
                              CelVarDecl.newVarDeclaration(name.iterVarName(), iterVarType)));
              newVarDecls.add(CelVarDecl.newVarDeclaration(name.resultName(), type.resultType()));
            });

    // Type-check all sub-expressions then create new block index identifiers.
    newVarDecls.addAll(newBlockIndexVariableDeclarations(cel, newVarDecls.build(), subexpressions));

    // Wrap the optimized expression in cel.block
    astToModify =
        astMutator.wrapAstWithNewCelBlock(CEL_BLOCK_FUNCTION, astToModify, subexpressions);
    astToModify = astMutator.renumberIdsConsecutively(astToModify);

    // Tag the AST with cel.block designated as an extension
    CelAbstractSyntaxTree optimizedAst = tagAstExtension(astToModify.toParsedAst());

    return OptimizationResult.create(
        optimizedAst,
        newVarDecls.build(),
        ImmutableList.of(newCelBlockFunctionDecl(ast.getResultType())));
  }

  /**
   * Asserts that the optimized AST has no correctness issues.
   *
   * @throws com.google.common.base.VerifyException if the optimized AST is malformed.
   */
  @VisibleForTesting
  static void verifyOptimizedAstCorrectness(CelAbstractSyntaxTree ast) {
    CelNavigableExpr celNavigableExpr = CelNavigableExpr.fromExpr(ast.getExpr());

    ImmutableList<CelExpr> allCelBlocks =
        celNavigableExpr
            .allNodes()
            .map(CelNavigableExpr::expr)
            .filter(expr -> expr.callOrDefault().function().equals(CEL_BLOCK_FUNCTION))
            .collect(toImmutableList());
    if (allCelBlocks.isEmpty()) {
      return;
    }

    CelExpr celBlockExpr = allCelBlocks.get(0);
    Verify.verify(
        allCelBlocks.size() == 1,
        "Expected 1 cel.block function to be present but found %s",
        allCelBlocks.size());
    Verify.verify(
        celNavigableExpr.expr().equals(celBlockExpr), "Expected cel.block to be present at root");

    // Assert correctness on block indices used in subexpressions
    CelCall celBlockCall = celBlockExpr.call();
    ImmutableList<CelExpr> subexprs = celBlockCall.args().get(0).list().elements();
    for (int i = 0; i < subexprs.size(); i++) {
      verifyBlockIndex(subexprs.get(i), i);
    }

    // Assert correctness on block indices used in block result
    CelExpr blockResult = celBlockCall.args().get(1);
    verifyBlockIndex(blockResult, subexprs.size());
    boolean resultHasAtLeastOneBlockIndex =
        CelNavigableExpr.fromExpr(blockResult)
            .allNodes()
            .map(CelNavigableExpr::expr)
            .anyMatch(expr -> expr.identOrDefault().name().startsWith(BLOCK_INDEX_PREFIX));
    Verify.verify(
        resultHasAtLeastOneBlockIndex,
        "Expected at least one reference of index in cel.block result");
  }

  private static void verifyBlockIndex(CelExpr celExpr, int maxIndexValue) {
    boolean areAllIndicesValid =
        CelNavigableExpr.fromExpr(celExpr)
            .allNodes()
            .map(CelNavigableExpr::expr)
            .filter(expr -> expr.identOrDefault().name().startsWith(BLOCK_INDEX_PREFIX))
            .map(CelExpr::ident)
            .allMatch(
                blockIdent ->
                    Integer.parseInt(blockIdent.name().substring(BLOCK_INDEX_PREFIX.length()))
                        < maxIndexValue);
    Verify.verify(
        areAllIndicesValid,
        "Illegal block index found. The index value must be less than %s. Expr: %s",
        maxIndexValue,
        celExpr);
  }

  private static CelAbstractSyntaxTree tagAstExtension(CelAbstractSyntaxTree ast) {
    // Tag the extension
    CelSource.Builder celSourceBuilder =
        ast.getSource().toBuilder().addAllExtensions(CEL_BLOCK_AST_EXTENSION_TAG);

    return CelAbstractSyntaxTree.newParsedAst(ast.getExpr(), celSourceBuilder.build());
  }

  /**
   * Creates a list of numbered identifiers from the subexpressions that act as an indexer to
   * cel.block (ex: @index0, @index1..). Each subexpressions are type-checked, then its result type
   * is used as the new identifiers' types.
   */
  private static ImmutableList<CelVarDecl> newBlockIndexVariableDeclarations(
      Cel cel, ImmutableList<CelVarDecl> mangledVarDecls, List<CelMutableExpr> subexpressions) {
    // The resulting type of the subexpressions will likely be different from the
    // entire expression's expected result type.
    CelBuilder celBuilder = cel.toCelBuilder().setResultType(SimpleType.DYN);
    // Add the mangled comprehension variables to the environment for type-checking subexpressions
    // to succeed
    celBuilder.addVarDeclarations(mangledVarDecls);

    ImmutableList.Builder<CelVarDecl> varDeclBuilder = ImmutableList.builder();
    for (int i = 0; i < subexpressions.size(); i++) {
      CelMutableExpr subexpression = subexpressions.get(i);

      CelAbstractSyntaxTree subAst =
          CelAbstractSyntaxTree.newParsedAst(
              CelMutableExprConverter.fromMutableExpr(subexpression),
              CelSource.newBuilder().build());

      try {
        subAst = celBuilder.build().check(subAst).getAst();
      } catch (CelValidationException e) {
        throw new IllegalStateException("Failed to type-check subexpression", e);
      }

      CelVarDecl indexVar = CelVarDecl.newVarDeclaration("@index" + i, subAst.getResultType());
      celBuilder.addVarDeclarations(indexVar);
      varDeclBuilder.add(indexVar);
    }

    return varDeclBuilder.build();
  }

  private OptimizationResult optimizeUsingCelBind(CelAbstractSyntaxTree ast) {
    CelMutableAst astToModify = CelMutableAst.fromCelAst(ast);
    if (!cseOptions.populateMacroCalls()) {
      astToModify.source().clearMacroCalls();
    }

    astToModify =
        astMutator
            .mangleComprehensionIdentifierNames(
                astToModify,
                MANGLED_COMPREHENSION_ITER_VAR_PREFIX,
                MANGLED_COMPREHENSION_ACCU_VAR_PREFIX,
                /* incrementSerially= */ true)
            .mutableAst();
    CelMutableSource sourceToModify = astToModify.source();

    int bindIdentifierIndex = 0;
    int iterCount;
    for (iterCount = 0; iterCount < cseOptions.iterationLimit(); iterCount++) {
      CelNavigableMutableAst navAst = CelNavigableMutableAst.fromAst(astToModify);
      List<CelMutableExpr> cseCandidates = getCseCandidates(navAst);
      if (cseCandidates.isEmpty()) {
        break;
      }

      String bindIdentifier = BIND_IDENTIFIER_PREFIX + bindIdentifierIndex;
      bindIdentifierIndex++;

      // Replace all CSE candidates with new bind identifier
      for (CelMutableExpr cseCandidate : cseCandidates) {
        iterCount++;

        astToModify =
            astMutator.replaceSubtree(
                astToModify, CelMutableExpr.ofIdent(bindIdentifier), cseCandidate.id());
      }

      // Find LCA to insert the new cel.bind macro into.
      CelNavigableMutableExpr lca = getLca(navAst, bindIdentifier);

      // Insert the new bind call
      CelMutableExpr subexpressionToBind = cseCandidates.get(0);
      // Re-add the macro source for bind identifiers that might have been lost from previous
      // iteration of CSE
      astToModify.source().addAllMacroCalls(sourceToModify.getMacroCalls());
      astToModify =
          astMutator.replaceSubtreeWithNewBindMacro(
              astToModify,
              bindIdentifier,
              subexpressionToBind,
              lca.expr(),
              lca.id(),
              cseOptions.populateMacroCalls());

      // Retain the existing macro calls in case if the bind identifiers are replacing a subtree
      // that contains a comprehension.
      sourceToModify = astToModify.source();
    }

    if (iterCount >= cseOptions.iterationLimit()) {
      throw new IllegalStateException("Max iteration count reached.");
    }

    if (iterCount == 0) {
      // No modification has been made.
      return OptimizationResult.create(astToModify.toParsedAst());
    }

    astToModify = astMutator.renumberIdsConsecutively(astToModify);

    return OptimizationResult.create(astToModify.toParsedAst());
  }

  private static CelNavigableMutableExpr getLca(
      CelNavigableMutableAst navAst, String boundIdentifier) {
    CelNavigableMutableExpr root = navAst.getRoot();
    ImmutableList<CelNavigableMutableExpr> allNodesWithIdentifier =
        root.allNodes()
            .filter(
                node ->
                    node.getKind().equals(Kind.IDENT)
                        && node.expr().ident().name().equals(boundIdentifier))
            .collect(toImmutableList());

    if (allNodesWithIdentifier.size() < 2) {
      throw new IllegalStateException("Expected at least 2 bound identifiers to be present.");
    }

    CelNavigableMutableExpr lca = root;
    long lcaAncestorCount = 0;
    HashMap<Long, Long> ancestors = new HashMap<>();
    for (CelNavigableMutableExpr navigableExpr : allNodesWithIdentifier) {
      Optional<CelNavigableMutableExpr> maybeParent = Optional.of(navigableExpr);
      while (maybeParent.isPresent()) {
        CelNavigableMutableExpr parent = maybeParent.get();
        if (!ancestors.containsKey(parent.id())) {
          ancestors.put(parent.id(), 1L);
          continue;
        }

        long ancestorCount = ancestors.get(parent.id());
        if (lcaAncestorCount < ancestorCount
            || (lcaAncestorCount == ancestorCount && lca.depth() < parent.depth())) {
          lca = parent;
          lcaAncestorCount = ancestorCount;
        }

        ancestors.put(parent.id(), ancestorCount + 1);
        maybeParent = parent.parent();
      }
    }

    return lca;
  }

  private List<CelMutableExpr> getCseCandidates(CelNavigableMutableAst navAst) {
    if (cseOptions.enableCelBlock() && cseOptions.subexpressionMaxRecursionDepth() > 0) {
      return getCseCandidatesWithRecursionDepth(
          navAst, cseOptions.subexpressionMaxRecursionDepth());
    } else {
      return getCseCandidatesWithCommonSubexpr(navAst);
    }
  }

  /**
   * Retrieves all subexpr candidates based on the recursion limit even if there's no duplicate
   * subexpr found.
   */
  private List<CelMutableExpr> getCseCandidatesWithRecursionDepth(
      CelNavigableMutableAst navAst, int recursionLimit) {
    Preconditions.checkArgument(recursionLimit > 0);
    Set<CelMutableExpr> ineligibleExprs = getIneligibleExprsFromComprehensionBranches(navAst);
    ImmutableList<CelNavigableMutableExpr> descendants =
        navAst
            .getRoot()
            .descendants(TraversalOrder.PRE_ORDER)
            .filter(node -> canEliminate(node, ineligibleExprs))
            .filter(node -> node.height() <= recursionLimit)
            .sorted(Comparator.comparingInt(CelNavigableMutableExpr::height).reversed())
            .collect(toImmutableList());
    if (descendants.isEmpty()) {
      return new ArrayList<>();
    }

    List<CelMutableExpr> cseCandidates = getCseCandidatesWithCommonSubexpr(descendants);
    if (!cseCandidates.isEmpty()) {
      return cseCandidates;
    }

    // If there's no common subexpr, just return the one with the highest height that's still below
    // the recursion limit, but only if it actually needs to be extracted due to exceeding the
    // recursion limit.
    boolean astHasMoreExtractableSubexprs =
        navAst
            .getRoot()
            .allNodes(TraversalOrder.POST_ORDER)
            .filter(node -> node.height() > recursionLimit)
            .anyMatch(node -> canEliminate(node, ineligibleExprs));
    if (astHasMoreExtractableSubexprs) {
      cseCandidates.add(descendants.get(0).expr());
      return cseCandidates;
    }

    // The height of the remaining subexpression is already below the recursion limit. No need to
    // extract.
    return new ArrayList<>();
  }

  private List<CelMutableExpr> getCseCandidatesWithCommonSubexpr(CelNavigableMutableAst navAst) {
    Set<CelMutableExpr> ineligibleExprs = getIneligibleExprsFromComprehensionBranches(navAst);
    ImmutableList<CelNavigableMutableExpr> allNodes =
        navAst
            .getRoot()
            .allNodes(TraversalOrder.PRE_ORDER)
            .filter(node -> canEliminate(node, ineligibleExprs))
            .collect(toImmutableList());

    return getCseCandidatesWithCommonSubexpr(allNodes);
  }

  private List<CelMutableExpr> getCseCandidatesWithCommonSubexpr(
      ImmutableList<CelNavigableMutableExpr> allNodes) {
    CelMutableExpr normalizedCseCandidate = null;
    HashSet<CelMutableExpr> semanticallyEqualNodes = new HashSet<>();
    for (CelNavigableMutableExpr node : allNodes) {
      // Normalize the expr to test semantic equivalence.
      CelMutableExpr normalizedExpr = normalizeForEquality(node.expr());
      if (semanticallyEqualNodes.contains(normalizedExpr)) {
        normalizedCseCandidate = normalizedExpr;
        break;
      }

      semanticallyEqualNodes.add(normalizedExpr);
    }

    List<CelMutableExpr> cseCandidates = new ArrayList<>();
    if (normalizedCseCandidate == null) {
      return cseCandidates;
    }

    for (CelNavigableMutableExpr node : allNodes) {
      // Normalize the expr to test semantic equivalence.
      CelMutableExpr normalizedExpr = normalizeForEquality(node.expr());
      if (normalizedExpr.equals(normalizedCseCandidate)) {
        cseCandidates.add(node.expr());
      }
    }

    return cseCandidates;
  }

  private boolean canEliminate(
      CelNavigableMutableExpr navigableExpr, Set<CelMutableExpr> ineligibleExprs) {
    return !navigableExpr.getKind().equals(Kind.CONSTANT)
        && !navigableExpr.getKind().equals(Kind.IDENT)
        && !(navigableExpr.getKind().equals(Kind.IDENT)
            && navigableExpr.expr().ident().name().startsWith(BIND_IDENTIFIER_PREFIX))
        // Exclude empty lists (cel.bind sets this for iterRange).
        && !(navigableExpr.getKind().equals(Kind.LIST)
            && navigableExpr.expr().list().elements().isEmpty())
        && containsEliminableFunctionOnly(navigableExpr)
        && !ineligibleExprs.contains(navigableExpr.expr());
  }

  /**
   * Collects a set of nodes that are not eligible to be optimized from comprehension branches.
   *
   * <p>All nodes from accumulator initializer and loop condition are not eligible to be optimized
   * as that can interfere with scoping of shadowed variables.
   */
  private static Set<CelMutableExpr> getIneligibleExprsFromComprehensionBranches(
      CelNavigableMutableAst navAst) {
    HashSet<CelMutableExpr> ineligibleExprs = new HashSet<>();
    navAst
        .getRoot()
        .allNodes()
        .filter(node -> node.getKind().equals(Kind.COMPREHENSION))
        .forEach(
            node -> {
              Set<CelMutableExpr> nodes =
                  Streams.concat(
                          CelNavigableMutableExpr.fromExpr(node.expr().comprehension().accuInit())
                              .allNodes(),
                          CelNavigableMutableExpr.fromExpr(
                                  node.expr().comprehension().loopCondition())
                              .allNodes())
                      .map(CelNavigableMutableExpr::expr)
                      .collect(toCollection(HashSet::new));

              ineligibleExprs.addAll(nodes);
            });

    return ineligibleExprs;
  }

  private boolean containsEliminableFunctionOnly(CelNavigableMutableExpr navigableExpr) {
    return navigableExpr
        .allNodes()
        .allMatch(
            node -> {
              if (node.getKind().equals(Kind.CALL)) {
                return cseEliminableFunctions.contains(node.expr().call().function());
              }

              return true;
            });
  }

  /**
   * Converts the {@link CelMutableExpr} to make it suitable for performing a semantically equals
   * check.
   *
   * <p>Specifically, this will deep copy the mutable expr then set all expr IDs in the expression
   * tree to 0.
   */
  private CelMutableExpr normalizeForEquality(CelMutableExpr mutableExpr) {
    CelMutableExpr copiedExpr = CelMutableExpr.newInstance(mutableExpr);

    return astMutator.clearExprIds(copiedExpr);
  }

  @VisibleForTesting
  static CelFunctionDecl newCelBlockFunctionDecl(CelType resultType) {
    return CelFunctionDecl.newFunctionDeclaration(
        CEL_BLOCK_FUNCTION,
        CelOverloadDecl.newGlobalOverload(
            "cel_block_list", resultType, ListType.create(SimpleType.DYN), resultType));
  }

  /** Options to configure how Common Subexpression Elimination behave. */
  @AutoValue
  public abstract static class SubexpressionOptimizerOptions {
    public abstract int iterationLimit();

    public abstract boolean populateMacroCalls();

    public abstract boolean enableCelBlock();

    public abstract int subexpressionMaxRecursionDepth();

    public abstract ImmutableSet<String> eliminableFunctions();

    /** Builder for configuring the {@link SubexpressionOptimizerOptions}. */
    @AutoValue.Builder
    public abstract static class Builder {

      /**
       * Limit the number of iteration while performing CSE. An exception is thrown if the iteration
       * count exceeds the set value.
       */
      public abstract Builder iterationLimit(int value);

      /**
       * Populate the macro_calls map in source_info with macro calls on the resulting optimized
       * AST.
       */
      public abstract Builder populateMacroCalls(boolean value);

      /**
       * Rewrites the optimized AST using cel.@block call instead of cascaded cel.bind macros, aimed
       * to produce a more compact AST. {@link CelSource.Extension} field will be populated in the
       * AST to inform that special runtime support is required to evaluate the optimized
       * expression.
       */
      public abstract Builder enableCelBlock(boolean value);

      /**
       * Ensures all extracted subexpressions do not exceed the maximum depth of designated value.
       * The purpose of this is to guarantee evaluation and deserialization safety by preventing
       * deeply nested ASTs. The trade-off is increased memory usage due to memoizing additional
       * block indices during lazy evaluation.
       *
       * <p>As a general note, root of a node has a depth of 0. An expression `x.y.z` has a depth of
       * 2.
       *
       * <p>Note that expressions containing no common subexpressions may become a candidate for
       * extraction to satisfy the max depth requirement.
       *
       * <p>This is a no-op if {@link #enableCelBlock} is set to false, the configured value is less
       * than 1, or no subexpression needs to be extracted because the entire expression is already
       * under the designated limit.
       *
       * <p>Examples:
       *
       * <ol>
       *   <li>a.b.c with depth 1 -> cel.@block([x.b, @index0.c], @index1)
       *   <li>a.b.c with depth 3 -> a.b.c
       *   <li>a.b + a.b.c.d with depth 3 -> cel.@block([a.b, @index0.c.d], @index0 + @index1)
       * </ol>
       *
       * <p>
       */
      public abstract Builder subexpressionMaxRecursionDepth(int value);

      abstract ImmutableSet.Builder<String> eliminableFunctionsBuilder();

      /**
       * Adds a collection of custom functions that will be a candidate for common subexpression
       * elimination. By default, standard functions are eliminable.
       *
       * <p>Note that the implementation of custom functions must be free of side effects.
       */
      @CanIgnoreReturnValue
      public Builder addEliminableFunctions(Iterable<String> functions) {
        checkNotNull(functions);
        this.eliminableFunctionsBuilder().addAll(functions);
        return this;
      }

      /** See {@link #addEliminableFunctions(Iterable)}. */
      @CanIgnoreReturnValue
      public Builder addEliminableFunctions(String... functions) {
        return addEliminableFunctions(Arrays.asList(functions));
      }

      public abstract SubexpressionOptimizerOptions build();

      Builder() {}
    }

    abstract Builder toBuilder();

    /** Returns a new options builder with recommended defaults pre-configured. */
    public static Builder newBuilder() {
      return new AutoValue_SubexpressionOptimizer_SubexpressionOptimizerOptions.Builder()
          .iterationLimit(500)
          .populateMacroCalls(false)
          .enableCelBlock(false)
          .subexpressionMaxRecursionDepth(0);
    }

    SubexpressionOptimizerOptions() {}
  }

  private SubexpressionOptimizer(SubexpressionOptimizerOptions cseOptions) {
    this.cseOptions = cseOptions;
    this.astMutator = AstMutator.newInstance(cseOptions.iterationLimit());
    this.cseEliminableFunctions =
        ImmutableSet.<String>builder()
            .addAll(DefaultOptimizerConstants.CEL_CANONICAL_FUNCTIONS)
            .addAll(cseOptions.eliminableFunctions())
            .build();
  }
}
