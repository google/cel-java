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
import static org.junit.Assert.assertThrows;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.protobuf.Any;
import com.google.protobuf.DescriptorProtos.FileDescriptorSet;
import com.google.protobuf.Descriptors.FileDescriptor;
import com.google.protobuf.Duration;
import com.google.protobuf.ListValue;
import com.google.protobuf.NullValue;
import com.google.protobuf.Struct;
import com.google.protobuf.Timestamp;
import com.google.protobuf.Value;
import com.google.rpc.context.AttributeContext;
import com.google.rpc.context.AttributeContext.Api;
import com.google.rpc.context.AttributeContext.Auth;
import com.google.rpc.context.AttributeContext.Peer;
import com.google.rpc.context.AttributeContext.Request;
import com.google.rpc.context.AttributeContext.Resource;
import com.google.rpc.context.AttributeContext.Response;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class CelDescriptorUtilTest {

  @Test
  public void getAllDescriptorsFromFileDescriptor() {
    CelDescriptors celDescriptors =
        CelDescriptorUtil.getAllDescriptorsFromFileDescriptor(
            ImmutableList.of(AttributeContext.getDescriptor().getFile()));

    assertThat(celDescriptors.messageTypeDescriptors())
        .containsExactly(
            Any.getDescriptor(),
            Duration.getDescriptor(),
            Struct.getDescriptor(),
            Value.getDescriptor(),
            ListValue.getDescriptor(),
            Timestamp.getDescriptor(),
            AttributeContext.getDescriptor(),
            Peer.getDescriptor(),
            Api.getDescriptor(),
            Auth.getDescriptor(),
            Request.getDescriptor(),
            Response.getDescriptor(),
            Resource.getDescriptor());
    assertThat(celDescriptors.enumDescriptors()).containsExactly(NullValue.getDescriptor());
    assertThat(celDescriptors.fileDescriptors())
        .containsExactly(
            AttributeContext.getDescriptor().getFile(),
            // The following fileDescriptors are defined as imports of AttributeContext proto
            Any.getDescriptor().getFile(),
            Timestamp.getDescriptor().getFile(),
            Struct.getDescriptor().getFile(),
            Duration.getDescriptor().getFile());
  }

  @Test
  public void getFileDescriptorsForDescriptors() {
    ImmutableSet<FileDescriptor> fileDescriptors =
        CelDescriptorUtil.getFileDescriptorsForDescriptors(
            ImmutableList.of(AttributeContext.getDescriptor()));
    assertThat(fileDescriptors)
        .containsExactly(
            Any.getDescriptor().getFile(),
            Duration.getDescriptor().getFile(),
            Struct.getDescriptor().getFile(),
            Timestamp.getDescriptor().getFile(),
            AttributeContext.getDescriptor().getFile());
  }

  @Test
  public void getFileDescriptorsForDescriptors_duplicateDescriptors() {
    ImmutableSet<FileDescriptor> fileDescriptors =
        CelDescriptorUtil.getFileDescriptorsForDescriptors(
            ImmutableList.of(AttributeContext.getDescriptor(), AttributeContext.getDescriptor()));
    assertThat(fileDescriptors)
        .containsExactly(
            Any.getDescriptor().getFile(),
            Duration.getDescriptor().getFile(),
            Struct.getDescriptor().getFile(),
            Timestamp.getDescriptor().getFile(),
            AttributeContext.getDescriptor().getFile());
  }

  @Test
  public void getFileDescriptorsForDescriptors_duplicateAncestorDescriptors() {
    ImmutableSet<FileDescriptor> fileDescriptors =
        CelDescriptorUtil.getFileDescriptorsForDescriptors(
            ImmutableList.of(AttributeContext.getDescriptor(), Any.getDescriptor()));
    assertThat(fileDescriptors)
        .containsExactly(
            Any.getDescriptor().getFile(),
            Duration.getDescriptor().getFile(),
            Struct.getDescriptor().getFile(),
            Timestamp.getDescriptor().getFile(),
            AttributeContext.getDescriptor().getFile());
  }

  @Test
  public void getFileDescriptorsFromFileDescriptorSet() {
    FileDescriptorSet fds =
        FileDescriptorSet.newBuilder()
            .addFile(Any.getDescriptor().getFile().toProto())
            .addFile(Duration.getDescriptor().getFile().toProto())
            .addFile(Struct.getDescriptor().getFile().toProto())
            .addFile(Timestamp.getDescriptor().getFile().toProto())
            .addFile(AttributeContext.getDescriptor().getFile().toProto())
            .build();
    ImmutableSet<FileDescriptor> fileDescriptors =
        CelDescriptorUtil.getFileDescriptorsFromFileDescriptorSet(fds);
    assertThat(fileDescriptors.stream().map(FileDescriptor::getName).collect(toImmutableSet()))
        .containsExactly(
            Any.getDescriptor().getFile().getName(),
            Duration.getDescriptor().getFile().getName(),
            Struct.getDescriptor().getFile().getName(),
            Timestamp.getDescriptor().getFile().getName(),
            AttributeContext.getDescriptor().getFile().getName());
  }

  @Test
  public void getFileDescriptorsFromFileDescriptorSet_incompleteFileSet() {
    FileDescriptorSet fds =
        FileDescriptorSet.newBuilder()
            .addFile(Duration.getDescriptor().getFile().toProto())
            .addFile(Struct.getDescriptor().getFile().toProto())
            .addFile(Timestamp.getDescriptor().getFile().toProto())
            .addFile(AttributeContext.getDescriptor().getFile().toProto())
            .build();
    IllegalArgumentException e =
        assertThrows(
            IllegalArgumentException.class,
            () -> CelDescriptorUtil.getFileDescriptorsFromFileDescriptorSet(fds));
    assertThat(e).hasMessageThat().contains("google/protobuf/any.proto");
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
