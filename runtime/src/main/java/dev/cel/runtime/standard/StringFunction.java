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

package dev.cel.runtime.standard;

import com.google.common.collect.ImmutableSet;
import com.google.common.primitives.UnsignedLong;
import com.google.common.primitives.UnsignedLongs;
import com.google.protobuf.ByteString;
import com.google.protobuf.Duration;
import com.google.protobuf.Timestamp;
import dev.cel.common.CelErrorCode;
import dev.cel.common.CelOptions;
import dev.cel.common.CelRuntimeException;
import dev.cel.common.internal.ProtoTimeUtils;
import dev.cel.common.values.CelByteString;
import dev.cel.runtime.CelFunctionBinding;
import dev.cel.runtime.RuntimeEquality;
import java.util.Arrays;

/** Standard function for {@code string} conversion function. */
public final class StringFunction extends CelStandardFunction {
  private static final StringFunction ALL_OVERLOADS = create(StringOverload.values());

  public static StringFunction create() {
    return ALL_OVERLOADS;
  }

  public static StringFunction create(StringFunction.StringOverload... overloads) {
    return create(Arrays.asList(overloads));
  }

  public static StringFunction create(Iterable<StringFunction.StringOverload> overloads) {
    return new StringFunction(ImmutableSet.copyOf(overloads));
  }

  /** Overloads for the standard function. */
  public enum StringOverload implements CelStandardOverload {
    STRING_TO_STRING(
        (celOptions, runtimeEquality) ->
            CelFunctionBinding.from("string_to_string", String.class, (String x) -> x)),
    INT64_TO_STRING(
        (celOptions, runtimeEquality) ->
            CelFunctionBinding.from("int64_to_string", Long.class, Object::toString)),
    DOUBLE_TO_STRING(
        (celOptions, runtimeEquality) ->
            CelFunctionBinding.from("double_to_string", Double.class, Object::toString)),
    BOOL_TO_STRING(
        (celOptions, runtimeEquality) ->
            CelFunctionBinding.from("bool_to_string", Boolean.class, Object::toString)),
    BYTES_TO_STRING(
        (celOptions, runtimeEquality) -> {
          if (celOptions.evaluateCanonicalTypesToNativeValues()) {
            return CelFunctionBinding.from(
                "bytes_to_string",
                CelByteString.class,
                (byteStr) -> {
                  if (!byteStr.isValidUtf8()) {
                    throw new CelRuntimeException(
                        new IllegalArgumentException(
                            "invalid UTF-8 in bytes, cannot convert to string"),
                        CelErrorCode.BAD_FORMAT);
                  }
                  return byteStr.toStringUtf8();
                });
          } else {
            return CelFunctionBinding.from(
                "bytes_to_string",
                ByteString.class,
                (byteStr) -> {
                  if (!byteStr.isValidUtf8()) {
                    throw new CelRuntimeException(
                        new IllegalArgumentException(
                            "invalid UTF-8 in bytes, cannot convert to string"),
                        CelErrorCode.BAD_FORMAT);
                  }
                  return byteStr.toStringUtf8();
                });
          }
        }),
    TIMESTAMP_TO_STRING(
        (celOptions, runtimeEquality) ->
            CelFunctionBinding.from(
                "timestamp_to_string", Timestamp.class, ProtoTimeUtils::toString)),
    DURATION_TO_STRING(
        (celOptions, runtimeEquality) ->
            CelFunctionBinding.from(
                "duration_to_string", Duration.class, ProtoTimeUtils::toString)),
    UINT64_TO_STRING(
        (celOptions, runtimeEquality) -> {
          if (celOptions.enableUnsignedLongs()) {
            return CelFunctionBinding.from(
                "uint64_to_string", UnsignedLong.class, UnsignedLong::toString);
          } else {
            return CelFunctionBinding.from("uint64_to_string", Long.class, UnsignedLongs::toString);
          }
        });

    private final FunctionBindingCreator bindingCreator;

    @Override
    public CelFunctionBinding newFunctionBinding(
        CelOptions celOptions, RuntimeEquality runtimeEquality) {
      return bindingCreator.create(celOptions, runtimeEquality);
    }

    StringOverload(FunctionBindingCreator bindingCreator) {
      this.bindingCreator = bindingCreator;
    }
  }

  private StringFunction(ImmutableSet<CelStandardOverload> overloads) {
    super(overloads);
  }
}
