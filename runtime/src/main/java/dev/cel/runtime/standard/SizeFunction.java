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
import com.google.protobuf.ByteString;
import dev.cel.common.CelOptions;
import dev.cel.common.values.CelByteString;
import dev.cel.runtime.CelFunctionBinding;
import dev.cel.runtime.RuntimeEquality;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/** Standard function for {@code size}. */
public final class SizeFunction extends CelStandardFunction {
  private static final SizeFunction ALL_OVERLOADS = create(SizeOverload.values());

  public static SizeFunction create() {
    return ALL_OVERLOADS;
  }

  public static SizeFunction create(SizeFunction.SizeOverload... overloads) {
    return create(Arrays.asList(overloads));
  }

  public static SizeFunction create(Iterable<SizeFunction.SizeOverload> overloads) {
    return new SizeFunction(ImmutableSet.copyOf(overloads));
  }

  /** Overloads for the standard function. */
  @SuppressWarnings("rawtypes")
  public enum SizeOverload implements CelStandardOverload {
    SIZE_BYTES(
        (celOptions, runtimeEquality) -> {
          if (celOptions.evaluateCanonicalTypesToNativeValues()) {
            return CelFunctionBinding.from(
                "size_bytes", CelByteString.class, (CelByteString bytes) -> (long) bytes.size());
          } else {
            return CelFunctionBinding.from(
                "size_bytes", ByteString.class, (ByteString bytes) -> (long) bytes.size());
          }
        }),
    BYTES_SIZE(
        (celOptions, runtimeEquality) -> {
          if (celOptions.evaluateCanonicalTypesToNativeValues()) {
            return CelFunctionBinding.from(
                "bytes_size", CelByteString.class, (CelByteString bytes) -> (long) bytes.size());
          } else {
            return CelFunctionBinding.from(
                "bytes_size", ByteString.class, (ByteString bytes) -> (long) bytes.size());
          }
        }),
    SIZE_LIST(
        (celOptions, runtimeEquality) ->
            CelFunctionBinding.from("size_list", List.class, (List list1) -> (long) list1.size())),
    LIST_SIZE(
        (celOptions, runtimeEquality) ->
            CelFunctionBinding.from("list_size", List.class, (List list1) -> (long) list1.size())),
    SIZE_STRING(
        (celOptions, runtimeEquality) ->
            CelFunctionBinding.from(
                "size_string", String.class, (String s) -> (long) s.codePointCount(0, s.length()))),
    STRING_SIZE(
        (celOptions, runtimeEquality) ->
            CelFunctionBinding.from(
                "string_size", String.class, (String s) -> (long) s.codePointCount(0, s.length()))),
    SIZE_MAP(
        (celOptions, runtimeEquality) ->
            CelFunctionBinding.from("size_map", Map.class, (Map map1) -> (long) map1.size())),
    MAP_SIZE(
        (celOptions, runtimeEquality) ->
            CelFunctionBinding.from("map_size", Map.class, (Map map1) -> (long) map1.size()));

    private final CelStandardOverload standardOverload;

    @Override
    public CelFunctionBinding newFunctionBinding(
        CelOptions celOptions, RuntimeEquality runtimeEquality) {
      return standardOverload.newFunctionBinding(celOptions, runtimeEquality);
    }

    SizeOverload(CelStandardOverload standardOverload) {
      this.standardOverload = standardOverload;
    }
  }

  private SizeFunction(ImmutableSet<CelStandardOverload> overloads) {
    super("size", overloads);
  }
}
