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

import com.google.errorprone.annotations.Immutable;
import com.google.protobuf.MessageLite;
import dev.cel.common.CelErrorCode;
import dev.cel.common.CelRuntimeException;
import dev.cel.common.annotations.Internal;
import dev.cel.common.values.BaseProtoCelValueConverter;
import dev.cel.common.values.CelValue;
import dev.cel.common.values.CelValueProvider;
import dev.cel.common.values.SelectableValue;
import dev.cel.common.values.StringValue;
import java.util.Map;
import java.util.NoSuchElementException;

/** Bridge between the old RuntimeTypeProvider and CelValueProvider APIs. */
@Internal
@Immutable
final class CelValueRuntimeTypeProvider implements RuntimeTypeProvider {

  private final CelValueProvider valueProvider;
  private final BaseProtoCelValueConverter protoCelValueConverter;

  static CelValueRuntimeTypeProvider newInstance(
      CelValueProvider valueProvider, BaseProtoCelValueConverter protoCelValueConverter) {
    return new CelValueRuntimeTypeProvider(valueProvider, protoCelValueConverter);
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
  public Object selectField(Object message, String fieldName) {
    SelectableValue<CelValue> selectableValue = getSelectableValueOrThrow(message, fieldName);

    return unwrapCelValue(selectableValue.select(StringValue.create(fieldName)));
  }

  @Override
  public Object hasField(Object message, String fieldName) {
    SelectableValue<CelValue> selectableValue = getSelectableValueOrThrow(message, fieldName);

    return selectableValue.find(StringValue.create(fieldName)).isPresent();
  }

  @SuppressWarnings("unchecked")
  private SelectableValue<CelValue> getSelectableValueOrThrow(Object obj, String fieldName) {
    CelValue convertedCelValue;
    if ((obj instanceof MessageLite)) {
      // TODO: Pass in typeName for lite messages
      convertedCelValue = protoCelValueConverter.fromProtoMessageToCelValue("", (MessageLite) obj);
    } else {
      convertedCelValue = protoCelValueConverter.fromJavaObjectToCelValue(obj);
    }

    if (!(convertedCelValue instanceof SelectableValue)) {
      throwInvalidFieldSelection(fieldName);
    }

    return (SelectableValue<CelValue>) convertedCelValue;
  }

  @Override
  public Object adapt(Object message) {
    if (message instanceof CelUnknownSet) {
      return message; // CelUnknownSet is handled specially for iterative evaluation. No need to
      // adapt to CelValue.
    }

    if (message instanceof MessageLite.Builder) {
      message = ((MessageLite.Builder) message).build();
    }

    if (message instanceof MessageLite) {
      // TODO: Pass in typeName for lite messages
      return unwrapCelValue(
          protoCelValueConverter.fromProtoMessageToCelValue("", (MessageLite) message));
    } else {
      return unwrapCelValue(protoCelValueConverter.fromJavaObjectToCelValue(message));
    }
  }

  /**
   * DefaultInterpreter cannot handle CelValue and instead expects plain Java objects.
   *
   * <p>This will become unnecessary once we introduce a rewrite of a Cel runtime.
   */
  private Object unwrapCelValue(CelValue object) {
    return protoCelValueConverter.fromCelValueToJavaObject(object);
  }

  private static void throwInvalidFieldSelection(String fieldName) {
    throw new CelRuntimeException(
        new IllegalArgumentException(
            String.format(
                "Error resolving field '%s'. Field selections must be performed on messages or"
                    + " maps.",
                fieldName)),
        CelErrorCode.ATTRIBUTE_NOT_FOUND);
  }

  private CelValueRuntimeTypeProvider(
      CelValueProvider valueProvider, BaseProtoCelValueConverter protoCelValueConverter) {
    this.valueProvider = valueProvider;
    this.protoCelValueConverter = protoCelValueConverter;
  }
}
