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

package dev.cel.common.ast;

import com.google.common.base.Preconditions;
import java.util.HashMap;

/** Factory for populating expression IDs */
public final class CelExprIdGeneratorFactory {

  /**
   * MonotonicIdGenerator increments expression IDs from an initial seed value.
   *
   * @param exprId Seed value. Must be non-negative. For example, if 1 is provided {@link
   *     CelExprIdGenerator#nextExprId} will return 2.
   */
  public static CelExprIdGenerator newMonotonicIdGenerator(long exprId) {
    return new MonotonicIdGenerator(exprId);
  }

  /** StableIdGenerator ensures new IDs are only created the first time they are encountered. */
  static CelExprIdGenerator newStableIdGenerator(long exprId) {
    return new StableIdGenerator(exprId);
  }

  private static class MonotonicIdGenerator implements CelExprIdGenerator {
    private long exprId;

    @Override
    public long nextExprId() {
      return ++exprId;
    }

    private MonotonicIdGenerator(long exprId) {
      Preconditions.checkArgument(exprId >= 0);
      this.exprId = exprId;
    }
  }

  private static class StableIdGenerator implements CelExprIdGenerator {
    private final HashMap<Long, Long> idSet;
    private long exprId;

    @Override
    public long nextExprId() {
      return ++exprId;
    }

    @Override
    public long renumberId(long id) {
      Preconditions.checkArgument(id >= 0);
      if (id == 0) {
        return 0;
      }

      if (idSet.containsKey(id)) {
        return idSet.get(id);
      }

      long nextExprId = nextExprId();
      idSet.put(id, nextExprId);
      return nextExprId;
    }

    private StableIdGenerator(long exprId) {
      Preconditions.checkArgument(exprId >= 0);
      this.idSet = new HashMap<>();
      this.exprId = exprId;
    }
  }

  private CelExprIdGeneratorFactory() {}
}
