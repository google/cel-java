package dev.cel.legacy.runtime.async;

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;
import javax.annotation.Nullable;

/**
 * Dynamic environments combine the local context (aka a runtime stack) for local variables with the
 * global context comprised of the resolver for global variables as well as the {@link AsyncContext}
 * to be used during execution. The local context stores futures rather than plain objects, making
 * it possible for comprehensions to treat individual steps lazily and allow for short-circuiting
 * evaluations.
 */
public final class DynamicEnv {

  private final GlobalContext globalContext;
  private final LocalContext localContext;
  private final List<String> globalValueNames;
  private final ImmutableList<Supplier<ListenableFuture<Object>>> cachedGlobals;

  /** Creates a dynamic environment by pairing the given global and local contexts. */
  private DynamicEnv(
      GlobalContext globalContext, LocalContext localContext, List<String> globalValueNames) {
    this.globalContext = globalContext;
    this.localContext = localContext;
    this.globalValueNames = globalValueNames;
    this.cachedGlobals =
        globalValueNames.stream().map(globalContext::resolve).collect(toImmutableList());
  }

  /** Creates a fresh dynamic environment and populates the stack with the given locals. */
  DynamicEnv(
      GlobalContext globalContext,
      List<ListenableFuture<Object>> locals,
      List<String> globalValueNames) {
    this(globalContext, LocalContext.create(locals), globalValueNames);
  }

  /** Creates a fresh dynamic environment from the given global context and no locals. */
  DynamicEnv(GlobalContext globalContext, List<String> globalValueNames) {
    this(globalContext, LocalContext.create(ImmutableList.of()), globalValueNames);
  }

  /** Implements the runtime stack for local bindings. */
  private static final class LocalContext {
    @Nullable final LocalContext parent;
    private final ImmutableList<FluentFuture<Object>> slots;

    /**
     * Effectively clones the parent and creates a new frame into which bindings from the given
     * locals are installed.
     */
    LocalContext(@Nullable LocalContext parent, List<ListenableFuture<Object>> locals) {
      this.parent = parent;
      this.slots = locals.stream().map(FluentFuture::from).collect(toImmutableList());
    }

    /**
     * Returns a context with just the given local bindings. In the degenerate case where the
     * bindings are empty, the result is null.
     */
    @Nullable
    static LocalContext create(List<ListenableFuture<Object>> locals) {
      return locals.isEmpty() ? null : new LocalContext(null, locals);
    }

    /**
     * Retrieves slot that is offset elements away from the top of the stack. Since the top of the
     * stack (corresponding to offset 0) is not a slot itself, valid slot numbers start at 1.
     */
    FluentFuture<Object> getAtSlotOffset(int offset) {
      int numSlots = slots.size();
      // An implicit invariant of the compilation algorithm is that parent is guaranteed
      // to be non-null when offset exceeds numSlots.  The value numSlots describes the number
      // "locally" available slots (within the current frame).  The global invariant is that
      // a dynamic environment (a "stack") always has sufficiently many slots in its local context
      // so that calls of getAtSlotOffset(...) can be satisfied.  This implies that when the
      // top-most frame does not have enough slots, then these slots must exist in the parent -
      // which therefore cannot be null in that case.
      return offset > numSlots
          ? parent.getAtSlotOffset(offset - numSlots)
          : slots.get(numSlots - offset);
    }
  }

  /**
   * Clones the current environment and extends the result with the given values corresponding to
   * local variables by pushing them onto the stack.
   */
  public DynamicEnv extend(FluentFuture<Object>... futures) {
    return new DynamicEnv(
        globalContext, new LocalContext(localContext, Arrays.asList(futures)), globalValueNames);
  }

  /** Obtains the value of a global variable by invoking the resolver-provided supplier. */
  FluentFuture<Object> getGlobal(int index) {
    return FluentFuture.from(cachedGlobals.get(index).get());
  }

  /**
   * Obtains the value of a local variable by accessing the given stack location relative to the
   * current top of the stack.
   */
  public FluentFuture<Object> getLocalAtSlotOffset(int offset) {
    return localContext.getAtSlotOffset(offset);
  }

  /**
   * Clones the stack of this dynamic environment while substituting the global context given by the
   * arguments. The result will only have a single frame containing all bindings.
   *
   * <p>Background: A {@link DynamicEnv} instance can be viewed as pairing the stack for local
   * bindings on the one hand with the global state (which includes global bindings as well as the
   * execution context) on the other.
   *
   * <p>Most of the time the global state remains fixed while the stack changes according to how the
   * flow of execution traverses the local binding structure of the program. But in some situation
   * it is useful to keep local bindings fixed while substituting a different global context. This
   * method provides the mechanism for that.
   *
   * <p>Notice that the current global references must be retained.
   */
  public DynamicEnv withGlobalContext(GlobalContext otherGlobalContext) {
    return new DynamicEnv(otherGlobalContext, localContext, globalValueNames);
  }

  /** Provides access to the global context. */
  public GlobalContext globalContext() {
    return globalContext;
  }

  /** Provides access to the context. */
  public AsyncContext currentContext() {
    return globalContext().context();
  }

  /** Provides access to the global resolver itself. */
  public AsyncCanonicalResolver globalResolver() {
    return globalContext().resolver();
  }
}
