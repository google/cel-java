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
import dev.cel.common.values.ProtoMessageLiteValueProvider;
import dev.cel.common.values.SelectableValue;
import dev.cel.common.values.StringValue;
import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;

/** Bridge between the old RuntimeTypeProvider and CelValueProvider APIs. */
@Internal
@Immutable
final class RuntimeTypeProviderLegacyImpl implements RuntimeTypeProvider {

  private final CelValueProvider valueProvider;
  private final BaseProtoCelValueConverter protoCelValueConverter;

  @SuppressWarnings("Immutable") // Lazily populated cache. Does not change any observable behavior.
  private final HashMap<MessageLite, CelValue> celMessageLiteCache;

  RuntimeTypeProviderLegacyImpl(
      ProtoMessageLiteValueProvider protoMessageLiteValueProvider) {
    this.valueProvider = protoMessageLiteValueProvider;
    this.protoCelValueConverter = protoMessageLiteValueProvider.getProtoLiteCelValueConverter();
    this.celMessageLiteCache = new HashMap<>();
  }


  RuntimeTypeProviderLegacyImpl(
      CelValueProvider valueProvider, BaseProtoCelValueConverter protoCelValueConverter) {
    this.valueProvider = valueProvider;
    this.protoCelValueConverter = protoCelValueConverter;
    this.celMessageLiteCache = new HashMap<>();
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
  public Object selectField(String typeName, Object message, String fieldName) {
    // TODO
    SelectableValue<CelValue> selectableValue = getSelectableValueOrThrow(typeName,
        message, fieldName);

    return unwrapCelValue(selectableValue.select(StringValue.create(fieldName)));
  }

  @Override
  @SuppressWarnings("unchecked")
  public Object hasField(String typeName, Object message, String fieldName) {
    // TODO
    SelectableValue<CelValue> selectableValue = getSelectableValueOrThrow(typeName,
        message, fieldName);

    return selectableValue.find(StringValue.create(fieldName)).isPresent();
  }

  private SelectableValue<CelValue> getSelectableValueOrThrow(String typeName, Object obj, String fieldName) {
    CelValue convertedCelValue = null;
    if ((obj instanceof MessageLite)) {
      convertedCelValue = celMessageLiteCache.get((MessageLite) obj);
      if (convertedCelValue == null) {
        throwInvalidFieldSelection(fieldName);
      }
      // convertedCelValue = protoCelValueConverter.fromProtoMessageToCelValue(typeName, (MessageLite) obj);
    } else if ((obj instanceof Map)) {
      convertedCelValue = protoCelValueConverter.fromJavaObjectToCelValue(obj);
    } else {
      throwInvalidFieldSelection(fieldName);
    }

    if (!(convertedCelValue instanceof SelectableValue)) {
      throwInvalidFieldSelection(fieldName);
    }

    return (SelectableValue<CelValue>) convertedCelValue;
  }

  @Override
  public Object adapt(String typeName, Object message) {
    if (message instanceof CelUnknownSet) {
      return message; // CelUnknownSet is handled specially for iterative evaluation. No need to
      // adapt to CelValue.
    }

    CelValue convertedCelValue;
    if (message instanceof MessageLite) {
      convertedCelValue = celMessageLiteCache.computeIfAbsent((MessageLite) message, (msg) -> protoCelValueConverter.fromProtoMessageToCelValue(typeName, (MessageLite) message));
    } else {
      convertedCelValue = protoCelValueConverter.fromJavaObjectToCelValue(message);
    }

    return unwrapCelValue(convertedCelValue);
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
}
