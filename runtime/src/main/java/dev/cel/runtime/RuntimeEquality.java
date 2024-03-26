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

import com.google.common.primitives.UnsignedLong;
import com.google.errorprone.annotations.Immutable;
import com.google.protobuf.Message;
import com.google.protobuf.MessageOrBuilder;
import dev.cel.common.CelErrorCode;
import dev.cel.common.CelOptions;
import dev.cel.common.CelRuntimeException;
import dev.cel.common.annotations.Internal;
import dev.cel.common.internal.ComparisonFunctions;
import dev.cel.common.internal.DynamicProto;
import dev.cel.common.internal.ProtoEquality;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** CEL Library Internals. Do Not Use. */
@Internal
@Immutable
public final class RuntimeEquality {

  private final DynamicProto dynamicProto;
  private final ProtoEquality protoEquality;

  public RuntimeEquality(DynamicProto dynamicProto) {
    this.dynamicProto = dynamicProto;
    this.protoEquality = new ProtoEquality(dynamicProto);
  }

  // Functions
  // =========

  /** Determine whether the {@code list} contains the given {@code value}. */
  public <A> boolean inList(List<A> list, A value, CelOptions celOptions) {
    if (list.contains(value)) {
      return true;
    }
    if (value instanceof Number) {
      for (A elem : list) {
        if (objectEquals(elem, value, celOptions)) {
          return true;
        }
      }
    }
    return false;
  }

  /** Bound-checked indexing of maps. */
  @SuppressWarnings("unchecked")
  public <A, B> B indexMap(Map<A, B> map, A index, CelOptions celOptions) {
    Optional<Object> value = findInMap(map, index, celOptions);
    // Use this method rather than the standard 'orElseThrow' method because of the unchecked cast.
    if (value.isPresent()) {
      return (B) value.get();
    }
    throw new CelRuntimeException(
        new IndexOutOfBoundsException(index.toString()), CelErrorCode.ATTRIBUTE_NOT_FOUND);
  }

  /** Determine whether the {@code map} contains the given {@code key}. */
  public <A, B> boolean inMap(Map<A, B> map, A key, CelOptions celOptions) {
    return findInMap(map, key, celOptions).isPresent();
  }

  public Optional<Object> findInMap(Map<?, ?> map, Object index, CelOptions celOptions) {
    if (celOptions.disableCelStandardEquality()) {
      return Optional.ofNullable(map.get(index));
    }

    if (index instanceof MessageOrBuilder) {
      index = RuntimeHelpers.adaptProtoToValue(dynamicProto, (MessageOrBuilder) index, celOptions);
    }
    Object v = map.get(index);
    if (v != null) {
      return Optional.of(v);
    }
    if (index instanceof Long) {
      return longToUnsignedLossless((Long) index).map(map::get);
    } else if (index instanceof UnsignedLong) {
      return unsignedToLongLossless((UnsignedLong) index).map(map::get);
    } else if (index instanceof Number) {
      Number numberIndex = (Number) index;
      Optional<Object> indexValue = RuntimeHelpers.doubleToLongLossless(numberIndex).map(map::get);
      // Early return rather than use 'or()' since or is not JDK 1.8 compatible.
      if (indexValue.isPresent()) {
        return indexValue;
      }
      return doubleToUnsignedLossless(numberIndex).map(map::get);
    }
    return Optional.empty();
  }

  // Object equality
  // ===================

  /**
   * CEL implements homogeneous equality where two values are only comparable if they have the same
   * type, otherwise an error is thrown.
   *
   * <p>If the values are of different type, the comparison fails with an error. For aggregate
   * types, equality
   *
   * <p>If lists are of different length, return false, otherwise take the CEL logical AND of
   * homogeneous equality of the elements by list position:
   *
   * <ul>
   *   <li>If any element comparison returns false, the list comparison returns false.
   *   <li>Otherwise, if all element comparisons return true, the list comparison returns true.
   *   <li>Otherwise, there are one or more errors, one of which is re-thrown.
   * </ul>
   *
   * <p>If maps have different key sets, return false, otherwise take the CEL logical AND of
   * homogeneous equality of the values by map key:
   *
   * <ul>
   *   <li>If any value comparison returns false, the map comparison returns false.
   *   <li>Otherwise, if all value comparisons return true, the map comparison returns true.
   *   <li>Otherwise, there are one or more errors, one of which is re-thrown.
   * </ul>
   *
   * <p>Heterogeneous equality differs from homogeneous equality in that two objects may be
   * comparable even if they are not of the same type, where type differences are usually trivially
   * false. Heterogeneous runtime equality is under consideration in b/71516544.
   *
   * <p>Note, uint values are problematic in that they cannot be properly type-tested for equality
   * in comparisons with 64-int signed integer values, see b/159183198. This problem only affects
   * Java and is typically inconsequential due to the requirement for type-checking expressions
   * before they are evaluated.
   */
  @SuppressWarnings({"rawtypes", "unchecked"})
  public boolean objectEquals(Object x, Object y, CelOptions celOptions) {
    if (celOptions.disableCelStandardEquality()) {
      return Objects.equals(x, y);
    }
    if (x == y) {
      return true;
    }
    x = RuntimeHelpers.adaptValue(dynamicProto, x, celOptions);
    y = RuntimeHelpers.adaptValue(dynamicProto, y, celOptions);
    if (x instanceof Number) {
      if (!(y instanceof Number)) {
        return false;
      }
      return ComparisonFunctions.numericEquals((Number) x, (Number) y);
    }
    if (celOptions.enableProtoDifferencerEquality()) {
      if (x instanceof Message) {
        if (!(y instanceof Message)) {
          return false;
        }
        return protoEquality.equals((Message) x, (Message) y);
      }
    }
    if (x instanceof Iterable) {
      if (!(y instanceof Iterable)) {
        return false;
      }
      Iterable<?> xIter = (Iterable<?>) x;
      Iterable<?> yIter = (Iterable<?>) y;
      Iterator<?> yElems = yIter.iterator();
      IllegalArgumentException e = null;
      for (Object xElem : xIter) {
        if (!yElems.hasNext()) {
          return false;
        }
        try {
          if (!objectEquals(xElem, yElems.next(), celOptions)) {
            return false;
          }
        } catch (IllegalArgumentException iae) {
          e = iae;
        }
      }
      if (yElems.hasNext()) {
        return false;
      }
      if (e != null) {
        throw e;
      }
      return true;
    }
    if (x instanceof Map) {
      if (!(y instanceof Map)) {
        return false;
      }
      Map xMap = (Map) x;
      Map yMap = (Map) y;
      if (xMap.size() != yMap.size()) {
        return false;
      }
      IllegalArgumentException e = null;
      Set<Map.Entry> entrySet = xMap.entrySet();
      for (Map.Entry xEntry : entrySet) {
        Optional<Object> yVal = findInMap(yMap, xEntry.getKey(), celOptions);
        // Use isPresent() rather than isEmpty() to stay backwards compatible with Java 8.
        if (!yVal.isPresent()) {
          return false;
        }
        try {
          if (!objectEquals(xEntry.getValue(), yVal.get(), celOptions)) {
            return false;
          }
        } catch (IllegalArgumentException iae) {
          e = iae;
        }
      }
      if (e != null) {
        throw e;
      }
      return true;
    }
    return Objects.equals(x, y);
  }

  private static Optional<UnsignedLong> doubleToUnsignedLossless(Number v) {
    Optional<UnsignedLong> conv = RuntimeHelpers.doubleToUnsignedChecked(v.doubleValue());
    return conv.map(ul -> ul.longValue() == v.doubleValue() ? ul : null);
  }

  private static Optional<UnsignedLong> longToUnsignedLossless(long v) {
    if (v >= 0) {
      return Optional.of(UnsignedLong.valueOf(v));
    }
    return Optional.empty();
  }

  private static Optional<Long> unsignedToLongLossless(UnsignedLong v) {
    if (v.compareTo(UnsignedLong.valueOf(Long.MAX_VALUE)) <= 0) {
      return Optional.of(v.longValue());
    }
    return Optional.empty();
  }
}
