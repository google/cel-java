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
import dev.cel.common.ast.CelExpr.CelComprehension;
import dev.cel.common.ast.CelExpr.CelIdent;
import dev.cel.common.ast.CelExpr.ExprKind.Kind;
import dev.cel.common.ast.CelExprFactory;
import dev.cel.common.ast.CelExprIdGeneratorFactory;
import dev.cel.common.ast.CelExprIdGeneratorFactory.ExprIdGenerator;
import dev.cel.common.ast.CelExprIdGeneratorFactory.MonotonicIdGenerator;
import dev.cel.common.ast.CelExprIdGeneratorFactory.StableIdGenerator;
import dev.cel.common.navigation.CelNavigableAst;
import dev.cel.common.navigation.CelNavigableExpr;
import dev.cel.common.navigation.CelNavigableExpr.TraversalOrder;
import dev.cel.common.types.CelType;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map.Entry;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/** MutableAst contains logic for mutating a {@link CelAbstractSyntaxTree}. */
@Immutable
public final class MutableAst {
  private static final ExprIdGenerator NO_OP_ID_GENERATOR = id -> id;
  private final long iterationLimit;

  /**
   * Returns a new instance of a Mutable AST with the iteration limit set.
   *
   * <p>Mutation is performed by walking the existing AST until the expression node to replace is
   * found, then the new subtree is walked to complete the mutation. Visiting of each node
   * increments the iteration counter. Replace subtree operations will throw an exception if this
   * counter reaches the limit.
   *
   * @param iterationLimit Must be greater than 0.
   */
  public static MutableAst newInstance(long iterationLimit) {
    return new MutableAst(iterationLimit);
  }

  private MutableAst(long iterationLimit) {
    Preconditions.checkState(iterationLimit > 0L);
    this.iterationLimit = iterationLimit;
  }

  /** Replaces all the expression IDs in the expression tree with 0. */
  public CelExpr clearExprIds(CelExpr celExpr) {
    return renumberExprIds((unused) -> 0, celExpr.toBuilder()).build();
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
    return mutateExpr(
            unused -> monotonicIdGenerator.nextExprId(),
            celExpr.toBuilder(),
            newExpr.toBuilder(),
            exprIdToReplace)
        .build();
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
        CelAbstractSyntaxTree.newParsedAst(newExpr, CelSource.newBuilder().build()),
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
    long maxId = max(getMaxId(varInit), getMaxId(ast));
    StableIdGenerator stableIdGenerator = CelExprIdGeneratorFactory.newStableIdGenerator(maxId);
    BindMacro bindMacro = newBindMacro(varName, varInit, resultExpr, stableIdGenerator);
    // In situations where the existing AST already contains a macro call (ex: nested cel.binds),
    // its macro source must be normalized to make it consistent with the newly generated bind
    // macro.
    CelSource celSource =
        normalizeMacroSource(
            ast.getSource(),
            -1, // Do not replace any of the subexpr in the macro map.
            bindMacro.bindMacro().toBuilder(),
            stableIdGenerator::renumberId);
    celSource =
        celSource.toBuilder()
            .addMacroCalls(bindMacro.bindExpr().id(), bindMacro.bindMacro())
            .build();

    return replaceSubtreeWithNewAst(
        ast, CelAbstractSyntaxTree.newParsedAst(bindMacro.bindExpr(), celSource), exprIdToReplace);
  }

  /** Renumbers all the expr IDs in the given AST in a consecutive manner starting from 1. */
  public CelAbstractSyntaxTree renumberIdsConsecutively(CelAbstractSyntaxTree ast) {
    StableIdGenerator stableIdGenerator = CelExprIdGeneratorFactory.newStableIdGenerator(0);
    CelExpr.Builder root =
        renumberExprIds(stableIdGenerator::renumberId, ast.getExpr().toBuilder());
    CelSource newSource =
        normalizeMacroSource(
            ast.getSource(), Integer.MIN_VALUE, root, stableIdGenerator::renumberId);

    return CelAbstractSyntaxTree.newParsedAst(root.build(), newSource);
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
    CelNavigableAst newNavigableAst = CelNavigableAst.fromAst(ast);
    Predicate<CelNavigableExpr> comprehensionIdentifierPredicate = x -> true;
    comprehensionIdentifierPredicate =
        comprehensionIdentifierPredicate
            .and(node -> node.getKind().equals(Kind.COMPREHENSION))
            .and(node -> !node.expr().comprehension().iterVar().startsWith(newIterVarPrefix))
            .and(node -> !node.expr().comprehension().accuVar().startsWith(newResultPrefix));

    LinkedHashMap<CelNavigableExpr, MangledComprehensionType> comprehensionsToMangle =
        newNavigableAst
            .getRoot()
            // This is important - mangling needs to happen bottom-up to avoid stepping over
            // shadowed variables that are not part of the comprehension being mangled.
            .allNodes(TraversalOrder.POST_ORDER)
            .filter(comprehensionIdentifierPredicate)
            .filter(
                node -> {
                  // Ensure the iter_var or the comprehension result is actually referenced in the
                  // loop_step. If it's not, we
                  // can skip mangling.
                  String iterVar = node.expr().comprehension().iterVar();
                  String result = node.expr().comprehension().result().ident().name();
                  return CelNavigableExpr.fromExpr(node.expr().comprehension().loopStep())
                      .allNodes()
                      .filter(subNode -> subNode.getKind().equals(Kind.IDENT))
                      .map(subNode -> subNode.expr().ident())
                      .anyMatch(
                          ident -> ident.name().contains(iterVar) || ident.name().contains(result));
                })
            .collect(
                Collectors.toMap(
                    k -> k,
                    v -> {
                      CelComprehension comprehension = v.expr().comprehension();
                      String iterVar = comprehension.iterVar();
                      // Identifiers to mangle could be the iteration variable, comprehension result
                      // or both, but at least one has to exist.
                      // As an example, [1,2].map(i, 3) would produce an optional.empty because `i`
                      // is not actually used.
                      Optional<Long> iterVarId =
                          CelNavigableExpr.fromExpr(comprehension.loopStep())
                              .allNodes()
                              .filter(
                                  loopStepNode ->
                                      loopStepNode.expr().identOrDefault().name().equals(iterVar))
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
                      Optional<CelType> resultType = ast.getType(comprehension.result().id());

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
    for (Entry<CelNavigableExpr, MangledComprehensionType> comprehensionEntry :
        comprehensionsToMangle.entrySet()) {
      iterCount++;
      // Refetch the comprehension node as mutating the AST could have renumbered its IDs.
      CelNavigableExpr comprehensionNode =
          newNavigableAst
              .getRoot()
              .allNodes(TraversalOrder.POST_ORDER)
              .filter(comprehensionIdentifierPredicate)
              .findAny()
              .orElseThrow(
                  () -> new NoSuchElementException("Failed to refetch mutated comprehension"));
      MangledComprehensionType comprehensionEntryType = comprehensionEntry.getValue();

      CelExpr.Builder comprehensionExpr = comprehensionNode.expr().toBuilder();
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

      String iterVar = comprehensionExpr.comprehension().iterVar();
      String accuVar = comprehensionExpr.comprehension().accuVar();
      CelExpr.Builder mutatedComprehensionExpr =
          mangleIdentsInComprehensionExpr(
              newNavigableAst.getAst().getExpr().toBuilder(),
              comprehensionExpr,
              iterVar,
              accuVar,
              mangledComprehensionName);
      // Repeat the mangling process for the macro source.
      CelSource newSource =
          mangleIdentsInMacroSource(
              newNavigableAst.getAst(),
              mutatedComprehensionExpr,
              iterVar,
              mangledComprehensionName,
              comprehensionExpr.id());

      newNavigableAst =
          CelNavigableAst.fromAst(
              CelAbstractSyntaxTree.newParsedAst(mutatedComprehensionExpr.build(), newSource));
    }

    if (iterCount >= iterationLimit) {
      // Note that it's generally impossible to reach this for a well-formed AST. The nesting level
      // of AST being mutated is always deeper than the number of identifiers being mangled, thus
      // the mutation operation should throw before we ever reach here.
      throw new IllegalStateException("Max iteration count reached.");
    }

    return MangledComprehensionAst.of(
        newNavigableAst.getAst(), ImmutableMap.copyOf(mangledIdentNamesToType));
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
      CelAbstractSyntaxTree ast, CelAbstractSyntaxTree newAst, long exprIdToReplace) {
    // Stabilize the incoming AST by renumbering all of its expression IDs.
    long maxId = max(getMaxId(ast), getMaxId(newAst));
    newAst = stabilizeAst(newAst, maxId);

    // Mutate the AST root with the new subtree. All the existing expr IDs are renumbered in the
    // process, but its original IDs are memoized so that we can normalize the expr IDs
    // in the macro source map.
    StableIdGenerator stableIdGenerator =
        CelExprIdGeneratorFactory.newStableIdGenerator(getMaxId(newAst));
    CelExpr.Builder mutatedRoot =
        mutateExpr(
            stableIdGenerator::renumberId,
            ast.getExpr().toBuilder(),
            newAst.getExpr().toBuilder(),
            exprIdToReplace);

    CelSource newAstSource = ast.getSource();
    if (!newAst.getSource().getMacroCalls().isEmpty()) {
      // The root is mutated, but the expr IDs in the macro map needs to be normalized.
      // In situations where an AST with a new macro map is being inserted (ex: new bind call),
      // the new subtree's expr ID is not memoized in the stable ID generator because the ID never
      // existed in the main AST.
      // In this case, we forcibly memoize the new subtree ID with a newly generated ID so
      // that the macro map IDs can be normalized properly.
      stableIdGenerator.memoize(
          newAst.getExpr().id(), stableIdGenerator.renumberId(exprIdToReplace));
      newAstSource = combine(newAstSource, newAst.getSource());
    }

    newAstSource =
        normalizeMacroSource(
            newAstSource, exprIdToReplace, mutatedRoot, stableIdGenerator::renumberId);

    return CelAbstractSyntaxTree.newParsedAst(mutatedRoot.build(), newAstSource);
  }

  private CelExpr.Builder mangleIdentsInComprehensionExpr(
      CelExpr.Builder root,
      CelExpr.Builder comprehensionExpr,
      String originalIterVar,
      String originalAccuVar,
      MangledComprehensionName mangledComprehensionName) {
    CelExpr.Builder modifiedLoopStep =
        replaceIdentName(
            comprehensionExpr.comprehension().loopStep().toBuilder(),
            originalIterVar,
            mangledComprehensionName.iterVarName());
    comprehensionExpr.setComprehension(
        comprehensionExpr.comprehension().toBuilder()
            .setLoopStep(modifiedLoopStep.build())
            .build());
    comprehensionExpr =
        replaceIdentName(comprehensionExpr, originalAccuVar, mangledComprehensionName.resultName());

    CelComprehension.Builder newComprehension =
        comprehensionExpr.comprehension().toBuilder()
            .setIterVar(mangledComprehensionName.iterVarName());
    // Most standard macros set accu_var as __result__, but not all (ex: cel.bind).
    if (newComprehension.accuVar().equals(originalAccuVar)) {
      newComprehension.setAccuVar(mangledComprehensionName.resultName());
    }

    return mutateExpr(
        NO_OP_ID_GENERATOR,
        root,
        comprehensionExpr.setComprehension(newComprehension.build()),
        comprehensionExpr.id());
  }

  private CelExpr.Builder replaceIdentName(
      CelExpr.Builder comprehensionExpr, String originalIdentName, String newIdentName) {
    int iterCount;
    for (iterCount = 0; iterCount < iterationLimit; iterCount++) {
      Optional<CelExpr> identToMangle =
          CelNavigableExpr.fromExpr(comprehensionExpr.build())
              .descendants()
              .map(CelNavigableExpr::expr)
              .filter(node -> node.identOrDefault().name().equals(originalIdentName))
              .findAny();
      if (!identToMangle.isPresent()) {
        break;
      }

      comprehensionExpr =
          mutateExpr(
              NO_OP_ID_GENERATOR,
              comprehensionExpr,
              CelExpr.newBuilder().setIdent(CelIdent.newBuilder().setName(newIdentName).build()),
              identToMangle.get().id());
    }

    if (iterCount >= iterationLimit) {
      throw new IllegalStateException("Max iteration count reached.");
    }

    return comprehensionExpr;
  }

  private CelSource mangleIdentsInMacroSource(
      CelAbstractSyntaxTree ast,
      CelExpr.Builder mutatedComprehensionExpr,
      String originalIterVar,
      MangledComprehensionName mangledComprehensionName,
      long originalComprehensionId) {
    if (!ast.getSource().getMacroCalls().containsKey(originalComprehensionId)) {
      return ast.getSource();
    }

    // First, normalize the macro source.
    // ex: [x].exists(x, [x].exists(x, x == 1)) -> [x].exists(x, [@c1].exists(x, @c0 == 1)).
    CelSource.Builder newSource =
        normalizeMacroSource(ast.getSource(), -1, mutatedComprehensionExpr, (id) -> id).toBuilder();

    // Note that in the above example, the iteration variable is not replaced after normalization.
    // This is because populating a macro call map upon parse generates a new unique identifier
    // that does not exist in the main AST. Thus, we need to manually replace the identifier.
    // Also note that this only applies when the macro is at leaf. For nested macros, the iteration
    // variable actually exists in the main AST thus, this step isn't needed.
    // ex: [1].map(x, [2].filter(y, x == y). Here, the variable declaration `x` exists in the AST
    // but not `y`.
    CelExpr.Builder macroExpr = newSource.getMacroCalls().get(originalComprehensionId).toBuilder();
    // By convention, the iteration variable is always the first argument of the
    // macro call expression.
    CelExpr identToMangle = macroExpr.call().args().get(0);
    if (identToMangle.identOrDefault().name().equals(originalIterVar)) {
      macroExpr =
          mutateExpr(
              NO_OP_ID_GENERATOR,
              macroExpr,
              CelExpr.newBuilder()
                  .setIdent(
                      CelIdent.newBuilder()
                          .setName(mangledComprehensionName.iterVarName())
                          .build()),
              identToMangle.id());
    }

    newSource.addMacroCalls(originalComprehensionId, macroExpr.build());
    return newSource.build();
  }

  private BindMacro newBindMacro(
      String varName, CelExpr varInit, CelExpr resultExpr, StableIdGenerator stableIdGenerator) {
    // Renumber incoming expression IDs in the init and result expression to avoid collision with
    // the main AST. Existing IDs are memoized for a macro source sanitization pass at the end
    // (e.g: inserting a bind macro to an existing macro expr)
    varInit = renumberExprIds(stableIdGenerator::nextExprId, varInit.toBuilder()).build();
    resultExpr = renumberExprIds(stableIdGenerator::nextExprId, resultExpr.toBuilder()).build();
    CelExprFactory exprFactory =
        CelExprFactory.newInstance((unused) -> stableIdGenerator.nextExprId());
    CelExpr bindMacroExpr =
        exprFactory.fold(
            "#unused",
            exprFactory.newList(),
            varName,
            varInit,
            exprFactory.newBoolLiteral(false),
            exprFactory.newIdentifier(varName),
            resultExpr);

    CelExpr bindMacroCallExpr =
        exprFactory
            .newReceiverCall(
                "bind",
                CelExpr.ofIdentExpr(stableIdGenerator.nextExprId(), "cel"),
                CelExpr.ofIdentExpr(stableIdGenerator.nextExprId(), varName),
                bindMacroExpr.comprehension().accuInit(),
                bindMacroExpr.comprehension().result())
            .toBuilder()
            .setId(0)
            .build();

    return BindMacro.of(bindMacroExpr, bindMacroCallExpr);
  }

  private static CelSource combine(CelSource celSource1, CelSource celSource2) {
    ImmutableMap.Builder<Long, CelExpr> macroMap = ImmutableMap.builder();
    macroMap.putAll(celSource1.getMacroCalls());
    macroMap.putAll(celSource2.getMacroCalls());

    return CelSource.newBuilder().addAllMacroCalls(macroMap.buildOrThrow()).build();
  }

  /**
   * Stabilizes the incoming AST by ensuring that all of expr IDs are consistently renumbered
   * (monotonically increased) from the starting seed ID. If the AST contains any macro calls, its
   * IDs are also normalized.
   */
  private CelAbstractSyntaxTree stabilizeAst(CelAbstractSyntaxTree ast, long seedExprId) {
    StableIdGenerator stableIdGenerator =
        CelExprIdGeneratorFactory.newStableIdGenerator(seedExprId);
    CelExpr.Builder newExprBuilder =
        renumberExprIds(stableIdGenerator::nextExprId, ast.getExpr().toBuilder());

    if (ast.getSource().getMacroCalls().isEmpty()) {
      return CelAbstractSyntaxTree.newParsedAst(newExprBuilder.build(), ast.getSource());
    }

    CelSource.Builder sourceBuilder = CelSource.newBuilder();
    // Update the macro call IDs and their call IDs
    for (Entry<Long, CelExpr> macroCall : ast.getSource().getMacroCalls().entrySet()) {
      long macroId = macroCall.getKey();
      long newCallId = stableIdGenerator.renumberId(macroId);

      CelExpr.Builder newCall =
          renumberExprIds(stableIdGenerator::renumberId, macroCall.getValue().toBuilder());

      sourceBuilder.addMacroCalls(newCallId, newCall.build());
    }

    return CelAbstractSyntaxTree.newParsedAst(newExprBuilder.build(), sourceBuilder.build());
  }

  private CelSource normalizeMacroSource(
      CelSource celSource,
      long exprIdToReplace,
      CelExpr.Builder mutatedRoot,
      ExprIdGenerator idGenerator) {
    // Remove the macro metadata that no longer exists in the AST due to being replaced.
    celSource = celSource.toBuilder().clearMacroCall(exprIdToReplace).build();
    CelSource.Builder sourceBuilder =
        CelSource.newBuilder().addAllExtensions(celSource.getExtensions());
    if (celSource.getMacroCalls().isEmpty()) {
      return sourceBuilder.build();
    }

    ImmutableMap<Long, CelExpr> allExprs =
        CelNavigableExpr.fromExpr(mutatedRoot.build())
            .allNodes()
            .map(CelNavigableExpr::expr)
            .collect(
                toImmutableMap(
                    CelExpr::id,
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
    for (Entry<Long, CelExpr> macroCall : celSource.getMacroCalls().entrySet()) {
      long macroId = macroCall.getKey();
      long callId = idGenerator.generate(macroId);

      if (!allExprs.containsKey(callId)) {
        continue;
      }

      CelExpr.Builder newCall = renumberExprIds(idGenerator, macroCall.getValue().toBuilder());
      CelNavigableExpr callNav = CelNavigableExpr.fromExpr(newCall.build());
      ImmutableList<CelExpr> callDescendants =
          callNav.descendants().map(CelNavigableExpr::expr).collect(toImmutableList());

      for (CelExpr callChild : callDescendants) {
        if (!allExprs.containsKey(callChild.id())) {
          continue;
        }

        CelExpr mutatedExpr = allExprs.get(callChild.id());
        if (!callChild.equals(mutatedExpr)) {
          newCall = mutateExpr((arg) -> arg, newCall, mutatedExpr.toBuilder(), callChild.id());
        }
      }
      sourceBuilder.addMacroCalls(callId, newCall.build());
    }

    // Replace comprehension nodes with a NOT_SET reference to reduce AST size.
    for (Entry<Long, CelExpr> macroCall : sourceBuilder.getMacroCalls().entrySet()) {
      CelExpr macroCallExpr = macroCall.getValue();
      CelNavigableExpr.fromExpr(macroCallExpr)
          .allNodes()
          .filter(node -> node.getKind().equals(Kind.COMPREHENSION))
          .map(CelNavigableExpr::expr)
          .forEach(
              node -> {
                CelExpr.Builder mutatedNode =
                    mutateExpr(
                        (id) -> id,
                        macroCallExpr.toBuilder(),
                        CelExpr.ofNotSet(node.id()).toBuilder(),
                        node.id());
                macroCall.setValue(mutatedNode.build());
              });
    }

    return sourceBuilder.build();
  }

  private CelExpr.Builder mutateExpr(
      ExprIdGenerator idGenerator,
      CelExpr.Builder root,
      CelExpr.Builder newExpr,
      long exprIdToReplace) {
    MutableExprVisitor mutableAst =
        MutableExprVisitor.newInstance(idGenerator, newExpr, exprIdToReplace, iterationLimit);
    return mutableAst.visit(root);
  }

  private CelExpr.Builder renumberExprIds(ExprIdGenerator idGenerator, CelExpr.Builder root) {
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
      return new AutoValue_MutableAst_MangledComprehensionAst(ast, mangledComprehensionMap);
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
      return new AutoValue_MutableAst_MangledComprehensionType(iterVarType, resultType);
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
      return new AutoValue_MutableAst_MangledComprehensionName(iterVarName, resultName);
    }
  }

  /**
   * Intermediate value class to store the generated CelExpr for the bind macro and the macro call
   * information.
   */
  @AutoValue
  abstract static class BindMacro {
    /** Comprehension expr for the generated cel.bind macro. */
    abstract CelExpr bindExpr();

    /**
     * Call expr representation that will be stored in the macro call map of the AST. This is
     * typically used for the purposes of supporting unparse.
     */
    abstract CelExpr bindMacro();

    private static BindMacro of(CelExpr bindExpr, CelExpr bindMacro) {
      return new AutoValue_MutableAst_BindMacro(bindExpr, bindMacro);
    }
  }
}
