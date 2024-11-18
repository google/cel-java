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
import com.google.common.annotations.VisibleForTesting;
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
import dev.cel.common.annotations.Internal;
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
 *
 * <p>CEL Library Internals. Do Not Use. Consumers should use {@code CelCompilerFactory} to
 * instantiate a type-checker.
 */
@Immutable
@Internal
public final class CelCheckerLegacyImpl implements CelChecker, EnvVisitable {

  private final CelOptions celOptions;
  private final String container;
  private final ImmutableSet<CelIdentDecl> identDeclarations;
  private final ImmutableSet<CelFunctionDecl> functionDeclarations;
  private final Optional<CelType> expectedResultType;

  @SuppressWarnings("Immutable")
  private final TypeProvider typeProvider;

  private final CelTypeProvider celTypeProvider;
  private final boolean standardEnvironmentEnabled;

  private final CelStandardDeclarations overriddenStandardDeclarations;

  // Builder is mutable by design. APIs must make defensive copies in and out of this class.
  @SuppressWarnings("Immutable")
  private final Builder checkerBuilder;

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
  public CelTypeProvider getTypeProvider() {
    return this.celTypeProvider;
  }

  @Override
  public CelCheckerBuilder toCheckerBuilder() {
    return new Builder(checkerBuilder);
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
    } else if (overriddenStandardDeclarations != null) {
      env = Env.standard(overriddenStandardDeclarations, errors, typeProvider, celOptions);
    } else {
      env = Env.unconfigured(errors, typeProvider, celOptions);
    }
    identDeclarations.forEach(env::add);
    functionDeclarations.forEach(env::add);
    return env;
  }

  /** Create a new builder to construct a {@code CelChecker} instance. */
  public static CelCheckerBuilder newBuilder() {
    return new Builder();
  }

  /** Builder class for the legacy {@code CelChecker} implementation. */
  public static final class Builder implements CelCheckerBuilder {

    private final ImmutableSet.Builder<CelIdentDecl> identDeclarations;
    private final ImmutableSet.Builder<CelFunctionDecl> functionDeclarations;
    private final ImmutableSet.Builder<ProtoTypeMask> protoTypeMasks;
    private final ImmutableSet.Builder<Descriptor> messageTypes;
    private final ImmutableSet.Builder<FileDescriptor> fileTypes;
    private final ImmutableSet.Builder<CelCheckerLibrary> celCheckerLibraries;
    private CelOptions celOptions;
    private String container;
    private CelType expectedResultType;
    private TypeProvider customTypeProvider;
    private CelTypeProvider celTypeProvider;
    private boolean standardEnvironmentEnabled;
    private CelStandardDeclarations standardDeclarations;

    @Override
    public CelCheckerBuilder setOptions(CelOptions celOptions) {
      this.celOptions = checkNotNull(celOptions);
      return this;
    }

    @Override
    public CelCheckerBuilder setContainer(String container) {
      checkNotNull(container);
      this.container = container;
      return this;
    }

    @Override
    public CelCheckerBuilder addDeclarations(Decl... declarations) {
      checkNotNull(declarations);
      return addDeclarations(Arrays.asList(declarations));
    }

    @Override
    public CelCheckerBuilder addDeclarations(Iterable<Decl> declarations) {
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

    @Override
    @CanIgnoreReturnValue
    public CelCheckerBuilder addFunctionDeclarations(CelFunctionDecl... celFunctionDecls) {
      checkNotNull(celFunctionDecls);
      return addFunctionDeclarations(Arrays.asList(celFunctionDecls));
    }

    @Override
    public CelCheckerBuilder addFunctionDeclarations(Iterable<CelFunctionDecl> celFunctionDecls) {
      checkNotNull(celFunctionDecls);
      this.functionDeclarations.addAll(celFunctionDecls);
      return this;
    }

    @Override
    public CelCheckerBuilder addVarDeclarations(CelVarDecl... celVarDecls) {
      checkNotNull(celVarDecls);
      return addVarDeclarations(Arrays.asList(celVarDecls));
    }

    @Override
    public CelCheckerBuilder addVarDeclarations(Iterable<CelVarDecl> celVarDecls) {
      checkNotNull(celVarDecls);
      for (CelVarDecl celVarDecl : celVarDecls) {
        this.identDeclarations.add(
            CelIdentDecl.newIdentDeclaration(celVarDecl.name(), celVarDecl.type()));
      }
      return this;
    }

    @Override
    public CelCheckerBuilder addProtoTypeMasks(ProtoTypeMask... typeMasks) {
      checkNotNull(typeMasks);
      return addProtoTypeMasks(Arrays.asList(typeMasks));
    }

    @Override
    public CelCheckerBuilder addProtoTypeMasks(Iterable<ProtoTypeMask> typeMasks) {
      checkNotNull(typeMasks);
      protoTypeMasks.addAll(typeMasks);
      return this;
    }

    @Override
    public CelCheckerBuilder setResultType(CelType resultType) {
      checkNotNull(resultType);
      this.expectedResultType = resultType;
      return this;
    }

    @Override
    public CelCheckerBuilder setProtoResultType(Type resultType) {
      checkNotNull(resultType);
      return setResultType(CelTypes.typeToCelType(resultType));
    }

    @Override
    @Deprecated
    public CelCheckerBuilder setTypeProvider(TypeProvider typeProvider) {
      this.customTypeProvider = typeProvider;
      return this;
    }

    @Override
    public CelCheckerBuilder setTypeProvider(CelTypeProvider celTypeProvider) {
      this.celTypeProvider = celTypeProvider;
      return this;
    }

    @Override
    public CelCheckerBuilder addMessageTypes(Descriptor... descriptors) {
      return addMessageTypes(Arrays.asList(checkNotNull(descriptors)));
    }

    @Override
    public CelCheckerBuilder addMessageTypes(Iterable<Descriptor> descriptors) {
      this.messageTypes.addAll(checkNotNull(descriptors));
      return this;
    }

    @Override
    public CelCheckerBuilder addFileTypes(FileDescriptor... fileDescriptors) {
      return addFileTypes(Arrays.asList(checkNotNull(fileDescriptors)));
    }

    @Override
    public CelCheckerBuilder addFileTypes(Iterable<FileDescriptor> fileDescriptors) {
      this.fileTypes.addAll(checkNotNull(fileDescriptors));
      return this;
    }

    @Override
    public CelCheckerBuilder addFileTypes(FileDescriptorSet fileDescriptorSet) {
      checkNotNull(fileDescriptorSet);
      return addFileTypes(
          CelDescriptorUtil.getFileDescriptorsFromFileDescriptorSet(fileDescriptorSet));
    }

    @Override
    public CelCheckerBuilder setStandardEnvironmentEnabled(boolean value) {
      this.standardEnvironmentEnabled = value;
      return this;
    }

    @Override
    public CelCheckerBuilder setStandardDeclarations(CelStandardDeclarations standardDeclarations) {
      this.standardDeclarations = checkNotNull(standardDeclarations);
      return this;
    }

    @Override
    public CelCheckerBuilder addLibraries(CelCheckerLibrary... libraries) {
      checkNotNull(libraries);
      return this.addLibraries(Arrays.asList(libraries));
    }

    @Override
    public CelCheckerBuilder addLibraries(Iterable<? extends CelCheckerLibrary> libraries) {
      checkNotNull(libraries);
      this.celCheckerLibraries.addAll(libraries);
      return this;
    }

    // The following getters exist for asserting immutability for collections held by this builder,
    // and shouldn't be exposed to the public.
    @VisibleForTesting
    ImmutableSet.Builder<CelFunctionDecl> getFunctionDecls() {
      return this.functionDeclarations;
    }

    @VisibleForTesting
    ImmutableSet.Builder<CelIdentDecl> getIdentDecls() {
      return this.identDeclarations;
    }

    @VisibleForTesting
    ImmutableSet.Builder<ProtoTypeMask> getProtoTypeMasks() {
      return this.protoTypeMasks;
    }

    @VisibleForTesting
    ImmutableSet.Builder<Descriptor> getMessageTypes() {
      return this.messageTypes;
    }

    @VisibleForTesting
    ImmutableSet.Builder<FileDescriptor> getFileTypes() {
      return this.fileTypes;
    }

    @VisibleForTesting
    ImmutableSet.Builder<CelCheckerLibrary> getCheckerLibraries() {
      return this.celCheckerLibraries;
    }

    @Override
    @CheckReturnValue
    public CelCheckerLegacyImpl build() {
      if (standardEnvironmentEnabled && standardDeclarations != null) {
        throw new IllegalArgumentException(
            "setStandardEnvironmentEnabled must be set to false to override standard"
                + " declarations.");
      }
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
      ImmutableSet<CelIdentDecl> identDeclarationSet = identDeclarations.build();
      ImmutableSet<ProtoTypeMask> protoTypeMaskSet = protoTypeMasks.build();
      if (!protoTypeMaskSet.isEmpty()) {
        ProtoTypeMaskTypeProvider protoTypeMaskTypeProvider =
            new ProtoTypeMaskTypeProvider(messageTypeProvider, protoTypeMaskSet);
        identDeclarationSet =
            ImmutableSet.<CelIdentDecl>builder()
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
          messageTypeProvider,
          standardEnvironmentEnabled,
          standardDeclarations,
          this);
    }

    private Builder() {
      this.celOptions = CelOptions.newBuilder().build();
      this.identDeclarations = ImmutableSet.builder();
      this.functionDeclarations = ImmutableSet.builder();
      this.fileTypes = ImmutableSet.builder();
      this.messageTypes = ImmutableSet.builder();
      this.protoTypeMasks = ImmutableSet.builder();
      this.celCheckerLibraries = ImmutableSet.builder();
      this.container = "";
    }

    private Builder(Builder builder) {
      // The following properties are either immutable or simple primitives, thus can be assigned
      // directly.
      this.celOptions = builder.celOptions;
      this.celTypeProvider = builder.celTypeProvider;
      this.container = builder.container;
      this.customTypeProvider = builder.customTypeProvider;
      this.expectedResultType = builder.expectedResultType;
      this.standardEnvironmentEnabled = builder.standardEnvironmentEnabled;
      // The following needs to be deep copied as they are collection builders
      this.functionDeclarations = deepCopy(builder.functionDeclarations);
      this.identDeclarations = deepCopy(builder.identDeclarations);
      this.fileTypes = deepCopy(builder.fileTypes);
      this.messageTypes = deepCopy(builder.messageTypes);
      this.protoTypeMasks = deepCopy(builder.protoTypeMasks);
      this.celCheckerLibraries = deepCopy(builder.celCheckerLibraries);
    }

    private static <T> ImmutableSet.Builder<T> deepCopy(ImmutableSet.Builder<T> builderToCopy) {
      ImmutableSet.Builder<T> newBuilder = ImmutableSet.builder();
      newBuilder.addAll(builderToCopy.build());
      return newBuilder;
    }
  }

  private CelCheckerLegacyImpl(
      CelOptions celOptions,
      String container,
      ImmutableSet<CelIdentDecl> identDeclarations,
      ImmutableSet<CelFunctionDecl> functionDeclarations,
      Optional<CelType> expectedResultType,
      TypeProvider typeProvider,
      CelTypeProvider celTypeProvider,
      boolean standardEnvironmentEnabled,
      CelStandardDeclarations overriddenStandardDeclarations,
      Builder checkerBuilder) {
    this.celOptions = celOptions;
    this.container = container;
    this.identDeclarations = identDeclarations;
    this.functionDeclarations = functionDeclarations;
    this.expectedResultType = expectedResultType;
    this.typeProvider = typeProvider;
    this.celTypeProvider = celTypeProvider;
    this.standardEnvironmentEnabled = standardEnvironmentEnabled;
    this.overriddenStandardDeclarations = overriddenStandardDeclarations;
    this.checkerBuilder = new Builder(checkerBuilder);
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
