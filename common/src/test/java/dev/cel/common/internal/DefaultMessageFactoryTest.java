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

package dev.cel.common.internal;

import static com.google.common.truth.Truth.assertThat;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.base.Ascii;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.Resources;
import com.google.common.truth.Truth8;
import com.google.protobuf.DescriptorProtos.FileDescriptorSet;
import com.google.protobuf.Descriptors.FileDescriptor;
import com.google.protobuf.DynamicMessage;
import com.google.protobuf.TextFormat;
import com.google.protobuf.Value;
import dev.cel.common.CelDescriptorUtil;
import dev.cel.common.CelDescriptors;
import dev.cel.common.internal.ProtoMessageFactory.CombinedMessageFactory;
import dev.cel.testing.testdata.proto2.TestAllTypesProto.TestAllTypes;
import java.util.Optional;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class DefaultMessageFactoryTest {

  @Test
  public void newBuilder_wellKnownType_producesNewMessage() {
    DefaultMessageFactory messageFactory = DefaultMessageFactory.INSTANCE;

    Value.Builder valueBuilder =
        (Value.Builder) messageFactory.newBuilder("google.protobuf.Value").get();

    assertThat(valueBuilder.setStringValue("hello").build())
        .isEqualTo(Value.newBuilder().setStringValue("hello").build());
  }

  @Test
  public void newBuilder_withDescriptor_producesNewMessageBuilder() {
    CelDescriptorPool celDescriptorPool =
        DefaultDescriptorPool.create(
            CelDescriptorUtil.getAllDescriptorsFromFileDescriptor(
                TestAllTypes.getDescriptor().getFile()));
    DefaultMessageFactory messageFactory = DefaultMessageFactory.create(celDescriptorPool);

    TestAllTypes.Builder builder =
        (TestAllTypes.Builder)
            messageFactory.newBuilder("dev.cel.testing.testdata.proto2.TestAllTypes").get();

    assertThat(builder.setSingleInt64(5L).build())
        .isEqualTo(TestAllTypes.newBuilder().setSingleInt64(5L).build());
  }

  @Test
  public void newBuilder_unknownMessage_returnsEmpty() {
    DefaultMessageFactory messageFactory = DefaultMessageFactory.INSTANCE;

    Truth8.assertThat(messageFactory.newBuilder("unknown_message")).isEmpty();
  }

  @Test
  public void newBuilder_unequalDescriptorForSameMessage_returnsDynamicMessage() throws Exception {
    String fdsContent =
        Resources.toString(Resources.getResource(Ascii.toLowerCase("value.fds")), UTF_8);
    FileDescriptorSet fds = TextFormat.parse(fdsContent, FileDescriptorSet.class);
    ImmutableSet<FileDescriptor> files =
        CelDescriptorUtil.getFileDescriptorsFromFileDescriptorSet(fds);
    CelDescriptors celDescriptors = CelDescriptorUtil.getAllDescriptorsFromFileDescriptor(files);

    DefaultMessageFactory messageFactory =
        DefaultMessageFactory.create(DefaultDescriptorPool.create(celDescriptors));

    Truth8.assertThat(messageFactory.newBuilder("google.api.expr.Value")).isPresent();
    assertThat(messageFactory.newBuilder("google.api.expr.Value").get())
        .isInstanceOf(DynamicMessage.Builder.class);
  }

  @Test
  public void getDescriptorPoolTest() {
    CelDescriptorPool celDescriptorPool =
        DefaultDescriptorPool.create(
            CelDescriptorUtil.getAllDescriptorsFromFileDescriptor(
                TestAllTypes.getDescriptor().getFile()));
    DefaultMessageFactory messageFactory = DefaultMessageFactory.create(celDescriptorPool);

    assertThat(messageFactory.getDescriptorPool()).isEqualTo(celDescriptorPool);
  }

  @Test
  public void combinedMessageFactoryTest() {
    CombinedMessageFactory messageFactory =
        new ProtoMessageFactory.CombinedMessageFactory(
            ImmutableList.of(
                DefaultMessageFactory.INSTANCE,
                (messageName) ->
                    messageName.equals("test")
                        ? Optional.of(TestAllTypes.newBuilder())
                        : Optional.empty()));

    assertThat(messageFactory.newBuilder("test").get().build())
        .isEqualTo(TestAllTypes.getDefaultInstance());
    Truth8.assertThat(messageFactory.newBuilder("bogus")).isEmpty();
  }
}
