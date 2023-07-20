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
import static com.google.common.truth.extensions.proto.ProtoTruth.assertThat;

import dev.cel.expr.Type;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.protobuf.Descriptors.Descriptor;
import dev.cel.common.types.CelTypes;
import dev.cel.common.types.ProtoMessageTypeProvider;
import dev.cel.testing.testdata.proto2.Proto2ExtensionScopedMessage;
import dev.cel.testing.testdata.proto2.Proto2Message;
import dev.cel.testing.testdata.proto2.TestAllTypesProto.TestAllTypes;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class TypeProviderLegacyImplTest {

  private static final ImmutableList<Descriptor> DESCRIPTORS =
      ImmutableList.of(
          TestAllTypes.getDescriptor(),
          Proto2Message.getDescriptor(),
          Proto2ExtensionScopedMessage.getDescriptor());

  private final ProtoMessageTypeProvider proto2Provider = new ProtoMessageTypeProvider(DESCRIPTORS);

  private final DescriptorTypeProvider descriptorTypeProvider =
      new DescriptorTypeProvider(DESCRIPTORS);

  private final TypeProviderLegacyImpl compatTypeProvider =
      new TypeProviderLegacyImpl(proto2Provider);

  @Test
  public void lookupType() {
    assertThat(compatTypeProvider.lookupType("google.api.expr.test.v1.proto2.TestAllTypes"))
        .isEqualTo(
            descriptorTypeProvider.lookupType("google.api.expr.test.v1.proto2.TestAllTypes"));
    assertThat(compatTypeProvider.lookupType("not.registered.TypeName"))
        .isEqualTo(descriptorTypeProvider.lookupType("not.registered.TypeName"));
  }

  @Test
  public void lookupFieldNames() {
    Type nestedTestAllTypes =
        compatTypeProvider
            .lookupType("dev.cel.testing.testdata.proto2.NestedTestAllTypes")
            .getType();
    ImmutableSet<String> fieldNames = compatTypeProvider.lookupFieldNames(nestedTestAllTypes);
    assertThat(fieldNames)
        .containsExactlyElementsIn(descriptorTypeProvider.lookupFieldNames(nestedTestAllTypes));
    assertThat(fieldNames).containsExactly("payload", "child");
  }

  @Test
  public void lookupFieldType() {
    Type nestedTestAllTypes =
        compatTypeProvider
            .lookupType("dev.cel.testing.testdata.proto2.NestedTestAllTypes")
            .getType();
    assertThat(compatTypeProvider.lookupFieldType(nestedTestAllTypes, "payload"))
        .isEqualTo(descriptorTypeProvider.lookupFieldType(nestedTestAllTypes, "payload"));
    assertThat(compatTypeProvider.lookupFieldType(nestedTestAllTypes, "child"))
        .isEqualTo(descriptorTypeProvider.lookupFieldType(nestedTestAllTypes, "child"));
  }

  @Test
  public void lookupFieldType_inputNotMessage() {
    Type globalEnumType =
        compatTypeProvider.lookupType("dev.cel.testing.testdata.proto2.GlobalEnum").getType();
    assertThat(compatTypeProvider.lookupFieldType(globalEnumType, "payload")).isNull();
    assertThat(compatTypeProvider.lookupFieldType(globalEnumType, "payload"))
        .isEqualTo(descriptorTypeProvider.lookupFieldType(globalEnumType, "payload"));
  }

  @Test
  public void lookupExtension() {
    TypeProvider.ExtensionFieldType extensionType =
        compatTypeProvider.lookupExtensionType("dev.cel.testing.testdata.proto2.nested_enum_ext");
    assertThat(extensionType.messageType())
        .isEqualTo(CelTypes.createMessage("dev.cel.testing.testdata.proto2.Proto2Message"));
    assertThat(extensionType.fieldType().type()).isEqualTo(CelTypes.INT64);
    assertThat(extensionType)
        .isEqualTo(
            descriptorTypeProvider.lookupExtensionType(
                "dev.cel.testing.testdata.proto2.nested_enum_ext"));
  }

  @Test
  public void lookupEnumValue() {
    Integer enumValue =
        compatTypeProvider.lookupEnumValue("dev.cel.testing.testdata.proto2.GlobalEnum.GAR");
    assertThat(enumValue).isEqualTo(1);
    assertThat(enumValue)
        .isEqualTo(
            descriptorTypeProvider.lookupEnumValue(
                "dev.cel.testing.testdata.proto2.GlobalEnum.GAR"));
  }

  @Test
  public void lookupEnumValue_notFoundValue() {
    Integer enumValue =
        compatTypeProvider.lookupEnumValue("dev.cel.testing.testdata.proto2.GlobalEnum.BAR");
    assertThat(enumValue).isNull();
    assertThat(enumValue)
        .isEqualTo(
            descriptorTypeProvider.lookupEnumValue(
                "dev.cel.testing.testdata.proto2.GlobalEnum.BAR"));
  }

  @Test
  public void lookupEnumValue_notFoundEnumType() {
    Integer enumValue =
        compatTypeProvider.lookupEnumValue("dev.cel.testing.testdata.proto2.InvalidEnum.TEST");
    assertThat(enumValue).isNull();
    assertThat(enumValue)
        .isEqualTo(
            descriptorTypeProvider.lookupEnumValue(
                "dev.cel.testing.testdata.proto2.InvalidEnum.TEST"));
  }

  @Test
  public void lookupEnumValue_notFoundBadEnumName() {
    assertThat(compatTypeProvider.lookupEnumValue("TEST")).isNull();
    assertThat(compatTypeProvider.lookupEnumValue("TEST.")).isNull();
    assertThat(descriptorTypeProvider.lookupEnumValue("TEST")).isNull();
    assertThat(descriptorTypeProvider.lookupEnumValue("TEST.")).isNull();
  }
}
