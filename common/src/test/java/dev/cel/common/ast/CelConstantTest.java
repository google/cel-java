// Copyright 2023 Google LLC
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

package dev.cel.common.ast;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import com.google.common.primitives.UnsignedLong;
import com.google.protobuf.ByteString;
import com.google.protobuf.Duration;
import com.google.protobuf.NullValue;
import com.google.protobuf.Timestamp;
import com.google.testing.junit.testparameterinjector.TestParameter;
import com.google.testing.junit.testparameterinjector.TestParameterInjector;
import dev.cel.common.ast.CelConstant.Kind;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(TestParameterInjector.class)
public class CelConstantTest {

  @Test
  public void equality_objectsAreValueEqual_success() {
    assertThat(CelConstant.ofValue(NullValue.NULL_VALUE))
        .isEqualTo(CelConstant.ofValue(NullValue.NULL_VALUE));
    assertThat(CelConstant.ofValue(true)).isEqualTo(CelConstant.ofValue(true));
    assertThat(CelConstant.ofValue(false)).isEqualTo(CelConstant.ofValue(false));
    assertThat(CelConstant.ofValue(2)).isEqualTo(CelConstant.ofValue(2));
    assertThat(CelConstant.ofValue(UnsignedLong.valueOf(2)))
        .isEqualTo(CelConstant.ofValue(UnsignedLong.valueOf(2)));
    assertThat(CelConstant.ofValue(2.1)).isEqualTo(CelConstant.ofValue(2.1));
    assertThat(CelConstant.ofValue("Hello world!")).isEqualTo(CelConstant.ofValue("Hello world!"));
    assertThat(CelConstant.ofValue(ByteString.copyFromUtf8("Test")))
        .isEqualTo(CelConstant.ofValue(ByteString.copyFromUtf8("Test")));
    assertThat(CelConstant.ofValue(Duration.newBuilder().setSeconds(100L).build()))
        .isEqualTo(CelConstant.ofValue(Duration.newBuilder().setSeconds(100L).build()));
    assertThat(CelConstant.ofValue(Timestamp.newBuilder().setSeconds(100L).build()))
        .isEqualTo(CelConstant.ofValue(Timestamp.newBuilder().setSeconds(100L).build()));
  }

  @Test
  public void equality_valueEqualityUnsatisfied_fails() {
    assertThat(CelConstant.ofValue(NullValue.NULL_VALUE))
        .isNotEqualTo(CelConstant.ofValue(NullValue.UNRECOGNIZED));
    assertThat(CelConstant.ofValue(true)).isNotEqualTo(CelConstant.ofValue(false));
    assertThat(CelConstant.ofValue(false)).isNotEqualTo(CelConstant.ofValue(true));
    assertThat(CelConstant.ofValue(3)).isNotEqualTo(CelConstant.ofValue(2));
    assertThat(CelConstant.ofValue(UnsignedLong.valueOf(3)))
        .isNotEqualTo(CelConstant.ofValue(UnsignedLong.valueOf(2)));
    assertThat(CelConstant.ofValue(3)).isNotEqualTo(CelConstant.ofValue(UnsignedLong.valueOf(3)));
    assertThat(CelConstant.ofValue(3)).isNotEqualTo(CelConstant.ofValue(3.0));
    assertThat(CelConstant.ofValue(3.1)).isNotEqualTo(CelConstant.ofValue(2.1));
    assertThat(CelConstant.ofValue("world!")).isNotEqualTo(CelConstant.ofValue("Hello world!"));
    assertThat(CelConstant.ofValue(ByteString.copyFromUtf8("T")))
        .isNotEqualTo(CelConstant.ofValue(ByteString.copyFromUtf8("Test")));
    assertThat(CelConstant.ofValue(Duration.newBuilder().setSeconds(100L).build()))
        .isNotEqualTo(CelConstant.ofValue(Duration.newBuilder().setSeconds(50).build()));
    assertThat(CelConstant.ofValue(Timestamp.newBuilder().setSeconds(100L).build()))
        .isNotEqualTo(CelConstant.ofValue(Timestamp.newBuilder().setSeconds(50).build()));
  }

  @Test
  public void constructNullValue() {
    CelConstant constant = CelConstant.ofValue(NullValue.NULL_VALUE);

    assertThat(constant.nullValue()).isEqualTo(NullValue.NULL_VALUE);
  }

  @Test
  public void constructBooleanValue() {
    CelConstant trueConstant = CelConstant.ofValue(true);
    CelConstant falseConstant = CelConstant.ofValue(false);

    assertThat(trueConstant.booleanValue()).isTrue();
    assertThat(falseConstant.booleanValue()).isFalse();
  }

  @Test
  public void constructInt64Value() {
    CelConstant constant = CelConstant.ofValue(2);

    assertThat(constant.int64Value()).isEqualTo(2);
  }

  @Test
  public void constructUInt64Value() {
    CelConstant constant = CelConstant.ofValue(UnsignedLong.valueOf(2));

    assertThat(constant.uint64Value()).isEqualTo(UnsignedLong.valueOf(2));
  }

  @Test
  public void constructDoubleValue() {
    CelConstant constant = CelConstant.ofValue(2.1);

    assertThat(constant.doubleValue()).isEqualTo(2.1);
  }

  @Test
  public void constructStringValue() {
    CelConstant constant = CelConstant.ofValue("Hello world!");

    assertThat(constant.stringValue()).isEqualTo("Hello world!");
  }

  @Test
  public void constructBytesValue() {
    CelConstant constant = CelConstant.ofValue(ByteString.copyFromUtf8("Test"));

    assertThat(constant.bytesValue()).isEqualTo(ByteString.copyFromUtf8("Test"));
  }

  @Test
  public void constructDurationValue() {
    CelConstant constant = CelConstant.ofValue(Duration.newBuilder().setSeconds(100L).build());

    assertThat(constant.durationValue()).isEqualTo(Duration.newBuilder().setSeconds(100L).build());
  }

  @Test
  public void constructTimestampValue() {
    CelConstant constant = CelConstant.ofValue(Timestamp.newBuilder().setSeconds(100L).build());

    assertThat(constant.timestampValue())
        .isEqualTo(Timestamp.newBuilder().setSeconds(100L).build());
  }

  @Test
  public void constructNotSetConstant() {
    CelConstant constant = CelConstant.ofNotSet();

    assertThat(constant).isNotNull();
    assertThat(constant.getKind()).isEqualTo(Kind.NOT_SET);
  }

  private enum CelConstantTestCase {
    NULL(CelConstant.ofValue(NullValue.NULL_VALUE)),
    BOOLEAN(CelConstant.ofValue(true)),
    INT64(CelConstant.ofValue(2)),
    UINT64(CelConstant.ofValue(UnsignedLong.valueOf(2))),
    DOUBLE(CelConstant.ofValue(2.1)),
    STRING(CelConstant.ofValue("Hello world!")),
    BYTES(CelConstant.ofValue(ByteString.copyFromUtf8("Test"))),
    DURATION(CelConstant.ofValue(Duration.newBuilder().setSeconds(100L).build())),
    TIMESTAMP(CelConstant.ofValue(Timestamp.newBuilder().setSeconds(100L).build()));

    final CelConstant constant;

    CelConstantTestCase(CelConstant constant) {
      this.constant = constant;
    }
  }

  @Test
  public void getValueOnInvalidKindCase_throwsException(
      @TestParameter CelConstantTestCase testCase) {
    CelConstant constant = testCase.constant;
    CelConstant.Kind constantKindCase = testCase.constant.getKind();
    if (constantKindCase != Kind.NULL_VALUE) {
      assertThrows(UnsupportedOperationException.class, constant::nullValue);
    }
    if (constantKindCase != Kind.BOOLEAN_VALUE) {
      assertThrows(UnsupportedOperationException.class, constant::booleanValue);
    }
    if (constantKindCase != Kind.INT64_VALUE) {
      assertThrows(UnsupportedOperationException.class, constant::int64Value);
    }
    if (constantKindCase != Kind.UINT64_VALUE) {
      assertThrows(UnsupportedOperationException.class, constant::uint64Value);
    }
    if (constantKindCase != Kind.DOUBLE_VALUE) {
      assertThrows(UnsupportedOperationException.class, constant::doubleValue);
    }
    if (constantKindCase != Kind.STRING_VALUE) {
      assertThrows(UnsupportedOperationException.class, constant::stringValue);
    }
    if (constantKindCase != Kind.BYTES_VALUE) {
      assertThrows(UnsupportedOperationException.class, constant::bytesValue);
    }
    if (constantKindCase != Kind.DURATION_VALUE) {
      assertThrows(UnsupportedOperationException.class, constant::durationValue);
    }
    if (constantKindCase != Kind.TIMESTAMP_VALUE) {
      assertThrows(UnsupportedOperationException.class, constant::timestampValue);
    }
  }
}
