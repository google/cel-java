package dev.cel.protobuf;
import static com.google.common.truth.Truth.assertThat;

import com.google.testing.junit.testparameterinjector.TestParameterInjector;
import dev.cel.common.CelAbstractSyntaxTree;
import dev.cel.common.CelOptions;
import dev.cel.common.values.CelLiteProtoMessageValueProvider;
import dev.cel.compiler.CelCompiler;
import dev.cel.compiler.CelCompilerFactory;
import dev.cel.runtime.CelRuntime;
import dev.cel.runtime.CelRuntimeFactory;
import dev.cel.testing.SimpleTestCelLiteDescriptor;
import dev.cel.testing.SimpleTestOuterClass.SimpleTest;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(TestParameterInjector.class)
public class CelLiteDescriptorGeneratorTest {

  @Test
  public void messageCreation() throws Exception {
    CelCompiler celCompiler = CelCompilerFactory.standardCelCompilerBuilder().addMessageTypes(
        SimpleTest.getDescriptor()).build();
    CelAbstractSyntaxTree ast = celCompiler.compile("dev.cel.testing.SimpleTest{}").getAst();
    CelRuntime celRuntime = CelRuntimeFactory.standardCelRuntimeBuilder()
        .setOptions(CelOptions.current().enableCelValue(true).build())
        .setValueProvider(CelLiteProtoMessageValueProvider.newInstance(SimpleTestCelLiteDescriptor.getDescriptor()))
        .build();

    SimpleTest simpleTest = (SimpleTest) celRuntime.createProgram(ast).eval();

    assertThat(simpleTest).isEqualTo(SimpleTest.getDefaultInstance());
  }
}
