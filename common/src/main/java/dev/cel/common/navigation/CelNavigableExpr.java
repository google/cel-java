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

package dev.cel.common.navigation;

import com.google.auto.value.AutoValue;
import dev.cel.common.ast.CelExpr;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * CelNavigableExpr decorates {@link CelExpr} with capabilities to inspect the parent and its
 * descendants with ease.
 */
@AutoValue
// unchecked: Generic types are properly bound to BaseNavigableExpr
// redundant override: Overriding is required to specify the return type to a concrete type.
@SuppressWarnings({"unchecked", "RedundantOverride"})
public abstract class CelNavigableExpr extends BaseNavigableExpr<CelExpr> {
  /** Constructs a new instance of {@link CelNavigableExpr} from {@link CelExpr}. */
  public static CelNavigableExpr fromExpr(CelExpr expr) {
    ExprHeightCalculator<CelExpr> exprHeightCalculator = new ExprHeightCalculator<>(expr);
    return builder().setExpr(expr).setHeight(exprHeightCalculator.getHeight(expr.id())).build();
  }

  @Override
  public Stream<CelNavigableExpr> allNodes() {
    return super.allNodes();
  }

  @Override
  public Stream<CelNavigableExpr> allNodes(TraversalOrder traversalOrder) {
    return super.allNodes(traversalOrder);
  }

  @Override
  public abstract Optional<CelNavigableExpr> parent();

  @Override
  public Stream<CelNavigableExpr> descendants() {
    return super.descendants();
  }

  @Override
  public Stream<CelNavigableExpr> descendants(TraversalOrder traversalOrder) {
    return super.descendants(traversalOrder);
  }

  @Override
  public Stream<CelNavigableExpr> children() {
    return super.children();
  }

  @Override
  public Stream<CelNavigableExpr> children(TraversalOrder traversalOrder) {
    return super.children(traversalOrder);
  }

  @Override
  public Builder builderFromInstance() {
    return builder();
  }

  /** Create a new builder to construct a {@link CelNavigableExpr} instance. */
  public static Builder builder() {
    return new AutoValue_CelNavigableExpr.Builder().setDepth(0).setHeight(0);
  }

  /** Builder to configure {@link CelNavigableExpr}. */
  @AutoValue.Builder
  public abstract static class Builder
      implements BaseNavigableExpr.Builder<CelExpr, CelNavigableExpr> {

    @Override
    public abstract Builder setParent(CelNavigableExpr value);

    @Override
    public abstract Builder setExpr(CelExpr value);

    @Override
    public abstract Builder setDepth(int value);

    @Override
    public abstract Builder setHeight(int value);
  }

  public abstract Builder toBuilder();
}
