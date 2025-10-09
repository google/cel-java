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
  @TestParameters("{typeName: R, resolved: my.alias.R}")
  @TestParameters("{typeName: R.S.T, resolved: my.alias.R.S.T}")
  public void resolveCandidateName_withMatchingAbbreviation_resolvesSingleCandidate(
      String typeName, String resolved) {
    CelContainer container =
        CelContainer.newBuilder().setName("a.b.c").addAbbreviations("my.alias.R").build();

    ImmutableSet<String> resolvedNames = container.resolveCandidateNames(typeName);

    assertThat(resolvedNames).containsExactly(resolved);
  }

  @Test
  public void resolveCandidateName_withUnmatchedAbbreviation_resolvesMultipleCandidates() {
    CelContainer container =
        CelContainer.newBuilder().setName("a.b.c").addAbbreviations("my.alias.R").build();

    ImmutableSet<String> resolvedNames = container.resolveCandidateNames("S");

    assertThat(resolvedNames).containsExactly("a.b.c.S", "a.b.S", "a.S", "S").inOrder();
  }

  @Test
  public void containerBuilder_duplicateAliases_throws() {
    IllegalArgumentException e =
        assertThrows(
            IllegalArgumentException.class,
            () -> CelContainer.newBuilder().addAlias("foo", "a.b").addAlias("foo", "b.c"));
    assertThat(e)
        .hasMessageThat()
        .contains("alias collides with existing reference: name=b.c, alias=foo, existing=a.b");
  }

  @Test
  public void containerBuilder_aliasCollidesWithContainer_throws() {
    IllegalArgumentException e =
        assertThrows(
            IllegalArgumentException.class,
            () -> CelContainer.newBuilder().setName("foo").addAlias("foo", "a.b"));
    assertThat(e)
        .hasMessageThat()
        .contains("alias collides with container name: name=a.b, alias=foo, container=foo");
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

  @Test
  public void containerBuilder_addAbbreviationsError_throws(
      @TestParameter AbbreviationErrorTestCase testCase) {
    IllegalArgumentException e =
        assertThrows(
            IllegalArgumentException.class,
            () -> CelContainer.newBuilder().addAbbreviations(testCase.qualifiedNames));
    assertThat(e).hasMessageThat().contains(testCase.errorMessage);
  }

  private enum AbbreviationErrorTestCase {
    ABBREVIATION_COLLISION(
        ImmutableSet.of("my.alias.R", "yer.other.R"),
        "abbreviation collides with existing reference: name=yer.other.R, abbreviation=R,"
            + " existing=my.alias.R"),
    INVALID_DOT_PREFIX(
        ".bad", "invalid qualified name: .bad, wanted name of the form 'qualified.name'"),
    INVALID_DOT_SUFFIX(
        "bad.alias.",
        "invalid qualified name: bad.alias., wanted name of the form 'qualified.name'"),
    NO_QUALIFIER(
        "  bad_alias1  ",
        "invalid qualified name: bad_alias1, wanted name of the form 'qualified.name'"),
    INVALID_IDENTIFIER(
        "  bad.alias!",
        "invalid qualified name: bad.alias!, wanted name of the form 'qualified.name'"),
    ;

    private final ImmutableSet<String> qualifiedNames;
    private final String errorMessage;

    AbbreviationErrorTestCase(String qualifiedNames, String errorMessage) {
      this(ImmutableSet.of(qualifiedNames), errorMessage);
    }

    AbbreviationErrorTestCase(ImmutableSet<String> qualifiedNames, String errorMessage) {
      this.qualifiedNames = qualifiedNames;
      this.errorMessage = errorMessage;
    }
  }

  @Test
  public void containerBuilder_addAbbreviationsCollidesWithContainer_throws() {
    IllegalArgumentException e =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                CelContainer.newBuilder()
                    .setName("a.b.c.M.N")
                    .addAbbreviations("my.alias.a", "yer.other.b"));

    assertThat(e)
        .hasMessageThat()
        .contains(
            "abbreviation collides with container name: name=my.alias.a, abbreviation=a,"
                + " container=a.b.c.M.N");
  }

  @Test
  public void container_toBuilderRoundTrip_retainsExistingProperties() {
    CelContainer container =
        CelContainer.newBuilder().setName("hello").addAlias("foo", "x.y").build();

    container = container.toBuilder().addAlias("bar", "a.b").build();

    assertThat(container.name()).isEqualTo("hello");
    assertThat(container.aliases()).containsExactly("foo", "x.y", "bar", "a.b").inOrder();
  }
}
