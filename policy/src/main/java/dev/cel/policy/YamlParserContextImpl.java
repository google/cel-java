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

import com.google.common.base.Strings;
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
    ScalarStyle style = scalarNode.getScalarStyle();
    if (style.equals(ScalarStyle.FOLDED) || style.equals(ScalarStyle.LITERAL)) {
      CelSourceLocation location = idToLocationMap.get(id);
      int line = location.getLine();
      int column = location.getColumn();

      String indent = Strings.padStart("", column, ' ');
      String text = policySource.getSnippet(line).orElse("");
      StringBuilder raw = new StringBuilder();
      while (text.startsWith(indent)) {
        line++;
        raw.append(text);
        text = policySource.getSnippet(line).orElse("");
        if (text.isEmpty()) {
          break;
        }
        if (text.startsWith(indent)) {
          raw.append("\n");
        }
      }

      idToOffsetMap.compute(id, (k, offset) -> offset - column);

      return ValueString.of(id, raw.toString());
    }

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
        column = 0;
        String snippet = policySource.getSnippet(line).orElse("");
        for (char c : snippet.toCharArray()) {
          if (!Character.isWhitespace(c)) {
            break;
          }
          column++;
        }
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
