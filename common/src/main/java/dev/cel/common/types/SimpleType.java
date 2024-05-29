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

package dev.cel.common.types;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableMap;
import com.google.errorprone.annotations.CheckReturnValue;
import com.google.errorprone.annotations.Immutable;

import java.util.Optional;

/**
 * Simple types represent scalar, dynamic, and error values.
 */
@AutoValue
@CheckReturnValue
@Immutable
public abstract class SimpleType extends CelType {

  // Internal error type.
  public static final CelType ERROR = create(CelKind.ERROR, "*error*");

  // Dynamic 'top' types.
  public static final CelType DYN = create(CelKind.DYN, "dyn");
  public static final CelType ANY = create(CelKind.ANY, "google.protobuf.Any");

  // Scalar types.
  public static final CelType BOOL = create(CelKind.BOOL, "bool");
  public static final CelType BYTES = create(CelKind.BYTES, "bytes");
  public static final CelType DOUBLE = create(CelKind.DOUBLE, "double");
  public static final CelType DURATION = create(CelKind.DURATION, "google.protobuf.Duration");
  public static final CelType INT = create(CelKind.INT, "int");
  public static final CelType NULL_TYPE = create(CelKind.NULL_TYPE, "null_type");
  public static final CelType STRING = create(CelKind.STRING, "string");
  public static final CelType TIMESTAMP = create(CelKind.TIMESTAMP, "google.protobuf.Timestamp");
  public static final CelType UINT = create(CelKind.UINT, "uint");

  private static final ImmutableMap<String, CelType> TYPE_MAP = ImmutableMap.of(
          DYN.name(), DYN,
          BOOL.name(), BOOL,
          BYTES.name(), BYTES,
          DOUBLE.name(), DOUBLE,
          DURATION.name(), DURATION,
          INT.name(), INT,
          NULL_TYPE.name(), NULL_TYPE,
          STRING.name(), STRING,
          TIMESTAMP.name(), TIMESTAMP,
          UINT.name(), UINT
  );

  @Override
  public abstract CelKind kind();

  @Override
  public abstract String name();

  @Override
  public boolean isAssignableFrom(CelType other) {
    return kind().isDyn()
        || super.isAssignableFrom(other)
        || (kind().equals(CelKind.INT) && other instanceof EnumType)
        || (other instanceof NullableType && other.isAssignableFrom(this));
  }

  public static Optional<CelType> findByName(String typeName) {
    if (!TYPE_MAP.containsKey(typeName)) {
      return Optional.empty();
    }
    return Optional.of(TYPE_MAP.get(typeName));
  }

  private static CelType create(CelKind kind, String name) {
    return new AutoValue_SimpleType(kind, name);
  }
}
