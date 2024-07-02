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

package dev.cel.policy;

/** Public interface for parsing CEL policy sources. */
public interface CelPolicyConfigParser {

  /** Parsers the input {@code policyConfigSource} and returns a {@link CelPolicyConfig}. */
  CelPolicyConfig parse(String policyConfigSource) throws CelPolicyValidationException;

  /**
   * Parses the input {@code policyConfigSource} and returns a {@link CelPolicyConfig}.
   *
   * <p>The {@code description} may be used to help tailor error messages for the location where the
   * {@code policySource} originates, e.g. a file name or form UI element.
   */
  CelPolicyConfig parse(String policyConfigSource, String description)
      throws CelPolicyValidationException;
}
