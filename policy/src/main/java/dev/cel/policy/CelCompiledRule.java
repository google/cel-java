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

package dev.cel.policy;

import com.google.auto.value.AutoOneOf;
import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import dev.cel.bundle.Cel;
import dev.cel.common.CelAbstractSyntaxTree;

/** Abstract representation of a compiled rule. */
@AutoValue
abstract class CelCompiledRule {
  abstract ImmutableList<CelCompiledVariable> variables();

  abstract ImmutableList<CelCompiledMatch> matches();

  abstract Cel cel();

  @AutoValue
  abstract static class CelCompiledVariable {
    abstract String name();

    abstract CelAbstractSyntaxTree ast();

    static CelCompiledVariable create(String name, CelAbstractSyntaxTree ast) {
      return new AutoValue_CelCompiledRule_CelCompiledVariable(name, ast);
    }
  }

  @AutoValue
  abstract static class CelCompiledMatch {
    abstract CelAbstractSyntaxTree condition();

    abstract Result result();

    @AutoOneOf(CelCompiledMatch.Result.Kind.class)
    abstract static class Result {
      abstract CelAbstractSyntaxTree output();

      abstract CelCompiledRule rule();

      abstract Kind kind();

      static Result ofOutput(CelAbstractSyntaxTree value) {
        return AutoOneOf_CelCompiledRule_CelCompiledMatch_Result.output(value);
      }

      static Result ofRule(CelCompiledRule value) {
        return AutoOneOf_CelCompiledRule_CelCompiledMatch_Result.rule(value);
      }

      enum Kind {
        OUTPUT,
        RULE
      }
    }

    static CelCompiledMatch create(
        CelAbstractSyntaxTree condition, CelCompiledMatch.Result result) {
      return new AutoValue_CelCompiledRule_CelCompiledMatch(condition, result);
    }
  }

  static CelCompiledRule create(
      ImmutableList<CelCompiledVariable> variables,
      ImmutableList<CelCompiledMatch> matches,
      Cel cel) {
    return new AutoValue_CelCompiledRule(variables, matches, cel);
  }
}
