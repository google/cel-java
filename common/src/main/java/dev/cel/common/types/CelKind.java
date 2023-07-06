// Copyright 2022 Google LLC
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

package dev.cel.common.types;

import com.google.errorprone.annotations.CheckReturnValue;

/**
 * The {@code CelKind} indicates the type category.
 *
 * <p>In most cases, the type category maps to a well-defined CEL type, but there are a handful of
 * types which are either internal or used to extend the type set natively understood by CEL.
 */
@CheckReturnValue
public enum CelKind {
  UNSPECIFIED,
  ERROR,
  DYN,
  ANY,
  BOOL,
  BYTES,
  DOUBLE,
  DURATION,
  FUNCTION,
  INT,
  LIST,
  MAP,
  NULL_TYPE,
  OPAQUE,
  STRING,
  STRUCT,
  TIMESTAMP,
  TYPE,
  TYPE_PARAM,
  UINT;

  public boolean isDyn() {
    return this == DYN || this == ANY;
  }

  public boolean isTypeParam() {
    return this == TYPE_PARAM;
  }

  public boolean isError() {
    return this == ERROR;
  }

  public boolean isPrimitive() {
    return this == BOOL
        || this == INT
        || this == UINT
        || this == DOUBLE
        || this == STRING
        || this == BYTES;
  }
}
