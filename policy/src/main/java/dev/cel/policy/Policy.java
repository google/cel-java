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

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import dev.cel.common.CelSource;

@AutoValue
public abstract class Policy {

  abstract ValueString name();

  abstract Rule rule();

  abstract CelSource celSource();

  abstract PolicySource policySource();

  public static Builder newBuilder() {
    return new AutoValue_Policy.Builder();
  }

  @AutoValue.Builder
  public abstract static class Builder {

    public abstract Builder setName(ValueString name);

    public abstract Builder setRule(Rule rule);

    public abstract Builder setCelSource(CelSource celSource);

    public abstract Builder setPolicySource(PolicySource policySource);

    public abstract Policy build();
  }

  @AutoValue
  abstract static class Rule {

    abstract ValueString id();

    abstract ValueString description();

    abstract ImmutableList<Variable> variables();

    abstract ImmutableList<Match> matches();
  }

  @AutoValue
  abstract static class Match {

    abstract ValueString condition();

    abstract ValueString output();

    abstract Rule rule();
  }

  @AutoValue
  abstract static class Variable {

    abstract Variable name();

    abstract Variable expression();
  }

  @AutoValue
  abstract static class ValueString {

    abstract long id();

    abstract String value();

    static ValueString of(long id, String value) {
      return new AutoValue_Policy_ValueString(id, value);
    }
  }
}