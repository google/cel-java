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

package dev.cel.compiler;

import static com.google.common.base.Preconditions.checkNotNull;

import dev.cel.expr.Decl;
import dev.cel.expr.Type;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.errorprone.annotations.CheckReturnValue;
import com.google.errorprone.annotations.Immutable;
import com.google.protobuf.DescriptorProtos.FileDescriptorSet;
import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.FileDescriptor;
import dev.cel.checker.CelChecker;
import dev.cel.checker.CelCheckerBuilder;
import dev.cel.checker.ProtoTypeMask;
import dev.cel.checker.TypeProvider;
import dev.cel.common.CelAbstractSyntaxTree;
import dev.cel.common.CelFunctionDecl;
import dev.cel.common.CelOptions;
import dev.cel.common.CelSource;
import dev.cel.common.CelValidationResult;
import dev.cel.common.CelVarDecl;
import dev.cel.common.annotations.Internal;
import dev.cel.common.internal.EnvVisitable;
import dev.cel.common.internal.EnvVisitor;
import dev.cel.common.types.CelType;
import dev.cel.common.types.CelTypeProvider;
import dev.cel.common.types.CelTypes;
import dev.cel.parser.CelMacro;
import dev.cel.parser.CelParser;
import dev.cel.parser.CelParserBuilder;
import dev.cel.parser.CelStandardMacro;
import java.util.Arrays;

/**
 * CelCompiler implementation which uses either the legacy or modernized CEL-Java stack to offer a
 * stream-lined expression parse/type-check experience, via a single {@code compile} method.
 *
 * <p>CEL Library Internals. Do Not Use. Consumers should use factories, such as {@link
 * CelCompilerFactory} instead to instantiate a compiler.
 */
@Immutable
@Internal
public final class CelCompilerImpl implements CelCompiler, EnvVisitable {

  private final CelParser parser;
  private final CelChecker checker;

  @Override
  public CelValidationResult parse(String expression, String description) {
    return parser.parse(expression, description);
  }

  @Override
  public CelValidationResult parse(CelSource source) {
    return parser.parse(source);
  }

  @Override
  public CelValidationResult check(CelAbstractSyntaxTree ast) {
    return checker.check(ast);
  }

  @Override
  public void accept(EnvVisitor envVisitor) {
    if (checker instanceof EnvVisitable) {
      ((EnvVisitable) checker).accept(envVisitor);
    }
  }

  /** Combines a prebuilt {@link CelParser} and {@link CelChecker} into {@link CelCompilerImpl}. */
  static CelCompilerImpl combine(CelParser parser, CelChecker checker) {
    return new CelCompilerImpl(parser, checker);
  }

  /**
   * Create a new builder for constructing a {@code CelCompiler} instance.
   *
   * <p>By default, {@link CelOptions#DEFAULT} are enabled, as is the CEL standard environment.
   */
  public static Builder newBuilder(
      CelParserBuilder parserBuilder, CelCheckerBuilder checkerBuilder) {
    return new Builder(parserBuilder, checkerBuilder);
  }

  /** Builder for {@code CelCompilerImpl} */
  public static final class Builder implements CelCompilerBuilder {
    private final CelParserBuilder parserBuilder;
    private final CelCheckerBuilder checkerBuilder;

    /** {@inheritDoc} */
    @Override
    @CanIgnoreReturnValue
    public Builder setOptions(CelOptions options) {
      parserBuilder.setOptions(options);
      checkerBuilder.setOptions(options);
      return this;
    }

    /** {@inheritDoc} */
    @Override
    @CanIgnoreReturnValue
    public Builder setStandardMacros(CelStandardMacro... macros) {
      parserBuilder.setStandardMacros(macros);
      return this;
    }

    /** {@inheritDoc} */
    @Override
    @CanIgnoreReturnValue
    public Builder setStandardMacros(Iterable<CelStandardMacro> macros) {
      parserBuilder.setStandardMacros(macros);
      return this;
    }

    @Override
    public Builder addMacros(CelMacro... macros) {
      checkNotNull(macros);
      return addMacros(Arrays.asList(macros));
    }

    @Override
    public Builder addMacros(Iterable<CelMacro> macros) {
      checkNotNull(macros);
      parserBuilder.addMacros(macros);
      return this;
    }

    /** {@inheritDoc} */
    @Override
    @CanIgnoreReturnValue
    public Builder setContainer(String container) {
      checkerBuilder.setContainer(container);
      return this;
    }

    /** {@inheritDoc} */
    @Override
    @CanIgnoreReturnValue
    public Builder addVar(String name, Type type) {
      return addVar(name, CelTypes.typeToCelType(type));
    }

    /** {@inheritDoc} */
    @Override
    @CanIgnoreReturnValue
    public Builder addVar(String name, CelType type) {
      return addVarDeclarations(CelVarDecl.newVarDeclaration(name, type));
    }

    /** {@inheritDoc} */
    @Override
    @CanIgnoreReturnValue
    public Builder addDeclarations(Decl... declarations) {
      checkerBuilder.addDeclarations(declarations);
      return this;
    }

    /** {@inheritDoc} */
    @Override
    @CanIgnoreReturnValue
    public Builder addDeclarations(Iterable<Decl> declarations) {
      checkerBuilder.addDeclarations(declarations);
      return this;
    }

    /** {@inheritDoc} */
    @Override
    @CanIgnoreReturnValue
    public Builder addFunctionDeclarations(CelFunctionDecl... celFunctionDecls) {
      checkerBuilder.addFunctionDeclarations(celFunctionDecls);
      return this;
    }

    /** {@inheritDoc} */
    @Override
    @CanIgnoreReturnValue
    public Builder addFunctionDeclarations(Iterable<CelFunctionDecl> celFunctionDecls) {
      checkerBuilder.addFunctionDeclarations(celFunctionDecls);
      return this;
    }

    /** {@inheritDoc} */
    @Override
    @CanIgnoreReturnValue
    public Builder addVarDeclarations(CelVarDecl... celVarDecls) {
      checkerBuilder.addVarDeclarations(celVarDecls);
      return this;
    }

    /** {@inheritDoc} */
    @Override
    @CanIgnoreReturnValue
    public Builder addVarDeclarations(Iterable<CelVarDecl> celVarDecls) {
      checkerBuilder.addVarDeclarations(celVarDecls);
      return this;
    }

    /** {@inheritDoc} */
    @Override
    @CanIgnoreReturnValue
    public Builder addProtoTypeMasks(ProtoTypeMask... typeMasks) {
      checkerBuilder.addProtoTypeMasks(typeMasks);
      return this;
    }

    /** {@inheritDoc} */
    @Override
    @CanIgnoreReturnValue
    public Builder addProtoTypeMasks(Iterable<ProtoTypeMask> typeMasks) {
      checkerBuilder.addProtoTypeMasks(typeMasks);
      return this;
    }

    /** {@inheritDoc} */
    @Override
    @CanIgnoreReturnValue
    public Builder setResultType(CelType resultType) {
      checkNotNull(resultType);
      return setProtoResultType(CelTypes.celTypeToType(resultType));
    }

    /** {@inheritDoc} */
    @Override
    @CanIgnoreReturnValue
    public Builder setProtoResultType(Type resultType) {
      checkerBuilder.setProtoResultType(resultType);
      return this;
    }

    /** {@inheritDoc} */
    @Override
    @CanIgnoreReturnValue
    @Deprecated
    public Builder setTypeProvider(TypeProvider typeProvider) {
      checkerBuilder.setTypeProvider(typeProvider);
      return this;
    }

    /** {@inheritDoc} */
    @Override
    @CanIgnoreReturnValue
    public Builder setTypeProvider(CelTypeProvider celTypeProvider) {
      checkerBuilder.setTypeProvider(celTypeProvider);
      return this;
    }

    /** {@inheritDoc} */
    @Override
    @CanIgnoreReturnValue
    public Builder addMessageTypes(Descriptor... descriptors) {
      checkerBuilder.addMessageTypes(descriptors);
      return this;
    }

    /** {@inheritDoc} */
    @Override
    @CanIgnoreReturnValue
    public Builder addMessageTypes(Iterable<Descriptor> descriptors) {
      checkerBuilder.addMessageTypes(descriptors);
      return this;
    }

    /** {@inheritDoc} */
    @Override
    @CanIgnoreReturnValue
    public Builder addFileTypes(FileDescriptor... fileDescriptors) {
      checkerBuilder.addFileTypes(fileDescriptors);
      return this;
    }

    /** {@inheritDoc} */
    @Override
    @CanIgnoreReturnValue
    public Builder addFileTypes(Iterable<FileDescriptor> fileDescriptors) {
      checkerBuilder.addFileTypes(fileDescriptors);
      return this;
    }

    /** {@inheritDoc} */
    @Override
    @CanIgnoreReturnValue
    public Builder addFileTypes(FileDescriptorSet fileDescriptorSet) {
      checkerBuilder.addFileTypes(fileDescriptorSet);
      return this;
    }

    /** {@inheritDoc} */
    @Override
    @CanIgnoreReturnValue
    public Builder setStandardEnvironmentEnabled(boolean value) {
      checkerBuilder.setStandardEnvironmentEnabled(value);
      return this;
    }

    /** {@inheritDoc} */
    @Override
    @CanIgnoreReturnValue
    public Builder addLibraries(CelCompilerLibrary... libraries) {
      checkNotNull(libraries);
      return this.addLibraries(Arrays.asList(libraries));
    }

    /** {@inheritDoc} */
    @Override
    @CanIgnoreReturnValue
    public Builder addLibraries(Iterable<? extends CelCompilerLibrary> libraries) {
      checkNotNull(libraries);
      parserBuilder.addLibraries(libraries);
      checkerBuilder.addLibraries(libraries);
      return this;
    }

    /** {@inheritDoc} */
    @Override
    @CheckReturnValue
    public CelCompilerImpl build() {
      return new CelCompilerImpl(parserBuilder.build(), checkerBuilder.build());
    }

    private Builder(CelParserBuilder parserBuilder, CelCheckerBuilder checkerBuilder) {
      this.parserBuilder = parserBuilder;
      this.checkerBuilder = checkerBuilder;
    }
  }

  private CelCompilerImpl(CelParser parser, CelChecker checker) {
    this.parser = parser;
    this.checker = checker;
  }
}
