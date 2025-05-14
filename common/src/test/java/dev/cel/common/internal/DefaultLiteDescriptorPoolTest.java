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

package dev.cel.common.internal;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import com.google.common.collect.ImmutableSet;
import com.google.common.truth.Expect;
import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.testing.junit.testparameterinjector.TestParameterInjector;
import dev.cel.expr.conformance.proto3.TestAllTypesCelDescriptor;
import dev.cel.protobuf.CelLiteDescriptor.FieldLiteDescriptor;
import dev.cel.protobuf.CelLiteDescriptor.FieldLiteDescriptor.EncodingType;
import dev.cel.protobuf.CelLiteDescriptor.MessageLiteDescriptor;
import java.util.NoSuchElementException;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(TestParameterInjector.class)
public class DefaultLiteDescriptorPoolTest {
  @Rule public final Expect expect = Expect.create();

  @Test
  public void containsAllWellKnownProtos() {
    DefaultLiteDescriptorPool descriptorPool =
        DefaultLiteDescriptorPool.newInstance(ImmutableSet.of());

    for (WellKnownProto wellKnownProto : WellKnownProto.values()) {
      MessageLiteDescriptor liteDescriptor =
          descriptorPool.getDescriptorOrThrow(wellKnownProto.typeName());
      expect.that(liteDescriptor.getProtoTypeName()).isEqualTo(wellKnownProto.typeName());
    }
  }

  @Test
  public void wellKnownProto_compareAgainstFullDescriptors_allFieldPropertiesAreEqual() {
    DefaultLiteDescriptorPool descriptorPool =
        DefaultLiteDescriptorPool.newInstance(ImmutableSet.of());

    for (WellKnownProto wellKnownProto : WellKnownProto.values()) {
      Descriptor fullDescriptor =
          DefaultDescriptorPool.INSTANCE.findDescriptor(wellKnownProto.typeName()).get();
      MessageLiteDescriptor liteDescriptor =
          descriptorPool.getDescriptorOrThrow(wellKnownProto.typeName());

      for (FieldDescriptor fullFieldDescriptor : fullDescriptor.getFields()) {
        String expectMessageTitle =
            wellKnownProto.typeName() + ", field number: " + fullFieldDescriptor.getNumber();
        FieldLiteDescriptor fieldLiteDescriptor =
            liteDescriptor.getByFieldNumberOrThrow(fullFieldDescriptor.getNumber());

        expect
            .withMessage(expectMessageTitle)
            .that(fieldLiteDescriptor.getFieldName())
            .isEqualTo(fullFieldDescriptor.getName());
        expect
            .withMessage(expectMessageTitle)
            .that(fieldLiteDescriptor.getIsPacked())
            .isEqualTo(fullFieldDescriptor.isPacked());
        expect
            .withMessage(expectMessageTitle)
            .that(fieldLiteDescriptor.getEncodingType())
            .isEqualTo(
                fullFieldDescriptor.isMapField()
                    ? EncodingType.MAP
                    : fullFieldDescriptor.isRepeated() ? EncodingType.LIST : EncodingType.SINGULAR);
        // Note: enums such as JavaType are semantically equal, but their instances differ.
        expect
            .withMessage(expectMessageTitle)
            .that(fieldLiteDescriptor.getJavaType().toString())
            .isEqualTo(fullFieldDescriptor.getJavaType().toString());
        expect
            .withMessage(expectMessageTitle)
            .that(fieldLiteDescriptor.getProtoFieldType().toString())
            .isEqualTo(fullFieldDescriptor.getType().toString());
        if (fullFieldDescriptor.getType().equals(FieldDescriptor.Type.MESSAGE)) {
          expect
              .withMessage(expectMessageTitle)
              .that(fieldLiteDescriptor.getFieldProtoTypeName())
              .isEqualTo(fullFieldDescriptor.getMessageType().getFullName());
        }
      }
    }
  }

  @Test
  public void findDescriptor_success() {
    DefaultLiteDescriptorPool descriptorPool =
        DefaultLiteDescriptorPool.newInstance(
            ImmutableSet.of(TestAllTypesCelDescriptor.getDescriptor()));

    MessageLiteDescriptor liteDescriptor =
        descriptorPool.getDescriptorOrThrow("cel.expr.conformance.proto3.TestAllTypes");

    assertThat(liteDescriptor.getProtoTypeName())
        .isEqualTo("cel.expr.conformance.proto3.TestAllTypes");
  }

  @Test
  public void findDescriptor_throws() {
    DefaultLiteDescriptorPool descriptorPool =
        DefaultLiteDescriptorPool.newInstance(ImmutableSet.of());

    assertThrows(NoSuchElementException.class, () -> descriptorPool.getDescriptorOrThrow("foo"));
  }
}
