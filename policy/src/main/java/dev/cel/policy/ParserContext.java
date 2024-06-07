package dev.cel.policy;

public interface ParserContext<T> {

  void reportError(long id, String message);
  String getIssueString();
  boolean hasError();
  long collectMetadata(T node);
}
