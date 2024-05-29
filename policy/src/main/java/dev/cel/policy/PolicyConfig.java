package dev.cel.policy;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.errorprone.annotations.CheckReturnValue;
import dev.cel.bundle.Cel;
import dev.cel.bundle.CelBuilder;
import dev.cel.bundle.CelFactory;
import dev.cel.common.CelFunctionDecl;
import dev.cel.common.CelOptions;
import dev.cel.common.CelOverloadDecl;
import dev.cel.common.CelVarDecl;
import dev.cel.common.types.CelType;
import dev.cel.common.types.SimpleType;
import dev.cel.extensions.CelExtensions;
import dev.cel.extensions.CelOptionalLibrary;

import java.util.Arrays;
import java.util.Optional;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.ImmutableList.toImmutableList;

@AutoValue
public abstract class PolicyConfig {

  abstract String name();
  abstract String description();
  abstract String container();
  abstract ImmutableSet<ExtensionConfig> extensions();
  abstract ImmutableSet<VariableDecl> variables();
  abstract ImmutableSet<FunctionDecl> functions();

  @AutoValue.Builder
  public abstract static class Builder {
    public abstract Builder setName(String name);
    public abstract Builder setDescription(String description);
    public abstract Builder setContainer(String container);
    public abstract Builder setExtensions(ImmutableSet<ExtensionConfig> extensions);
    public abstract Builder setVariables(ImmutableSet<VariableDecl> variables);
    public abstract Builder setFunctions(ImmutableSet<FunctionDecl> functions);

    @CheckReturnValue
    public abstract PolicyConfig build();
  }

  public static Builder newBuilder() {
    return new AutoValue_PolicyConfig.Builder()
            .setName("")
            .setDescription("")
            .setContainer("")
            .setExtensions(ImmutableSet.of())
            .setVariables(ImmutableSet.of())
            .setFunctions(ImmutableSet.of())
            ;
  }

  public Cel toCel(CelOptions celOptions) {
    CelBuilder celBuilder = CelFactory.standardCelBuilder()
          .setContainer(container())
          .addVarDeclarations(
                  variables().stream().map(VariableDecl::toCelVarDecl).collect(toImmutableList())
          )
          .addFunctionDeclarations(
                  functions().stream().map(FunctionDecl::toCelFunctionDecl).collect(toImmutableList())
          );

    addAllExtensions(celBuilder, celOptions);

    return celBuilder.build();
  }


  private void addAllExtensions(CelBuilder celBuilder, CelOptions celOptions) {
    for (ExtensionConfig extensionConfig : extensions()) {
      switch (extensionConfig.name()) {
        case "bindings":
          celBuilder.addCompilerLibraries(CelExtensions.bindings());
          break;
        case "encoders":
          celBuilder.addCompilerLibraries(CelExtensions.encoders());
          celBuilder.addRuntimeLibraries(CelExtensions.encoders());
          break;
        case "math":
          celBuilder.addCompilerLibraries(CelExtensions.math(celOptions));
          celBuilder.addRuntimeLibraries(CelExtensions.math(celOptions));
          break;
        case "optional":
          celBuilder.addCompilerLibraries(CelOptionalLibrary.INSTANCE);
          celBuilder.addRuntimeLibraries(CelOptionalLibrary.INSTANCE);
          break;
        case "protos":
          celBuilder.addCompilerLibraries(CelExtensions.protos());
          break;
        case "strings":
          celBuilder.addCompilerLibraries(CelExtensions.strings());
          celBuilder.addRuntimeLibraries(CelExtensions.strings());
          break;
        default:
          throw new IllegalArgumentException("Unsupported extension: " + extensionConfig.name());
      }
    }
  }

  @AutoValue
  public static abstract class VariableDecl {

    /**
     * Fully qualified variable name.
     */
    public abstract String name();

    /**
     * The type of the variable.
     */
    public abstract TypeDecl type();

    public CelVarDecl toCelVarDecl() {
      return CelVarDecl.newVarDeclaration(name(), type().toCelType());
    }
  }

  @AutoValue
  public static abstract class FunctionDecl {

    public abstract String name();
    public abstract ImmutableSet<OverloadDecl> overloads();

    public static FunctionDecl create(String name, ImmutableSet<OverloadDecl> overloads) {
      return new AutoValue_PolicyConfig_FunctionDecl(name, overloads);
    }

    public CelFunctionDecl toCelFunctionDecl() {
      return CelFunctionDecl.newFunctionDeclaration(
              name(),
              overloads().stream().map(OverloadDecl::toCelOverloadDecl).collect(toImmutableList())
      );
    }
  }

  @AutoValue
  public static abstract class OverloadDecl {
    public abstract String overloadId();
    public abstract Optional<TypeDecl> target();
    public abstract ImmutableList<TypeDecl> arguments();
    public abstract TypeDecl returnType();

    @AutoValue.Builder
    public abstract static class Builder {
      public abstract Builder setOverloadId(String overloadId);
      public abstract Builder setTarget(TypeDecl target);
      abstract ImmutableList.Builder<TypeDecl> argumentsBuilder();
      abstract Builder setArguments(ImmutableList<TypeDecl> args);

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

      @CheckReturnValue
      public abstract OverloadDecl build();
    }

    public static Builder newBuilder() {
      return new AutoValue_PolicyConfig_OverloadDecl.Builder().setArguments(ImmutableList.of());
    }

    public CelOverloadDecl toCelOverloadDecl() {
      CelOverloadDecl.Builder builder = CelOverloadDecl.newBuilder()
              .setIsInstanceFunction(false)
              .setOverloadId(overloadId())
              .setResultType(returnType().toCelType());

      target().ifPresent(t -> {
        builder.setIsInstanceFunction(true);
        builder.addParameterTypes(t.toCelType());
      });

      for (TypeDecl type : arguments()) {
        builder.addParameterTypes(type.toCelType());
      }

      return builder.build();
    }
  }

  @AutoValue
  public static abstract class TypeDecl {

    public abstract String name();

    public abstract ImmutableList<TypeDecl> params();

    public abstract Boolean isTypeParam();

    @AutoValue.Builder
    public abstract static class Builder {
      public abstract Builder setName(String name);
      abstract Builder setParams(ImmutableList<TypeDecl> typeDecls);
      abstract ImmutableList.Builder<TypeDecl> paramsBuilder();

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

      @CheckReturnValue
      public abstract TypeDecl build();
    }

    public static Builder newBuilder() {
      return new AutoValue_PolicyConfig_TypeDecl.Builder();
    }

    public CelType toCelType() {
      switch (name()) {
        case "list":
          throw new UnsupportedOperationException("");
        default:
          return SimpleType.findByName(name()).get();
      }
    }
  }

  @AutoValue
  public static abstract class ExtensionConfig {

    /**
     * Name of the extension (ex: bindings, optional, math, etc).".
     */
    abstract String name();

    /**
     * Version of the extension. Presently, this field is ignored as CEL-Java extensions are not
     * versioned.
     */
    abstract Integer version();

    public static ExtensionConfig of(String name) {
      return of(name, 0);
    }

    public static ExtensionConfig of(String name, int version) {
      return new AutoValue_PolicyConfig_ExtensionConfig(name, version);
    }
  }
}
