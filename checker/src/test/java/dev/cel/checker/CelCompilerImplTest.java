package dev.cel.checker;

import static com.google.common.truth.Truth.assertThat;

import dev.cel.common.CelFunctionDecl;
import dev.cel.common.CelOverloadDecl;
import dev.cel.common.types.SimpleType;
import dev.cel.compiler.CelCompilerBuilder;
import dev.cel.compiler.CelCompilerFactory;
import dev.cel.compiler.CelCompilerImpl;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class CelCompilerImplTest {

  @Test
  public void toCompilerBuilder_isImmutable() {
    CelCompilerBuilder celCompilerBuilder = CelCompilerFactory.standardCelCompilerBuilder();
    CelCompilerImpl celCompiler = (CelCompilerImpl) celCompilerBuilder.build();
    celCompilerBuilder.addFunctionDeclarations(CelFunctionDecl.newFunctionDeclaration("test", CelOverloadDecl.newGlobalOverload("test_id", SimpleType.INT)));

    CelCompilerImpl.Builder newCompilerBuilder = (CelCompilerImpl.Builder) celCompiler.toCompilerBuilder();

    assertThat(newCompilerBuilder).isInstanceOf(CelCompilerBuilder.class);
    assertThat(newCompilerBuilder).isNotEqualTo(celCompilerBuilder);
  }
}
