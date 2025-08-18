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

package dev.cel.common.internal;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import com.google.common.primitives.UnsignedLong;
import com.google.testing.junit.testparameterinjector.TestParameterInjector;
import com.google.testing.junit.testparameterinjector.TestParameters;
import dev.cel.common.ast.CelConstant;
import dev.cel.common.ast.CelConstant.Kind;
import dev.cel.common.values.CelByteString;
import java.text.ParseException;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(TestParameterInjector.class)
public final class ConstantsTest {

  @Test
  public void parseInt_base10Zero() throws Exception {
    CelConstant constant = Constants.parseInt("0");
    assertThat(constant.getKind()).isEqualTo(Kind.INT64_VALUE);
    assertThat(constant.int64Value()).isEqualTo(0L);
  }

  @Test
  public void parseInt_base10Max() throws Exception {
    CelConstant constant = Constants.parseInt(Long.toString(Long.MAX_VALUE, 10));
    assertThat(constant.getKind()).isEqualTo(Kind.INT64_VALUE);
    assertThat(constant.int64Value()).isEqualTo(Long.MAX_VALUE);
  }

  @Test
  public void parseInt_base10Min() throws Exception {
    CelConstant constant = Constants.parseInt(Long.toString(Long.MIN_VALUE, 10));
    assertThat(constant.getKind()).isEqualTo(Kind.INT64_VALUE);
    assertThat(constant.int64Value()).isEqualTo(Long.MIN_VALUE);
  }

  @Test
  public void parseInt_base16Zero() throws Exception {
    CelConstant constant = Constants.parseInt("0x0");
    assertThat(constant.getKind()).isEqualTo(Kind.INT64_VALUE);
    assertThat(constant.int64Value()).isEqualTo(0L);
  }

  @Test
  public void parseInt_base16Max() throws Exception {
    CelConstant constant = Constants.parseInt("0x" + Long.toString(Long.MAX_VALUE, 16));
    assertThat(constant.getKind()).isEqualTo(Kind.INT64_VALUE);
    assertThat(constant.int64Value()).isEqualTo(Long.MAX_VALUE);
  }

  @Test
  public void parseInt_base16Min() throws Exception {
    CelConstant constant =
        Constants.parseInt("-0x" + Long.toString(Long.MIN_VALUE, 16).substring(1));
    assertThat(constant.getKind()).isEqualTo(Kind.INT64_VALUE);
    assertThat(constant.int64Value()).isEqualTo(Long.MIN_VALUE);
  }

  @Test
  public void parseInt_base16ThrowsOnInvalidArgument() throws Exception {
    assertThrows(ParseException.class, () -> Constants.parseInt("0xz"));
  }

  @Test
  public void parseInt_base10ThrowsOnInvalidArgument() throws Exception {
    assertThrows(ParseException.class, () -> Constants.parseInt("abcdef"));
  }

  @Test
  @TestParameters("{literal: 0u}")
  @TestParameters("{literal: 0U}")
  public void parseUint_base10Zero(String literal) throws Exception {
    CelConstant constant = Constants.parseUint(literal);
    assertThat(constant.getKind()).isEqualTo(Kind.UINT64_VALUE);
    assertThat(constant.uint64Value()).isEqualTo(UnsignedLong.valueOf(0L));
  }

  @Test
  @TestParameters("{literal: 18446744073709551615u}")
  @TestParameters("{literal: 18446744073709551615U}")
  public void parseUint_base10Max(String literal) throws Exception {
    CelConstant constant = Constants.parseUint(literal);
    assertThat(constant.getKind()).isEqualTo(Kind.UINT64_VALUE);
    assertThat(constant.uint64Value()).isEqualTo(UnsignedLong.MAX_VALUE);
  }

  @Test
  @TestParameters("{literal: 0x0u}")
  @TestParameters("{literal: 0x0U}")
  public void parseUint_base16Zero(String literal) throws Exception {
    CelConstant constant = Constants.parseUint(literal);
    assertThat(constant.getKind()).isEqualTo(Kind.UINT64_VALUE);
    assertThat(constant.uint64Value()).isEqualTo(UnsignedLong.valueOf(0L));
  }

  @Test
  @TestParameters("{literal: 0xffffffffffffffffu}")
  @TestParameters("{literal: 0xffffffffffffffffU}")
  public void parseUint_base16Max(String literal) throws Exception {
    CelConstant constant = Constants.parseUint(literal);
    assertThat(constant.getKind()).isEqualTo(Kind.UINT64_VALUE);
    assertThat(constant.uint64Value()).isEqualTo(UnsignedLong.MAX_VALUE);
  }

  @Test
  @TestParameters("{literal: 0xzu}")
  @TestParameters("{literal: 0xZU}")
  public void parseUint_base16ThrowsOnInvalidArgument(String literal) throws Exception {
    assertThrows(ParseException.class, () -> Constants.parseUint(literal));
  }

  @Test
  @TestParameters("{literal: abcdefu}")
  @TestParameters("{literal: abcdefU}")
  public void parseUint_base10ThrowsOnInvalidArgument(String literal) throws Exception {
    assertThrows(ParseException.class, () -> Constants.parseUint(literal));
  }

  @Test
  public void parseUint_throwsOnMissingSuffix() throws Exception {
    assertThrows(ParseException.class, () -> Constants.parseUint("0"));
  }

  @Test
  public void parseDouble_positiveZero() throws Exception {
    CelConstant constant = Constants.parseDouble("0.0");
    assertThat(constant.getKind()).isEqualTo(Kind.DOUBLE_VALUE);
    assertThat(constant.doubleValue()).isEqualTo(0.0);
  }

  @Test
  public void parseDouble_negativeZero() throws Exception {
    CelConstant constant = Constants.parseDouble("-0.0");
    assertThat(constant.getKind()).isEqualTo(Kind.DOUBLE_VALUE);
    assertThat(constant.doubleValue()).isEqualTo(-0.0);
  }

  @Test
  public void parseDouble_max() throws Exception {
    CelConstant constant = Constants.parseDouble(Double.toString(Double.MAX_VALUE));
    assertThat(constant.getKind()).isEqualTo(Kind.DOUBLE_VALUE);
    assertThat(constant.doubleValue()).isEqualTo(Double.MAX_VALUE);
  }

  @Test
  public void parseDouble_min() throws Exception {
    CelConstant constant = Constants.parseDouble(Double.toString(-Double.MAX_VALUE));
    assertThat(constant.getKind()).isEqualTo(Kind.DOUBLE_VALUE);
    assertThat(constant.doubleValue()).isEqualTo(-Double.MAX_VALUE);
  }

  @Test
  public void parseDouble_throwsOnInvalidArgument() throws Exception {
    assertThrows(ParseException.class, () -> Constants.parseDouble("0.#"));
  }

  @Test
  public void parseString_throwsOnInvalidArgument() throws Exception {
    assertThrows(ParseException.class, () -> Constants.parseString(""));
    assertThrows(ParseException.class, () -> Constants.parseString("\"   '"));
    assertThrows(ParseException.class, () -> Constants.parseString("''''"));
  }

  @Test
  public void parseString_escapeSequence() throws Exception {
    testQuotedString("\\a\\b\\f\\n\\r\\t\\v\\`\\'\\\"\\?\\\\", "\u0007\b\f\n\r\t\u000b`'\"?\\");
  }

  @Test
  public void parseString_normalizesNewlines() throws Exception {
    testQuotedString("\r\n\r\n\n", "\n\n\n");
  }

  @Test
  public void parseString_throwsOnInvalidEscapeSequence() throws Exception {
    assertThrows(ParseException.class, () -> Constants.parseString("\"\\z\""));
    assertThrows(ParseException.class, () -> Constants.parseString("\"\\\\\\\""));
  }

  @Test
  public void parseString_octalEscapeSequence() throws Exception {
    testQuotedString("\\150\\145\\154\\154\\157", "hello");
  }

  @Test
  public void parseString_throwsOnInvalidOctalEscapeSequence() throws Exception {
    assertThrows(ParseException.class, () -> Constants.parseString("\"\\0\""));
    assertThrows(ParseException.class, () -> Constants.parseString("\"\\029\""));
  }

  @Test
  public void parseString_hexEscapeSequence() throws Exception {
    testQuotedString("\\x68\\x65\\x6c\\x6c\\x6f", "hello");
  }

  @Test
  public void parseString_throwsOnInvalidHexEscapeSequence() throws Exception {
    assertThrows(ParseException.class, () -> Constants.parseString("\"\\x9\""));
    assertThrows(ParseException.class, () -> Constants.parseString("\"\\x9g\""));
  }

  @Test
  public void parseString_shortUnicodeEscapeSequence() throws Exception {
    testQuotedString("\\u0068\\u0065\\u006c\\u006c\\u006f", "hello");
  }

  @Test
  public void parseString_throwsOnInvalidShortUnicodeEscapeSequence() throws Exception {
    assertThrows(ParseException.class, () -> Constants.parseString("\"\\u009\""));
    assertThrows(ParseException.class, () -> Constants.parseString("\"\\u009g\""));
  }

  @Test
  public void parseString_longUnicodeEscapeSequence() throws Exception {
    testQuotedString("\\U00000068\\U00000065\\U0000006c\\U0000006c\\U0000006f", "hello");
  }

  @Test
  public void parseString_throwsOnInvalidLongUnicodeEscapeSequence() throws Exception {
    assertThrows(ParseException.class, () -> Constants.parseString("\"\\U0000009\""));
    assertThrows(ParseException.class, () -> Constants.parseString("\"\\U0000009g\""));
  }

  @Test
  public void parseString_throwsOnInvalidCodePoint() throws Exception {
    assertThat(
            assertThrows(ParseException.class, () -> Constants.parseString("\"\\uD900\""))
                .getErrorOffset())
        // Should be 1, as the error should start at the beginning of the sequence.
        .isEqualTo(1);
    assertThrows(ParseException.class, () -> Constants.parseString("\"\\uDD00\""));
    assertThrows(ParseException.class, () -> Constants.parseString("\"\\uDD0\""));
    assertThrows(ParseException.class, () -> Constants.parseString("\"\\U0000D900\""));
    assertThrows(ParseException.class, () -> Constants.parseString("\"\\U0000DD00\""));
    assertThrows(ParseException.class, () -> Constants.parseString("\"\\U00110000\""));
  }

  private static void testString(String actual, String expected) throws Exception {
    CelConstant constant = Constants.parseString(actual);
    assertThat(constant.getKind()).isEqualTo(Kind.STRING_VALUE);
    assertThat(constant.stringValue()).isEqualTo(expected);
  }

  private static void testQuotedString(String actual, String expected) throws Exception {
    testString("\"" + actual + "\"", expected);
    testString("'" + actual + "'", expected);
    testString("\"\"\"" + actual + "\"\"\"", expected);
    testString("'''" + actual + "'''", expected);
  }

  @Test
  public void parseBytes_throwsOnInvalidArgument() throws Exception {
    assertThrows(ParseException.class, () -> Constants.parseBytes(""));
    assertThrows(ParseException.class, () -> Constants.parseBytes("b"));
    assertThrows(ParseException.class, () -> Constants.parseBytes("b\"   '"));
    assertThrows(ParseException.class, () -> Constants.parseBytes("b''''"));
  }

  @Test
  public void parseBytes_escapeSequence() throws Exception {
    testQuotedBytes("\\a\\b\\f\\n\\r\\t\\v\\`\\'\\\"\\?\\\\", "\u0007\b\f\n\r\t\u000b`'\"?\\");
  }

  @Test
  public void parseBytes_normalizesNewlines() throws Exception {
    testQuotedBytes("\r\n\r\n\n", "\n\n\n");
  }

  @Test
  public void parseBytes_throwsOnInvalidEscapeSequence() throws Exception {
    assertThrows(ParseException.class, () -> Constants.parseBytes("\"\\z\""));
    assertThrows(ParseException.class, () -> Constants.parseBytes("\"\\\\\\\""));
  }

  @Test
  public void parseBytes_octalEscapeSequence() throws Exception {
    testQuotedBytes("\\150\\145\\154\\154\\157", "hello");
  }

  @Test
  public void parseBytes_throwsOnInvalidOctalEscapeSequence() throws Exception {
    assertThrows(ParseException.class, () -> Constants.parseBytes("\"\\0\""));
    assertThrows(ParseException.class, () -> Constants.parseBytes("\"\\029\""));
  }

  @Test
  public void parseBytes_hexEscapeSequence() throws Exception {
    testQuotedBytes("\\x68\\x65\\x6c\\x6c\\x6f", "hello");
  }

  @Test
  public void parseBytes_throwsOnInvalidHexEscapeSequence() throws Exception {
    assertThrows(ParseException.class, () -> Constants.parseBytes("\"\\x9\""));
    assertThrows(ParseException.class, () -> Constants.parseBytes("\"\\x9g\""));
  }

  @Test
  public void parseBytes_throwsOnShortUnicodeEscapeSequence() throws Exception {
    assertThrows(
        ParseException.class,
        () -> Constants.parseBytes("b\"\\u0068\\u0065\\u006c\\u006c\\u006f\""));
  }

  @Test
  public void parseBytes_throwsOnLongUnicodeEscapeSequence() throws Exception {
    assertThrows(
        ParseException.class,
        () -> Constants.parseBytes("b\"\\U00000068\\U00000065\\U0000006c\\U0000006c\\U0000006f\""));
  }

  @Test
  public void parseBytes_multibyteCodePoints() throws Exception {
    // An input string with 4 code points where each, when represented in UTF-8, encodes to
    // different length code unit sequences.
    String input = "a\u0080\u0800" + new String(Character.toChars(0x10000));
    testQuotedBytes(input, input);
  }

  private static void testBytes(String actual, String expected) throws Exception {
    CelConstant constant = Constants.parseBytes(actual);
    assertThat(constant.getKind()).isEqualTo(Kind.BYTES_VALUE);
    assertThat(constant.bytesValue()).isEqualTo(CelByteString.copyFromUtf8(expected));
  }

  private static void testQuotedBytes(String actual, String expected) throws Exception {
    testBytes("b\"" + actual + "\"", expected);
    testBytes("b'" + actual + "'", expected);
    testBytes("b\"\"\"" + actual + "\"\"\"", expected);
    testBytes("b'''" + actual + "'''", expected);
  }

  @Test
  public void parseRawString_escapeSequence() throws Exception {
    // In raw strings, escape sequences are returned as is.
    testRawQuotedString(
        "\\a\\b\\f\\n\\r\\t\\v\\`\\'\\\"\\?\\\\", "\\a\\b\\f\\n\\r\\t\\v\\`\\'\\\"\\?\\\\");
  }

  private static void testRawString(String actual, String expected) throws Exception {
    CelConstant constant = Constants.parseString(actual);
    assertThat(constant.getKind()).isEqualTo(Kind.STRING_VALUE);
    assertThat(constant.stringValue()).isEqualTo(expected);
  }

  private static void testRawQuotedString(String actual, String expected) throws Exception {
    testRawString("r\"" + actual + "\"", expected);
    testRawString("R\"" + actual + "\"", expected);
    testRawString("r'" + actual + "'", expected);
    testRawString("R'" + actual + "'", expected);
    testRawString("r\"\"\"" + actual + "\"\"\"", expected);
    testRawString("R\"\"\"" + actual + "\"\"\"", expected);
    testRawString("r'''" + actual + "'''", expected);
    testRawString("R'''" + actual + "'''", expected);
  }

  @Test
  public void parseRawBytes_escapeSequence() throws Exception {
    // In raw strings, escape sequences are returned as is.
    testRawQuotedBytes(
        "\\a\\b\\f\\n\\r\\t\\v\\`\\'\\\"\\?\\\\", "\\a\\b\\f\\n\\r\\t\\v\\`\\'\\\"\\?\\\\");
  }

  private static void testRawBytes(String actual, String expected) throws Exception {
    CelConstant constant = Constants.parseBytes(actual);
    assertThat(constant.getKind()).isEqualTo(Kind.BYTES_VALUE);
    assertThat(constant.bytesValue()).isEqualTo(CelByteString.copyFromUtf8(expected));
  }

  private static void testRawQuotedBytes(String actual, String expected) throws Exception {
    testRawBytes("br\"" + actual + "\"", expected);
    testRawBytes("rb\"" + actual + "\"", expected);
    testRawBytes("bR\"" + actual + "\"", expected);
    testRawBytes("Rb\"" + actual + "\"", expected);
    testRawBytes("Br\"" + actual + "\"", expected);
    testRawBytes("rB\"" + actual + "\"", expected);
    testRawBytes("BR\"" + actual + "\"", expected);
    testRawBytes("RB\"" + actual + "\"", expected);
    testRawBytes("br'" + actual + "'", expected);
    testRawBytes("rb'" + actual + "'", expected);
    testRawBytes("bR'" + actual + "'", expected);
    testRawBytes("Rb'" + actual + "'", expected);
    testRawBytes("Br'" + actual + "'", expected);
    testRawBytes("rB'" + actual + "'", expected);
    testRawBytes("BR'" + actual + "'", expected);
    testRawBytes("RB'" + actual + "'", expected);
    testRawBytes("br\"\"\"" + actual + "\"\"\"", expected);
    testRawBytes("rb\"\"\"" + actual + "\"\"\"", expected);
    testRawBytes("bR\"\"\"" + actual + "\"\"\"", expected);
    testRawBytes("Rb\"\"\"" + actual + "\"\"\"", expected);
    testRawBytes("Br\"\"\"" + actual + "\"\"\"", expected);
    testRawBytes("rB\"\"\"" + actual + "\"\"\"", expected);
    testRawBytes("BR\"\"\"" + actual + "\"\"\"", expected);
    testRawBytes("RB\"\"\"" + actual + "\"\"\"", expected);
    testRawBytes("br'''" + actual + "'''", expected);
    testRawBytes("rb'''" + actual + "'''", expected);
    testRawBytes("bR'''" + actual + "'''", expected);
    testRawBytes("Rb'''" + actual + "'''", expected);
    testRawBytes("Br'''" + actual + "'''", expected);
    testRawBytes("rB'''" + actual + "'''", expected);
    testRawBytes("BR'''" + actual + "'''", expected);
    testRawBytes("RB'''" + actual + "'''", expected);
  }
}
