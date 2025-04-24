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

package dev.cel.bundle;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.auto.value.AutoValue;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.errorprone.annotations.CheckReturnValue;
import dev.cel.common.CelFunctionDecl;
import dev.cel.common.CelOptions;
import dev.cel.common.CelOverloadDecl;
import dev.cel.common.CelVarDecl;
import dev.cel.common.Source;
import dev.cel.common.types.CelType;
import dev.cel.common.types.CelTypeProvider;
import dev.cel.common.types.ListType;
import dev.cel.common.types.MapType;
import dev.cel.common.types.OptionalType;
import dev.cel.common.types.SimpleType;
import dev.cel.common.types.TypeParamType;
import dev.cel.compiler.CelCompiler;
import dev.cel.compiler.CelCompilerBuilder;
import dev.cel.extensions.CelExtensions;
import dev.cel.extensions.CelOptionalLibrary;
import dev.cel.runtime.CelRuntimeBuilder;
import java.util.Arrays;
import java.util.Optional;

/**
 * CelEnvironment is a native representation of a CEL environment for compiler and runtime. This
 * object is amenable to being serialized into YAML, textproto or other formats as needed.
 */
@AutoValue
public abstract class CelEnvironment {

  @VisibleForTesting
  static final ImmutableMap<String, CanonicalCelExtension> CEL_EXTENSION_CONFIG_MAP =
      ImmutableMap.of(
          "bindings", CanonicalCelExtension.BINDINGS,
          "encoders", CanonicalCelExtension.ENCODERS,
          "lists", CanonicalCelExtension.LISTS,
          "math", CanonicalCelExtension.MATH,
          "optional", CanonicalCelExtension.OPTIONAL,
          "protos", CanonicalCelExtension.PROTOS,
          "sets", CanonicalCelExtension.SETS,
          "strings", CanonicalCelExtension.STRINGS);

  /** Environment source in textual format (ex: textproto, YAML). */
  public abstract Optional<Source> source();

  /** Name of the environment. */
  public abstract String name();

  /**
   * An optional description of the config (example: location of the file containing the config
   * content).
   */
  public abstract String container();

  /**
   * An optional description of the environment (example: location of the file containing the config
   * content).
   */
  public abstract String description();

  /** Converts this {@code CelEnvironment} object into a builder. */
  public abstract Builder toBuilder();

  /**
   * Canonical extensions to enable in the environment, such as Optional, String and Math
   * extensions.
   */
  public abstract ImmutableSet<ExtensionConfig> extensions();

  /** New variable declarations to add in the compilation environment. */
  public abstract ImmutableSet<VariableDecl> variables();

  /** New function declarations to add in the compilation environment. */
  public abstract ImmutableSet<FunctionDecl> functions();

  /** Builder for {@link CelEnvironment}. */
  @AutoValue.Builder
  public abstract static class Builder {

    public abstract ImmutableSet.Builder<ExtensionConfig> extensionsBuilder();

    // For testing only, to empty out the source.
    abstract Builder setSource(Optional<Source> source);

    public abstract Builder setSource(Source source);

    public abstract Builder setName(String name);

    public abstract Builder setDescription(String description);

    public abstract Builder setContainer(String container);

    @CanIgnoreReturnValue
    public Builder addExtensions(ExtensionConfig... extensions) {
      checkNotNull(extensions);
      return addExtensions(Arrays.asList(extensions));
    }

    @CanIgnoreReturnValue
    public Builder addExtensions(Iterable<ExtensionConfig> extensions) {
      checkNotNull(extensions);
      this.extensionsBuilder().addAll(extensions);
      return this;
    }

    @CanIgnoreReturnValue
    public Builder setVariables(VariableDecl... variables) {
      return setVariables(ImmutableSet.copyOf(variables));
    }

    public abstract Builder setVariables(ImmutableSet<VariableDecl> variables);

    @CanIgnoreReturnValue
    public Builder setFunctions(FunctionDecl... functions) {
      return setFunctions(ImmutableSet.copyOf(functions));
    }

    public abstract Builder setFunctions(ImmutableSet<FunctionDecl> functions);

    @CheckReturnValue
    public abstract CelEnvironment build();
  }

  /** Creates a new builder to construct a {@link CelEnvironment} instance. */
  public static Builder newBuilder() {
    return new AutoValue_CelEnvironment.Builder()
        .setName("")
        .setDescription("")
        .setContainer("")
        .setVariables(ImmutableSet.of())
        .setFunctions(ImmutableSet.of());
  }
  
  /** Extends the provided {@link CelCompiler} environment with this configuration. */
  public CelCompiler extend(CelCompiler celCompiler, CelOptions celOptions)
      throws CelEnvironmentException {
    try {
      CelTypeProvider celTypeProvider = celCompiler.getTypeProvider();
      CelCompilerBuilder compilerBuilder =
          celCompiler
              .toCompilerBuilder()
              .setTypeProvider(celTypeProvider)
              .setContainer(container())
              .addVarDeclarations(
                  variables().stream()
                      .map(v -> v.toCelVarDecl(celTypeProvider))
                      .collect(toImmutableList()))
              .addFunctionDeclarations(
                  functions().stream()
                      .map(f -> f.toCelFunctionDecl(celTypeProvider))
                      .collect(toImmutableList()));

      if (!container().isEmpty()) {
        compilerBuilder.setContainer(container());
      }

      addAllCompilerExtensions(compilerBuilder, celOptions);

      return compilerBuilder.build();
    } catch (RuntimeException e) {
      throw new CelEnvironmentException(e.getMessage(), e);
    }
  }

  /** Extends the provided {@link Cel} environment with this configuration. */
  public Cel extend(Cel cel, CelOptions celOptions) throws CelEnvironmentException {
    try {
      // Casting is necessary to only extend the compiler here
      CelCompiler celCompiler = extend((CelCompiler) cel, celOptions);

      CelRuntimeBuilder celRuntimeBuilder = cel.toRuntimeBuilder();
      addAllRuntimeExtensions(celRuntimeBuilder, celOptions);

      return CelFactory.combine(celCompiler, celRuntimeBuilder.build());
    } catch (RuntimeException e) {
      throw new CelEnvironmentException(e.getMessage(), e);
    }
  }

  private void addAllCompilerExtensions(
      CelCompilerBuilder celCompilerBuilder, CelOptions celOptions) {
    // TODO: Add capability to accept user defined exceptions
    for (ExtensionConfig extensionConfig : extensions()) {
      CanonicalCelExtension extension = getExtensionOrThrow(extensionConfig.name());
      extension.addCompilerExtension(celCompilerBuilder, celOptions);
    }
  }

  private void addAllRuntimeExtensions(CelRuntimeBuilder celRuntimeBuilder, CelOptions celOptions) {
    // TODO: Add capability to accept user defined exceptions
    for (ExtensionConfig extensionConfig : extensions()) {
      CanonicalCelExtension extension = getExtensionOrThrow(extensionConfig.name());
      extension.addRuntimeExtension(celRuntimeBuilder, celOptions);
    }
  }

  private static CanonicalCelExtension getExtensionOrThrow(String extensionName) {
    CanonicalCelExtension extension = CEL_EXTENSION_CONFIG_MAP.get(extensionName);
    if (extension == null) {
      throw new IllegalArgumentException("Unrecognized extension: " + extensionName);
    }

    return extension;
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

      public abstract Optional<String> name();

      public abstract Optional<TypeDecl> type();

      public abstract VariableDecl.Builder setName(String name);

      public abstract VariableDecl.Builder setType(TypeDecl typeDecl);

      @Override
      public ImmutableList<RequiredField> requiredFields() {
        return ImmutableList.of(
            RequiredField.of("name", this::name), RequiredField.of("type", this::type));
      }

      /** Builds a new instance of {@link VariableDecl}. */
      public abstract VariableDecl build();
    }

    public static VariableDecl.Builder newBuilder() {
      return new AutoValue_CelEnvironment_VariableDecl.Builder();
    }

    /** Creates a new builder to construct a {@link VariableDecl} instance. */
    public static VariableDecl create(String name, TypeDecl type) {
      return newBuilder().setName(name).setType(type).build();
    }

    /** Converts this policy variable declaration into a {@link CelVarDecl}. */
    public CelVarDecl toCelVarDecl(CelTypeProvider celTypeProvider) {
      return CelVarDecl.newVarDeclaration(name(), type().toCelType(celTypeProvider));
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

      public abstract Optional<String> name();

      public abstract Optional<ImmutableSet<OverloadDecl>> overloads();

      public abstract FunctionDecl.Builder setName(String name);

      public abstract FunctionDecl.Builder setOverloads(ImmutableSet<OverloadDecl> overloads);

      @Override
      public ImmutableList<RequiredField> requiredFields() {
        return ImmutableList.of(
            RequiredField.of("name", this::name), RequiredField.of("overloads", this::overloads));
      }

      /** Builds a new instance of {@link FunctionDecl}. */
      public abstract FunctionDecl build();
    }

    /** Creates a new builder to construct a {@link FunctionDecl} instance. */
    public static FunctionDecl.Builder newBuilder() {
      return new AutoValue_CelEnvironment_FunctionDecl.Builder();
    }

    /** Creates a new {@link FunctionDecl} with the provided function name and its overloads. */
    public static FunctionDecl create(String name, ImmutableSet<OverloadDecl> overloads) {
      return newBuilder().setName(name).setOverloads(overloads).build();
    }

    /** Converts this policy function declaration into a {@link CelFunctionDecl}. */
    public CelFunctionDecl toCelFunctionDecl(CelTypeProvider celTypeProvider) {
      return CelFunctionDecl.newFunctionDeclaration(
          name(),
          overloads().stream()
              .map(o -> o.toCelOverloadDecl(celTypeProvider))
              .collect(toImmutableList()));
    }
  }

  /** Represents an overload declaration on a policy function. */
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

      public abstract Optional<String> id();

      public abstract Optional<TypeDecl> returnType();

      public abstract OverloadDecl.Builder setId(String overloadId);

      public abstract OverloadDecl.Builder setTarget(TypeDecl target);

      // This should stay package-private to encourage add/set methods to be used instead.
      abstract ImmutableList.Builder<TypeDecl> argumentsBuilder();

      public abstract OverloadDecl.Builder setArguments(ImmutableList<TypeDecl> args);

      @CanIgnoreReturnValue
      public OverloadDecl.Builder addArguments(Iterable<TypeDecl> args) {
        this.argumentsBuilder().addAll(checkNotNull(args));
        return this;
      }

      @CanIgnoreReturnValue
      public OverloadDecl.Builder addArguments(TypeDecl... args) {
        return addArguments(Arrays.asList(args));
      }

      public abstract OverloadDecl.Builder setReturnType(TypeDecl returnType);

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
    public static OverloadDecl.Builder newBuilder() {
      return new AutoValue_CelEnvironment_OverloadDecl.Builder().setArguments(ImmutableList.of());
    }

    /** Converts this policy function overload into a {@link CelOverloadDecl}. */
    public CelOverloadDecl toCelOverloadDecl(CelTypeProvider celTypeProvider) {
      CelOverloadDecl.Builder builder =
          CelOverloadDecl.newBuilder()
              .setIsInstanceFunction(false)
              .setOverloadId(id())
              .setResultType(returnType().toCelType(celTypeProvider));

      target()
          .ifPresent(
              t ->
                  builder
                      .setIsInstanceFunction(true)
                      .addParameterTypes(t.toCelType(celTypeProvider)));

      for (TypeDecl type : arguments()) {
        builder.addParameterTypes(type.toCelType(celTypeProvider));
      }

      return builder.build();
    }
  }

  /**
   * Represents an abstract type declaration used to declare functions and variables in a policy.
   */
  @AutoValue
  public abstract static class TypeDecl {

    public abstract String name();

    public abstract ImmutableList<TypeDecl> params();

    public abstract boolean isTypeParam();

    /** Builder for {@link TypeDecl}. */
    @AutoValue.Builder
    public abstract static class Builder implements RequiredFieldsChecker {

      public abstract Optional<String> name();

      public abstract TypeDecl.Builder setName(String name);

      // This should stay package-private to encourage add/set methods to be used instead.
      abstract ImmutableList.Builder<TypeDecl> paramsBuilder();

      public abstract TypeDecl.Builder setParams(ImmutableList<TypeDecl> typeDecls);

      @CanIgnoreReturnValue
      public TypeDecl.Builder addParams(TypeDecl... params) {
        return addParams(Arrays.asList(params));
      }

      @CanIgnoreReturnValue
      public TypeDecl.Builder addParams(Iterable<TypeDecl> params) {
        this.paramsBuilder().addAll(checkNotNull(params));
        return this;
      }

      public abstract TypeDecl.Builder setIsTypeParam(boolean isTypeParam);

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

    public static TypeDecl.Builder newBuilder() {
      return new AutoValue_CelEnvironment_TypeDecl.Builder().setIsTypeParam(false);
    }

    /** Converts this type declaration into a {@link CelType}. */
    public CelType toCelType(CelTypeProvider celTypeProvider) {
      switch (name()) {
        case "list":
          if (params().size() != 1) {
            throw new IllegalArgumentException(
                "List type has unexpected param count: " + params().size());
          }

          CelType elementType = params().get(0).toCelType(celTypeProvider);
          return ListType.create(elementType);
        case "map":
          if (params().size() != 2) {
            throw new IllegalArgumentException(
                "Map type has unexpected param count: " + params().size());
          }

          CelType keyType = params().get(0).toCelType(celTypeProvider);
          CelType valueType = params().get(1).toCelType(celTypeProvider);
          return MapType.create(keyType, valueType);
        default:
          if (isTypeParam()) {
            return TypeParamType.create(name());
          }

          CelType simpleType = SimpleType.findByName(name()).orElse(null);
          if (simpleType != null) {
            return simpleType;
          }

          if (name().equals(OptionalType.NAME)) {
            checkState(
                params().size() == 1,
                "Optional type must have exactly 1 parameter. Found %s",
                params().size());
            return OptionalType.create(params().get(0).toCelType(celTypeProvider));
          }

          return celTypeProvider
              .findType(name())
              .orElseThrow(() -> new IllegalArgumentException("Undefined type name: " + name()));
      }
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
    public abstract int version();

    /** Builder for {@link ExtensionConfig}. */
    @AutoValue.Builder
    public abstract static class Builder implements RequiredFieldsChecker {

      public abstract Optional<String> name();

      public abstract Optional<Integer> version();

      public abstract ExtensionConfig.Builder setName(String name);

      public abstract ExtensionConfig.Builder setVersion(int version);

      @Override
      public ImmutableList<RequiredField> requiredFields() {
        return ImmutableList.of(RequiredField.of("name", this::name));
      }

      /** Builds a new instance of {@link ExtensionConfig}. */
      public abstract ExtensionConfig build();
    }

    /** Creates a new builder to construct a {@link ExtensionConfig} instance. */
    public static ExtensionConfig.Builder newBuilder() {
      return new AutoValue_CelEnvironment_ExtensionConfig.Builder().setVersion(0);
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

  @VisibleForTesting
  enum CanonicalCelExtension {
    BINDINGS((compilerBuilder, options) -> compilerBuilder.addLibraries(CelExtensions.bindings())),
    PROTOS((compilerBuilder, options) -> compilerBuilder.addLibraries(CelExtensions.protos())),
    ENCODERS(
        (compilerBuilder, options) -> compilerBuilder.addLibraries(CelExtensions.encoders()),
        (runtimeBuilder, options) -> runtimeBuilder.addLibraries(CelExtensions.encoders())),
    MATH(
        (compilerBuilder, options) -> compilerBuilder.addLibraries(CelExtensions.math(options)),
        (runtimeBuilder, options) -> runtimeBuilder.addLibraries(CelExtensions.math(options))),
    OPTIONAL(
        (compilerBuilder, options) -> compilerBuilder.addLibraries(CelOptionalLibrary.INSTANCE),
        (runtimeBuilder, options) -> runtimeBuilder.addLibraries(CelOptionalLibrary.INSTANCE)),
    STRINGS(
        (compilerBuilder, options) -> compilerBuilder.addLibraries(CelExtensions.strings()),
        (runtimeBuilder, options) -> runtimeBuilder.addLibraries(CelExtensions.strings())),
    SETS(
        (compilerBuilder, options) -> compilerBuilder.addLibraries(CelExtensions.sets(options)),
        (runtimeBuilder, options) -> runtimeBuilder.addLibraries(CelExtensions.sets(options))),
    LISTS(
        (compilerBuilder, options) -> compilerBuilder.addLibraries(CelExtensions.lists()),
        (runtimeBuilder, options) -> runtimeBuilder.addLibraries(CelExtensions.lists()));

    @SuppressWarnings("ImmutableEnumChecker")
    private final CompilerExtensionApplier compilerExtensionApplier;

    @SuppressWarnings("ImmutableEnumChecker")
    private final RuntimeExtensionApplier runtimeExtensionApplier;

    interface CompilerExtensionApplier {
      void apply(CelCompilerBuilder compilerBuilder, CelOptions options);
    }

    interface RuntimeExtensionApplier {
      void apply(CelRuntimeBuilder runtimeBuilder, CelOptions options);
    }

    void addCompilerExtension(CelCompilerBuilder compilerBuilder, CelOptions options) {
      compilerExtensionApplier.apply(compilerBuilder, options);
    }

    void addRuntimeExtension(CelRuntimeBuilder runtimeBuilder, CelOptions options) {
      runtimeExtensionApplier.apply(runtimeBuilder, options);
    }

    CanonicalCelExtension(CompilerExtensionApplier compilerExtensionApplier) {
      this(
          compilerExtensionApplier,
          (runtimeBuilder, options) -> {}); // no-op. Not all extensions augment the runtime.
    }

    CanonicalCelExtension(
        CompilerExtensionApplier compilerExtensionApplier,
        RuntimeExtensionApplier runtimeExtensionApplier) {
      this.compilerExtensionApplier = compilerExtensionApplier;
      this.runtimeExtensionApplier = runtimeExtensionApplier;
    }
  }
}
