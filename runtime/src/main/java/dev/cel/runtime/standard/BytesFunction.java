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
import dev.cel.runtime.CelFunctionBinding;
import dev.cel.runtime.RuntimeEquality;
import java.util.Arrays;

/** Standard function for {@code bytes} conversion function. */
public final class BytesFunction extends CelStandardFunction {

  public static BytesFunction create(BytesFunction.BytesOverload... overloads) {
    return create(Arrays.asList(overloads));
  }

  public static BytesFunction create(Iterable<BytesFunction.BytesOverload> overloads) {
    return new BytesFunction(ImmutableSet.copyOf(overloads));
  }

  /** Overloads for the standard function. */
  public enum BytesOverload implements CelStandardOverload {
    BYTES_TO_BYTES(
        (celOptions, runtimeEquality) ->
            CelFunctionBinding.from("bytes_to_bytes", ByteString.class, (ByteString x) -> x)),
    STRING_TO_BYTES(
        (celOptions, runtimeEquality) ->
            CelFunctionBinding.from("string_to_bytes", String.class, ByteString::copyFromUtf8)),
    ;

    private final FunctionBindingCreator bindingCreator;
    ;

    @Override
    public CelFunctionBinding newFunctionBinding(
        CelOptions celOptions, RuntimeEquality runtimeEquality) {
      return bindingCreator.create(celOptions, runtimeEquality);
    }

    BytesOverload(FunctionBindingCreator bindingCreator) {
      this.bindingCreator = bindingCreator;
    }
  }

  private BytesFunction(ImmutableSet<CelStandardOverload> overloads) {
    super(overloads);
  }
}
