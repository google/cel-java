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

import com.google.common.collect.ImmutableList;
import com.google.testing.junit.testparameterinjector.TestParameterInjector;
import dev.cel.expr.conformance.proto3.NestedTestAllTypes;
import dev.cel.expr.conformance.proto3.TestAllTypes;
import dev.cel.testing.testdata.MultiFile;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(TestParameterInjector.class)
public class ProtoDescriptorCollectorTest {

  @Test
  public void collectCodegenMetadata_containsAllDescriptors() {
    ProtoDescriptorCollector collector =
        ProtoDescriptorCollector.newInstance(DebugPrinter.newInstance(false));

    ImmutableList<LiteDescriptorCodegenMetadata> testAllTypesDescriptors =
        collector.collectCodegenMetadata(TestAllTypes.getDescriptor());
    ImmutableList<LiteDescriptorCodegenMetadata> nestedTestAllTypesDescriptors =
        collector.collectCodegenMetadata(NestedTestAllTypes.getDescriptor());

    // All proto messages, including transitive ones + maps
    assertThat(testAllTypesDescriptors).hasSize(165);
    assertThat(nestedTestAllTypesDescriptors).hasSize(1);
  }

  @Test
  public void collectCodegenMetadata_withProtoDependencies_containsAllDescriptors() {
    ProtoDescriptorCollector collector =
        ProtoDescriptorCollector.newInstance(DebugPrinter.newInstance(false));

    ImmutableList<LiteDescriptorCodegenMetadata> descriptors =
        collector.collectCodegenMetadata(MultiFile.getDescriptor());

    assertThat(descriptors).hasSize(3);
    assertThat(
            descriptors.stream()
                .filter(d -> d.getProtoTypeName().equals("dev.cel.testing.testdata.MultiFile"))
                .findAny())
        .isPresent();
  }

  @Test
  public void collectCodegenMetadata_withProtoDependencies_doesNotContainImportedProto() {
    ProtoDescriptorCollector collector =
        ProtoDescriptorCollector.newInstance(DebugPrinter.newInstance(false));

    ImmutableList<LiteDescriptorCodegenMetadata> descriptors =
        collector.collectCodegenMetadata(MultiFile.getDescriptor());

    assertThat(
            descriptors.stream()
                .filter(d -> d.getProtoTypeName().equals("dev.cel.testing.testdata.SingleFile"))
                .findAny())
        .isEmpty();
  }
}
