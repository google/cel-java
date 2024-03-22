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

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableMap.toImmutableMap;
import static java.lang.Math.max;

import com.google.auto.value.AutoValue;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Table;
import com.google.errorprone.annotations.Immutable;
import dev.cel.common.CelAbstractSyntaxTree;
import dev.cel.common.CelSource;
import dev.cel.common.ast.CelExpr;
import dev.cel.common.ast.CelExpr.CelCall;
import dev.cel.common.ast.CelExpr.ExprKind.Kind;
import dev.cel.common.ast.CelExprIdGeneratorFactory;
import dev.cel.common.ast.CelExprIdGeneratorFactory.ExprIdGenerator;
import dev.cel.common.ast.CelExprIdGeneratorFactory.MonotonicIdGenerator;
import dev.cel.common.ast.CelExprIdGeneratorFactory.StableIdGenerator;
import dev.cel.common.navigation.CelNavigableExpr;
import dev.cel.common.navigation.CelNavigableExpr.TraversalOrder;
import dev.cel.common.navigation.MutableExpr;
import dev.cel.common.navigation.MutableExpr.MutableCall;
import dev.cel.common.navigation.MutableExpr.MutableComprehension;
import dev.cel.common.navigation.MutableExpr.MutableConstant;
import dev.cel.common.navigation.MutableExpr.MutableCreateList;
import dev.cel.common.navigation.MutableExprConverter;
import dev.cel.common.types.CelType;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map.Entry;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/** AstMutator contains logic for mutating a {@link CelAbstractSyntaxTree}. */
@Immutable
public final class AstMutator {
  private static final ExprIdGenerator NO_OP_ID_GENERATOR = id -> id;
  private final long iterationLimit;

  /**
   * Returns a new instance of a AST mutator with the iteration limit set.
   *
   * <p>Mutation is performed by walking the existing AST until the expression node to replace is
   * found, then the new subtree is walked to complete the mutation. Visiting of each node
   * increments the iteration counter. Replace subtree operations will throw an exception if this
   * counter reaches the limit.
   *
   * @param iterationLimit Must be greater than 0.
   */
  public static AstMutator newInstance(long iterationLimit) {
    return new AstMutator(iterationLimit);
  }

  private AstMutator(long iterationLimit) {
    Preconditions.checkState(iterationLimit > 0L);
    this.iterationLimit = iterationLimit;
  }

  /** Replaces all the expression IDs in the expression tree with 0. */
  public CelExpr clearExprIds(CelExpr celExpr) {
//    return renumberExprIds((unused) -> 0, celExpr.toBuilder()).build();
    return null;
  }

  /**
   * Replaces a subtree in the given expression node. This operation is intended for AST
   * optimization purposes.
   *
   * <p>This is a very dangerous operation. Callers should re-typecheck the mutated AST and
   * additionally verify that the resulting AST is semantically valid.
   *
   * <p>All expression IDs will be renumbered in a stable manner to ensure there's no ID collision
   * between the nodes. The renumbering occurs even if the subtree was not replaced.
   *
   * <p>If the ability to unparse an expression containing a macro call must be retained, use {@link
   * #replaceSubtree(CelAbstractSyntaxTree, CelExpr, long) instead.}
   *
   * @param celExpr Original expression node to rewrite.
   * @param newExpr New CelExpr to replace the subtree with.
   * @param exprIdToReplace Expression id of the subtree that is getting replaced.
   */
  public CelExpr replaceSubtree(CelExpr celExpr, CelExpr newExpr, long exprIdToReplace) {
    MonotonicIdGenerator monotonicIdGenerator =
        CelExprIdGeneratorFactory.newMonotonicIdGenerator(0);
    return null;
//    return mutateExpr(
//            unused -> monotonicIdGenerator.nextExprId(),
//            celExpr.toBuilder(),
//            newExpr.toBuilder(),
//            exprIdToReplace)
//        .build();
  }

  /**
   * Replaces a subtree in the given AST. This operation is intended for AST optimization purposes.
   *
   * <p>This is a very dangerous operation. Callers should re-typecheck the mutated AST and
   * additionally verify that the resulting AST is semantically valid.
   *
   * <p>All expression IDs will be renumbered in a stable manner to ensure there's no ID collision
   * between the nodes. The renumbering occurs even if the subtree was not replaced.
   *
   * <p>This will scrub out the description, positions and line offsets from {@code CelSource}. If
   * the source contains macro calls, its call IDs will be to be consistent with the renumbered IDs
   * in the AST.
   *
   * @param ast Original ast to mutate.
   * @param newExpr New CelExpr to replace the subtree with.
   * @param exprIdToReplace Expression id of the subtree that is getting replaced.
   */
  public CelAbstractSyntaxTree replaceSubtree(
      CelAbstractSyntaxTree ast, CelExpr newExpr, long exprIdToReplace) {
    return replaceSubtreeWithNewAst(
        ast,
        MutableExprConverter.fromCelExpr(ast.getExpr()),
        CelAbstractSyntaxTree.newParsedAst(
            newExpr,
            // Copy the macro call information to the new AST such that macro call map can be
            // normalized post-replacement.
            CelSource.newBuilder().addAllMacroCalls(ast.getSource().getMacroCalls()).build()),
        MutableExprConverter.fromCelExpr(newExpr),
        exprIdToReplace);
  }

  /** Wraps the given AST and its subexpressions with a new cel.@block call. */
  public CelAbstractSyntaxTree wrapAstWithNewCelBlock(
      String celBlockFunction, CelAbstractSyntaxTree ast, Collection<CelExpr> subexpressions) {
    long maxId = getMaxId(ast);
    CelExpr blockExpr =
        CelExpr.newBuilder()
            .setId(++maxId)
            .setCall(
                CelCall.newBuilder()
                    .setFunction(celBlockFunction)
                    .addArgs(
                        CelExpr.ofCreateListExpr(
                            ++maxId, ImmutableList.copyOf(subexpressions), ImmutableList.of()),
                        ast.getExpr())
                    .build())
            .build();

    return CelAbstractSyntaxTree.newParsedAst(blockExpr, ast.getSource());
  }

  /**
   * Generates a new bind macro using the provided initialization and result expression, then
   * replaces the subtree using the new bind expr at the designated expr ID.
   *
   * <p>The bind call takes the format of: {@code cel.bind(varInit, varName, resultExpr)}
   *
   * @param root TODO
   * @param rootSource TODO
   * @param varName New variable name for the bind macro call.
   * @param varInit Initialization expression to bind to the local variable.
   * @param resultExpr Result expression
   * @param exprIdToReplace Expression ID of the subtree that is getting replaced.
   */
  public MutableAst replaceSubtreeWithNewBindMacro(
      MutableExpr root,
      CelSource.Builder rootSource,
      String varName,
      MutableExpr varInit,
      MutableExpr resultExpr,
      long exprIdToReplace) {
    long maxId = max(getMaxId(varInit), getMaxId(root));
    StableIdGenerator stableIdGenerator = CelExprIdGeneratorFactory.newStableIdGenerator(maxId);
    BindMacro bindMacro = newBindMacro(varName, varInit, resultExpr, stableIdGenerator);
    // In situations where the existing AST already contains a macro call (ex: nested cel.binds),
    // its macro source must be normalized to make it consistent with the newly generated bind
    // macro.
    CelSource.Builder celSource =
        normalizeMacroSource(
            rootSource,
            -1, // Do not replace any of the subexpr in the macro map.
            bindMacro.bindMacroExpr(),
            stableIdGenerator::renumberId)
            .addMacroCalls(bindMacro.bindExpr().id(), MutableExprConverter.fromMutableExpr(bindMacro.bindMacroExpr()));

    return replaceSubtree(root, bindMacro.bindExpr(), exprIdToReplace, rootSource, celSource);
  }

  /**
   * Generates a new bind macro using the provided initialization and result expression, then
   * replaces the subtree using the new bind expr at the designated expr ID.
   *
   * <p>The bind call takes the format of: {@code cel.bind(varInit, varName, resultExpr)}
   *
   * @param ast Original ast to mutate.
   * @param varName New variable name for the bind macro call.
   * @param varInit Initialization expression to bind to the local variable.
   * @param resultExpr Result expression
   * @param exprIdToReplace Expression ID of the subtree that is getting replaced.
   */
  public CelAbstractSyntaxTree replaceSubtreeWithNewBindMacro(
      CelAbstractSyntaxTree ast,
      String varName,
      CelExpr varInit,
      CelExpr resultExpr,
      long exprIdToReplace) {
    throw new UnsupportedOperationException("Unsupported combine!");
  }

  /** Renumbers all the expr IDs in the given AST in a consecutive manner starting from 1. */
  public CelAbstractSyntaxTree renumberIdsConsecutively(CelAbstractSyntaxTree ast) {
    throw new UnsupportedOperationException("Unsupported!");
  }

  /** Renumbers all the expr IDs in the given AST in a consecutive manner starting from 1. */
  public MutableAst renumberIdsConsecutively(MutableAst mutableAst) {
    return MutableAst.of(mutableAst.mutableExpr(), mutableAst.sourceBuilder());
  }

  /** Renumbers all the expr IDs in the given AST in a consecutive manner starting from 1. */
  private MutableAst renumberIdsConsecutively(MutableExpr root, CelSource.Builder source) {
    StableIdGenerator stableIdGenerator = CelExprIdGeneratorFactory.newStableIdGenerator(0);
    MutableExpr mutableExpr =
        renumberExprIds(stableIdGenerator::renumberId, root);
    CelSource.Builder newSource =
        normalizeMacroSource(
            source, Integer.MIN_VALUE, root, stableIdGenerator::renumberId);

    return MutableAst.of(mutableExpr, newSource);
  }

  public MangledComprehensionAst mangleComprehensionIdentifierNames(
      CelAbstractSyntaxTree ast, MutableExpr mutableExpr, String newIterVarPrefix, String newResultPrefix) {
    CelNavigableExpr newNavigableExpr = CelNavigableExpr.fromMutableExpr(mutableExpr);
    Predicate<CelNavigableExpr> comprehensionIdentifierPredicate = x -> true;
    comprehensionIdentifierPredicate =
        comprehensionIdentifierPredicate
            .and(node -> node.getKind().equals(Kind.COMPREHENSION))
            .and(node -> !node.mutableExpr().comprehension().getIterVar().startsWith(newIterVarPrefix))
            .and(node -> !node.mutableExpr().comprehension().getAccuVar().startsWith(newResultPrefix));

    LinkedHashMap<CelNavigableExpr, MangledComprehensionType> comprehensionsToMangle =
        newNavigableExpr
            // This is important - mangling needs to happen bottom-up to avoid stepping over
            // shadowed variables that are not part of the comprehension being mangled.
            .allNodes(TraversalOrder.POST_ORDER)
            .filter(comprehensionIdentifierPredicate)
            .filter(
                node -> {
                  // Ensure the iter_var or the comprehension result is actually referenced in the
                  // loop_step. If it's not, we
                  // can skip mangling.
                  String iterVar = node.mutableExpr().comprehension().getIterVar();
                  String result = node.mutableExpr().comprehension().getResult().ident().name();
                  return CelNavigableExpr.fromMutableExpr(node.mutableExpr().comprehension().getLoopStep())
                      .allNodes()
                      .filter(subNode -> subNode.getKind().equals(Kind.IDENT))
                      .map(subNode -> subNode.mutableExpr().ident())
                      .anyMatch(
                          ident -> ident.name().contains(iterVar) || ident.name().contains(result));
                })
            .collect(
                Collectors.toMap(
                    k -> k,
                    v -> {
                      MutableComprehension comprehension = v.mutableExpr().comprehension();
                      String iterVar = comprehension.getIterVar();
                      // Identifiers to mangle could be the iteration variable, comprehension result
                      // or both, but at least one has to exist.
                      // As an example, [1,2].map(i, 3) would produce an optional.empty because `i`
                      // is not actually used.
                      Optional<Long> iterVarId =
                          CelNavigableExpr.fromMutableExpr(comprehension.getLoopStep())
                              .allNodes()
                              .filter(
                                  loopStepNode ->
                                      loopStepNode.mutableExpr().ident().name().equals(iterVar))
                              .map(CelNavigableExpr::id)
                              .findAny();
                      Optional<CelType> iterVarType =
                          iterVarId.map(
                              id ->
                                  ast.getType(id)
                                      .orElseThrow(
                                          () ->
                                              new NoSuchElementException(
                                                  "Checked type not present for iteration variable:"
                                                      + " "
                                                      + iterVarId)));
                      Optional<CelType> resultType = ast.getType(comprehension.getResult().id());

                      return MangledComprehensionType.of(iterVarType, resultType);
                    },
                    (x, y) -> {
                      throw new IllegalStateException("Unexpected CelNavigableExpr collision");
                    },
                    LinkedHashMap::new));
    int iterCount = 0;

    // The map that we'll eventually return to the caller.
    HashMap<MangledComprehensionName, MangledComprehensionType> mangledIdentNamesToType =
        new HashMap<>();
    // Intermediary table used for the purposes of generating a unique mangled variable name.
    Table<Integer, MangledComprehensionType, MangledComprehensionName> comprehensionLevelToType =
        HashBasedTable.create();
    MutableExpr mutatedComprehensionExpr = newNavigableExpr.mutableExpr();
    CelSource.Builder newSource = ast.getSource().toBuilder();
    for (Entry<CelNavigableExpr, MangledComprehensionType> comprehensionEntry :
        comprehensionsToMangle.entrySet()) {
      iterCount++;
      CelNavigableExpr comprehensionNode = comprehensionEntry.getKey();
      MangledComprehensionType comprehensionEntryType = comprehensionEntry.getValue();

      MutableExpr comprehensionExpr = comprehensionNode.mutableExpr();
      int comprehensionNestingLevel = countComprehensionNestingLevel(comprehensionNode);
      MangledComprehensionName mangledComprehensionName;
      if (comprehensionLevelToType.contains(comprehensionNestingLevel, comprehensionEntryType)) {
        mangledComprehensionName =
            comprehensionLevelToType.get(comprehensionNestingLevel, comprehensionEntryType);
      } else {
        // First time encountering the pair of <ComprehensionLevel, CelType>. Generate a unique
        // mangled variable name for this.
        int uniqueTypeIdx = comprehensionLevelToType.row(comprehensionNestingLevel).size();
        String mangledIterVarName =
            newIterVarPrefix + comprehensionNestingLevel + ":" + uniqueTypeIdx;
        String mangledResultName =
            newResultPrefix + comprehensionNestingLevel + ":" + uniqueTypeIdx;
        mangledComprehensionName =
            MangledComprehensionName.of(mangledIterVarName, mangledResultName);
        comprehensionLevelToType.put(
            comprehensionNestingLevel, comprehensionEntryType, mangledComprehensionName);
      }
      mangledIdentNamesToType.put(mangledComprehensionName, comprehensionEntryType);

      String iterVar = comprehensionExpr.comprehension().getIterVar();
      String accuVar = comprehensionExpr.comprehension().getAccuVar();
      mutatedComprehensionExpr =
          mangleIdentsInComprehensionExpr(
              mutatedComprehensionExpr,
              comprehensionExpr,
              iterVar,
              accuVar,
              mangledComprehensionName);
      // Repeat the mangling process for the macro source.
      newSource =
          mangleIdentsInMacroSource(
              newSource,
              mutatedComprehensionExpr,
              iterVar,
              mangledComprehensionName,
              comprehensionExpr.id());
    }

    if (iterCount >= iterationLimit) {
      // Note that it's generally impossible to reach this for a well-formed AST. The nesting level
      // of AST being mutated is always deeper than the number of identifiers being mangled, thus
      // the mutation operation should throw before we ever reach here.
      throw new IllegalStateException("Max iteration count reached.");
    }

    CelAbstractSyntaxTree newAst =
        MutableAst.of(mutatedComprehensionExpr,newSource).toParsedAst();
        // CelNavigableAst.fromAst(;
        //     CelAbstractSyntaxTree.newParsedAst(mutatedComprehensionExpr, newSource));

    // TODO
    return MangledComprehensionAst.of(
        newAst, ImmutableMap.copyOf(mangledIdentNamesToType));
  }

  /**
   * Replaces all comprehension identifier names with a unique name based on the given prefix.
   *
   * <p>The purpose of this is to avoid errors that can be caused by shadowed variables while
   * augmenting an AST. As an example: {@code [2, 3].exists(x, x - 1 > 3) || x - 1 > 3}. Note that
   * the scoping of `x - 1` is different between th two LOGICAL_OR branches. Iteration variable `x`
   * in `exists` will be mangled to {@code [2, 3].exists(@c0, @c0 - 1 > 3) || x - 1 > 3} to avoid
   * erroneously extracting x - 1 as common subexpression.
   *
   * <p>The expression IDs are not modified when the identifier names are changed.
   *
   * <p>Mangling occurs only if the iteration variable is referenced within the loop step.
   *
   * <p>Iteration variables in comprehensions are numbered based on their comprehension nesting
   * levels and the iteration variable's type. Examples:
   *
   * <ul>
   *   <li>{@code [true].exists(i, i) && [true].exists(j, j)} -> {@code [true].exists(@c0:0, @c0:0)
   *       && [true].exists(@c0:0, @c0:0)} // Note that i,j gets replaced to the same @c0:0 in this
   *       example as they share the same nesting level and type.
   *   <li>{@code [1].exists(i, i > 0) && [1u].exists(j, j > 0u)} -> {@code [1].exists(@c0:0, @c0:0
   *       > 0) && [1u].exists(@c0:1, @c0:1 > 0u)}
   *   <li>{@code [true].exists(i, i && [true].exists(j, j))} -> {@code [true].exists(@c0:0, @c0:0
   *       && [true].exists(@c1:0, @c1:0))}
   * </ul>
   *
   * @param ast AST to mutate
   * @param newIterVarPrefix Prefix to use for new iteration variable identifier name. For example,
   *     providing @c will produce @c0:0, @c0:1, @c1:0, @c2:0... as new names.
   * @param newResultPrefix Prefix to use for new comprehensin result identifier names.
   */
  public MangledComprehensionAst mangleComprehensionIdentifierNames(
      CelAbstractSyntaxTree ast, String newIterVarPrefix, String newResultPrefix) {
    throw new UnsupportedOperationException("Unsupported!");
  }

  /**
   * Mutates the given AST by replacing a subtree at a given index.
   *
   * @param ast Existing AST being mutated
   * @param newAst New subtree to perform the replacement with. If the subtree has a macro map
   *     populated, its macro source is merged with the existing AST's after normalization.
   * @param exprIdToReplace The expr ID in the existing AST to replace the subtree at.
   */
  @VisibleForTesting
  CelAbstractSyntaxTree replaceSubtreeWithNewAst(
      CelAbstractSyntaxTree ast,
      MutableExpr astExpr,
      CelAbstractSyntaxTree newAst,
      MutableExpr newExpr,
      long exprIdToReplace) {
//     // Stabilize the incoming AST by renumbering all of its expression IDs.
//     long maxId = max(getMaxId(ast), getMaxId(newAst));
//     // TODO
// //    newAst = stabilizeAst(newAst, maxId);
//
//     // Mutate the AST root with the new subtree. All the existing expr IDs are renumbered in the
//     // process, but its original IDs are memoized so that we can normalize the expr IDs
//     // in the macro source map.
//     StableIdGenerator stableIdGenerator =
//         CelExprIdGeneratorFactory.newStableIdGenerator(getMaxId(newAst));
//     MutableExpr mutatedRoot =
//         mutateExpr(
//             stableIdGenerator::renumberId,
//             astExpr,
//             newExpr,
//             exprIdToReplace);
//
//     CelSource newAstSource = ast.getSource();
//     if (!newAst.getSource().getMacroCalls().isEmpty()) {
//       // The root is mutated, but the expr IDs in the macro map needs to be normalized.
//       // In situations where an AST with a new macro map is being inserted (ex: new bind call),
//       // the new subtree's expr ID is not memoized in the stable ID generator because the ID never
//       // existed in the main AST.
//       // In this case, we forcibly memoize the new subtree ID with a newly generated ID so
//       // that the macro map IDs can be normalized properly.
//       stableIdGenerator.memoize(
//           newAst.getExpr().id(), stableIdGenerator.renumberId(exprIdToReplace));
//       newAstSource = combine(newAstSource, newAst.getSource());
//     }
//
//     // TODO
// //    newAstSource =
// //        normalizeMacroSource(
// //            newAstSource, exprIdToReplace, mutatedRoot, stableIdGenerator::renumberId);
//
//     return CelAbstractSyntaxTree.newParsedAst(MutableExprConverter.fromMutableExpr(mutatedRoot), newAstSource);
    throw new UnsupportedOperationException("Unsupported!");
  }

  public MutableAst replaceSubtree(
          MutableExpr root,
          MutableExpr newExpr,
          long exprIdToReplace) {
    return replaceSubtree(root, newExpr, exprIdToReplace, CelSource.newBuilder());
  }

  public MutableAst replaceSubtree(
          MutableExpr root,
          MutableExpr newExpr,
          long exprIdToReplace,
          CelSource.Builder rootSource
          ) {
    return replaceSubtree(root, newExpr, exprIdToReplace, rootSource, CelSource.newBuilder());
  }

  public MutableAst replaceSubtree(
          MutableExpr root,
          MutableExpr newExpr,
          long exprIdToReplace,
          CelSource.Builder rootSource,
          CelSource.Builder newSource
          ) {
    // Stabilize the incoming AST by renumbering all of its expression IDs.
    long maxId = max(getMaxId(root), getMaxId(newExpr));
    MutableAst stablizedAst = stabilizeAst(newExpr, newSource, maxId);
    long stablizedNewExprRootId = newExpr.id();
    newExpr = stablizedAst.mutatedExpr;
    newSource = stablizedAst.sourceBuilder;

    // Mutate the AST root with the new subtree. All the existing expr IDs are renumbered in the
    // process, but its original IDs are memoized so that we can normalize the expr IDs
    // in the macro source map.
    StableIdGenerator stableIdGenerator =
        CelExprIdGeneratorFactory.newStableIdGenerator(getMaxId(stablizedAst));
    MutableExpr mutatedRoot =
        mutateExpr(
            stableIdGenerator::renumberId,
            root,
            newExpr,
            exprIdToReplace);

    CelSource.Builder newAstSource = CelSource.newBuilder();
    if (!rootSource.getMacroCalls().isEmpty()) {
      newAstSource = combine(newAstSource, rootSource);
    }

    if (!newSource.getMacroCalls().isEmpty()) {
      stableIdGenerator.memoize(
              stablizedNewExprRootId, stableIdGenerator.renumberId(exprIdToReplace));
      newAstSource = combine(newAstSource, newSource);
    }

    // TODO: pass in macro source directly instead of source builder?
    newAstSource = normalizeMacroSource(newAstSource, exprIdToReplace, mutatedRoot, stableIdGenerator::renumberId);

    return MutableAst.of(mutatedRoot, newAstSource);
  }

  public static class MutableAst {
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

    private static MutableAst of(MutableExpr mutableExpr, CelSource.Builder sourceBuilder) {
      return new MutableAst(mutableExpr, sourceBuilder);
    }

    private MutableAst(MutableExpr mutatedExpr, CelSource.Builder sourceBuilder) {
      this.mutatedExpr = mutatedExpr;
      this.sourceBuilder = sourceBuilder;
    }
  }

  private MutableExpr mangleIdentsInComprehensionExpr(
      MutableExpr root,
      MutableExpr comprehensionExpr,
      String originalIterVar,
      String originalAccuVar,
      MangledComprehensionName mangledComprehensionName) {
    MutableComprehension comprehension = comprehensionExpr.comprehension();
    replaceIdentName(
        comprehension.getLoopStep(),
        originalIterVar,
        mangledComprehensionName.iterVarName());
    replaceIdentName(comprehensionExpr, originalAccuVar, mangledComprehensionName.resultName());

    comprehension.setIterVar(mangledComprehensionName.iterVarName());
    // Most standard macros set accu_var as __result__, but not all (ex: cel.bind).
    if (comprehension.getAccuVar().equals(originalAccuVar)) {
      comprehension.setAccuVar(mangledComprehensionName.resultName());
    }

    return mutateExpr(
        NO_OP_ID_GENERATOR,
        root,
        comprehensionExpr,
        comprehensionExpr.id());
  }

  private void replaceIdentName(
      MutableExpr comprehensionExpr, String originalIdentName, String newIdentName) {
   int iterCount;
   for (iterCount = 0; iterCount < iterationLimit; iterCount++) {
     MutableExpr identToMangle =
         CelNavigableExpr.fromMutableExpr(comprehensionExpr)
             .descendants()
             .map(CelNavigableExpr::mutableExpr)
             .filter(node -> node.exprKind().equals(Kind.IDENT) && node.ident().name().equals(originalIdentName))
             .findAny().orElse(null);
     if (identToMangle == null) {
       break;
     }

     comprehensionExpr =
         mutateExpr(
             NO_OP_ID_GENERATOR,
             comprehensionExpr,
             MutableExpr.ofIdent(newIdentName),
             identToMangle.id());
   }

   if (iterCount >= iterationLimit) {
     throw new IllegalStateException("Max iteration count reached.");
   }
  }

  private CelExpr.Builder replaceIdentName(
      CelExpr.Builder comprehensionExpr, String originalIdentName, String newIdentName) {
    throw new UnsupportedOperationException("Unsupported!");
  }

  private CelSource.Builder mangleIdentsInMacroSource(
      CelSource.Builder sourceBuilder,
      MutableExpr mutatedComprehensionExpr,
      String originalIterVar,
      MangledComprehensionName mangledComprehensionName,
      long originalComprehensionId) {
   if (!sourceBuilder.getMacroCalls().containsKey(originalComprehensionId)) {
     return sourceBuilder;
   }

   // First, normalize the macro source.
   // ex: [x].exists(x, [x].exists(x, x == 1)) -> [x].exists(x, [@c1].exists(x, @c0 == 1)).
   CelSource.Builder newSource =
       normalizeMacroSource(sourceBuilder, -1, mutatedComprehensionExpr, (id) -> id);

   // Note that in the above example, the iteration variable is not replaced after normalization.
   // This is because populating a macro call map upon parse generates a new unique identifier
   // that does not exist in the main AST. Thus, we need to manually replace the identifier.
   // Also note that this only applies when the macro is at leaf. For nested macros, the iteration
   // variable actually exists in the main AST thus, this step isn't needed.
   // ex: [1].map(x, [2].filter(y, x == y). Here, the variable declaration `x` exists in the AST
   // but not `y`.
   MutableExpr macroExpr = MutableExprConverter.fromCelExpr(newSource.getMacroCalls().get(originalComprehensionId));
   // By convention, the iteration variable is always the first argument of the
   // macro call expression.
   MutableExpr identToMangle = macroExpr.call().args().get(0);
    if (identToMangle.ident().name().equals(originalIterVar)) {
   // if (identToMangle.identOrDefault().name().equals(originalIterVar)) {
     macroExpr =
         mutateExpr(
             NO_OP_ID_GENERATOR,
             macroExpr,
             MutableExpr.ofIdent(mangledComprehensionName.iterVarName()),
             identToMangle.id());
   }

   newSource.addMacroCalls(originalComprehensionId, MutableExprConverter.fromMutableExpr(macroExpr));

   return newSource;
  }

  private CelSource mangleIdentsInMacroSource(
      CelAbstractSyntaxTree ast,
      CelExpr.Builder mutatedComprehensionExpr,
      String originalIterVar,
      MangledComprehensionName mangledComprehensionName,
      long originalComprehensionId) {
    throw new UnsupportedOperationException("Unsupported!");
  }

  private BindMacro newBindMacro(
      String varName, MutableExpr varInit, MutableExpr resultExpr, StableIdGenerator stableIdGenerator) {
   // Renumber incoming expression IDs in the init and result expression to avoid collision with
   // the main AST. Existing IDs are memoized for a macro source sanitization pass at the end
   // (e.g: inserting a bind macro to an existing macro expr)
   varInit = renumberExprIds(stableIdGenerator::nextExprId, varInit);
   resultExpr = renumberExprIds(stableIdGenerator::nextExprId, resultExpr);

   // TODO: make this a factory?
   MutableExpr bindMacroExpr = MutableExpr.ofComprehension(
       stableIdGenerator.nextExprId(),
       MutableComprehension.create(
           "#unused",
           MutableExpr.ofCreateList(stableIdGenerator.nextExprId(), MutableCreateList.create()),
           varName,
           varInit,
           MutableExpr.ofConstant(stableIdGenerator.nextExprId(), MutableConstant.ofValue(false)),
           MutableExpr.ofIdent(stableIdGenerator.nextExprId(), varName),
           resultExpr
       )
   );

   MutableExpr bindMacroCallExpr =
       MutableExpr.ofCall(
           0, // Required sentinel value for macro call
           MutableCall.create(
               MutableExpr.ofIdent(stableIdGenerator.nextExprId(), "cel"),
               "bind",
               MutableExpr.ofIdent(stableIdGenerator.nextExprId(), varName),
               bindMacroExpr.comprehension().getAccuInit(),
               bindMacroExpr.comprehension().getResult()
           )
       );

   return BindMacro.of(bindMacroExpr, bindMacroCallExpr);
  }

  private static CelSource combine(CelSource celSource1, CelSource celSource2) {
    throw new UnsupportedOperationException("Unsupported combine!");
  }

  private static CelSource.Builder combine(CelSource.Builder celSource1, CelSource.Builder celSource2) {
    ImmutableMap.Builder<Long, CelExpr> macroMap = ImmutableMap.builder();
    macroMap.putAll(celSource1.getMacroCalls());
    macroMap.putAll(celSource2.getMacroCalls());

    return CelSource.newBuilder()
        .addAllExtensions(celSource1.getExtensions())
        .addAllExtensions(celSource2.getExtensions())
        .addAllMacroCalls(macroMap.buildOrThrow());
  }

  /**
   * Stabilizes the incoming AST by ensuring that all of expr IDs are consistently renumbered
   * (monotonically increased) from the starting seed ID. If the AST contains any macro calls, its
   * IDs are also normalized.
   */
  private MutableAst stabilizeAst(MutableExpr mutableExpr, CelSource.Builder source, long seedExprId) {
    StableIdGenerator stableIdGenerator =
        CelExprIdGeneratorFactory.newStableIdGenerator(seedExprId);
    MutableExpr mutatedExpr =
        renumberExprIds(stableIdGenerator::nextExprId, mutableExpr);

    if (source.getMacroCalls().isEmpty()) {
      return MutableAst.of(mutatedExpr, source);
    }

    CelSource.Builder sourceBuilder =
        CelSource.newBuilder().addAllExtensions(source.getExtensions());
    // Update the macro call IDs and their call IDs
    for (Entry<Long, CelExpr> macroCall : source.getMacroCalls().entrySet()) {
      long macroId = macroCall.getKey();
      long newCallId = stableIdGenerator.renumberId(macroId);
      MutableExpr existingMacroCallExpr = MutableExprConverter.fromCelExpr(macroCall.getValue());

      MutableExpr newCall =
          renumberExprIds(stableIdGenerator::renumberId, existingMacroCallExpr);

      sourceBuilder.addMacroCalls(newCallId, MutableExprConverter.fromMutableExpr(newCall));
    }

    return MutableAst.of(mutatedExpr, sourceBuilder);
  }

  /**
   * Stabilizes the incoming AST by ensuring that all of expr IDs are consistently renumbered
   * (monotonically increased) from the starting seed ID. If the AST contains any macro calls, its
   * IDs are also normalized.
   */
  private CelAbstractSyntaxTree stabilizeAst(CelAbstractSyntaxTree ast, long seedExprId) {
//    StableIdGenerator stableIdGenerator =
//        CelExprIdGeneratorFactory.newStableIdGenerator(seedExprId);
//    CelExpr.Builder newExprBuilder =
//        renumberExprIds(stableIdGenerator::nextExprId, ast.getExpr().toBuilder());
//
//    if (ast.getSource().getMacroCalls().isEmpty()) {
//      return CelAbstractSyntaxTree.newParsedAst(newExprBuilder.build(), ast.getSource());
//    }
//
//    CelSource.Builder sourceBuilder =
//        CelSource.newBuilder().addAllExtensions(ast.getSource().getExtensions());
//    // Update the macro call IDs and their call IDs
//    for (Entry<Long, CelExpr> macroCall : ast.getSource().getMacroCalls().entrySet()) {
//      long macroId = macroCall.getKey();
//      long newCallId = stableIdGenerator.renumberId(macroId);
//
//      CelExpr.Builder newCall =
//          renumberExprIds(stableIdGenerator::renumberId, macroCall.getValue().toBuilder());
//
//      sourceBuilder.addMacroCalls(newCallId, newCall.build());
//    }
//
//    return CelAbstractSyntaxTree.newParsedAst(newExprBuilder.build(), sourceBuilder.build());
    return null;
  }

  private CelSource.Builder normalizeMacroSource(
          CelSource.Builder celSource,
          long exprIdToReplace,
          MutableExpr mutatedRoot,
          ExprIdGenerator idGenerator) {
    // Remove the macro metadata that no longer exists in the AST due to being replaced.
    celSource.clearMacroCall(exprIdToReplace);
    CelSource.Builder sourceBuilder =
        CelSource.newBuilder().addAllExtensions(celSource.getExtensions());
    if (celSource.getMacroCalls().isEmpty()) {
      return sourceBuilder;
    }

    ImmutableMap<Long, MutableExpr> allExprs =
        CelNavigableExpr.fromMutableExpr(mutatedRoot)
            .allNodes()
            .map(CelNavigableExpr::mutableExpr)
            .collect(
                toImmutableMap(
                    MutableExpr::id,
                    expr -> expr,
                    (expr1, expr2) -> {
                      // Comprehensions can reuse same expression (result). We just need to ensure
                      // that they are identical.
                      if (expr1.equals(expr2)) {
                        return expr1;
                      }
                      throw new IllegalStateException(
                          "Expected expressions to be the same for id: " + expr1.id());
                    }));

    // Update the macro call IDs and their call references
    for (Entry<Long, CelExpr> existingMacroCall : celSource.getMacroCalls().entrySet()) {
      long macroId = existingMacroCall.getKey();
      long callId = idGenerator.generate(macroId);

      if (!allExprs.containsKey(callId)) {
        continue;
      }

      MutableExpr existingMacroCallExpr = MutableExprConverter.fromCelExpr(existingMacroCall.getValue());
      MutableExpr newMacroCallExpr =
          renumberExprIds(idGenerator, existingMacroCallExpr);

//      CelNavigableExpr callNav = CelNavigableExpr.fromExpr(newMacroCallExpr.build());
      CelNavigableExpr callNav = CelNavigableExpr.fromMutableExpr(newMacroCallExpr);
      ImmutableList<MutableExpr> callDescendants =
          callNav.descendants().map(CelNavigableExpr::mutableExpr).collect(toImmutableList());

      for (MutableExpr callChild : callDescendants) {
        if (!allExprs.containsKey(callChild.id())) {
          continue;
        }

        MutableExpr mutatedExpr = allExprs.get(callChild.id());
        if (!callChild.equals(mutatedExpr)) {
          newMacroCallExpr =
              mutateExpr(
                  NO_OP_ID_GENERATOR, newMacroCallExpr, mutatedExpr, callChild.id());
        }
      }

     if (exprIdToReplace > 0) {
       long replacedId = idGenerator.generate(exprIdToReplace);
       boolean isListExprBeingReplaced =
           allExprs.containsKey(replacedId)
               && allExprs.get(replacedId).exprKind().equals(Kind.CREATE_LIST);
       if (isListExprBeingReplaced) {
         unwrapListArgumentsInMacroCallExpr(
             allExprs.get(callId).comprehension(), newMacroCallExpr);
       }
     }

      sourceBuilder.addMacroCalls(callId, MutableExprConverter.fromMutableExpr(newMacroCallExpr));
    }

   // Replace comprehension nodes with a NOT_SET reference to reduce AST size.
   for (Entry<Long, CelExpr> macroCall : sourceBuilder.getMacroCalls().entrySet()) {
     MutableExpr macroCallExpr = MutableExprConverter.fromCelExpr(macroCall.getValue());
     CelNavigableExpr.fromMutableExpr(macroCallExpr)
         .allNodes()
         .filter(node -> node.getKind().equals(Kind.COMPREHENSION))
         .map(CelNavigableExpr::mutableExpr)
         .forEach(
             node -> {
               MutableExpr mutatedNode =
                   mutateExpr(
                       NO_OP_ID_GENERATOR,
                       macroCallExpr,
                       MutableExpr.ofNotSet(node.id()),
                       node.id());
               macroCall.setValue(MutableExprConverter.fromMutableExpr(mutatedNode));
             });

     // // Prune any NOT_SET (comprehension) nodes that no longer exist in the main AST
     // // This can occur from pulling out a nested comprehension into a separate cel.block index
     // CelNavigableExpr.fromExpr(macroCallExpr)
     //     .allNodes()
     //     .filter(node -> node.getKind().equals(Kind.NOT_SET))
     //     .map(CelNavigableExpr::id)
     //     .filter(id -> !allExprs.containsKey(id))
     //     .forEach(
     //         id -> {
     //           ImmutableList<CelExpr> newCallArgs =
     //               macroCallExpr.call().args().stream()
     //                   .filter(node -> node.id() != id)
     //                   .collect(toImmutableList());
     //           CelCall.Builder call =
     //               macroCallExpr.call().toBuilder().clearArgs().addArgs(newCallArgs);
     //
     //           macroCall.setValue(macroCallExpr.toBuilder().setCall(call.build()).build());
     //         });
   }

    return sourceBuilder;
  }

  private CelSource normalizeMacroSource(
      CelSource celSource,
      long exprIdToReplace,
      CelExpr.Builder mutatedRoot,
      ExprIdGenerator idGenerator) {
    throw new UnsupportedOperationException("Unsupported!");
  }

  /**
   * Unwraps the arguments in the extraneous list_expr which is present in the AST but does not
   * exist in the macro call map. `map`, `filter` are examples of such.
   *
   * <p>This method inspects the comprehension's accumulator initializer to infer that the list_expr
   * solely exists to match the expected result type of the macro call signature.
   *
   * @param comprehension Comprehension in the main AST to extract the macro call arguments from
   *     (loop step).
   * @param newMacroCallExpr (Output parameter) Modified macro call expression with the call
   *     arguments unwrapped.
   */
  private static void unwrapListArgumentsInMacroCallExpr(
      MutableComprehension comprehension, MutableExpr newMacroCallExpr) {
    MutableExpr accuInit = comprehension.getAccuInit();
    if (!accuInit.exprKind().equals(Kind.CREATE_LIST)
        || !accuInit.createList().elements().isEmpty()) {
      // Does not contain an extraneous list.
      return;
    }

    MutableExpr loopStepExpr = comprehension.getLoopStep();
    List<MutableExpr> loopStepArgs = loopStepExpr.call().args();
    if (loopStepArgs.size() != 2) {
      throw new IllegalArgumentException(
          String.format(
              "Expected exactly 2 arguments but got %d instead on expr id: %d",
              loopStepArgs.size(), loopStepExpr.id()));
    }

    MutableCall existingMacroCall = newMacroCallExpr.call();
    MutableCall newMacroCall = existingMacroCall.target().isPresent() ?
        MutableCall.create(
        existingMacroCall.target().get(),
        existingMacroCall.function()
    ) : MutableCall.create(existingMacroCall.function());
    newMacroCall.addArgs(existingMacroCall.args().get(0)); // iter_var is first argument of the call by convention
    newMacroCall.addArgs(loopStepArgs.get(1).createList().elements());

    newMacroCallExpr.setCall(newMacroCall);
  }

  private MutableExpr mutateExpr(
      ExprIdGenerator idGenerator,
      MutableExpr root,
      MutableExpr newExpr,
      long exprIdToReplace) {
    MutableExprVisitor mutableAst =
        MutableExprVisitor.newInstance(idGenerator, newExpr, exprIdToReplace, iterationLimit);
    return mutableAst.visit(root);
  }

  private MutableExpr renumberExprIds(ExprIdGenerator idGenerator, MutableExpr root) {
    MutableExprVisitor mutableAst =
        MutableExprVisitor.newInstance(idGenerator, root, Integer.MIN_VALUE, iterationLimit);
    return mutableAst.visit(root);
  }

  private static long getMaxId(CelAbstractSyntaxTree ast) {
    long maxId = getMaxId(ast.getExpr());
    for (Entry<Long, CelExpr> macroCall : ast.getSource().getMacroCalls().entrySet()) {
      maxId = max(maxId, getMaxId(macroCall.getValue()));
    }

    return maxId;
  }

  private static long getMaxId(CelExpr newExpr) {
    return CelNavigableExpr.fromExpr(newExpr)
        .allNodes()
        .mapToLong(CelNavigableExpr::id)
        .max()
        .orElseThrow(NoSuchElementException::new);
  }

  private static long getMaxId(MutableAst mutableAst) {
    long maxId = getMaxId(mutableAst.mutatedExpr);
    for (Entry<Long, CelExpr> macroCall : mutableAst.sourceBuilder.getMacroCalls().entrySet()) {
      maxId = max(maxId, getMaxId(macroCall.getValue()));
    }

    return maxId;
  }

  private static long getMaxId(MutableExpr mutableExpr) {
    return CelNavigableExpr.fromMutableExpr(mutableExpr)
        .allNodes()
        .mapToLong(CelNavigableExpr::id)
        .max()
        .orElseThrow(NoSuchElementException::new);
  }


  private static int countComprehensionNestingLevel(CelNavigableExpr comprehensionExpr) {
    int nestedLevel = 0;
    Optional<CelNavigableExpr> maybeParent = comprehensionExpr.parent();
    while (maybeParent.isPresent()) {
      if (maybeParent.get().getKind().equals(Kind.COMPREHENSION)) {
        nestedLevel++;
      }

      maybeParent = maybeParent.get().parent();
    }
    return nestedLevel;
  }

  /**
   * Intermediate value class to store the mangled identifiers for iteration variable and the
   * comprehension result.
   */
  @AutoValue
  public abstract static class MangledComprehensionAst {

    /** AST after the iteration variables have been mangled. */
    public abstract CelAbstractSyntaxTree ast();

    /** Map containing the mangled identifier names to their types. */
    public abstract ImmutableMap<MangledComprehensionName, MangledComprehensionType>
        mangledComprehensionMap();

    private static MangledComprehensionAst of(
        CelAbstractSyntaxTree ast,
        ImmutableMap<MangledComprehensionName, MangledComprehensionType> mangledComprehensionMap) {
      return new AutoValue_AstMutator_MangledComprehensionAst(ast, mangledComprehensionMap);
    }
  }

  /**
   * Intermediate value class to store the types for iter_var and comprehension result of which its
   * identifier names are being mangled.
   */
  @AutoValue
  public abstract static class MangledComprehensionType {

    /** Type of iter_var */
    public abstract Optional<CelType> iterVarType();

    /** Type of comprehension result */
    public abstract Optional<CelType> resultType();

    private static MangledComprehensionType of(
        Optional<CelType> iterVarType, Optional<CelType> resultType) {
      Preconditions.checkArgument(iterVarType.isPresent() || resultType.isPresent());
      return new AutoValue_AstMutator_MangledComprehensionType(iterVarType, resultType);
    }
  }

  /**
   * Intermediate value class to store the mangled names for iteration variable and the
   * comprehension result.
   */
  @AutoValue
  public abstract static class MangledComprehensionName {

    /** Mangled name for iter_var */
    public abstract String iterVarName();

    /** Mangled name for comprehension result */
    public abstract String resultName();

    private static MangledComprehensionName of(String iterVarName, String resultName) {
      return new AutoValue_AstMutator_MangledComprehensionName(iterVarName, resultName);
    }
  }

  /**
   * Intermediate value class to store the generated CelExpr for the bind macro and the macro call
   * information.
   */
  @AutoValue
  abstract static class BindMacro {
    /** Comprehension expr for the generated cel.bind macro. */
    abstract MutableExpr bindExpr();

    /**
     * Call expr representation that will be stored in the macro call map of the AST. This is
     * typically used for the purposes of supporting unparse.
     */
    abstract MutableExpr bindMacroExpr();

    private static BindMacro of(MutableExpr bindExpr, MutableExpr bindMacro) {
      return new AutoValue_AstMutator_BindMacro(bindExpr, bindMacro);
    }
  }
}
