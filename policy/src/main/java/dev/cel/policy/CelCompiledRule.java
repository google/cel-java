package dev.cel.policy;

import com.google.auto.value.AutoOneOf;
import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import dev.cel.bundle.Cel;
import dev.cel.common.CelAbstractSyntaxTree;

@AutoValue
abstract class CelCompiledRule {
  abstract ImmutableList<CelCompiledVariable> variables();
  abstract ImmutableList<CelCompiledMatch> matches();
  abstract Cel cel();

  @AutoValue
  static abstract class CelCompiledVariable {
    abstract String name();
    abstract CelAbstractSyntaxTree ast();

    static CelCompiledVariable create(String name, CelAbstractSyntaxTree ast) {
      return new AutoValue_CelCompiledRule_CelCompiledVariable(name, ast);
    }
  }

  @AutoValue
  static abstract class CelCompiledMatch {
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

    static CelCompiledMatch create(CelAbstractSyntaxTree condition, CelCompiledMatch.Result result) {
      return new AutoValue_CelCompiledRule_CelCompiledMatch(condition, result);
    }
  }

  static CelCompiledRule create(ImmutableList<CelCompiledVariable> variables, ImmutableList<CelCompiledMatch> matches, Cel cel) {
    return new AutoValue_CelCompiledRule(variables, matches, cel);
  }
}
