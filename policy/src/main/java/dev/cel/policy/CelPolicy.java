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

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableSet;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import dev.cel.common.CelSource;
import java.util.Arrays;
import java.util.Optional;

@AutoValue
public abstract class CelPolicy {

  abstract ValueString name();

  abstract Rule rule();

  abstract CelSource celSource();

  abstract CelPolicySource policySource();

  public static Builder newBuilder() {
    return new AutoValue_CelPolicy.Builder()
        .setName(ValueString.of(0, ""))
        .setRule(Rule.newBuilder().build());
  }

  // public static Builder newBuilder(CelPolicySource policySource) {
  //   return new AutoValue_CelPolicy.Builder()
  //       .setName(ValueString.of(0, ""))
  //       .setPolicySource(policySource)
  //       .setRule(Rule.newBuilder().build());
  // }

  @AutoValue.Builder
  public abstract static class Builder {

    public abstract Builder setName(ValueString name);

    public abstract Builder setRule(Rule rule);

    public abstract Builder setCelSource(CelSource celSource);

    public abstract Builder setPolicySource(CelPolicySource policySource);

    public abstract CelPolicy build();
  }

  @AutoValue
  public abstract static class Rule {

    abstract Optional<ValueString> id();

    abstract Optional<ValueString> description();

    abstract ImmutableSet<Variable> variables();

    abstract ImmutableSet<Match> matches();

    public static Builder newBuilder() {
      return new AutoValue_CelPolicy_Rule.Builder()
          .setVariables(ImmutableSet.of())
          .setMatches(ImmutableSet.of());
    }

    @AutoValue.Builder
    public abstract static class Builder {

      public abstract Rule.Builder setId(ValueString id);

      public abstract Rule.Builder setDescription(ValueString description);

      public abstract ImmutableSet<Variable> variables();

      public abstract ImmutableSet.Builder<Variable> variablesBuilder();

      public abstract ImmutableSet<Match> matches();

      public abstract ImmutableSet.Builder<Match> matchesBuilder();

      @CanIgnoreReturnValue
      public Builder addVariables(Variable... variables) {
        return addVariables(Arrays.asList(variables));
      }

      @CanIgnoreReturnValue
      public Builder addVariables(Iterable<Variable> variables) {
        this.variablesBuilder().addAll(checkNotNull(variables));
        return this;
      }

      @CanIgnoreReturnValue
      public Builder addMatches(Match... matches) {
        return addMatches(Arrays.asList(matches));
      }

      @CanIgnoreReturnValue
      public Builder addMatches(Iterable<Match> matches) {
        this.matchesBuilder().addAll(checkNotNull(matches));
        return this;
      }

      abstract Rule.Builder setVariables(ImmutableSet<Variable> variables);

      abstract Rule.Builder setMatches(ImmutableSet<Match> matches);

      public abstract Rule build();
    }
  }

  @AutoValue
  abstract static class Match {

    abstract ValueString condition();

    abstract ValueString output();

    abstract Rule rule();


    @AutoValue.Builder
    abstract static class Builder {

      abstract Builder setCondition(ValueString condition);

      abstract Builder setOutput(ValueString output);

      abstract Builder setRule(Rule rule);

      abstract Match build();
    }

    static Builder newBuilder() {
      return new AutoValue_CelPolicy_Match.Builder();
    }

  }

  @AutoValue
  abstract static class Variable {

    abstract ValueString name();

    abstract ValueString expression();

    @AutoValue.Builder
    abstract static class Builder {

      abstract Builder setName(ValueString name);

      abstract Builder setExpression(ValueString expression);

      abstract Variable build();
    }

    static Builder newBuilder() {
      return new AutoValue_CelPolicy_Variable.Builder()
          .setName(ValueString.newBuilder().build())
          .setExpression(ValueString.newBuilder().build());
    }
  }

  @AutoValue
  abstract static class ValueString {

    abstract long id();

    abstract String value();


    @AutoValue.Builder
    abstract static class Builder {

      abstract Builder setId(long id);

      abstract Builder setValue(String value);

      abstract ValueString build();
    }

    static Builder newBuilder() {
      return new AutoValue_CelPolicy_ValueString.Builder().setId(0).setValue("");
    }

    static ValueString of(long id, String value) {
      return newBuilder().setId(id).setValue(value).build();
    }
  }
}