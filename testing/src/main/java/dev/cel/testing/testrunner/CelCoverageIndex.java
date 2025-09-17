// Copyright 2025 Google LLC
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
package dev.cel.testing.testrunner;

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import javax.annotation.concurrent.ThreadSafe;
import dev.cel.common.CelAbstractSyntaxTree;
import dev.cel.common.ast.CelExpr;
import dev.cel.common.ast.CelExpr.ExprKind;
import dev.cel.common.navigation.CelNavigableAst;
import dev.cel.common.navigation.CelNavigableExpr;
import dev.cel.common.types.CelKind;
import dev.cel.parser.CelUnparserVisitor;
import dev.cel.runtime.CelEvaluationListener;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import java.util.logging.Logger;

/**
 * A class for managing the coverage index for CEL tests.
 *
 * <p>This class is used to manage the coverage index for CEL tests. It provides a method for
 * getting the coverage index for a given test case.
 */
final class CelCoverageIndex {

  private static final Logger logger = Logger.getLogger(CelCoverageIndex.class.getName());

  private CelAbstractSyntaxTree ast;
  private final ConcurrentHashMap<Long, NodeCoverageStats> nodeCoverageStatsMap =
      new ConcurrentHashMap<>();

  public void init(CelAbstractSyntaxTree ast) {
    this.ast = ast;
    CelNavigableExpr.fromExpr(ast.getExpr())
        .allNodes()
        .forEach(
            celNavigableExpr -> {
              NodeCoverageStats nodeCoverageStats = new NodeCoverageStats();
              nodeCoverageStats.isBooleanNode.set(isNodeTypeBoolean(celNavigableExpr.expr()));
              nodeCoverageStatsMap.put(celNavigableExpr.id(), nodeCoverageStats);
            });
  }

  /**
   * Returns the evaluation listener for the CEL test suite.
   *
   * <p>This listener is used to track the coverage of the CEL test suite.
   */
  public CelEvaluationListener newEvaluationListener() {
    return new EvaluationListener(nodeCoverageStatsMap);
  }

  /** Returns the coverage report for the CEL test suite. */
  public CoverageReport generateCoverageReport() {
    CoverageReport.Builder reportBuilder =
        CoverageReport.builder().setCelExpression(new CelUnparserVisitor(ast).unparse());
    traverseAndCalculateCoverage(
        CelNavigableAst.fromAst(ast).getRoot(), nodeCoverageStatsMap, true, "", reportBuilder);
    CoverageReport report = reportBuilder.build();
    logger.info("CEL Expression: " + report.celExpression());
    logger.info("Nodes: " + report.nodes());
    logger.info("Covered Nodes: " + report.coveredNodes());
    logger.info("Branches: " + report.branches());
    logger.info("Covered Boolean Outcomes: " + report.coveredBooleanOutcomes());
    logger.info("Unencountered Nodes: \n" + String.join("\n", report.unencounteredNodes()));
    logger.info("Unencountered Branches: \n" + String.join("\n",
    report.unencounteredBranches()));
    return report;
  }

  /** A class for managing the coverage report for a CEL test suite. */
  @AutoValue
  public abstract static class CoverageReport {
    public abstract String celExpression();

    public abstract long nodes();

    public abstract long coveredNodes();

    public abstract long branches();

    public abstract long coveredBooleanOutcomes();

    public abstract ImmutableList<String> unencounteredNodes();

    public abstract ImmutableList<String> unencounteredBranches();

    public static Builder builder() {
      return new AutoValue_CelCoverageIndex_CoverageReport.Builder()
          .setNodes(0L)
          .setCoveredNodes(0L)
          .setBranches(0L)
          .setCelExpression("")
          .setCoveredBooleanOutcomes(0L);
    }

    /** Builder for {@link CoverageReport}. */
    @AutoValue.Builder
    public abstract static class Builder {
      public abstract Builder setCelExpression(String value);

      public abstract long nodes();

      public abstract Builder setNodes(long value);

      public abstract long coveredNodes();

      public abstract Builder setCoveredNodes(long value);

      public abstract long branches();

      public abstract Builder setBranches(long value);

      public abstract long coveredBooleanOutcomes();

      public abstract Builder setCoveredBooleanOutcomes(long value);

      public abstract ImmutableList.Builder<String> unencounteredNodesBuilder();

      public abstract ImmutableList.Builder<String> unencounteredBranchesBuilder();

      @CanIgnoreReturnValue
      public final Builder addUnencounteredNodes(String value) {
        unencounteredNodesBuilder().add(value);
        return this;
      }

      @CanIgnoreReturnValue
      public final Builder addUnencounteredBranches(String value) {
        unencounteredBranchesBuilder().add(value);
        return this;
      }

      public abstract CoverageReport build();
    }
  }

  /** A class for managing the coverage stats for a CEL node. */
  @ThreadSafe
  private static final class NodeCoverageStats {
    final AtomicBoolean isBooleanNode = new AtomicBoolean(false);
    final AtomicBoolean covered = new AtomicBoolean(false);
    final AtomicBoolean hasTrueBranch = new AtomicBoolean(false);
    final AtomicBoolean hasFalseBranch = new AtomicBoolean(false);
  }

  private Boolean isNodeTypeBoolean(CelExpr celExpr) {
    return ast.getTypeMap().containsKey(celExpr.id())
        && ast.getTypeMap().get(celExpr.id()).kind().equals(CelKind.BOOL);
  }

  private void traverseAndCalculateCoverage(
      CelNavigableExpr node,
      Map<Long, NodeCoverageStats> statsMap,
      boolean logUnencountered,
      String precedingTabs,
      CoverageReport.Builder reportBuilder) {
    long nodeId = node.id();
    NodeCoverageStats stats = statsMap.getOrDefault(nodeId, new NodeCoverageStats());
    reportBuilder.setNodes(reportBuilder.nodes() + 1);

    boolean isInterestingBooleanNode = isInterestingBooleanNode(node, stats);

    // Only unparse if the node is interesting (boolean node) and we need to log
    // unencountered nodes.
    String exprText = "";
    if (isInterestingBooleanNode && logUnencountered) {
      exprText = new CelUnparserVisitor(ast).unparse(node.expr());
    }

    // Update coverage for the current node and determine if we should continue logging
    // unencountered.
    logUnencountered =
        updateNodeCoverage(
            nodeId, stats, isInterestingBooleanNode, exprText, logUnencountered, reportBuilder);

    if (isInterestingBooleanNode) {
      precedingTabs =
          updateBooleanBranchCoverage(
              nodeId, stats, exprText, precedingTabs, logUnencountered, reportBuilder);
    }

    for (CelNavigableExpr child : node.children().collect(toImmutableList())) {
      traverseAndCalculateCoverage(child, statsMap, logUnencountered, precedingTabs, reportBuilder);
    }
  }

  private boolean isInterestingBooleanNode(CelNavigableExpr node, NodeCoverageStats stats) {
    return stats.isBooleanNode.get()
        && !node.expr().getKind().equals(ExprKind.Kind.CONSTANT)
        && !(node.expr().getKind().equals(ExprKind.Kind.CALL)
            && node.expr().call().function().equals("cel.@block"));
  }

  /**
   * Updates the coverage report based on whether the current node was covered. Returns true if
   * logging of unencountered nodes should continue for children, false otherwise.
   */
  private boolean updateNodeCoverage(
      long nodeId,
      NodeCoverageStats stats,
      boolean isInterestingBooleanNode,
      String exprText,
      boolean logUnencountered,
      CoverageReport.Builder reportBuilder) {
    if (stats.covered.get()) {
      reportBuilder.setCoveredNodes(reportBuilder.coveredNodes() + 1);
    } else {
      if (logUnencountered) {
        if (isInterestingBooleanNode) {
          reportBuilder.addUnencounteredNodes(
              String.format("Expression ID %d ('%s')", nodeId, exprText));
        }
        // Once an unencountered node is found, we don't log further unencountered nodes in its
        // subtree to avoid noise.
        return false;
      }
    }
    return logUnencountered;
  }

  /**
   * Updates the coverage report for boolean nodes, including branch coverage. Returns the
   * potentially modified `precedingTabs` string.
   */
  private String updateBooleanBranchCoverage(
      long nodeId,
      NodeCoverageStats stats,
      String exprText,
      String precedingTabs,
      boolean logUnencountered,
      CoverageReport.Builder reportBuilder) {
    reportBuilder.setBranches(reportBuilder.branches() + 2);
    if (stats.hasTrueBranch.get()) {
      reportBuilder.setCoveredBooleanOutcomes(reportBuilder.coveredBooleanOutcomes() + 1);
    } else if (logUnencountered) {
      reportBuilder.addUnencounteredBranches(
          String.format(
              "%sExpression ID %d ('%s'): lacks 'true' coverage", precedingTabs, nodeId, exprText));
      precedingTabs += "\t\t";
    }
    if (stats.hasFalseBranch.get()) {
      reportBuilder.setCoveredBooleanOutcomes(reportBuilder.coveredBooleanOutcomes() + 1);
    } else if (logUnencountered) {
      reportBuilder.addUnencounteredBranches(
          String.format(
              "%sExpression ID %d ('%s'): lacks 'false' coverage",
              precedingTabs, nodeId, exprText));
      precedingTabs += "\t\t";
    }
    return precedingTabs;
  }

  @ThreadSafe
  private static final class EvaluationListener implements CelEvaluationListener {

    private final ConcurrentHashMap<Long, NodeCoverageStats> nodeCoverageStatsMap;

    EvaluationListener(ConcurrentHashMap<Long, NodeCoverageStats> nodeCoverageStatsMap) {
      this.nodeCoverageStatsMap = nodeCoverageStatsMap;
    }

    @Override
    public void callback(CelExpr celExpr, Object evaluationResult) {
      NodeCoverageStats nodeCoverageStats = nodeCoverageStatsMap.get(celExpr.id());
      nodeCoverageStats.covered.set(true);
      if (nodeCoverageStats.isBooleanNode.get()) {
        if (evaluationResult instanceof Boolean) {
          if ((Boolean) evaluationResult) {
            nodeCoverageStats.hasTrueBranch.set(true);
          } else {
            nodeCoverageStats.hasFalseBranch.set(true);
          }
        }
      }
    }
  }
}
