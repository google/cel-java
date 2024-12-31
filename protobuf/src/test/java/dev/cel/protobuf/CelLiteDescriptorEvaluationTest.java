package dev.cel.protobuf;
import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.primitives.UnsignedLong;
import com.google.protobuf.BoolValue;
import com.google.protobuf.ByteString;
import com.google.protobuf.BytesValue;
import com.google.protobuf.DoubleValue;
import com.google.protobuf.FloatValue;
import com.google.protobuf.Int32Value;
import com.google.protobuf.Int64Value;
import com.google.protobuf.NullValue;
import com.google.protobuf.StringValue;
import com.google.protobuf.UInt32Value;
import com.google.protobuf.UInt64Value;
import com.google.protobuf.util.Timestamps;
import com.google.testing.junit.testparameterinjector.TestParameterInjector;
import com.google.testing.junit.testparameterinjector.TestParameters;
import dev.cel.common.CelAbstractSyntaxTree;
import dev.cel.common.CelOptions;
import dev.cel.common.types.SimpleType;
import dev.cel.common.types.StructTypeReference;
import dev.cel.compiler.CelCompiler;
import dev.cel.compiler.CelCompilerFactory;
import dev.cel.expr.conformance.proto3.NestedTestAllTypes;
import dev.cel.expr.conformance.proto3.TestAllTypes;
import dev.cel.expr.conformance.proto3.TestAllTypes.NestedEnum;
import dev.cel.expr.conformance.proto3.TestAllTypes.NestedMessage;
import dev.cel.expr.conformance.proto3.TestAllTypesCelLiteDescriptor;
import dev.cel.parser.CelStandardMacro;
import dev.cel.runtime.CelRuntime;
import dev.cel.runtime.CelRuntimeFactory;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(TestParameterInjector.class)
public class CelLiteDescriptorEvaluationTest {
  private static final CelCompiler CEL_COMPILER =
      CelCompilerFactory.standardCelCompilerBuilder()
          .setStandardMacros(CelStandardMacro.STANDARD_MACROS)
          .addVar("ts", SimpleType.TIMESTAMP)
          .addVar("msg", StructTypeReference.create(TestAllTypes.getDescriptor().getFullName()))
          .addMessageTypes(TestAllTypes.getDescriptor())
          .setContainer("cel.expr.conformance.proto3")
          .build();

  private static final CelRuntime CEL_RUNTIME =
      CelRuntimeFactory.standardCelRuntimeBuilder()
          .setOptions(CelOptions.current().enableCelValue(false).build())
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
        + "single_float: 7.1,"
        + "single_double: 8.2,"
        + "single_nested_enum: TestAllTypes.NestedEnum.BAR,"
        + "repeated_int32: [1,2],"
        + "repeated_int64: [3,4],"
        + "map_string_int32: {'a': 1},"
        + "map_string_int64: {'b': 2},"
        + "single_int32_wrapper: google.protobuf.Int32Value{value: 9},"
        + "single_int64_wrapper: google.protobuf.Int64Value{value: 10},"
        + "single_float_wrapper: 11.1,"
        + "single_double_wrapper: 12.2,"
        + "single_uint32_wrapper: google.protobuf.UInt32Value{value: 13u},"
        + "single_uint64_wrapper: google.protobuf.UInt64Value{value: 14u},"
        + "oneof_type: NestedTestAllTypes {"
        + "    payload: TestAllTypes {"
        + "       single_bytes: b'abc',"
        + "    }"
        + "  },"
        + "}").getAst();
    TestAllTypes expectedMessage = TestAllTypes.newBuilder()
        .setSingleInt32(4)
        .setSingleInt64(6L)
        .setSingleFloat(7.1f)
        .setSingleDouble(8.2d)
        .setSingleNestedEnum(NestedEnum.BAR)
        .addAllRepeatedInt32(Arrays.asList(1,2))
        .addAllRepeatedInt64(Arrays.asList(3L,4L))
        .putMapStringInt32("a", 1)
        .putMapStringInt64("b", 2)
        .setSingleInt32Wrapper(Int32Value.of(9))
        .setSingleInt64Wrapper(Int64Value.of(10L))
        .setSingleFloatWrapper(FloatValue.of(11.1f))
        .setSingleDoubleWrapper(DoubleValue.of(12.2d))
        .setSingleUint32Wrapper(UInt32Value.of(13))
        .setSingleUint64Wrapper(UInt64Value.of(14L))
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
  @TestParameters("{expression: 'msg.single_uint32'}")
  @TestParameters("{expression: 'msg.single_uint64'}")
  public void fieldSelection_unsigned(String expression) throws Exception {
    CelAbstractSyntaxTree ast = CEL_COMPILER.compile(expression).getAst();
    TestAllTypes msg = TestAllTypes.newBuilder()
        .setSingleUint32(4)
        .setSingleUint64(4L)
        .build();

    Object result = CEL_RUNTIME.createProgram(ast).eval(ImmutableMap.of("msg", msg));

    assertThat(result).isEqualTo(UnsignedLong.valueOf(4L));
  }

  @Test
  @TestParameters("{expression: 'msg.repeated_int32'}")
  @TestParameters("{expression: 'msg.repeated_int64'}")
  public void fieldSelection_list(String expression) throws Exception {
    CelAbstractSyntaxTree ast = CEL_COMPILER.compile(expression).getAst();
    TestAllTypes msg = TestAllTypes.newBuilder()
        .addRepeatedInt32(1)
        .addRepeatedInt32(2)
        .addRepeatedInt64(1L)
        .addRepeatedInt64(2L)
        .build();

    List<Long> result = (List<Long>) CEL_RUNTIME.createProgram(ast).eval(ImmutableMap.of("msg", msg));

    assertThat(result).containsExactly(1L, 2L).inOrder();
  }

  @Test
  @TestParameters("{expression: 'msg.map_string_int32'}")
  @TestParameters("{expression: 'msg.map_string_int64'}")
  public void fieldSelection_map(String expression) throws Exception {
    CelAbstractSyntaxTree ast = CEL_COMPILER.compile(expression).getAst();
    TestAllTypes msg = TestAllTypes.newBuilder()
        .putMapStringInt32("a", 1)
        .putMapStringInt32("b", 2)
        .putMapStringInt64("a", 1L)
        .putMapStringInt64("b", 2L)
        .build();

    Map<String, Long> result = (Map<String, Long>) CEL_RUNTIME.createProgram(ast).eval(ImmutableMap.of("msg", msg));

    assertThat(result).containsExactly("a", 1L, "b", 2L);
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
  public void fieldSelection_wrappers(String expression) throws Exception {
    CelAbstractSyntaxTree ast = CEL_COMPILER.compile(expression).getAst();
    TestAllTypes msg = TestAllTypes.newBuilder()
        .setSingleInt32Wrapper(Int32Value.of(1))
        .setSingleInt64Wrapper(Int64Value.of(2L))
        .setSingleUint32Wrapper(UInt32Value.of(3))
        .setSingleUint64Wrapper(UInt64Value.of(4L))
        .setSingleFloatWrapper(FloatValue.of(1.5f))
        .setSingleDoubleWrapper(DoubleValue.of(2.5d))
        .setSingleBoolWrapper(BoolValue.of(true))
        .setSingleStringWrapper(StringValue.of("foo"))
        .setSingleBytesWrapper(BytesValue.of(ByteString.copyFromUtf8("abc")))
        .build();

    boolean result = (boolean) CEL_RUNTIME.createProgram(ast).eval(ImmutableMap.of("msg", msg));

    assertThat(result).isTrue();
  }

  @Test
  @TestParameters("{expression: 'msg.single_int32_wrapper'}")
  @TestParameters("{expression: 'msg.single_int64_wrapper'}")
  @TestParameters("{expression: 'msg.single_uint32_wrapper'}")
  @TestParameters("{expression: 'msg.single_uint64_wrapper'}")
  @TestParameters("{expression: 'msg.single_float_wrapper'}")
  @TestParameters("{expression: 'msg.single_double_wrapper'}")
  @TestParameters("{expression: 'msg.single_bool_wrapper'}")
  @TestParameters("{expression: 'msg.single_string_wrapper'}")
  @TestParameters("{expression: 'msg.single_bytes_wrapper'}")
  public void fieldSelection_wrappersNullability(String expression) throws Exception {
    CelAbstractSyntaxTree ast = CEL_COMPILER.compile(expression).getAst();
    TestAllTypes msg = TestAllTypes.newBuilder().build();

    Object result = CEL_RUNTIME.createProgram(ast).eval(ImmutableMap.of("msg", msg));

    assertThat(result).isEqualTo(NullValue.NULL_VALUE);
  }

  @Test
  @TestParameters("{expression: 'has(msg.single_int32)'}")
  @TestParameters("{expression: 'has(msg.single_int64)'}")
  @TestParameters("{expression: 'has(msg.single_int32_wrapper)'}")
  @TestParameters("{expression: 'has(msg.single_int64_wrapper)'}")
  @TestParameters("{expression: 'has(msg.repeated_int32)'}")
  @TestParameters("{expression: 'has(msg.repeated_int64)'}")
  @TestParameters("{expression: 'has(msg.repeated_int32_wrapper)'}")
  @TestParameters("{expression: 'has(msg.repeated_int64_wrapper)'}")
  @TestParameters("{expression: 'has(msg.map_string_int32)'}")
  @TestParameters("{expression: 'has(msg.map_string_int64)'}")
  @TestParameters("{expression: 'has(msg.map_bool_int32_wrapper)'}")
  @TestParameters("{expression: 'has(msg.map_bool_int64_wrapper)'}")
  public void presenceTest_evaluatesToFalse(String expression) throws Exception {
    CelAbstractSyntaxTree ast = CEL_COMPILER.compile(expression).getAst();
    TestAllTypes msg = TestAllTypes.newBuilder()
        .setSingleInt32(0)
        .addAllRepeatedInt32(ImmutableList.of())
        .addAllRepeatedInt32Wrapper(ImmutableList.of())
        .putAllMapBoolInt32(ImmutableMap.of())
        .putAllMapBoolInt32Wrapper(ImmutableMap.of())
        .build();

    boolean result = (boolean) CEL_RUNTIME.createProgram(ast).eval(ImmutableMap.of("msg", msg));

    assertThat(result).isFalse();
  }

  @Test
  @TestParameters("{expression: 'has(msg.single_int32)'}")
  @TestParameters("{expression: 'has(msg.single_int64)'}")
  @TestParameters("{expression: 'has(msg.single_int32_wrapper)'}")
  @TestParameters("{expression: 'has(msg.single_int64_wrapper)'}")
  @TestParameters("{expression: 'has(msg.repeated_int32)'}")
  @TestParameters("{expression: 'has(msg.repeated_int64)'}")
  @TestParameters("{expression: 'has(msg.repeated_int32_wrapper)'}")
  @TestParameters("{expression: 'has(msg.repeated_int64_wrapper)'}")
  @TestParameters("{expression: 'has(msg.map_string_int32)'}")
  @TestParameters("{expression: 'has(msg.map_string_int64)'}")
  @TestParameters("{expression: 'has(msg.map_string_int32_wrapper)'}")
  @TestParameters("{expression: 'has(msg.map_string_int64_wrapper)'}")
  public void presenceTest_evaluatesToTrue(String expression) throws Exception {
    CelAbstractSyntaxTree ast = CEL_COMPILER.compile(expression).getAst();
    TestAllTypes msg = TestAllTypes.newBuilder()
        .setSingleInt32(1)
        .setSingleInt64(2)
        .setSingleInt32Wrapper(Int32Value.of(0))
        .setSingleInt64Wrapper(Int64Value.of(0))
        .addAllRepeatedInt32(ImmutableList.of(1))
        .addAllRepeatedInt64(ImmutableList.of(2L))
        .addAllRepeatedInt32Wrapper(ImmutableList.of(Int32Value.of(0)))
        .addAllRepeatedInt64Wrapper(ImmutableList.of(Int64Value.of(0L)))
        .putAllMapStringInt32Wrapper(ImmutableMap.of("a", Int32Value.of(1)))
        .putAllMapStringInt64Wrapper(ImmutableMap.of("b", Int64Value.of(2L)))
        .putMapStringInt32("a", 1)
        .putMapStringInt64("b", 2)
        .build();

    boolean result = (boolean) CEL_RUNTIME.createProgram(ast).eval(ImmutableMap.of("msg", msg));

    assertThat(result).isTrue();
  }

  @Test
  public void nestedMessage() throws Exception {
    CelAbstractSyntaxTree ast = CEL_COMPILER.compile("msg.single_nested_message.bb == 43 && has(msg.single_nested_message)").getAst();
    TestAllTypes nestedMessage =
        TestAllTypes.newBuilder()
            .setSingleNestedMessage(NestedMessage.newBuilder().setBb(43))
            .build();

    boolean result = (boolean) CEL_RUNTIME.createProgram(ast).eval(ImmutableMap.of("msg", nestedMessage));

    assertThat(result).isTrue();
  }

  @Test
  public void enumSelection() throws Exception {
    CelAbstractSyntaxTree ast = CEL_COMPILER.compile("msg.single_nested_enum").getAst();
    TestAllTypes nestedMessage =
        TestAllTypes.newBuilder()
            .setSingleNestedEnum(NestedEnum.BAR)
            .build();

    Long result = (Long) CEL_RUNTIME.createProgram(ast).eval(ImmutableMap.of("msg", nestedMessage));

    assertThat(result).isEqualTo(NestedEnum.BAR.getNumber());
  }

  @Test
  public void jsonStruct() throws Exception {
    CelAbstractSyntaxTree ast = CEL_COMPILER.compile("google.protobuf.Struct { fields: {'timestamp': ts } }").getAst();

    Object result =  CEL_RUNTIME.createProgram(ast).eval(ImmutableMap.of("ts", Timestamps.fromSeconds(100)));

    assertThat(result).isNotNull();
  }
}
