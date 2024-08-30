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

package dev.cel.runtime;

import java.util.Optional;

final class HierarchicalVariableResolver implements CelVariableResolver {

  private final CelVariableResolver primary;
  private final CelVariableResolver secondary;

  @Override
  public Optional<Object> find(String name) {
    Optional<Object> value = primary.find(name);
    return value.isPresent() ? value : secondary.find(name);
  }

  @Override
  public String toString() {
    return secondary + " +> " + primary;
  }

  static HierarchicalVariableResolver newInstance(
      CelVariableResolver primary, CelVariableResolver secondary) {
    return new HierarchicalVariableResolver(primary, secondary);
  }

  private HierarchicalVariableResolver(CelVariableResolver primary, CelVariableResolver secondary) {
    this.primary = primary;
    this.secondary = secondary;
  }
}
