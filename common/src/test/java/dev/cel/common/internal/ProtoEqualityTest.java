// Copyright 2022 Google LLC
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

import com.google.protobuf.Any;
import com.google.protobuf.DynamicMessage;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Struct;
import com.google.protobuf.Value;
import dev.cel.testing.testdata.proto3.TestAllTypesProto.TestAllTypes;
import dev.cel.testing.testdata.proto3.TestAllTypesProto.TestAllTypes.NestedEnum;
import dev.cel.testing.testdata.proto3.TestAllTypesProto.TestAllTypes.NestedMessage;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class ProtoEqualityTest {

  private DynamicProto dynamicProto;
  private ProtoEquality protoEquality;

  @Before
  public void setUp() {
    this.dynamicProto = DynamicProto.newBuilder().build();
    this.protoEquality = new ProtoEquality(dynamicProto);
  }

  @Test
  public void equalsFloatFields() {
    assertThat(
            protoEquality.equals(
                TestAllTypes.newBuilder().setSingleDouble(1.3).build(),
                TestAllTypes.newBuilder().setSingleDouble(1.3).build()))
        .isTrue();
  }

  @Test
  public void notEqualsFloatFields() {
    assertThat(
            protoEquality.equals(
                TestAllTypes.newBuilder().setSingleDouble(4.88888).build(),
                TestAllTypes.newBuilder().setSingleDouble(4.8).build()))
        .isFalse();
  }

  @Test
  public void notEqualsNaNFields() {
    assertThat(
            protoEquality.equals(
                TestAllTypes.newBuilder().setSingleDouble(Double.NaN).build(),
                TestAllTypes.newBuilder().setSingleDouble(Double.NaN).build()))
        .isFalse();
  }

  @Test
  public void equalsIntFields() {
    assertThat(
            protoEquality.equals(
                TestAllTypes.newBuilder().setSingleInt32(2).build(),
                TestAllTypes.newBuilder().setSingleInt32(2).build()))
        .isTrue();
  }

  @Test
  public void notEqualsIntFields() {
    assertThat(
            protoEquality.equals(
                TestAllTypes.newBuilder().setSingleInt32(1).build(),
                TestAllTypes.newBuilder().setSingleInt32(2).build()))
        .isFalse();
  }

  @Test
  public void equalsStringFields() {
    assertThat(
            protoEquality.equals(
                TestAllTypes.newBuilder().setSingleString("hello").build(),
                TestAllTypes.newBuilder().setSingleString("hello").build()))
        .isTrue();
  }

  @Test
  public void notEqualsStringFields() {
    assertThat(
            protoEquality.equals(
                TestAllTypes.newBuilder().setSingleString("hello").build(),
                TestAllTypes.newBuilder().setSingleString("world").build()))
        .isFalse();
  }

  @Test
  public void equalsMessage_sameInstance() {
    assertThat(
            protoEquality.equals(
                TestAllTypes.getDefaultInstance(), TestAllTypes.getDefaultInstance()))
        .isTrue();
  }

  @Test
  public void notEqualsMessage_differentTypes() {
    assertThat(
            protoEquality.equals(
                TestAllTypes.getDefaultInstance(), NestedMessage.getDefaultInstance()))
        .isFalse();
  }

  @Test
  public void equalsMessageFields() {
    assertThat(
            protoEquality.equals(
                TestAllTypes.newBuilder()
                    .setSingleNestedMessage(NestedMessage.newBuilder().setBb(42))
                    .build(),
                TestAllTypes.newBuilder()
                    .setSingleNestedMessage(NestedMessage.newBuilder().setBb(42))
                    .build()))
        .isTrue();
  }

  @Test
  public void notEqualsMessageFields() {
    assertThat(
            protoEquality.equals(
                TestAllTypes.newBuilder()
                    .setSingleNestedMessage(NestedMessage.getDefaultInstance())
                    .build(),
                TestAllTypes.newBuilder()
                    .setSingleNestedMessage(NestedMessage.newBuilder().setBb(42))
                    .build()))
        .isFalse();
  }

  @Test
  public void notEqualsMessageFields_differentFieldSet() {
    assertThat(
            protoEquality.equals(
                TestAllTypes.newBuilder().setSingleInt32(1).build(),
                TestAllTypes.newBuilder().setSingleInt64(2L).build()))
        .isFalse();
  }

  @Test
  public void equalsRepeatedFields() {
    assertThat(
            protoEquality.equals(
                TestAllTypes.newBuilder()
                    .addRepeatedNestedEnum(NestedEnum.FOO)
                    .addRepeatedNestedMessage(NestedMessage.newBuilder().setBb(42))
                    .addRepeatedInt32(1)
                    .addRepeatedInt32(2)
                    .addRepeatedInt32(3)
                    .putMapStringString("key", "value")
                    .build(),
                TestAllTypes.newBuilder()
                    .addRepeatedNestedMessage(NestedMessage.newBuilder().setBb(42))
                    .addRepeatedNestedEnum(NestedEnum.FOO)
                    .addRepeatedInt32(1)
                    .addRepeatedInt32(2)
                    .addRepeatedInt32(3)
                    .putMapStringString("key", "value")
                    .build()))
        .isTrue();
  }

  @Test
  public void notEqualsRepeatedFields_differentLengths() {
    assertThat(
            protoEquality.equals(
                TestAllTypes.newBuilder().addRepeatedInt32(1).addRepeatedInt32(2).build(),
                TestAllTypes.newBuilder()
                    .addRepeatedInt32(1)
                    .addRepeatedInt32(2)
                    .addRepeatedInt32(3)
                    .build()))
        .isFalse();
  }

  @Test
  public void notEqualsRepeatedFields_differentValues() {
    assertThat(
            protoEquality.equals(
                TestAllTypes.newBuilder().addRepeatedInt32(1).addRepeatedInt32(2).build(),
                TestAllTypes.newBuilder().addRepeatedInt32(1).addRepeatedInt32(3).build()))
        .isFalse();
  }

  @Test
  public void notEqualsRepeatedFields_differentMapSizes() {
    assertThat(
            protoEquality.equals(
                TestAllTypes.newBuilder()
                    .putMapStringString("hello", "world")
                    .putMapStringString("goodbye", "world")
                    .build(),
                TestAllTypes.newBuilder().putMapStringString("goodbye", "world").build()))
        .isFalse();
  }

  @Test
  public void notEqualsRepeatedFields_differentMapKeys() {
    assertThat(
            protoEquality.equals(
                TestAllTypes.newBuilder().putMapStringString("hello", "world").build(),
                TestAllTypes.newBuilder().putMapStringString("goodbye", "world").build()))
        .isFalse();
  }

  @Test
  public void notEqualsRepeatedFields_differentMapValues() {
    assertThat(
            protoEquality.equals(
                TestAllTypes.newBuilder().putMapStringString("hello", "world").build(),
                TestAllTypes.newBuilder().putMapStringString("hello", "universe").build()))
        .isFalse();
  }

  @Test
  public void equalsMessageAnyFields() {
    Struct struct =
        Struct.newBuilder()
            .putFields("first", Value.newBuilder().setStringValue("entry1").build())
            .putFields("second", Value.newBuilder().setStringValue("entry2").build())
            .build();
    Any packedStruct = Any.pack(struct);
    Any packedStruct2 = Any.pack(struct);
    assertThat(protoEquality.equals(packedStruct, packedStruct2)).isTrue();
  }

  @Test
  public void equalsMessageUnknownAnyFields() {
    Struct struct =
        Struct.newBuilder()
            .putFields("first", Value.newBuilder().setStringValue("entry1").build())
            .putFields("second", Value.newBuilder().setStringValue("entry2").build())
            .build();
    Any packedStruct = Any.pack(struct);
    Any packedStruct2 = Any.pack(struct);
    packedStruct = packedStruct.toBuilder().setTypeUrl("type.googleapis.com/Unknown").build();
    assertThat(protoEquality.equals(packedStruct, packedStruct2)).isTrue();
  }

  @Test
  public void equalsMessageDynamicAnyFields() throws InvalidProtocolBufferException {
    Struct struct =
        Struct.newBuilder()
            .putFields("first", Value.newBuilder().setStringValue("entry1").build())
            .putFields("second", Value.newBuilder().setStringValue("entry2").build())
            .build();
    Any packedStruct = Any.pack(struct);
    Any doublePackedStruct = Any.pack(packedStruct);
    DynamicMessage dynAny =
        DynamicMessage.parseFrom(
            Any.getDescriptor(),
            doublePackedStruct.getValue(),
            ProtoRegistryProvider.getExtensionRegistry());
    DynamicMessage dynAny2 =
        DynamicMessage.parseFrom(
            Any.getDescriptor(),
            doublePackedStruct.getValue(),
            ProtoRegistryProvider.getExtensionRegistry());
    assertThat(protoEquality.equals(dynAny, dynAny2)).isTrue();
  }
}
