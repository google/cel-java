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
import dev.cel.common.types.SimpleType;
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
}
