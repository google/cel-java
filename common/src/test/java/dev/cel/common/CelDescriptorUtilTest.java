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

package dev.cel.common;

import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.protobuf.Any;
import com.google.protobuf.BoolValue;
import com.google.protobuf.BytesValue;
import com.google.protobuf.DescriptorProtos.FileDescriptorSet;
import com.google.protobuf.Descriptors.FileDescriptor;
import com.google.protobuf.DoubleValue;
import com.google.protobuf.Duration;
import com.google.protobuf.Empty;
import com.google.protobuf.FieldMask;
import com.google.protobuf.FloatValue;
import com.google.protobuf.Int32Value;
import com.google.protobuf.Int64Value;
import com.google.protobuf.ListValue;
import com.google.protobuf.NullValue;
import com.google.protobuf.StringValue;
import com.google.protobuf.Struct;
import com.google.protobuf.Timestamp;
import com.google.protobuf.UInt32Value;
import com.google.protobuf.UInt64Value;
import com.google.protobuf.Value;
import com.google.protobuf.WrappersProto;
import dev.cel.expr.conformance.proto3.GlobalEnum;
import dev.cel.expr.conformance.proto3.NestedTestAllTypes;
import dev.cel.expr.conformance.proto3.TestAllTypes;
import dev.cel.expr.conformance.proto3.TestAllTypes.NestedEnum;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class CelDescriptorUtilTest {

  @Test
  public void getAllDescriptorsFromFileDescriptor() {
    CelDescriptors celDescriptors =
        CelDescriptorUtil.getAllDescriptorsFromFileDescriptor(
            ImmutableList.of(TestAllTypes.getDescriptor().getFile()));

    assertThat(celDescriptors.messageTypeDescriptors())
        .containsExactly(
            Any.getDescriptor(),
            BoolValue.getDescriptor(),
            BytesValue.getDescriptor(),
            DoubleValue.getDescriptor(),
            Duration.getDescriptor(),
            Empty.getDescriptor(),
            FieldMask.getDescriptor(),
            FloatValue.getDescriptor(),
            Int32Value.getDescriptor(),
            Int64Value.getDescriptor(),
            ListValue.getDescriptor(),
            NestedTestAllTypes.getDescriptor(),
            StringValue.getDescriptor(),
            Struct.getDescriptor(),
            TestAllTypes.NestedMessage.getDescriptor(),
            TestAllTypes.getDescriptor(),
            Timestamp.getDescriptor(),
            UInt32Value.getDescriptor(),
            UInt64Value.getDescriptor(),
            Value.getDescriptor());
    assertThat(celDescriptors.enumDescriptors())
        .containsExactly(
            NullValue.getDescriptor(), GlobalEnum.getDescriptor(), NestedEnum.getDescriptor());
    assertThat(celDescriptors.fileDescriptors())
        .containsExactly(
            TestAllTypes.getDescriptor().getFile(),
            // The following fileDescriptors are defined as imports of TestAllTypes proto
            Any.getDescriptor().getFile(),
            Duration.getDescriptor().getFile(),
            Empty.getDescriptor().getFile(),
            FieldMask.getDescriptor().getFile(),
            Struct.getDescriptor().getFile(),
            Timestamp.getDescriptor().getFile(),
            WrappersProto.getDescriptor().getFile());
  }

  @Test
  public void getFileDescriptorsForDescriptors() {
    ImmutableSet<FileDescriptor> fileDescriptors =
        CelDescriptorUtil.getFileDescriptorsForDescriptors(
            ImmutableList.of(TestAllTypes.getDescriptor()));
    assertThat(fileDescriptors)
        .containsExactly(
            TestAllTypes.getDescriptor().getFile(),
            Any.getDescriptor().getFile(),
            Duration.getDescriptor().getFile(),
            Empty.getDescriptor().getFile(),
            FieldMask.getDescriptor().getFile(),
            Struct.getDescriptor().getFile(),
            Timestamp.getDescriptor().getFile(),
            WrappersProto.getDescriptor().getFile());
  }

  @Test
  public void getFileDescriptorsForDescriptors_duplicateDescriptors() {
    ImmutableSet<FileDescriptor> fileDescriptors =
        CelDescriptorUtil.getFileDescriptorsForDescriptors(
            ImmutableList.of(TestAllTypes.getDescriptor(), TestAllTypes.getDescriptor()));
    assertThat(fileDescriptors)
        .containsExactly(
            TestAllTypes.getDescriptor().getFile(),
            Any.getDescriptor().getFile(),
            Duration.getDescriptor().getFile(),
            Empty.getDescriptor().getFile(),
            FieldMask.getDescriptor().getFile(),
            Struct.getDescriptor().getFile(),
            Timestamp.getDescriptor().getFile(),
            WrappersProto.getDescriptor().getFile());
  }

  @Test
  public void getFileDescriptorsForDescriptors_duplicateAncestorDescriptors() {
    ImmutableSet<FileDescriptor> fileDescriptors =
        CelDescriptorUtil.getFileDescriptorsForDescriptors(
            ImmutableList.of(TestAllTypes.getDescriptor(), Any.getDescriptor()));
    assertThat(fileDescriptors)
        .containsExactly(
            TestAllTypes.getDescriptor().getFile(),
            Any.getDescriptor().getFile(),
            Duration.getDescriptor().getFile(),
            Empty.getDescriptor().getFile(),
            FieldMask.getDescriptor().getFile(),
            Struct.getDescriptor().getFile(),
            Timestamp.getDescriptor().getFile(),
            WrappersProto.getDescriptor().getFile());
  }

  @Test
  public void getFileDescriptorsFromFileDescriptorSet() {
    FileDescriptorSet fds =
        FileDescriptorSet.newBuilder()
            .addFile(Any.getDescriptor().getFile().toProto())
            .addFile(Empty.getDescriptor().getFile().toProto())
            .addFile(FieldMask.getDescriptor().getFile().toProto())
            .addFile(WrappersProto.getDescriptor().getFile().toProto())
            .addFile(Duration.getDescriptor().getFile().toProto())
            .addFile(Struct.getDescriptor().getFile().toProto())
            .addFile(Timestamp.getDescriptor().getFile().toProto())
            .addFile(TestAllTypes.getDescriptor().getFile().toProto())
            .build();
    ImmutableSet<FileDescriptor> fileDescriptors =
        CelDescriptorUtil.getFileDescriptorsFromFileDescriptorSet(fds);
    assertThat(fileDescriptors.stream().map(FileDescriptor::getName).collect(toImmutableSet()))
        .containsExactly(
            TestAllTypes.getDescriptor().getFile().getName(),
            Any.getDescriptor().getFile().getName(),
            Duration.getDescriptor().getFile().getName(),
            Empty.getDescriptor().getFile().getName(),
            FieldMask.getDescriptor().getFile().getName(),
            Struct.getDescriptor().getFile().getName(),
            Timestamp.getDescriptor().getFile().getName(),
            WrappersProto.getDescriptor().getFile().getName());
  }

  @Test
  public void getFileDescriptorsFromFileDescriptorSet_incompleteFileSet() {
    FileDescriptorSet fds =
        FileDescriptorSet.newBuilder()
            .addFile(Duration.getDescriptor().getFile().toProto())
            .addFile(Struct.getDescriptor().getFile().toProto())
            .addFile(Timestamp.getDescriptor().getFile().toProto())
            .addFile(TestAllTypes.getDescriptor().getFile().toProto())
            .build();

    ImmutableSet<FileDescriptor> fileDescriptors =
        CelDescriptorUtil.getFileDescriptorsFromFileDescriptorSet(fds);

    assertThat(fileDescriptors.stream().map(FileDescriptor::getName).collect(toImmutableSet()))
        .containsExactly(
            TestAllTypes.getDescriptor().getFile().getName(),
            Duration.getDescriptor().getFile().getName(),
            Struct.getDescriptor().getFile().getName(),
            Timestamp.getDescriptor().getFile().getName());
  }

  @Test
  public void getFileDescriptorsFromFileDescriptorSet_duplicateFiles() {
    FileDescriptorSet fds =
        FileDescriptorSet.newBuilder()
            .addFile(Any.getDescriptor().getFile().toProto())
            .addFile(Any.getDescriptor().getFile().toProto())
            .build();
    ImmutableSet<FileDescriptor> fileDescriptors =
        CelDescriptorUtil.getFileDescriptorsFromFileDescriptorSet(fds);
    assertThat(fileDescriptors.stream().map(FileDescriptor::getName).collect(toImmutableSet()))
        .containsExactly(Any.getDescriptor().getFile().getName());
  }
}
