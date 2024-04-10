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

package dev.cel.common.ast;

import static com.google.common.base.Preconditions.checkArgument;

import dev.cel.common.ast.CelExpr.CelNotSet;
import dev.cel.common.ast.CelExpr.ExprKind;
import dev.cel.common.ast.CelExpr.ExprKind.Kind;

/**
 * An abstract representation of a common expression that allows mutation in any of its properties.
 * The expressions are semantically the same as that of the immutable {@link CelExpr}.
 *
 * <p>This allows for an efficient optimization of an AST without having to traverse and rebuild the
 * entire tree.
 *
 * <p>This class is not thread-safe by design.
 */
public final class CelMutableExpr {
  private long id;
  private ExprKind.Kind exprKind;
  private CelNotSet notSet;
  private CelConstant constant;
  private int hash = 0;

  public long id() {
    return id;
  }

  public void setId(long id) {
    this.id = id;
  }

  public ExprKind.Kind getKind() {
    return exprKind;
  }

  public CelNotSet notSet() {
    checkExprKind(Kind.NOT_SET);
    return notSet;
  }

  public CelConstant constant() {
    checkExprKind(Kind.CONSTANT);
    return constant;
  }

  public void setConstant(CelConstant constant) {
    this.exprKind = ExprKind.Kind.CONSTANT;
    this.constant = constant;
  }

  public static CelMutableExpr ofConstant(CelConstant constant) {
    return ofConstant(0L, constant);
  }

  public static CelMutableExpr ofConstant(long id, CelConstant constant) {
    return new CelMutableExpr(id, constant);
  }

  public static CelMutableExpr ofNotSet() {
    return ofNotSet(0L);
  }

  public static CelMutableExpr ofNotSet(long id) {
    return new CelMutableExpr(id);
  }

  private CelMutableExpr(long id, CelConstant mutableConstant) {
    this.id = id;
    setConstant(mutableConstant);
  }

  private CelMutableExpr(long id) {
    this();
    this.id = id;
  }

  private CelMutableExpr() {
    this.notSet = CelExpr.newBuilder().build().exprKind().notSet();
    this.exprKind = ExprKind.Kind.NOT_SET;
  }

  private Object exprValue() {
    switch (this.exprKind) {
      case NOT_SET:
        return notSet();
      case CONSTANT:
        return constant();
      case IDENT:
      case SELECT:
      case CALL:
      case CREATE_LIST:
      case CREATE_STRUCT:
      case CREATE_MAP:
      case COMPREHENSION:
        // fall-through (not implemented yet)
    }

    throw new IllegalStateException("Unexpected expr kind: " + this.exprKind);
  }

  private void checkExprKind(ExprKind.Kind exprKind) {
    checkArgument(this.exprKind.equals(exprKind), "Invalid ExprKind: %s", exprKind);
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == this) {
      return true;
    }
    if (obj instanceof CelMutableExpr) {
      CelMutableExpr that = (CelMutableExpr) obj;
      if (this.id != that.id() || !this.exprKind.equals(that.getKind())) {
        return false;
      }
      // When both objects' hashes are cached and they do not match, they can never be equal.
      if (this.hash != 0 && that.hash != 0 && this.hash != that.hash) {
        return false;
      }
      return this.exprValue().equals(that.exprValue());
    }

    return false;
  }

  @Override
  public int hashCode() {
    if (hash == 0) {
      int h = 1;
      h *= 1000003;
      h ^= (int) ((id >>> 32) ^ id);
      h *= 1000003;
      h ^= this.exprValue().hashCode();

      if (h == 0) {
        h = 1;
      }
      hash = h;
    }

    return hash;
  }
}
