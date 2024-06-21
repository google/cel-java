package dev.cel.policy;


import dev.cel.common.Source;

import java.util.Map;

public interface ParserContext<T> {

  void reportError(long id, String message);
  String getIssueString();

  Source getSource();

  boolean hasError();

  Map<Long, Integer> getIdToOffsetMap();

  long collectMetadata(T node);
  long nextId();
}
