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
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import dev.cel.common.CelAbstractSyntaxTree;
import dev.cel.common.CelSource;
import dev.cel.common.annotations.Internal;
import dev.cel.common.ast.CelExpr;
import dev.cel.common.ast.CelExpr.CelCall;
import dev.cel.common.ast.CelExpr.CelComprehension;
import dev.cel.common.ast.CelExpr.CelCreateList;
import dev.cel.common.ast.CelExpr.CelCreateMap;
import dev.cel.common.ast.CelExpr.CelCreateStruct;
import dev.cel.common.ast.CelExpr.CelIdent;
import dev.cel.common.ast.CelExpr.CelSelect;
import dev.cel.common.ast.CelExpr.ExprKind.Kind;
import dev.cel.common.ast.CelExprFactory;
import dev.cel.common.ast.CelExprIdGeneratorFactory;
import dev.cel.common.ast.CelExprIdGeneratorFactory.ExprIdGenerator;
import dev.cel.common.ast.CelExprIdGeneratorFactory.StableIdGenerator;
import dev.cel.common.navigation.CelNavigableAst;
import dev.cel.common.navigation.CelNavigableExpr;
import dev.cel.common.navigation.CelNavigableExpr.TraversalOrder;
import java.util.Map.Entry;
import java.util.NoSuchElementException;
import java.util.Optional;

/** MutableAst contains logic for mutating a {@link CelExpr}. */
@Internal
final class MutableAst {
  private static final int MAX_ITERATION_COUNT = 1000;
  private static final ExprIdGenerator NO_OP_ID_GENERATOR = id -> id;

  private final CelExpr.Builder newExpr;
  private final ExprIdGenerator celExprIdGenerator;
  private int iterationCount;
  private long exprIdToReplace;

  private MutableAst(ExprIdGenerator celExprIdGenerator, CelExpr.Builder newExpr, long exprId) {
    this.celExprIdGenerator = celExprIdGenerator;
    this.newExpr = newExpr;
    this.exprIdToReplace = exprId;
  }

  /** Replaces all the expression IDs in the expression tree with 0. */
  static CelExpr clearExprIds(CelExpr celExpr) {
    return renumberExprIds((unused) -> 0, celExpr.toBuilder()).build();
  }

  /**
   * Mutates the given AST by replacing a subtree at a given index.
   *
   * @param ast Existing AST being mutated
   * @param newExpr New subtree to perform the replacement with.
   * @param exprIdToReplace The expr ID in the existing AST to replace the subtree at.
   */
  static CelAbstractSyntaxTree replaceSubtree(
      CelAbstractSyntaxTree ast, CelExpr newExpr, long exprIdToReplace) {
    return replaceSubtree(
        ast,
        CelAbstractSyntaxTree.newParsedAst(newExpr, CelSource.newBuilder().build()),
        exprIdToReplace);
  }

  /**
   * Mutates the given AST by replacing a subtree at a given index.
   *
   * @param ast Existing AST being mutated
   * @param newAst New subtree to perform the replacement with. If the subtree has a macro map
   *     populated, its macro source is merged with the existing AST's after normalization.
   * @param exprIdToReplace The expr ID in the existing AST to replace the subtree at.
   */
  static CelAbstractSyntaxTree replaceSubtree(
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
        replaceSubtreeImpl(
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

  /** Replaces the subtree at the given ID with a newly created bind macro. */
  static CelAbstractSyntaxTree replaceSubtreeWithNewBindMacro(
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

    return replaceSubtree(
        ast, CelAbstractSyntaxTree.newParsedAst(bindMacro.bindExpr(), celSource), exprIdToReplace);
  }

  static CelAbstractSyntaxTree renumberIdsConsecutively(CelAbstractSyntaxTree ast) {
    StableIdGenerator stableIdGenerator = CelExprIdGeneratorFactory.newStableIdGenerator(0);
    CelExpr.Builder root =
        renumberExprIds(stableIdGenerator::renumberId, ast.getExpr().toBuilder());
    CelSource newSource =
        normalizeMacroSource(
            ast.getSource(), Integer.MIN_VALUE, root, stableIdGenerator::renumberId);

    return CelAbstractSyntaxTree.newParsedAst(root.build(), newSource);
  }

  static CelAbstractSyntaxTree mangleComprehensionIdentifierNames(
      CelAbstractSyntaxTree ast, String newIdentPrefix) {
    int iterCount;
    CelNavigableAst newNavigableAst = CelNavigableAst.fromAst(ast);
    for (iterCount = 0; iterCount < MAX_ITERATION_COUNT; iterCount++) {
      Optional<CelNavigableExpr> maybeComprehensionExpr =
          newNavigableAst
              .getRoot()
              // This is important - mangling needs to happen bottom-up to avoid stepping over
              // shadowed variables that are not part of the comprehension being mangled.
              .allNodes(TraversalOrder.POST_ORDER)
              .filter(node -> node.getKind().equals(Kind.COMPREHENSION))
              .filter(node -> !node.expr().comprehension().iterVar().startsWith(newIdentPrefix))
              .findAny();
      if (!maybeComprehensionExpr.isPresent()) {
        break;
      }

      CelExpr.Builder comprehensionExpr = maybeComprehensionExpr.get().expr().toBuilder();
      String iterVar = comprehensionExpr.comprehension().iterVar();
      int comprehensionNestingLevel = countComprehensionNestingLevel(maybeComprehensionExpr.get());
      String mangledVarName = newIdentPrefix + comprehensionNestingLevel;

      CelExpr.Builder mutatedComprehensionExpr =
          mangleIdentsInComprehensionExpr(
              newNavigableAst.getAst().getExpr().toBuilder(),
              comprehensionExpr,
              iterVar,
              mangledVarName);
      // Repeat the mangling process for the macro source.
      CelSource newSource =
          mangleIdentsInMacroSource(
              newNavigableAst.getAst(),
              mutatedComprehensionExpr,
              iterVar,
              mangledVarName,
              comprehensionExpr.id());

      newNavigableAst =
          CelNavigableAst.fromAst(
              CelAbstractSyntaxTree.newParsedAst(mutatedComprehensionExpr.build(), newSource));
    }

    if (iterCount >= MAX_ITERATION_COUNT) {
      throw new IllegalStateException("Max iteration count reached.");
    }

    return newNavigableAst.getAst();
  }

  private static CelExpr.Builder mangleIdentsInComprehensionExpr(
      CelExpr.Builder root,
      CelExpr.Builder comprehensionExpr,
      String originalIterVar,
      String mangledVarName) {
    int iterCount;
    for (iterCount = 0; iterCount < MAX_ITERATION_COUNT; iterCount++) {
      Optional<CelExpr> identToMangle =
          CelNavigableExpr.fromExpr(comprehensionExpr.comprehension().loopStep())
              .descendants()
              .map(CelNavigableExpr::expr)
              .filter(node -> node.identOrDefault().name().equals(originalIterVar))
              .findAny();
      if (!identToMangle.isPresent()) {
        break;
      }

      comprehensionExpr =
          replaceSubtreeImpl(
              NO_OP_ID_GENERATOR,
              comprehensionExpr,
              CelExpr.newBuilder().setIdent(CelIdent.newBuilder().setName(mangledVarName).build()),
              identToMangle.get().id());
    }

    if (iterCount >= MAX_ITERATION_COUNT) {
      throw new IllegalStateException("Max iteration count reached.");
    }

    return replaceSubtreeImpl(
        NO_OP_ID_GENERATOR,
        root,
        comprehensionExpr.setComprehension(
            comprehensionExpr.comprehension().toBuilder().setIterVar(mangledVarName).build()),
        comprehensionExpr.id());
  }

  private static CelSource mangleIdentsInMacroSource(
      CelAbstractSyntaxTree ast,
      CelExpr.Builder mutatedComprehensionExpr,
      String originalIterVar,
      String mangledVarName,
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
    CelExpr.Builder macroExpr = newSource.getMacroCalls().get(originalComprehensionId).toBuilder();
    // By convention, the iteration variable is always the first argument of the
    // macro call expression.
    CelExpr identToMangle = macroExpr.call().args().get(0);
    if (!identToMangle.identOrDefault().name().equals(originalIterVar)) {
      throw new IllegalStateException(
          String.format(
              "Expected %s for iteration variable but got %s instead.",
              identToMangle.identOrDefault().name(), originalIterVar));
    }
    macroExpr =
        replaceSubtreeImpl(
            NO_OP_ID_GENERATOR,
            macroExpr,
            CelExpr.newBuilder().setIdent(CelIdent.newBuilder().setName(mangledVarName).build()),
            identToMangle.id());

    newSource.addMacroCalls(originalComprehensionId, macroExpr.build());
    return newSource.build();
  }

  private static BindMacro newBindMacro(
      String varName, CelExpr varInit, CelExpr resultExpr, StableIdGenerator stableIdGenerator) {
    // Renumber incoming expression IDs in the init and result expression to avoid collision with
    // the main AST. Existing IDs are memoized for a macro source sanitization pass at the end
    // (e.g: inserting a bind macro to an existing macro expr)
    varInit = renumberExprIds(stableIdGenerator::nextExprId, varInit.toBuilder()).build();
    resultExpr = renumberExprIds(stableIdGenerator::nextExprId, resultExpr.toBuilder()).build();
    CelExprFactory exprFactory =
        CelExprFactory.newInstance((unused) -> stableIdGenerator.nextExprId(-1));
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
                CelExpr.ofIdentExpr(stableIdGenerator.nextExprId(-1), "cel"),
                CelExpr.ofIdentExpr(stableIdGenerator.nextExprId(-1), varName),
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
  private static CelAbstractSyntaxTree stabilizeAst(CelAbstractSyntaxTree ast, long seedExprId) {
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

  private static CelSource normalizeMacroSource(
      CelSource celSource,
      long exprIdToReplace,
      CelExpr.Builder mutatedRoot,
      ExprIdGenerator idGenerator) {
    // Remove the macro metadata that no longer exists in the AST due to being replaced.
    celSource = celSource.toBuilder().clearMacroCall(exprIdToReplace).build();
    if (celSource.getMacroCalls().isEmpty()) {
      return CelSource.newBuilder().build();
    }

    CelSource.Builder sourceBuilder = CelSource.newBuilder();
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
          newCall =
              replaceSubtreeImpl((arg) -> arg, newCall, mutatedExpr.toBuilder(), callChild.id());
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
                    replaceSubtreeImpl(
                        (id) -> id,
                        macroCallExpr.toBuilder(),
                        CelExpr.ofNotSet(node.id()).toBuilder(),
                        node.id());
                macroCall.setValue(mutatedNode.build());
              });
    }

    return sourceBuilder.build();
  }

  private static CelExpr.Builder replaceSubtreeImpl(
      ExprIdGenerator idGenerator,
      CelExpr.Builder root,
      CelExpr.Builder newExpr,
      long exprIdToReplace) {
    MutableAst mutableAst = new MutableAst(idGenerator, newExpr, exprIdToReplace);
    return mutableAst.visit(root);
  }

  private static CelExpr.Builder renumberExprIds(
      ExprIdGenerator idGenerator, CelExpr.Builder root) {
    MutableAst mutableAst = new MutableAst(idGenerator, root, Integer.MIN_VALUE);
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

  private CelExpr.Builder visit(CelExpr.Builder expr) {
    if (++iterationCount > MAX_ITERATION_COUNT) {
      throw new IllegalStateException("Max iteration count reached.");
    }

    if (expr.id() == exprIdToReplace) {
      exprIdToReplace = Integer.MIN_VALUE; // Marks that the subtree has been replaced.
      return visit(newExpr.setId(expr.id()));
    }

    expr.setId(celExprIdGenerator.generate(expr.id()));

    switch (expr.exprKind().getKind()) {
      case SELECT:
        return visit(expr, expr.select().toBuilder());
      case CALL:
        return visit(expr, expr.call().toBuilder());
      case CREATE_LIST:
        return visit(expr, expr.createList().toBuilder());
      case CREATE_STRUCT:
        return visit(expr, expr.createStruct().toBuilder());
      case CREATE_MAP:
        return visit(expr, expr.createMap().toBuilder());
      case COMPREHENSION:
        return visit(expr, expr.comprehension().toBuilder());
      case CONSTANT: // Fall-through is intended
      case IDENT:
      case NOT_SET: // Note: comprehension arguments can contain a not set expr.
        return expr;
      default:
        throw new IllegalArgumentException("unexpected expr kind: " + expr.exprKind().getKind());
    }
  }

  private CelExpr.Builder visit(CelExpr.Builder expr, CelSelect.Builder select) {
    select.setOperand(visit(select.operand().toBuilder()).build());
    return expr.setSelect(select.build());
  }

  private CelExpr.Builder visit(CelExpr.Builder expr, CelCall.Builder call) {
    if (call.target().isPresent()) {
      call.setTarget(visit(call.target().get().toBuilder()).build());
    }
    ImmutableList<CelExpr.Builder> argsBuilders = call.getArgsBuilders();
    for (int i = 0; i < argsBuilders.size(); i++) {
      CelExpr.Builder arg = argsBuilders.get(i);
      call.setArg(i, visit(arg).build());
    }

    return expr.setCall(call.build());
  }

  private CelExpr.Builder visit(CelExpr.Builder expr, CelCreateStruct.Builder createStruct) {
    ImmutableList<CelCreateStruct.Entry.Builder> entries = createStruct.getEntriesBuilders();
    for (int i = 0; i < entries.size(); i++) {
      CelCreateStruct.Entry.Builder entry = entries.get(i);
      entry.setId(celExprIdGenerator.generate(entry.id()));
      entry.setValue(visit(entry.value().toBuilder()).build());

      createStruct.setEntry(i, entry.build());
    }

    return expr.setCreateStruct(createStruct.build());
  }

  private CelExpr.Builder visit(CelExpr.Builder expr, CelCreateMap.Builder createMap) {
    ImmutableList<CelCreateMap.Entry.Builder> entriesBuilders = createMap.getEntriesBuilders();
    for (int i = 0; i < entriesBuilders.size(); i++) {
      CelCreateMap.Entry.Builder entry = entriesBuilders.get(i);
      entry.setId(celExprIdGenerator.generate(entry.id()));
      entry.setKey(visit(entry.key().toBuilder()).build());
      entry.setValue(visit(entry.value().toBuilder()).build());

      createMap.setEntry(i, entry.build());
    }

    return expr.setCreateMap(createMap.build());
  }

  private CelExpr.Builder visit(CelExpr.Builder expr, CelCreateList.Builder createList) {
    ImmutableList<CelExpr.Builder> elementsBuilders = createList.getElementsBuilders();
    for (int i = 0; i < elementsBuilders.size(); i++) {
      CelExpr.Builder elem = elementsBuilders.get(i);
      createList.setElement(i, visit(elem).build());
    }

    return expr.setCreateList(createList.build());
  }

  private CelExpr.Builder visit(CelExpr.Builder expr, CelComprehension.Builder comprehension) {
    comprehension.setIterRange(visit(comprehension.iterRange().toBuilder()).build());
    comprehension.setAccuInit(visit(comprehension.accuInit().toBuilder()).build());
    comprehension.setLoopCondition(visit(comprehension.loopCondition().toBuilder()).build());
    comprehension.setLoopStep(visit(comprehension.loopStep().toBuilder()).build());
    comprehension.setResult(visit(comprehension.result().toBuilder()).build());

    return expr.setComprehension(comprehension.build());
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
