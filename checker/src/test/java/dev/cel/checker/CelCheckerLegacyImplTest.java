package dev.cel.checker;

import static com.google.common.truth.Truth.assertThat;

import dev.cel.common.CelFunctionDecl;
import dev.cel.common.CelOverloadDecl;
import dev.cel.common.CelVarDecl;
import dev.cel.common.types.SimpleType;
import dev.cel.compiler.CelCompilerFactory;
import dev.cel.testing.testdata.proto2.TestAllTypesProto.TestAllTypes;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class CelCheckerLegacyImplTest {

  @Test
  public void toCheckerBuilder_isImmutable() {
    CelCheckerBuilder celCheckerBuilder = CelCompilerFactory.standardCelCheckerBuilder();
    CelCheckerLegacyImpl celChecker = (CelCheckerLegacyImpl) celCheckerBuilder.build();

    CelCheckerLegacyImpl.Builder newCheckerBuilder = (CelCheckerLegacyImpl.Builder) celChecker.toCheckerBuilder();

    assertThat(newCheckerBuilder).isInstanceOf(CelCheckerBuilder.class);
    assertThat(newCheckerBuilder).isNotEqualTo(celCheckerBuilder);
  }


  @Test
  public void toCheckerBuilder_collectionProperties_areImmutable() {
    CelCheckerBuilder celCheckerBuilder = CelCompilerFactory.standardCelCheckerBuilder();
    CelCheckerLegacyImpl celChecker = (CelCheckerLegacyImpl) celCheckerBuilder.build();
    CelCheckerLegacyImpl.Builder newCheckerBuilder = (CelCheckerLegacyImpl.Builder) celChecker.toCheckerBuilder();

    // Mutate the original builder containing collections
    celCheckerBuilder.addFunctionDeclarations(
        CelFunctionDecl.newFunctionDeclaration("test", CelOverloadDecl.newGlobalOverload("test_id", SimpleType.INT)));
    celCheckerBuilder.addVarDeclarations(CelVarDecl.newVarDeclaration("ident", SimpleType.INT));
    celCheckerBuilder.addMessageTypes(TestAllTypes.getDescriptor());
    celCheckerBuilder.addFileTypes(TestAllTypes.getDescriptor().getFile());
    celCheckerBuilder.addProtoTypeMasks(ProtoTypeMask.ofAllFields("field"));
    celCheckerBuilder.addLibraries(new CelCheckerLibrary() {
      @Override
      public void setCheckerOptions(CelCheckerBuilder checkerBuilder) {}
    });

    assertThat(newCheckerBuilder.getFunctionDecls().build()).isEmpty();
    assertThat(newCheckerBuilder.getIdentDecls().build()).isEmpty();
    assertThat(newCheckerBuilder.getProtoTypeMasks().build()).isEmpty();
    assertThat(newCheckerBuilder.getMessageTypes().build()).isEmpty();
    assertThat(newCheckerBuilder.getFileTypes().build()).isEmpty();
    assertThat(newCheckerBuilder.getCheckerLibraries().build()).isEmpty();
  }
}
