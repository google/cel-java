package dev.cel.policy;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.errorprone.annotations.CheckReturnValue;
import dev.cel.common.Source;
import dev.cel.common.internal.CelCodePointArray;
import java.util.List;
import java.util.Optional;

@AutoValue
public abstract class CelPolicySource implements Source {

  @Override
  public abstract CelCodePointArray content();

  @Override
  public abstract String description();

  @Override
  public Optional<String> getSnippet(int line) {
    // TODO: Generalize
    checkArgument(line > 0);
    ImmutableList<Integer> lineOffsets = content().lineOffsets();
    int start = findLineOffset(lineOffsets, line);
    if (start == -1) {
      return Optional.empty();
    }
    int end = findLineOffset(lineOffsets, line + 1);
    if (end == -1) {
      end = content().size();
    } else {
      end--;
    }
    return Optional.of(end != start ? content().slice(start, end).toString() : "");
  }

  private static int findLineOffset(List<Integer> lineOffsets, int line) {
    if (line == 1) {
      return 0;
    }
    if (line > 1 && line <= lineOffsets.size()) {
      return lineOffsets.get(line - 2);
    }
    return -1;
  }

  @AutoValue.Builder
  public abstract static class Builder {

    public abstract Builder setContent(CelCodePointArray content);

    public abstract Builder setDescription(String description);

    @CheckReturnValue
    public abstract CelPolicySource build();
  }

  public abstract Builder toBuilder();

  public static Builder newBuilder(String text) {
    return new AutoValue_CelPolicySource.Builder()
        .setContent(CelCodePointArray.fromString(text))
        .setDescription("<input>");
  }
}
