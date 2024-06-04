package dev.cel.policy;

import com.google.auto.value.AutoValue;
import com.google.errorprone.annotations.CheckReturnValue;
import dev.cel.common.Source;
import java.util.Optional;

@AutoValue
public abstract class CelPolicySource implements Source {

  public abstract String content();

  @Override
  public abstract String description();

  @Override
  public Optional<String> getSnippet(int line) {
    // TODO
    return Optional.empty();
  }

  @AutoValue.Builder
  public abstract static class Builder {

    public abstract Builder setContent(String content);

    public abstract Builder setDescription(String description);

    @CheckReturnValue
    public abstract CelPolicySource build();
  }

  public abstract Builder toBuilder();

  public static Builder newBuilder() {
    return new AutoValue_CelPolicySource.Builder().setDescription("<input>");
  }

  // public static CelPolicySource create(String content, String location) {
  //   return new AutoValue_CelPolicySource(content, location);
  // }
}
