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

package dev.cel.common.formats;

import dev.cel.common.CelIssue;
import java.util.List;
import java.util.Map;

/**
 * ParserContext declares a set of interfaces for managing metadata, such as node IDs, parsing
 * errors and source offsets.
 */
public interface ParserContext<T> {

  /**
   * NextID returns a monotonically increasing identifier for a source fragment. This ID is
   * implicitly created and tracked within the CollectMetadata method.
   */
  long nextId();

  /**
   * CollectMetadata records the source position information of a given node, and returns the id
   * associated with the source metadata which is returned in the Policy SourceInfo object.
   */
  long collectMetadata(T node);

  void reportError(long id, String message);

  List<CelIssue> getIssues();

  Map<Long, Integer> getIdToOffsetMap();

  /** NewString creates a new ValueString from the YAML node. */
  ValueString newValueString(T node);
}
