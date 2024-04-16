// Copyright 2024 Google LLC
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
import dev.cel.common.ast.CelMutableExpr;
import dev.cel.common.navigation.ExprPropertyCalculator.ExprProperty;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * CelNavigableMutableExpr decorates {@link CelMutableExpr} with capabilities to inspect the parent
 * and its descendants with ease.
 */
@AutoValue
// unchecked: Generic types are properly bound to BaseNavigableExpr
// redundant override: Overriding is required to specify the return type to a concrete type.
@SuppressWarnings({"unchecked", "RedundantOverride"})
public abstract class CelNavigableMutableExpr extends BaseNavigableExpr<CelMutableExpr> {

  /** Constructs a new instance of {@link CelNavigableMutableExpr} from {@link CelMutableExpr}. */
  public static CelNavigableMutableExpr fromExpr(CelMutableExpr expr) {
    ExprPropertyCalculator<CelMutableExpr> exprHeightCalculator =
        new ExprPropertyCalculator<>(expr);
    ExprProperty exprProperty = exprHeightCalculator.getProperty(expr.id());

    return builder()
        .setExpr(expr)
        .setHeight(exprProperty.height())
        .setMaxId(exprProperty.maxId())
        .build();
  }

  @Override
  public Stream<CelNavigableMutableExpr> allNodes() {
    return super.allNodes();
  }

  @Override
  public Stream<CelNavigableMutableExpr> allNodes(TraversalOrder traversalOrder) {
    return super.allNodes(traversalOrder);
  }

  @Override
  public abstract Optional<CelNavigableMutableExpr> parent();

  @Override
  public Stream<CelNavigableMutableExpr> descendants() {
    return super.descendants();
  }

  @Override
  public Stream<CelNavigableMutableExpr> descendants(TraversalOrder traversalOrder) {
    return super.descendants(traversalOrder);
  }

  @Override
  public Stream<CelNavigableMutableExpr> children() {
    return super.children();
  }

  @Override
  public Stream<CelNavigableMutableExpr> children(TraversalOrder traversalOrder) {
    return super.children(traversalOrder);
  }

  @Override
  public Builder builderFromInstance() {
    return builder();
  }

  /** Create a new builder to construct a {@link CelNavigableExpr} instance. */
  public static CelNavigableMutableExpr.Builder builder() {
    return new AutoValue_CelNavigableMutableExpr.Builder().setDepth(0).setHeight(0).setMaxId(0);
  }

  /** Builder to configure {@link CelNavigableExpr}. */
  @AutoValue.Builder
  public abstract static class Builder
      implements BaseNavigableExpr.Builder<CelMutableExpr, CelNavigableMutableExpr> {

    @Override
    public abstract Builder setParent(CelNavigableMutableExpr value);

    @Override
    public abstract Builder setExpr(CelMutableExpr value);

    @Override
    public abstract Builder setDepth(int value);

    @Override
    public abstract Builder setMaxId(long value);

    @Override
    public abstract Builder setHeight(int value);
  }
}
