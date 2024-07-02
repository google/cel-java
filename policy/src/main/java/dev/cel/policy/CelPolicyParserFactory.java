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

import org.yaml.snakeyaml.nodes.Node;

/** Factory class for producing policy parser and policy config parsers. */
public final class CelPolicyParserFactory {

  /**
   * Configure a builder to construct a {@link CelPolicyParser} instance that takes in a YAML
   * document.
   */
  public static CelPolicyParserBuilder<Node> newYamlParserBuilder() {
    return CelPolicyYamlParser.newBuilder();
  }

  /**
   * Configure a builder to construct a {@link CelPolicyConfigParser} instance that takes in a YAML
   * document.
   */
  public static CelPolicyConfigParser newYamlConfigParser() {
    return CelPolicyYamlConfigParser.newInstance();
  }

  private CelPolicyParserFactory() {}
}
