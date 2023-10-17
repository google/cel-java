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
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertThrows;

import com.google.common.base.Ascii;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.Resources;
import com.google.protobuf.Any;
import com.google.protobuf.DescriptorProtos.FileDescriptorSet;
import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.Descriptors.FileDescriptor;
import com.google.protobuf.DynamicMessage;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Message;
import com.google.protobuf.Struct;
import com.google.protobuf.TextFormat;
import com.google.protobuf.Value;
import com.google.type.Expr;
import dev.cel.common.CelDescriptorUtil;
import dev.cel.common.CelDescriptors;
import dev.cel.testing.testdata.MultiFile;
import dev.cel.testing.testdata.SingleFileProto.SingleFile;
import java.io.IOException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class DynamicProtoTest {

  @Test
  public void unpackLinkedMessageType_withTypeRegistry() throws Exception {
    Expr expr =
        Expr.newBuilder().setExpression("a < b").setTitle("Simple ordering predicate").build();
    Any packedExpr = Any.pack(expr);
    CelDescriptors celDescriptors =
        CelDescriptorUtil.getAllDescriptorsFromFileDescriptor(Expr.getDescriptor().getFile());
    DynamicProto dynamicProto =
        DynamicProto.create(
            DefaultMessageFactory.create(DefaultDescriptorPool.create(celDescriptors)));

    Message unpacked = dynamicProto.unpack(packedExpr);

    assertThat(expr).isEqualTo(unpacked);
    assertThat(unpacked).isInstanceOf(Expr.class);
  }

  @Test
  public void unpackLinkedMessageType_withTypeRegistry_multiFileNested() throws Exception {
    MultiFile.File file =
        MultiFile.File.newBuilder()
            .setName("my_file")
            .setPath(MultiFile.File.Path.newBuilder().addFragments("dir"))
            .build();
    Any packed = Any.pack(file);
    CelDescriptors celDescriptors =
        CelDescriptorUtil.getAllDescriptorsFromFileDescriptor(MultiFile.getDescriptor().getFile());
    DynamicProto dynamicProto =
        DynamicProto.create(
            DefaultMessageFactory.create(DefaultDescriptorPool.create(celDescriptors)));

    Message unpacked = dynamicProto.unpack(packed);

    assertThat(file).isEqualTo(unpacked);
    assertThat(unpacked).isInstanceOf(MultiFile.File.class);
  }

  @Test
  public void unpackLinkedMessageType_withTypeRegistry_singleFileNested() throws Exception {
    SingleFile.Path path = SingleFile.Path.newBuilder().addFragments("dir").build();
    Any packed = Any.pack(path);
    CelDescriptors celDescriptors =
        CelDescriptorUtil.getAllDescriptorsFromFileDescriptor(SingleFile.getDescriptor().getFile());
    DynamicProto dynamicProto =
        DynamicProto.create(
            DefaultMessageFactory.create(DefaultDescriptorPool.create(celDescriptors)));

    Message unpacked = dynamicProto.unpack(packed);

    assertThat(path).isEqualTo(unpacked);
    assertThat(unpacked).isInstanceOf(SingleFile.Path.class);
  }

  @Test
  public void unpackLinkedMessageType_withTypeRegistryCached() throws Exception {
    Expr expr =
        Expr.newBuilder().setExpression("a < b").setTitle("Simple ordering predicate").build();
    Any packedExpr = Any.pack(expr);
    CelDescriptors celDescriptors =
        CelDescriptorUtil.getAllDescriptorsFromFileDescriptor(Expr.getDescriptor().getFile());
    DynamicProto dynamicProto =
        DynamicProto.create(
            DefaultMessageFactory.create(DefaultDescriptorPool.create(celDescriptors)));

    // Order is important here.
    Message unpacked = dynamicProto.unpack(packedExpr);
    assertThat(expr).isEqualTo(unpacked);
    assertThat(unpacked).isInstanceOf(Expr.class);
    Message unpacked2 = dynamicProto.unpack(packedExpr);
    assertThat(expr).isEqualTo(unpacked2);
  }

  @Test
  public void unpackLinkedMessageType_removeDescriptorLocalLinkedType() throws Exception {
    Struct struct =
        Struct.newBuilder()
            .putFields("hello", Value.newBuilder().setStringValue("world").build())
            .build();
    Any packedStruct = Any.pack(struct);
    // This descriptor set will contain an overlapping reference to Struct type
    FileDescriptorSet fds = TextFormat.parse(readFile("value.fds"), FileDescriptorSet.class);
    ImmutableList.Builder<FileDescriptor> fileDescriptorBuilder = ImmutableList.builder();
    fileDescriptorBuilder.add(Struct.getDescriptor().getFile());
    fileDescriptorBuilder.addAll(CelDescriptorUtil.getFileDescriptorsFromFileDescriptorSet(fds));
    // Unpacking should still use the linked class.
    CelDescriptors celDescriptors =
        CelDescriptorUtil.getAllDescriptorsFromFileDescriptor(fileDescriptorBuilder.build());
    DynamicProto dynamicProto =
        DynamicProto.create(
            DefaultMessageFactory.create(DefaultDescriptorPool.create(celDescriptors)));

    Message unpacked = dynamicProto.unpack(packedStruct);

    assertThat(unpacked).isInstanceOf(Struct.class);
    assertThat(struct).isEqualTo(unpacked);
  }

  @Test
  public void unpackDynamicMessageType_noDescriptor() throws Exception {
    DynamicProto dynamicProto =
        DynamicProto.create(DefaultMessageFactory.create(DefaultDescriptorPool.INSTANCE));
    Any.Builder anyValue = Any.newBuilder();
    TextFormat.merge(readFile("value.textproto"), anyValue);
    assertThat(anyValue.getTypeUrl()).isEqualTo("type.googleapis.com/google.api.expr.Value");
    assertThrows(InvalidProtocolBufferException.class, () -> dynamicProto.unpack(anyValue.build()));
  }

  @Test
  public void unpackDynamicMessageType_badDescriptor() throws Exception {
    DynamicProto dynamicProto =
        DynamicProto.create(DefaultMessageFactory.create(DefaultDescriptorPool.INSTANCE));
    Any.Builder anyValue = Any.newBuilder();
    TextFormat.merge(readFile("value.textproto"), anyValue);
    anyValue.setTypeUrl("google.api.expr.Value");
    assertThrows(InvalidProtocolBufferException.class, () -> dynamicProto.unpack(anyValue.build()));
  }

  @Test
  public void unpackDynamicMessageType() throws Exception {
    FileDescriptorSet fds = TextFormat.parse(readFile("value.fds"), FileDescriptorSet.class);
    ImmutableSet<FileDescriptor> files =
        CelDescriptorUtil.getFileDescriptorsFromFileDescriptorSet(fds);
    CelDescriptors celDescriptors = CelDescriptorUtil.getAllDescriptorsFromFileDescriptor(files);
    DynamicProto dynamicProto =
        DynamicProto.create(
            DefaultMessageFactory.create(DefaultDescriptorPool.create(celDescriptors)));
    Any.Builder anyValue = Any.newBuilder();
    TextFormat.merge(readFile("value.textproto"), anyValue);
    assertThat(anyValue.getTypeUrl()).isEqualTo("type.googleapis.com/google.api.expr.Value");
    Message message = dynamicProto.unpack(anyValue.build());
    assertThat(message).isNotNull();

    Descriptor valueDescriptor = message.getDescriptorForType();
    FieldDescriptor listValueField = valueDescriptor.findFieldByName("list_value");
    assertThat(listValueField).isNotNull();
    FieldDescriptor valuesField = listValueField.getMessageType().findFieldByName("values");
    assertThat(valuesField).isNotNull();
    FieldDescriptor stringValueField = valueDescriptor.findFieldByName("string_value");
    assertThat(stringValueField).isNotNull();
    FieldDescriptor int64ValueField = valueDescriptor.findFieldByName("int64_value");
    assertThat(int64ValueField).isNotNull();

    Message listValue = (Message) message.getField(listValueField);
    assertThat(listValue).isNotNull();

    assertThat(((Message) listValue.getRepeatedField(valuesField, 0)).getField(stringValueField))
        .isEqualTo("Hello");
    assertThat(((Message) listValue.getRepeatedField(valuesField, 1)).getField(stringValueField))
        .isEqualTo("World");
    assertThat(((Message) listValue.getRepeatedField(valuesField, 2)).getField(int64ValueField))
        .isEqualTo(42L);
  }

  @Test
  public void unpackDynamicMessageType_cached() throws Exception {
    FileDescriptorSet fds = TextFormat.parse(readFile("value.fds"), FileDescriptorSet.class);
    ImmutableSet<FileDescriptor> files =
        CelDescriptorUtil.getFileDescriptorsFromFileDescriptorSet(fds);
    CelDescriptors celDescriptors = CelDescriptorUtil.getAllDescriptorsFromFileDescriptor(files);
    DynamicProto dynamicProto =
        DynamicProto.create(
            DefaultMessageFactory.create(DefaultDescriptorPool.create(celDescriptors)));
    Any.Builder anyValue = Any.newBuilder();
    TextFormat.merge(readFile("value.textproto"), anyValue);
    assertThat(anyValue.getTypeUrl()).isEqualTo("type.googleapis.com/google.api.expr.Value");
    Message message = dynamicProto.unpack(anyValue.build());
    Message message2 = dynamicProto.unpack(anyValue.build());
    assertThat(message).isEqualTo(message2);
  }

  @Test
  public void maybeAdaptDynamicMessage() throws Exception {
    DynamicProto dynamicProto =
        DynamicProto.create(DefaultMessageFactory.create(DefaultDescriptorPool.INSTANCE));
    Struct struct =
        Struct.newBuilder()
            .putFields("hello", Value.newBuilder().setStringValue("world").build())
            .build();
    Any any = Any.pack(struct);
    DynamicMessage anyDyn =
        DynamicMessage.parseFrom(
            Struct.getDescriptor(),
            any.getValue(),
            DefaultDescriptorPool.INSTANCE.getExtensionRegistry());
    Message adapted = dynamicProto.maybeAdaptDynamicMessage(anyDyn);
    assertThat(adapted).isEqualTo(struct);
    assertThat(adapted).isInstanceOf(Struct.class);
  }

  @Test
  public void maybeAdaptDynamicMessage_cached() throws Exception {
    DynamicProto dynamicProto =
        DynamicProto.create(DefaultMessageFactory.create(DefaultDescriptorPool.INSTANCE));
    Struct struct =
        Struct.newBuilder()
            .putFields("hello", Value.newBuilder().setStringValue("world").build())
            .build();
    Any any = Any.pack(struct);
    DynamicMessage anyDyn =
        DynamicMessage.parseFrom(
            Struct.getDescriptor(),
            any.getValue(),
            DefaultDescriptorPool.INSTANCE.getExtensionRegistry());
    Message adapted = dynamicProto.maybeAdaptDynamicMessage(anyDyn);
    Message adapted2 = dynamicProto.maybeAdaptDynamicMessage(anyDyn);
    assertThat(adapted).isEqualTo(adapted2);
    assertThat(adapted).isInstanceOf(Struct.class);
    assertThat(adapted2).isInstanceOf(Struct.class);
  }

  @Test
  public void maybeAdaptDynamicMessage_notLinked() throws Exception {
    FileDescriptorSet fds = TextFormat.parse(readFile("value.fds"), FileDescriptorSet.class);
    ImmutableSet<FileDescriptor> files =
        CelDescriptorUtil.getFileDescriptorsFromFileDescriptorSet(fds);
    CelDescriptors celDescriptors = CelDescriptorUtil.getAllDescriptorsFromFileDescriptor(files);
    DynamicProto dynamicProto =
        DynamicProto.create(
            DefaultMessageFactory.create(DefaultDescriptorPool.create(celDescriptors)));
    Any any = TextFormat.parse(readFile("value.textproto"), Any.class);
    Message unpacked = dynamicProto.unpack(any);
    assertThat(unpacked).isInstanceOf(DynamicMessage.class);
    assertThat(dynamicProto.maybeAdaptDynamicMessage((DynamicMessage) unpacked))
        .isSameInstanceAs(unpacked);
  }

  private static String readFile(String path) throws IOException {
    return Resources.toString(Resources.getResource(Ascii.toLowerCase(path)), UTF_8);
  }
}
