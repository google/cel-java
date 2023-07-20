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

package dev.cel.checker;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import dev.cel.common.CelIssue;
import dev.cel.common.CelSource;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class CelIssueTest {

  private static final Joiner JOINER = Joiner.on('\n');

  @Test
  public void toDisplayString_narrow() throws Exception {
    CelSource source =
        CelSource.newBuilder("a.b\n&&arg(missing, paren").setDescription("issues-test").build();
    ImmutableList<CelIssue> issues =
        ImmutableList.of(
            CelIssue.formatError(1, 1, "No such field"),
            CelIssue.formatError(2, 20, "Syntax error, missing paren"));
    assertThat(JOINER.join(Iterables.transform(issues, error -> error.toDisplayString(source))))
        .isEqualTo(
            "ERROR: issues-test:1:2: No such field\n"
                + " | a.b\n"
                + " | .^\n"
                + "ERROR: issues-test:2:21: Syntax error, missing paren\n"
                + " | &&arg(missing, paren\n"
                + " | ....................^");
  }

  @Test
  public void toDisplayString_wideAndNarrow() throws Exception {
    CelSource source = CelSource.newBuilder("你好吗\n我b很好\n").setDescription("issues-test").build();
    ImmutableList<CelIssue> issues =
        ImmutableList.of(CelIssue.formatError(2, 3, "Unexpected character '好'"));
    assertThat(JOINER.join(Iterables.transform(issues, error -> error.toDisplayString(source))))
        .isEqualTo("ERROR: issues-test:2:4: Unexpected character '好'\n" + " | 我b很好\n" + " | ．.．＾");
  }

  @Test
  public void toDisplayString_emojis() throws Exception {
    CelSource source =
        CelSource.newBuilder("      '😁' in ['😁', '😑', '😦'] && in.😁")
            .setDescription("issues-test")
            .build();
    ImmutableList<CelIssue> issues =
        ImmutableList.of(
            CelIssue.formatError(
                1,
                32,
                "Syntax error: extraneous input 'in' expecting {'[', '{', '(', '.', '-', '!',"
                    + " 'true', 'false', 'null', NUM_FLOAT, NUM_INT, NUM_UINT, STRING, BYTES,"
                    + " IDENTIFIER}"),
            CelIssue.formatError(1, 35, "Syntax error: token recognition error at: '😁'"),
            CelIssue.formatError(1, 36, "Syntax error: missing IDENTIFIER at '<EOF>'"));
    assertThat(JOINER.join(Iterables.transform(issues, error -> error.toDisplayString(source))))
        .isEqualTo(
            "ERROR: issues-test:1:33: Syntax error: extraneous input 'in' expecting {'[', '{',"
                + " '(', '.', '-', '!', 'true', 'false', 'null', NUM_FLOAT, NUM_INT, NUM_UINT,"
                + " STRING, BYTES, IDENTIFIER}\n"
                + " |       '😁' in ['😁', '😑', '😦'] && in.😁\n"
                + " | .......．.......．....．....．......^\n"
                + "ERROR: issues-test:1:36: Syntax error: token recognition error at: '😁'\n"
                + " |       '😁' in ['😁', '😑', '😦'] && in.😁\n"
                + " | .......．.......．....．....．.........＾\n"
                + "ERROR: issues-test:1:37: Syntax error: missing IDENTIFIER at '<EOF>'\n"
                + " |       '😁' in ['😁', '😑', '😦'] && in.😁\n"
                + " | .......．.......．....．....．.........．^");
  }
}
