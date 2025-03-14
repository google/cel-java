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
import dev.cel.expr.conformance.proto3.TestAllTypes;
import dev.cel.expr.conformance.proto3.TestAllTypesCelLiteDescriptor;
import dev.cel.protobuf.CelLiteDescriptor.FieldDescriptor;
import dev.cel.protobuf.CelLiteDescriptor.FieldDescriptor.CelFieldValueType;
import dev.cel.protobuf.CelLiteDescriptor.FieldDescriptor.JavaType;
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
  public void getDescriptors_fromProtoTypeAndJavaClassNames_referenceEquals() {
    Map<String, MessageLiteDescriptor> protoNamesToDescriptors =
        TEST_ALL_TYPES_CEL_LITE_DESCRIPTOR.getProtoTypeNamesToDescriptors();
    Map<String, MessageLiteDescriptor> javaClassNamesToDescriptors =
        TEST_ALL_TYPES_CEL_LITE_DESCRIPTOR.getProtoJavaClassNameToDescriptors();

    assertThat(protoNamesToDescriptors.get("cel.expr.conformance.proto3.TestAllTypes"))
        .isSameInstanceAs(
            javaClassNamesToDescriptors.get("dev.cel.expr.conformance.proto3.TestAllTypes"));
    assertThat(
            protoNamesToDescriptors.get("cel.expr.conformance.proto3.TestAllTypes.NestedMessage"))
        .isSameInstanceAs(
            javaClassNamesToDescriptors.get(
                "dev.cel.expr.conformance.proto3.TestAllTypes$NestedMessage"));
    assertThat(protoNamesToDescriptors.get("cel.expr.conformance.proto3.NestedTestAllTypes"))
        .isSameInstanceAs(
            javaClassNamesToDescriptors.get("dev.cel.expr.conformance.proto3.NestedTestAllTypes"));
  }

  @Test
  public void testAllTypesMessageLiteDescriptor_fullyQualifiedNames() {
    MessageLiteDescriptor testAllTypesDescriptor =
        TEST_ALL_TYPES_CEL_LITE_DESCRIPTOR
            .getProtoTypeNamesToDescriptors()
            .get("cel.expr.conformance.proto3.TestAllTypes");

    assertThat(testAllTypesDescriptor.getFullyQualifiedProtoTypeName())
        .isEqualTo("cel.expr.conformance.proto3.TestAllTypes");
    assertThat(testAllTypesDescriptor.getFullyQualifiedProtoJavaClassName())
        .isEqualTo("dev.cel.expr.conformance.proto3.TestAllTypes");
  }

  @Test
  public void testAllTypesMessageLiteDescriptor_fieldInfoMap_containsAllEntries() {
    MessageLiteDescriptor testAllTypesDescriptor =
        TEST_ALL_TYPES_CEL_LITE_DESCRIPTOR
            .getProtoTypeNamesToDescriptors()
            .get("cel.expr.conformance.proto3.TestAllTypes");

    assertThat(testAllTypesDescriptor.getFieldInfoMap()).hasSize(243);
  }

  @Test
  public void fieldDescriptor_scalarField() {
    MessageLiteDescriptor testAllTypesDescriptor =
        TEST_ALL_TYPES_CEL_LITE_DESCRIPTOR
            .getProtoTypeNamesToDescriptors()
            .get("cel.expr.conformance.proto3.TestAllTypes");
    FieldDescriptor fieldDescriptor = testAllTypesDescriptor.getFieldInfoMap().get("single_string");

    assertThat(fieldDescriptor.getCelFieldValueType()).isEqualTo(CelFieldValueType.SCALAR);
    assertThat(fieldDescriptor.getJavaType()).isEqualTo(JavaType.STRING);
    assertThat(fieldDescriptor.getProtoFieldType()).isEqualTo(FieldDescriptor.Type.STRING);
  }

  @Test
  public void fieldDescriptor_primitiveField_fullyQualifiedNames() {
    MessageLiteDescriptor testAllTypesDescriptor =
        TEST_ALL_TYPES_CEL_LITE_DESCRIPTOR
            .getProtoTypeNamesToDescriptors()
            .get("cel.expr.conformance.proto3.TestAllTypes");
    FieldDescriptor fieldDescriptor = testAllTypesDescriptor.getFieldInfoMap().get("single_string");

    assertThat(fieldDescriptor.getFullyQualifiedProtoFieldName())
        .isEqualTo("cel.expr.conformance.proto3.TestAllTypes.single_string");
    assertThat(fieldDescriptor.getFieldProtoTypeName()).isEmpty();
  }

  @Test
  public void fieldDescriptor_primitiveField_getFieldJavaClass() {
    MessageLiteDescriptor testAllTypesDescriptor =
        TEST_ALL_TYPES_CEL_LITE_DESCRIPTOR
            .getProtoTypeNamesToDescriptors()
            .get("cel.expr.conformance.proto3.TestAllTypes");
    FieldDescriptor fieldDescriptor = testAllTypesDescriptor.getFieldInfoMap().get("single_string");

    assertThat(fieldDescriptor.getFieldJavaClass()).isEqualTo(String.class);
    assertThat(fieldDescriptor.getFieldJavaClassName()).isEmpty();
  }

  @Test
  public void fieldDescriptor_scalarField_builderMethods() {
    MessageLiteDescriptor testAllTypesDescriptor =
        TEST_ALL_TYPES_CEL_LITE_DESCRIPTOR
            .getProtoTypeNamesToDescriptors()
            .get("cel.expr.conformance.proto3.TestAllTypes");
    FieldDescriptor fieldDescriptor = testAllTypesDescriptor.getFieldInfoMap().get("single_string");

    assertThat(fieldDescriptor.getHasHasser()).isFalse();
    assertThat(fieldDescriptor.getGetterName()).isEqualTo("getSingleString");
    assertThat(fieldDescriptor.getSetterName()).isEqualTo("setSingleString");
  }

  @Test
  public void fieldDescriptor_getHasserName_throwsIfNotWrapper() {
    MessageLiteDescriptor testAllTypesDescriptor =
        TEST_ALL_TYPES_CEL_LITE_DESCRIPTOR
            .getProtoTypeNamesToDescriptors()
            .get("cel.expr.conformance.proto3.TestAllTypes");
    FieldDescriptor fieldDescriptor = testAllTypesDescriptor.getFieldInfoMap().get("single_string");

    assertThrows(IllegalArgumentException.class, fieldDescriptor::getHasserName);
  }

  @Test
  public void fieldDescriptor_getHasserName_success() {
    MessageLiteDescriptor testAllTypesDescriptor =
        TEST_ALL_TYPES_CEL_LITE_DESCRIPTOR
            .getProtoTypeNamesToDescriptors()
            .get("cel.expr.conformance.proto3.TestAllTypes");
    FieldDescriptor fieldDescriptor =
        testAllTypesDescriptor.getFieldInfoMap().get("single_string_wrapper");

    assertThat(fieldDescriptor.getHasHasser()).isTrue();
    assertThat(fieldDescriptor.getHasserName()).isEqualTo("hasSingleStringWrapper");
  }

  @Test
  public void fieldDescriptor_mapField() {
    MessageLiteDescriptor testAllTypesDescriptor =
        TEST_ALL_TYPES_CEL_LITE_DESCRIPTOR
            .getProtoTypeNamesToDescriptors()
            .get("cel.expr.conformance.proto3.TestAllTypes");
    FieldDescriptor fieldDescriptor =
        testAllTypesDescriptor.getFieldInfoMap().get("map_bool_string");

    assertThat(fieldDescriptor.getCelFieldValueType()).isEqualTo(CelFieldValueType.MAP);
    assertThat(fieldDescriptor.getJavaType()).isEqualTo(JavaType.MESSAGE);
    assertThat(fieldDescriptor.getProtoFieldType()).isEqualTo(FieldDescriptor.Type.MESSAGE);
  }

  @Test
  public void fieldDescriptor_mapField_builderMethods() {
    MessageLiteDescriptor testAllTypesDescriptor =
        TEST_ALL_TYPES_CEL_LITE_DESCRIPTOR
            .getProtoTypeNamesToDescriptors()
            .get("cel.expr.conformance.proto3.TestAllTypes");
    FieldDescriptor fieldDescriptor =
        testAllTypesDescriptor.getFieldInfoMap().get("map_bool_string");

    assertThat(fieldDescriptor.getHasHasser()).isFalse();
    assertThat(fieldDescriptor.getGetterName()).isEqualTo("getMapBoolStringMap");
    assertThat(fieldDescriptor.getSetterName()).isEqualTo("putAllMapBoolString");
  }

  @Test
  public void fieldDescriptor_mapField_getFieldJavaClass() {
    MessageLiteDescriptor testAllTypesDescriptor =
        TEST_ALL_TYPES_CEL_LITE_DESCRIPTOR
            .getProtoTypeNamesToDescriptors()
            .get("cel.expr.conformance.proto3.TestAllTypes");
    FieldDescriptor fieldDescriptor =
        testAllTypesDescriptor.getFieldInfoMap().get("map_bool_string");

    assertThat(fieldDescriptor.getFieldJavaClass()).isEqualTo(Map.class);
    assertThat(fieldDescriptor.getFieldJavaClassName())
        .isEqualTo("dev.cel.expr.conformance.proto3.TestAllTypes$MapBoolStringEntry");
  }

  @Test
  public void fieldDescriptor_repeatedField() {
    MessageLiteDescriptor testAllTypesDescriptor =
        TEST_ALL_TYPES_CEL_LITE_DESCRIPTOR
            .getProtoTypeNamesToDescriptors()
            .get("cel.expr.conformance.proto3.TestAllTypes");
    FieldDescriptor fieldDescriptor =
        testAllTypesDescriptor.getFieldInfoMap().get("repeated_int64");

    assertThat(fieldDescriptor.getCelFieldValueType()).isEqualTo(CelFieldValueType.LIST);
    assertThat(fieldDescriptor.getJavaType()).isEqualTo(JavaType.LONG);
    assertThat(fieldDescriptor.getProtoFieldType()).isEqualTo(FieldDescriptor.Type.INT64);
  }

  @Test
  public void fieldDescriptor_repeatedField_builderMethods() {
    MessageLiteDescriptor testAllTypesDescriptor =
        TEST_ALL_TYPES_CEL_LITE_DESCRIPTOR
            .getProtoTypeNamesToDescriptors()
            .get("cel.expr.conformance.proto3.TestAllTypes");
    FieldDescriptor fieldDescriptor =
        testAllTypesDescriptor.getFieldInfoMap().get("repeated_int64");

    assertThat(fieldDescriptor.getHasHasser()).isFalse();
    assertThat(fieldDescriptor.getGetterName()).isEqualTo("getRepeatedInt64List");
    assertThat(fieldDescriptor.getSetterName()).isEqualTo("addAllRepeatedInt64");
  }

  @Test
  public void fieldDescriptor_repeatedField_primitives_getFieldJavaClass() {
    MessageLiteDescriptor testAllTypesDescriptor =
        TEST_ALL_TYPES_CEL_LITE_DESCRIPTOR
            .getProtoTypeNamesToDescriptors()
            .get("cel.expr.conformance.proto3.TestAllTypes");
    FieldDescriptor fieldDescriptor =
        testAllTypesDescriptor.getFieldInfoMap().get("repeated_int64");

    assertThat(fieldDescriptor.getFieldJavaClass()).isEqualTo(Iterable.class);
    assertThat(fieldDescriptor.getFieldJavaClassName()).isEmpty();
  }

  @Test
  public void fieldDescriptor_repeatedField_wrappers_getFieldJavaClass() {
    MessageLiteDescriptor testAllTypesDescriptor =
        TEST_ALL_TYPES_CEL_LITE_DESCRIPTOR
            .getProtoTypeNamesToDescriptors()
            .get("cel.expr.conformance.proto3.TestAllTypes");
    FieldDescriptor fieldDescriptor =
        testAllTypesDescriptor.getFieldInfoMap().get("repeated_double_wrapper");

    assertThat(fieldDescriptor.getFieldJavaClass()).isEqualTo(Iterable.class);
    assertThat(fieldDescriptor.getFieldJavaClassName())
        .isEqualTo("com.google.protobuf.DoubleValue");
  }

  @Test
  public void fieldDescriptor_nestedMessage() {
    MessageLiteDescriptor testAllTypesDescriptor =
        TEST_ALL_TYPES_CEL_LITE_DESCRIPTOR
            .getProtoTypeNamesToDescriptors()
            .get("cel.expr.conformance.proto3.TestAllTypes");
    FieldDescriptor fieldDescriptor =
        testAllTypesDescriptor.getFieldInfoMap().get("standalone_message");

    assertThat(fieldDescriptor.getCelFieldValueType()).isEqualTo(CelFieldValueType.SCALAR);
    assertThat(fieldDescriptor.getJavaType()).isEqualTo(JavaType.MESSAGE);
    assertThat(fieldDescriptor.getProtoFieldType()).isEqualTo(FieldDescriptor.Type.MESSAGE);
  }

  @Test
  public void fieldDescriptor_nestedMessage_getFieldJavaClass() {
    MessageLiteDescriptor testAllTypesDescriptor =
        TEST_ALL_TYPES_CEL_LITE_DESCRIPTOR
            .getProtoTypeNamesToDescriptors()
            .get("cel.expr.conformance.proto3.TestAllTypes");
    FieldDescriptor fieldDescriptor =
        testAllTypesDescriptor.getFieldInfoMap().get("standalone_message");

    assertThat(fieldDescriptor.getFieldJavaClass()).isEqualTo(TestAllTypes.NestedMessage.class);
    assertThat(fieldDescriptor.getFieldJavaClassName())
        .isEqualTo("dev.cel.expr.conformance.proto3.TestAllTypes$NestedMessage");
  }

  @Test
  public void fieldDescriptor_nestedMessage_fullyQualifiedNames() {
    MessageLiteDescriptor testAllTypesDescriptor =
        TEST_ALL_TYPES_CEL_LITE_DESCRIPTOR
            .getProtoTypeNamesToDescriptors()
            .get("cel.expr.conformance.proto3.TestAllTypes");
    FieldDescriptor fieldDescriptor =
        testAllTypesDescriptor.getFieldInfoMap().get("standalone_message");

    assertThat(fieldDescriptor.getFullyQualifiedProtoFieldName())
        .isEqualTo("cel.expr.conformance.proto3.TestAllTypes.standalone_message");
    assertThat(fieldDescriptor.getFieldProtoTypeName())
        .isEqualTo("cel.expr.conformance.proto3.TestAllTypes.NestedMessage");
  }
}
