// Copyright 2024 Google LLC
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

package codelab;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.testing.junit.testparameterinjector.TestParameterInjector;
import dev.cel.common.CelAbstractSyntaxTree;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(TestParameterInjector.class)
public final class Exercise7Test {
  private final Exercise7 exercise7 = new Exercise7();

  @Test
  public void evaluate_checkJwtClaimsWithMacro_evaluatesToTrue() {
    String expression =
        "jwt.extra_claims.exists(c, c.startsWith('group'))"
            + " && jwt.extra_claims"
            + ".filter(c, c.startsWith('group'))"
            + ".all(c, jwt.extra_claims[c]"
            + ".all(g, g.endsWith('@acme.co')))";
    ImmutableMap<String, Object> jwt =
        ImmutableMap.of(
            "sub",
            "serviceAccount:delegate@acme.co",
            "aud",
            "my-project",
            "iss",
            "auth.acme.com:12350",
            "extra_claims",
            ImmutableMap.of("group1", ImmutableList.of("admin@acme.co", "analyst@acme.co")),
            "labels",
            ImmutableList.of("metadata", "prod", "pii"),
            "groupN",
            ImmutableList.of("forever@acme.co"));
    CelAbstractSyntaxTree ast = exercise7.compile(expression);

    // Evaluate a complex-ish JWT with two groups that satisfy the criteria.
    // Output: true.
    boolean evaluatedResult = (boolean) exercise7.eval(ast, ImmutableMap.of("jwt", jwt));

    assertThat(evaluatedResult).isTrue();
  }
}
