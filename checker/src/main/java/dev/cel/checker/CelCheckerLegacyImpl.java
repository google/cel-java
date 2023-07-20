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

package dev.cel.checker;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableSet.toImmutableSet;

import dev.cel.expr.Decl;
import dev.cel.expr.Type;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.errorprone.annotations.CheckReturnValue;
import com.google.errorprone.annotations.Immutable;
import com.google.protobuf.DescriptorProtos.FileDescriptorSet;
import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.FileDescriptor;
import dev.cel.common.CelAbstractSyntaxTree;
import dev.cel.common.CelDescriptorUtil;
import dev.cel.common.CelFunctionDecl;
import dev.cel.common.CelIssue;
import dev.cel.common.CelOptions;
import dev.cel.common.CelOverloadDecl;
import dev.cel.common.CelSource;
import dev.cel.common.CelSourceLocation;
import dev.cel.common.CelValidationResult;
import dev.cel.common.CelVarDecl;
import dev.cel.common.ast.CelExprConverter;
import dev.cel.common.internal.EnvVisitable;
import dev.cel.common.internal.EnvVisitor;
import dev.cel.common.internal.Errors;
import dev.cel.common.types.CelType;
import dev.cel.common.types.CelTypeProvider;
import dev.cel.common.types.CelTypes;
import dev.cel.common.types.ProtoMessageTypeProvider;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * {@code CelChecker} implementation which uses the original CEL-Java APIs to provide a simple,
 * consistent interface for type-checking.
 */
@Immutable
public final class CelCheckerLegacyImpl implements CelChecker, EnvVisitable {

  private final CelOptions celOptions;
  private final String container;
  private final ImmutableList<CelIdentDecl> identDeclarations;
  private final ImmutableList<CelFunctionDecl> functionDeclarations;
  private final Optional<CelType> expectedResultType;

  @SuppressWarnings("Immutable")
  private final TypeProvider typeProvider;

  private final boolean standardEnvironmentEnabled;

  @Override
  public CelValidationResult check(CelAbstractSyntaxTree ast) {
    CelSource source = ast.getSource();
    Errors errors = new Errors(source.getDescription(), source.getContent().toString());
    Env env = getEnv(errors);
    if (errors.getErrorCount() > 0) {
      return new CelValidationResult(source, errorsToIssues(errors));
    }

    CelAbstractSyntaxTree checkedAst =
        ExprChecker.typecheck(env, container, ast, expectedResultType);
    if (errors.getErrorCount() > 0) {
      return new CelValidationResult(source, errorsToIssues(errors));
    }
    return new CelValidationResult(checkedAst, ImmutableList.of());
  }

  @Override
  public void accept(EnvVisitor envVisitor) {
    Errors errors = new Errors("", "");
    Env env = getEnv(errors);
    for (int i = env.scopeDepth(); i >= 0; i--) {
      Env.DeclGroup declGroup = env.getDeclGroup(i);
      SortedSet<String> names = new TreeSet<>();
      names.addAll(declGroup.getIdents().keySet());
      names.addAll(declGroup.getFunctions().keySet());
      for (String name : names) {
        CelIdentDecl ident = declGroup.getIdent(name);
        CelFunctionDecl func = declGroup.getFunction(name);
        List<Decl> decls = new ArrayList<>();
        if (ident != null) {
          decls.add(CelIdentDecl.celIdentToDecl(ident));
        }
        if (func != null) {
          decls.add(CelFunctionDecl.celFunctionDeclToDecl(func));
        }
        envVisitor.visitDecl(name, decls);
      }
    }
  }

  private Env getEnv(Errors errors) {
    Env env;
    if (standardEnvironmentEnabled) {
      env = Env.standard(errors, typeProvider, celOptions);
    } else {
      env = Env.unconfigured(errors, typeProvider, celOptions);
    }
    identDeclarations.forEach(env::add);
    functionDeclarations.forEach(env::add);
    return env;
  }

  /** Create a new builder to construct a {@code CelChecker} instance. */
  public static Builder newBuilder() {
    return new Builder();
  }

  /** Builder class for the legacy {@code CelChecker} implementation. */
  public static final class Builder implements CelCheckerBuilder {

    private final ImmutableList.Builder<CelIdentDecl> identDeclarations;
    private final ImmutableList.Builder<CelFunctionDecl> functionDeclarations;
    private final ImmutableList.Builder<ProtoTypeMask> protoTypeMasks;
    private final ImmutableSet.Builder<Descriptor> messageTypes;
    private final ImmutableSet.Builder<FileDescriptor> fileTypes;
    private final ImmutableSet.Builder<CelCheckerLibrary> celCheckerLibraries;
    private CelOptions celOptions;
    private String container;
    private CelType expectedResultType;
    private TypeProvider customTypeProvider;
    private CelTypeProvider celTypeProvider;
    private boolean standardEnvironmentEnabled;

    /** {@inheritDoc} */
    @Override
    @CanIgnoreReturnValue
    public Builder setOptions(CelOptions celOptions) {
      this.celOptions = checkNotNull(celOptions);
      return this;
    }

    /** {@inheritDoc} */
    @Override
    @CanIgnoreReturnValue
    public Builder setContainer(String container) {
      checkNotNull(container);
      this.container = container;
      return this;
    }

    /** {@inheritDoc} */
    @Override
    @CanIgnoreReturnValue
    public Builder addDeclarations(Decl... declarations) {
      checkNotNull(declarations);
      return addDeclarations(Arrays.asList(declarations));
    }

    /** {@inheritDoc} */
    @Override
    @CanIgnoreReturnValue
    public Builder addDeclarations(Iterable<Decl> declarations) {
      checkNotNull(declarations);
      for (Decl decl : declarations) {
        switch (decl.getDeclKindCase()) {
          case IDENT:
            CelIdentDecl.Builder identBuilder =
                CelIdentDecl.newBuilder()
                    .setName(decl.getName())
                    .setType(CelTypes.typeToCelType(decl.getIdent().getType()))
                    // Note: Setting doc and constant value exists for compatibility reason. This
                    // should not be set by the users.
                    .setDoc(decl.getIdent().getDoc());
            if (decl.getIdent().hasValue()) {
              identBuilder.setConstant(
                  CelExprConverter.exprConstantToCelConstant(decl.getIdent().getValue()));
            }

            this.identDeclarations.add(identBuilder.build());
            break;
          case FUNCTION:
            addFunctionDeclarations(
                CelFunctionDecl.newFunctionDeclaration(
                    decl.getName(),
                    decl.getFunction().getOverloadsList().stream()
                        .map(CelOverloadDecl::overloadToCelOverload)
                        .collect(toImmutableList())));
            break;
          default:
            throw new IllegalArgumentException("unexpected decl kind: " + decl.getDeclKindCase());
        }
      }
      return this;
    }

    /** {@inheritDoc} */
    @Override
    @CanIgnoreReturnValue
    public Builder addFunctionDeclarations(CelFunctionDecl... celFunctionDecls) {
      checkNotNull(celFunctionDecls);
      return addFunctionDeclarations(Arrays.asList(celFunctionDecls));
    }

    /** {@inheritDoc} */
    @Override
    @CanIgnoreReturnValue
    public Builder addFunctionDeclarations(Iterable<CelFunctionDecl> celFunctionDecls) {
      checkNotNull(celFunctionDecls);
      this.functionDeclarations.addAll(celFunctionDecls);
      return this;
    }

    /** {@inheritDoc} */
    @Override
    @CanIgnoreReturnValue
    public Builder addVarDeclarations(CelVarDecl... celVarDecls) {
      checkNotNull(celVarDecls);
      return addVarDeclarations(Arrays.asList(celVarDecls));
    }

    /** {@inheritDoc} */
    @Override
    @CanIgnoreReturnValue
    public Builder addVarDeclarations(Iterable<CelVarDecl> celVarDecls) {
      checkNotNull(celVarDecls);
      for (CelVarDecl celVarDecl : celVarDecls) {
        this.identDeclarations.add(
            CelIdentDecl.newIdentDeclaration(celVarDecl.name(), celVarDecl.type()));
      }
      return this;
    }

    /** {@inheritDoc} */
    @Override
    @CanIgnoreReturnValue
    public Builder addProtoTypeMasks(ProtoTypeMask... typeMasks) {
      checkNotNull(typeMasks);
      return addProtoTypeMasks(Arrays.asList(typeMasks));
    }

    /** {@inheritDoc} */
    @Override
    @CanIgnoreReturnValue
    public Builder addProtoTypeMasks(Iterable<ProtoTypeMask> typeMasks) {
      checkNotNull(typeMasks);
      protoTypeMasks.addAll(typeMasks);
      return this;
    }

    /** {@inheritDoc} */
    @Override
    @CanIgnoreReturnValue
    public Builder setResultType(CelType resultType) {
      checkNotNull(resultType);
      this.expectedResultType = resultType;
      return this;
    }

    /** {@inheritDoc} */
    @Override
    @CanIgnoreReturnValue
    public Builder setProtoResultType(Type resultType) {
      checkNotNull(resultType);
      return setResultType(CelTypes.typeToCelType(resultType));
    }

    /** {@inheritDoc} */
    @CanIgnoreReturnValue
    @Override
    @Deprecated
    public Builder setTypeProvider(TypeProvider typeProvider) {
      this.customTypeProvider = typeProvider;
      return this;
    }

    /** {@inheritDoc} */
    @Override
    @CanIgnoreReturnValue
    public Builder setTypeProvider(CelTypeProvider celTypeProvider) {
      this.celTypeProvider = celTypeProvider;
      return this;
    }

    /** {@inheritDoc} */
    @Override
    @CanIgnoreReturnValue
    public Builder addMessageTypes(Descriptor... descriptors) {
      return addMessageTypes(Arrays.asList(checkNotNull(descriptors)));
    }

    /** {@inheritDoc} */
    @Override
    @CanIgnoreReturnValue
    public Builder addMessageTypes(Iterable<Descriptor> descriptors) {
      this.messageTypes.addAll(checkNotNull(descriptors));
      return this;
    }

    /** {@inheritDoc} */
    @Override
    @CanIgnoreReturnValue
    public Builder addFileTypes(FileDescriptor... fileDescriptors) {
      return addFileTypes(Arrays.asList(checkNotNull(fileDescriptors)));
    }

    /** {@inheritDoc} */
    @Override
    @CanIgnoreReturnValue
    public Builder addFileTypes(Iterable<FileDescriptor> fileDescriptors) {
      this.fileTypes.addAll(checkNotNull(fileDescriptors));
      return this;
    }

    /** {@inheritDoc} */
    @Override
    @CanIgnoreReturnValue
    public Builder addFileTypes(FileDescriptorSet fileDescriptorSet) {
      checkNotNull(fileDescriptorSet);
      return addFileTypes(
          CelDescriptorUtil.getFileDescriptorsFromFileDescriptorSet(fileDescriptorSet));
    }

    /** {@inheritDoc} */
    @Override
    @CanIgnoreReturnValue
    public Builder setStandardEnvironmentEnabled(boolean value) {
      standardEnvironmentEnabled = value;
      return this;
    }

    /** {@inheritDoc} */
    @Override
    @CanIgnoreReturnValue
    public Builder addLibraries(CelCheckerLibrary... libraries) {
      checkNotNull(libraries);
      return this.addLibraries(Arrays.asList(libraries));
    }

    /** {@inheritDoc} */
    @Override
    @CanIgnoreReturnValue
    public Builder addLibraries(Iterable<? extends CelCheckerLibrary> libraries) {
      checkNotNull(libraries);
      this.celCheckerLibraries.addAll(libraries);
      return this;
    }

    /** {@inheritDoc} */
    @Override
    @CheckReturnValue
    public CelCheckerLegacyImpl build() {
      // Add libraries, such as extensions
      celCheckerLibraries.build().forEach(celLibrary -> celLibrary.setCheckerOptions(this));

      // Configure the type provider.
      ImmutableSet<FileDescriptor> fileTypeSet = fileTypes.build();
      ImmutableSet<Descriptor> messageTypeSet = messageTypes.build();
      if (!messageTypeSet.isEmpty()) {
        fileTypeSet =
            new ImmutableSet.Builder<FileDescriptor>()
                .addAll(fileTypeSet)
                .addAll(messageTypeSet.stream().map(Descriptor::getFile).collect(toImmutableSet()))
                .build();
      }

      CelTypeProvider messageTypeProvider =
          new ProtoMessageTypeProvider(
              CelDescriptorUtil.getAllDescriptorsFromFileDescriptor(
                  fileTypeSet, celOptions.resolveTypeDependencies()));
      if (celTypeProvider != null && fileTypeSet.isEmpty()) {
        messageTypeProvider = celTypeProvider;
      } else if (celTypeProvider != null) {
        messageTypeProvider =
            new CelTypeProvider.CombinedCelTypeProvider(
                ImmutableList.of(celTypeProvider, messageTypeProvider));
      }

      // Configure the declaration set, and possibly alter the type provider if ProtoDecl values
      // are provided as they may prevent the use of certain field selection patterns against the
      // proto.
      ImmutableList<CelIdentDecl> identDeclarationSet = identDeclarations.build();
      ImmutableList<ProtoTypeMask> protoTypeMaskSet = protoTypeMasks.build();
      if (!protoTypeMaskSet.isEmpty()) {
        ProtoTypeMaskTypeProvider protoTypeMaskTypeProvider =
            new ProtoTypeMaskTypeProvider(messageTypeProvider, protoTypeMaskSet);
        identDeclarationSet =
            ImmutableList.<CelIdentDecl>builder()
                .addAll(identDeclarationSet)
                .addAll(protoTypeMaskTypeProvider.computeDeclsFromProtoTypeMasks())
                .build();
        messageTypeProvider = protoTypeMaskTypeProvider;
      }

      TypeProvider legacyProvider = new TypeProviderLegacyImpl(messageTypeProvider);
      if (customTypeProvider != null) {
        legacyProvider =
            new TypeProvider.CombinedTypeProvider(
                ImmutableList.of(customTypeProvider, legacyProvider));
      }

      return new CelCheckerLegacyImpl(
          celOptions,
          container,
          identDeclarationSet,
          functionDeclarations.build(),
          Optional.fromNullable(expectedResultType),
          legacyProvider,
          standardEnvironmentEnabled);
    }

    private Builder() {
      this.celOptions = CelOptions.newBuilder().build();
      this.identDeclarations = ImmutableList.builder();
      this.functionDeclarations = ImmutableList.builder();
      this.fileTypes = ImmutableSet.builder();
      this.messageTypes = ImmutableSet.builder();
      this.protoTypeMasks = ImmutableList.builder();
      this.celCheckerLibraries = ImmutableSet.builder();
      this.container = "";
    }
  }

  private CelCheckerLegacyImpl(
      CelOptions celOptions,
      String container,
      ImmutableList<CelIdentDecl> identDeclarations,
      ImmutableList<CelFunctionDecl> functionDeclarations,
      Optional<CelType> expectedResultType,
      TypeProvider typeProvider,
      boolean standardEnvironmentEnabled) {
    this.celOptions = celOptions;
    this.container = container;
    this.identDeclarations = identDeclarations;
    this.functionDeclarations = functionDeclarations;
    this.expectedResultType = expectedResultType;
    this.typeProvider = typeProvider;
    this.standardEnvironmentEnabled = standardEnvironmentEnabled;
  }

  private static ImmutableList<CelIssue> errorsToIssues(Errors errors) {
    ImmutableList<Errors.Error> errorList = errors.getErrors();
    CelIssue.Builder issueBuilder = CelIssue.newBuilder().setSeverity(CelIssue.Severity.ERROR);
    return errorList.stream()
        .map(
            e -> {
              Errors.SourceLocation loc = errors.getPositionLocation(e.position());
              CelSourceLocation newLoc = CelSourceLocation.of(loc.line(), loc.column() - 1);
              return issueBuilder.setMessage(e.rawMessage()).setSourceLocation(newLoc).build();
            })
        .collect(toImmutableList());
  }
}
