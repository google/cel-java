package dev.cel.policy;

import com.google.auto.value.AutoValue;

@AutoValue
public abstract class CelPolicySource {

  public abstract String content();

  public abstract String location();

  public static CelPolicySource create(String content, String location) {
    return new AutoValue_CelPolicySource(content, location);
  }

}
