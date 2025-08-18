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

import com.google.errorprone.annotations.Immutable;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Comparator;

/** CelByteString is an immutable sequence of a byte array. */
@Immutable
@SuppressWarnings("Immutable") // We make defensive copies on the byte array.
public final class CelByteString {
  private static final byte[] EMPTY_BYTE_ARRAY = new byte[0];
  public static final CelByteString EMPTY = new CelByteString(EMPTY_BYTE_ARRAY);

  private final byte[] data;

  private volatile int hash = 0;

  public static CelByteString of(byte[] buffer) {
    if (buffer == null) {
      throw new NullPointerException("buffer cannot be null");
    }
    if (buffer.length == 0) {
      return EMPTY;
    }
    return new CelByteString(buffer);
  }

  public static CelByteString copyFromUtf8(String utf8String) {
    return new CelByteString(utf8String.getBytes(StandardCharsets.UTF_8));
  }

  public static Comparator<CelByteString> unsignedLexicographicalComparator() {
    return UNSIGNED_LEXICOGRAPHICAL_COMPARATOR;
  }

  public String toStringUtf8() {
    return new String(data, StandardCharsets.UTF_8);
  }

  /** Checks if the byte array is a valid utf-8 encoded text. */
  public boolean isValidUtf8() {
    CharsetDecoder charsetDecoder = StandardCharsets.UTF_8.newDecoder();
    charsetDecoder.onMalformedInput(CodingErrorAction.REPORT);
    charsetDecoder.onUnmappableCharacter(CodingErrorAction.REPORT);

    try {
      charsetDecoder.decode(ByteBuffer.wrap(data));
    } catch (CharacterCodingException unused) {
      return false;
    }
    return true;
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

  public CelByteString concat(CelByteString other) {
    byte[] result = new byte[data.length + other.data.length];
    System.arraycopy(data, 0, result, 0, data.length);
    System.arraycopy(other.data, 0, result, data.length, other.data.length);

    return CelByteString.of(result);
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

  @Override
  public String toString() {
    return toStringUtf8();
  }

  private static final Comparator<CelByteString> UNSIGNED_LEXICOGRAPHICAL_COMPARATOR =
      (former, latter) -> {
        // Once we're on Java 9+, we can replace this whole thing with Arrays.compareUnsigned
        byte[] formerBytes = former.toByteArray();
        byte[] latterBytes = latter.toByteArray();
        int minLength = Math.min(formerBytes.length, latterBytes.length);

        for (int i = 0; i < minLength; i++) {
          int formerUnsigned = Byte.toUnsignedInt(formerBytes[i]);
          int latterUnsigned = Byte.toUnsignedInt(latterBytes[i]);
          int result = Integer.compare(formerUnsigned, latterUnsigned);

          if (result != 0) {
            return result;
          }
        }

        return Integer.compare(formerBytes.length, latterBytes.length);
      };
}
