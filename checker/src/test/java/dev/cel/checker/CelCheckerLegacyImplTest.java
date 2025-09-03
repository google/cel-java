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

package dev.cel.checker;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import dev.cel.checker.CelStandardDeclarations.StandardFunction;
import dev.cel.common.CelContainer;
import dev.cel.common.CelFunctionDecl;
import dev.cel.common.CelOptions;
import dev.cel.common.CelOverloadDecl;
import dev.cel.common.CelVarDecl;
import dev.cel.common.types.CelType;
import dev.cel.common.types.CelTypeProvider;
import dev.cel.common.types.SimpleType;
import dev.cel.compiler.CelCompilerFactory;
import dev.cel.expr.conformance.proto3.TestAllTypes;
import java.util.Optional;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class CelCheckerLegacyImplTest {

  @Test
  public void toCheckerBuilder_isNewInstance() {
    CelCheckerBuilder celCheckerBuilder = CelCompilerFactory.standardCelCheckerBuilder();
    CelCheckerLegacyImpl celChecker = (CelCheckerLegacyImpl) celCheckerBuilder.build();

    CelCheckerLegacyImpl.Builder newCheckerBuilder =
        (CelCheckerLegacyImpl.Builder) celChecker.toCheckerBuilder();

    assertThat(newCheckerBuilder).isNotEqualTo(celCheckerBuilder);
  }

  @Test
  public void toCheckerBuilder_isImmutable() {
    CelCheckerBuilder originalCheckerBuilder = CelCompilerFactory.standardCelCheckerBuilder();
    CelCheckerLegacyImpl celChecker = (CelCheckerLegacyImpl) originalCheckerBuilder.build();
    originalCheckerBuilder.addLibraries(new CelCheckerLibrary() {});

    CelCheckerLegacyImpl.Builder newCheckerBuilder =
        (CelCheckerLegacyImpl.Builder) celChecker.toCheckerBuilder();

    assertThat(newCheckerBuilder.checkerLibraries().build()).isEmpty();
  }

  @Test
  public void toCheckerBuilder_singularFields_copied() {
    CelStandardDeclarations subsetDecls =
        CelStandardDeclarations.newBuilder().includeFunctions(StandardFunction.BOOL).build();
    CelOptions celOptions = CelOptions.current().enableTimestampEpoch(true).build();
    CelContainer celContainer = CelContainer.ofName("foo");
    CelType expectedResultType = SimpleType.BOOL;
    CelTypeProvider customTypeProvider =
        new CelTypeProvider() {
          @Override
          public ImmutableList<CelType> types() {
            return ImmutableList.of();
          }

          @Override
          public Optional<CelType> findType(String typeName) {
            return Optional.empty();
          }
        };
    CelCheckerBuilder celCheckerBuilder =
        CelCompilerFactory.standardCelCheckerBuilder()
            .setOptions(celOptions)
            .setContainer(celContainer)
            .setResultType(expectedResultType)
            .setTypeProvider(customTypeProvider)
            .setStandardEnvironmentEnabled(false)
            .setStandardDeclarations(subsetDecls);
    CelCheckerLegacyImpl celChecker = (CelCheckerLegacyImpl) celCheckerBuilder.build();

    CelCheckerLegacyImpl.Builder newCheckerBuilder =
        (CelCheckerLegacyImpl.Builder) celChecker.toCheckerBuilder();

    assertThat(newCheckerBuilder.standardDeclarations()).isEqualTo(subsetDecls);
    assertThat(newCheckerBuilder.options()).isEqualTo(celOptions);
    assertThat(newCheckerBuilder.container()).isEqualTo(celContainer);
    assertThat(newCheckerBuilder.celTypeProvider()).isEqualTo(customTypeProvider);
  }

  @Test
  public void toCheckerBuilder_collectionProperties_copied() {
    CelCheckerBuilder celCheckerBuilder =
        CelCompilerFactory.standardCelCheckerBuilder()
            .addFunctionDeclarations(
                CelFunctionDecl.newFunctionDeclaration(
                    "test", CelOverloadDecl.newGlobalOverload("test_id", SimpleType.INT)))
            .addVarDeclarations(CelVarDecl.newVarDeclaration("ident", SimpleType.INT))
            .addMessageTypes(TestAllTypes.getDescriptor())
            .addFileTypes(TestAllTypes.getDescriptor().getFile())
            .addProtoTypeMasks(
                ProtoTypeMask.ofAllFields("cel.expr.conformance.proto3.TestAllTypes"))
            .addLibraries(new CelCheckerLibrary() {});
    CelCheckerLegacyImpl celChecker = (CelCheckerLegacyImpl) celCheckerBuilder.build();

    CelCheckerLegacyImpl.Builder newCheckerBuilder =
        (CelCheckerLegacyImpl.Builder) celChecker.toCheckerBuilder();

    assertThat(newCheckerBuilder.functionDecls().build()).hasSize(1);
    assertThat(newCheckerBuilder.identDecls().build()).hasSize(1);
    assertThat(newCheckerBuilder.protoTypeMasks().build()).hasSize(1);
    assertThat(newCheckerBuilder.fileTypes().build())
        .hasSize(1); // MessageTypes and FileTypes deduped into the same file descriptor
    assertThat(newCheckerBuilder.checkerLibraries().build()).hasSize(1);
  }

  @Test
  public void toCheckerBuilder_collectionProperties_areImmutable() {
    CelCheckerBuilder celCheckerBuilder = CelCompilerFactory.standardCelCheckerBuilder();
    CelCheckerLegacyImpl celChecker = (CelCheckerLegacyImpl) celCheckerBuilder.build();
    CelCheckerLegacyImpl.Builder newCheckerBuilder =
        (CelCheckerLegacyImpl.Builder) celChecker.toCheckerBuilder();

    // Mutate the original builder containing collections
    celCheckerBuilder.addFunctionDeclarations(
        CelFunctionDecl.newFunctionDeclaration(
            "test", CelOverloadDecl.newGlobalOverload("test_id", SimpleType.INT)));
    celCheckerBuilder.addVarDeclarations(CelVarDecl.newVarDeclaration("ident", SimpleType.INT));
    celCheckerBuilder.addMessageTypes(TestAllTypes.getDescriptor());
    celCheckerBuilder.addFileTypes(TestAllTypes.getDescriptor().getFile());
    celCheckerBuilder.addProtoTypeMasks(
        ProtoTypeMask.ofAllFields("cel.expr.conformance.proto3.TestAllTypes"));
    celCheckerBuilder.addLibraries(new CelCheckerLibrary() {});

    assertThat(newCheckerBuilder.functionDecls().build()).isEmpty();
    assertThat(newCheckerBuilder.identDecls().build()).isEmpty();
    assertThat(newCheckerBuilder.protoTypeMasks().build()).isEmpty();
    assertThat(newCheckerBuilder.messageTypes().build()).isEmpty();
    assertThat(newCheckerBuilder.fileTypes().build()).isEmpty();
    assertThat(newCheckerBuilder.checkerLibraries().build()).isEmpty();
  }
}
