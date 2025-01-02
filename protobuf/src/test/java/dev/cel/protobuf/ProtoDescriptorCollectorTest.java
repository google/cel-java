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
import dev.cel.expr.conformance.proto3.TestAllTypes;
import dev.cel.protobuf.CelLiteDescriptor.MessageDescriptor;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(TestParameterInjector.class)
public class ProtoDescriptorCollectorTest {

  private static final ProtoDescriptorCollector COLLECTOR = new ProtoDescriptorCollector();

  @Test
  public void collectMessageInfo() throws Exception {
    ImmutableList<MessageDescriptor> messageInfoList =
        COLLECTOR.collectMessageInfo(TestAllTypes.getDescriptor().getFile());

    assertThat(messageInfoList.size()).isEqualTo(3);
    assertThat(messageInfoList.get(0).getFullyQualifiedProtoName())
        .isEqualTo("cel.expr.conformance.proto3.TestAllTypes");
    assertThat(messageInfoList.get(0).getFullyQualifiedProtoJavaClassName())
        .isEqualTo("dev.cel.expr.conformance.proto3.TestAllTypes");
    assertThat(messageInfoList.get(1).getFullyQualifiedProtoName())
        .isEqualTo("cel.expr.conformance.proto3.TestAllTypes.NestedMessage");
    assertThat(messageInfoList.get(1).getFullyQualifiedProtoJavaClassName())
        .isEqualTo("dev.cel.expr.conformance.proto3.TestAllTypes$NestedMessage");
    assertThat(messageInfoList.get(2).getFullyQualifiedProtoName())
        .isEqualTo("cel.expr.conformance.proto3.NestedTestAllTypes");
    assertThat(messageInfoList.get(2).getFullyQualifiedProtoJavaClassName())
        .isEqualTo("dev.cel.expr.conformance.proto3.NestedTestAllTypes");
  }
}
