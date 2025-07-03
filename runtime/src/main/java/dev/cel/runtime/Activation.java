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

import static com.google.common.collect.ImmutableMap.toImmutableMap;

import com.google.common.base.Supplier;
import com.google.common.collect.ImmutableMap;
import com.google.protobuf.ByteString;
import com.google.protobuf.ByteString.ByteIterator;
import dev.cel.common.annotations.Internal;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * An object which allows to bind names to values.
 *
 * <p>CEL Library Internals. Do Not Use.
 */
@Internal
public abstract class Activation implements GlobalResolver {

  /** Resolves the given name to its value. Returns null if resolution fails. */
  @Override
  public abstract @Nullable Object resolve(String name);

  /** An empty binder which resolves everything to null. */
  public static final Activation EMPTY =
      new Activation() {

        @Override
        public @Nullable Object resolve(String name) {
          return GlobalResolver.EMPTY.resolve(name);
        }

        @Override
        public String toString() {
          return GlobalResolver.EMPTY.toString();
        }
      };

  /** Creates a binder which binds the given name to the value. */
  public static Activation of(final String name, Object value) {
    return new Activation() {

      @Override
      public @Nullable Object resolve(String theName) {
        if (theName.equals(name)) {
          // TODO: Decouple
          return RuntimeHelpers.maybeAdaptPrimitive(value);
        }
        return null;
      }

      @Override
      public String toString() {
        // TODO: Remove.
        if (value instanceof ByteString) {
          ByteString bs = (ByteString) value;
          StringBuilder val = new StringBuilder();
          val.append("[");
          for (ByteIterator i = bs.iterator(); i.hasNext(); ) {
            byte b = i.nextByte();
            val.append(b);
            if (i.hasNext()) {
              val.append(", ");
            }
          }
          val.append("]");
          return String.format("{%s=%s}", name, val);
        }
        return String.format("{%s=%s}", name, value);
      }
    };
  }

  /** Creates a binder which binds the given name to the supplier. */
  public static Activation of(final String name, final Supplier<?> supplier) {
    return new Activation() {

      @Override
      public @Nullable Object resolve(String theName) {
        if (theName.equals(name)) {
          return RuntimeHelpers.maybeAdaptPrimitive(supplier.get());
        }
        return null;
      }

      @Override
      public String toString() {
        return String.format("{%s=%s}", name, supplier.get());
      }
    };
  }

  /** Creates a binder backed up by a map. */
  public static Activation copyOf(Map<String, ?> map) {
    @SuppressWarnings("unchecked")
    final ImmutableMap<String, Object> copy =
        (map instanceof ImmutableMap)
            ? (ImmutableMap<String, Object>) map
            : map.entrySet().stream()
                // ImmutableMaps are null-hostile, but the Activation is not, so make sure that null
                // values and entries are skipped.
                .filter(entry -> entry.getKey() != null && entry.getValue() != null)
                .collect(toImmutableMap(Map.Entry::getKey, Map.Entry::getValue));
    return new Activation() {

      @Override
      public @Nullable Object resolve(String name) {
        return RuntimeHelpers.maybeAdaptPrimitive(copy.get(name));
      }

      @Override
      public String toString() {
        return copy.toString();
      }
    };
  }

  /**
   * Extends this binder by another binder. Names will be attempted to first resolve in the other
   * binder, then in this binder.
   */
  public Activation extend(final Activation activation) {
    final Activation outer = this;
    return new Activation() {

      @Override
      public @Nullable Object resolve(String name) {
        Object value = activation.resolve(name);
        return value != null ? value : outer.resolve(name);
      }

      @Override
      public String toString() {
        return activation + " +> " + outer;
      }
    };
  }
}
