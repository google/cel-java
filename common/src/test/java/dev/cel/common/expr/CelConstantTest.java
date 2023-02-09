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

package dev.cel.common.expr;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import com.google.protobuf.ByteString;
import com.google.protobuf.NullValue;
import com.google.testing.junit.testparameterinjector.TestParameter;
import com.google.testing.junit.testparameterinjector.TestParameterInjector;
import dev.cel.common.expr.CelConstant.Kind;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(TestParameterInjector.class)
public class CelConstantTest {

  @Test
  public void equality_objectsAreValueEqual_success() {
    assertThat(CelConstant.ofNullValue(NullValue.NULL_VALUE))
        .isEqualTo(CelConstant.ofNullValue(NullValue.NULL_VALUE));
    assertThat(CelConstant.ofBooleanValue(true)).isEqualTo(CelConstant.ofBooleanValue(true));
    assertThat(CelConstant.ofBooleanValue(false)).isEqualTo(CelConstant.ofBooleanValue(false));
    assertThat(CelConstant.ofInt64Value(2)).isEqualTo(CelConstant.ofInt64Value(2));
    assertThat(CelConstant.ofUInt64Value(2)).isEqualTo(CelConstant.ofUInt64Value(2));
    assertThat(CelConstant.ofDoubleValue(2.1)).isEqualTo(CelConstant.ofDoubleValue(2.1));
    assertThat(CelConstant.ofStringValue("Hello world!"))
        .isEqualTo(CelConstant.ofStringValue("Hello world!"));
    assertThat(CelConstant.ofBytesValue(ByteString.copyFromUtf8("Test")))
        .isEqualTo(CelConstant.ofBytesValue(ByteString.copyFromUtf8("Test")));
  }

  @Test
  public void equality_valueEqualityUnsatisfied_fails() {
    assertThat(CelConstant.ofNullValue(NullValue.NULL_VALUE))
        .isNotEqualTo(CelConstant.ofNullValue(NullValue.UNRECOGNIZED));
    assertThat(CelConstant.ofBooleanValue(true)).isNotEqualTo(CelConstant.ofBooleanValue(false));
    assertThat(CelConstant.ofBooleanValue(false)).isNotEqualTo(CelConstant.ofBooleanValue(true));
    assertThat(CelConstant.ofInt64Value(3)).isNotEqualTo(CelConstant.ofInt64Value(2));
    assertThat(CelConstant.ofUInt64Value(3)).isNotEqualTo(CelConstant.ofUInt64Value(2));
    assertThat(CelConstant.ofInt64Value(3)).isNotEqualTo(CelConstant.ofUInt64Value(3));
    assertThat(CelConstant.ofInt64Value(3)).isNotEqualTo(CelConstant.ofDoubleValue(3));
    assertThat(CelConstant.ofDoubleValue(3.1)).isNotEqualTo(CelConstant.ofDoubleValue(2.1));
    assertThat(CelConstant.ofStringValue("world!"))
        .isNotEqualTo(CelConstant.ofStringValue("Hello world!"));
    assertThat(CelConstant.ofBytesValue(ByteString.copyFromUtf8("T")))
        .isNotEqualTo(CelConstant.ofBytesValue(ByteString.copyFromUtf8("Test")));
  }

  @Test
  public void constructNullValue() {
    CelConstant constant = CelConstant.ofNullValue(NullValue.NULL_VALUE);

    assertThat(constant.nullValue()).isEqualTo(NullValue.NULL_VALUE);
  }

  @Test
  public void constructBooleanValue() {
    CelConstant trueConstant = CelConstant.ofBooleanValue(true);
    CelConstant falseConstant = CelConstant.ofBooleanValue(false);

    assertThat(trueConstant.booleanValue()).isTrue();
    assertThat(falseConstant.booleanValue()).isFalse();
  }

  @Test
  public void constructInt64Value() {
    CelConstant constant = CelConstant.ofInt64Value(2);

    assertThat(constant.int64Value()).isEqualTo(2);
  }

  @Test
  public void constructUInt64Value() {
    CelConstant constant = CelConstant.ofUInt64Value(2);

    assertThat(constant.uint64Value()).isEqualTo(2);
  }

  @Test
  public void constructDoubleValue() {
    CelConstant constant = CelConstant.ofDoubleValue(2.1);

    assertThat(constant.doubleValue()).isEqualTo(2.1);
  }

  @Test
  public void constructStringValue() {
    CelConstant constant = CelConstant.ofStringValue("Hello world!");

    assertThat(constant.stringValue()).isEqualTo("Hello world!");
  }

  @Test
  public void constructBytesValue() {
    CelConstant constant = CelConstant.ofBytesValue(ByteString.copyFromUtf8("Test"));

    assertThat(constant.bytesValue()).isEqualTo(ByteString.copyFromUtf8("Test"));
  }

  private enum CelConstantTestCase {
    NULL(CelConstant.ofNullValue(NullValue.NULL_VALUE)),
    BOOLEAN(CelConstant.ofBooleanValue(true)),
    INT64(CelConstant.ofInt64Value(2)),
    UINT64(CelConstant.ofUInt64Value(2)),
    DOUBLE(CelConstant.ofDoubleValue(2.1)),
    STRING(CelConstant.ofStringValue("Hello world!")),
    BYTES(CelConstant.ofBytesValue(ByteString.copyFromUtf8("Test")));

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
  }
}
