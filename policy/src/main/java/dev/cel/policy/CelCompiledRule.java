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
import dev.cel.common.CelVarDecl;

/**
 * Abstract representation of a compiled rule. This contains set of compiled variables and match
 * statements which defines an expression graph for a policy.
 */
@AutoValue
public abstract class CelCompiledRule {
  public abstract ImmutableList<CelCompiledVariable> variables();

  public abstract ImmutableList<CelCompiledMatch> matches();

  public abstract Cel cel();

  /**
   * A compiled policy variable (ex: variables.foo). Note that this is not the same thing as the
   * variables declared in the config.
   */
  @AutoValue
  public abstract static class CelCompiledVariable {
    public abstract String name();

    /** Compiled variable in AST. */
    public abstract CelAbstractSyntaxTree ast();

    /** The variable declaration used to compile this variable in {@link #ast}. */
    public abstract CelVarDecl celVarDecl();

    static CelCompiledVariable create(
        String name, CelAbstractSyntaxTree ast, CelVarDecl celVarDecl) {
      return new AutoValue_CelCompiledRule_CelCompiledVariable(name, ast, celVarDecl);
    }
  }

  /** A compiled Match. */
  @AutoValue
  public abstract static class CelCompiledMatch {
    public abstract CelAbstractSyntaxTree condition();

    public abstract Result result();

    /** Encapsulates the result of this match when condition is met. (either an output or a rule) */
    @AutoOneOf(CelCompiledMatch.Result.Kind.class)
    public abstract static class Result {
      public abstract CelAbstractSyntaxTree output();

      public abstract CelCompiledRule rule();

      public abstract Kind kind();

      static Result ofOutput(CelAbstractSyntaxTree value) {
        return AutoOneOf_CelCompiledRule_CelCompiledMatch_Result.output(value);
      }

      static Result ofRule(CelCompiledRule value) {
        return AutoOneOf_CelCompiledRule_CelCompiledMatch_Result.rule(value);
      }

      /** Kind for {@link Result}. */
      public enum Kind {
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
