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

import static dev.cel.policy.YamlHelper.ERROR;
import static dev.cel.policy.YamlHelper.assertYamlType;

import dev.cel.common.CelIssue;
import dev.cel.common.CelSourceLocation;
import dev.cel.policy.YamlHelper.YamlNodeType;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.DumperOptions.ScalarStyle;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.ScalarNode;

/** Package-private class to assist with storing policy parsing context. */
final class YamlParserContextImpl implements ParserContext<Node> {

  private final ArrayList<CelIssue> issues;
  private final HashMap<Long, CelSourceLocation> idToLocationMap;
  private final HashMap<Long, Integer> idToOffsetMap;
  private final CelPolicySource policySource;
  private long id;

  @Override
  public void reportError(long id, String message) {
    issues.add(CelIssue.formatError(idToLocationMap.get(id), message));
  }

  @Override
  public List<CelIssue> getIssues() {
    return issues;
  }

  @Override
  public Map<Long, Integer> getIdToOffsetMap() {
    return idToOffsetMap;
  }

  @Override
  public ValueString newValueString(Node node) {
    long id = collectMetadata(node);
    if (!assertYamlType(this, id, node, YamlNodeType.STRING, YamlNodeType.TEXT)) {
      return ValueString.of(id, ERROR);
    }

    ScalarNode scalarNode = (ScalarNode) node;

    // TODO: Compute relative source for multiline strings
    return ValueString.of(id, scalarNode.getValue());
  }

  @Override
  public long collectMetadata(Node node) {
    long id = nextId();
    int line = node.getStartMark().getLine() + 1; // Yaml lines are 0 indexed
    int column = node.getStartMark().getColumn();
    if (node instanceof ScalarNode) {
      DumperOptions.ScalarStyle style = ((ScalarNode) node).getScalarStyle();
      if (style.equals(ScalarStyle.SINGLE_QUOTED)
          || style.equals(ScalarStyle.DOUBLE_QUOTED)) {
        column++;
      } else if (style.equals(ScalarStyle.LITERAL) || style.equals(ScalarStyle.FOLDED)) {
        // For multi-lines, actual string content begins on next line
        line++;
        // Columns must be computed from the indentation

      }
    }
    idToLocationMap.put(id, CelSourceLocation.of(line, column));

    int offset = 0;
    if (line > 1) {
      offset = policySource.getContent().lineOffsets().get(line - 2) + column;
    }
    idToOffsetMap.put(id, offset);

    return id;
  }

  @Override
  public long nextId() {
    return ++id;
  }

  static ParserContext<Node> newInstance(CelPolicySource source) {
    return new YamlParserContextImpl(source);
  }

  private YamlParserContextImpl(CelPolicySource source) {
    this.issues = new ArrayList<>();
    this.idToLocationMap = new HashMap<>();
    this.idToOffsetMap = new HashMap<>();
    this.policySource = source;
  }
}
