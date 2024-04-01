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
import dev.cel.common.ast.CelExpr.CelComprehension;
import dev.cel.common.ast.CelExpr.ExprKind;
import dev.cel.common.ast.MutableExpr;

import java.util.Optional;
import java.util.stream.Stream;

/**
 * CelNavigableExpr represents the base navigable expression value with methods to inspect the
 * parent and child expressions.
 */
@AutoValue
public abstract class CelNavigableExpr {

  /**
   * Specifies the traversal order of AST navigation.
   *
   * <p>For call expressions, the target is visited before its arguments.
   *
   * <p>For comprehensions, the visiting order is as follows:
   *
   * <ol>
   *   <li>{@link CelComprehension#iterRange}
   *   <li>{@link CelComprehension#accuInit}
   *   <li>{@link CelComprehension#loopCondition}
   *   <li>{@link CelComprehension#loopStep}
   *   <li>{@link CelComprehension#result}
   * </ol>
   */
  public enum TraversalOrder {
    PRE_ORDER,
    POST_ORDER
  }

  public abstract CelExpr expr();
  public abstract MutableExpr mutableExpr();

  public long id() {
    // TODO: Hack. Separate CelNavigableExpr between immutable/mutable exprs via generic
    if (mutableExpr().exprKind().equals(ExprKind.Kind.NOT_SET) && mutableExpr().id() == 0L) {
      return expr().id();
    } else {
      return mutableExpr().id();
    }
  }

  public abstract Optional<CelNavigableExpr> parent();

  /** Represents the count of transitive parents. Depth of an AST's root is 0. */
  public abstract int depth();

  /**
   * Represents the maximum count of children from any of its branches. Height of a leaf node is 0.
   * For example, the height of the call node 'func' in expression `(1 + 2 + 3).func(4 + 5)` is 3.
   */
  public abstract int height();

  public abstract long maxId();

  /** Constructs a new instance of {@link CelNavigableExpr} from {@link CelExpr}. */
  public static CelNavigableExpr fromExpr(CelExpr expr) {
    // ExprHeightCalculator exprHeightCalculator = new ExprHeightCalculator(expr);

    return CelNavigableExpr.builder()
        .setExpr(expr)
        // .setHeight(exprHeightCalculator.getHeight(expr.id()))
        .build();
  }


  /** Constructs a new instance of {@link CelNavigableExpr} from {@link CelExpr}. */
  public static CelNavigableExpr fromMutableExpr(MutableExpr expr) {
    ExprHeightCalculator exprHeightCalculator = new ExprHeightCalculator(expr);

    return CelNavigableExpr.builder()
        .setMutableExpr(expr)
        .setHeight(exprHeightCalculator.getHeight(expr.id()))
        .setMaxId(exprHeightCalculator.getMaxId())
        .build();
  }


  /**
   * Returns a stream of {@link CelNavigableExpr} collected from the current node down to the last
   * leaf-level member using post-order traversal.
   */
  public Stream<CelNavigableExpr> allNodes() {
    return allNodes(TraversalOrder.POST_ORDER);
  }

  /**
   * Returns a stream of {@link CelNavigableExpr} collected from the current node down to the last
   * leaf-level member using the specified traversal order.
   */
  public Stream<CelNavigableExpr> allNodes(TraversalOrder traversalOrder) {
    return CelNavigableExprVisitor.collect(this, traversalOrder);
  }

  /**
   * Returns a stream of {@link CelNavigableExpr} collected down to the last leaf-level member using
   * post-order traversal.
   */
  public Stream<CelNavigableExpr> descendants() {
    return descendants(TraversalOrder.POST_ORDER);
  }

  /**
   * Returns a stream of {@link CelNavigableExpr} collected down to the last leaf-level member using
   * the specified traversal order.
   */
  public Stream<CelNavigableExpr> descendants(TraversalOrder traversalOrder) {
    return CelNavigableExprVisitor.collect(this, traversalOrder)
        .filter(node -> node.depth() > this.depth());
  }

  /**
   * Returns a stream of {@link CelNavigableExpr} collected from its immediate children using
   * post-order traversal.
   */
  public Stream<CelNavigableExpr> children() {
    return children(TraversalOrder.POST_ORDER);
  }

  /**
   * Returns a stream of {@link CelNavigableExpr} collected from its immediate children using the
   * specified traversal order.
   */
  public Stream<CelNavigableExpr> children(TraversalOrder traversalOrder) {
    return CelNavigableExprVisitor.collect(this, this.depth() + 1, traversalOrder)
        .filter(node -> node.depth() > this.depth());
  }

  /** Returns the underlying kind of the {@link CelExpr}. */
  public ExprKind.Kind getKind() {
    // TODO: Hack. Separate CelNavigableExpr between immutable/mutable exprs via generic
    if (mutableExpr().exprKind().equals(ExprKind.Kind.NOT_SET)) {
      return expr().exprKind().getKind();
    } else {
      return mutableExpr().exprKind();
    }
  }

  /** Create a new builder to construct a {@link CelNavigableExpr} instance. */
  public static Builder builder() {
    return new AutoValue_CelNavigableExpr.Builder()
        .setExpr(CelExpr.ofNotSet(0))
        .setMutableExpr(MutableExpr.ofNotSet()).setDepth(0).setHeight(0).setMaxId(0);
  }

  /** Builder to configure {@link CelNavigableExpr}. */
  @AutoValue.Builder
  public abstract static class Builder {

    public abstract CelExpr expr();

    public abstract int depth();

    public ExprKind.Kind getKind() {
      return expr().exprKind().getKind();
    }

    public abstract Builder setExpr(CelExpr value);
    public abstract Builder setMutableExpr(MutableExpr value);

    abstract Builder setParent(CelNavigableExpr value);

    public abstract Builder setDepth(int value);

    public abstract Builder setHeight(int value);
    public abstract Builder setMaxId(long value);

    public abstract CelNavigableExpr build();
  }

  public abstract Builder toBuilder();
}
