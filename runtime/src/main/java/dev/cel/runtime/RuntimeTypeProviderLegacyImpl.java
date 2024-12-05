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

package dev.cel.runtime;

import com.google.common.annotations.VisibleForTesting;
import com.google.errorprone.annotations.Immutable;
import dev.cel.common.CelErrorCode;
import dev.cel.common.CelOptions;
import dev.cel.common.CelRuntimeException;
import dev.cel.common.annotations.Internal;
import dev.cel.common.internal.CelDescriptorPool;
import dev.cel.common.internal.DynamicProto;
import dev.cel.common.values.CelValue;
import dev.cel.common.values.CelValueProvider;
import dev.cel.common.values.ProtoCelValueConverter;
import dev.cel.common.values.SelectableValue;
import dev.cel.common.values.StringValue;
import java.util.Map;
import java.util.NoSuchElementException;

/** Bridge between the old RuntimeTypeProvider and CelValueProvider APIs. */
@Internal
@Immutable
public final class RuntimeTypeProviderLegacyImpl implements RuntimeTypeProvider {

  private final CelValueProvider valueProvider;
  private final ProtoCelValueConverter protoCelValueConverter;

  @VisibleForTesting
  public RuntimeTypeProviderLegacyImpl(
      CelOptions celOptions,
      CelValueProvider valueProvider,
      CelDescriptorPool celDescriptorPool,
      DynamicProto dynamicProto) {
    this.valueProvider = valueProvider;
    this.protoCelValueConverter =
        ProtoCelValueConverter.newInstance(celOptions, celDescriptorPool, dynamicProto);
  }

  @Override
  public Object createMessage(String messageName, Map<String, Object> values) {
    return unwrapCelValue(
        valueProvider
            .newValue(messageName, values)
            .orElseThrow(
                () ->
                    new NoSuchElementException(
                        "Could not generate a new value for message name: " + messageName)));
  }

  @Override
  @SuppressWarnings("unchecked")
  public Object selectField(Object message, String fieldName) {
    CelValue convertedCelValue = protoCelValueConverter.fromJavaObjectToCelValue(message);
    if (!(convertedCelValue instanceof SelectableValue)) {
      throw new CelRuntimeException(
          new IllegalArgumentException(
              String.format(
                  "Error resolving field '%s'. Field selections must be performed on messages or"
                      + " maps.",
                  fieldName)),
          CelErrorCode.ATTRIBUTE_NOT_FOUND);
    }

    SelectableValue<CelValue> selectableValue = (SelectableValue<CelValue>) convertedCelValue;

    return unwrapCelValue(selectableValue.select(StringValue.create(fieldName)));
  }

  @Override
  @SuppressWarnings("unchecked")
  public Object hasField(Object message, String fieldName) {
    CelValue convertedCelValue = protoCelValueConverter.fromJavaObjectToCelValue(message);
    if (!(convertedCelValue instanceof SelectableValue)) {
      throw new CelRuntimeException(
          new IllegalArgumentException(
              String.format(
                  "Error resolving field '%s'. Field selections must be performed on messages or"
                      + " maps.",
                  fieldName)),
          CelErrorCode.ATTRIBUTE_NOT_FOUND);
    }

    SelectableValue<CelValue> selectableValue = (SelectableValue<CelValue>) convertedCelValue;

    return selectableValue.find(StringValue.create(fieldName)).isPresent();
  }

  @Override
  public Object adapt(Object message) {
    if (message instanceof CelUnknownSet) {
      return message; // CelUnknownSet is handled specially for iterative evaluation. No need to
      // adapt to CelValue.
    }
    return unwrapCelValue(protoCelValueConverter.fromJavaObjectToCelValue(message));
  }

  /**
   * DefaultInterpreter cannot handle CelValue and instead expects plain Java objects.
   *
   * <p>This will become unnecessary once we introduce a rewrite of a Cel runtime.
   */
  private Object unwrapCelValue(CelValue object) {
    return protoCelValueConverter.fromCelValueToJavaObject(object);
  }
}
