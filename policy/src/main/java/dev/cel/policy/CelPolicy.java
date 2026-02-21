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

import com.google.auto.value.AutoOneOf;
import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import dev.cel.common.formats.ValueString;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Abstract representation of a policy. It declares a name, rule, and evaluation semantic for a
 * given expression graph.
 */
@AutoValue
public abstract class CelPolicy {

  public abstract ValueString name();

  public abstract Optional<ValueString> description();

  public abstract Optional<ValueString> displayName();

  public abstract Rule rule();

  public abstract CelPolicySource policySource();

  public abstract ImmutableMap<String, Object> metadata();

  public abstract ImmutableList<Import> imports();

  /** Creates a new builder to construct a {@link CelPolicy} instance. */
  public static Builder newBuilder() {
    return new AutoValue_CelPolicy.Builder()
        .setName(ValueString.of(0, ""))
        .setRule(Rule.newBuilder(0).build())
        .setMetadata(ImmutableMap.of());
  }

  public abstract Builder toBuilder();

  /** Builder for {@link CelPolicy}. */
  @AutoValue.Builder
  public abstract static class Builder {
    public abstract CelPolicySource policySource();

    public abstract Builder setName(ValueString name);

    public abstract Builder setDescription(ValueString description);

    public abstract Builder setDisplayName(ValueString displayName);

    public abstract Builder setRule(Rule rule);

    public abstract Builder setPolicySource(CelPolicySource policySource);

    private final HashMap<String, Object> metadata = new HashMap<>();

    public abstract Builder setMetadata(ImmutableMap<String, Object> value);

    private final ArrayList<Import> importList = new ArrayList<>();

    abstract Builder setImports(ImmutableList<Import> value);

    public List<Import> imports() {
      return Collections.unmodifiableList(importList);
    }

    public Map<String, Object> metadata() {
      return Collections.unmodifiableMap(metadata);
    }

    @CanIgnoreReturnValue
    public Builder addImport(Import value) {
      importList.add(value);
      return this;
    }

    @CanIgnoreReturnValue
    public Builder addImports(Collection<Import> values) {
      importList.addAll(values);
      return this;
    }

    @CanIgnoreReturnValue
    public Builder putMetadata(String key, Object value) {
      metadata.put(key, value);
      return this;
    }

    @CanIgnoreReturnValue
    public Builder putMetadata(Map<String, Object> map) {
      metadata.putAll(map);
      return this;
    }

    abstract CelPolicy autoBuild();

    public CelPolicy build() {
      setImports(ImmutableList.copyOf(importList));
      return autoBuild();
    }
  }

  /**
   * Rule declares a rule identifier, description, along with a set of variables and match
   * statements.
   */
  @AutoValue
  public abstract static class Rule {
    public abstract long id();

    public abstract Optional<ValueString> ruleId();

    public abstract Optional<ValueString> description();

    public abstract ImmutableSet<Variable> variables();

    public abstract ImmutableSet<Match> matches();

    /** Builder for {@link Rule}. */
    public static Builder newBuilder(long id) {
      return new AutoValue_CelPolicy_Rule.Builder()
          .setId(id)
          .setVariables(ImmutableSet.of())
          .setMatches(ImmutableSet.of());
    }

    /** Creates a new builder to construct a {@link Rule} instance. */
    @AutoValue.Builder
    public abstract static class Builder {

      public abstract Rule.Builder setRuleId(ValueString id);

      public abstract Rule.Builder setDescription(ValueString description);

      abstract ImmutableSet<Variable> variables();

      abstract ImmutableSet.Builder<Variable> variablesBuilder();

      abstract ImmutableSet<Match> matches();

      abstract ImmutableSet.Builder<Match> matchesBuilder();

      abstract Builder setId(long value);

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

  /**
   * Match declares a condition (defaults to true) as well as an output or a rule. Either the output
   * or the rule field may be set, but not both.
   */
  @AutoValue
  public abstract static class Match {

    public abstract ValueString condition();

    public abstract Result result();

    public abstract long id();

    /** Explanation returns the explanation expression, or empty expression if output is not set. */
    public abstract Optional<ValueString> explanation();

    /** Encapsulates the result of this match when condition is met. (either an output or a rule) */
    @AutoOneOf(Match.Result.Kind.class)
    public abstract static class Result {
      public abstract ValueString output();

      public abstract Rule rule();

      public abstract Kind kind();

      public static Result ofOutput(ValueString value) {
        return AutoOneOf_CelPolicy_Match_Result.output(value);
      }

      public static Result ofRule(Rule value) {
        return AutoOneOf_CelPolicy_Match_Result.rule(value);
      }

      /** Kind for {@link Result}. */
      public enum Kind {
        OUTPUT,
        RULE
      }
    }

    /** Builder for {@link Match}. */
    @AutoValue.Builder
    public abstract static class Builder implements RequiredFieldsChecker {
      public abstract Builder setId(long value);

      public abstract Builder setCondition(ValueString condition);

      public abstract Builder setResult(Result result);

      public abstract Builder setExplanation(ValueString explanation);

      abstract Optional<Long> id();

      abstract Optional<Result> result();

      abstract Optional<ValueString> explanation();

      @Override
      public ImmutableList<RequiredField> requiredFields() {
        return ImmutableList.of(RequiredField.of("output or a rule", this::result));
      }

      public abstract Match build();
    }

    /** Creates a new builder to construct a {@link Match} instance. */
    public static Builder newBuilder(long id) {
      return new AutoValue_CelPolicy_Match.Builder().setId(id);
    }
  }

  /** Variable is a named expression which may be referenced in subsequent expressions. */
  @AutoValue
  public abstract static class Variable {

    public abstract ValueString name();

    public abstract ValueString expression();

    public abstract Optional<ValueString> description();

    public abstract Optional<ValueString> displayName();

    /** Builder for {@link Variable}. */
    @AutoValue.Builder
    public abstract static class Builder implements RequiredFieldsChecker {

      abstract Optional<ValueString> name();

      abstract Optional<ValueString> expression();

      abstract Optional<ValueString> description();

      abstract Optional<ValueString> displayName();

      public abstract Builder setName(ValueString name);

      public abstract Builder setExpression(ValueString expression);

      public abstract Builder setDescription(ValueString description);

      public abstract Builder setDisplayName(ValueString displayName);

      @Override
      public ImmutableList<RequiredField> requiredFields() {
        return ImmutableList.of(
            RequiredField.of("name", this::name), RequiredField.of("expression", this::expression));
      }

      public abstract Variable build();
    }

    /** Creates a new builder to construct a {@link Variable} instance. */
    public static Builder newBuilder() {
      return new AutoValue_CelPolicy_Variable.Builder();
    }
  }

  /** Import represents an imported type name which is aliased within CEL expressions. */
  @AutoValue
  public abstract static class Import {
    public abstract long id();

    public abstract ValueString name();

    public static Import create(long id, ValueString name) {
      return new AutoValue_CelPolicy_Import(id, name);
    }
  }
}
