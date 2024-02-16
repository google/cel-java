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

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.collect.ImmutableSet;
import dev.cel.bundle.Cel;
import dev.cel.bundle.CelBuilder;
import dev.cel.common.CelAbstractSyntaxTree;
import dev.cel.common.CelValidationException;
import dev.cel.common.navigation.CelNavigableAst;
import java.util.Arrays;

final class CelOptimizerImpl implements CelOptimizer {
  private final Cel cel;
  private final ImmutableSet<CelAstOptimizer> astOptimizers;

  CelOptimizerImpl(Cel cel, ImmutableSet<CelAstOptimizer> astOptimizers) {
    this.cel = cel;
    this.astOptimizers = astOptimizers;
  }

  @Override
  public CelAbstractSyntaxTree optimize(CelAbstractSyntaxTree ast) throws CelOptimizationException {
    if (!ast.isChecked()) {
      throw new IllegalArgumentException("AST must be type-checked.");
    }

    CelAbstractSyntaxTree optimizedAst = ast;
    CelBuilder celBuilder = cel.toCelBuilder();
    try {
      for (CelAstOptimizer optimizer : astOptimizers) {
        CelNavigableAst navigableAst = CelNavigableAst.fromAst(optimizedAst);
        optimizedAst = optimizer.optimize(navigableAst, celBuilder);
        optimizedAst = celBuilder.build().check(optimizedAst).getAst();
      }
    } catch (CelValidationException e) {
      throw new CelOptimizationException(
          "Optimized AST failed to type-check: " + e.getMessage(), e);
    } catch (RuntimeException e) {
      throw new CelOptimizationException("Optimization failure: " + e.getMessage(), e);
    }

    return optimizedAst;
  }

  /** Create a new builder for constructing a {@link CelOptimizer} instance. */
  static CelOptimizerImpl.Builder newBuilder(Cel cel) {
    return new CelOptimizerImpl.Builder(cel);
  }

  /** Builder class for {@link CelOptimizerImpl}. */
  static final class Builder implements CelOptimizerBuilder {
    private final Cel cel;
    private final ImmutableSet.Builder<CelAstOptimizer> astOptimizers;

    private Builder(Cel cel) {
      this.cel = cel;
      this.astOptimizers = ImmutableSet.builder();
    }

    @Override
    public CelOptimizerBuilder addAstOptimizers(CelAstOptimizer... astOptimizers) {
      checkNotNull(astOptimizers);
      return addAstOptimizers(Arrays.asList(astOptimizers));
    }

    @Override
    public CelOptimizerBuilder addAstOptimizers(Iterable<CelAstOptimizer> astOptimizers) {
      checkNotNull(astOptimizers);
      this.astOptimizers.addAll(astOptimizers);
      return this;
    }

    @Override
    public CelOptimizer build() {
      return new CelOptimizerImpl(cel, astOptimizers.build());
    }
  }
}
