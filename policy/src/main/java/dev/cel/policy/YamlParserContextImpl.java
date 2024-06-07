package dev.cel.policy;

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.common.base.Joiner;
import dev.cel.common.CelIssue;
import dev.cel.common.CelSourceLocation;
import java.util.ArrayList;
import java.util.HashMap;
import org.yaml.snakeyaml.nodes.Node;


/**
 * Package-private class to assist with storing policy parsing context.
 */
final class YamlParserContextImpl implements ParserContext<Node> {

  private static final Joiner JOINER = Joiner.on('\n');

  private final ArrayList<CelIssue> issues;
  private final HashMap<Long, CelSourceLocation> idToLocationMap;
  private final CelPolicySource source;
  private long id;

  @Override
  public void reportError(long id, String message) {
    issues.add(CelIssue.formatError(idToLocationMap.get(id), message));
  }

  @Override
  public String getIssueString() {
    return JOINER.join(
        issues.stream().map(iss -> iss.toDisplayString(source))
            .collect(toImmutableList()));
  }

  @Override
  public boolean hasError() {
    return !issues.isEmpty();
  }

  @Override
  public long collectMetadata(Node node) {
    long id = nextId();
    int line = node.getStartMark().getLine() + 1; // Yaml lines are 0 indexed
    int column = node.getStartMark().getColumn();
    idToLocationMap.put(id, CelSourceLocation.of(line, column));

    return id;
  }

  long nextId() {
    return ++id;
  }

  static ParserContext<Node> newInstance(CelPolicySource policySource) {
    return new YamlParserContextImpl(policySource);
  }

  private YamlParserContextImpl(CelPolicySource policySource) {
    this.issues = new ArrayList<>();
    this.idToLocationMap = new HashMap<>();
    this.source = policySource;
  }
}

