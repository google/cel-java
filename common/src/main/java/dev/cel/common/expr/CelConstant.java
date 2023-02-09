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

package dev.cel.common.expr;

import com.google.auto.value.AutoOneOf;
import com.google.errorprone.annotations.Immutable;
import com.google.protobuf.ByteString;
import com.google.protobuf.NullValue;
import dev.cel.common.annotations.Internal;

/**
 * Represents a primitive literal.
 *
 * <p>This is the CEL-Java native type equivalent of Constant message type from syntax.proto.
 */
@AutoOneOf(CelConstant.Kind.class)
@Internal
@Immutable
public abstract class CelConstant {
  /** Represents the type of the Constant */
  public enum Kind {
    NULL_VALUE,
    BOOLEAN_VALUE,
    INT64_VALUE,
    UINT64_VALUE,
    DOUBLE_VALUE,
    STRING_VALUE,
    BYTES_VALUE,
  }

  public abstract Kind getKind();

  public abstract NullValue nullValue();

  public abstract boolean booleanValue();

  public abstract long int64Value();

  public abstract long uint64Value();

  public abstract double doubleValue();

  public abstract String stringValue();

  public abstract ByteString bytesValue();

  public static CelConstant ofNullValue(NullValue value) {
    return AutoOneOf_CelConstant.nullValue(value);
  }

  public static CelConstant ofBooleanValue(boolean value) {
    return AutoOneOf_CelConstant.booleanValue(value);
  }

  public static CelConstant ofInt64Value(long value) {
    return AutoOneOf_CelConstant.int64Value(value);
  }

  public static CelConstant ofUInt64Value(long value) {
    return AutoOneOf_CelConstant.uint64Value(value);
  }

  public static CelConstant ofDoubleValue(double value) {
    return AutoOneOf_CelConstant.doubleValue(value);
  }

  public static CelConstant ofStringValue(String value) {
    return AutoOneOf_CelConstant.stringValue(value);
  }

  public static CelConstant ofBytesValue(ByteString value) {
    return AutoOneOf_CelConstant.bytesValue(value);
  }
}
