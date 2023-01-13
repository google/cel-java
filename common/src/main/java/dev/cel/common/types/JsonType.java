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
import com.google.errorprone.annotations.Immutable;

/** The JSON type is a union of all of the types which can be represented as JSON values. */
@CheckReturnValue
@Immutable
public final class JsonType extends CelType {

  public static final CelType JSON = new JsonType();

  private JsonType() {}

  @Override
  public CelKind kind() {
    return CelKind.OPAQUE;
  }

  @Override
  public String name() {
    return "json";
  }

  @Override
  public boolean isAssignableFrom(CelType other) {
    if (this.equals(other)) {
      return true;
    }
    switch (other.kind()) {
      case BOOL:
      case DOUBLE:
      case NULL_TYPE:
      case STRING:
        return true;
      case LIST:
        ListType list = (ListType) other;
        return this.isAssignableFrom(list.elemType());
      case MAP:
        MapType map = (MapType) other;
        return map.keyType().kind() == CelKind.STRING && this.isAssignableFrom(map.valueType());
      default:
        return false;
    }
  }
}
