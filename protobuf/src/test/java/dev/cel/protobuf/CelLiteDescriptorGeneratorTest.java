package dev.cel.protobuf;
import static com.google.common.truth.Truth.assertThat;

import com.google.common.primitives.Ints;
import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.Internal;
import com.google.protobuf.Message;
import com.google.protobuf.MessageLite;
import com.google.testing.junit.testparameterinjector.TestParameterInjector;
import dev.cel.common.CelAbstractSyntaxTree;
import dev.cel.common.CelOptions;
import dev.cel.common.internal.AdaptingTypes;
import dev.cel.common.internal.BidiConverter;
import dev.cel.common.internal.Converter;
import dev.cel.common.internal.ProtoJavaQualifiedNames;
import dev.cel.common.values.ProtoMessageLiteValueProvider;
import dev.cel.compiler.CelCompiler;
import dev.cel.compiler.CelCompilerFactory;
import dev.cel.expr.conformance.proto3.TestAllTypes;
import dev.cel.expr.conformance.proto3.TestAllTypes.NestedEnum;
import dev.cel.expr.conformance.proto3.TestAllTypesCelLiteDescriptor;
import dev.cel.runtime.CelRuntime;
import dev.cel.runtime.CelRuntimeFactory;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(TestParameterInjector.class)
public class CelLiteDescriptorGeneratorTest {
  private static final CelCompiler CEL_COMPILER =
      CelCompilerFactory.standardCelCompilerBuilder()
          .addMessageTypes(TestAllTypes.getDescriptor())
          .build();

  private static final CelRuntime CEL_RUNTIME =
      CelRuntimeFactory.standardCelRuntimeBuilder()
          .setOptions(CelOptions.current().enableCelValue(true).build())
          .setValueProvider(ProtoMessageLiteValueProvider.newInstance(
              TestAllTypesCelLiteDescriptor.getDescriptor()))
          .build();

  @Test
  public void messageCreation_emptyMessage() throws Exception {
    CelAbstractSyntaxTree ast = CEL_COMPILER.compile("cel.expr.conformance.proto3.TestAllTypes{}").getAst();

    TestAllTypes simpleTest = (TestAllTypes) CEL_RUNTIME.createProgram(ast).eval();

    assertThat(simpleTest).isEqualTo(TestAllTypes.getDefaultInstance());
  }

  public void foo(List<String> a) {

  }

  @Test
  public void messageCreation_fieldsPopulated() throws Exception {
    CelAbstractSyntaxTree ast = CEL_COMPILER.compile("cel.expr.conformance.proto3.TestAllTypes{"
        + "single_int32: 4,"
        + "single_int64: 6,"
        + "single_nested_enum: cel.expr.conformance.proto3.TestAllTypes.NestedEnum.BAR,"
        + "repeated_int32: [1,2],"
        + "repeated_int64: [3,4],"
        + "map_string_int32: {'a': 1},"
        + "map_string_int64: {'b': 2}"
        + "}").getAst();
    TestAllTypes expectedMessage = TestAllTypes.newBuilder()
        .setSingleInt32(4)
        .setSingleInt64(6L)
        .setSingleNestedEnum(NestedEnum.BAR)
        .addAllRepeatedInt32(Arrays.asList(1,2))
        .addAllRepeatedInt64(Arrays.asList(3L,4L))
        .putMapStringInt32("a", 1)
        .putMapStringInt64("b", 2)
        .build();

    TestAllTypes simpleTest = (TestAllTypes) CEL_RUNTIME.createProgram(ast).eval();

    assertThat(simpleTest).isEqualTo(expectedMessage);
  }
}
