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

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.truth.Truth.assertThat;
import static java.util.Arrays.stream;

import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Message;
import dev.cel.common.testdata.EnumConflictingNameOuterClass.MessageInEnumConflictingName;
import dev.cel.common.testdata.MessageConflictingNameOuterClass.MessageConflictingName;
import dev.cel.common.testdata.MessageConflictingNameOuterClass.MessageInConflictingNameClass;
import dev.cel.common.testdata.ProtoWithoutJavaOpts.MessageWithoutJavaOpts;
import dev.cel.common.testdata.ServiceConflictingNameOuterClass.StubRequest;
import com.google.testing.junit.testparameterinjector.TestParameter;
import com.google.testing.junit.testparameterinjector.TestParameterInjector;
import dev.cel.common.testdata.ProtoJavaApiVersion1.Proto2JavaVersion1Message;
import dev.cel.common.testing.RepeatedTestProvider;
import dev.cel.testing.testdata.proto3.TestAllTypesProto.TestAllTypes;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(TestParameterInjector.class)
public final class DefaultInstanceMessageFactoryTest {

  @Before
  public void setUp() {
    // Reset the statically initialized descriptor map to get clean test runs.
    DefaultInstanceMessageFactory.getInstance().resetDescriptorMapForTesting();
  }

  private enum PrototypeDescriptorTestCase {
    MESSAGE(TestAllTypes.getDescriptor(), TestAllTypes.getDefaultInstance()),
    NESTED_MESSAGE(
        TestAllTypes.NestedMessage.getDescriptor(),
        TestAllTypes.NestedMessage.getDefaultInstance()),
    MESSAGE_WITHOUT_JAVA_OPTS(
        MessageWithoutJavaOpts.getDescriptor(), MessageWithoutJavaOpts.getDefaultInstance()),
    NESTED_MESSAGE_WITHOUT_JAVA_OPTS(
        MessageWithoutJavaOpts.NestedMessage.getDescriptor(),
        MessageWithoutJavaOpts.NestedMessage.getDefaultInstance()),
    MESSAGE_WITH_CONFLICTING_OUTER_CLASS_NAME(
        MessageConflictingName.getDescriptor(), MessageConflictingName.getDefaultInstance()),
    NESTED_MESSAGE_WITH_CONFLICTING_OUTER_CLASS_NAME(
        MessageConflictingName.NestedMessage.getDescriptor(),
        MessageConflictingName.NestedMessage.getDefaultInstance()),
    ENUM_WITH_CONFLICTING_OUTER_CLASS_NAME(
        MessageInEnumConflictingName.getDescriptor(),
        MessageInEnumConflictingName.getDefaultInstance()),
    SERVICE_WITH_CONFLICTING_OUTER_CLASS_NAME(
        StubRequest.getDescriptor(), StubRequest.getDefaultInstance()),
    ANOTHER_MESSAGE_IN_CONFLICTING_OUTER_CLASS_NAME(
        MessageInConflictingNameClass.getDescriptor(),
        MessageInConflictingNameClass.getDefaultInstance()),
    PROTO2_JAVA_VERSION_1_MESSAGE(
        Proto2JavaVersion1Message.getDescriptor(), Proto2JavaVersion1Message.getDefaultInstance());

    final Descriptor descriptor;
    final Message defaultInstance;

    PrototypeDescriptorTestCase(Descriptor descriptor, Message defaultInstance) {
      this.descriptor = descriptor;
      this.defaultInstance = defaultInstance;
    }
  }

  @Test
  public void getPrototype_success(@TestParameter PrototypeDescriptorTestCase testCase) {
    Descriptor descriptor = testCase.descriptor;

    Optional<Message> defaultMessage =
        DefaultInstanceMessageFactory.getInstance().getPrototype(descriptor);

    assertThat(defaultMessage).hasValue(testCase.defaultInstance);
  }

  @Test
  public void getPrototype_cached_success(@TestParameter PrototypeDescriptorTestCase testCase) {
    Descriptor descriptor = testCase.descriptor;

    Optional<Message> defaultMessage =
        DefaultInstanceMessageFactory.getInstance().getPrototype(descriptor);
    Optional<Message> defaultMessage2 =
        DefaultInstanceMessageFactory.getInstance().getPrototype(descriptor);

    assertThat(defaultMessage).hasValue(testCase.defaultInstance);
    assertThat(defaultMessage2).hasValue(testCase.defaultInstance);
  }

  @Test
  public void getPrototype_concurrentAccess_doesNotThrow(
      @TestParameter(valuesProvider = RepeatedTestProvider.class) int testRunIndex)
      throws Exception {
    // Arrange
    int threadCount = 10;
    ExecutorService executor =
        MoreExecutors.getExitingExecutorService(
            (ThreadPoolExecutor) Executors.newFixedThreadPool(threadCount));
    ImmutableList<Message> expectedDefaultInstances =
        stream(PrototypeDescriptorTestCase.values())
            .map(x -> x.defaultInstance)
            .collect(toImmutableList());

    // Act
    List<Future<Optional<Message>>> futures = new ArrayList<>();
    for (PrototypeDescriptorTestCase testCase : PrototypeDescriptorTestCase.values()) {
      futures.add(
          executor.submit(
              () -> DefaultInstanceMessageFactory.getInstance().getPrototype(testCase.descriptor)));
    }
    List<Message> evaluatedDefaultInstances = new ArrayList<>();
    for (Future<Optional<Message>> future : futures) {
      evaluatedDefaultInstances.add(future.get().get());
    }

    // Assert
    assertThat(evaluatedDefaultInstances).containsExactlyElementsIn(expectedDefaultInstances);
  }
}
