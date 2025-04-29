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

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.errorprone.annotations.Immutable;
import com.google.protobuf.MessageLite;
import dev.cel.common.CelErrorCode;
import dev.cel.common.CelRuntimeException;
import dev.cel.common.annotations.Internal;
import dev.cel.common.values.BaseProtoCelValueConverter;
import dev.cel.common.values.BaseProtoMessageValueProvider;
import dev.cel.common.values.CelValue;
import dev.cel.common.values.CelValueProvider;
import dev.cel.common.values.CombinedCelValueProvider;
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
  private static final BaseProtoCelValueConverter DEFAULT_CEL_VALUE_CONVERTER =
      new BaseProtoCelValueConverter() {
        @Override
        public CelValue fromProtoMessageToCelValue(String protoTypeName, MessageLite msg) {
          throw new UnsupportedOperationException(
              "A value provider must be provided in the runtime to handle protobuf messages");
        }
      };

  static CelValueRuntimeTypeProvider newInstance(CelValueProvider valueProvider) {
    BaseProtoCelValueConverter converter = DEFAULT_CEL_VALUE_CONVERTER;

    // Find the underlying ProtoCelValueConverter.
    // This is required because DefaultInterpreter works with a resolved protobuf messages directly
    // in evaluation flow.
    // A new runtime should not directly depend on protobuf, thus this will not be needed in the
    // future.
    if (valueProvider instanceof BaseProtoMessageValueProvider) {
      converter = ((BaseProtoMessageValueProvider) valueProvider).protoCelValueConverter();
    } else if (valueProvider instanceof CombinedCelValueProvider) {
      converter =
          ((CombinedCelValueProvider) valueProvider)
              .valueProviders().stream()
                  .filter(p -> p instanceof BaseProtoMessageValueProvider)
                  .map(p -> ((BaseProtoMessageValueProvider) p).protoCelValueConverter())
                  .findFirst()
                  .orElse(DEFAULT_CEL_VALUE_CONVERTER);
    }

    return new CelValueRuntimeTypeProvider(valueProvider, converter);
  }

  @Override
  public Object createMessage(String messageName, Map<String, Object> values) {
    return unwrapCelValue(
        valueProvider
            .newValue(messageName, values)
            .orElseThrow(
                () ->
                    new NoSuchElementException(
                        String.format("cannot resolve '%s' as a message", messageName))));
  }

  @Override
  public Object selectField(String typeName, Object message, String fieldName) {
    SelectableValue<CelValue> selectableValue =
        getSelectableValueOrThrow(typeName, message, fieldName);

    return unwrapCelValue(selectableValue.select(StringValue.create(fieldName)));
  }

  @Override
  public Object hasField(String messageName, Object message, String fieldName) {
    SelectableValue<CelValue> selectableValue =
        getSelectableValueOrThrow(messageName, message, fieldName);

    return selectableValue.find(StringValue.create(fieldName)).isPresent();
  }

  @SuppressWarnings("unchecked")
  private SelectableValue<CelValue> getSelectableValueOrThrow(
      String typeName, Object obj, String fieldName) {
    CelValue convertedCelValue;
    if ((obj instanceof MessageLite)) {
      convertedCelValue =
          protoCelValueConverter.fromProtoMessageToCelValue(typeName, (MessageLite) obj);
    } else {
      convertedCelValue = protoCelValueConverter.fromJavaObjectToCelValue(obj);
    }

    if (!(convertedCelValue instanceof SelectableValue)) {
      throwInvalidFieldSelection(fieldName);
    }

    return (SelectableValue<CelValue>) convertedCelValue;
  }

  @Override
  public Object adapt(String messageName, Object message) {
    if (message instanceof CelUnknownSet) {
      return message; // CelUnknownSet is handled specially for iterative evaluation. No need to
      // adapt to CelValue.
    }

    if (message instanceof MessageLite.Builder) {
      message = ((MessageLite.Builder) message).build();
    }

    if (message instanceof MessageLite) {
      return unwrapCelValue(
          protoCelValueConverter.fromProtoMessageToCelValue(messageName, (MessageLite) message));
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
    this.valueProvider = checkNotNull(valueProvider);
    this.protoCelValueConverter = checkNotNull(protoCelValueConverter);
  }
}
