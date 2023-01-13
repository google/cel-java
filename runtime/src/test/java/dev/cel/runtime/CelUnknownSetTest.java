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

import com.google.common.collect.ImmutableSet;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class CelUnknownSetTest {
  @Test
  public void unknownSet_creation() {
    assertThat(CelUnknownSet.create(CelAttribute.create("identifier")))
        .isEqualTo(CelUnknownSet.create(ImmutableSet.of(CelAttribute.create("identifier"))));

    assertThat(
            CelUnknownSet.create(CelAttribute.create("identifier"))
                .merge(CelUnknownSet.create(CelAttribute.create("identifier2")))
                .merge(CelUnknownSet.create(CelAttribute.create("identifier3"))))
        .isEqualTo(
            CelUnknownSet.create(
                ImmutableSet.of(
                    CelAttribute.create("identifier"),
                    CelAttribute.create("identifier2"),
                    CelAttribute.create("identifier3"))));

    assertThat(
            CelUnknownSet.union(
                CelUnknownSet.create(CelAttribute.create("identifier")),
                CelUnknownSet.create(CelAttribute.create("identifier2"))))
        .isEqualTo(
            CelUnknownSet.create(
                ImmutableSet.of(
                    CelAttribute.create("identifier"), CelAttribute.create("identifier2"))));
  }

  @Test
  public void unknownSet_maintainsSetSemantics() {
    assertThat(CelUnknownSet.create(CelAttribute.create("identifier")))
        .isEqualTo(CelUnknownSet.create(ImmutableSet.of(CelAttribute.create("identifier"))));

    assertThat(
            CelUnknownSet.create(CelAttribute.create("identifier"))
                .merge(
                    CelUnknownSet.create(
                        CelAttribute.create("identifier")
                            .qualify(CelAttribute.Qualifier.ofString("field1"))))
                .merge(
                    CelUnknownSet.create(
                        CelAttribute.create("identifier")
                            .qualify(CelAttribute.Qualifier.ofString("field2")))))
        .isEqualTo(
            CelUnknownSet.create(
                ImmutableSet.of(
                    CelAttribute.create("identifier"),
                    CelAttribute.create("identifier")
                        .qualify(CelAttribute.Qualifier.ofString("field1")),
                    CelAttribute.create("identifier")
                        .qualify(CelAttribute.Qualifier.ofString("field2")))));

    assertThat(
            CelUnknownSet.create(CelAttribute.create("identifier"))
                .merge(
                    CelUnknownSet.create(
                        CelAttribute.create("identifier")
                            .qualify(CelAttribute.Qualifier.ofString("field1"))))
                .merge(
                    CelUnknownSet.create(
                        CelAttribute.create("identifier")
                            .qualify(CelAttribute.Qualifier.ofString("field1")))))
        .isEqualTo(
            CelUnknownSet.create(
                ImmutableSet.of(
                    CelAttribute.create("identifier"),
                    CelAttribute.create("identifier")
                        .qualify(CelAttribute.Qualifier.ofString("field1")))));

    assertThat(
            CelUnknownSet.union(
                CelUnknownSet.create(CelAttribute.create("identifier")),
                CelUnknownSet.create(CelAttribute.create("identifier2"))))
        .isEqualTo(
            CelUnknownSet.create(
                ImmutableSet.of(
                    CelAttribute.create("identifier"), CelAttribute.create("identifier2"))));

    assertThat(
            CelUnknownSet.union(
                CelUnknownSet.create(CelAttribute.create("identifier")),
                CelUnknownSet.create(CelAttribute.create("identifier"))))
        .isEqualTo(CelUnknownSet.create(ImmutableSet.of(CelAttribute.create("identifier"))));
  }
}
