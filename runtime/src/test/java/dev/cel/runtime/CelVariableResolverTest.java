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
import static dev.cel.runtime.CelVariableResolver.hierarchicalVariableResolver;

import com.google.common.collect.ImmutableMap;
import java.util.Optional;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class CelVariableResolverTest {

  @Test
  public void hierarchicalVariableResolvers_findsValueInPrimary() {
    ImmutableMap<String, String> primary = ImmutableMap.of("revision", "a_prime");
    ImmutableMap<String, String> secondary = ImmutableMap.of("revision", "a");
    CelVariableResolver resolver =
        hierarchicalVariableResolver(
            (name) -> Optional.ofNullable(primary.get(name)),
            (name) -> Optional.ofNullable(secondary.get(name)));
    assertThat(resolver.find("revision")).hasValue("a_prime");
  }

  @Test
  public void hierarchicalVariableResolvers_findsValueInSecondary() {
    ImmutableMap<String, String> primary = ImmutableMap.of("current", "descendant");
    ImmutableMap<String, String> secondary = ImmutableMap.of("previous", "ancestor");
    CelVariableResolver resolver =
        hierarchicalVariableResolver(
            (name) -> Optional.ofNullable(primary.get(name)),
            (name) -> Optional.ofNullable(secondary.get(name)));
    assertThat(resolver.find("previous")).hasValue("ancestor");
    assertThat(resolver.find("current")).hasValue("descendant");
  }

  @Test
  public void hierarchicalVariableResolvers_notFound() {
    ImmutableMap<Object, Object> primary = ImmutableMap.of();
    ImmutableMap<Object, Object> secondary = ImmutableMap.of();
    CelVariableResolver resolver =
        hierarchicalVariableResolver(
            (name) -> Optional.ofNullable(primary.get(name)),
            (name) -> Optional.ofNullable(secondary.get(name)));
    assertThat(resolver.find("anything")).isEmpty();
  }
}
