package dev.cel.policy;
import com.google.auto.value.AutoValue;

@AutoValue
public abstract class ValueString {

  abstract long id();

  abstract String value();


  @AutoValue.Builder
  abstract static class Builder {

    abstract Builder setId(long id);

    abstract Builder setValue(String value);

    abstract ValueString build();
  }

  static Builder newBuilder() {
    return new AutoValue_ValueString.Builder().setId(0).setValue("");
  }

  static ValueString of(long id, String value) {
    return newBuilder().setId(id).setValue(value).build();
  }
}
