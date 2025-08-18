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

package dev.cel.runtime;

import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.Message;
import dev.cel.common.CelOptions;
import dev.cel.common.internal.DefaultMessageFactory;
import dev.cel.common.internal.DynamicProto;
import dev.cel.common.internal.ProtoAdapter;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/** Package-private factory to facilitate binding a full protobuf message into an activation. */
final class ProtoMessageActivationFactory {

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
        new ProtoAdapter(DynamicProto.create(DefaultMessageFactory.INSTANCE), celOptions);

    boolean skipUnsetFields =
        celOptions.fromProtoUnsetFieldOption().equals(CelOptions.ProtoUnsetFieldOptions.SKIP);

    for (FieldDescriptor field : message.getDescriptorForType().getFields()) {
      // If skipping unset fields and the field is not repeated, then continue.
      if (skipUnsetFields && !field.isRepeated() && !msgFieldValues.containsKey(field)) {
        continue;
      }

      // Get the value of the field set on the message, if present, otherwise use reflection to
      // get the default value for the field using the FieldDescriptor.
      Object fieldValue = msgFieldValues.getOrDefault(field, message.getField(field));
      try {
        Optional<Object> adapted = protoAdapter.adaptFieldToValue(field, fieldValue);
        variables.put(field.getName(), adapted.orElse(null));
      } catch (IllegalArgumentException e) {
        variables.put(
            field.getName(),
            CelEvaluationExceptionBuilder.newBuilder(
                    "illegal field value. field=%s, value=%s", field.getName(), fieldValue)
                .setCause(e)
                .build());
      }
    }
    return Activation.copyOf(variables);
  }

  private ProtoMessageActivationFactory() {}
}
