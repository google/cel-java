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

package dev.cel.protobuf;

import static com.google.common.truth.Truth.assertThat;

import com.google.testing.junit.testparameterinjector.TestParameterInjector;
import dev.cel.expr.conformance.proto3.TestAllTypesProto3LiteCelDescriptor;
import dev.cel.protobuf.CelLiteDescriptor.FieldLiteDescriptor;
import dev.cel.protobuf.CelLiteDescriptor.FieldLiteDescriptor.EncodingType;
import dev.cel.protobuf.CelLiteDescriptor.FieldLiteDescriptor.JavaType;
import dev.cel.protobuf.CelLiteDescriptor.MessageLiteDescriptor;
import java.util.Map;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(TestParameterInjector.class)
public class CelLiteDescriptorTest {

  private static final TestAllTypesProto3LiteCelDescriptor TEST_ALL_TYPES_CEL_LITE_DESCRIPTOR =
      TestAllTypesProto3LiteCelDescriptor.getDescriptor();

  @Test
  public void getProtoTypeNamesToDescriptors_containsAllMessages() {
    Map<String, MessageLiteDescriptor> protoNamesToDescriptors =
        TEST_ALL_TYPES_CEL_LITE_DESCRIPTOR.getProtoTypeNamesToDescriptors();

    assertThat(protoNamesToDescriptors).containsKey("cel.expr.conformance.proto3.TestAllTypes");
    assertThat(protoNamesToDescriptors)
        .containsKey("cel.expr.conformance.proto3.TestAllTypes.NestedMessage");
    assertThat(protoNamesToDescriptors)
        .containsKey("cel.expr.conformance.proto3.NestedTestAllTypes");
  }

  @Test
  public void testAllTypesMessageLiteDescriptor_fullyQualifiedNames() {
    MessageLiteDescriptor testAllTypesDescriptor =
        TEST_ALL_TYPES_CEL_LITE_DESCRIPTOR
            .getProtoTypeNamesToDescriptors()
            .get("cel.expr.conformance.proto3.TestAllTypes");

    assertThat(testAllTypesDescriptor.getProtoTypeName())
        .isEqualTo("cel.expr.conformance.proto3.TestAllTypes");
  }

  @Test
  public void testAllTypesMessageLiteDescriptor_fieldInfoMap_containsAllEntries() {
    MessageLiteDescriptor testAllTypesDescriptor =
        TEST_ALL_TYPES_CEL_LITE_DESCRIPTOR
            .getProtoTypeNamesToDescriptors()
            .get("cel.expr.conformance.proto3.TestAllTypes");

    assertThat(testAllTypesDescriptor.getFieldDescriptors()).hasSize(243);
  }

  @Test
  public void fieldDescriptor_getByFieldNumber() {
    MessageLiteDescriptor testAllTypesDescriptor =
        TEST_ALL_TYPES_CEL_LITE_DESCRIPTOR
            .getProtoTypeNamesToDescriptors()
            .get("cel.expr.conformance.proto3.TestAllTypes");

    FieldLiteDescriptor fieldLiteDescriptor = testAllTypesDescriptor.getByFieldNumberOrThrow(14);

    assertThat(fieldLiteDescriptor.getFieldName()).isEqualTo("single_string");
  }

  @Test
  public void fieldDescriptor_scalarField() {
    MessageLiteDescriptor testAllTypesDescriptor =
        TEST_ALL_TYPES_CEL_LITE_DESCRIPTOR
            .getProtoTypeNamesToDescriptors()
            .get("cel.expr.conformance.proto3.TestAllTypes");
    FieldLiteDescriptor fieldLiteDescriptor =
        testAllTypesDescriptor.getByFieldNameOrThrow("single_string");

    assertThat(fieldLiteDescriptor.getEncodingType()).isEqualTo(EncodingType.SINGULAR);
    assertThat(fieldLiteDescriptor.getJavaType()).isEqualTo(JavaType.STRING);
    assertThat(fieldLiteDescriptor.getProtoFieldType()).isEqualTo(FieldLiteDescriptor.Type.STRING);
  }

  @Test
  public void fieldDescriptor_primitiveField_fullyQualifiedNames() {
    MessageLiteDescriptor testAllTypesDescriptor =
        TEST_ALL_TYPES_CEL_LITE_DESCRIPTOR
            .getProtoTypeNamesToDescriptors()
            .get("cel.expr.conformance.proto3.TestAllTypes");
    FieldLiteDescriptor fieldLiteDescriptor =
        testAllTypesDescriptor.getByFieldNameOrThrow("single_string");

    assertThat(fieldLiteDescriptor.getFieldProtoTypeName()).isEmpty();
  }

  @Test
  public void fieldDescriptor_mapField() {
    MessageLiteDescriptor testAllTypesDescriptor =
        TEST_ALL_TYPES_CEL_LITE_DESCRIPTOR
            .getProtoTypeNamesToDescriptors()
            .get("cel.expr.conformance.proto3.TestAllTypes");
    FieldLiteDescriptor fieldLiteDescriptor =
        testAllTypesDescriptor.getByFieldNameOrThrow("map_bool_string");

    assertThat(fieldLiteDescriptor.getEncodingType()).isEqualTo(EncodingType.MAP);
    assertThat(fieldLiteDescriptor.getJavaType()).isEqualTo(JavaType.MESSAGE);
    assertThat(fieldLiteDescriptor.getProtoFieldType()).isEqualTo(FieldLiteDescriptor.Type.MESSAGE);
  }

  @Test
  public void fieldDescriptor_repeatedField() {
    MessageLiteDescriptor testAllTypesDescriptor =
        TEST_ALL_TYPES_CEL_LITE_DESCRIPTOR
            .getProtoTypeNamesToDescriptors()
            .get("cel.expr.conformance.proto3.TestAllTypes");
    FieldLiteDescriptor fieldLiteDescriptor =
        testAllTypesDescriptor.getByFieldNameOrThrow("repeated_int64");

    assertThat(fieldLiteDescriptor.getEncodingType()).isEqualTo(EncodingType.LIST);
    assertThat(fieldLiteDescriptor.getJavaType()).isEqualTo(JavaType.LONG);
    assertThat(fieldLiteDescriptor.getIsPacked()).isTrue();
    assertThat(fieldLiteDescriptor.getProtoFieldType()).isEqualTo(FieldLiteDescriptor.Type.INT64);
  }

  @Test
  public void fieldDescriptor_nestedMessage() {
    MessageLiteDescriptor testAllTypesDescriptor =
        TEST_ALL_TYPES_CEL_LITE_DESCRIPTOR
            .getProtoTypeNamesToDescriptors()
            .get("cel.expr.conformance.proto3.TestAllTypes");
    FieldLiteDescriptor fieldLiteDescriptor =
        testAllTypesDescriptor.getByFieldNameOrThrow("standalone_message");

    assertThat(fieldLiteDescriptor.getEncodingType()).isEqualTo(EncodingType.SINGULAR);
    assertThat(fieldLiteDescriptor.getJavaType()).isEqualTo(JavaType.MESSAGE);
    assertThat(fieldLiteDescriptor.getProtoFieldType()).isEqualTo(FieldLiteDescriptor.Type.MESSAGE);
  }

  @Test
  public void fieldDescriptor_nestedMessage_fullyQualifiedNames() {
    MessageLiteDescriptor testAllTypesDescriptor =
        TEST_ALL_TYPES_CEL_LITE_DESCRIPTOR
            .getProtoTypeNamesToDescriptors()
            .get("cel.expr.conformance.proto3.TestAllTypes");
    FieldLiteDescriptor fieldLiteDescriptor =
        testAllTypesDescriptor.getByFieldNameOrThrow("standalone_message");

    assertThat(fieldLiteDescriptor.getFieldProtoTypeName())
        .isEqualTo("cel.expr.conformance.proto3.TestAllTypes.NestedMessage");
  }
}
