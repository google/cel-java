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

package dev.cel.common.types;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.truth.Truth8;
import dev.cel.common.types.CelTypeProvider.CombinedCelTypeProvider;
import dev.cel.testing.testdata.proto2.MessagesProto2;
import dev.cel.testing.testdata.proto2.MessagesProto2Extensions;
import dev.cel.testing.testdata.proto3.TestAllTypesProto.TestAllTypes;
import java.util.Optional;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class ProtoMessageTypeProviderTest {

  private final ProtoMessageTypeProvider emptyProvider = new ProtoMessageTypeProvider();

  private final ProtoMessageTypeProvider proto3Provider =
      new ProtoMessageTypeProvider(ImmutableList.of(TestAllTypes.getDescriptor()));

  private final ProtoMessageTypeProvider proto2Provider =
      new ProtoMessageTypeProvider(
          ImmutableSet.of(
              MessagesProto2.getDescriptor(), MessagesProto2Extensions.getDescriptor()));

  @Test
  public void types_emptyTypeSet() {
    assertThat(emptyProvider.types()).isEmpty();
  }

  @Test
  public void findType_emptyTypeSet() {
    Truth8.assertThat(emptyProvider.findType("any")).isEmpty();
  }

  @Test
  public void types_allGlobalAndNestedDeclarations() {
    assertThat(proto3Provider.types().stream().map(CelType::name).collect(toImmutableList()))
        .containsAtLeast(
            "dev.cel.testing.testdata.proto3.GlobalEnum",
            "dev.cel.testing.testdata.proto3.TestAllTypes",
            "dev.cel.testing.testdata.proto3.TestAllTypes.NestedMessage",
            "dev.cel.testing.testdata.proto3.TestAllTypes.NestedEnum",
            "dev.cel.testing.testdata.proto3.NestedTestAllTypes");
  }

  @Test
  public void findType_globalEnumWithAllNamesAndNumbers() {
    Optional<CelType> celType =
        proto3Provider.findType("dev.cel.testing.testdata.proto3.GlobalEnum");
    Truth8.assertThat(celType).isPresent();
    assertThat(celType.get()).isInstanceOf(EnumType.class);
    EnumType enumType = (EnumType) celType.get();
    assertThat(enumType.name()).isEqualTo("dev.cel.testing.testdata.proto3.GlobalEnum");
    Truth8.assertThat(enumType.findNameByNumber(0)).hasValue("GOO");
    Truth8.assertThat(enumType.findNameByNumber(1)).hasValue("GAR");
    Truth8.assertThat(enumType.findNameByNumber(2)).hasValue("GAZ");
    Truth8.assertThat(enumType.findNameByNumber(3)).isEmpty();
  }

  @Test
  public void findType_nestedEnumWithAllNamesAndNumbers() {
    Optional<CelType> celType =
        proto3Provider.findType("dev.cel.testing.testdata.proto3.TestAllTypes.NestedEnum");
    Truth8.assertThat(celType).isPresent();
    assertThat(celType.get()).isInstanceOf(EnumType.class);
    EnumType enumType = (EnumType) celType.get();
    assertThat(enumType.name())
        .isEqualTo("dev.cel.testing.testdata.proto3.TestAllTypes.NestedEnum");
    Truth8.assertThat(enumType.findNumberByName("FOO")).hasValue(0);
    Truth8.assertThat(enumType.findNumberByName("BAR")).hasValue(1);
    Truth8.assertThat(enumType.findNumberByName("BAZ")).hasValue(2);
    Truth8.assertThat(enumType.findNumberByName("MISSING")).isEmpty();
  }

  @Test
  public void findType_globalMessageTypeNoExtensions() {
    Optional<CelType> celType =
        proto3Provider.findType("dev.cel.testing.testdata.proto3.NestedTestAllTypes");
    Truth8.assertThat(celType).isPresent();
    assertThat(celType.get()).isInstanceOf(ProtoMessageType.class);
    ProtoMessageType protoType = (ProtoMessageType) celType.get();
    assertThat(protoType.name()).isEqualTo("dev.cel.testing.testdata.proto3.NestedTestAllTypes");
    Truth8.assertThat(protoType.findField("payload")).isPresent();
    Truth8.assertThat(protoType.findField("child")).isPresent();
    Truth8.assertThat(protoType.findField("missing")).isEmpty();
    assertThat(protoType.fields()).hasSize(2);
    Truth8.assertThat(protoType.findExtension("dev.cel.testing.testdata.proto3.any")).isEmpty();
  }

  @Test
  public void findType_globalMessageWithExtensions() {
    Optional<CelType> celType =
        proto2Provider.findType("dev.cel.testing.testdata.proto2.Proto2Message");
    Truth8.assertThat(celType).isPresent();
    assertThat(celType.get()).isInstanceOf(ProtoMessageType.class);
    ProtoMessageType protoType = (ProtoMessageType) celType.get();
    assertThat(protoType.name()).isEqualTo("dev.cel.testing.testdata.proto2.Proto2Message");
    Truth8.assertThat(protoType.findField("single_int32")).isPresent();
    Truth8.assertThat(protoType.findField("single_enum")).isPresent();
    Truth8.assertThat(protoType.findField("single_nested_test_all_types")).isPresent();
    Truth8.assertThat(protoType.findField("nestedgroup")).isPresent();
    Truth8.assertThat(protoType.findField("nested_ext")).isEmpty();

    Truth8.assertThat(protoType.findExtension("dev.cel.testing.testdata.proto2.nested_ext"))
        .isPresent();
    Truth8.assertThat(protoType.findExtension("dev.cel.testing.testdata.proto2.int32_ext"))
        .isPresent();
    Truth8.assertThat(protoType.findExtension("dev.cel.testing.testdata.proto2.test_all_types_ext"))
        .isPresent();
    Truth8.assertThat(protoType.findExtension("dev.cel.testing.testdata.proto2.nested_enum_ext"))
        .isPresent();
    Truth8.assertThat(
            protoType.findExtension("dev.cel.testing.testdata.proto2.repeated_string_holder_ext"))
        .isPresent();

    Truth8.assertThat(
            protoType.findExtension("dev.cel.testing.testdata.proto2.Proto2Message.int32_ext"))
        .isEmpty();

    Optional<CelType> holderType =
        proto2Provider.findType("dev.cel.testing.testdata.proto2.StringHolder");
    Truth8.assertThat(holderType).isPresent();
    ProtoMessageType stringHolderType = (ProtoMessageType) holderType.get();
    Truth8.assertThat(
            stringHolderType.findExtension("dev.cel.testing.testdata.proto2.nested_enum_ext"))
        .isEmpty();
  }

  @Test
  public void findType_scopedMessageWithExtensions() {
    Optional<CelType> celType =
        proto2Provider.findType("dev.cel.testing.testdata.proto2.Proto2Message");
    Truth8.assertThat(celType).isPresent();
    assertThat(celType.get()).isInstanceOf(ProtoMessageType.class);
    ProtoMessageType protoType = (ProtoMessageType) celType.get();

    Truth8.assertThat(
            protoType.findExtension(
                "dev.cel.testing.testdata.proto2.Proto2ExtensionScopedMessage.message_scoped_nested_ext"))
        .isPresent();
    Truth8.assertThat(
            protoType.findExtension(
                "dev.cel.testing.testdata.proto2.Proto2ExtensionScopedMessage.int64_ext"))
        .isPresent();
    Truth8.assertThat(
            protoType.findExtension(
                "dev.cel.testing.testdata.proto2.Proto2ExtensionScopedMessage.string_ext"))
        .isPresent();

    Truth8.assertThat(
            protoType.findExtension(
                "dev.cel.testing.testdata.proto2.Proto2ExtensionScopedMessage.nested_message_inside_ext"))
        .isPresent();
  }

  @Test
  public void findType_withRepeatedEnumField() {
    Optional<CelType> celType =
        proto3Provider.findType("dev.cel.testing.testdata.proto3.TestAllTypes");
    Truth8.assertThat(celType).isPresent();
    assertThat(celType.get()).isInstanceOf(ProtoMessageType.class);
    ProtoMessageType protoType = (ProtoMessageType) celType.get();
    assertThat(protoType.name()).isEqualTo("dev.cel.testing.testdata.proto3.TestAllTypes");
    Truth8.assertThat(protoType.findField("repeated_nested_enum")).isPresent();

    CelType fieldType = protoType.findField("repeated_nested_enum").get().type();
    assertThat(fieldType.kind()).isEqualTo(CelKind.LIST);
    assertThat(fieldType.parameters()).hasSize(1);
    CelType elemType = fieldType.parameters().get(0);
    assertThat(elemType.name())
        .isEqualTo("dev.cel.testing.testdata.proto3.TestAllTypes.NestedEnum");
    assertThat(elemType.kind()).isEqualTo(CelKind.INT);
    assertThat(elemType).isInstanceOf(EnumType.class);
    Truth8.assertThat(
            proto3Provider.findType("dev.cel.testing.testdata.proto3.TestAllTypes.NestedEnum"))
        .hasValue(elemType);
  }

  @Test
  public void findType_withOneofField() {
    Optional<CelType> celType =
        proto3Provider.findType("dev.cel.testing.testdata.proto3.TestAllTypes");
    ProtoMessageType protoType = (ProtoMessageType) celType.get();
    assertThat(protoType.name()).isEqualTo("dev.cel.testing.testdata.proto3.TestAllTypes");
    Truth8.assertThat(protoType.findField("single_nested_message").map(f -> f.type().name()))
        .hasValue("dev.cel.testing.testdata.proto3.TestAllTypes.NestedMessage");
  }

  @Test
  public void findType_withMapField() {
    Optional<CelType> celType =
        proto3Provider.findType("dev.cel.testing.testdata.proto3.TestAllTypes");
    ProtoMessageType protoType = (ProtoMessageType) celType.get();
    CelType fieldType = protoType.findField("map_int64_nested_type").get().type();

    assertThat(fieldType.kind()).isEqualTo(CelKind.MAP);
    assertThat(fieldType.parameters()).hasSize(2);
    CelType keyType = fieldType.parameters().get(0);
    CelType valueType = fieldType.parameters().get(1);
    assertThat(keyType.name()).isEqualTo("int");
    assertThat(keyType.kind()).isEqualTo(CelKind.INT);
    assertThat(valueType.name()).isEqualTo("dev.cel.testing.testdata.proto3.NestedTestAllTypes");
    assertThat(valueType.kind()).isEqualTo(CelKind.STRUCT);
  }

  @Test
  public void findType_withWellKnownTypes() {
    Optional<CelType> celType =
        proto3Provider.findType("dev.cel.testing.testdata.proto3.TestAllTypes");
    ProtoMessageType protoType = (ProtoMessageType) celType.get();
    Truth8.assertThat(protoType.findField("single_any").map(f -> f.type()))
        .hasValue(SimpleType.ANY);
    Truth8.assertThat(protoType.findField("single_duration").map(f -> f.type()))
        .hasValue(SimpleType.DURATION);
    Truth8.assertThat(protoType.findField("single_timestamp").map(f -> f.type()))
        .hasValue(SimpleType.TIMESTAMP);
    Truth8.assertThat(protoType.findField("single_value").map(f -> f.type()))
        .hasValue(SimpleType.DYN);
    Truth8.assertThat(protoType.findField("single_list_value").map(f -> f.type()))
        .hasValue(ListType.create(SimpleType.DYN));
    Truth8.assertThat(protoType.findField("single_struct").map(f -> f.type()))
        .hasValue(MapType.create(SimpleType.STRING, SimpleType.DYN));
    Truth8.assertThat(protoType.findField("single_bool_wrapper").map(f -> f.type()))
        .hasValue(NullableType.create(SimpleType.BOOL));
    Truth8.assertThat(protoType.findField("single_bytes_wrapper").map(f -> f.type()))
        .hasValue(NullableType.create(SimpleType.BYTES));
    Truth8.assertThat(protoType.findField("single_int32_wrapper").map(f -> f.type()))
        .hasValue(NullableType.create(SimpleType.INT));
    Truth8.assertThat(protoType.findField("single_int64_wrapper").map(f -> f.type()))
        .hasValue(NullableType.create(SimpleType.INT));
    Truth8.assertThat(protoType.findField("single_double_wrapper").map(f -> f.type()))
        .hasValue(NullableType.create(SimpleType.DOUBLE));
    Truth8.assertThat(protoType.findField("single_float_wrapper").map(f -> f.type()))
        .hasValue(NullableType.create(SimpleType.DOUBLE));
    Truth8.assertThat(protoType.findField("single_string_wrapper").map(f -> f.type()))
        .hasValue(NullableType.create(SimpleType.STRING));
    Truth8.assertThat(protoType.findField("single_uint32_wrapper").map(f -> f.type()))
        .hasValue(NullableType.create(SimpleType.UINT));
    Truth8.assertThat(protoType.findField("single_uint64_wrapper").map(f -> f.type()))
        .hasValue(NullableType.create(SimpleType.UINT));
  }

  @Test
  public void types_combinedEmptyProviderIsEmpty() {
    CombinedCelTypeProvider combined = new CombinedCelTypeProvider(emptyProvider, emptyProvider);
    assertThat(combined.types()).isEmpty();
  }

  @Test
  public void types_combinedDuplicateProviderIsSameAsFirst() {
    CombinedCelTypeProvider combined = new CombinedCelTypeProvider(proto3Provider, proto3Provider);
    assertThat(combined.types()).hasSize(proto3Provider.types().size());
  }
}
