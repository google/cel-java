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

package dev.cel.common;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;
import static org.antlr.v4.runtime.IntStream.UNKNOWN_SOURCE_NAME;
import static org.junit.Assert.assertThrows;

import dev.cel.common.internal.BasicCodePointArray;
import dev.cel.common.internal.CodePointStream;
import dev.cel.common.internal.Latin1CodePointArray;
import dev.cel.common.internal.SupplementalCodePointArray;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.IntStream;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class CelSourceTest {

  private static final String LATIN_1_EXPR = "a + b == c";
  private static final String LATIN_1_NL_EXPR = "a + b\n== c";
  private static final String BASIC_EXPR = "a + \uff20 == c";
  private static final String BASIC_NL_EXPR = "a + \uff20\n == c";
  private static final String SUPPLEMENTAL_EXPR =
      "a + \uff20 == " + new String(Character.toChars(0x1f4a2)) + " ";
  private static final String SUPPLEMENTAL_NL_EXPR =
      "a + \uff20\n == " + new String(Character.toChars(0x1f4a2)) + " ";

  @Test
  public void getLocationOffset_correctStartingLocation() throws Exception {
    CelSource source = CelSource.fromString(LATIN_1_EXPR);
    assertThat(source.getLocationOffset(CelSourceLocation.of(1, 0))).hasValue(0);
  }

  @Test
  public void getOffsetLocation_correctStartingLocation() throws Exception {
    CelSource source = CelSource.fromString(LATIN_1_EXPR);
    assertThat(source.getOffsetLocation(0)).hasValue(CelSourceLocation.of(1, 0));
  }

  @Test
  public void fromString_handlesSingleLineLatin1() throws Exception {
    CelSource source = CelSource.fromString(LATIN_1_EXPR, UNKNOWN_SOURCE_NAME);
    assertThat(source.getContent().toString()).isEqualTo(LATIN_1_EXPR);
    assertThat(source.getContent()).isInstanceOf(Latin1CodePointArray.class);
    CharStream charStream = new CodePointStream(source.getDescription(), source.getContent());
    assertThat(charStream.getSourceName()).isEqualTo(UNKNOWN_SOURCE_NAME);
    assertThat(charStream.size()).isEqualTo(LATIN_1_EXPR.codePoints().count());
    charStream.seek(charStream.size());
    assertThrows(IllegalStateException.class, charStream::consume);
    charStream.seek(0);
    assertThat(charStream.LA(0)).isEqualTo(0);
    assertThat(charStream.LA(-1)).isEqualTo(IntStream.EOF);
    assertThat(source.getLineOffsets()).hasSize(1);
    assertThat(source.getLineOffsets().get(0)).isEqualTo(LATIN_1_EXPR.codePoints().count() + 1);
  }

  @Test
  public void fromString_handlesMultiLineLatin1() throws Exception {
    CelSource source = CelSource.fromString(LATIN_1_NL_EXPR, UNKNOWN_SOURCE_NAME);
    assertThat(source.getContent().toString()).isEqualTo(LATIN_1_NL_EXPR);
    assertThat(source.getContent()).isInstanceOf(Latin1CodePointArray.class);
    CharStream charStream = new CodePointStream(source.getDescription(), source.getContent());
    assertThat(charStream.getSourceName()).isEqualTo(UNKNOWN_SOURCE_NAME);
    assertThat(charStream.size()).isEqualTo(LATIN_1_NL_EXPR.codePoints().count());
    charStream.seek(charStream.size());
    assertThrows(IllegalStateException.class, charStream::consume);
    charStream.seek(0);
    assertThat(charStream.LA(0)).isEqualTo(0);
    assertThat(charStream.LA(-1)).isEqualTo(IntStream.EOF);
    assertThat(source.getLineOffsets()).containsExactly(6, 11).inOrder();
  }

  @Test
  public void fromString_handlesSingleLineBasic() throws Exception {
    CelSource source = CelSource.fromString(BASIC_EXPR, UNKNOWN_SOURCE_NAME);
    assertThat(source.getContent().toString()).isEqualTo(BASIC_EXPR);
    assertThat(source.getContent()).isInstanceOf(BasicCodePointArray.class);
    CharStream charStream = new CodePointStream(source.getDescription(), source.getContent());
    assertThat(charStream.getSourceName()).isEqualTo(UNKNOWN_SOURCE_NAME);
    assertThat(charStream.size()).isEqualTo(BASIC_EXPR.codePoints().count());
    charStream.seek(charStream.size());
    assertThrows(IllegalStateException.class, charStream::consume);
    charStream.seek(0);
    assertThat(charStream.LA(0)).isEqualTo(0);
    assertThat(charStream.LA(-1)).isEqualTo(IntStream.EOF);
    assertThat(source.getLineOffsets()).hasSize(1);
    assertThat(source.getLineOffsets().get(0)).isEqualTo(BASIC_EXPR.codePoints().count() + 1);
  }

  @Test
  public void fromString_handlesMultiLineBasic() throws Exception {
    CelSource source = CelSource.fromString(BASIC_NL_EXPR, UNKNOWN_SOURCE_NAME);
    assertThat(source.getContent().toString()).isEqualTo(BASIC_NL_EXPR);
    assertThat(source.getContent()).isInstanceOf(BasicCodePointArray.class);
    CharStream charStream = new CodePointStream(source.getDescription(), source.getContent());
    assertThat(charStream.getSourceName()).isEqualTo(UNKNOWN_SOURCE_NAME);
    assertThat(charStream.size()).isEqualTo(BASIC_NL_EXPR.codePoints().count());
    charStream.seek(charStream.size());
    assertThrows(IllegalStateException.class, charStream::consume);
    charStream.seek(0);
    assertThat(charStream.LA(0)).isEqualTo(0);
    assertThat(charStream.LA(-1)).isEqualTo(IntStream.EOF);
    assertThat(source.getLineOffsets()).containsExactly(6, 12).inOrder();
  }

  @Test
  public void fromString_handlesSingleLineSupplemental() throws Exception {
    CelSource source = CelSource.fromString(SUPPLEMENTAL_EXPR, UNKNOWN_SOURCE_NAME);
    assertThat(source.getContent().toString()).isEqualTo(SUPPLEMENTAL_EXPR);
    assertThat(source.getContent()).isInstanceOf(SupplementalCodePointArray.class);
    CharStream charStream = new CodePointStream(source.getDescription(), source.getContent());
    assertThat(charStream.getSourceName()).isEqualTo(UNKNOWN_SOURCE_NAME);
    assertThat(charStream.size()).isEqualTo(SUPPLEMENTAL_EXPR.codePoints().count());
    charStream.seek(charStream.size());
    assertThrows(IllegalStateException.class, charStream::consume);
    charStream.seek(0);
    assertThat(charStream.LA(0)).isEqualTo(0);
    assertThat(charStream.LA(-1)).isEqualTo(IntStream.EOF);
    assertThat(source.getLineOffsets()).hasSize(1);
    assertThat(source.getLineOffsets().get(0))
        .isEqualTo(SUPPLEMENTAL_EXPR.codePoints().count() + 1);
  }

  @Test
  public void fromString_handlesMultiLineSupplemental() throws Exception {
    CelSource source = CelSource.fromString(SUPPLEMENTAL_NL_EXPR, UNKNOWN_SOURCE_NAME);
    assertThat(source.getContent().toString()).isEqualTo(SUPPLEMENTAL_NL_EXPR);
    assertThat(source.getContent()).isInstanceOf(SupplementalCodePointArray.class);
    CharStream charStream = new CodePointStream(source.getDescription(), source.getContent());
    assertThat(charStream.getSourceName()).isEqualTo(UNKNOWN_SOURCE_NAME);
    assertThat(charStream.size()).isEqualTo(SUPPLEMENTAL_NL_EXPR.codePoints().count());
    charStream.seek(charStream.size());
    assertThrows(IllegalStateException.class, charStream::consume);
    charStream.seek(0);
    assertThat(charStream.LA(0)).isEqualTo(0);
    assertThat(charStream.LA(-1)).isEqualTo(IntStream.EOF);
    assertThat(source.getLineOffsets()).containsExactly(6, 13).inOrder();
  }
}
