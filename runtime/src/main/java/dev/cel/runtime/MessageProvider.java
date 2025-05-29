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

package dev.cel.runtime;

import dev.cel.common.annotations.Internal;
import java.util.Map;

/**
 * An object which allows to create and interpret messages.
 *
 * <p>CEL Library Internals. Do Not Use.
 */
@Internal
public interface MessageProvider {

  /** Create a message based on the fully qualified message name and field-value mapping. */
  Object createMessage(String messageName, Map<String, Object> values);

  /** Select field from message. */
  Object selectField(Object message, String fieldName);

  /** Check whether a field is set on message. */
  Object hasField(Object message, String fieldName);

  /** Adapt object to its message value with source location metadata on failure. */
  Object adapt(String messageName, Object message);
}
