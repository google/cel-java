package dev.cel.policy;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.errorprone.annotations.CheckReturnValue;
import dev.cel.policy.CelPolicyParser.TagVisitor;

public interface CelPolicyParserBuilder<T> {

  @CanIgnoreReturnValue
  CelPolicyParserBuilder<T> addTagVisitor(TagVisitor<T> tagVisitor);

  @CheckReturnValue
  CelPolicyParser build();
}
