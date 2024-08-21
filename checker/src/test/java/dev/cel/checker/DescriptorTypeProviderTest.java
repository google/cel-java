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

package dev.cel.checker;

import static com.google.common.truth.Truth.assertThat;

import dev.cel.expr.Type;
import com.google.api.expr.test.v1.proto2.TestAllTypesExtensions;
import com.google.api.expr.test.v1.proto2.TestAllTypesProto.TestAllTypes;
import com.google.common.collect.ImmutableList;
import com.google.rpc.context.AttributeContext;
import dev.cel.checker.TypeProvider.CombinedTypeProvider;
import dev.cel.checker.TypeProvider.ExtensionFieldType;
import dev.cel.common.types.CelTypes;
import java.util.Arrays;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class DescriptorTypeProviderTest {

  @Test
  public void lookupFieldNames_nonMessageType() {
    TypeProvider typeProvider = new DescriptorTypeProvider();
    assertThat(typeProvider.lookupFieldNames(CelTypes.STRING)).isNull();
  }

  @Test
  public void lookupFieldNames_undeclaredMessageType() {
    TypeProvider typeProvider = new DescriptorTypeProvider();
    assertThat(
            typeProvider.lookupFieldNames(
                CelTypes.createMessage("google.rpc.context.AttributeContext")))
        .isNull();
  }

  @Test
  public void lookupFieldNames_groupTypeField() throws Exception {
    Type proto2MessageType = CelTypes.createMessage("google.api.expr.test.v1.proto2.TestAllTypes");
    TypeProvider typeProvider =
        new DescriptorTypeProvider(
            ImmutableList.of(
                TestAllTypes.getDescriptor().getFile(), TestAllTypesExtensions.getDescriptor()));
    assertThat(typeProvider.lookupFieldType(proto2MessageType, "nestedgroup").type())
        .isEqualTo(
            CelTypes.createMessage("google.api.expr.test.v1.proto2.TestAllTypes.NestedGroup"));
  }

  @Test
  public void lookupFieldNames_combinedProvider() {
    final TypeProvider typeProvider =
        new DescriptorTypeProvider(Arrays.asList(AttributeContext.getDescriptor().getFile()));
    TypeProvider partialTypeProvider = makePartialTypeProvider(typeProvider);
    TypeProvider combinedTypeProvider =
        new CombinedTypeProvider(Arrays.asList(typeProvider, partialTypeProvider));
    Type attrContextType = combinedTypeProvider.lookupType("google.rpc.context.AttributeContext");
    assertThat(attrContextType).isNotNull();
    assertThat(combinedTypeProvider.lookupFieldType(attrContextType.getType(), "resource"))
        .isNotNull();
    assertThat(combinedTypeProvider.lookupFieldNames(attrContextType.getType()))
        .containsExactly(
            "resource",
            "request",
            "api",
            "origin",
            "response",
            "destination",
            "source",
            "extensions");

    Assert.assertThrows(
        UnsupportedOperationException.class,
        () ->
            combinedTypeProvider.lookupFieldNames(
                Type.newBuilder().setMessageType("google.type.Expr").build()));
  }

  @Test
  public void lookupExtensionType_combinedProvider() {
    final TypeProvider configuredProvider =
        new DescriptorTypeProvider(
            ImmutableList.of(
                TestAllTypes.getDescriptor().getFile(), TestAllTypesExtensions.getDescriptor()));
    final TypeProvider partialProvider =
        // The partial provider has no extension lookup.
        makePartialTypeProvider(configuredProvider);
    final TypeProvider typeProvider =
        new CombinedTypeProvider(ImmutableList.of(partialProvider, configuredProvider));
    final Type messageType = CelTypes.createMessage("google.api.expr.test.v1.proto2.TestAllTypes");

    assertThat(typeProvider.lookupExtensionType("non.existent")).isNull();

    ExtensionFieldType nestedExt =
        typeProvider.lookupExtensionType("google.api.expr.test.v1.proto2.nested_ext");
    assertThat(nestedExt).isNotNull();
    assertThat(nestedExt.fieldType().type()).isEqualTo(messageType);
    assertThat(nestedExt.messageType()).isEqualTo(messageType);

    ExtensionFieldType int32Ext =
        typeProvider.lookupExtensionType("google.api.expr.test.v1.proto2.int32_ext");
    assertThat(int32Ext).isNotNull();
    assertThat(int32Ext.fieldType().type()).isEqualTo(CelTypes.INT64);
    assertThat(int32Ext.messageType()).isEqualTo(messageType);

    ExtensionFieldType repeatedExt =
        typeProvider.lookupExtensionType("google.api.expr.test.v1.proto2.repeated_test_all_types");
    assertThat(repeatedExt).isNotNull();
    assertThat(repeatedExt.fieldType().type())
        .isEqualTo(
            CelTypes.createList(
                CelTypes.createMessage("google.api.expr.test.v1.proto2.TestAllTypes")));
    assertThat(repeatedExt.messageType()).isEqualTo(messageType);

    // With leading dot '.'.
    assertThat(
            typeProvider.lookupExtensionType(
                ".google.api.expr.test.v1.proto2.repeated_test_all_types"))
        .isNotNull();
  }

  private static TypeProvider makePartialTypeProvider(TypeProvider typeProvider) {
    return new TypeProvider() {
      @Override
      public Type lookupType(String typeName) {
        return typeProvider.lookupType(typeName);
      }

      @Override
      public Integer lookupEnumValue(String enumName) {
        return typeProvider.lookupEnumValue(enumName);
      }

      @Override
      public FieldType lookupFieldType(Type type, String fieldName) {
        return typeProvider.lookupFieldType(type, fieldName);
      }
    };
  }
}
