// Copyright 2024 Google LLC
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

package dev.cel.policy;

import com.google.auto.value.AutoValue;

/** ValueString contains an identifier corresponding to source metadata and a simple string. */
@AutoValue
public abstract class ValueString {

  /** A unique identifier. This is populated by the parser. */
  public abstract long id();

  /** String value of the {@code ValueString} */
  public abstract String value();

  /** Builder for {@link ValueString}. */
  @AutoValue.Builder
  public abstract static class Builder {

    /** Set the identifier for the string to associate it back to collected source metadata. */
    public abstract Builder setId(long id);

    /** Set the string value. */
    public abstract Builder setValue(String value);

    /** Build the {@code ValueString}. */
    public abstract ValueString build();
  }

  /** Convert the {@code ValueString} to a {@code Builder}. */
  public abstract Builder toBuilder();

  /** Builder for {@link ValueString}. */
  public static Builder newBuilder() {
    return new AutoValue_ValueString.Builder().setId(0).setValue("");
  }

  /** Creates a new {@link ValueString} instance with the specified ID and string value. */
  public static ValueString of(long id, String value) {
    return newBuilder().setId(id).setValue(value).build();
  }
}
