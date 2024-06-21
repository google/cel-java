package dev.cel.common;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.collect.ImmutableList;
import dev.cel.common.internal.CelCodePointArray;
import java.util.List;
import java.util.Optional;
public final class CelSourceHelper {

  /**
   * TODO
   * @param content
   * @param line
   * @return
   */
  public static Optional<String> getSnippet(CelCodePointArray content, int line) {
    checkArgument(line > 0);
    ImmutableList<Integer> lineOffsets = content.lineOffsets();
    int start = findLineOffset(lineOffsets, line);
    if (start == -1) {
      return Optional.empty();
    }
    int end = findLineOffset(lineOffsets, line + 1);
    if (end == -1) {
      end = content.size();
    } else {
      end--;
    }
    return Optional.of(end != start ? content.slice(start, end).toString() : "");
  }

  static int findLineOffset(List<Integer> lineOffsets, int line) {
    if (line == 1) {
      return 0;
    }
    if (line > 1 && line <= lineOffsets.size()) {
      return lineOffsets.get(line - 2);
    }
    return -1;
  }

  private CelSourceHelper() {}
}
