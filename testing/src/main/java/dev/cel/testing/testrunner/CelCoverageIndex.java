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
import static java.nio.charset.StandardCharsets.UTF_8;

import javax.annotation.concurrent.ThreadSafe;
import dev.cel.common.CelAbstractSyntaxTree;
import dev.cel.common.ast.CelExpr;
import dev.cel.common.ast.CelExpr.ExprKind;
import dev.cel.common.navigation.CelNavigableAst;
import dev.cel.common.navigation.CelNavigableExpr;
import dev.cel.common.types.CelKind;
import dev.cel.parser.CelUnparserVisitor;
import dev.cel.runtime.CelEvaluationListener;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import java.util.logging.Logger;

/**
 * A class for managing the coverage index for CEL tests.
 *
 * <p>This class is used to manage the coverage index for CEL tests. It provides a method for
 * getting the coverage index for a given test case.
 */
final class CelCoverageIndex {

  private static final Logger logger = Logger.getLogger(CelCoverageIndex.class.getName());

  private static final String UNCOVERED_NODE_COLOR = "googlered";
  private static final String PARTIALLY_COVERED_NODE_COLOR = "googleyellow";
  private static final String COMPLETELY_COVERED_NODE_COLOR = "googlegreen";
  private static final String GRAPHVIZ_URL_PREFIX =
      "https://graphviz.corp.google.com/render_svg?layout_engine=dot&dot=";
  private static final String G3STYLESHEET_URL =
      "https://g3doc.corp.google.com/frameworks/g3doc/includes/graphviz-style.css";

  private CelAbstractSyntaxTree ast;
  private final Map<Long, NodeCoverageStats> nodeCoverageStatsMap = new HashMap<>();

  public void setAst(CelAbstractSyntaxTree ast) {
    this.ast = ast;
    CelNavigableExpr.fromExpr(ast.getExpr())
        .allNodes()
        .forEach(
            celNavigableExpr -> {
              NodeCoverageStats nodeCoverageStats = new NodeCoverageStats();
              nodeCoverageStats.isBooleanNode = inferBooleanNodeType(celNavigableExpr.expr());
              nodeCoverageStatsMap.put(celNavigableExpr.id(), nodeCoverageStats);
            });
  }

  /**
   * Returns the evaluation listener for the CEL test suite.
   *
   * <p>This listener is used to track the coverage of the CEL test suite.
   */
  public CelEvaluationListener getEvaluationListener() {
    return new EvaluationListener(nodeCoverageStatsMap);
  }

  /** Returns the coverage report for the CEL test suite. */
  public CoverageReport getCoverageReport() {
    CoverageReport report = new CoverageReport();
    report.dotGraph = "digraph {\nstylesheet = \"" + G3STYLESHEET_URL + "\"\n";
    traverseAndCalculateCoverage(
        CelNavigableAst.fromAst(ast).getRoot(), nodeCoverageStatsMap, true, "", report);
    report.dotGraph += "}";
    try {
      report.graphUrl =
          GRAPHVIZ_URL_PREFIX
              + URLEncoder.encode(report.dotGraph, UTF_8.name()).replace(".", "%2E");
    } catch (UnsupportedEncodingException e) {
      throw new LinkageError(e.getMessage(), e);
    }
    report.celExpr = new CelUnparserVisitor(ast).unparse(ast.getExpr());
    logger.info("CEL Expression: " + report.celExpr);
    logger.info("Nodes: " + report.nodes);
    logger.info("Covered Nodes: " + report.coveredNodes);
    logger.info("Branches: " + report.branches);
    logger.info("Covered Boolean Outcomes: " + report.coveredBooleanOutcomes);
    logger.info("Unencountered Nodes: \n" + String.join("\n", report.unencounteredNodes));
    logger.info("Unencountered Branches: \n" + String.join("\n", report.unencounteredBranches));
    return report;
  } 


  /** A class for managing the coverage report for a CEL test suite. */
  public static final class CoverageReport {
    String celExpr;
    long nodes = 0L;
    long coveredNodes = 0L;
    long branches = 0L;
    long coveredBooleanOutcomes = 0L;
    List<String> unencounteredNodes = new ArrayList<>();
    List<String> unencounteredBranches = new ArrayList<>();
    String dotGraph;
    String graphUrl;
  }

  /** A class for managing the coverage stats for a CEL node. */
  private static final class NodeCoverageStats {
    Boolean isBooleanNode;
    Boolean covered = false;
    Boolean hasTrueBranch = false;
    Boolean hasFalseBranch = false;
  }

  private Boolean inferBooleanNodeType(CelExpr celExpr) {
    return ast.getTypeMap().containsKey(celExpr.id())
        && ast.getTypeMap().get(celExpr.id()).kind().equals(CelKind.BOOL);
  }

  private void traverseAndCalculateCoverage(
      CelNavigableExpr node,
      Map<Long, NodeCoverageStats> statsMap,
      boolean logUnencountered,
      String precedingTabs,
      CoverageReport report) {
    long nodeId = node.id();
    NodeCoverageStats stats = statsMap.getOrDefault(nodeId, new NodeCoverageStats());
    report.nodes++;

    boolean isInterestingBooleanNode = isInterestingBooleanNode(node, stats);

    String exprText = new CelUnparserVisitor(ast).unparse(node.expr());
    String nodeCoverageColor = UNCOVERED_NODE_COLOR;
    if (stats.covered) {
      if (isInterestingBooleanNode) {
        if (stats.hasTrueBranch && stats.hasFalseBranch) {
          nodeCoverageColor = COMPLETELY_COVERED_NODE_COLOR;
        } else {
          nodeCoverageColor = PARTIALLY_COVERED_NODE_COLOR;
        }
      } else {
        nodeCoverageColor = COMPLETELY_COVERED_NODE_COLOR;
      }
    }
    String escapedExprText = escapeSpecialCharacters(exprText);
    report.dotGraph +=
        String.format(
            "%d [shape=record, id=%s, label=\"{<1> exprID: %d | <2> %s} | <3> %s\"];\n",
            nodeId, nodeCoverageColor, nodeId, kindToString(node), escapedExprText);

    // Update coverage for the current node and determine if we should continue logging unencountered.
    logUnencountered = updateNodeCoverage(nodeId, stats, isInterestingBooleanNode, exprText, logUnencountered, report);

    if (isInterestingBooleanNode) {
      precedingTabs = updateBooleanBranchCoverage(nodeId, stats, exprText, precedingTabs, logUnencountered, report);
    }

    for (CelNavigableExpr child : node.children().collect(toImmutableList())) {
      report.dotGraph += String.format("%d -> %d;\n", nodeId, child.id());
      traverseAndCalculateCoverage(child, statsMap, logUnencountered, precedingTabs, report);
    }
  }

  private boolean isInterestingBooleanNode(CelNavigableExpr node, NodeCoverageStats stats) {
    return stats.isBooleanNode
        && !node.expr().getKind().equals(ExprKind.Kind.CONSTANT)
        && !(node.expr().getKind().equals(ExprKind.Kind.CALL)
            && node.expr().call().function().equals("cel.@block"));
  }

  /**
   * Updates the coverage report based on whether the current node was covered.
   * Returns true if logging of unencountered nodes should continue for children, false otherwise.
   */
  private boolean updateNodeCoverage(
      long nodeId,
      NodeCoverageStats stats,
      boolean isInterestingBooleanNode,
      String exprText,
      boolean logUnencountered,
      CoverageReport report) {
    if (stats.covered) {
      report.coveredNodes++;
      return logUnencountered;
    } else {
      if (logUnencountered) {
        if (isInterestingBooleanNode) {
          report.unencounteredNodes.add(String.format("Expression ID %d ('%s')", nodeId, exprText));
        }
        // Once an unencountered node is found, we don't log further unencountered nodes in its
        // subtree to avoid noise.
        return false;
      }
      return logUnencountered;
    }
  }

  /**
   * Updates the coverage report for boolean nodes, including branch coverage.
   * Returns the potentially modified `precedingTabs` string.
   */
  private String updateBooleanBranchCoverage(
      long nodeId,
      NodeCoverageStats stats,
      String exprText,
      String precedingTabs,
      boolean logUnencountered,
      CoverageReport report) {
    report.branches += 2;
    if (stats.hasTrueBranch) {
      report.coveredBooleanOutcomes++;
    } else if (logUnencountered) {
      report.unencounteredBranches.add(
          String.format(
              "%sExpression ID %d ('%s'): Never evaluated to 'true'",
              precedingTabs, nodeId, exprText));
      precedingTabs += "\t\t";
    }
    if (stats.hasFalseBranch) {
      report.coveredBooleanOutcomes++;
    } else if (logUnencountered) {
      report.unencounteredBranches.add(
          String.format(
              "%sExpression ID %d ('%s'): Never evaluated to 'false'",
              precedingTabs, nodeId, exprText));
      precedingTabs += "\t\t";
    }
    return precedingTabs;
  }

  @ThreadSafe
  private static final class EvaluationListener implements CelEvaluationListener {

    private final Map<Long, NodeCoverageStats> nodeCoverageStatsMap;

    EvaluationListener(Map<Long, NodeCoverageStats> nodeCoverageStatsMap) {
      this.nodeCoverageStatsMap = nodeCoverageStatsMap;
    }

    @Override
    public void callback(CelExpr celExpr, Object evaluationResult) {
      NodeCoverageStats nodeCoverageStats = nodeCoverageStatsMap.get(celExpr.id());
      nodeCoverageStats.covered = true;
      if (nodeCoverageStats.isBooleanNode) {
        if (evaluationResult instanceof Boolean) {
          if ((Boolean) evaluationResult) {
            nodeCoverageStats.hasTrueBranch = true;
          } else {
            nodeCoverageStats.hasFalseBranch = true;
          }
        }
      }
    }
  }

  private String kindToString(CelNavigableExpr node) {
    if (node.parent().isPresent()
        && node.parent().get().expr().getKind().equals(ExprKind.Kind.COMPREHENSION)) {
      CelExpr.CelComprehension comp = node.parent().get().expr().comprehension();
      if (node.id() == comp.iterRange().id()) {
        return "IterRange";
      }
      if (node.id() == comp.accuInit().id()) {
        return "AccuInit";
      }
      if (node.id() == comp.loopCondition().id()) {
        return "LoopCondition";
      }
      if (node.id() == comp.loopStep().id()) {
        return "LoopStep";
      }
      if (node.id() == comp.result().id()) {
        return "Result";
      }
    }

    switch (node.getKind()) {
      case CALL:
        return "Call Node";
      case COMPREHENSION:
        return "Comprehension Node";
      case IDENT:
        return "Ident Node";
      case LIST:
        return "List Node";
      case CONSTANT:
        return "Literal Node";
      case MAP:
        return "Map Node";
      case SELECT:
        return "Select Node";
      case STRUCT:
        return "Struct Node";
      default:
        return "Unspecified Node";
    }
  }

  private String escapeSpecialCharacters(String exprText) {
    return exprText
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("||", " \\| \\| ")
        .replace("<", "\\<")
        .replace(">", "\\>")
        .replace("{", "\\{")
        .replace("}", "\\}");
  }
}
