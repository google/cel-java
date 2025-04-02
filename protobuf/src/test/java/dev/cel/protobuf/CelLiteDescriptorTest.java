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
import static org.junit.Assert.assertThrows;

import com.google.testing.junit.testparameterinjector.TestParameterInjector;
import dev.cel.expr.conformance.proto3.TestAllTypesCelLiteDescriptor;
import dev.cel.protobuf.CelLiteDescriptor.FieldLiteDescriptor;
import dev.cel.protobuf.CelLiteDescriptor.FieldLiteDescriptor.CelFieldValueType;
import dev.cel.protobuf.CelLiteDescriptor.FieldLiteDescriptor.JavaType;
import dev.cel.protobuf.CelLiteDescriptor.MessageLiteDescriptor;
import java.util.Map;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(TestParameterInjector.class)
public class CelLiteDescriptorTest {

  private static final TestAllTypesCelLiteDescriptor TEST_ALL_TYPES_CEL_LITE_DESCRIPTOR =
      TestAllTypesCelLiteDescriptor.getDescriptor();

  @Test
  public void getProtoTypeNamesToDescriptors_containsAllMessages() {
    Map<String, MessageLiteDescriptor> protoNamesToDescriptors =
        TEST_ALL_TYPES_CEL_LITE_DESCRIPTOR.getProtoTypeNamesToDescriptors();

    assertThat(protoNamesToDescriptors).hasSize(3);
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
  public void fieldDescriptor_scalarField() {
    MessageLiteDescriptor testAllTypesDescriptor =
        TEST_ALL_TYPES_CEL_LITE_DESCRIPTOR
            .getProtoTypeNamesToDescriptors()
            .get("cel.expr.conformance.proto3.TestAllTypes");
    FieldLiteDescriptor fieldLiteDescriptor = testAllTypesDescriptor.getByFieldNameOrThrow("single_string");

    assertThat(fieldLiteDescriptor.getCelFieldValueType()).isEqualTo(CelFieldValueType.SCALAR);
    assertThat(fieldLiteDescriptor.getJavaType()).isEqualTo(JavaType.STRING);
    assertThat(fieldLiteDescriptor.getProtoFieldType()).isEqualTo(FieldLiteDescriptor.Type.STRING);
  }

  @Test
  public void fieldDescriptor_primitiveField_fullyQualifiedNames() {
    MessageLiteDescriptor testAllTypesDescriptor =
        TEST_ALL_TYPES_CEL_LITE_DESCRIPTOR
            .getProtoTypeNamesToDescriptors()
            .get("cel.expr.conformance.proto3.TestAllTypes");
    FieldLiteDescriptor fieldLiteDescriptor = testAllTypesDescriptor.getByFieldNameOrThrow("single_string");

    assertThat(fieldLiteDescriptor.getFullyQualifiedProtoFieldName())
        .isEqualTo("cel.expr.conformance.proto3.TestAllTypes.single_string");
    assertThat(fieldLiteDescriptor.getFieldProtoTypeName()).isEmpty();
  }

  @Test
  public void fieldDescriptor_hasHasser_falseOnPrimitive() {
    MessageLiteDescriptor testAllTypesDescriptor =
        TEST_ALL_TYPES_CEL_LITE_DESCRIPTOR
            .getProtoTypeNamesToDescriptors()
            .get("cel.expr.conformance.proto3.TestAllTypes");
    FieldLiteDescriptor fieldLiteDescriptor = testAllTypesDescriptor.getByFieldNameOrThrow("single_string");

    assertThat(fieldLiteDescriptor.getHasHasser()).isFalse();
  }

  @Test
  public void fieldDescriptor_hasHasser_trueOnWrapper() {
    MessageLiteDescriptor testAllTypesDescriptor =
        TEST_ALL_TYPES_CEL_LITE_DESCRIPTOR
            .getProtoTypeNamesToDescriptors()
            .get("cel.expr.conformance.proto3.TestAllTypes");
    FieldLiteDescriptor fieldLiteDescriptor =
        testAllTypesDescriptor.getByFieldNameOrThrow("single_string_wrapper");

    assertThat(fieldLiteDescriptor.getHasHasser()).isTrue();
  }

  @Test
  public void fieldDescriptor_mapField() {
    MessageLiteDescriptor testAllTypesDescriptor =
        TEST_ALL_TYPES_CEL_LITE_DESCRIPTOR
            .getProtoTypeNamesToDescriptors()
            .get("cel.expr.conformance.proto3.TestAllTypes");
    FieldLiteDescriptor fieldLiteDescriptor =
        testAllTypesDescriptor.getByFieldNameOrThrow("map_bool_string");

    assertThat(fieldLiteDescriptor.getCelFieldValueType()).isEqualTo(CelFieldValueType.MAP);
    assertThat(fieldLiteDescriptor.getJavaType()).isEqualTo(JavaType.MESSAGE);
    assertThat(fieldLiteDescriptor.getProtoFieldType()).isEqualTo(FieldLiteDescriptor.Type.MESSAGE);
  }

  @Test
  public void fieldDescriptor_hasHasser_falseOnMap() {
    MessageLiteDescriptor testAllTypesDescriptor =
        TEST_ALL_TYPES_CEL_LITE_DESCRIPTOR
            .getProtoTypeNamesToDescriptors()
            .get("cel.expr.conformance.proto3.TestAllTypes");
    FieldLiteDescriptor fieldLiteDescriptor =
        testAllTypesDescriptor.getByFieldNameOrThrow("map_bool_string");

    assertThat(fieldLiteDescriptor.getHasHasser()).isFalse();
  }

  @Test
  public void fieldDescriptor_repeatedField() {
    MessageLiteDescriptor testAllTypesDescriptor =
        TEST_ALL_TYPES_CEL_LITE_DESCRIPTOR
            .getProtoTypeNamesToDescriptors()
            .get("cel.expr.conformance.proto3.TestAllTypes");
    FieldLiteDescriptor fieldLiteDescriptor =
        testAllTypesDescriptor.getByFieldNameOrThrow("repeated_int64");

    assertThat(fieldLiteDescriptor.getCelFieldValueType()).isEqualTo(CelFieldValueType.LIST);
    assertThat(fieldLiteDescriptor.getJavaType()).isEqualTo(JavaType.LONG);
    assertThat(fieldLiteDescriptor.getProtoFieldType()).isEqualTo(FieldLiteDescriptor.Type.INT64);
  }

  @Test
  public void fieldDescriptor_hasHasser_falseOnRepeatedField() {
    MessageLiteDescriptor testAllTypesDescriptor =
        TEST_ALL_TYPES_CEL_LITE_DESCRIPTOR
            .getProtoTypeNamesToDescriptors()
            .get("cel.expr.conformance.proto3.TestAllTypes");
    FieldLiteDescriptor fieldLiteDescriptor =
        testAllTypesDescriptor.getByFieldNameOrThrow("repeated_int64");

    assertThat(fieldLiteDescriptor.getHasHasser()).isFalse();
  }

  @Test
  public void fieldDescriptor_nestedMessage() {
    MessageLiteDescriptor testAllTypesDescriptor =
        TEST_ALL_TYPES_CEL_LITE_DESCRIPTOR
            .getProtoTypeNamesToDescriptors()
            .get("cel.expr.conformance.proto3.TestAllTypes");
    FieldLiteDescriptor fieldLiteDescriptor =
        testAllTypesDescriptor.getByFieldNameOrThrow("standalone_message");

    assertThat(fieldLiteDescriptor.getCelFieldValueType()).isEqualTo(CelFieldValueType.SCALAR);
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

    assertThat(fieldLiteDescriptor.getFullyQualifiedProtoFieldName())
        .isEqualTo("cel.expr.conformance.proto3.TestAllTypes.standalone_message");
    assertThat(fieldLiteDescriptor.getFieldProtoTypeName())
        .isEqualTo("cel.expr.conformance.proto3.TestAllTypes.NestedMessage");
  }
}
