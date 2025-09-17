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

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableMap;
import dev.cel.bundle.Cel;
import dev.cel.bundle.CelFactory;
import dev.cel.common.CelAbstractSyntaxTree;
import dev.cel.common.CelOptions;
import dev.cel.common.types.SimpleType;
import dev.cel.compiler.CelCompiler;
import dev.cel.extensions.CelExtensions;
import dev.cel.parser.CelStandardMacro;
import dev.cel.runtime.CelEvaluationListener;
import dev.cel.runtime.CelRuntime;
import dev.cel.testing.testrunner.CelCoverageIndex.CoverageReport;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class CelCoverageIndexTest {

  private Cel cel;
  private CelAbstractSyntaxTree ast;
  private CelRuntime.Program program;

  @Before
  public void setUp() throws Exception {
    cel =
        CelFactory.standardCelBuilder()
            .addVar("x", SimpleType.INT)
            .addVar("y", SimpleType.INT)
            .build();
    ast = cel.compile("x > 1 && y > 1").getAst();
    program = cel.createProgram(ast);
  }

  @Test
  public void getCoverageReport_fullCoverage() throws Exception {
    CelCoverageIndex coverageIndex = new CelCoverageIndex();
    coverageIndex.init(ast);
    CelEvaluationListener listener = coverageIndex.newEvaluationListener();

    program.trace(ImmutableMap.of("x", 2L, "y", 2L), listener);

    CoverageReport report = coverageIndex.generateCoverageReport();
    assertThat(report.nodes()).isGreaterThan(0);
    assertThat(report.coveredNodes()).isEqualTo(report.nodes());
    assertThat(report.branches()).isEqualTo(6);
    assertThat(report.coveredBooleanOutcomes())
        .isEqualTo(3); // x>1 -> true, y>1 -> true, && -> true
    assertThat(report.unencounteredNodes()).isEmpty();
    assertThat(report.unencounteredBranches())
        .containsExactly(
            "Expression ID 4 ('x > 1 && y > 1'): lacks 'false' coverage",
            "\t\tExpression ID 2 ('x > 1'): lacks 'false' coverage",
            "\t\tExpression ID 6 ('y > 1'): lacks 'false' coverage");
  }

  @Test
  public void getCoverageReport_partialCoverage_shortCircuit() throws Exception {
    CelCoverageIndex coverageIndex = new CelCoverageIndex();
    coverageIndex.init(ast);
    CelEvaluationListener listener = coverageIndex.newEvaluationListener();

    program.trace(ImmutableMap.of("x", 0L, "y", 2L), listener);

    CoverageReport report = coverageIndex.generateCoverageReport();
    assertThat(report.celExpression()).isEqualTo("x > 1 && y > 1");
    assertThat(report.nodes()).isGreaterThan(0);
    assertThat(report.coveredNodes()).isLessThan(report.nodes());
    assertThat(report.branches()).isEqualTo(6);
    assertThat(report.coveredBooleanOutcomes()).isEqualTo(2); // x>1 -> false, && -> false
    assertThat(report.unencounteredNodes()).containsExactly("Expression ID 6 ('y > 1')");
    // y > 1 is unencountered, so logUnencountered becomes false, and branch coverage for y > 1
    // isn't logged to unencounteredBranches.
    assertThat(report.unencounteredBranches())
        .containsExactly(
            "Expression ID 4 ('x > 1 && y > 1'): lacks 'true' coverage",
            "\t\tExpression ID 2 ('x > 1'): lacks 'true' coverage");
  }

  @Test
  public void getCoverageReport_comprehension_generatesDotGraph() throws Exception {
    cel = CelFactory.standardCelBuilder().build();
    CelCompiler compiler =
        cel.toCompilerBuilder()
            .setOptions(CelOptions.newBuilder().populateMacroCalls(true).build())
            .setStandardMacros(CelStandardMacro.STANDARD_MACROS)
            .addLibraries(CelExtensions.comprehensions())
            .build();
    ast = compiler.compile("[1, 2, 3].all(i, i % 2 != 0)").getAst();
    program = cel.createProgram(ast);
    CelCoverageIndex coverageIndex = new CelCoverageIndex();
    coverageIndex.init(ast);
    CelEvaluationListener listener = coverageIndex.newEvaluationListener();

    program.trace(ImmutableMap.of(), listener);

    CoverageReport report = coverageIndex.generateCoverageReport();
    assertThat(report.dotGraph())
        .contains("label=\"{<1> exprID: 1 | <2> IterRange} | <3> [1, 2, 3]\"");
    assertThat(report.dotGraph()).contains("label=\"{<1> exprID: 12 | <2> AccuInit} | <3> true\"");
    assertThat(report.dotGraph()).doesNotContain("red"); // No unencountered nodes.
    assertThat(report.dotGraph())
        .contains(
            "label=\"{<1> exprID: 14 | <2> LoopCondition} | <3>"
                + " @not_strictly_false(@result)\"");
    assertThat(report.dotGraph())
        .contains("label=\"{<1> exprID: 16 | <2> LoopStep} | <3> @result && i % 2 != 0\"");
    assertThat(report.dotGraph()).contains("label=\"{<1> exprID: 17 | <2> Result} | <3> @result\"");
  }
}
