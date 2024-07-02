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
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.errorprone.annotations.CheckReturnValue;
import java.util.Arrays;
import java.util.Optional;

/**
 * CelPolicyConfig represents the policy configuration object to extend the CEL's compilation and
 * runtime environment with.
 */
@AutoValue
public abstract class CelPolicyConfig {

  /** Config source. */
  public abstract CelPolicySource configSource();

  /** Name of the config. */
  public abstract String name();

  /**
   * An optional description of the config (example: location of the file containing the config
   * content).
   */
  public abstract String description();

  /**
   * Container name to use as the namespace for resolving CEL expression variables and functions.
   */
  public abstract String container();

  /**
   * Canonical extensions to enable in the environment, such as Optional, String and Math
   * extensions.
   */
  public abstract ImmutableSet<ExtensionConfig> extensions();

  /** New variable declarations to add in the compilation environment. */
  public abstract ImmutableSet<VariableDecl> variables();

  /** New function declarations to add in the compilation environment. */
  public abstract ImmutableSet<FunctionDecl> functions();

  /** Builder for {@link CelPolicyConfig}. */
  @AutoValue.Builder
  public abstract static class Builder {

    public abstract Builder setConfigSource(CelPolicySource value);

    public abstract Builder setName(String name);

    public abstract Builder setDescription(String description);

    public abstract Builder setContainer(String container);

    public abstract Builder setExtensions(ImmutableSet<ExtensionConfig> extensions);

    public abstract Builder setVariables(ImmutableSet<VariableDecl> variables);

    public abstract Builder setFunctions(ImmutableSet<FunctionDecl> functions);

    @CheckReturnValue
    public abstract CelPolicyConfig build();
  }

  /** Creates a new builder to construct a {@link CelPolicyConfig} instance. */
  public static Builder newBuilder() {
    return new AutoValue_CelPolicyConfig.Builder()
        .setName("")
        .setDescription("")
        .setContainer("")
        .setExtensions(ImmutableSet.of())
        .setVariables(ImmutableSet.of())
        .setFunctions(ImmutableSet.of());
  }

  /** Represents a policy variable declaration. */
  @AutoValue
  public abstract static class VariableDecl {

    /** Fully qualified variable name. */
    public abstract String name();

    /** The type of the variable. */
    public abstract TypeDecl type();

    /** Builder for {@link VariableDecl}. */
    @AutoValue.Builder
    public abstract static class Builder implements RequiredFieldsChecker {

      abstract Optional<String> name();

      abstract Optional<TypeDecl> type();

      public abstract Builder setName(String name);

      public abstract Builder setType(TypeDecl typeDecl);

      @Override
      public ImmutableList<RequiredField> requiredFields() {
        return ImmutableList.of(
            RequiredField.of("name", this::name), RequiredField.of("type", this::type));
      }

      /** Builds a new instance of {@link VariableDecl}. */
      public abstract VariableDecl build();
    }

    public static Builder newBuilder() {
      return new AutoValue_CelPolicyConfig_VariableDecl.Builder();
    }

    /** Creates a new builder to construct a {@link VariableDecl} instance. */
    public static VariableDecl create(String name, TypeDecl type) {
      return newBuilder().setName(name).setType(type).build();
    }
  }

  /** Represents a policy function declaration. */
  @AutoValue
  public abstract static class FunctionDecl {

    public abstract String name();

    public abstract ImmutableSet<OverloadDecl> overloads();

    /** Builder for {@link FunctionDecl}. */
    @AutoValue.Builder
    public abstract static class Builder implements RequiredFieldsChecker {

      abstract Optional<String> name();

      abstract Optional<ImmutableSet<OverloadDecl>> overloads();

      public abstract Builder setName(String name);

      public abstract Builder setOverloads(ImmutableSet<OverloadDecl> overloads);

      @Override
      public ImmutableList<RequiredField> requiredFields() {
        return ImmutableList.of(
            RequiredField.of("name", this::name), RequiredField.of("overloads", this::overloads));
      }

      /** Builds a new instance of {@link FunctionDecl}. */
      public abstract FunctionDecl build();
    }

    /** Creates a new builder to construct a {@link FunctionDecl} instance. */
    public static Builder newBuilder() {
      return new AutoValue_CelPolicyConfig_FunctionDecl.Builder();
    }

    /** Creates a new {@link FunctionDecl} with the provided function name and its overloads. */
    public static FunctionDecl create(String name, ImmutableSet<OverloadDecl> overloads) {
      return newBuilder().setName(name).setOverloads(overloads).build();
    }
  }

  /** Represents an overload declaraion on a policy function. */
  @AutoValue
  public abstract static class OverloadDecl {

    /**
     * A unique overload ID. Required. This should follow the typical naming convention used in CEL
     * (e.g: targetType_func_argType1_argType...)
     */
    public abstract String id();

    /** Target of the function overload if it's a receiver style (example: foo in `foo.f(...)`) */
    public abstract Optional<TypeDecl> target();

    /** List of function overload type values. */
    public abstract ImmutableList<TypeDecl> arguments();

    /** Return type of the overload. Required. */
    public abstract TypeDecl returnType();

    /** Builder for {@link OverloadDecl}. */
    @AutoValue.Builder
    public abstract static class Builder implements RequiredFieldsChecker {

      abstract Optional<String> id();

      abstract Optional<TypeDecl> returnType();

      public abstract Builder setId(String overloadId);

      public abstract Builder setTarget(TypeDecl target);

      // This should stay package-private to encourage add/set methods to be used instead.
      abstract ImmutableList.Builder<TypeDecl> argumentsBuilder();

      public abstract Builder setArguments(ImmutableList<TypeDecl> args);

      @CanIgnoreReturnValue
      public Builder addArguments(Iterable<TypeDecl> args) {
        this.argumentsBuilder().addAll(checkNotNull(args));
        return this;
      }

      @CanIgnoreReturnValue
      public Builder addArguments(TypeDecl... args) {
        return addArguments(Arrays.asList(args));
      }

      public abstract Builder setReturnType(TypeDecl returnType);

      @Override
      public ImmutableList<RequiredField> requiredFields() {
        return ImmutableList.of(
            RequiredField.of("id", this::id), RequiredField.of("return", this::returnType));
      }

      /** Builds a new instance of {@link OverloadDecl}. */
      @CheckReturnValue
      public abstract OverloadDecl build();
    }

    /** Creates a new builder to construct a {@link OverloadDecl} instance. */
    public static Builder newBuilder() {
      return new AutoValue_CelPolicyConfig_OverloadDecl.Builder().setArguments(ImmutableList.of());
    }
  }

  /**
   * Represents an abstract type declaration used to declare functions and variables in a policy.
   */
  @AutoValue
  public abstract static class TypeDecl {

    public abstract String name();

    public abstract ImmutableList<TypeDecl> params();

    public abstract Boolean isTypeParam();

    /** Builder for {@link TypeDecl}. */
    @AutoValue.Builder
    public abstract static class Builder implements RequiredFieldsChecker {

      abstract Optional<String> name();

      public abstract Builder setName(String name);

      // This should stay package-private to encourage add/set methods to be used instead.
      abstract ImmutableList.Builder<TypeDecl> paramsBuilder();

      public abstract Builder setParams(ImmutableList<TypeDecl> typeDecls);

      @CanIgnoreReturnValue
      public Builder addParams(TypeDecl... params) {
        return addParams(Arrays.asList(params));
      }

      @CanIgnoreReturnValue
      public Builder addParams(Iterable<TypeDecl> params) {
        this.paramsBuilder().addAll(checkNotNull(params));
        return this;
      }

      public abstract Builder setIsTypeParam(boolean isTypeParam);

      @Override
      public ImmutableList<RequiredField> requiredFields() {
        return ImmutableList.of(RequiredField.of("type_name", this::name));
      }

      @CheckReturnValue
      public abstract TypeDecl build();
    }

    /** Creates a new {@link TypeDecl} with the provided name. */
    public static TypeDecl create(String name) {
      return newBuilder().setName(name).build();
    }

    public static Builder newBuilder() {
      return new AutoValue_CelPolicyConfig_TypeDecl.Builder().setIsTypeParam(false);
    }
  }

  /**
   * Represents a configuration for a canonical CEL extension that can be enabled in the
   * environment.
   */
  @AutoValue
  public abstract static class ExtensionConfig {

    /** Name of the extension (ex: bindings, optional, math, etc).". */
    public abstract String name();

    /**
     * Version of the extension. Presently, this field is ignored as CEL-Java extensions are not
     * versioned.
     */
    public abstract Integer version();

    /** Builder for {@link ExtensionConfig}. */
    @AutoValue.Builder
    public abstract static class Builder implements RequiredFieldsChecker {

      abstract Optional<String> name();

      abstract Optional<Integer> version();

      public abstract Builder setName(String name);

      public abstract Builder setVersion(Integer version);

      @Override
      public ImmutableList<RequiredField> requiredFields() {
        return ImmutableList.of(RequiredField.of("name", this::name));
      }

      /** Builds a new instance of {@link ExtensionConfig}. */
      public abstract ExtensionConfig build();
    }

    /** Creates a new builder to construct a {@link ExtensionConfig} instance. */
    public static Builder newBuilder() {
      return new AutoValue_CelPolicyConfig_ExtensionConfig.Builder().setVersion(0);
    }

    /** Create a new extension config with the specified name and version set to 0. */
    public static ExtensionConfig of(String name) {
      return of(name, 0);
    }

    /** Create a new extension config with the specified name and version. */
    public static ExtensionConfig of(String name, int version) {
      return newBuilder().setName(name).setVersion(version).build();
    }
  }
}
