package dev.cel.protobuf;
import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableMap;
import com.google.protobuf.ByteString;
import com.google.testing.junit.testparameterinjector.TestParameterInjector;
import dev.cel.common.CelAbstractSyntaxTree;
import dev.cel.common.CelOptions;
import dev.cel.common.types.StructTypeReference;
import dev.cel.common.values.ProtoMessageLiteValueProvider;
import dev.cel.compiler.CelCompiler;
import dev.cel.compiler.CelCompilerFactory;
import dev.cel.expr.conformance.proto3.NestedTestAllTypes;
import dev.cel.expr.conformance.proto3.TestAllTypes;
import dev.cel.expr.conformance.proto3.TestAllTypes.NestedEnum;
import dev.cel.expr.conformance.proto3.TestAllTypesCelLiteDescriptor;
import dev.cel.runtime.CelRuntime;
import dev.cel.runtime.CelRuntimeFactory;
import java.util.Arrays;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(TestParameterInjector.class)
public class CelLiteDescriptorGeneratorTest {
  private static final CelCompiler CEL_COMPILER =
      CelCompilerFactory.standardCelCompilerBuilder()
          .addVar("msg", StructTypeReference.create(TestAllTypes.getDescriptor().getFullName()))
          .addMessageTypes(TestAllTypes.getDescriptor())
          .setContainer("cel.expr.conformance.proto3")
          .build();

  private static final CelRuntime CEL_RUNTIME =
      CelRuntimeFactory.standardCelRuntimeBuilder()
          .setOptions(CelOptions.current().enableCelValue(true).build())
          .setValueProvider(ProtoMessageLiteValueProvider.newInstance(
              TestAllTypesCelLiteDescriptor.getDescriptor()))
          .build();

  @Test
  public void messageCreation_emptyMessage() throws Exception {
    CelAbstractSyntaxTree ast = CEL_COMPILER.compile("TestAllTypes{}").getAst();

    TestAllTypes simpleTest = (TestAllTypes) CEL_RUNTIME.createProgram(ast).eval();

    assertThat(simpleTest).isEqualTo(TestAllTypes.getDefaultInstance());
  }

  @Test
  public void messageCreation_fieldsPopulated() throws Exception {
    CelAbstractSyntaxTree ast = CEL_COMPILER.compile("TestAllTypes{"
        + "single_int32: 4,"
        + "single_int64: 6,"
        + "single_nested_enum: TestAllTypes.NestedEnum.BAR,"
        + "repeated_int32: [1,2],"
        + "repeated_int64: [3,4],"
        + "map_string_int32: {'a': 1},"
        + "map_string_int64: {'b': 2},"
        + "oneof_type: NestedTestAllTypes {"
        + "    payload: TestAllTypes {"
        + "       single_bytes: b'abc',"
        + "    }"
        + "  },"
        + "}").getAst();
    TestAllTypes expectedMessage = TestAllTypes.newBuilder()
        .setSingleInt32(4)
        .setSingleInt64(6L)
        .setSingleNestedEnum(NestedEnum.BAR)
        .addAllRepeatedInt32(Arrays.asList(1,2))
        .addAllRepeatedInt64(Arrays.asList(3L,4L))
        .putMapStringInt32("a", 1)
        .putMapStringInt64("b", 2)
        .setOneofType(
            NestedTestAllTypes.newBuilder().setPayload(
                TestAllTypes.newBuilder().setSingleBytes(
                    ByteString.copyFromUtf8("abc"))))
        .build();

    TestAllTypes simpleTest = (TestAllTypes) CEL_RUNTIME.createProgram(ast).eval();

    assertThat(simpleTest).isEqualTo(expectedMessage);
  }

  @Test
  public void fieldSelection() throws Exception {
    CelAbstractSyntaxTree ast = CEL_COMPILER.compile("msg.single_int64").getAst();

    Long result = (Long) CEL_RUNTIME.createProgram(ast).eval(ImmutableMap.of("msg", TestAllTypes.newBuilder().setSingleInt64(6L)));

    assertThat(result).isEqualTo(6L);
  }
}
