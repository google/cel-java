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

package dev.cel.runtime.planner;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;
import com.google.common.primitives.UnsignedLong;
import com.google.errorprone.annotations.Immutable;
import dev.cel.common.exceptions.CelDuplicateKeyException;
import dev.cel.common.exceptions.CelInvalidArgumentException;
import dev.cel.runtime.AccumulatedUnknowns;
import dev.cel.runtime.CelEvaluationException;
import dev.cel.runtime.GlobalResolver;
import java.util.HashSet;
import java.util.Optional;

@Immutable
final class EvalCreateMap extends PlannedInterpretable {

  // Array contents are not mutated
  @SuppressWarnings("Immutable")
  private final PlannedInterpretable[] keys;

  // Array contents are not mutated
  @SuppressWarnings("Immutable")
  private final PlannedInterpretable[] values;

  // Array contents are not mutated
  @SuppressWarnings("Immutable")
  private final boolean[] isOptional;

  @Override
  public Object eval(GlobalResolver resolver, ExecutionFrame frame) throws CelEvaluationException {
    ImmutableMap.Builder<Object, Object> builder =
        ImmutableMap.builderWithExpectedSize(keys.length);
    HashSet<Object> keysSeen = Sets.newHashSetWithExpectedSize(keys.length);
    AccumulatedUnknowns unknowns = null;

    for (int i = 0; i < keys.length; i++) {
      PlannedInterpretable keyInterpretable = keys[i];
      Object key = keyInterpretable.eval(resolver, frame);

      if (key instanceof AccumulatedUnknowns) {
        unknowns = AccumulatedUnknowns.maybeMerge(unknowns, key);
      } else {
        if (!(key instanceof String
            || key instanceof Long
            || key instanceof UnsignedLong
            || key instanceof Boolean)) {
          throw new LocalizedEvaluationException(
              new CelInvalidArgumentException("Unsupported key type: " + key),
              keyInterpretable.exprId());
        }

        boolean isDuplicate = !keysSeen.add(key);
        if (!isDuplicate) {
          if (key instanceof Long) {
            long longVal = (Long) key;
            if (longVal >= 0) {
              isDuplicate = keysSeen.contains(UnsignedLong.valueOf(longVal));
            }
          } else if (key instanceof UnsignedLong) {
            UnsignedLong ulongVal = (UnsignedLong) key;
            isDuplicate = keysSeen.contains(ulongVal.longValue());
          }
        }

        if (isDuplicate) {
          throw new LocalizedEvaluationException(
              CelDuplicateKeyException.of(key), keyInterpretable.exprId());
        }
      }

      Object val = EvalHelpers.evalStrictly(values[i], resolver, frame);

      unknowns = AccumulatedUnknowns.maybeMerge(unknowns, val);
      if (unknowns != null) {
        continue;
      }

      if (isOptional[i]) {
        if (!(val instanceof Optional)) {
          throw new IllegalArgumentException(
              String.format(
                  "Cannot initialize optional entry '%s' from non-optional value %s", key, val));
        }

        Optional<?> opt = (Optional<?>) val;
        if (!opt.isPresent()) {
          // This is a no-op currently but will be semantically correct when extended proto
          // support allows proto mutation.
          keysSeen.remove(key);
          continue;
        }
        val = opt.get();
      }

      builder.put(key, val);
    }

    if (unknowns != null) {
      return unknowns;
    }

    return builder.buildOrThrow();
  }

  static EvalCreateMap create(
      long exprId,
      PlannedInterpretable[] keys,
      PlannedInterpretable[] values,
      boolean[] isOptional) {
    return new EvalCreateMap(exprId, keys, values, isOptional);
  }

  private EvalCreateMap(
      long exprId,
      PlannedInterpretable[] keys,
      PlannedInterpretable[] values,
      boolean[] isOptional) {
    super(exprId);
    Preconditions.checkArgument(keys.length == values.length);
    Preconditions.checkArgument(keys.length == isOptional.length);
    this.keys = keys;
    this.values = values;
    this.isOptional = isOptional;
  }
}
