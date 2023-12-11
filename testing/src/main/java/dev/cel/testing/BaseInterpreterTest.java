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

package dev.cel.testing;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.truth.Truth.assertThat;

import dev.cel.expr.CheckedExpr;
import dev.cel.expr.ExprValue;
import dev.cel.expr.Type;
import dev.cel.expr.Type.AbstractType;
import dev.cel.expr.UnknownSet;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.primitives.UnsignedLong;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.protobuf.Any;
import com.google.protobuf.BoolValue;
import com.google.protobuf.ByteString;
import com.google.protobuf.BytesValue;
import com.google.protobuf.Descriptors.FileDescriptor;
import com.google.protobuf.DoubleValue;
import com.google.protobuf.Duration;
import com.google.protobuf.DynamicMessage;
import com.google.protobuf.FieldMask;
import com.google.protobuf.FloatValue;
import com.google.protobuf.Int32Value;
import com.google.protobuf.Int64Value;
import com.google.protobuf.ListValue;
import com.google.protobuf.NullValue;
import com.google.protobuf.StringValue;
import com.google.protobuf.Struct;
import com.google.protobuf.Timestamp;
import com.google.protobuf.UInt32Value;
import com.google.protobuf.UInt64Value;
import com.google.protobuf.Value;
import com.google.protobuf.util.Durations;
import com.google.protobuf.util.Timestamps;
import dev.cel.common.CelAbstractSyntaxTree;
import dev.cel.common.CelProtoAbstractSyntaxTree;
import dev.cel.common.internal.DefaultDescriptorPool;
import dev.cel.common.types.CelTypes;
import dev.cel.runtime.Activation;
import dev.cel.runtime.InterpreterException;
import dev.cel.runtime.PartialMessage;
import dev.cel.testing.testdata.proto3.StandaloneGlobalEnum;
import dev.cel.testing.testdata.proto3.TestAllTypesProto.TestAllTypes;
import dev.cel.testing.testdata.proto3.TestAllTypesProto.TestAllTypes.NestedEnum;
import dev.cel.testing.testdata.proto3.TestAllTypesProto.TestAllTypes.NestedMessage;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.LongStream;
import org.junit.Test;

/** Base class for evaluation outputs that can be stored and used as a baseline test. */
public abstract class BaseInterpreterTest extends CelBaselineTestCase {

  protected static final ImmutableList<FileDescriptor> TEST_FILE_DESCRIPTORS =
      ImmutableList.of(
          TestAllTypes.getDescriptor().getFile(), StandaloneGlobalEnum.getDescriptor().getFile());

  private final Eval eval;

  public BaseInterpreterTest(boolean declareWithCelType, Eval eval) {
    super(declareWithCelType);
    this.eval = eval;
  }

  /** Helper to run a test for configured instance variables. */
  @CanIgnoreReturnValue // Test generates a file to diff against baseline. Ignoring Intermediary
  // evaluation is not a concern.
  private Object runTest(Activation activation) throws Exception {
    CelAbstractSyntaxTree ast = prepareTest(eval.fileDescriptors());
    if (ast == null) {
      return null;
    }
    assertAstRoundTrip(ast);

    testOutput().println("bindings: " + activation);
    Object result = null;
    try {
      result = eval.eval(ast, activation);
      if (result instanceof ByteString) {
        // Note: this call may fail for printing byte sequences that are not valid UTF-8, but works
        // pretty well for test purposes.
        result = ((ByteString) result).toStringUtf8();
      }
      testOutput().println("result:   " + result);
    } catch (InterpreterException e) {
      testOutput().println("error:    " + e.getMessage());
      testOutput().println("error_code:    " + e.getErrorCode());
    }
    testOutput().println();
    return result;
  }

  /**
   * Checks that the CheckedExpr produced by CelCompiler is equal to the one reproduced by the
   * native CelAbstractSyntaxTree
   */
  private void assertAstRoundTrip(CelAbstractSyntaxTree ast) {
    CheckedExpr checkedExpr = CelProtoAbstractSyntaxTree.fromCelAst(ast).toCheckedExpr();
    CelProtoAbstractSyntaxTree protoAst = CelProtoAbstractSyntaxTree.fromCelAst(ast);
    assertThat(checkedExpr).isEqualTo(protoAst.toCheckedExpr());
  }

  @Test
  public void arithmInt64() throws Exception {
    source = "1 < 2 && 1 <= 1 && 2 > 1 && 1 >= 1 && 1 == 1 && 2 != 1";
    runTest(Activation.EMPTY);

    declareVariable("x", CelTypes.INT64);
    source = "1 + 2 - x * 3 / x + (x % 3)";
    runTest(Activation.of("x", -5L));

    declareVariable("y", CelTypes.DYN);
    source = "x + y == 1";
    runTest(Activation.of("x", -5L).extend(Activation.of("y", 6)));
  }

  @Test
  public void arithmInt64_error() throws Exception {
    source = "9223372036854775807 + 1";
    runTest(Activation.EMPTY);

    source = "-9223372036854775808 - 1";
    runTest(Activation.EMPTY);

    source = "-(-9223372036854775808)";
    runTest(Activation.EMPTY);

    source = "5000000000 * 5000000000";
    runTest(Activation.EMPTY);

    source = "(-9223372036854775808)/-1";
    runTest(Activation.EMPTY);

    source = "1 / 0";
    runTest(Activation.EMPTY);

    source = "1 % 0";
    runTest(Activation.EMPTY);
  }

  @Test
  public void arithmUInt64() throws Exception {
    source = "1u < 2u && 1u <= 1u && 2u > 1u && 1u >= 1u && 1u == 1u && 2u != 1u";
    runTest(Activation.EMPTY);

    boolean useUnsignedLongs = eval.celOptions().enableUnsignedLongs();
    declareVariable("x", CelTypes.UINT64);
    source = "1u + 2u + x * 3u / x + (x % 3u)";
    runTest(Activation.of("x", useUnsignedLongs ? UnsignedLong.valueOf(5L) : 5L));

    declareVariable("y", CelTypes.DYN);
    source = "x + y == 11u";
    runTest(
        Activation.of("x", useUnsignedLongs ? UnsignedLong.valueOf(5L) : 5L)
            .extend(Activation.of("y", useUnsignedLongs ? UnsignedLong.valueOf(6L) : 6)));

    source = "x - y == 1u";
    runTest(
        Activation.of("x", useUnsignedLongs ? UnsignedLong.valueOf(6L) : 6L)
            .extend(Activation.of("y", useUnsignedLongs ? UnsignedLong.valueOf(5) : 5)));
  }

  @Test
  public void arithmUInt64_error() throws Exception {
    source = "18446744073709551615u + 1u";
    runTest(Activation.EMPTY);

    source = "0u - 1u";
    runTest(Activation.EMPTY);

    source = "5000000000u * 5000000000u";
    runTest(Activation.EMPTY);

    source = "1u / 0u";
    runTest(Activation.EMPTY);

    source = "1u % 0u";
    runTest(Activation.EMPTY);
  }

  @Test
  public void arithmDouble() throws Exception {
    source = "1.9 < 2.0 && 1.1 <= 1.1 && 2.0 > 1.9 && 1.1 >= 1.1 && 1.1 == 1.1 && 2.0 != 1.9";
    runTest(Activation.EMPTY);

    declareVariable("x", CelTypes.DOUBLE);
    source = "1.0 + 2.3 + x * 3.0 / x";
    runTest(Activation.of("x", 3.33));

    declareVariable("y", CelTypes.DYN);
    source = "x + y == 9.99";
    runTest(Activation.of("x", 3.33d).extend(Activation.of("y", 6.66)));
  }

  @Test
  public void quantifiers() throws Exception {
    source = "[1,-2,3].exists_one(x, x > 0)";
    runTest(Activation.EMPTY);

    source = "[-1,-2,3].exists_one(x, x > 0)";
    runTest(Activation.EMPTY);

    source = "[-1,-2,-3].exists(x, x > 0)";
    runTest(Activation.EMPTY);

    source = "[1,-2,3].exists(x, x > 0)";
    runTest(Activation.EMPTY);

    source = "[1,-2,3].all(x, x > 0)";
    runTest(Activation.EMPTY);

    source = "[1,2,3].all(x, x > 0)";
    runTest(Activation.EMPTY);
  }

  @Test
  public void arithmTimestamp() throws Exception {
    container = Type.getDescriptor().getFile().getPackage();
    declareVariable("ts1", CelTypes.TIMESTAMP);
    declareVariable("ts2", CelTypes.TIMESTAMP);
    declareVariable("d1", CelTypes.DURATION);
    Duration d1 = Duration.newBuilder().setSeconds(15).setNanos(25).build();
    Timestamp ts1 = Timestamp.newBuilder().setSeconds(25).setNanos(35).build();
    Timestamp ts2 = Timestamp.newBuilder().setSeconds(10).setNanos(10).build();
    Activation activation =
        Activation.of("d1", d1).extend(Activation.of("ts1", ts1)).extend(Activation.of("ts2", ts2));

    source = "ts1 - ts2 == d1";
    runTest(activation);

    source = "ts1 - d1 == ts2";
    runTest(activation);

    source = "ts2 + d1 == ts1";
    runTest(activation);

    source = "d1 + ts2 == ts1";
    runTest(activation);
  }

  @Test
  public void arithmDuration() throws Exception {
    container = Type.getDescriptor().getFile().getPackage();
    declareVariable("d1", CelTypes.DURATION);
    declareVariable("d2", CelTypes.DURATION);
    declareVariable("d3", CelTypes.DURATION);
    Duration d1 = Duration.newBuilder().setSeconds(15).setNanos(25).build();
    Duration d2 = Duration.newBuilder().setSeconds(10).setNanos(20).build();
    Duration d3 = Duration.newBuilder().setSeconds(25).setNanos(45).build();
    Activation activation =
        Activation.of("d1", d1).extend(Activation.of("d2", d2)).extend(Activation.of("d3", d3));

    source = "d1 + d2 == d3";
    runTest(activation);

    source = "d3 - d1 == d2";
    runTest(activation);
  }

  @Test
  public void arithCrossNumericTypes() throws Exception {
    if (!eval.celOptions().enableUnsignedLongs()) {
      skipBaselineVerification();
      return;
    }
    source = "1.9 < 2 && 1 < 1.1 && 2u < 2.9 && 1.1 < 2u && 1 < 2u && 2u < 3";
    runTest(Activation.EMPTY);

    source = "1.9 <= 2 && 1 <= 1.1 && 2u <= 2.9 && 1.1 <= 2u && 2 <= 2u && 2u <= 2";
    runTest(Activation.EMPTY);

    source = "1.9 > 2 && 1 > 1.1 && 2u > 2.9 && 1.1 > 2u && 2 > 2u && 2u > 2";
    runTest(Activation.EMPTY);

    source = "1.9 >= 2 && 1 >= 1.1 && 2u >= 2.9 && 1.1 >= 2u && 2 >= 2u && 2u >= 2";
    runTest(Activation.EMPTY);
  }

  @Test
  public void booleans() throws Exception {
    declareVariable("x", CelTypes.BOOL);
    source = "x ? 1 : 0";
    runTest(Activation.of("x", true));
    runTest(Activation.of("x", false));

    source = "(1 / 0 == 0 && false) == (false && 1 / 0 == 0)";
    runTest(Activation.EMPTY);

    source = "(1 / 0 == 0 || true) == (true || 1 / 0 == 0)";
    runTest(Activation.EMPTY);

    declareVariable("y", CelTypes.INT64);
    source = "1 / y == 1 || true";
    runTest(Activation.of("y", 0L));

    source = "1 / y == 1 || false";
    runTest(Activation.of("y", 0L));

    source = "false || 1 / y == 1";
    runTest(Activation.of("y", 0L));

    source = "1 / y == 1 && true";
    runTest(Activation.of("y", 0L));

    source = "true && 1 / y == 1";
    runTest(Activation.of("y", 0L));

    source = "1 / y == 1 && false";
    runTest(Activation.of("y", 0L));

    source = "(true > false) == true";
    runTest(Activation.EMPTY);

    source = "(true > true) == false";
    runTest(Activation.EMPTY);

    source = "(false > true) == false";
    runTest(Activation.EMPTY);

    source = "(false > false) == false";
    runTest(Activation.EMPTY);

    source = "(true >= false) == true";
    runTest(Activation.EMPTY);

    source = "(true >= true) == true";
    runTest(Activation.EMPTY);

    source = "(false >= false) == true";
    runTest(Activation.EMPTY);

    source = "(false >= true) == false";
    runTest(Activation.EMPTY);

    source = "(false < true) == true";
    runTest(Activation.EMPTY);

    source = "(false < false) == false";
    runTest(Activation.EMPTY);

    source = "(true < false) == false";
    runTest(Activation.EMPTY);

    source = "(true < true) == false";
    runTest(Activation.EMPTY);

    source = "(false <= true) == true";
    runTest(Activation.EMPTY);

    source = "(false <= false) == true";
    runTest(Activation.EMPTY);

    source = "(true <= false) == false";
    runTest(Activation.EMPTY);

    source = "(true <= true) == true";
    runTest(Activation.EMPTY);
  }

  @Test
  public void strings() throws Exception {
    source = "'a' < 'b' && 'a' <= 'b' && 'b' > 'a' && 'a' >= 'a' && 'a' == 'a' && 'a' != 'b'";
    runTest(Activation.EMPTY);

    declareVariable("x", CelTypes.STRING);
    source =
        "'abc' + x == 'abcdef' && "
            + "x.endsWith('ef') && "
            + "x.startsWith('d') && "
            + "x.contains('de') && "
            + "!x.contains('abcdef')";
    runTest(Activation.of("x", "def"));
  }

  @Test
  public void messages() throws Exception {
    TestAllTypes nestedMessage =
        TestAllTypes.newBuilder()
            .setSingleNestedMessage(NestedMessage.newBuilder().setBb(43))
            .build();
    declareVariable("x", CelTypes.createMessage(TestAllTypes.getDescriptor().getFullName()));
    source = "x.single_nested_message.bb == 43 && has(x.single_nested_message)";
    runTest(Activation.of("x", nestedMessage));

    declareVariable(
        "single_nested_message",
        CelTypes.createMessage(NestedMessage.getDescriptor().getFullName()));
    source = "single_nested_message.bb == 43";
    runTest(Activation.of("single_nested_message", nestedMessage.getSingleNestedMessage()));

    source = "TestAllTypes{single_int64: 1, single_sfixed64: 2, single_int32: 2}.single_int32 == 2";
    container = TestAllTypes.getDescriptor().getFile().getPackage();
    runTest(Activation.EMPTY);
  }

  @Test
  public void messages_error() throws Exception {
    source = "TestAllTypes{single_int32_wrapper: 12345678900}";
    container = TestAllTypes.getDescriptor().getFile().getPackage();
    runTest(Activation.EMPTY);

    source = "TestAllTypes{}.map_string_string.a";
    runTest(Activation.EMPTY);
  }

  @Test
  public void has() throws Exception {
    TestAllTypes nestedMessage =
        TestAllTypes.newBuilder()
            .setSingleInt32(1)
            .setSingleInt64(0L)
            .setSingleBoolWrapper(BoolValue.newBuilder().setValue(false))
            .setSingleInt32Wrapper(Int32Value.newBuilder().setValue(42))
            .setOptionalBool(false)
            .setOneofBool(false)
            .addRepeatedInt32(1)
            .putMapInt32Int64(1, 2L)
            .setSingleNestedMessage(NestedMessage.newBuilder().setBb(43))
            .build();
    declareVariable("x", CelTypes.createMessage(TestAllTypes.getDescriptor().getFullName()));
    source =
        "has(x.single_int32) && !has(x.single_int64) && has(x.single_bool_wrapper)"
            + " && has(x.single_int32_wrapper) && !has(x.single_int64_wrapper)"
            + " && has(x.repeated_int32) && !has(x.repeated_int64)"
            + " && has(x.optional_bool) && !has(x.optional_string)"
            + " && has(x.oneof_bool) && !has(x.oneof_type)"
            + " && has(x.map_int32_int64) && !has(x.map_string_string)"
            + " && has(x.single_nested_message) && !has(x.single_duration)";
    runTest(Activation.of("x", nestedMessage));
  }

  @Test
  public void duration() throws Exception {
    declareVariable("d1", CelTypes.DURATION);
    declareVariable("d2", CelTypes.DURATION);
    Duration d1010 = Duration.newBuilder().setSeconds(10).setNanos(10).build();
    Duration d1009 = Duration.newBuilder().setSeconds(10).setNanos(9).build();
    Duration d0910 = Duration.newBuilder().setSeconds(9).setNanos(10).build();
    container = Type.getDescriptor().getFile().getPackage();

    source = "d1 < d2";
    runTest(Activation.of("d1", d1010).extend(Activation.of("d2", d1009)));
    runTest(Activation.of("d1", d1010).extend(Activation.of("d2", d0910)));
    runTest(Activation.of("d1", d1010).extend(Activation.of("d2", d1010)));
    runTest(Activation.of("d1", d1009).extend(Activation.of("d2", d1010)));
    runTest(Activation.of("d1", d0910).extend(Activation.of("d2", d1010)));

    source = "d1 <= d2";
    runTest(Activation.of("d1", d1010).extend(Activation.of("d2", d1009)));
    runTest(Activation.of("d1", d1010).extend(Activation.of("d2", d0910)));
    runTest(Activation.of("d1", d1010).extend(Activation.of("d2", d1010)));
    runTest(Activation.of("d1", d1009).extend(Activation.of("d2", d1010)));
    runTest(Activation.of("d1", d0910).extend(Activation.of("d2", d1010)));

    source = "d1 > d2";
    runTest(Activation.of("d1", d1010).extend(Activation.of("d2", d1009)));
    runTest(Activation.of("d1", d1010).extend(Activation.of("d2", d0910)));
    runTest(Activation.of("d1", d1010).extend(Activation.of("d2", d1010)));
    runTest(Activation.of("d1", d1009).extend(Activation.of("d2", d1010)));
    runTest(Activation.of("d1", d0910).extend(Activation.of("d2", d1010)));

    source = "d1 >= d2";
    runTest(Activation.of("d1", d1010).extend(Activation.of("d2", d1009)));
    runTest(Activation.of("d1", d1010).extend(Activation.of("d2", d0910)));
    runTest(Activation.of("d1", d1010).extend(Activation.of("d2", d1010)));
    runTest(Activation.of("d1", d1009).extend(Activation.of("d2", d1010)));
    runTest(Activation.of("d1", d0910).extend(Activation.of("d2", d1010)));
  }

  @Test
  public void timestamp() throws Exception {
    declareVariable("t1", CelTypes.TIMESTAMP);
    declareVariable("t2", CelTypes.TIMESTAMP);
    Timestamp ts1010 = Timestamp.newBuilder().setSeconds(10).setNanos(10).build();
    Timestamp ts1009 = Timestamp.newBuilder().setSeconds(10).setNanos(9).build();
    Timestamp ts0910 = Timestamp.newBuilder().setSeconds(9).setNanos(10).build();
    container = Type.getDescriptor().getFile().getPackage();

    source = "t1 < t2";
    runTest(Activation.of("t1", ts1010).extend(Activation.of("t2", ts1009)));
    runTest(Activation.of("t1", ts1010).extend(Activation.of("t2", ts0910)));
    runTest(Activation.of("t1", ts1010).extend(Activation.of("t2", ts1010)));
    runTest(Activation.of("t1", ts1009).extend(Activation.of("t2", ts1010)));
    runTest(Activation.of("t1", ts0910).extend(Activation.of("t2", ts1010)));

    source = "t1 <= t2";
    runTest(Activation.of("t1", ts1010).extend(Activation.of("t2", ts1009)));
    runTest(Activation.of("t1", ts1010).extend(Activation.of("t2", ts0910)));
    runTest(Activation.of("t1", ts1010).extend(Activation.of("t2", ts1010)));
    runTest(Activation.of("t1", ts1009).extend(Activation.of("t2", ts1010)));
    runTest(Activation.of("t1", ts0910).extend(Activation.of("t2", ts1010)));

    source = "t1 > t2";
    runTest(Activation.of("t1", ts1010).extend(Activation.of("t2", ts1009)));
    runTest(Activation.of("t1", ts1010).extend(Activation.of("t2", ts0910)));
    runTest(Activation.of("t1", ts1010).extend(Activation.of("t2", ts1010)));
    runTest(Activation.of("t1", ts1009).extend(Activation.of("t2", ts1010)));
    runTest(Activation.of("t1", ts0910).extend(Activation.of("t2", ts1010)));

    source = "t1 >= t2";
    runTest(Activation.of("t1", ts1010).extend(Activation.of("t2", ts1009)));
    runTest(Activation.of("t1", ts1010).extend(Activation.of("t2", ts0910)));
    runTest(Activation.of("t1", ts1010).extend(Activation.of("t2", ts1010)));
    runTest(Activation.of("t1", ts1009).extend(Activation.of("t2", ts1010)));
    runTest(Activation.of("t1", ts0910).extend(Activation.of("t2", ts1010)));
  }

  @Test
  // TODO: Support JSON type pack/unpack google.protobuf.Any.
  public void packUnpackAny() throws Exception {
    // The use of long values results in the incorrect type being serialized for a uint value.
    if (!eval.celOptions().enableUnsignedLongs()) {
      skipBaselineVerification();
      return;
    }
    container = TestAllTypes.getDescriptor().getFile().getPackage();
    declareVariable("any", CelTypes.ANY);
    declareVariable("d", CelTypes.DURATION);
    declareVariable("message", CelTypes.createMessage(TestAllTypes.getDescriptor().getFullName()));
    Duration duration = Durations.fromSeconds(100);
    Any any = Any.pack(duration);
    TestAllTypes message = TestAllTypes.newBuilder().setSingleAny(any).build();

    // unpack any
    source = "any == d";
    runTest(Activation.of("any", any).extend(Activation.of("d", duration)));
    source = "any == message.single_any";
    runTest(Activation.of("any", any).extend(Activation.of("message", message)));
    source = "d == message.single_any";
    runTest(Activation.of("d", duration).extend(Activation.of("message", message)));
    source = "any.single_int64 == 1";
    runTest(Activation.of("any", TestAllTypes.newBuilder().setSingleInt64(1).build()));
    source = "any == 1";
    runTest(Activation.of("any", Any.pack(Int64Value.of(1))));

    // pack any
    source = "TestAllTypes{single_any: d}";
    runTest(Activation.of("d", duration));
    source = "TestAllTypes{single_any: message.single_int64}";
    runTest(Activation.of("message", TestAllTypes.newBuilder().setSingleInt64(-1).build()));
    source = "TestAllTypes{single_any: message.single_uint64}";
    runTest(Activation.of("message", TestAllTypes.newBuilder().setSingleUint64(1).build()));
    source = "TestAllTypes{single_any: 1.0}";
    runTest(Activation.EMPTY);
    source = "TestAllTypes{single_any: true}";
    runTest(Activation.EMPTY);
    source = "TestAllTypes{single_any: \"happy\"}";
    runTest(Activation.EMPTY);
    source = "TestAllTypes{single_any: message.single_bytes}";
    runTest(
        Activation.of(
            "message",
            TestAllTypes.newBuilder().setSingleBytes(ByteString.copyFromUtf8("happy")).build()));
  }

  @Test
  public void nestedEnums() throws Exception {
    TestAllTypes nestedEnum = TestAllTypes.newBuilder().setSingleNestedEnum(NestedEnum.BAR).build();
    declareVariable("x", CelTypes.createMessage(TestAllTypes.getDescriptor().getFullName()));
    container = TestAllTypes.getDescriptor().getFile().getPackage();
    source = "x.single_nested_enum == TestAllTypes.NestedEnum.BAR";
    runTest(Activation.of("x", nestedEnum));

    declareVariable("single_nested_enum", CelTypes.INT64);
    source = "single_nested_enum == TestAllTypes.NestedEnum.BAR";
    runTest(Activation.of("single_nested_enum", nestedEnum.getSingleNestedEnumValue()));

    source =
        "TestAllTypes{single_nested_enum : TestAllTypes.NestedEnum.BAR}.single_nested_enum == 1";
    runTest(Activation.EMPTY);
  }

  @Test
  public void globalEnums() throws Exception {
    declareVariable("x", CelTypes.INT64);
    source = "x == dev.cel.testing.testdata.proto3.StandaloneGlobalEnum.SGAR";
    runTest(Activation.of("x", StandaloneGlobalEnum.SGAR.getNumber()));
  }

  @Test
  public void lists() throws Exception {
    declareVariable("x", CelTypes.createMessage(TestAllTypes.getDescriptor().getFullName()));
    declareVariable("y", CelTypes.INT64);
    container = TestAllTypes.getDescriptor().getFile().getPackage();
    source = "([1, 2, 3] + x.repeated_int32)[3] == 4";
    runTest(Activation.of("x", TestAllTypes.newBuilder().addRepeatedInt32(4).build()));

    source = "!(y in [1, 2, 3]) && y in [4, 5, 6]";
    runTest(Activation.of("y", 4L));

    source = "TestAllTypes{repeated_int32: [1,2]}.repeated_int32[1] == 2";
    runTest(Activation.EMPTY);

    source = "1 in TestAllTypes{repeated_int32: [1,2]}.repeated_int32";
    runTest(Activation.EMPTY);

    source = "!(4 in [1, 2, 3]) && 1 in [1, 2, 3]";
    runTest(Activation.EMPTY);

    declareVariable("list", CelTypes.createList(CelTypes.INT64));

    source = "!(4 in list) && 1 in list";
    runTest(Activation.of("list", ImmutableList.of(1L, 2L, 3L)));

    source = "!(y in list)";
    runTest(Activation.copyOf(ImmutableMap.of("y", 4L, "list", ImmutableList.of(1L, 2L, 3L))));

    source = "y in list";
    runTest(Activation.copyOf(ImmutableMap.of("y", 1L, "list", ImmutableList.of(1L, 2L, 3L))));

    source = "list[3]";
    runTest(Activation.copyOf(ImmutableMap.of("y", 1L, "list", ImmutableList.of(1L, 2L, 3L))));
  }

  @Test
  public void maps() throws Exception {
    declareVariable("x", CelTypes.createMessage(TestAllTypes.getDescriptor().getFullName()));
    container = TestAllTypes.getDescriptor().getFile().getPackage();
    source = "{1: 2, 3: 4}[3] == 4";
    runTest(Activation.EMPTY);

    // Constant key in constant map.
    source = "3 in {1: 2, 3: 4} && !(4 in {1: 2, 3: 4})";
    runTest(Activation.EMPTY);

    source = "x.map_int32_int64[22] == 23";
    runTest(Activation.of("x", TestAllTypes.newBuilder().putMapInt32Int64(22, 23).build()));

    source = "TestAllTypes{map_int32_int64: {21: 22, 22: 23}}.map_int32_int64[22] == 23";
    runTest(Activation.EMPTY);

    source =
        "TestAllTypes{oneof_type: NestedTestAllTypes{payload: x}}"
            + ".oneof_type.payload.map_int32_int64[22] == 23";
    runTest(Activation.of("x", TestAllTypes.newBuilder().putMapInt32Int64(22, 23).build()));

    declareVariable("y", CelTypes.INT64);
    declareVariable("map", CelTypes.createMap(CelTypes.INT64, CelTypes.INT64));

    // Constant key in variable map.
    source = "!(4 in map) && 1 in map";
    runTest(Activation.of("map", ImmutableMap.of(1L, 4L, 2L, 3L, 3L, 2L)));

    // Variable key in constant map.
    source = "!(y in {1: 4, 2: 3, 3: 2}) && y in {5: 3, 4: 2, 3: 3}";
    runTest(Activation.of("y", 4L));

    // Variable key in variable map.
    source = "!(y in map) && (y + 3) in map";
    runTest(
        Activation.copyOf(
            ImmutableMap.of("y", 1L, "map", ImmutableMap.of(4L, 1L, 5L, 2L, 6L, 3L))));

    // Message value in map
    source = "TestAllTypes{map_int64_nested_type:{42:NestedTestAllTypes{payload:TestAllTypes{}}}}";
    runTest(Activation.EMPTY);

    // Repeated key - constant
    source = "{true: 1, false: 2, true: 3}[true]";
    runTest(Activation.EMPTY);

    // Repeated key - expressions
    declareVariable("b", CelTypes.BOOL);
    source = "{b: 1, !b: 2, b: 3}[true]";
    runTest(Activation.of("b", true));
  }

  @Test
  public void comprehension() throws Exception {
    source = "[0, 1, 2].map(x, x > 0, x + 1) == [2, 3]";
    runTest(Activation.EMPTY);

    source = "[0, 1, 2].exists(x, x > 0)";
    runTest(Activation.EMPTY);

    source = "[0, 1, 2].exists(x, x > 2)";
    runTest(Activation.EMPTY);
  }

  @Test
  public void abstractType() throws Exception {
    Type typeParam = CelTypes.createTypeParam("T");
    Type abstractType =
        Type.newBuilder()
            .setAbstractType(
                AbstractType.newBuilder().setName("vector").addParameterTypes(typeParam))
            .build();
    // Declare a function to create a vector.
    declareFunction(
        "vector",
        globalOverload(
            "vector",
            ImmutableList.of(CelTypes.createList(typeParam)),
            ImmutableList.of("T"),
            abstractType));
    eval.registrar()
        .add(
            "vector",
            ImmutableList.of(List.class),
            (Object[] args) -> {
              List<?> list = (List<?>) args[0];
              return list.toArray(new Object[0]);
            });
    // Declare a function to access element of a vector.
    declareFunction(
        "at",
        memberOverload(
            "at",
            ImmutableList.of(abstractType, CelTypes.INT64),
            ImmutableList.of("T"),
            typeParam));
    eval.registrar()
        .add(
            "at",
            ImmutableList.of(Object[].class, Long.class),
            (Object[] args) -> {
              Object[] array = (Object[]) args[0];
              return array[(int) (long) args[1]];
            });

    source = "vector([1,2,3]).at(1) == 2";
    runTest(Activation.EMPTY);

    source = "vector([1,2,3]).at(1) + vector([7]).at(0)";
    runTest(Activation.EMPTY);
  }

  @Test
  public void namespacedFunctions() throws Exception {
    declareFunction(
        "ns.func",
        globalOverload("ns_func_overload", ImmutableList.of(CelTypes.STRING), CelTypes.INT64));
    declareFunction(
        "member",
        memberOverload(
            "ns_member_overload",
            ImmutableList.of(CelTypes.INT64, CelTypes.INT64),
            CelTypes.INT64));
    eval.registrar().add("ns_func_overload", String.class, s -> (long) s.length());
    eval.registrar().add("ns_member_overload", Long.class, Long.class, Long::sum);
    source = "ns.func('hello')";
    runTest(Activation.EMPTY);

    source = "ns.func('hello').member(ns.func('test'))";
    runTest(Activation.EMPTY);

    source = "{ns.func('test'): 2}";
    runTest(Activation.EMPTY);

    source = "{2: ns.func('test')}";
    runTest(Activation.EMPTY);

    source = "[ns.func('test'), 2]";
    runTest(Activation.EMPTY);

    source = "[ns.func('test')].map(x, x * 2)";
    runTest(Activation.EMPTY);

    source = "[1, 2].map(x, x * ns.func('test'))";
    runTest(Activation.EMPTY);

    container = "ns";
    // Call with the container set as the function's namespace
    source = "ns.func('hello')";
    runTest(Activation.EMPTY);

    source = "func('hello')";
    runTest(Activation.EMPTY);

    source = "func('hello').member(func('test'))";
    runTest(Activation.EMPTY);
  }

  @Test
  public void namespacedVariables() throws Exception {
    container = "ns";
    declareVariable("ns.x", CelTypes.INT64);
    source = "x";
    runTest(Activation.of("ns.x", 2));

    container = "dev.cel.testing.testdata.proto3";
    Type messageType = CelTypes.createMessage("dev.cel.testing.testdata.proto3.TestAllTypes");
    declareVariable("dev.cel.testing.testdata.proto3.msgVar", messageType);
    source = "msgVar.single_int32";
    runTest(
        Activation.of(
            "dev.cel.testing.testdata.proto3.msgVar",
            TestAllTypes.newBuilder().setSingleInt32(5).build()));
  }

  @Test
  public void durationFunctions() throws Exception {
    declareVariable("d1", CelTypes.DURATION);
    Duration d1 =
        Duration.newBuilder().setSeconds(25 * 3600 + 59 * 60 + 1).setNanos(11000000).build();
    Duration d2 =
        Duration.newBuilder().setSeconds(-(25 * 3600 + 59 * 60 + 1)).setNanos(-11000000).build();
    container = Type.getDescriptor().getFile().getPackage();

    source = "d1.getHours()";
    runTest(Activation.of("d1", d1));
    runTest(Activation.of("d1", d2));

    source = "d1.getMinutes()";
    runTest(Activation.of("d1", d1));
    runTest(Activation.of("d1", d2));

    source = "d1.getSeconds()";
    runTest(Activation.of("d1", d1));
    runTest(Activation.of("d1", d2));

    source = "d1.getMilliseconds()";
    runTest(Activation.of("d1", d1));
    runTest(Activation.of("d1", d2));

    declareVariable("val", CelTypes.INT64);
    source = "d1.getHours() < val";
    runTest(Activation.of("d1", d1).extend(Activation.of("val", 30L)));
    source = "d1.getMinutes() > val";
    runTest(Activation.of("d1", d1).extend(Activation.of("val", 30L)));
    source = "d1.getSeconds() > val";
    runTest(Activation.of("d1", d1).extend(Activation.of("val", 30L)));
    source = "d1.getMilliseconds() < val";
    runTest(Activation.of("d1", d1).extend(Activation.of("val", 30L)));
  }

  @Test
  public void timestampFunctions() throws Exception {
    declareVariable("ts1", CelTypes.TIMESTAMP);
    container = Type.getDescriptor().getFile().getPackage();
    Timestamp ts1 = Timestamp.newBuilder().setSeconds(1).setNanos(11000000).build();
    Timestamp ts2 = Timestamps.fromSeconds(-1);

    source = "ts1.getFullYear(\"America/Los_Angeles\")";
    runTest(Activation.of("ts1", ts1));
    source = "ts1.getFullYear()";
    runTest(Activation.of("ts1", ts1));
    runTest(Activation.of("ts1", ts2));
    source = "ts1.getFullYear(\"Indian/Cocos\")";
    runTest(Activation.of("ts1", ts1));
    source = "ts1.getFullYear(\"2:00\")";
    runTest(Activation.of("ts1", ts1));

    source = "ts1.getMonth(\"America/Los_Angeles\")";
    runTest(Activation.of("ts1", ts1));
    source = "ts1.getMonth()";
    runTest(Activation.of("ts1", ts1));
    runTest(Activation.of("ts1", ts2));
    source = "ts1.getMonth(\"Indian/Cocos\")";
    runTest(Activation.of("ts1", ts1));
    source = "ts1.getMonth(\"-8:15\")";
    runTest(Activation.of("ts1", ts1));

    source = "ts1.getDayOfYear(\"America/Los_Angeles\")";
    runTest(Activation.of("ts1", ts1));
    source = "ts1.getDayOfYear()";
    runTest(Activation.of("ts1", ts1));
    runTest(Activation.of("ts1", ts2));
    source = "ts1.getDayOfYear(\"Indian/Cocos\")";
    runTest(Activation.of("ts1", ts1));
    source = "ts1.getDayOfYear(\"-9:00\")";
    runTest(Activation.of("ts1", ts1));

    source = "ts1.getDayOfMonth(\"America/Los_Angeles\")";
    runTest(Activation.of("ts1", ts1));
    source = "ts1.getDayOfMonth()";
    runTest(Activation.of("ts1", ts1));
    runTest(Activation.of("ts1", ts2));
    source = "ts1.getDayOfMonth(\"Indian/Cocos\")";
    runTest(Activation.of("ts1", ts1));
    source = "ts1.getDayOfMonth(\"8:00\")";
    runTest(Activation.of("ts1", ts1));

    source = "ts1.getDate(\"America/Los_Angeles\")";
    runTest(Activation.of("ts1", ts1));
    source = "ts1.getDate()";
    runTest(Activation.of("ts1", ts1));
    runTest(Activation.of("ts1", ts2));
    source = "ts1.getDate(\"Indian/Cocos\")";
    runTest(Activation.of("ts1", ts1));
    source = "ts1.getDate(\"9:30\")";
    runTest(Activation.of("ts1", ts1));

    Timestamp tsSunday = Timestamps.fromSeconds(3 * 24 * 3600);
    source = "ts1.getDayOfWeek(\"America/Los_Angeles\")";
    runTest(Activation.of("ts1", tsSunday));
    source = "ts1.getDayOfWeek()";
    runTest(Activation.of("ts1", tsSunday));
    runTest(Activation.of("ts1", ts2));
    source = "ts1.getDayOfWeek(\"Indian/Cocos\")";
    runTest(Activation.of("ts1", tsSunday));
    source = "ts1.getDayOfWeek(\"-9:30\")";
    runTest(Activation.of("ts1", tsSunday));

    source = "ts1.getHours(\"America/Los_Angeles\")";
    runTest(Activation.of("ts1", ts1));
    source = "ts1.getHours()";
    runTest(Activation.of("ts1", ts1));
    runTest(Activation.of("ts1", ts2));
    source = "ts1.getHours(\"Indian/Cocos\")";
    runTest(Activation.of("ts1", ts1));
    source = "ts1.getHours(\"6:30\")";
    runTest(Activation.of("ts1", ts1));

    source = "ts1.getMinutes(\"America/Los_Angeles\")";
    runTest(Activation.of("ts1", ts1));
    source = "ts1.getMinutes()";
    runTest(Activation.of("ts1", ts1));
    runTest(Activation.of("ts1", ts2));
    source = "ts1.getMinutes(\"Indian/Cocos\")";
    runTest(Activation.of("ts1", ts1));
    source = "ts1.getMinutes(\"-8:00\")";
    runTest(Activation.of("ts1", ts1));

    source = "ts1.getSeconds(\"America/Los_Angeles\")";
    runTest(Activation.of("ts1", ts1));
    source = "ts1.getSeconds()";
    runTest(Activation.of("ts1", ts1));
    runTest(Activation.of("ts1", ts2));
    source = "ts1.getSeconds(\"Indian/Cocos\")";
    runTest(Activation.of("ts1", ts1));
    source = "ts1.getSeconds(\"-8:00\")";
    runTest(Activation.of("ts1", ts1));

    source = "ts1.getMilliseconds(\"America/Los_Angeles\")";
    runTest(Activation.of("ts1", ts1));
    source = "ts1.getMilliseconds()";
    runTest(Activation.of("ts1", ts1));
    source = "ts1.getMilliseconds(\"Indian/Cocos\")";
    runTest(Activation.of("ts1", ts1));
    source = "ts1.getMilliseconds(\"-8:00\")";
    runTest(Activation.of("ts1", ts1));

    declareVariable("val", CelTypes.INT64);
    source = "ts1.getFullYear() < val";
    runTest(Activation.of("ts1", ts1).extend(Activation.of("val", 2013L)));
    source = "ts1.getMonth() < val";
    runTest(Activation.of("ts1", ts1).extend(Activation.of("val", 12L)));
    source = "ts1.getDayOfYear() < val";
    runTest(Activation.of("ts1", ts1).extend(Activation.of("val", 13L)));
    source = "ts1.getDayOfMonth() < val";
    runTest(Activation.of("ts1", ts1).extend(Activation.of("val", 10L)));
    source = "ts1.getDate() < val";
    runTest(Activation.of("ts1", ts1).extend(Activation.of("val", 15L)));
    source = "ts1.getDayOfWeek() < val";
    runTest(Activation.of("ts1", ts1).extend(Activation.of("val", 15L)));
    source = "ts1.getHours() < val";
    runTest(Activation.of("ts1", ts1).extend(Activation.of("val", 15L)));
    source = "ts1.getMinutes() < val";
    runTest(Activation.of("ts1", ts1).extend(Activation.of("val", 15L)));
    source = "ts1.getSeconds() < val";
    runTest(Activation.of("ts1", ts1).extend(Activation.of("val", 15L)));
    source = "ts1.getMilliseconds() < val";
    runTest(Activation.of("ts1", ts1).extend(Activation.of("val", 15L)));
  }

  @Test
  public void unknownField() throws Exception {
    container = TestAllTypes.getDescriptor().getFile().getPackage();
    declareVariable("x", CelTypes.createMessage(TestAllTypes.getDescriptor().getFullName()));
    TestAllTypes val =
        TestAllTypes.newBuilder()
            .setSingleTimestamp(Timestamps.fromSeconds(15))
            .setSingleDuration(Durations.fromSeconds(15))
            .setSingleNestedMessage(NestedMessage.getDefaultInstance())
            .addRepeatedNestedMessage(0, NestedMessage.newBuilder().setBb(14).build())
            .build();

    PartialMessage wm =
        new PartialMessage(
            val,
            FieldMask.newBuilder()
                .addPaths("map_int32_int64")
                .addPaths("single_int32")
                .addPaths("single_nested_message.bb")
                .addPaths("repeated_nested_message")
                .addPaths("single_duration.seconds")
                .build());

    // Unknown field is accessed.
    source = "x.single_int32";
    runTest(Activation.of("x", wm));

    source = "x.map_int32_int64[22]";
    runTest(Activation.of("x", wm));

    source = "x.repeated_nested_message[1]";
    runTest(Activation.of("x", wm));

    // Function call for a known field.
    source = "x.single_timestamp.getSeconds()";
    runTest(Activation.of("x", wm));

    // PartialMessage does not support function call
    source = "x.single_duration.getMilliseconds()";
    runTest(Activation.of("x", wm));

    // PartialMessage does not support operators.
    source = "x.single_duration + x.single_duration";
    runTest(Activation.of("x", wm));

    // Unknown field in a nested message
    source = "x.single_nested_message.bb";
    runTest(Activation.of("x", wm));

    // PartialMessage cannot be a final expr result.
    source = "x.single_nested_message";
    runTest(Activation.of("x", wm));

    // PartialMessage cannot be a field of another message.
    source = "TestAllTypes{single_nested_message: x.single_nested_message}";
    runTest(Activation.of("x", wm));

    // Unknown field cannot be a value of a map now.
    source = "{1: x.single_int32}";
    runTest(Activation.of("x", wm));

    // Access a known field as a val of a map.
    source = "{1: x.single_int64}";
    runTest(Activation.of("x", wm));

    // PartialMessage cannot be a value of a map.
    source = "{1: x.single_nested_message}";
    runTest(Activation.of("x", wm));

    // Unknown field cannot be a value of a list now.
    source = "[1, x.single_int32]";
    runTest(Activation.of("x", wm));

    // PartialMessage cannot be an elem in a list.
    source = "[x.single_nested_message]";
    runTest(Activation.of("x", wm));

    // Access a field in a nested message masked as unknown.
    wm = new PartialMessage(val, FieldMask.newBuilder().addPaths("single_nested_message").build());

    // Access unknown field.
    source = "x.single_nested_message.bb";
    runTest(Activation.of("x", wm));

    // Error or true should be true.
    source = "(x.single_nested_message.bb == 42) || true";
    runTest(Activation.of("x", wm));

    // Error or false should be error.
    source = "(x.single_nested_message.bb == 42) || false";
    runTest(Activation.of("x", wm));

    // Error and true should be error.
    source = "(x.single_nested_message.bb == 42) && true";
    runTest(Activation.of("x", wm));

    // Error and false should be false.
    source = "(x.single_nested_message.bb == 42) && false";
    runTest(Activation.of("x", wm));
  }

  @Test
  public void unknownResultSet() throws Exception {
    container = TestAllTypes.getDescriptor().getFile().getPackage();
    declareVariable("x", CelTypes.createMessage(TestAllTypes.getDescriptor().getFullName()));
    TestAllTypes val =
        TestAllTypes.newBuilder()
            .setSingleString("test")
            .setSingleTimestamp(Timestamp.newBuilder().setSeconds(15))
            .build();

    PartialMessage message =
        new PartialMessage(
            val,
            FieldMask.newBuilder()
                .addPaths("single_int32")
                .addPaths("single_int64")
                .addPaths("map_int32_int64")
                .build());

    // unknown && true ==> unknown
    source = "x.single_int32 == 1 && x.single_string == \"test\"";
    runTest(Activation.of("x", message));

    // unknown && false ==> false
    source = "x.single_int32 == 1 && x.single_string != \"test\"";
    runTest(Activation.of("x", message));

    // unknown && Unknown ==> UnknownSet
    source = "x.single_int32 == 1 && x.single_int64 == 1";
    runTest(Activation.of("x", message));

    // unknown && error ==> unknown
    source = "x.single_int32 == 1 && x.single_timestamp <= timestamp(\"bad timestamp string\")";
    runTest(Activation.of("x", message));

    // true && unknown ==> unknown
    source = "x.single_string == \"test\" && x.single_int32 == 1";
    runTest(Activation.of("x", message));

    // false && unknown ==> false
    source = "x.single_string != \"test\" && x.single_int32 == 1";
    runTest(Activation.of("x", message));

    // error && unknown ==> unknown
    source = "x.single_timestamp <= timestamp(\"bad timestamp string\") && x.single_int32 == 1";
    runTest(Activation.of("x", message));

    // error && error ==> error
    source =
        "x.single_timestamp <= timestamp(\"bad timestamp string\") "
            + "&& x.single_timestamp > timestamp(\"another bad timestamp string\")";
    runTest(Activation.of("x", message));

    // unknown || true ==> true
    source = "x.single_int32 == 1 || x.single_string == \"test\"";
    runTest(Activation.of("x", message));

    // unknown || false ==> unknown
    source = "x.single_int32 == 1 || x.single_string != \"test\"";
    runTest(Activation.of("x", message));

    // unknown || unknown ==> UnknownSet
    source = "x.single_int32 == 1 || x.single_int64 == 1";
    runTest(Activation.of("x", message));

    // unknown || error ==> unknown
    source = "x.single_int32 == 1 || x.single_timestamp <= timestamp(\"bad timestamp string\")";
    runTest(Activation.of("x", message));

    // true || unknown ==> true
    source = "x.single_string == \"test\" || x.single_int32 == 1";
    runTest(Activation.of("x", message));

    // false || unknown ==> unknown
    source = "x.single_string != \"test\" || x.single_int32 == 1";
    runTest(Activation.of("x", message));

    // error || unknown ==> unknown
    source = "x.single_timestamp <= timestamp(\"bad timestamp string\") || x.single_int32 == 1";
    runTest(Activation.of("x", message));

    // error || error ==> error
    source =
        "x.single_timestamp <= timestamp(\"bad timestamp string\") "
            + "|| x.single_timestamp > timestamp(\"another bad timestamp string\")";
    runTest(Activation.of("x", message));

    // dispatch test
    declareFunction(
        "f", memberOverload("f", Arrays.asList(CelTypes.INT64, CelTypes.INT64), CelTypes.BOOL));
    eval.registrar().add("f", Integer.class, Integer.class, Objects::equals);

    // dispatch: unknown.f(1)  ==> unknown
    source = "x.single_int32.f(1)";
    runTest(Activation.of("x", message));

    // dispatch: 1.f(unknown)  ==> unknown
    source = "1.f(x.single_int32)";
    runTest(Activation.of("x", message));

    // dispatch: unknown.f(unknown)  ==> unknownSet
    source = "x.single_int64.f(x.single_int32)";
    runTest(Activation.of("x", message));

    // ident is null(x is unbound) ==> unknown
    source = "x";
    runTest(Activation.of("y", message));

    // ident is unknown ==> unknown
    source = "x";
    ExprValue unknownMessage =
        ExprValue.newBuilder().setUnknown(UnknownSet.getDefaultInstance()).build();
    runTest(Activation.of("x", unknownMessage));

    // comprehension test
    // iteRange is unknown => unknown
    source = "x.map_int32_int64.map(x, x > 0, x + 1)";
    runTest(Activation.of("x", message));

    // exists, loop condition encounters unknown => skip unknown and check other element
    source = "[0, 2, 4].exists(z, z == 2 || z == x.single_int32)";
    runTest(Activation.of("x", message));

    // exists, loop condition encounters unknown => skip unknown and check other element, no dupe id
    // in result
    source = "[0, 2, 4].exists(z, z == x.single_int32)";
    runTest(Activation.of("x", message));

    // exists_one, loop condition encounters unknown => collect all unknowns
    source =
        "[0, 2, 4].exists_one(z, z == 0 || (z == 2 && z == x.single_int32) "
            + "|| (z == 4 && z == x.single_int64))";
    runTest(Activation.of("x", message));

    // all, loop condition encounters unknown => skip unknown and check other element
    source = "[0, 2].all(z, z == 2 || z == x.single_int32)";
    runTest(Activation.of("x", message));

    // filter, loop condition encounters unknown => skip unknown and check other element
    source =
        "[0, 2, 4].filter(z, z == 0 || (z == 2 && z == x.single_int32) "
            + "|| (z == 4 && z == x.single_int64))";
    runTest(Activation.of("x", message));

    // map, loop condition encounters unknown => skip unknown and check other element
    source =
        "[0, 2, 4].map(z, z == 0 || (z == 2 && z == x.single_int32) "
            + "|| (z == 4 && z == x.single_int64))";
    runTest(Activation.of("x", message));

    // conditional test
    // unknown ? 1 : 2 ==> unknown
    source = "x.single_int32 == 1 ? 1 : 2";
    runTest(Activation.of("x", message));

    // true ? unknown : 2  ==> unknown
    source = "x.single_string == \"test\" ? x.single_int32 : 2";
    runTest(Activation.of("x", message));

    // true ? 1 : unknown  ==> 1
    source = "x.single_string == \"test\" ? 1 : x.single_int32";
    runTest(Activation.of("x", message));

    // false ? unknown : 2 ==> 2
    source = "x.single_string != \"test\" ? x.single_int32 : 2";
    runTest(Activation.of("x", message));

    // false ? 1 : unknown ==> unknown
    source = "x.single_string != \"test\" ? 1 : x.single_int32";
    runTest(Activation.of("x", message));

    // unknown condition ? unknown : unknown ==> unknown condition
    source = "x.single_int64 == 1 ? x.single_int32 : x.single_int32";
    runTest(Activation.of("x", message));

    // map with unknown key => unknown
    source = "{x.single_int32: 2, 3: 4}";
    runTest(Activation.of("x", message));

    // map with unknown value => unknown
    source = "{1: x.single_int32, 3: 4}";
    runTest(Activation.of("x", message));

    // map with unknown key and value => unknownSet
    source = "{1: x.single_int32, x.single_int64: 4}";
    runTest(Activation.of("x", message));

    // list with unknown => unknown
    source = "[1, x.single_int32, 3, 4]";
    runTest(Activation.of("x", message));

    // list with multiple unknowns => unknownSet
    source = "[1, x.single_int32, x.single_int64, 4]";
    runTest(Activation.of("x", message));

    // message with unknown => unknown
    source = "TestAllTypes{single_int32: x.single_int32}.single_int32 == 2";
    runTest(Activation.of("x", message));

    // message with multiple unknowns => unknownSet
    source = "TestAllTypes{single_int32: x.single_int32, single_int64: x.single_int64}";
    runTest(Activation.of("x", message));
  }

  @Test
  public void timeConversions() throws Exception {
    container = Type.getDescriptor().getFile().getPackage();
    declareVariable("t1", CelTypes.TIMESTAMP);

    source = "timestamp(\"1972-01-01T10:00:20.021-05:00\")";
    runTest(Activation.EMPTY);

    source = "timestamp(123)";
    runTest(Activation.EMPTY);

    source = "duration(\"15.11s\")";
    runTest(Activation.EMPTY);

    source = "int(t1) == 100";
    runTest(Activation.of("t1", Timestamps.fromSeconds(100)));

    source = "duration(\"1h2m3.4s\")";
    runTest(Activation.EMPTY);

    // Not supported.
    source = "duration('inf')";
    runTest(Activation.EMPTY);
  }

  @Test
  public void sizeTests() throws Exception {
    container = Type.getDescriptor().getFile().getPackage();
    declareVariable("str", CelTypes.STRING);
    declareVariable("b", CelTypes.BYTES);

    source = "size(b) == 5 && b.size() == 5";
    runTest(Activation.of("b", ByteString.copyFromUtf8("happy")));

    source = "size(str) == 5 && str.size() == 5";
    runTest(Activation.of("str", "happy"));
    runTest(Activation.of("str", "happ\uDBFF\uDFFC"));

    source = "size({1:14, 2:15}) == 2 && {1:14, 2:15}.size() == 2";
    runTest(Activation.EMPTY);

    source = "size([1,2,3]) == 3 && [1,2,3].size() == 3";
    runTest(Activation.EMPTY);
  }

  @Test
  public void nonstrictQuantifierTests() throws Exception {
    // Plain tests.  Everything is constant.
    source = "[0, 2, 4].exists(x, 4/x == 2 && 4/(4-x) == 2)";
    runTest(Activation.EMPTY);

    source = "![0, 2, 4].all(x, 4/x != 2 && 4/(4-x) != 2)";
    runTest(Activation.EMPTY);

    declareVariable("four", CelTypes.INT64);

    // Condition is dynamic.
    source = "[0, 2, 4].exists(x, four/x == 2 && four/(four-x) == 2)";
    runTest(Activation.of("four", 4L));

    source = "![0, 2, 4].all(x, four/x != 2 && four/(four-x) != 2)";
    runTest(Activation.of("four", 4L));

    // Both range and condition are dynamic.
    source = "[0, 2, four].exists(x, four/x == 2 && four/(four-x) == 2)";
    runTest(Activation.of("four", 4L));

    source = "![0, 2, four].all(x, four/x != 2 && four/(four-x) != 2)";
    runTest(Activation.of("four", 4L));
  }

  @Test
  public void regexpMatchingTests() throws Exception {
    // Constant everything.
    source = "matches(\"alpha\", \"^al.*\") == true";
    runTest(Activation.EMPTY);

    source = "matches(\"alpha\", \"^.al.*\") == false";
    runTest(Activation.EMPTY);

    source = "matches(\"alpha\", \".*ha$\") == true";
    runTest(Activation.EMPTY);

    source = "matches(\"alpha\", \"^.*ha.$\") == false";
    runTest(Activation.EMPTY);

    source = "matches(\"alpha\", \"\") == true";
    runTest(Activation.EMPTY);

    source = "matches(\"alpha\", \"ph\") == true";
    runTest(Activation.EMPTY);

    source = "matches(\"alpha\", \"^ph\") == false";
    runTest(Activation.EMPTY);

    source = "matches(\"alpha\", \"ph$\") == false";
    runTest(Activation.EMPTY);

    // Constant everything, receiver-style.
    source = "\"alpha\".matches(\"^al.*\") == true";
    runTest(Activation.EMPTY);

    source = "\"alpha\".matches(\"^.al.*\") == false";
    runTest(Activation.EMPTY);

    source = "\"alpha\".matches(\".*ha$\") == true";
    runTest(Activation.EMPTY);

    source = "\"alpha\".matches(\".*ha.$\") == false";
    runTest(Activation.EMPTY);

    source = "\"alpha\".matches(\"\") == true";
    runTest(Activation.EMPTY);

    source = "\"alpha\".matches(\"ph\") == true";
    runTest(Activation.EMPTY);

    source = "\"alpha\".matches(\"^ph\") == false";
    runTest(Activation.EMPTY);

    source = "\"alpha\".matches(\"ph$\") == false";
    runTest(Activation.EMPTY);

    // Constant string.
    declareVariable("regexp", CelTypes.STRING);

    source = "matches(\"alpha\", regexp) == true";
    runTest(Activation.of("regexp", "^al.*"));

    source = "matches(\"alpha\", regexp) == false";
    runTest(Activation.of("regexp", "^.al.*"));

    source = "matches(\"alpha\", regexp) == true";
    runTest(Activation.of("regexp", ".*ha$"));

    source = "matches(\"alpha\", regexp) == false";
    runTest(Activation.of("regexp", ".*ha.$"));

    // Constant string, receiver-style.
    source = "\"alpha\".matches(regexp) == true";
    runTest(Activation.of("regexp", "^al.*"));

    source = "\"alpha\".matches(regexp) == false";
    runTest(Activation.of("regexp", "^.al.*"));

    source = "\"alpha\".matches(regexp) == true";
    runTest(Activation.of("regexp", ".*ha$"));

    source = "\"alpha\".matches(regexp) == false";
    runTest(Activation.of("regexp", ".*ha.$"));

    // Constant regexp.
    declareVariable("s", CelTypes.STRING);

    source = "matches(s, \"^al.*\") == true";
    runTest(Activation.of("s", "alpha"));

    source = "matches(s, \"^.al.*\") == false";
    runTest(Activation.of("s", "alpha"));

    source = "matches(s, \".*ha$\") == true";
    runTest(Activation.of("s", "alpha"));

    source = "matches(s, \"^.*ha.$\") == false";
    runTest(Activation.of("s", "alpha"));

    // Constant regexp, receiver-style.
    source = "s.matches(\"^al.*\") == true";
    runTest(Activation.of("s", "alpha"));

    source = "s.matches(\"^.al.*\") == false";
    runTest(Activation.of("s", "alpha"));

    source = "s.matches(\".*ha$\") == true";
    runTest(Activation.of("s", "alpha"));

    source = "s.matches(\"^.*ha.$\") == false";
    runTest(Activation.of("s", "alpha"));

    // No constants.
    source = "matches(s, regexp) == true";
    runTest(Activation.copyOf(ImmutableMap.of("s", "alpha", "regexp", "^al.*")));
    runTest(Activation.copyOf(ImmutableMap.of("s", "alpha", "regexp", ".*ha$")));

    source = "matches(s, regexp) == false";
    runTest(Activation.copyOf(ImmutableMap.of("s", "alpha", "regexp", "^.al.*")));
    runTest(Activation.copyOf(ImmutableMap.of("s", "alpha", "regexp", ".*ha.$")));

    // No constants, receiver-style.
    source = "s.matches(regexp) == true";
    runTest(Activation.copyOf(ImmutableMap.of("s", "alpha", "regexp", "^al.*")));
    runTest(Activation.copyOf(ImmutableMap.of("s", "alpha", "regexp", ".*ha$")));

    source = "s.matches(regexp) == false";
    runTest(Activation.copyOf(ImmutableMap.of("s", "alpha", "regexp", "^.al.*")));
    runTest(Activation.copyOf(ImmutableMap.of("s", "alpha", "regexp", ".*ha.$")));
  }

  @Test
  public void regexpMatches_error() throws Exception {
    source = "matches(\"alpha\", \"**\")";
    runTest(Activation.EMPTY);

    source = "\"alpha\".matches(\"**\")";
    runTest(Activation.EMPTY);
  }

  @Test
  public void int64Conversions() throws Exception {
    source = "int('-1')"; // string converts to -1
    runTest(Activation.EMPTY);

    source = "int(2.1)"; // double converts to 2
    runTest(Activation.EMPTY);

    source = "int(18446744073709551615u)"; // 2^64-1 should error
    runTest(Activation.EMPTY);

    source = "int(1e99)"; // out of range should error
    runTest(Activation.EMPTY);

    source = "int(42u)"; // converts to 42
    runTest(Activation.EMPTY);
  }

  @Test
  public void uint64Conversions() throws Exception {
    // The test case `uint(1e19)` succeeds with unsigned longs and fails with longs in a way that
    // cannot be easily tested.
    if (!eval.celOptions().enableUnsignedLongs()) {
      skipBaselineVerification();
      return;
    }
    source = "uint('1')"; // string converts to 1u
    runTest(Activation.EMPTY);

    source = "uint(2.1)"; // double converts to 2u
    runTest(Activation.EMPTY);

    source = "uint(-1)"; // should error
    runTest(Activation.EMPTY);

    source = "uint(1e19)"; // valid uint but outside of int range
    runTest(Activation.EMPTY);

    source = "uint(6.022e23)"; // outside uint range
    runTest(Activation.EMPTY);

    source = "uint(42)"; // int converts to 42u
    runTest(Activation.EMPTY);

    source = "uint('f1')"; // should error
    runTest(Activation.EMPTY);
  }

  @Test
  public void doubleConversions() throws Exception {
    source = "double('1.1')"; // string converts to 1.1
    runTest(Activation.EMPTY);

    source = "double(2u)"; // uint converts to 2.0
    runTest(Activation.EMPTY);

    source = "double(-1)"; // int converts to -1.0
    runTest(Activation.EMPTY);

    source = "double('bad')";
    runTest(Activation.EMPTY);
  }

  @Test
  public void stringConversions() throws Exception {
    source = "string(1.1)"; // double converts to '1.1'
    runTest(Activation.EMPTY);

    source = "string(2u)"; // uint converts to '2'
    runTest(Activation.EMPTY);

    source = "string(-1)"; // int converts to '-1'
    runTest(Activation.EMPTY);

    // Byte literals in Google SQL only take the leading byte of an escape character.
    // This means that to translate a byte literal to a UTF-8 encoded string, all bytes must be
    // encoded in the literal as they would be laid out in memory for UTF-8, hence the extra octal
    // escape to achieve parity with the bidi test below.
    source = "string(b'abc\\303\\203')";
    runTest(Activation.EMPTY); // bytes convert to 'abcÃ'

    // Bi-di conversion for strings and bytes for 'abcÃ', note the difference between the string
    // and byte literal values.
    source = "string(bytes('abc\\303'))";
    runTest(Activation.EMPTY);

    source = "string(timestamp('2009-02-13T23:31:30Z'))";
    runTest(Activation.EMPTY);

    source = "string(duration('1000000s'))";
    runTest(Activation.EMPTY);
  }

  @Test
  public void bytes() throws Exception {
    source =
        "b'a' < b'b' && b'a' <= b'b' && b'b' > b'a' && b'a' >= b'a' && b'a' == b'a' && b'a' !="
            + " b'b'";
    runTest(Activation.EMPTY);
  }

  @Test
  public void bytesConversions() throws Exception {
    source = "bytes('abc\\303')";
    runTest(Activation.EMPTY); // string converts to abcÃ in bytes form.
  }

  @Test
  public void dynConversions() throws Exception {
    source = "dyn(42)";
    runTest(Activation.EMPTY);

    source = "dyn({'a':1, 'b':2})";
    runTest(Activation.EMPTY);
  }

  // This lambda implements @Immutable interface 'Function', but 'InterpreterTest' has field 'eval'
  // of type 'com.google.api.expr.cel.testing.Eval', the declaration of
  // type
  // 'com.google.api.expr.cel.testing.Eval' is not annotated with
  // @com.google.errorprone.annotations.Immutable
  @SuppressWarnings("Immutable")
  @Test
  public void jsonValueTypes() throws Exception {
    container = TestAllTypes.getDescriptor().getFile().getPackage();
    declareVariable("x", CelTypes.createMessage(TestAllTypes.getDescriptor().getFullName()));

    // JSON bool selection.
    TestAllTypes xBool =
        TestAllTypes.newBuilder().setSingleValue(Value.newBuilder().setBoolValue(true)).build();
    source = "x.single_value";
    runTest(Activation.of("x", xBool));

    // JSON number selection with int comparison.
    TestAllTypes xInt =
        TestAllTypes.newBuilder().setSingleValue(Value.newBuilder().setNumberValue(1)).build();
    source = "x.single_value == double(1)";
    runTest(Activation.of("x", xInt));

    // JSON number selection with float comparison.
    TestAllTypes xFloat =
        TestAllTypes.newBuilder().setSingleValue(Value.newBuilder().setNumberValue(1.1)).build();
    source = "x.single_value == 1.1";
    runTest(Activation.of("x", xFloat));

    // JSON null selection.
    TestAllTypes xNull =
        TestAllTypes.newBuilder()
            .setSingleValue(Value.newBuilder().setNullValue(NullValue.NULL_VALUE))
            .build();
    source = "x.single_value == null";
    runTest(Activation.of("x", xNull));

    // JSON string selection.
    TestAllTypes xString =
        TestAllTypes.newBuilder()
            .setSingleValue(Value.newBuilder().setStringValue("hello"))
            .build();
    source = "x.single_value == 'hello'";
    runTest(Activation.of("x", xString));

    // JSON list equality.
    TestAllTypes xList =
        TestAllTypes.newBuilder()
            .setSingleValue(
                Value.newBuilder()
                    .setListValue(
                        ListValue.newBuilder()
                            .addValues(
                                Value.newBuilder()
                                    .setListValue(
                                        ListValue.newBuilder()
                                            .addValues(Value.newBuilder().setStringValue("hello"))))
                            .addValues(Value.newBuilder().setNumberValue(-1.1))))
            .build();
    source = "x.single_value[0] == [['hello'], -1.1][0]";
    runTest(Activation.of("x", xList));

    // JSON struct equality.
    TestAllTypes xStruct =
        TestAllTypes.newBuilder()
            .setSingleStruct(
                Struct.newBuilder()
                    .putFields(
                        "str",
                        Value.newBuilder()
                            .setListValue(
                                ListValue.newBuilder()
                                    .addValues(Value.newBuilder().setStringValue("hello")))
                            .build())
                    .putFields("num", Value.newBuilder().setNumberValue(-1.1).build()))
            .build();
    source = "x.single_struct.num == {'str': ['hello'], 'num': -1.1}['num']";
    runTest(Activation.of("x", xStruct));

    // Build a proto message using a dynamically constructed map and assign the map to a struct
    // value.
    source =
        "TestAllTypes{"
            + "single_struct: "
            + "TestAllTypes{single_value: {'str': ['hello']}}.single_value"
            + "}";
    runTest(Activation.EMPTY);

    // Ensure that types are being wrapped and unwrapped on function dispatch.
    declareFunction(
        "pair",
        globalOverload("pair", ImmutableList.of(CelTypes.STRING, CelTypes.STRING), CelTypes.DYN));
    eval.registrar()
        .add(
            "pair",
            ImmutableList.of(String.class, String.class),
            (Object[] args) -> {
              String key = (String) args[0];
              String val = (String) args[1];
              return eval.adapt(
                  Value.newBuilder()
                      .setStructValue(
                          Struct.newBuilder()
                              .putFields(key, Value.newBuilder().setStringValue(val).build()))
                      .build());
            });
    source = "pair(x.single_struct.str[0], 'val')";
    runTest(Activation.of("x", xStruct));
  }

  @Test
  public void typeComparisons() throws Exception {
    container = TestAllTypes.getDescriptor().getFile().getPackage();

    // Test numeric types.
    source =
        "type(1) == int && type(1u) == uint && "
            + "type(1u) != int && type(1) != uint && "
            + "type(uint(1.1)) == uint && "
            + "type(1.1) == double";
    runTest(Activation.EMPTY);

    // Test string and bytes types.
    source = "type('hello') == string && type(b'\277') == bytes";
    runTest(Activation.EMPTY);

    // Test list and map types.
    source = "type([1, 2, 3]) == list && type({'a': 1, 'b': 2}) == map";
    runTest(Activation.EMPTY);

    // Test bool types.
    source = "type(true) == bool && type(false) == bool";
    runTest(Activation.EMPTY);

    // Test well-known proto-based types.
    source = "type(duration('10s')) == google.protobuf.Duration";
    runTest(Activation.EMPTY);

    // Test external proto-based types with container resolution.
    source =
        "type(TestAllTypes{}) == TestAllTypes && "
            + "type(TestAllTypes{}) == proto3.TestAllTypes && "
            + "type(TestAllTypes{}) == .dev.cel.testing.testdata.proto3.TestAllTypes && "
            + "type(proto3.TestAllTypes{}) == TestAllTypes && "
            + "type(proto3.TestAllTypes{}) == proto3.TestAllTypes && "
            + "type(proto3.TestAllTypes{}) == .dev.cel.testing.testdata.proto3.TestAllTypes && "
            + "type(.dev.cel.testing.testdata.proto3.TestAllTypes{}) == TestAllTypes && "
            + "type(.dev.cel.testing.testdata.proto3.TestAllTypes{}) == proto3.TestAllTypes && "
            + "type(.dev.cel.testing.testdata.proto3.TestAllTypes{}) == "
            + ".dev.cel.testing.testdata.proto3.TestAllTypes";
    runTest(Activation.EMPTY);

    // Test whether a type name is recognized as a type.
    source = "type(TestAllTypes) == type";
    runTest(Activation.EMPTY);

    // Test whether the type resolution of a proto object is recognized as the message's type.
    source = "type(TestAllTypes{}) == TestAllTypes";
    runTest(Activation.EMPTY);

    // Test whether null resolves to null_type.
    source = "type(null) == null_type";
    runTest(Activation.EMPTY);
  }

  @Test
  public void wrappers() throws Exception {
    TestAllTypes.Builder wrapperBindings =
        TestAllTypes.newBuilder()
            .setSingleBoolWrapper(BoolValue.of(true))
            .setSingleBytesWrapper(BytesValue.of(ByteString.copyFrom(new byte[] {'h', 'i'})))
            .setSingleDoubleWrapper(DoubleValue.of(-3.0))
            .setSingleFloatWrapper(FloatValue.of(1.5f))
            .setSingleInt32Wrapper(Int32Value.of(-12))
            .setSingleInt64Wrapper(Int64Value.of(-34))
            .setSingleStringWrapper(StringValue.of("hello"))
            .setSingleUint32Wrapper(UInt32Value.of(12))
            .setSingleUint64Wrapper(UInt64Value.of(34));

    declareVariable("x", CelTypes.createMessage(TestAllTypes.getDescriptor().getFullName()));
    source =
        "x.single_bool_wrapper == true && "
            + "x.single_bytes_wrapper == b'hi' && "
            + "x.single_double_wrapper == -3.0 && "
            + "x.single_float_wrapper == 1.5 && "
            + "x.single_int32_wrapper == -12 && "
            + "x.single_int64_wrapper == -34 && "
            + "x.single_string_wrapper == 'hello' && "
            + "x.single_uint32_wrapper == 12u && "
            + "x.single_uint64_wrapper == 34u";
    runTest(Activation.of("x", wrapperBindings));

    source =
        "x.single_bool_wrapper == google.protobuf.BoolValue{} && "
            + "x.single_bytes_wrapper == google.protobuf.BytesValue{value: b'hi'} && "
            + "x.single_double_wrapper == google.protobuf.DoubleValue{value: -3.0} && "
            + "x.single_float_wrapper == google.protobuf.FloatValue{value: 1.5} && "
            + "x.single_int32_wrapper == google.protobuf.Int32Value{value: -12} && "
            + "x.single_int64_wrapper == google.protobuf.Int64Value{value: -34} && "
            + "x.single_string_wrapper == google.protobuf.StringValue{} && "
            + "x.single_uint32_wrapper == google.protobuf.UInt32Value{value: 12u} && "
            + "x.single_uint64_wrapper == google.protobuf.UInt64Value{value: 34u}";
    runTest(
        Activation.of(
            "x",
            wrapperBindings
                .setSingleBoolWrapper(BoolValue.getDefaultInstance())
                .setSingleStringWrapper(StringValue.getDefaultInstance())));

    source =
        "x.single_bool_wrapper == null && "
            + "x.single_bytes_wrapper == null && "
            + "x.single_double_wrapper == null && "
            + "x.single_float_wrapper == null && "
            + "x.single_int32_wrapper == null && "
            + "x.single_int64_wrapper == null && "
            + "x.single_string_wrapper == null && "
            + "x.single_uint32_wrapper == null && "
            + "x.single_uint64_wrapper == null";
    runTest(Activation.of("x", TestAllTypes.getDefaultInstance()));
  }

  @Test
  public void longComprehension() throws Exception {
    ImmutableList<Long> l = LongStream.range(0L, 1000L).boxed().collect(toImmutableList());
    eval.registrar().add("constantLongList", ImmutableList.of(), args -> l);

    // Comprehension over compile-time constant long list.
    declareFunction(
        "constantLongList",
        globalOverload(
            "constantLongList", ImmutableList.of(), CelTypes.createList(CelTypes.INT64)));
    source = "size(constantLongList().map(x, x+1)) == 1000";
    runTest(Activation.EMPTY);

    // Comprehension over long list that is not compile-time constant.
    declareVariable("longlist", CelTypes.createList(CelTypes.INT64));
    source = "size(longlist.map(x, x+1)) == 1000";
    runTest(Activation.of("longlist", l));

    // Comprehension over long list where the computation is very slow.
    // (This is here pro-forma only since in the synchronous interpreter there
    // is no notion of a computation being slow so that another computation can
    // build up a stack while waiting.)
    eval.registrar().add("f_slow_inc", Long.class, n -> n + 1L);
    eval.registrar().add("f_unleash", Object.class, x -> x);
    declareFunction(
        "f_slow_inc",
        globalOverload("f_slow_inc", ImmutableList.of(CelTypes.INT64), CelTypes.INT64));
    declareFunction(
        "f_unleash",
        globalOverload(
            "f_unleash",
            ImmutableList.of(CelTypes.createTypeParam("A")),
            ImmutableList.of("A"),
            CelTypes.createTypeParam("A")));
    source = "f_unleash(longlist.map(x, f_slow_inc(x)))[0] == 1";
    runTest(Activation.of("longlist", l));
  }

  @Test
  public void maxComprehension() throws Exception {
    if (eval.celOptions().comprehensionMaxIterations() < 0) {
      skipBaselineVerification();
      return;
    }
    // Comprehension over long list that is not compile-time constant.
    declareVariable("longlist", CelTypes.createList(CelTypes.INT64));
    source = "size(longlist.map(x, x+1)) == 1000";

    // Comprehension which exceeds the configured iteration limit.
    ImmutableList<Long> tooLongList =
        LongStream.range(0L, COMPREHENSION_MAX_ITERATIONS + 1).boxed().collect(toImmutableList());
    runTest(Activation.of("longlist", tooLongList));

    // Sequential iterations within the collective limit of 1000.
    source = "longlist.filter(i, i % 2 == 0).map(i, i * 2).map(i, i / 2).size() == 250";
    ImmutableList<Long> l =
        LongStream.range(0L, COMPREHENSION_MAX_ITERATIONS / 2).boxed().collect(toImmutableList());
    runTest(Activation.of("longlist", l));

    // Sequential iterations outside the limit of 1000.
    source = "(longlist + [0]).filter(i, i % 2 == 0).map(i, i * 2).map(i, i / 2).size() == 251";
    runTest(Activation.of("longlist", l));

    // Nested iteration within the iteration limit.
    // Note, there is some double-counting of the inner-loops which causes the iteration limit to
    // get tripped sooner than one might expect for the nested case.
    source = "longlist.map(i, longlist.map(j, longlist.map(k, [i, j, k]))).size() == 9";
    l = LongStream.range(0L, 9).boxed().collect(toImmutableList());
    runTest(Activation.of("longlist", l));

    // Nested iteration which exceeds the iteration limit. This result may be surprising, but the
    // limit is tripped precisely because each complete iteration of an inner-loop counts as inner-
    // loop + 1 as there's not a clean way to deduct an iteration and only count the inner most
    // loop.
    l = LongStream.range(0L, 10).boxed().collect(toImmutableList());
    runTest(Activation.of("longlist", l));
  }

  @Test
  public void dynamicMessage() throws Exception {
    TestAllTypes wrapperBindings =
        TestAllTypes.newBuilder()
            .setSingleAny(Any.pack(NestedMessage.newBuilder().setBb(42).build()))
            .setSingleBoolWrapper(BoolValue.of(true))
            .setSingleBytesWrapper(BytesValue.of(ByteString.copyFrom(new byte[] {'h', 'i'})))
            .setSingleDoubleWrapper(DoubleValue.of(-3.0))
            .setSingleFloatWrapper(FloatValue.of(1.5f))
            .setSingleInt32Wrapper(Int32Value.of(-12))
            .setSingleInt64Wrapper(Int64Value.of(-34))
            .setSingleStringWrapper(StringValue.of("hello"))
            .setSingleUint32Wrapper(UInt32Value.of(12))
            .setSingleUint64Wrapper(UInt64Value.of(34))
            .setSingleDuration(Duration.newBuilder().setSeconds(10).setNanos(20))
            .setSingleTimestamp(Timestamp.newBuilder().setSeconds(100).setNanos(200))
            .setSingleValue(Value.newBuilder().setStringValue("a"))
            .setSingleStruct(
                Struct.newBuilder().putFields("b", Value.newBuilder().setStringValue("c").build()))
            .setSingleListValue(
                ListValue.newBuilder().addValues(Value.newBuilder().setStringValue("d")).build())
            .build();

    Activation activation =
        Activation.of(
            "msg",
            DynamicMessage.parseFrom(
                TestAllTypes.getDescriptor(),
                wrapperBindings.toByteArray(),
                DefaultDescriptorPool.INSTANCE.getExtensionRegistry()));

    declareVariable("msg", CelTypes.createMessage(TestAllTypes.getDescriptor().getFullName()));

    source = "msg.single_any";
    assertThat(runTest(activation)).isInstanceOf(NestedMessage.class);

    source = "msg.single_bool_wrapper";
    assertThat(runTest(activation)).isInstanceOf(Boolean.class);

    source = "msg.single_bytes_wrapper";
    assertThat(runTest(activation)).isInstanceOf(String.class);

    source = "msg.single_double_wrapper";
    assertThat(runTest(activation)).isInstanceOf(Double.class);

    source = "msg.single_float_wrapper";
    assertThat(runTest(activation)).isInstanceOf(Double.class);

    source = "msg.single_int32_wrapper";
    assertThat(runTest(activation)).isInstanceOf(Long.class);

    source = "msg.single_int64_wrapper";
    assertThat(runTest(activation)).isInstanceOf(Long.class);

    source = "msg.single_string_wrapper";
    assertThat(runTest(activation)).isInstanceOf(String.class);

    source = "msg.single_uint32_wrapper";
    assertThat(runTest(activation))
        .isInstanceOf(eval.celOptions().enableUnsignedLongs() ? UnsignedLong.class : Long.class);

    source = "msg.single_uint64_wrapper";
    assertThat(runTest(activation))
        .isInstanceOf(eval.celOptions().enableUnsignedLongs() ? UnsignedLong.class : Long.class);

    source = "msg.single_duration";
    assertThat(runTest(activation)).isInstanceOf(Duration.class);

    source = "msg.single_timestamp";
    assertThat(runTest(activation)).isInstanceOf(Timestamp.class);

    source = "msg.single_value";
    assertThat(runTest(activation)).isInstanceOf(String.class);

    source = "msg.single_struct";
    assertThat(runTest(activation)).isInstanceOf(Map.class);

    source = "msg.single_list_value";
    assertThat(runTest(activation)).isInstanceOf(List.class);
  }
}
