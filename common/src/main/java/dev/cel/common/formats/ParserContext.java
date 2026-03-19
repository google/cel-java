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

  /**
   * @deprecated Use {@link #newSourceString} instead.
   */
  @Deprecated
  default ValueString newValueString(T node) {
    return newSourceString(node);
  }

  /**
   * NewYamlString creates a new ValueString from the YAML node, evaluated according to standard
   * YAML parsing rules.
   *
   * <p>This respects the whitespace folding semantics defined by the node's scalar style (e.g.,
   * folded string {@code >} versus literal string {@code |}). Use this method for general string
   * fields such as {@code description}, {@code name}, or {@code id}.
   */
  ValueString newYamlString(T node);

  /**
   * NewRawString creates a new ValueString from the YAML node, preserving formatting for accurate
   * source mapping.
   *
   * <p>This extracts the verbatim text directly from the source file, preserving raw block
   * indentation and unmodified newlines. Use this method when the string represents code or a CEL
   * expression where precise character-level offsets must be maintained for accurate diagnostic
   * error reporting.
   */
  ValueString newSourceString(T node);
}
