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

import com.google.auto.value.AutoValue;
import com.google.common.base.Preconditions;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Table;
import com.google.errorprone.annotations.Immutable;
import dev.cel.common.CelAbstractSyntaxTree;
import dev.cel.common.CelSource;
import dev.cel.common.ast.CelConstant;
import dev.cel.common.ast.CelExpr;
import dev.cel.common.ast.CelExpr.ExprKind.Kind;
import dev.cel.common.ast.CelExprIdGeneratorFactory;
import dev.cel.common.ast.CelExprIdGeneratorFactory.ExprIdGenerator;
import dev.cel.common.ast.CelExprIdGeneratorFactory.StableIdGenerator;
import dev.cel.common.ast.MutableAst;
import dev.cel.common.ast.MutableExpr;
import dev.cel.common.ast.MutableExpr.MutableCall;
import dev.cel.common.ast.MutableExpr.MutableComprehension;
import dev.cel.common.ast.MutableExpr.MutableCreateList;
import dev.cel.common.ast.MutableExprConverter;
import dev.cel.common.navigation.CelNavigableExpr;
import dev.cel.common.navigation.CelNavigableExpr.TraversalOrder;
import dev.cel.common.types.CelType;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map.Entry;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableMap.toImmutableMap;
import static java.lang.Math.max;

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
  public MutableExpr clearExprIds(MutableExpr expr) {
   return renumberExprIds((unused) -> 0, expr);
  }

  /** Wraps the given AST and its subexpressions with a new cel.@block call. */
  public MutableAst wrapAstWithNewCelBlock(
      String celBlockFunction, MutableAst ast, List<MutableExpr> subexpressions) {
    long maxId = getMaxId(ast);
    MutableExpr blockExpr =
        MutableExpr.ofCall(
            ++maxId,
            MutableCall.create(
                celBlockFunction,
                MutableExpr.ofCreateList(
                    ++maxId,
                    MutableCreateList.create(subexpressions)
                ),
                ast.mutableExpr()
            )
        );

    return MutableAst.of(blockExpr, ast.source());
  }

  /**
   * Generates a new bind macro using the provided initialization and result expression, then
   * replaces the subtree using the new bind expr at the designated expr ID.
   *
   * <p>The bind call takes the format of: {@code cel.bind(varInit, varName, resultExpr)}
   *
   * @param ast TODO
   * @param varName New variable name for the bind macro call.
   * @param varInit Initialization expression to bind to the local variable.
   * @param resultExpr Result expression
   * @param exprIdToReplace Expression ID of the subtree that is getting replaced.
   */
  public MutableAst replaceSubtreeWithNewBindMacro(
      MutableAst ast,
      String varName,
      MutableExpr varInit,
      MutableExpr resultExpr,
      long exprIdToReplace,
      boolean populateMacroSource
      ) {
    // Copy the incoming expressions to prevent modifying the root
    long maxId = max(getMaxId(varInit), getMaxId(ast));
    StableIdGenerator stableIdGenerator = CelExprIdGeneratorFactory.newStableIdGenerator(maxId);
    MutableExpr newBindMacroExpr = newBindMacroExpr(varName, varInit, resultExpr.deepCopy(), stableIdGenerator);
    CelSource.Builder celSource = CelSource.newBuilder();
    if (populateMacroSource) {
      MutableExpr newBindMacroSourceExpr = newBindMacroSourceExpr(newBindMacroExpr, varName, stableIdGenerator);
      // In situations where the existing AST already contains a macro call (ex: nested cel.binds),
      // its macro source must be normalized to make it consistent with the newly generated bind
      // macro.
      celSource = normalizeMacroSource(
          ast.source(),
          -1, // Do not replace any of the subexpr in the macro map.
          newBindMacroSourceExpr,
          stableIdGenerator::renumberId)
          .addMacroCalls(newBindMacroExpr.id(), MutableExprConverter.fromMutableExpr(newBindMacroSourceExpr));
    }

    MutableAst newBindAst = MutableAst.of(newBindMacroExpr, celSource);

    return replaceSubtree(ast, newBindAst, exprIdToReplace);
  }

  /** Renumbers all the expr IDs in the given AST in a consecutive manner starting from 1. */
  public MutableAst renumberIdsConsecutively(MutableAst mutableAst) {
    StableIdGenerator stableIdGenerator = CelExprIdGeneratorFactory.newStableIdGenerator(0);
    MutableExpr mutableExpr =
            renumberExprIds(stableIdGenerator::renumberId, mutableAst.mutableExpr());
    CelSource.Builder newSource =
            normalizeMacroSource(
                    mutableAst.source(), Integer.MIN_VALUE, mutableExpr, stableIdGenerator::renumberId);

    return MutableAst.of(mutableExpr, newSource);
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
   * @param mutableExpr TODO
   * @param ast AST containing type-checked references
   * @param newIterVarPrefix Prefix to use for new iteration variable identifier name. For example,
   *     providing @c will produce @c0:0, @c0:1, @c1:0, @c2:0... as new names.
   * @param newResultPrefix Prefix to use for new comprehensin result identifier names.
   */
  public MangledComprehensionAst mangleComprehensionIdentifierNames(
          MutableExpr mutableExpr, CelAbstractSyntaxTree ast, String newIterVarPrefix, String newResultPrefix) {
    CelNavigableExpr newNavigableExpr = CelNavigableExpr.fromMutableExpr(mutableExpr);
    Predicate<CelNavigableExpr> comprehensionIdentifierPredicate = x -> true;
    comprehensionIdentifierPredicate =
        comprehensionIdentifierPredicate
            .and(node -> node.getKind().equals(Kind.COMPREHENSION))
            .and(node -> !node.mutableExpr().comprehension().iterVar().startsWith(newIterVarPrefix))
            .and(node -> !node.mutableExpr().comprehension().accuVar().startsWith(newResultPrefix));

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
                  String iterVar = node.mutableExpr().comprehension().iterVar();
                  String result = node.mutableExpr().comprehension().result().ident().name();
                  return CelNavigableExpr.fromMutableExpr(node.mutableExpr().comprehension().loopStep())
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
                      String iterVar = comprehension.iterVar();
                      // Identifiers to mangle could be the iteration variable, comprehension result
                      // or both, but at least one has to exist.
                      // As an example, [1,2].map(i, 3) would produce an optional.empty because `i`
                      // is not actually used.
                      Optional<Long> iterVarId =
                          CelNavigableExpr.fromMutableExpr(comprehension.loopStep())
                              .allNodes()
                              .filter(
                                  loopStepNode ->
                                      loopStepNode.getKind().equals(Kind.IDENT) &&
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

      String iterVar = comprehensionExpr.comprehension().iterVar();
      String accuVar = comprehensionExpr.comprehension().accuVar();
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

    return MangledComprehensionAst.of(
        MutableAst.of(mutatedComprehensionExpr,newSource), ImmutableMap.copyOf(mangledIdentNamesToType));
  }

  public MutableAst replaceSubtree(
          MutableExpr root,
          MutableExpr newExpr,
          long exprIdToReplace) {
    return replaceSubtree(MutableAst.of(root, CelSource.newBuilder()), newExpr, exprIdToReplace);
  }

  public MutableAst replaceSubtree(
          MutableAst ast,
          MutableExpr newExpr,
          long exprIdToReplace
          ) {
    return replaceSubtree(ast,
        MutableAst.of(newExpr,
                // Copy the macro call information to the new AST such that macro call map can be
                // normalized post-replacement.
                CelSource.newBuilder().addAllMacroCalls(ast.source().getMacroCalls())),
        exprIdToReplace);
  }

  public MutableAst replaceSubtree(
          MutableAst ast,
          MutableAst newAst,
          long exprIdToReplace
          ) {
    // TODO: Make this a part of API
    // Stabilize the incoming AST by renumbering all of its expression IDs.
    long maxId = max(getMaxId(ast), getMaxId(newAst));
    newAst = stabilizeAst(newAst, maxId);
    long stablizedNewExprRootId = newAst.mutableExpr().id();

    // Mutate the AST root with the new subtree. All the existing expr IDs are renumbered in the
    // process, but its original IDs are memoized so that we can normalize the expr IDs
    // in the macro source map.
    StableIdGenerator stableIdGenerator =
        CelExprIdGeneratorFactory.newStableIdGenerator(getMaxId(newAst));
    MutableExpr mutatedRoot =
        mutateExpr(
            stableIdGenerator::renumberId,
            ast.mutableExpr(),
            newAst.mutableExpr(),
            exprIdToReplace);

    CelSource.Builder newAstSource = CelSource.newBuilder();
    if (!ast.source().getMacroCalls().isEmpty()) {
      newAstSource = combine(newAstSource, ast.source());
    }

    if (!newAst.source().getMacroCalls().isEmpty()) {
      stableIdGenerator.memoize(
              stablizedNewExprRootId, stableIdGenerator.renumberId(exprIdToReplace));
      newAstSource = combine(newAstSource, newAst.source());
    }

    // TODO: pass in macro source directly instead of source builder?
    newAstSource = normalizeMacroSource(newAstSource, exprIdToReplace, mutatedRoot, stableIdGenerator::renumberId);

    return MutableAst.of(mutatedRoot, newAstSource);
  }

  private MutableExpr mangleIdentsInComprehensionExpr(
      MutableExpr root,
      MutableExpr comprehensionExpr,
      String originalIterVar,
      String originalAccuVar,
      MangledComprehensionName mangledComprehensionName) {
    MutableComprehension comprehension = comprehensionExpr.comprehension();
    replaceIdentName(
        comprehension.loopStep(),
        originalIterVar,
        mangledComprehensionName.iterVarName());
    replaceIdentName(comprehensionExpr, originalAccuVar, mangledComprehensionName.resultName());

    comprehension.setIterVar(mangledComprehensionName.iterVarName());
    // Most standard macros set accu_var as __result__, but not all (ex: cel.bind).
    if (comprehension.accuVar().equals(originalAccuVar)) {
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

  private MutableExpr newBindMacroExpr(String varName, MutableExpr varInit, MutableExpr resultExpr, StableIdGenerator stableIdGenerator) {
    // Renumber incoming expression IDs in the init and result expression to avoid collision with
    // the main AST. Existing IDs are memoized for a macro source sanitization pass at the end
    // (e.g: inserting a bind macro to an existing macro expr)
    varInit = renumberExprIds(stableIdGenerator::nextExprId, varInit);
    resultExpr = renumberExprIds(stableIdGenerator::nextExprId, resultExpr);

    long iterRangeId = stableIdGenerator.nextExprId();
    long loopConditionId = stableIdGenerator.nextExprId();
    long loopStepId = stableIdGenerator.nextExprId();
    long comprehensionId = stableIdGenerator.nextExprId();

      return MutableExpr.ofComprehension(
            comprehensionId,
            MutableComprehension.create(
                    "#unused",
                    MutableExpr.ofCreateList(iterRangeId, MutableCreateList.create()),
                    varName,
                    varInit,
                    MutableExpr.ofConstant(loopConditionId, CelConstant.ofValue(false)),
                    MutableExpr.ofIdent(loopStepId, varName),
                    resultExpr
            )
    );
  }

  private MutableExpr newBindMacroSourceExpr(MutableExpr bindMacroExpr, String varName, StableIdGenerator stableIdGenerator) {
    MutableExpr bindMacroCallExpr =
        MutableExpr.ofCall(
            0, // Required sentinel value for macro call
            MutableCall.create(
                MutableExpr.ofIdent(stableIdGenerator.nextExprId(), "cel"),
                "bind",
                MutableExpr.ofIdent(stableIdGenerator.nextExprId(), varName),
                bindMacroExpr.comprehension().accuInit(),
                bindMacroExpr.comprehension().result()
            )
        );

    // TODO: Remove?
    stableIdGenerator.nextExprId();

    return bindMacroCallExpr;
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
  private MutableAst stabilizeAst(MutableAst mutableAst, long seedExprId) {
    MutableExpr mutableExpr = mutableAst.mutableExpr();
    CelSource.Builder source = mutableAst.source();
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

     // Prune any NOT_SET (comprehension) nodes that no longer exist in the main AST
     // This can occur from pulling out a nested comprehension into a separate cel.block index
     CelNavigableExpr.fromMutableExpr(macroCallExpr)
         .allNodes()
         .filter(node -> node.getKind().equals(Kind.NOT_SET))
         .map(CelNavigableExpr::id)
         .filter(id -> !allExprs.containsKey(id))
         .forEach(
             id -> {
               ImmutableList<MutableExpr> newCallArgs =
                   macroCallExpr.call().args().stream()
                       .filter(node -> node.id() != id)
                       .collect(toImmutableList());
               MutableCall call = macroCallExpr.call();
               call.clearArgs();
               call.addArgs(newCallArgs);
               macroCall.setValue(MutableExprConverter.fromMutableExpr(macroCallExpr));
             });
   }

    return sourceBuilder;
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
    MutableExpr accuInit = comprehension.accuInit();
    if (!accuInit.exprKind().equals(Kind.CREATE_LIST)
        || !accuInit.createList().elements().isEmpty()) {
      // Does not contain an extraneous list.
      return;
    }

    MutableExpr loopStepExpr = comprehension.loopStep();
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

  private static long getMaxId(CelExpr newExpr) {
    return CelNavigableExpr.fromExpr(newExpr)
        .allNodes()
        .mapToLong(CelNavigableExpr::id)
        .max()
        .orElseThrow(NoSuchElementException::new);
  }

  private static long getMaxId(MutableAst mutableAst) {
    long maxId = getMaxId(mutableAst.mutableExpr());
    for (Entry<Long, CelExpr> macroCall : mutableAst.source().getMacroCalls().entrySet()) {
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
    public abstract MutableAst mutableAst();

    /** Map containing the mangled identifier names to their types. */
    public abstract ImmutableMap<MangledComprehensionName, MangledComprehensionType>
        mangledComprehensionMap();

    private static MangledComprehensionAst of(
        MutableAst ast,
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
}
