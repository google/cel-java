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
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.Message;
import dev.cel.common.CelOptions;
import dev.cel.common.ExprFeatures;
import dev.cel.common.annotations.Internal;
import dev.cel.common.internal.DynamicProto;
import dev.cel.common.internal.ProtoAdapter;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.jspecify.nullness.Nullable;

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
          return null;
        }

        @Override
        public String toString() {
          return "{}";
        }
      };

  /** Creates a binder which binds the given name to the value. */
  public static Activation of(final String name, Object value) {
    return new Activation() {

      @Override
      public @Nullable Object resolve(String theName) {
        if (theName.equals(name)) {
          return RuntimeHelpers.maybeAdaptPrimitive(value);
        }
        return null;
      }

      @Override
      public String toString() {
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
   * Creates an {@code Activation} from a {@code Message} where each field in the message is exposed
   * as a top-level variable in the {@code Activation}.
   *
   * <p>Unset message fields are published with the default value for the field type. However, an
   * unset {@code google.protobuf.Any} value is not a valid CEL value, and will be published as an
   * {@code Exception} value on the {@code Activation} just as though an unset {@code Any} would if
   * it were accessed during a CEL evaluation.
   *
   * <p>Note, this call does not support unsigned integer fields properly and encodes them as long
   * values. If {@link ExprFeatures#ENABLE_UNSIGNED_LONGS} is in use, use {@link #fromProto(Message,
   * CelOptions)} to ensure that the message fields are properly designated as {@code UnsignedLong}
   * values.
   */
  public static Activation fromProto(Message message) {
    return fromProto(message, CelOptions.LEGACY);
  }

  /**
   * Creates an {@code Activation} from a {@code Message} where each field in the message is exposed
   * as a top-level variable in the {@code Activation}.
   *
   * <p>Unset message fields are published with the default value for the field type. However, an
   * unset {@code google.protobuf.Any} value is not a valid CEL value, and will be published as an
   * {@code Exception} value on the {@code Activation} just as though an unset {@code Any} would if
   * it were accessed during a CEL evaluation.
   */
  public static Activation fromProto(Message message, CelOptions celOptions) {
    Map<String, Object> variables = new HashMap<>();
    Map<FieldDescriptor, Object> msgFieldValues = message.getAllFields();
    ProtoAdapter protoAdapter =
        new ProtoAdapter(DynamicProto.newBuilder().build(), celOptions.enableUnsignedLongs());

    for (FieldDescriptor field : message.getDescriptorForType().getFields()) {
      // Get the value of the field set on the message, if present, otherwise use reflection to
      // get the default value for the field using the FieldDescriptor.
      Object fieldValue = msgFieldValues.getOrDefault(field, message.getField(field));
      try {
        Optional<Object> adapted = protoAdapter.adaptFieldToValue(field, fieldValue);
        variables.put(field.getName(), adapted.orElse(null));
      } catch (IllegalArgumentException e) {
        variables.put(
            field.getName(),
            new InterpreterException.Builder(
                    "illegal field value. field=%s, value=%s", field.getName(), fieldValue)
                .setCause(e)
                .build());
      }
    }
    return copyOf(variables);
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
