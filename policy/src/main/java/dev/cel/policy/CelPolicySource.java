package dev.cel.policy;

import com.google.auto.value.AutoValue;
import com.google.errorprone.annotations.CheckReturnValue;
import dev.cel.common.CelSourceHelper;
import dev.cel.common.Source;
import dev.cel.common.internal.CelCodePointArray;
import java.util.Optional;

@AutoValue
public abstract class CelPolicySource implements Source {

  @Override
  public abstract CelCodePointArray content();

  @Override
  public abstract String description();

  @Override
  public Optional<String> getSnippet(int line) {
    return CelSourceHelper.getSnippet(content(), line);
  }

  @AutoValue.Builder
  public abstract static class Builder {

    public abstract Builder setContent(CelCodePointArray content);

    public abstract Builder setDescription(String description);

    @CheckReturnValue
    public abstract CelPolicySource build();
  }

  public abstract Builder toBuilder();

  public static CelPolicySource fromText(String text) {
    return CelPolicySource.newBuilder(text).build();
  }

  public static Builder newBuilder(String text) {
    return new AutoValue_CelPolicySource.Builder()
        .setContent(CelCodePointArray.fromString(text))
        .setDescription("<input>");
  }
}
