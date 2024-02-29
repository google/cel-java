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

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import dev.cel.bundle.Cel;
import dev.cel.bundle.CelFactory;
import dev.cel.common.CelAbstractSyntaxTree;
import dev.cel.common.CelSource;
import dev.cel.common.CelValidationException;
import dev.cel.common.ast.CelExpr;
import dev.cel.optimizer.CelAstOptimizer.OptimizationResult;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class CelOptimizerImplTest {

  private static final Cel CEL = CelFactory.standardCelBuilder().build();

  @Test
  public void constructCelOptimizer_success() {
    CelOptimizer celOptimizer =
        CelOptimizerImpl.newBuilder(CEL)
            .addAstOptimizers(
                (navigableAst, cel) ->
                    // no-op
                    OptimizationResult.create(navigableAst.getAst()))
            .build();

    assertThat(celOptimizer).isNotNull();
    assertThat(celOptimizer).isInstanceOf(CelOptimizerImpl.class);
  }

  @Test
  public void astOptimizers_invokedInOrder() throws Exception {
    List<Integer> list = new ArrayList<>();

    CelOptimizer celOptimizer =
        CelOptimizerImpl.newBuilder(CEL)
            .addAstOptimizers(
                (navigableAst, cel) -> {
                  list.add(1);
                  return OptimizationResult.create(navigableAst.getAst());
                })
            .addAstOptimizers(
                (navigableAst, cel) -> {
                  list.add(2);
                  return OptimizationResult.create(navigableAst.getAst());
                })
            .addAstOptimizers(
                (navigableAst, cel) -> {
                  list.add(3);
                  return OptimizationResult.create(navigableAst.getAst());
                })
            .build();

    CelAbstractSyntaxTree ast = celOptimizer.optimize(CEL.compile("'hello world'").getAst());

    assertThat(ast).isNotNull();
    assertThat(list).containsExactly(1, 2, 3).inOrder();
  }

  @Test
  public void optimizer_whenAstOptimizerThrows_throwsException() {
    CelOptimizer celOptimizer =
        CelOptimizerImpl.newBuilder(CEL)
            .addAstOptimizers(
                (navigableAst, cel) -> {
                  throw new IllegalArgumentException("Test exception");
                })
            .build();

    CelOptimizationException e =
        assertThrows(
            CelOptimizationException.class,
            () -> celOptimizer.optimize(CEL.compile("'hello world'").getAst()));
    assertThat(e).hasMessageThat().isEqualTo("Optimization failure: Test exception");
    assertThat(e).hasCauseThat().isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  public void parsedAst_throwsException() {
    CelOptimizer celOptimizer = CelOptimizerImpl.newBuilder(CEL).build();

    IllegalArgumentException e =
        assertThrows(
            IllegalArgumentException.class,
            () -> celOptimizer.optimize(CEL.parse("'test'").getAst()));
    assertThat(e).hasMessageThat().contains("AST must be type-checked.");
  }

  @Test
  public void optimizedAst_failsToTypeCheck_throwsException() {
    CelOptimizer celOptimizer =
        CelOptimizerImpl.newBuilder(CEL)
            .addAstOptimizers(
                (navigableAst, cel) ->
                    OptimizationResult.create(
                        CelAbstractSyntaxTree.newParsedAst(
                            CelExpr.ofIdentExpr(1, "undeclared_ident"),
                            CelSource.newBuilder().build())))
            .build();

    CelOptimizationException e =
        assertThrows(
            CelOptimizationException.class,
            () -> celOptimizer.optimize(CEL.compile("'hello world'").getAst()));

    assertThat(e)
        .hasMessageThat()
        .contains(
            "Optimized AST failed to type-check: ERROR: :1:1: undeclared reference to"
                + " 'undeclared_ident' (in container '')");
    assertThat(e).hasCauseThat().isInstanceOf(CelValidationException.class);
  }
}
