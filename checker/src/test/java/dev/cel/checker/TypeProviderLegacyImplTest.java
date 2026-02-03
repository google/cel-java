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

package dev.cel.checker;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.protobuf.Descriptors.Descriptor;
import dev.cel.common.types.CelType;
import dev.cel.expr.conformance.proto2.TestAllTypes;
import java.util.Optional;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class TypeProviderLegacyImplTest {

  private static final ImmutableList<Descriptor> DESCRIPTORS =
      ImmutableList.of(TestAllTypes.getDescriptor());

  @Test
  public void findType_delegatesToLegacyLookup() {
    DescriptorTypeProvider legacyProvider = new DescriptorTypeProvider(DESCRIPTORS);
    TypeProviderLegacyImpl celTypeProvider = new TypeProviderLegacyImpl(legacyProvider);
    String typeName = TestAllTypes.getDescriptor().getFullName();

    Optional<CelType> result = celTypeProvider.findType(typeName);

    assertThat(result).isPresent();
    assertThat(result.get().name()).isEqualTo(typeName);
  }

  @Test
  public void findType_returnsEmptyForUnknownType() {
    DescriptorTypeProvider legacyProvider = new DescriptorTypeProvider(DESCRIPTORS);
    TypeProviderLegacyImpl celTypeProvider = new TypeProviderLegacyImpl(legacyProvider);

    Optional<CelType> result = celTypeProvider.findType("unknown.Type");

    assertThat(result).isEmpty();
  }

  @Test
  public void types_delegatesToLegacyTypes() {
    DescriptorTypeProvider legacyProvider = new DescriptorTypeProvider(DESCRIPTORS);
    TypeProviderLegacyImpl celTypeProvider = new TypeProviderLegacyImpl(legacyProvider);

    ImmutableCollection<CelType> types = celTypeProvider.types();

    assertThat(types).isNotEmpty();
    assertThat(types).hasSize(legacyProvider.types().size());
    assertThat(types.stream().map(CelType::name))
        .contains(TestAllTypes.getDescriptor().getFullName());
  }
}