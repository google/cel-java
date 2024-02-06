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

package dev.cel.runtime;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.util.Optional;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class UnknownContextTest {
  @Test
  public void create_variableResolverOnly() {
    UnknownContext context =
        UnknownContext.create(name -> name.equals("test") ? "test_value" : null);

    CelAttributeResolver attributeResolver = context.createAttributeResolver();

    assertThat(attributeResolver.resolve(CelAttribute.fromQualifiedIdentifier("test"))).isEmpty();
    assertThat(context.variableResolver().resolve("test")).isEqualTo("test_value");
  }

  @Test
  public void create_attributeResolverResolvesUnknown() {
    UnknownContext context =
        UnknownContext.create(
            unused -> Optional.empty(),
            ImmutableList.of(
                CelAttributePattern.fromQualifiedIdentifier("qualified.Identifier1"),
                CelAttributePattern.fromQualifiedIdentifier("qualified.Identifier2.field1")));

    CelAttributeResolver attributeResolver = context.createAttributeResolver();

    assertThat(
            attributeResolver.resolve(
                CelAttribute.fromQualifiedIdentifier("qualified.Identifier1")))
        .hasValue(
            CelUnknownSet.create(CelAttribute.fromQualifiedIdentifier("qualified.Identifier1")));
    assertThat(
            attributeResolver.resolve(
                CelAttribute.fromQualifiedIdentifier("qualified.Identifier2")))
        .isEmpty();
    assertThat(
            attributeResolver.resolve(
                CelAttribute.fromQualifiedIdentifier("qualified.Identifier2.field1")))
        .hasValue(
            CelUnknownSet.create(
                CelAttribute.fromQualifiedIdentifier("qualified.Identifier2.field1")));
  }

  @Test
  public void create_attributeResolverIdentifiesPartials() {
    UnknownContext context =
        UnknownContext.create(
            unused -> Optional.empty(),
            ImmutableList.of(
                CelAttributePattern.fromQualifiedIdentifier("qualified.Identifier1"),
                CelAttributePattern.fromQualifiedIdentifier("qualified.Identifier2.field1")));

    CelAttributeResolver attributeResolver = context.createAttributeResolver();

    assertThat(
            attributeResolver.maybePartialUnknown(
                CelAttribute.fromQualifiedIdentifier("qualified.Identifier1")))
        .hasValue(
            CelUnknownSet.create(CelAttribute.fromQualifiedIdentifier("qualified.Identifier1")));
    assertThat(
            attributeResolver.maybePartialUnknown(
                CelAttribute.fromQualifiedIdentifier("qualified.Identifier2")))
        .hasValue(
            CelUnknownSet.create(CelAttribute.fromQualifiedIdentifier("qualified.Identifier2")));
    assertThat(
            attributeResolver.maybePartialUnknown(
                CelAttribute.fromQualifiedIdentifier("qualified.Identifier2.field1")))
        .hasValue(
            CelUnknownSet.create(
                CelAttribute.fromQualifiedIdentifier("qualified.Identifier2.field1")));
  }

  @Test
  public void withResolvedAttributes_attributeResolverReturnsValue() {
    UnknownContext context =
        UnknownContext.create(
            unused -> Optional.empty(),
            ImmutableList.of(
                CelAttributePattern.fromQualifiedIdentifier("qualified.Identifier1"),
                CelAttributePattern.fromQualifiedIdentifier("qualified.Identifier2.field1")));

    CelAttributeResolver attributeResolver = context.createAttributeResolver();

    assertThat(
            attributeResolver.maybePartialUnknown(
                CelAttribute.fromQualifiedIdentifier("qualified.Identifier1")))
        .hasValue(
            CelUnknownSet.create(CelAttribute.fromQualifiedIdentifier("qualified.Identifier1")));
    assertThat(
            attributeResolver.maybePartialUnknown(
                CelAttribute.fromQualifiedIdentifier("qualified.Identifier2")))
        .hasValue(
            CelUnknownSet.create(CelAttribute.fromQualifiedIdentifier("qualified.Identifier2")));
    assertThat(
            attributeResolver.maybePartialUnknown(
                CelAttribute.fromQualifiedIdentifier("qualified.Identifier2.field1")))
        .hasValue(
            CelUnknownSet.create(
                CelAttribute.fromQualifiedIdentifier("qualified.Identifier2.field1")));
  }

  @Test
  public void withResolvedAttributes_attributeResolverResolves() {
    UnknownContext baseContext =
        UnknownContext.create(unused -> Optional.empty(), ImmutableList.of());

    UnknownContext context =
        baseContext.withResolvedAttributes(
            ImmutableMap.of(
                CelAttribute.fromQualifiedIdentifier("qualified.Identifier"), "value1"));

    CelAttributeResolver attributeResolver = context.createAttributeResolver();

    assertThat(
            attributeResolver.resolve(CelAttribute.fromQualifiedIdentifier("qualified.Identifier")))
        .hasValue("value1");
  }

  @Test
  public void withResolvedAttributes_attributeResolverPartialsShadowed() {
    UnknownContext baseContext =
        UnknownContext.create(
            unused -> Optional.empty(),
            ImmutableList.of(
                CelAttributePattern.fromQualifiedIdentifier("qualified.Identifier.field1"),
                CelAttributePattern.fromQualifiedIdentifier("qualified.Identifier.field2")));

    UnknownContext context =
        baseContext.withResolvedAttributes(
            ImmutableMap.of(
                CelAttribute.fromQualifiedIdentifier("qualified.Identifier"), "value1"));

    CelAttributeResolver attributeResolver = context.createAttributeResolver();

    assertThat(
            attributeResolver.resolve(CelAttribute.fromQualifiedIdentifier("qualified.Identifier")))
        .hasValue("value1");
    assertThat(
            attributeResolver.maybePartialUnknown(
                CelAttribute.fromQualifiedIdentifier("qualified.Identifier.field1")))
        .isEmpty();
    assertThat(
            attributeResolver.maybePartialUnknown(
                CelAttribute.fromQualifiedIdentifier("qualified.Identifier.field2")))
        .isEmpty();
  }
}
