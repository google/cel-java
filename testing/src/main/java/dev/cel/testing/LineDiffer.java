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

import static java.lang.Math.max;

import com.google.auto.value.AutoValue;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.errorprone.annotations.CheckReturnValue;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/** Utility for generating a set of differences between two text files. */
@CheckReturnValue
public final class LineDiffer {

  static final Diff EMPTY_DIFF = Diff.of(ImmutableList.of(), ImmutableList.of());
  private static final Splitter LINE_SPLITTER = Splitter.on('\n');

  public static Diff diffLines(String firstText, String secondText) {
    if (firstText.equals(secondText)) {
      return EMPTY_DIFF;
    }

    List<String> firstLines = LINE_SPLITTER.splitToList(firstText);
    List<String> secondLines = LINE_SPLITTER.splitToList(secondText);
    int firstLinesCount = firstLines.size();
    int secondLinesCount = secondLines.size();
    if (firstText.isEmpty()) {
      return Diff.of(
          ImmutableList.of(Snippet.of(1, secondLinesCount, secondLines)), ImmutableList.of());
    }
    if (secondText.isEmpty()) {
      return Diff.of(
          ImmutableList.of(), ImmutableList.of(Snippet.of(1, firstLinesCount, firstLines)));
    }

    // Compute a matrix of equivalent lines (ignoring spaces) into a matrix representing the longest
    // common subsequences present in the input text. This method is O(n^2) and more efficient
    // techniques exist, but the texts being compared are quite small so the performance should
    // suffice.
    int[][] longestCommonSubsequence = new int[firstLinesCount + 1][secondLinesCount + 1];
    for (int firstLine = 1; firstLine < firstLinesCount + 1; firstLine++) {
      for (int secondLine = 1; secondLine < secondLinesCount + 1; secondLine++) {
        if (equalsHashIgnoreSpaces(
            firstLines.get(firstLine - 1), secondLines.get(secondLine - 1))) {
          // Increments the common subsequence length.
          longestCommonSubsequence[firstLine][secondLine] =
              1 + longestCommonSubsequence[firstLine - 1][secondLine - 1];
        } else {
          // Records the longest common subsequence from either the first or second text.
          longestCommonSubsequence[firstLine][secondLine] =
              max(
                  longestCommonSubsequence[firstLine - 1][secondLine],
                  longestCommonSubsequence[firstLine][secondLine - 1]);
        }
      }
    }

    int firstLine = firstLinesCount;
    int secondLine = secondLinesCount;
    ArrayList<Snippet> removals = new ArrayList<>();
    ArrayList<Snippet> additions = new ArrayList<>();
    while (firstLine != 0 || secondLine != 0) {
      // This is an addition case where the second text contains more lines than the first.
      if (firstLine == 0) {
        if (!secondLines.isEmpty()) {
          Snippet addition = Snippet.of(secondLine, secondLines.get(secondLine - 1));
          appendOrJoin(additions, addition);
        }
        secondLine--;
        continue;
      }
      // This is a removal case where the second text contains fewer lines than the first.
      if (secondLine == 0) {
        if (!firstLines.isEmpty()) {
          Snippet removal = Snippet.of(firstLine, firstLines.get(firstLine - 1));
          appendOrJoin(removals, removal);
        }
        firstLine--;
        continue;
      }
      // The lines of text are equal and thus can be omitted from the results.
      if (equalsIgnoreSpaces(firstLines.get(firstLine - 1), secondLines.get(secondLine - 1))) {
        firstLine--;
        secondLine--;
        continue;
      }

      // Follow the direction of the longest common subsequence.
      int cmp =
          Integer.compare(
              longestCommonSubsequence[firstLine - 1][secondLine],
              longestCommonSubsequence[firstLine][secondLine - 1]);
      if (cmp <= 0) {
        Snippet addition = Snippet.of(secondLine, secondLines.get(secondLine - 1));
        appendOrJoin(additions, addition);
        secondLine--;
      } else {
        Snippet removal = Snippet.of(firstLine, firstLines.get(firstLine - 1));
        appendOrJoin(removals, removal);
        firstLine--;
      }
    }
    return Diff.of(
        ImmutableList.copyOf(additions).reverse(), ImmutableList.copyOf(removals).reverse());
  }

  private static void appendOrJoin(ArrayList<Snippet> snippets, Snippet snippet) {
    if (snippets.isEmpty()) {
      snippets.add(snippet);
      return;
    }

    Snippet prev = Iterables.getLast(snippets);
    if (snippet.isContiguous(prev)) {
      snippets.set(snippets.size() - 1, snippet.join(prev));
    } else {
      snippets.add(snippet);
    }
  }

  private static boolean equalsHashIgnoreSpaces(String first, String second) {
    return first.trim().hashCode() == second.trim().hashCode();
  }

  private static boolean equalsIgnoreSpaces(String first, String second) {
    return first.trim().equals(second.trim());
  }

  /**
   * Snippet of text which includes the starting and ending lines where the snippet occurs in to the
   * lines of text.
   */
  @AutoValue
  public abstract static class Snippet {
    public abstract int startLine();

    public abstract int endLine();

    public abstract ImmutableList<String> textLines();

    boolean isContiguous(Snippet snippet) {
      return endLine() + 1 == snippet.startLine();
    }

    boolean intersects(Snippet snippet) {
      return (startLine() <= snippet.startLine() && snippet.startLine() <= endLine())
          || (startLine() <= snippet.endLine() && snippet.endLine() <= endLine());
    }

    Snippet join(Snippet snippet) {
      return Snippet.of(
          startLine(),
          snippet.endLine(),
          ImmutableList.<String>builder().addAll(textLines()).addAll(snippet.textLines()).build());
    }

    String render(String direction) {
      StringBuilder snippetString = new StringBuilder();
      for (String line : textLines()) {
        snippetString.append(String.format("%s %s%n", direction, line));
      }
      return snippetString.toString();
    }

    static Snippet of(int line, String text) {
      return Snippet.of(line, line, ImmutableList.of(text));
    }

    static Snippet of(int startLine, int endLine, Collection<String> textLines) {
      return Snippet.of(startLine, endLine, ImmutableList.copyOf(textLines));
    }

    static Snippet of(int startLine, int endLine, ImmutableList<String> textLines) {
      return new AutoValue_LineDiffer_Snippet(startLine, endLine, textLines);
    }
  }

  /**
   * Diff holds a collection of additions and removals representing the differing lines between two
   * strings.
   */
  @AutoValue
  public abstract static class Diff {

    public abstract ImmutableList<Snippet> additions();

    public abstract ImmutableList<Snippet> removals();

    public boolean isEmpty() {
      return additions().isEmpty() && removals().isEmpty();
    }

    static Diff of(ImmutableList<Snippet> additions, ImmutableList<Snippet> removals) {
      return new AutoValue_LineDiffer_Diff(additions, removals);
    }

    @Override
    public final String toString() {
      int removeIndex = 0;
      StringBuilder diffString = new StringBuilder();
      for (int i = 0; i < additions().size(); i++) {
        Snippet addition = additions().get(i);
        Snippet removal = maybeNextSnippet(removals(), removeIndex);
        if (removal == null) {
          diffString.append(snippetHeader(addition)).append(addition.render(">"));
          continue;
        }
        while (removal != null
            && removal.startLine() < addition.startLine()
            && !addition.intersects(removal)) {
          diffString.append(snippetHeader(removal)).append(removal.render("<"));
          removal = maybeNextSnippet(removals(), ++removeIndex);
        }
        if (removal != null && addition.intersects(removal)) {
          diffString
              .append(snippetHeader(addition))
              .append(addition.render(">"))
              .append(removal.render("<"));
          removeIndex++;
        } else {
          diffString.append(snippetHeader(addition)).append(addition.render(">"));
        }
      }
      for (int i = removeIndex; i < removals().size(); i++) {
        Snippet removal = removals().get(i);
        diffString.append(snippetHeader(removal)).append(removal.render("<"));
      }
      return diffString.toString();
    }

    private static String snippetHeader(Snippet snippet) {
      return snippet.startLine() == snippet.endLine()
          ? String.format("===== Line %d =====\n", snippet.startLine())
          : String.format("===== Lines %d-%d =====\n", snippet.startLine(), snippet.endLine());
    }

    private static Snippet maybeNextSnippet(ImmutableList<Snippet> snippets, int index) {
      return index < snippets.size() ? snippets.get(index) : null;
    }
  }

  private LineDiffer() {}
}
