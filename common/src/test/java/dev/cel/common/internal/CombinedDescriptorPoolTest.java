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

import com.google.common.collect.ImmutableList;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.ExtensionRegistry;
import com.google.protobuf.Value;
import dev.cel.common.CelDescriptorUtil;
import dev.cel.expr.conformance.proto2.TestAllTypes;
import dev.cel.expr.conformance.proto2.TestAllTypesExtensions;
import java.util.Optional;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class CombinedDescriptorPoolTest {

  @Test
  public void findDescriptor_descriptorReturnedFromBothPool() {
    CelDescriptorPool dynamicDescriptorPool =
        DefaultDescriptorPool.create(
            CelDescriptorUtil.getAllDescriptorsFromFileDescriptor(
                TestAllTypes.getDescriptor().getFile()));
    CombinedDescriptorPool combinedDescriptorPool =
        CombinedDescriptorPool.create(
            ImmutableList.of(DefaultDescriptorPool.INSTANCE, dynamicDescriptorPool));

    assertThat(combinedDescriptorPool.findDescriptor(Value.getDescriptor().getFullName()))
        .hasValue(
            Value.getDescriptor()); // Retrieved from default descriptor pool (well-known-type)
    assertThat(combinedDescriptorPool.findDescriptor(TestAllTypes.getDescriptor().getFullName()))
        .hasValue(TestAllTypes.getDescriptor()); // Retrieved from the dynamic descriptor pool.
  }

  @Test
  public void findDescriptor_returnsEmpty() {
    CelDescriptorPool descriptorPool =
        DefaultDescriptorPool.create(
            CelDescriptorUtil.getAllDescriptorsFromFileDescriptor(
                TestAllTypes.getDescriptor().getFile()));
    CombinedDescriptorPool combinedDescriptorPool =
        CombinedDescriptorPool.create(
            ImmutableList.of(DefaultDescriptorPool.INSTANCE, descriptorPool));

    assertThat(combinedDescriptorPool.findDescriptor("bogus")).isEmpty();
  }

  @Test
  public void findExtensionDescriptor_success() {
    CelDescriptorPool dynamicDescriptorPool =
        DefaultDescriptorPool.create(
            CelDescriptorUtil.getAllDescriptorsFromFileDescriptor(
                TestAllTypesExtensions.getDescriptor().getFile()));
    CombinedDescriptorPool combinedDescriptorPool =
        CombinedDescriptorPool.create(
            ImmutableList.of(DefaultDescriptorPool.INSTANCE, dynamicDescriptorPool));

    Optional<FieldDescriptor> fieldDescriptor =
        combinedDescriptorPool.findExtensionDescriptor(
            TestAllTypes.getDescriptor(), "cel.expr.conformance.proto2.test_all_types_ext");

    assertThat(fieldDescriptor).isPresent();
    assertThat(fieldDescriptor.get().isExtension()).isTrue();
    assertThat(fieldDescriptor.get().getFullName())
        .isEqualTo("cel.expr.conformance.proto2.test_all_types_ext");
  }

  @Test
  public void findExtensionDescriptor_returnsEmpty() {
    CelDescriptorPool dynamicDescriptorPool =
        DefaultDescriptorPool.create(
            CelDescriptorUtil.getAllDescriptorsFromFileDescriptor(
                TestAllTypesExtensions.getDescriptor().getFile()));
    CombinedDescriptorPool combinedDescriptorPool =
        CombinedDescriptorPool.create(
            ImmutableList.of(DefaultDescriptorPool.INSTANCE, dynamicDescriptorPool));

    assertThat(
            combinedDescriptorPool.findExtensionDescriptor(TestAllTypes.getDescriptor(), "bogus"))
        .isEmpty();
  }

  @Test
  public void getExtensionRegistry_onDefaultPool_returnsEmptyRegistry() {
    CombinedDescriptorPool combinedDescriptorPool =
        CombinedDescriptorPool.create(ImmutableList.of(DefaultDescriptorPool.INSTANCE));

    assertThat(combinedDescriptorPool.getExtensionRegistry())
        .isEqualTo(ExtensionRegistry.getEmptyRegistry());
  }
}
