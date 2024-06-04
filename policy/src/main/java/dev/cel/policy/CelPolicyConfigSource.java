package dev.cel.policy;

import com.google.auto.value.AutoValue;

@AutoValue
public abstract class CelPolicyConfigSource {
    public abstract String content();

    public abstract String location();

    public static CelPolicyConfigSource create(String content, String location) {
        return new AutoValue_CelPolicyConfigSource(content, location);
    }
}
