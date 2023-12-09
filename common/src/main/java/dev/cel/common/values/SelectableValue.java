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

import java.util.Optional;

/**
 * SelectableValue is an interface for representing a value that supports field selection and
 * presence tests. Few examples are structs, protobuf messages, maps and optional values.
 */
public interface SelectableValue<T extends CelValue> {

  /**
   * Performs field selection. The behavior depends on the concrete implementation of the value
   * being selected. For structs and maps, this must throw an exception if the field does not exist.
   * For optional values, this will return an {@code optional.none()}.
   */
  CelValue select(T field);

  /**
   * Finds the field. This will return an {@link Optional#empty()} if the field does not exist. This
   * can be used for presence testing.
   */
  Optional<CelValue> find(T field);
}
