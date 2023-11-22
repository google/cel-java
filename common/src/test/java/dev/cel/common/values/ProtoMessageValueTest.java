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

package dev.cel.common.values;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import com.google.common.collect.ImmutableList;
import com.google.testing.junit.testparameterinjector.TestParameterInjector;
import dev.cel.common.CelDescriptorUtil;
import dev.cel.common.internal.CelDescriptorPool;
import dev.cel.common.internal.DefaultDescriptorPool;
import dev.cel.common.types.StructTypeReference;
import dev.cel.testing.testdata.proto2.MessagesProto2Extensions;
import dev.cel.testing.testdata.proto2.Proto2Message;
import dev.cel.testing.testdata.proto2.TestAllTypesProto.TestAllTypes;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(TestParameterInjector.class)
public final class ProtoMessageValueTest {

  @Test
  public void emptyProtoMessage() {
    ProtoMessageValue protoMessageValue =
        ProtoMessageValue.create(TestAllTypes.getDefaultInstance(), DefaultDescriptorPool.INSTANCE);

    assertThat(protoMessageValue.value()).isEqualTo(TestAllTypes.getDefaultInstance());
    assertThat(protoMessageValue.isZeroValue()).isTrue();
  }

  @Test
  public void constructProtoMessage() {
    TestAllTypes testAllTypes =
        TestAllTypes.newBuilder().setSingleBool(true).setSingleInt64(5L).build();
    ProtoMessageValue protoMessageValue =
        ProtoMessageValue.create(testAllTypes, DefaultDescriptorPool.INSTANCE);

    assertThat(protoMessageValue.value()).isEqualTo(testAllTypes);
    assertThat(protoMessageValue.isZeroValue()).isFalse();
  }

  @Test
  public void hasField_fieldIsSet_success() {
    TestAllTypes testAllTypes =
        TestAllTypes.newBuilder()
            .setSingleBool(true)
            .setSingleInt64(5L)
            .addRepeatedInt64(5L)
            .build();
    ProtoMessageValue protoMessageValue =
        ProtoMessageValue.create(testAllTypes, DefaultDescriptorPool.INSTANCE);

    assertThat(protoMessageValue.hasField("single_bool")).isTrue();
    assertThat(protoMessageValue.hasField("single_int64")).isTrue();
    assertThat(protoMessageValue.hasField("repeated_int64")).isTrue();
  }

  @Test
  public void hasField_fieldIsUnset_success() {
    ProtoMessageValue protoMessageValue =
        ProtoMessageValue.create(TestAllTypes.getDefaultInstance(), DefaultDescriptorPool.INSTANCE);

    assertThat(protoMessageValue.hasField("single_int32")).isFalse();
    assertThat(protoMessageValue.hasField("single_uint64")).isFalse();
    assertThat(protoMessageValue.hasField("repeated_int32")).isFalse();
  }

  @Test
  public void hasField_fieldIsUndeclared_throwsException() {
    ProtoMessageValue protoMessageValue =
        ProtoMessageValue.create(TestAllTypes.getDefaultInstance(), DefaultDescriptorPool.INSTANCE);

    IllegalArgumentException exception =
        assertThrows(IllegalArgumentException.class, () -> protoMessageValue.hasField("bogus"));
    assertThat(exception)
        .hasMessageThat()
        .isEqualTo(
            "field 'bogus' is not declared in message"
                + " 'dev.cel.testing.testdata.proto2.TestAllTypes'");
  }

  @Test
  public void hasField_extensionField_success() {
    CelDescriptorPool descriptorPool =
        DefaultDescriptorPool.create(
            CelDescriptorUtil.getAllDescriptorsFromFileDescriptor(
                ImmutableList.of(MessagesProto2Extensions.getDescriptor())));
    Proto2Message proto2Message =
        Proto2Message.newBuilder().setExtension(MessagesProto2Extensions.int32Ext, 1).build();

    ProtoMessageValue protoMessageValue = ProtoMessageValue.create(proto2Message, descriptorPool);

    assertThat(protoMessageValue.hasField("dev.cel.testing.testdata.proto2.int32_ext")).isTrue();
  }

  @Test
  public void hasField_extensionField_throwsWhenDescriptorMissing() {
    Proto2Message proto2Message =
        Proto2Message.newBuilder().setExtension(MessagesProto2Extensions.int32Ext, 1).build();

    ProtoMessageValue protoMessageValue =
        ProtoMessageValue.create(proto2Message, DefaultDescriptorPool.INSTANCE);

    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () -> protoMessageValue.hasField("dev.cel.testing.testdata.proto2.int32_ext"));
    assertThat(exception)
        .hasMessageThat()
        .isEqualTo(
            "field 'dev.cel.testing.testdata.proto2.int32_ext' is not declared in message"
                + " 'dev.cel.testing.testdata.proto2.Proto2Message'");
  }

  @Test
  public void celTypeTest() {
    ProtoMessageValue protoMessageValue =
        ProtoMessageValue.create(TestAllTypes.getDefaultInstance(), DefaultDescriptorPool.INSTANCE);

    assertThat(protoMessageValue.celType())
        .isEqualTo(StructTypeReference.create(TestAllTypes.getDescriptor().getFullName()));
  }
}
