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

import static com.google.common.truth.Truth.assertThat;
import static dev.cel.testing.LineDiffer.diffLines;

import com.google.common.collect.ImmutableList;
import dev.cel.testing.LineDiffer.Diff;
import dev.cel.testing.LineDiffer.Snippet;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class LineDifferTest {

  @Test
  public void emptyStringsDiff_emptyResult() {
    assertThat(diffLines("", "").isEmpty()).isTrue();
  }

  @Test
  public void nonEmptyAndEmptyStringsDiff_removalDiff() {
    Diff actual = diffLines("hello\nworld", "");
    assertThat(actual.isEmpty()).isFalse();
    assertThat(actual)
        .isEqualTo(
            Diff.of(
                ImmutableList.of(),
                ImmutableList.of(Snippet.of(1, 2, ImmutableList.of("hello", "world")))));

    String expectedText = "===== Lines 1-2 =====\n" + "< hello\n" + "< world\n";
    assertThat(actual.toString()).isEqualTo(expectedText);
  }

  @Test
  public void emptyAndNonEmptyStringsDiff_additionDiff() {
    Diff actual = diffLines("", "hello\nworld");
    assertThat(actual.isEmpty()).isFalse();
    assertThat(actual)
        .isEqualTo(
            Diff.of(
                ImmutableList.of(Snippet.of(1, 2, ImmutableList.of("hello", "world"))),
                ImmutableList.of()));

    String expectedText = "===== Lines 1-2 =====\n" + "> hello\n" + "> world\n";
    assertThat(actual.toString()).isEqualTo(expectedText);
  }

  @Test
  public void addRemoveMiddleDiffEqualSize_complexDiff() {
    assertThat(diffLines("hello\ndear\nworld", "hello\ngrand\nworld"))
        .isEqualTo(
            Diff.of(
                ImmutableList.of(Snippet.of(2, "grand")), ImmutableList.of(Snippet.of(2, "dear"))));
  }

  @Test
  public void addRemoveMiddleDiffUnequalSize_complexDiff() {
    assertThat(diffLines("hello\ndear\ngrand\nnow\ncow", "hello\ngrand\nworld"))
        .isEqualTo(
            Diff.of(
                ImmutableList.of(Snippet.of(3, "world")),
                ImmutableList.of(
                    Snippet.of(2, "dear"), Snippet.of(4, "now").join(Snippet.of(5, "cow")))));
  }

  @Test
  public void addTrailingText_additionDiff() {
    assertThat(diffLines("hello\ndear\ngrand", "hello\ndear\ngrand\nnow\ncow"))
        .isEqualTo(
            Diff.of(
                ImmutableList.of(Snippet.of(4, "now").join(Snippet.of(5, "cow"))),
                ImmutableList.of()));
  }

  @Test
  public void removeTrailingText_removalDiff() {
    assertThat(diffLines("hello\ndear\ngrand\nnow\ncow", "hello\ndear\ngrand"))
        .isEqualTo(
            Diff.of(
                ImmutableList.of(),
                ImmutableList.of(Snippet.of(4, "now").join(Snippet.of(5, "cow")))));
  }

  @Test
  public void leadingText_additionDiff() {
    assertThat(diffLines("dear", "hello\ndear"))
        .isEqualTo(Diff.of(ImmutableList.of(Snippet.of(1, "hello")), ImmutableList.of()));
  }

  @Test
  public void leadingAndTrailingText_complexDiff() {
    Diff actual = diffLines("hello\ndear", "dear\nword");
    assertThat(actual)
        .isEqualTo(
            Diff.of(
                ImmutableList.of(Snippet.of(2, "word")), ImmutableList.of(Snippet.of(1, "hello"))));

    String expectedText =
        "===== Line 1 =====\n" + "< hello\n" + "===== Line 2 =====\n" + "> word\n";
    assertThat(actual.toString()).isEqualTo(expectedText);
  }

  @Test
  public void leadingAndTrailingText_complexDiff_reverse() {
    Diff actual = diffLines("dear\nword", "hello\ndear");
    assertThat(actual)
        .isEqualTo(
            Diff.of(
                ImmutableList.of(Snippet.of(1, "hello")), ImmutableList.of(Snippet.of(2, "word"))));

    String expectedText =
        "===== Line 1 =====\n" + "> hello\n" + "===== Line 2 =====\n" + "< word\n";
    assertThat(actual.toString()).isEqualTo(expectedText);
  }

  @Test
  public void multipartDiff() {
    String first = "Here\nis\na\nman\nand\nhis\ndog\nat\nhome\ntonight";
    String second = "There\nis\na\ncat\nand\nit's\nstaying\nat\na neighbor's";
    assertThat(diffLines(first, second))
        .isEqualTo(
            Diff.of(
                ImmutableList.of(
                    Snippet.of(1, "There"),
                    Snippet.of(4, "cat"),
                    Snippet.of(6, 7, ImmutableList.of("it's", "staying")),
                    Snippet.of(9, "a neighbor's")),
                ImmutableList.of(
                    Snippet.of(1, "Here"),
                    Snippet.of(4, "man"),
                    Snippet.of(6, 7, ImmutableList.of("his", "dog")),
                    Snippet.of(9, 10, ImmutableList.of("home", "tonight")))));
  }

  @Test
  public void multipartDiff_reverse() {
    String first = "Here\nis\na\nman\nand\nhis\ndog\nat\nhome\ntonight";
    String second = "There\nis\na\ncat\nand\nit's\nstaying\nat\na neighbor's\nold\nhome\ntonight";
    Diff actual = diffLines(second, first);
    assertThat(actual)
        .isEqualTo(
            Diff.of(
                ImmutableList.of(
                    Snippet.of(1, "Here"),
                    Snippet.of(4, "man"),
                    Snippet.of(6, 7, ImmutableList.of("his", "dog"))),
                ImmutableList.of(
                    Snippet.of(1, "There"),
                    Snippet.of(4, "cat"),
                    Snippet.of(6, 7, ImmutableList.of("it's", "staying")),
                    Snippet.of(9, 10, ImmutableList.of("a neighbor's", "old")))));

    assertThat(actual.toString())
        .isEqualTo(
            "===== Line 1 =====\n"
                + "> Here\n"
                + "< There\n"
                + "===== Line 4 =====\n"
                + "> man\n"
                + "< cat\n"
                + "===== Lines 6-7 =====\n"
                + "> his\n"
                + "> dog\n"
                + "< it's\n"
                + "< staying\n"
                + "===== Lines 9-10 =====\n"
                + "< a neighbor's\n"
                + "< old\n");
  }
}
