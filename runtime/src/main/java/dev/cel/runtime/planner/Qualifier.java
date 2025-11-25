// Copyright 2025 Google LLC
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

package dev.cel.runtime.planner;

import com.google.errorprone.annotations.Immutable;
import dev.cel.common.values.SelectableValue;
import java.util.Map;

@Immutable
interface Qualifier {
  Object value();

  Object qualify(Object value);

  final class PresenceTestQualifier implements Qualifier {

    @SuppressWarnings("Immutable")
    private final Object value;

    @Override
    public Object value() {
      return value;
    }

    @Override
    public Boolean qualify(Object obj) {
      if (obj instanceof SelectableValue) {
        return ((SelectableValue<Object>) obj).find(value).isPresent();
      } else if (obj instanceof Map) {
        Map<?, ?> map = (Map<?, ?>) obj;
        return map.containsKey(value);
      }

      return false;
    }

    PresenceTestQualifier(Object value) {
      this.value = value;
    }
  }

  final class StringQualifier implements Qualifier {

    private final String value;

    @Override
    public String value() {
      return value;
    }

    @Override
    public Object qualify(Object obj) {
      if (obj instanceof SelectableValue) {
        return ((SelectableValue<String>) obj).select(value);
      } else if (obj instanceof Map) {
        Map<String, Object> map = (Map<String, Object>) obj;
        if (!map.containsKey(value)) {
          throw new IllegalArgumentException(String.format("No such key: %s", value));
        }

        Object mapVal = map.get(value);

        if (mapVal == null) {
          throw new NullPointerException(
              String.format("Map value cannot be null for key: %s", value));
        }
        return map.get(value);
      }

      throw new UnsupportedOperationException(
          String.format("Unable to qualify: %s for %s", obj, value));
    }

    StringQualifier(String value) {
      this.value = value;
    }
  }
}
