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

package dev.cel.common.formats;

import static com.google.common.collect.ImmutableMap.toImmutableMap;
import static java.util.Arrays.stream;
import static java.util.stream.Collectors.joining;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableMap;
import java.io.StringReader;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.ScalarNode;

/** Helper class for parsing YAML. */
public final class YamlHelper {
  public static final String ERROR = "*error*";

  /** Enum for YAML node types. */
  public enum YamlNodeType {
    MAP("tag:yaml.org,2002:map"),
    STRING("tag:yaml.org,2002:str"),
    BOOLEAN("tag:yaml.org,2002:bool"),
    INTEGER("tag:yaml.org,2002:int"),
    DOUBLE("tag:yaml.org,2002:float"),
    TEXT("!txt"),
    LIST("tag:yaml.org,2002:seq"),
    ;

    public static final ImmutableMap<String, YamlNodeType> TAG_TO_NODE_TYPE =
        stream(YamlNodeType.values())
            .collect(toImmutableMap(YamlNodeType::tag, Function.identity()));

    private final String tag;

    public String tag() {
      return tag;
    }

    YamlNodeType(String tag) {
      this.tag = tag;
    }
  }

  /** Assert that a given YAML node matches one of the provided {@code YamlNodeType} values. */
  public static boolean assertYamlType(
      ParserContext<Node> ctx, long id, Node node, YamlNodeType... expectedNodeTypes) {
    if (validateYamlType(node, expectedNodeTypes)) {
      return true;
    }
    String nodeTag = node.getTag().getValue();

    ctx.reportError(
        id,
        String.format(
            "Got yaml node type %s, wanted type(s) [%s]",
            nodeTag, stream(expectedNodeTypes).map(YamlNodeType::tag).collect(joining(" "))));
    return false;
  }

  public static Optional<Node> parseYamlSource(String policyContent) {
    Yaml yaml = new Yaml(new SafeConstructor(new LoaderOptions()));
    return Optional.ofNullable(yaml.compose(new StringReader(policyContent)));
  }

  public static boolean assertRequiredFields(
      ParserContext<Node> ctx, long id, List<String> missingRequiredFields) {
    if (missingRequiredFields.isEmpty()) {
      return true;
    }

    ctx.reportError(
        id,
        String.format(
            "Missing required attribute(s): %s", Joiner.on(", ").join(missingRequiredFields)));
    return false;
  }

  public static boolean validateYamlType(Node node, YamlNodeType... expectedNodeTypes) {
    String nodeTag = node.getTag().getValue();
    for (YamlNodeType expectedNodeType : expectedNodeTypes) {
      if (expectedNodeType.tag().equals(nodeTag)) {
        return true;
      }
    }
    return false;
  }

  public static Double newDouble(ParserContext<Node> ctx, Node node) {
    long id = ctx.collectMetadata(node);
    if (!assertYamlType(ctx, id, node, YamlNodeType.DOUBLE)) {
      return 0.0;
    }

    return Double.parseDouble(((ScalarNode) node).getValue());
  }

  public static Integer newInteger(ParserContext<Node> ctx, Node node) {
    long id = ctx.collectMetadata(node);
    if (!assertYamlType(ctx, id, node, YamlNodeType.INTEGER)) {
      return 0;
    }

    return Integer.parseInt(((ScalarNode) node).getValue());
  }

  public static boolean newBoolean(ParserContext<Node> ctx, Node node) {
    long id = ctx.collectMetadata(node);
    if (!assertYamlType(ctx, id, node, YamlNodeType.BOOLEAN)) {
      return false;
    }

    return Boolean.parseBoolean(((ScalarNode) node).getValue());
  }

  public static String newString(ParserContext<Node> ctx, Node node) {
    return ctx.newValueString(node).value();
  }

  private YamlHelper() {}
}
