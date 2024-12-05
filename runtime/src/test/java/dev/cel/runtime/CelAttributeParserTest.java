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

package dev.cel.runtime;

import static com.google.common.truth.Truth.assertThat;

import dev.cel.runtime.CelAttribute.Qualifier;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class CelAttributeParserTest {

  @Test
  public void parse_parses() {
    assertThat(CelAttributeParser.parse("com.google.Attr"))
        .isEqualTo(
            CelAttribute.create("com")
                .qualify(Qualifier.ofString("google"))
                .qualify(Qualifier.ofString("Attr")));
  }

  @Test
  public void parse_parseIndexes() {
    assertThat(CelAttributeParser.parse("com.google.Attr[1]"))
        .isEqualTo(
            CelAttribute.create("com")
                .qualify(Qualifier.ofString("google"))
                .qualify(Qualifier.ofString("Attr"))
                .qualify(Qualifier.ofInt(1)));
    assertThat(CelAttributeParser.parse("com.google.Attr[1u]"))
        .isEqualTo(
            CelAttribute.create("com")
                .qualify(Qualifier.ofString("google"))
                .qualify(Qualifier.ofString("Attr"))
                .qualify(Qualifier.ofUint(1)));
    assertThat(CelAttributeParser.parse("com.google.Attr[false]"))
        .isEqualTo(
            CelAttribute.create("com")
                .qualify(Qualifier.ofString("google"))
                .qualify(Qualifier.ofString("Attr"))
                .qualify(Qualifier.ofBool(false)));
    assertThat(CelAttributeParser.parse("com.google.Attr['1']"))
        .isEqualTo(
            CelAttribute.create("com")
                .qualify(Qualifier.ofString("google"))
                .qualify(Qualifier.ofString("Attr"))
                .qualify(Qualifier.ofString("1")));
  }

  @Test
  public void parse_parsesWildCard() {
    assertThat(CelAttributeParser.parsePattern("com.google.Attr.*"))
        .isEqualTo(
            CelAttributePattern.create("com")
                .qualify(Qualifier.ofString("google"))
                .qualify(Qualifier.ofString("Attr"))
                .qualify(Qualifier.ofWildCard()));
  }

  @Test
  public void parse_preservesEscapeCollisions() {
    // Implementation detail: cel doesn't accept '*' as a legal field so parser escapes to
    // _wildcard. check that the escaping scheme doesn't lose information.
    assertThat(CelAttributeParser.parse("attr._wildcard"))
        .isEqualTo(CelAttribute.create("attr").qualify(Qualifier.ofString("_wildcard")));

    assertThat(CelAttributeParser.parse("attr._wildcar"))
        .isEqualTo(CelAttribute.create("attr").qualify(Qualifier.ofString("_wildcar")));

    assertThat(CelAttributeParser.parse("attr.__f"))
        .isEqualTo(CelAttribute.create("attr").qualify(Qualifier.ofString("__f")));

    assertThat(CelAttributeParser.parse("attr['*']"))
        .isEqualTo(CelAttribute.create("attr").qualify(Qualifier.ofString("*")));

    assertThat(CelAttributeParser.parse("attr['_wildcard']"))
        .isEqualTo(CelAttribute.create("attr").qualify(Qualifier.ofString("_wildcard")));

    assertThat(CelAttributeParser.parse("attr['__f']"))
        .isEqualTo(CelAttribute.create("attr").qualify(Qualifier.ofString("__f")));
  }

  @Test
  public void parse_unsupportedExprKindThrows() {
    IllegalArgumentException iae =
        Assert.assertThrows(
            IllegalArgumentException.class, () -> CelAttributeParser.parse("1 / 2"));

    assertThat(iae).hasMessageThat().contains("_/_(CONSTANT, CONSTANT)");

    iae =
        Assert.assertThrows(
            IllegalArgumentException.class, () -> CelAttributeParser.parse("123.field"));

    assertThat(iae).hasMessageThat().contains("CelConstant");

    iae =
        Assert.assertThrows(
            IllegalArgumentException.class, () -> CelAttributeParser.parse("a && b"));

    assertThat(iae).hasMessageThat().contains("_&&_(IDENT, IDENT)");
  }

  @Test
  public void parse_unsupportedConstThrows() {
    Assert.assertThrows(
        IllegalArgumentException.class, () -> CelAttributeParser.parse("attr[null]"));
    Assert.assertThrows(
        IllegalArgumentException.class, () -> CelAttributeParser.parse("attr[1.0]"));
  }

  @Test
  public void parse_badSyntaxThrows() {
    Assert.assertThrows(IllegalArgumentException.class, () -> CelAttributeParser.parse("}attr,"));
  }
}
