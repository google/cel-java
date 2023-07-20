// Copyright 2022 Google LLC
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

import dev.cel.expr.Decl;
import dev.cel.expr.Type;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.errorprone.annotations.Immutable;
import com.google.protobuf.DescriptorProtos.FileDescriptorSet;
import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.FileDescriptor;
import com.google.protobuf.Message;
import dev.cel.checker.CelCheckerBuilder;
import dev.cel.checker.ProtoTypeMask;
import dev.cel.checker.TypeProvider;
import dev.cel.common.CelAbstractSyntaxTree;
import dev.cel.common.CelFunctionDecl;
import dev.cel.common.CelOptions;
import dev.cel.common.CelSource;
import dev.cel.common.CelValidationResult;
import dev.cel.common.CelVarDecl;
import dev.cel.common.internal.EnvVisitable;
import dev.cel.common.internal.EnvVisitor;
import dev.cel.common.types.CelType;
import dev.cel.common.types.CelTypeProvider;
import dev.cel.common.types.CelTypes;
import dev.cel.compiler.CelCompiler;
import dev.cel.compiler.CelCompilerBuilder;
import dev.cel.compiler.CelCompilerImpl;
import dev.cel.compiler.CelCompilerLibrary;
import dev.cel.parser.CelMacro;
import dev.cel.parser.CelParserBuilder;
import dev.cel.parser.CelStandardMacro;
import dev.cel.runtime.CelEvaluationException;
import dev.cel.runtime.CelRuntime;
import dev.cel.runtime.CelRuntimeBuilder;
import dev.cel.runtime.CelRuntimeLegacyImpl;
import dev.cel.runtime.CelRuntimeLibrary;
import java.util.Arrays;
import java.util.function.Function;

/**
 * Implementation of the synchronous CEL stack.
 *
 * <p>Note, the underlying {@link CelCompiler} and {@link CelRuntime} values are constructed lazily.
 */
@Immutable
final class CelImpl implements Cel, EnvVisitable {

  // The lazily constructed compiler and runtime values are memoized and guaranteed to be
  // constructed only once without side effects, thus making them effectively immutable.
  @SuppressWarnings("Immutable")
  private final Supplier<CelCompiler> compiler;

  @SuppressWarnings("Immutable")
  private final Supplier<CelRuntime> runtime;

  @Override
  public CelValidationResult parse(String expression, String description) {
    return compiler.get().parse(expression, description);
  }

  @Override
  public CelValidationResult parse(CelSource source) {
    return compiler.get().parse(source);
  }

  @Override
  public CelValidationResult check(CelAbstractSyntaxTree ast) {
    return compiler.get().check(ast);
  }

  @Override
  public CelRuntime.Program createProgram(CelAbstractSyntaxTree ast) throws CelEvaluationException {
    return runtime.get().createProgram(ast);
  }

  @Override
  public void accept(EnvVisitor envVisitor) {
    CelCompiler celCompiler = compiler.get();
    if (celCompiler instanceof EnvVisitable) {
      ((EnvVisitable) celCompiler).accept(envVisitor);
    }
  }

  /**
   * Create a new builder for constructing a {@code CelImpl} instance.
   *
   * <p>By default, {@link CelOptions#DEFAULT} are enabled, as is the CEL standard environment.
   */
  static Builder newBuilder(CelParserBuilder parserBuilder, CelCheckerBuilder checkerBuilder) {
    return new CelImpl.Builder(parserBuilder, checkerBuilder);
  }

  /** Builder class for CelImpl instances. */
  public static final class Builder implements CelBuilder {

    private final CelCompilerBuilder compilerBuilder;
    private final CelRuntimeBuilder runtimeBuilder;

    private Builder(CelParserBuilder parserBuilder, CelCheckerBuilder checkerBuilder) {
      this.compilerBuilder = CelCompilerImpl.newBuilder(parserBuilder, checkerBuilder);
      this.runtimeBuilder = CelRuntimeLegacyImpl.newBuilder();
    }

    @Override
    public Builder setOptions(CelOptions options) {
      compilerBuilder.setOptions(options);
      runtimeBuilder.setOptions(options);
      return this;
    }

    @Override
    public Builder setStandardMacros(CelStandardMacro... macros) {
      compilerBuilder.setStandardMacros(macros);
      return this;
    }

    @Override
    public Builder setStandardMacros(Iterable<CelStandardMacro> macros) {
      compilerBuilder.setStandardMacros(macros);
      return this;
    }

    @Override
    public CelBuilder addMacros(CelMacro... macros) {
      checkNotNull(macros);
      return addMacros(Arrays.asList(macros));
    }

    @Override
    public CelBuilder addMacros(Iterable<CelMacro> macros) {
      checkNotNull(macros);
      compilerBuilder.addMacros(macros);
      return this;
    }

    @Override
    public Builder setContainer(String container) {
      compilerBuilder.setContainer(container);
      return this;
    }

    @Override
    public Builder addVar(String name, Type type) {
      compilerBuilder.addVar(name, type);
      return this;
    }

    @Override
    public Builder addVar(String name, CelType type) {
      compilerBuilder.addVar(name, type);
      return this;
    }

    @Override
    public Builder addDeclarations(Decl... declarations) {
      compilerBuilder.addDeclarations(declarations);
      return this;
    }

    @Override
    public Builder addDeclarations(Iterable<Decl> declarations) {
      compilerBuilder.addDeclarations(declarations);
      return this;
    }

    @Override
    public Builder addFunctionDeclarations(CelFunctionDecl... celFunctionDecls) {
      compilerBuilder.addFunctionDeclarations(celFunctionDecls);
      return this;
    }

    @Override
    public Builder addFunctionDeclarations(Iterable<CelFunctionDecl> celFunctionDecls) {
      compilerBuilder.addFunctionDeclarations(celFunctionDecls);
      return this;
    }

    @Override
    public Builder addVarDeclarations(CelVarDecl... celVarDecls) {
      compilerBuilder.addVarDeclarations(celVarDecls);
      return this;
    }

    @Override
    public Builder addVarDeclarations(Iterable<CelVarDecl> celVarDecls) {
      compilerBuilder.addVarDeclarations(celVarDecls);
      return this;
    }

    @Override
    public Builder addProtoTypeMasks(ProtoTypeMask... typeMasks) {
      compilerBuilder.addProtoTypeMasks(typeMasks);
      return this;
    }

    @Override
    public Builder addProtoTypeMasks(Iterable<ProtoTypeMask> typeMasks) {
      compilerBuilder.addProtoTypeMasks(typeMasks);
      return this;
    }

    @Override
    public Builder addFunctionBindings(CelRuntime.CelFunctionBinding... bindings) {
      runtimeBuilder.addFunctionBindings(bindings);
      return this;
    }

    @Override
    public Builder addFunctionBindings(Iterable<CelRuntime.CelFunctionBinding> bindings) {
      runtimeBuilder.addFunctionBindings(bindings);
      return this;
    }

    @Override
    public Builder setResultType(CelType resultType) {
      checkNotNull(resultType);
      return setProtoResultType(CelTypes.celTypeToType(resultType));
    }

    @Override
    public Builder setProtoResultType(Type resultType) {
      compilerBuilder.setProtoResultType(resultType);
      return this;
    }

    @Override
    public Builder setTypeFactory(Function<String, Message.Builder> typeFactory) {
      runtimeBuilder.setTypeFactory(typeFactory);
      return this;
    }

    @Override
    @Deprecated
    public Builder setTypeProvider(TypeProvider typeProvider) {
      compilerBuilder.setTypeProvider(typeProvider);
      return this;
    }

    @Override
    public Builder setTypeProvider(CelTypeProvider celTypeProvider) {
      compilerBuilder.setTypeProvider(celTypeProvider);
      return this;
    }

    @Override
    public Builder addMessageTypes(Descriptor... descriptors) {
      compilerBuilder.addMessageTypes(descriptors);
      runtimeBuilder.addMessageTypes(descriptors);
      return this;
    }

    @Override
    public Builder addMessageTypes(Iterable<Descriptor> descriptors) {
      compilerBuilder.addMessageTypes(descriptors);
      runtimeBuilder.addMessageTypes(descriptors);
      return this;
    }

    @Override
    public Builder addFileTypes(FileDescriptor... fileDescriptors) {
      compilerBuilder.addFileTypes(fileDescriptors);
      runtimeBuilder.addFileTypes(fileDescriptors);
      return this;
    }

    @Override
    public Builder addFileTypes(Iterable<FileDescriptor> fileDescriptors) {
      compilerBuilder.addFileTypes(fileDescriptors);
      runtimeBuilder.addFileTypes(fileDescriptors);
      return this;
    }

    @Override
    public Builder addFileTypes(FileDescriptorSet fileDescriptorSet) {
      compilerBuilder.addFileTypes(fileDescriptorSet);
      runtimeBuilder.addFileTypes(fileDescriptorSet);
      return this;
    }

    @Override
    public Builder setStandardEnvironmentEnabled(boolean value) {
      compilerBuilder.setStandardEnvironmentEnabled(value);
      runtimeBuilder.setStandardEnvironmentEnabled(value);
      return this;
    }

    @Override
    public Builder addCompilerLibraries(CelCompilerLibrary... libraries) {
      checkNotNull(libraries);
      return this.addCompilerLibraries(Arrays.asList(libraries));
    }

    @Override
    public Builder addCompilerLibraries(Iterable<CelCompilerLibrary> libraries) {
      checkNotNull(libraries);
      compilerBuilder.addLibraries(libraries);
      return this;
    }

    @Override
    public Builder addRuntimeLibraries(CelRuntimeLibrary... libraries) {
      checkNotNull(libraries);
      return this.addRuntimeLibraries(Arrays.asList(libraries));
    }

    @Override
    public Builder addRuntimeLibraries(Iterable<CelRuntimeLibrary> libraries) {
      checkNotNull(libraries);
      runtimeBuilder.addLibraries(libraries);
      return this;
    }

    @Override
    public Cel build() {
      return new CelImpl(
          Suppliers.memoize(compilerBuilder::build), Suppliers.memoize(runtimeBuilder::build));
    }
  }

  private CelImpl(Supplier<CelCompiler> compiler, Supplier<CelRuntime> runtime) {
    this.compiler = compiler;
    this.runtime = runtime;
  }
}
