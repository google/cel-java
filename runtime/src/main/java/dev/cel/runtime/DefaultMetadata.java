// Copyright 2022 Google LLC
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

package dev.cel.runtime;

import com.google.api.expr.v1alpha1.CheckedExpr;
import com.google.common.base.Preconditions;
import com.google.errorprone.annotations.Immutable;
import dev.cel.common.annotations.Internal;
import java.util.Map;

/**
 * Metadata implementation based on {@link CheckedExpr}.
 *
 * <p>CEL Library Internals. Do Not Use.
 */
@Immutable
@Internal
public final class DefaultMetadata implements Metadata {

  private final CheckedExpr checkedExpr;

  public DefaultMetadata(CheckedExpr checkedExpr) {
    this.checkedExpr = Preconditions.checkNotNull(checkedExpr);
  }

  @Override
  public String getLocation() {
    return checkedExpr.getSourceInfo().getLocation();
  }

  @Override
  public int getPosition(long exprId) {
    Map<Long, Integer> positions = checkedExpr.getSourceInfo().getPositionsMap();
    return positions.containsKey(exprId) ? positions.get(exprId) : 0;
  }
}
