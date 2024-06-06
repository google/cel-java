package dev.cel.policy;

public interface CelPolicyParser {

  CelPolicy parse(CelPolicySource source) throws CelPolicyValidationException;

  interface ParserContext {

    void reportError(long id, String message);
  }

  interface TagVisitor<T> {

    void visitPolicyTag(ParserContext ctx, long id, String fieldName, T node,
        CelPolicy.Builder policyBuilder);

    void visitRuleTag(ParserContext ctx, long id, String fieldName, T node,
        CelPolicy.Rule.Builder ruleBuilder);

    void visitMatchTag(ParserContext ctx, long id, String fieldName, T node,
        CelPolicy.Match.Builder matchBuilder);

    void visitVariableTag(ParserContext ctx, long id, String fieldName, T node,
        CelPolicy.Variable.Builder variableBuilder);
  }
}
