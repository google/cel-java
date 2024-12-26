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

package dev.cel.runtime;

import static com.google.common.truth.Truth.assertThat;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.primitives.UnsignedLong;
import com.google.protobuf.Any;
import com.google.protobuf.BoolValue;
import com.google.protobuf.ByteString;
import com.google.protobuf.BytesValue;
import com.google.protobuf.DoubleValue;
import com.google.protobuf.FloatValue;
import com.google.protobuf.Int32Value;
import com.google.protobuf.Int64Value;
import com.google.protobuf.ListValue;
import com.google.protobuf.NullValue;
import com.google.protobuf.StringValue;
import com.google.protobuf.Struct;
import com.google.protobuf.UInt32Value;
import com.google.protobuf.UInt64Value;
import com.google.protobuf.Value;
import com.google.protobuf.util.Durations;
import com.google.protobuf.util.Timestamps;
import com.google.rpc.context.AttributeContext;
import com.google.rpc.context.AttributeContext.Auth;
import com.google.rpc.context.AttributeContext.Peer;
import com.google.rpc.context.AttributeContext.Request;
import dev.cel.common.CelDescriptorUtil;
import dev.cel.common.CelOptions;
import dev.cel.common.CelRuntimeException;
import dev.cel.common.internal.AdaptingTypes;
import dev.cel.common.internal.BidiConverter;
import dev.cel.common.internal.DefaultDescriptorPool;
import dev.cel.common.internal.DefaultMessageFactory;
import dev.cel.common.internal.DynamicProto;
import java.util.Arrays;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public final class RuntimeEqualityTest {
  private static final CelOptions EMPTY_OPTIONS =
      CelOptions.newBuilder().disableCelStandardEquality(false).build();
  private static final CelOptions PROTO_EQUALITY =
      CelOptions.newBuilder()
          .disableCelStandardEquality(false)
          .enableProtoDifferencerEquality(true)
          .build();
  private static final CelOptions UNSIGNED_LONGS =
      CelOptions.newBuilder().disableCelStandardEquality(false).build();
  private static final CelOptions PROTO_EQUALITY_UNSIGNED_LONGS =
      CelOptions.newBuilder()
          .disableCelStandardEquality(false)
          .enableProtoDifferencerEquality(true)
          .build();

  private static final RuntimeEquality RUNTIME_EQUALITY =
      new RuntimeEquality(
          DynamicProto.create(
              DefaultMessageFactory.create(
                  DefaultDescriptorPool.create(
                      CelDescriptorUtil.getAllDescriptorsFromFileDescriptor(
                          AttributeContext.getDescriptor().getFile())))));

  @Test
  public void inMap() throws Exception {
    CelOptions celOptions = CelOptions.newBuilder().disableCelStandardEquality(false).build();
    ImmutableMap<String, String> map = ImmutableMap.of("key", "value", "key2", "value2");
    assertThat(RUNTIME_EQUALITY.inMap(map, "key2", celOptions)).isTrue();
    assertThat(RUNTIME_EQUALITY.inMap(map, "key3", celOptions)).isFalse();

    ImmutableMap<Object, String> mixedKeyMap =
        ImmutableMap.of(
            "key", "value", 2L, "value2", UnsignedLong.valueOf(42), "answer to everything");
    // Integer tests.
    assertThat(RUNTIME_EQUALITY.inMap(mixedKeyMap, 2, celOptions)).isTrue();
    assertThat(RUNTIME_EQUALITY.inMap(mixedKeyMap, 3, celOptions)).isFalse();

    // Long tests.
    assertThat(RUNTIME_EQUALITY.inMap(mixedKeyMap, -1L, celOptions)).isFalse();
    assertThat(RUNTIME_EQUALITY.inMap(mixedKeyMap, 3L, celOptions)).isFalse();
    assertThat(RUNTIME_EQUALITY.inMap(mixedKeyMap, 2L, celOptions)).isTrue();
    assertThat(RUNTIME_EQUALITY.inMap(mixedKeyMap, 42L, celOptions)).isTrue();

    // Floating point tests
    assertThat(RUNTIME_EQUALITY.inMap(mixedKeyMap, -1.0d, celOptions)).isFalse();
    assertThat(RUNTIME_EQUALITY.inMap(mixedKeyMap, 2.1d, celOptions)).isFalse();
    assertThat(
            RUNTIME_EQUALITY.inMap(mixedKeyMap, UnsignedLong.MAX_VALUE.doubleValue(), celOptions))
        .isFalse();
    assertThat(RUNTIME_EQUALITY.inMap(mixedKeyMap, 2.0d, celOptions)).isTrue();
    assertThat(RUNTIME_EQUALITY.inMap(mixedKeyMap, Double.NaN, celOptions)).isFalse();

    // Unsigned long tests.
    assertThat(RUNTIME_EQUALITY.inMap(mixedKeyMap, UnsignedLong.valueOf(1L), celOptions)).isFalse();
    assertThat(RUNTIME_EQUALITY.inMap(mixedKeyMap, UnsignedLong.valueOf(2L), celOptions)).isTrue();
    assertThat(RUNTIME_EQUALITY.inMap(mixedKeyMap, UnsignedLong.MAX_VALUE, celOptions)).isFalse();
    assertThat(RUNTIME_EQUALITY.inMap(mixedKeyMap, UInt64Value.of(2L), celOptions)).isTrue();

    // Validate the legacy behavior as well.
    assertThat(RUNTIME_EQUALITY.inMap(mixedKeyMap, 2, CelOptions.LEGACY)).isFalse();
    assertThat(RUNTIME_EQUALITY.inMap(mixedKeyMap, 2L, CelOptions.LEGACY)).isTrue();
    assertThat(RUNTIME_EQUALITY.inMap(mixedKeyMap, Int64Value.of(2L), CelOptions.LEGACY)).isFalse();
    assertThat(RUNTIME_EQUALITY.inMap(mixedKeyMap, UInt64Value.of(2L), CelOptions.LEGACY))
        .isFalse();
  }

  @Test
  public void inList() throws Exception {
    CelOptions celOptions = CelOptions.newBuilder().disableCelStandardEquality(false).build();
    ImmutableList<String> list = ImmutableList.of("value", "value2");
    assertThat(RUNTIME_EQUALITY.inList(list, "value", celOptions)).isTrue();
    assertThat(RUNTIME_EQUALITY.inList(list, "value3", celOptions)).isFalse();

    ImmutableList<Object> mixedValueList = ImmutableList.of(1, "value", 2, "value2");
    assertThat(RUNTIME_EQUALITY.inList(mixedValueList, 2, celOptions)).isTrue();
    assertThat(RUNTIME_EQUALITY.inList(mixedValueList, 3, celOptions)).isFalse();

    assertThat(RUNTIME_EQUALITY.inList(mixedValueList, 2L, celOptions)).isTrue();
    assertThat(RUNTIME_EQUALITY.inList(mixedValueList, 3L, celOptions)).isFalse();

    assertThat(RUNTIME_EQUALITY.inList(mixedValueList, 2.0, celOptions)).isTrue();
    assertThat(RUNTIME_EQUALITY.inList(mixedValueList, Double.NaN, celOptions)).isFalse();

    assertThat(RUNTIME_EQUALITY.inList(mixedValueList, UnsignedLong.valueOf(2L), celOptions))
        .isTrue();
    assertThat(RUNTIME_EQUALITY.inList(mixedValueList, UnsignedLong.valueOf(3L), celOptions))
        .isFalse();

    // Validate the legacy behavior as well.
    assertThat(RUNTIME_EQUALITY.inList(mixedValueList, 2, CelOptions.LEGACY)).isTrue();
    assertThat(RUNTIME_EQUALITY.inList(mixedValueList, 2L, CelOptions.LEGACY)).isFalse();
  }

  @Test
  public void indexMap() throws Exception {
    ImmutableMap<Object, String> mixedKeyMap =
        ImmutableMap.of(1L, "value", UnsignedLong.valueOf(2L), "value2");
    assertThat(RUNTIME_EQUALITY.indexMap(mixedKeyMap, 1.0, CelOptions.DEFAULT)).isEqualTo("value");
    assertThat(RUNTIME_EQUALITY.indexMap(mixedKeyMap, 2.0, CelOptions.DEFAULT)).isEqualTo("value2");
    Assert.assertThrows(
        CelRuntimeException.class,
        () -> RUNTIME_EQUALITY.indexMap(mixedKeyMap, 1.0, CelOptions.LEGACY));
    Assert.assertThrows(
        CelRuntimeException.class,
        () -> RUNTIME_EQUALITY.indexMap(mixedKeyMap, 1.1, CelOptions.DEFAULT));
  }

  @AutoValue
  abstract static class State {
    /**
     * Expected comparison outcome when equality is performed with the given options.
     *
     * <p>The {@code null} value indicates that the outcome is an error.
     */
    public abstract @Nullable Boolean outcome();

    /** Set of options to use when performing the equality check. */
    public abstract CelOptions celOptions();

    public static State create(@Nullable Boolean outcome, CelOptions celOptions) {
      return new AutoValue_RuntimeEqualityTest_State(outcome, celOptions);
    }
  }

  /** Represents expected result states for an equality test case. */
  @AutoValue
  abstract static class Result {

    /** The result {@code State} value associated with different feature flag combinations. */
    public abstract ImmutableSet<State> states();

    /**
     * Creates a Result for a comparison that is undefined (throws an Exception) under both equality
     * modes.
     */
    public static Result undefined() {
      return always(null);
    }

    /** Creates a Result for a comparison that is false under both equality modes. */
    public static Result alwaysFalse() {
      return always(false);
    }

    /** Creates a Result for a comparison that is true under both equality modes. */
    public static Result alwaysTrue() {
      return always(true);
    }

    public static Result signed(Boolean outcome) {
      return Result.builder()
          .states(
              ImmutableList.of(
                  State.create(outcome, EMPTY_OPTIONS), State.create(outcome, PROTO_EQUALITY)))
          .build();
    }

    public static Result unsigned(Boolean outcome) {
      return Result.builder()
          .states(
              ImmutableList.of(
                  State.create(outcome, UNSIGNED_LONGS),
                  State.create(outcome, PROTO_EQUALITY_UNSIGNED_LONGS)))
          .build();
    }

    private static Result always(@Nullable Boolean outcome) {
      return Result.builder()
          .states(
              ImmutableList.of(
                  State.create(outcome, EMPTY_OPTIONS),
                  State.create(outcome, PROTO_EQUALITY),
                  State.create(outcome, PROTO_EQUALITY_UNSIGNED_LONGS)))
          .build();
    }

    private static Result proto(Boolean equalsOutcome, Boolean diffOutcome) {
      return Result.builder()
          .states(
              ImmutableList.of(
                  State.create(equalsOutcome, EMPTY_OPTIONS),
                  State.create(diffOutcome, PROTO_EQUALITY),
                  State.create(diffOutcome, PROTO_EQUALITY_UNSIGNED_LONGS)))
          .build();
    }

    public static Builder builder() {
      return new AutoValue_RuntimeEqualityTest_Result.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
      abstract Builder states(ImmutableList<State> states);

      abstract Result build();
    }
  }

  @Parameter(0)
  public Object lhs;

  @Parameter(1)
  public Object rhs;

  @Parameter(2)
  public Result result;

  @Parameters
  public static List<Object[]> data() {
    return Arrays.asList(
        new Object[][] {
          // Boolean tests.
          {true, true, Result.alwaysTrue()},
          {BoolValue.of(true), true, Result.alwaysTrue()},
          {Any.pack(BoolValue.of(true)), true, Result.alwaysTrue()},
          {Value.newBuilder().setBoolValue(true).build(), true, Result.alwaysTrue()},
          {true, false, Result.alwaysFalse()},
          {0, false, Result.alwaysFalse()},

          // Bytes tests.
          {ByteString.copyFromUtf8("h¢"), ByteString.copyFromUtf8("h¢"), Result.alwaysTrue()},
          {ByteString.copyFromUtf8("hello"), ByteString.EMPTY, Result.alwaysFalse()},
          {BytesValue.of(ByteString.EMPTY), ByteString.EMPTY, Result.alwaysTrue()},
          {
            BytesValue.of(ByteString.copyFromUtf8("h¢")),
            ByteString.copyFromUtf8("h¢"),
            Result.alwaysTrue()
          },
          {Any.pack(BytesValue.of(ByteString.EMPTY)), ByteString.EMPTY, Result.alwaysTrue()},
          {"h¢", ByteString.copyFromUtf8("h¢"), Result.alwaysFalse()},

          // Double tests.
          {1.0, 1.0, Result.alwaysTrue()},
          {Double.valueOf(1.0), 1.0, Result.alwaysTrue()},
          {DoubleValue.of(42.5), 42.5, Result.alwaysTrue()},
          // Floats are unwrapped to double types.
          {FloatValue.of(1.0f), 1.0, Result.alwaysTrue()},
          {Value.newBuilder().setNumberValue(-1.5D).build(), -1.5, Result.alwaysTrue()},
          {1.0, -1.0, Result.alwaysFalse()},
          {1.0, 1.0D, Result.alwaysTrue()},
          {1.0, 1.1D, Result.alwaysFalse()},
          {1.0D, 1.1f, Result.alwaysFalse()},
          {1.0, 1, Result.alwaysTrue()},

          // Float tests.
          {1.0f, 1.0f, Result.alwaysTrue()},
          {Float.valueOf(1.0f), 1.0f, Result.alwaysTrue()},
          {1.0f, -1.0f, Result.alwaysFalse()},
          {1.0f, 1.0, Result.alwaysTrue()},

          // Integer tests.
          {16, 16, Result.alwaysTrue()},
          {17, 16, Result.alwaysFalse()},
          {17, 16.0, Result.alwaysFalse()},

          // Long tests.
          {-15L, -15L, Result.alwaysTrue()},
          // Int32 values are unwrapped to int types.
          {Int32Value.of(-15), -15L, Result.alwaysTrue()},
          {Int64Value.of(-15L), -15L, Result.alwaysTrue()},
          {Any.pack(Int32Value.of(-15)), -15L, Result.alwaysTrue()},
          {Any.pack(Int64Value.of(-15L)), -15L, Result.alwaysTrue()},
          {-15L, -16L, Result.alwaysFalse()},
          {-15L, -15, Result.alwaysTrue()},
          {-15L, 15.0, Result.alwaysFalse()},

          // Null tests.
          {null, null, Result.alwaysTrue()},
          {false, null, Result.alwaysFalse()},
          {0.0, null, Result.alwaysFalse()},
          {0, null, Result.alwaysFalse()},
          {null, "null", Result.alwaysFalse()},
          {"null", null, Result.alwaysFalse()},
          {null, NullValue.NULL_VALUE, Result.alwaysTrue()},
          {null, ImmutableList.of(), Result.alwaysFalse()},
          {ImmutableMap.of(), null, Result.alwaysFalse()},
          {ByteString.copyFromUtf8(""), null, Result.alwaysFalse()},
          {null, Timestamps.EPOCH, Result.alwaysFalse()},
          {Durations.ZERO, null, Result.alwaysFalse()},
          {NullValue.NULL_VALUE, NullValue.NULL_VALUE, Result.alwaysTrue()},
          {
            Value.newBuilder().setNullValue(NullValue.NULL_VALUE).build(),
            NullValue.NULL_VALUE,
            Result.alwaysTrue()
          },
          {
            Any.pack(Value.newBuilder().setNullValue(NullValue.NULL_VALUE).build()),
            NullValue.NULL_VALUE,
            Result.alwaysTrue()
          },

          // String tests.
          {"", "", Result.alwaysTrue()},
          {"str", "str", Result.alwaysTrue()},
          {StringValue.of("str"), "str", Result.alwaysTrue()},
          {Value.newBuilder().setStringValue("str").build(), "str", Result.alwaysTrue()},
          {Any.pack(StringValue.of("str")), "str", Result.alwaysTrue()},
          {Any.pack(Value.newBuilder().setStringValue("str").build()), "str", Result.alwaysTrue()},
          {"", "non-empty", Result.alwaysFalse()},

          // Uint tests.
          {UInt32Value.of(1234), 1234L, Result.alwaysTrue()},
          {UInt64Value.of(1234L), 1234L, Result.alwaysTrue()},
          {UInt64Value.of(1234L), Int64Value.of(1234L), Result.alwaysTrue()},
          {UInt32Value.of(1234), UnsignedLong.valueOf(1234L), Result.alwaysTrue()},
          {UInt64Value.of(1234L), UnsignedLong.valueOf(1234L), Result.alwaysTrue()},
          {Any.pack(UInt64Value.of(1234L)), UnsignedLong.valueOf(1234L), Result.alwaysTrue()},
          {UInt32Value.of(123), UnsignedLong.valueOf(1234L), Result.alwaysFalse()},
          {UInt64Value.of(123L), UnsignedLong.valueOf(1234L), Result.alwaysFalse()},
          {Any.pack(UInt64Value.of(123L)), UnsignedLong.valueOf(1234L), Result.alwaysFalse()},

          // Cross-type equality tests.
          {UInt32Value.of(1234), 1234.0, Result.alwaysTrue()},
          {UInt32Value.of(1234), 1234.0, Result.alwaysTrue()},
          {UInt64Value.of(1234L), 1234L, Result.alwaysTrue()},
          {UInt32Value.of(1234), 1234.1, Result.alwaysFalse()},
          {UInt64Value.of(1234L), 1233L, Result.alwaysFalse()},
          {UnsignedLong.valueOf(1234L), 1234L, Result.alwaysTrue()},
          {UnsignedLong.valueOf(1234L), 1234.1, Result.alwaysFalse()},
          {1234L, 1233.2, Result.alwaysFalse()},
          {-1234L, UnsignedLong.valueOf(1233L), Result.alwaysFalse()},

          // List tests.
          // Note, this list equality behaves equivalently to the following expression:
          //   1.0 == 1.0 && "dos" == 2.0 && 3.0 == 4.0
          // The middle predicate is an error; however, the last comparison yields false and so

          // the error is short-circuited away.
          {Arrays.asList(1.0, "dos", 3.0), Arrays.asList(1.0, 2.0, 4.0), Result.alwaysFalse()},
          {Arrays.asList("1", 2), ImmutableList.of("1", 2), Result.alwaysTrue()},
          {Arrays.asList("1", 2), ImmutableSet.of("1", 2), Result.alwaysTrue()},
          {Arrays.asList(1.0, 2.0, 3.0), Arrays.asList(1.0, 2.0), Result.alwaysFalse()},
          {Arrays.asList(1.0, 3.0), Arrays.asList(1.0, 2.0), Result.alwaysFalse()},
          {
            AdaptingTypes.<Integer, Long>adaptingList(
                ImmutableList.of(1, 2, 3),
                BidiConverter.of(RuntimeHelpers.INT32_TO_INT64, RuntimeHelpers.INT64_TO_INT32)),
            Arrays.asList(1L, 2L, 3L),
            Result.alwaysTrue()
          },
          {
            ListValue.newBuilder()
                .addValues(Value.newBuilder().setStringValue("hello"))
                .addValues(Value.newBuilder().setStringValue("world"))
                .build(),
            ImmutableList.of("hello", "world"),
            Result.alwaysTrue()
          },
          {
            ListValue.newBuilder()
                .addValues(Value.newBuilder().setStringValue("hello"))
                .addValues(Value.newBuilder().setListValue(ListValue.getDefaultInstance()))
                .build(),
            ImmutableList.of("hello", "world"),
            Result.alwaysFalse()
          },
          {
            ListValue.newBuilder()
                .addValues(Value.newBuilder().setListValue(ListValue.getDefaultInstance()))
                .addValues(
                    Value.newBuilder()
                        .setListValue(
                            ListValue.newBuilder()
                                .addValues(Value.newBuilder().setBoolValue(true))))
                .build(),
            ImmutableList.of(ImmutableList.of(), ImmutableList.of(true)),
            Result.alwaysTrue()
          },
          {
            Value.newBuilder()
                .setListValue(
                    ListValue.newBuilder()
                        .addValues(Value.newBuilder().setNumberValue(-1.5))
                        .addValues(Value.newBuilder().setNumberValue(42.25)))
                .build(),
            AdaptingTypes.<Float, Double>adaptingList(
                ImmutableList.of(-1.5f, 42.25f),
                BidiConverter.of(RuntimeHelpers.FLOAT_TO_DOUBLE, RuntimeHelpers.DOUBLE_TO_FLOAT)),
            Result.alwaysTrue()
          },

          // Map tests.
          {ImmutableMap.of("one", 1), ImmutableMap.of("one", "uno"), Result.alwaysFalse()},
          {ImmutableMap.of("two", 2), ImmutableMap.of("two", 3), Result.alwaysFalse()},
          {ImmutableMap.of("one", 2), ImmutableMap.of("two", 3), Result.alwaysFalse()},
          // Note, this map is the composition of the following two tests above where:
          //   ("one", 1) == ("one", "uno") -> error
          //   ("two", 2) == ("two", 3) -> false
          // Within CEL error && false -> false, and the key order in the test has specifically
          // been chosen to exercise this behavior.
          {
            ImmutableMap.of("one", 1, "two", 2),
            ImmutableMap.of("one", "uno", "two", 3),
            Result.alwaysFalse()
          },
          {ImmutableMap.of("key", "value"), ImmutableMap.of("key", "value"), Result.alwaysTrue()},
          {ImmutableMap.of(), ImmutableMap.of("key", "value"), Result.alwaysFalse()},
          {ImmutableMap.of("key", "value"), ImmutableMap.of("key", "diff"), Result.alwaysFalse()},
          {ImmutableMap.of("key", 42), ImmutableMap.of("key", 42L), Result.alwaysTrue()},
          {ImmutableMap.of("key", 42.0), ImmutableMap.of("key", 42L), Result.alwaysTrue()},
          {
            AdaptingTypes.<String, Integer, String, Long>adaptingMap(
                ImmutableMap.of("key1", 42, "key2", 31, "key3", 20),
                BidiConverter.identity(),
                BidiConverter.of(RuntimeHelpers.INT32_TO_INT64, RuntimeHelpers.INT64_TO_INT32)),
            ImmutableMap.of("key1", 42L, "key2", 31L, "key3", 20L),
            Result.alwaysTrue()
          },
          {
            AdaptingTypes.<Integer, Float, Long, Double>adaptingMap(
                ImmutableMap.of(1, 42.5f, 2, 31f, 3, 20.25f),
                BidiConverter.of(RuntimeHelpers.INT32_TO_INT64, RuntimeHelpers.INT64_TO_INT32),
                BidiConverter.of(RuntimeHelpers.FLOAT_TO_DOUBLE, RuntimeHelpers.DOUBLE_TO_FLOAT)),
            ImmutableMap.of(1L, 42.5D, 2L, 31D, 3L, 20.25D),
            Result.alwaysTrue()
          },
          {
            AdaptingTypes.<String, Float, String, Double>adaptingMap(
                ImmutableMap.of("1", 42.5f, "2", 31f, "3", 20.25f),
                BidiConverter.identity(),
                BidiConverter.of(RuntimeHelpers.FLOAT_TO_DOUBLE, RuntimeHelpers.DOUBLE_TO_FLOAT)),
            Struct.getDefaultInstance(),
            Result.alwaysFalse()
          },
          {
            AdaptingTypes.<String, Float, String, Double>adaptingMap(
                ImmutableMap.of("1", 42.5f, "2", 31f, "3", 20.25f),
                BidiConverter.identity(),
                BidiConverter.of(RuntimeHelpers.FLOAT_TO_DOUBLE, RuntimeHelpers.DOUBLE_TO_FLOAT)),
            Struct.newBuilder()
                .putFields("1", Value.newBuilder().setNumberValue(42.5D).build())
                .putFields("2", Value.newBuilder().setNumberValue(31D).build())
                .putFields("3", Value.newBuilder().setNumberValue(20.25D).build())
                .build(),
            Result.alwaysTrue()
          },
          {
            AdaptingTypes.<String, Float, String, Double>adaptingMap(
                ImmutableMap.of("1", 42.5f, "2", 31f, "3", 20.25f),
                BidiConverter.identity(),
                BidiConverter.of(RuntimeHelpers.FLOAT_TO_DOUBLE, RuntimeHelpers.DOUBLE_TO_FLOAT)),
            Struct.newBuilder()
                .putFields("1", Value.newBuilder().setNumberValue(42.5D).build())
                .putFields("2", Value.newBuilder().setNumberValue(31D).build())
                .putFields("3", Value.newBuilder().setStringValue("oops").build())
                .build(),
            Result.alwaysFalse()
          },

          // Protobuf tests.
          {
            AttributeContext.newBuilder().setRequest(Request.getDefaultInstance()).build(),
            AttributeContext.newBuilder().setRequest(Request.newBuilder().setHost("")).build(),
            Result.alwaysTrue()
          },
          {
            AttributeContext.newBuilder()
                .setRequest(Request.getDefaultInstance())
                .setOrigin(Peer.getDefaultInstance())
                .build(),
            AttributeContext.newBuilder().setRequest(Request.getDefaultInstance()).build(),
            Result.alwaysFalse()
          },
          // Proto differencer unpacks any values.
          {
            AttributeContext.newBuilder()
                .addExtensions(
                    Any.newBuilder()
                        .setTypeUrl("type.googleapis.com/google.rpc.context.AttributeContext")
                        .setValue(ByteString.copyFromUtf8("\032\000:\000"))
                        .build())
                .build(),
            AttributeContext.newBuilder()
                .addExtensions(
                    Any.newBuilder()
                        .setTypeUrl("type.googleapis.com/google.rpc.context.AttributeContext")
                        .setValue(ByteString.copyFromUtf8(":\000\032\000"))
                        .build())
                .build(),
            Result.builder()
                .states(
                    ImmutableList.of(
                        State.create(false, EMPTY_OPTIONS), State.create(true, PROTO_EQUALITY)))
                .build()
          },
          // If type url is missing, fallback to bytes comparison for payload.
          {
            AttributeContext.newBuilder()
                .addExtensions(
                    Any.newBuilder().setValue(ByteString.copyFromUtf8("\032\000:\000")).build())
                .build(),
            AttributeContext.newBuilder()
                .addExtensions(
                    Any.newBuilder().setValue(ByteString.copyFromUtf8(":\000\032\000")).build())
                .build(),
            Result.alwaysFalse()
          },
          {
            AttributeContext.newBuilder()
                .setRequest(Request.getDefaultInstance())
                .setOrigin(Peer.getDefaultInstance())
                .build(),
            "test string",
            Result.alwaysFalse()
          },
          {
            AttributeContext.newBuilder()
                .setRequest(Request.getDefaultInstance())
                .setOrigin(Peer.getDefaultInstance())
                .build(),
            null,
            Result.alwaysFalse()
          },
          {
            AttributeContext.newBuilder()
                .addExtensions(
                    Any.pack(
                        AttributeContext.newBuilder()
                            .setRequest(Request.getDefaultInstance())
                            .setOrigin(Peer.getDefaultInstance())
                            .build()))
                .build(),
            AttributeContext.newBuilder()
                .addExtensions(
                    Any.pack(
                        AttributeContext.newBuilder()
                            .setRequest(Request.getDefaultInstance())
                            .build()))
                .build(),
            Result.alwaysFalse()
          },
          {
            AttributeContext.getDefaultInstance(),
            AttributeContext.newBuilder()
                .setRequest(Request.newBuilder().setHost("localhost"))
                .build(),
            Result.alwaysFalse()
          },
          // Differently typed messages aren't comparable.
          {AttributeContext.getDefaultInstance(), Auth.getDefaultInstance(), Result.alwaysFalse()},
          // Message.equals() treats NaN values as equal. Message differencer treats NaN values
          // as inequal (the same behavior as the C++ implementation).
          {
            AttributeContext.newBuilder()
                .setRequest(
                    Request.newBuilder()
                        .setAuth(
                            Auth.newBuilder()
                                .setClaims(
                                    Struct.newBuilder()
                                        .putFields(
                                            "custom",
                                            Value.newBuilder()
                                                .setNumberValue(Double.NaN)
                                                .build()))))
                .build(),
            AttributeContext.newBuilder()
                .setRequest(
                    Request.newBuilder()
                        .setAuth(
                            Auth.newBuilder()
                                .setClaims(
                                    Struct.newBuilder()
                                        .putFields(
                                            "custom",
                                            Value.newBuilder()
                                                .setNumberValue(Double.NaN)
                                                .build()))))
                .build(),
            Result.proto(/* equalsOutcome= */ true, /* diffOutcome= */ false),
          },

          // Note: this is the motivating use case for converting to heterogeneous equality in
          // the future.
          {
            AttributeContext.newBuilder()
                .setRequest(
                    Request.newBuilder()
                        .setAuth(
                            Auth.newBuilder()
                                .setClaims(
                                    Struct.newBuilder()
                                        .putFields(
                                            "custom",
                                            Value.newBuilder().setNumberValue(123.0).build()))))
                .build(),
            AttributeContext.newBuilder()
                .setRequest(
                    Request.newBuilder()
                        .setAuth(
                            Auth.newBuilder()
                                .setClaims(
                                    Struct.newBuilder()
                                        .putFields(
                                            "custom",
                                            Value.newBuilder().setBoolValue(true).build()))))
                .build(),
            Result.alwaysFalse(),
          },
        });
  }

  @Test
  public void objectEquals() throws Exception {
    for (State state : result.states()) {
      if (state.outcome() == null) {
        Assert.assertThrows(
            CelRuntimeException.class,
            () -> RUNTIME_EQUALITY.objectEquals(lhs, rhs, state.celOptions()));
        Assert.assertThrows(
            CelRuntimeException.class,
            () -> RUNTIME_EQUALITY.objectEquals(rhs, lhs, state.celOptions()));
        return;
      }
      assertThat(RUNTIME_EQUALITY.objectEquals(lhs, rhs, state.celOptions()))
          .isEqualTo(state.outcome());
      assertThat(RUNTIME_EQUALITY.objectEquals(rhs, lhs, state.celOptions()))
          .isEqualTo(state.outcome());
    }
  }
}
