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

package dev.cel.common;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import com.google.common.collect.ImmutableSet;
import com.google.testing.junit.testparameterinjector.TestParameter;
import com.google.testing.junit.testparameterinjector.TestParameterInjector;
import com.google.testing.junit.testparameterinjector.TestParameters;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(TestParameterInjector.class)
public class CelContainerTest {

  @Test
  public void resolveCandidateName_singleContainer_resolvesAllCandidates() {
    CelContainer container = CelContainer.ofName("a.b.c.M.N");

    ImmutableSet<String> resolvedNames = container.resolveCandidateNames("R.s");

    assertThat(resolvedNames)
        .containsExactly("a.b.c.M.N.R.s", "a.b.c.M.R.s", "a.b.c.R.s", "a.b.R.s", "a.R.s", "R.s")
        .inOrder();
  }

  @Test
  public void resolveCandidateName_fullyQualifiedTypeName_resolveSingleCandidate() {
    CelContainer container = CelContainer.ofName("a.b.c.M.N");

    ImmutableSet<String> resolvedNames = container.resolveCandidateNames(".R.s");

    assertThat(resolvedNames).containsExactly("R.s");
  }

  @Test
  @TestParameters("{typeName: bigex, resolved: my.example.pkg.verbose}")
  @TestParameters("{typeName: .bigex, resolved: my.example.pkg.verbose}")
  @TestParameters("{typeName: bigex.Execute, resolved: my.example.pkg.verbose.Execute}")
  @TestParameters("{typeName: .bigex.Execute, resolved: my.example.pkg.verbose.Execute}")
  public void resolveCandidateName_withAlias_resolvesSingleCandidate(
      String typeName, String resolved) {
    CelContainer container =
        CelContainer.newBuilder()
            .setName("a.b.c") // Note: alias takes precedence
            .addAlias("bigex", "my.example.pkg.verbose")
            .build();

    ImmutableSet<String> resolvedNames = container.resolveCandidateNames(typeName);

    assertThat(resolvedNames).containsExactly(resolved);
  }

  @Test
  public void containerBuilder_aliasCollides_throws() {
    IllegalArgumentException e =
        assertThrows(
            IllegalArgumentException.class,
            () -> CelContainer.newBuilder().addAlias("foo", "a.b").addAlias("foo", "b.c"));
    assertThat(e)
        .hasMessageThat()
        .contains("alias collides with existing reference: name=b.c, alias=foo, existing=a.b");
  }

  @Test
  public void containerBuilder_addAliasError_throws(@TestParameter AliasingErrorTestCase testCase) {
    IllegalArgumentException e =
        assertThrows(
            IllegalArgumentException.class,
            () -> CelContainer.newBuilder().addAlias(testCase.alias, testCase.qualifiedName));
    assertThat(e).hasMessageThat().contains(testCase.errorMessage);
  }

  private enum AliasingErrorTestCase {
    BAD_QUALIFIED_NAME(
        "foo",
        "invalid_qualified_name",
        "alias must refer to a valid qualified name: invalid_qualified_name"),
    BAD_QUALIFIED_NAME_2(
        "foo", ".bad.name", "qualified name must not begin with a leading '.': .bad.name"),
    BAD_ALIAS_NAME_1(
        "bad.alias", "b.c", "alias must be non-empty and simple (not qualified): alias=bad.alias"),
    BAD_ALIAS_NAME_2("", "b.c", "alias must be non-empty and simple (not qualified): alias=");

    private final String alias;
    private final String qualifiedName;
    private final String errorMessage;

    AliasingErrorTestCase(String alias, String qualifiedName, String errorMessage) {
      this.alias = alias;
      this.qualifiedName = qualifiedName;
      this.errorMessage = errorMessage;
    }
  }
}
