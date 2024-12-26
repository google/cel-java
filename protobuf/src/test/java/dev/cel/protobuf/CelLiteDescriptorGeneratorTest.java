package dev.cel.protobuf;
import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableMap;
import com.google.protobuf.ByteString;
import com.google.protobuf.Int32Value;
import com.google.testing.junit.testparameterinjector.TestParameterInjector;
import com.google.testing.junit.testparameterinjector.TestParameters;
import dev.cel.common.CelAbstractSyntaxTree;
import dev.cel.common.CelOptions;
import dev.cel.common.types.StructTypeReference;
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
          .addCelLiteDescriptors(TestAllTypesCelLiteDescriptor.getDescriptor())
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
  @TestParameters("{expression: 'msg.single_int32 == 1'}")
  @TestParameters("{expression: 'msg.single_int64 == 2'}")
  @TestParameters("{expression: 'msg.single_uint32 == 3u'}")
  @TestParameters("{expression: 'msg.single_uint64 == 4u'}")
  @TestParameters("{expression: 'msg.single_sint32 == 5'}")
  @TestParameters("{expression: 'msg.single_sint64 == 6'}")
  @TestParameters("{expression: 'msg.single_fixed32 == 7u'}")
  @TestParameters("{expression: 'msg.single_fixed64 == 8u'}")
  @TestParameters("{expression: 'msg.single_sfixed32 == 9'}")
  @TestParameters("{expression: 'msg.single_sfixed64 == 10'}")
  @TestParameters("{expression: 'msg.single_float == 1.5'}")
  @TestParameters("{expression: 'msg.single_double == 2.5'}")
  @TestParameters("{expression: 'msg.single_bool == true'}")
  @TestParameters("{expression: 'msg.single_string == \"foo\"'}")
  @TestParameters("{expression: 'msg.single_bytes == b\"abc\"'}")
  @TestParameters("{expression: 'msg.optional_bool == true'}")
  public void fieldSelection_literals(String expression) throws Exception {
    CelAbstractSyntaxTree ast = CEL_COMPILER.compile(expression).getAst();
    TestAllTypes msg = TestAllTypes.newBuilder()
        .setSingleInt32(1)
        .setSingleInt64(2L)
        .setSingleUint32(3)
        .setSingleUint64(4L)
        .setSingleSint32(5)
        .setSingleSint64(6L)
        .setSingleFixed32(7)
        .setSingleFixed64(8L)
        .setSingleSfixed32(9)
        .setSingleSfixed64(10L)
        .setSingleFloat(1.5f)
        .setSingleDouble(2.5d)
        .setSingleBool(true)
        .setSingleString("foo")
        .setSingleBytes(ByteString.copyFromUtf8("abc"))
        .setOptionalBool(true)
        .build();

    boolean result = (boolean) CEL_RUNTIME.createProgram(ast).eval(ImmutableMap.of("msg", msg));

    assertThat(result).isTrue();
  }

  @Test
  public void smokeTest() throws Exception {
    CelAbstractSyntaxTree ast = CEL_COMPILER.compile("msg.single_int32_wrapper").getAst();
    TestAllTypes msg = TestAllTypes.newBuilder()
        .setSingleInt32Wrapper(Int32Value.of(5))
        .build();

    long result = (long) CEL_RUNTIME.createProgram(ast).eval(ImmutableMap.of("msg", msg));

    assertThat(result).isEqualTo(5L);
  }

  @Test
  @TestParameters("{expression: 'msg.single_int32_wrapper == 1'}")
  @TestParameters("{expression: 'msg.single_int64_wrapper == 2'}")
  @TestParameters("{expression: 'msg.single_uint32_wrapper == 3u'}")
  @TestParameters("{expression: 'msg.single_uint64_wrapper == 4u'}")
  @TestParameters("{expression: 'msg.single_float_wrapper == 1.5'}")
  @TestParameters("{expression: 'msg.single_double_wrapper == 2.5'}")
  @TestParameters("{expression: 'msg.single_bool_wrapper == true'}")
  @TestParameters("{expression: 'msg.single_string_wrapper == \"foo\"'}")
  @TestParameters("{expression: 'msg.single_bytes_wrapper == b\"abc\"'}")
  public void fieldSelection_wellKnownTypes(String expression) throws Exception {
    CelAbstractSyntaxTree ast = CEL_COMPILER.compile(expression).getAst();
    TestAllTypes msg = TestAllTypes.newBuilder()
        .setSingleInt32(1)
        .setSingleInt64(2L)
        .setSingleUint32(3)
        .setSingleUint64(4L)
        .setSingleSint32(5)
        .setSingleSint64(6L)
        .setSingleFixed32(7)
        .setSingleFixed64(8L)
        .setSingleSfixed32(9)
        .setSingleSfixed64(10L)
        .setSingleFloat(1.5f)
        .setSingleDouble(2.5d)
        .setSingleBool(true)
        .setSingleString("foo")
        .setSingleBytes(ByteString.copyFromUtf8("abc"))
        .setOptionalBool(true)
        .build();

    boolean result = (boolean) CEL_RUNTIME.createProgram(ast).eval(ImmutableMap.of("msg", msg));

    assertThat(result).isTrue();
  }
}
