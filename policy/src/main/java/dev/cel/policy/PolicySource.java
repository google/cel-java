package dev.cel.policy;

import com.google.auto.value.AutoValue;

@AutoValue
public abstract class PolicySource {

  public abstract String content();

  public abstract String location();

  public static PolicySource create(String content, String location) {
    return new AutoValue_PolicySource(content, location);
  }

}
