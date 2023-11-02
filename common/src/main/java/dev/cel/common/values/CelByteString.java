// Copyright 2023 Google LLC
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

package dev.cel.common.values;

import com.google.common.base.Preconditions;
import com.google.errorprone.annotations.Immutable;
import java.util.Arrays;

/** CelByteString is an immutable sequence of a byte array. */
@Immutable
@SuppressWarnings("Immutable") // We make defensive copies on the byte array.
public final class CelByteString {
  private static final byte[] EMPTY_BYTE_ARRAY = new byte[0];
  public static final CelByteString EMPTY = new CelByteString(EMPTY_BYTE_ARRAY);

  private final byte[] data;

  private volatile int hash = 0;

  public static CelByteString of(byte[] buffer) {
    Preconditions.checkNotNull(buffer);
    if (buffer.length == 0) {
      return EMPTY;
    }
    return new CelByteString(buffer);
  }

  public int size() {
    return data.length;
  }

  public boolean isEmpty() {
    return data.length == 0;
  }

  public byte[] toByteArray() {
    int size = size();
    if (size == 0) {
      return EMPTY_BYTE_ARRAY;
    }

    return Arrays.copyOf(data, size);
  }

  private CelByteString(byte[] buffer) {
    data = Arrays.copyOf(buffer, buffer.length);
  }

  @Override
  public boolean equals(Object o) {
    if (o == this) {
      return true;
    }

    if (!(o instanceof CelByteString)) {
      return false;
    }

    return Arrays.equals(data, ((CelByteString) o).data);
  }

  /**
   * Note that we do not use Arrays.hashCode directly due to its implementation using 31 as an odd
   * prime, which is outdated and is more prone to hash collisions. This code is very similar to
   * what AutoValue generates.
   */
  @Override
  public int hashCode() {
    if (hash == 0) {
      int h = 1;
      h *= 1000003;
      h ^= Arrays.hashCode(data);
      if (h == 0) {
        h = 1;
      }
      hash = h;
    }

    return hash;
  }
}
