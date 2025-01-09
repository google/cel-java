package dev.cel.legacy.runtime.async;

import com.google.common.collect.ImmutableSet;
import dev.cel.common.CelOptions;
import dev.cel.common.ExprFeatures;
import dev.cel.runtime.StandardFunctions;

/** Backward-compatible helper class for creating instances of {@link AsyncDispatcher}. */
public final class DefaultAsyncDispatcher {

  /**
   * Creates a new dispatcher with all standard functions using a provided type resolver and the
   * provided custom set of {@link ExprFeatures} to enable various fixes and features.
   *
   * <p>It is recommended that callers supply {@link ExprFeatures#CURRENT} if they wish to
   * automatically pick up fixes for CEL-Java conformance issues.
   */
  public static AsyncDispatcher create(
      TypeResolver typeResolver, ImmutableSet<ExprFeatures> features) {
    return create(typeResolver, CelOptions.fromExprFeatures(features));
  }

  public static AsyncDispatcher create(TypeResolver typeResolver, CelOptions celOptions) {
    AsyncDispatcher dispatcher = new AsyncDispatcherBase();
    new StandardConstructs(typeResolver, celOptions).addAllTo(dispatcher);
    StandardFunctions.addNonInlined(dispatcher, celOptions);
    return dispatcher;
  }

  /** Creates a new dispatcher with all standard functions and using the standard type resolver. */
  public static AsyncDispatcher create(ImmutableSet<ExprFeatures> features) {
    return create(CelOptions.fromExprFeatures(features));
  }

  public static AsyncDispatcher create(CelOptions celOptions) {
    return create(StandardTypeResolver.getInstance(celOptions), celOptions);
  }

  private DefaultAsyncDispatcher() {}
}
