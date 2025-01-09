package dev.cel.legacy.runtime.async;

import static dev.cel.legacy.runtime.async.EvaluationHelpers.immediateException;
import static java.util.stream.Collectors.joining;

import com.google.auto.value.AutoValue;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.protobuf.MessageLite;
import dev.cel.common.CelErrorCode;
import dev.cel.runtime.InterpreterException;
import dev.cel.runtime.Metadata;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;

/**
 * Base implementation of interface {@link AsyncDispatcher}.
 *
 * <p>A fresh {@link AsyncDispatcherBase} starts out empty, i.e., initially has no bindings at all.
 */
public class AsyncDispatcherBase implements AsyncDispatcher {

  // Mappings from overload IDs to either call constructors or runtime-overloadable
  // (strict) functions.  The demains of these two maps are maintained to be disjoint.
  private final Map<String, CallConstructor> constructors;
  private final Map<String, OverloadInfo> overloads;

  /** Creates new empty registry. */
  public AsyncDispatcherBase() {
    this.constructors = new HashMap<>();
    this.overloads = new HashMap<>();
  }

  /** Cloning constructor, for implementing snapshots. */
  protected AsyncDispatcherBase(AsyncDispatcherBase orig) {
    this.constructors = new HashMap<>(orig.constructors);
    this.overloads = new HashMap<>(orig.overloads);
  }

  /**
   * Adds a generic call constructor for the given overload ID. Function calls with this overload ID
   * will later be handled by the given {@link CallConstructor}. No runtime overloading is possible.
   */
  @Override
  public void addCallConstructor(String overloadId, CallConstructor callConstructor) {
    checkNotAlreadyBound(overloadId);
    constructors.put(overloadId, callConstructor);
  }

  /** Adds a strict function as one possible binding for a runtime-overloadable function. */
  @Override
  public void addStrictFunction(
      String overloadId,
      List<Class<?>> argumentTypes,
      boolean contextIndependent,
      StrictFunction strictFunction) {
    checkNotAlreadyBound(overloadId);
    overloads.put(
        overloadId, OverloadInfo.of(overloadId, argumentTypes, contextIndependent, strictFunction));
  }

  /**
   * Constructs the compiled CEL expression that implements the call of a function at some call
   * site.
   *
   * <p>If multiple overload IDs are given, then a runtime dispatch is implemented. All overload IDs
   * must refer to strict functions in that case.
   */
  // This lambda implements @Immutable interface 'StrictFunction', but 'List' is mutable
  @SuppressWarnings("Immutable")
  @Override
  public CompiledExpression constructCall(
      @Nullable Metadata metadata,
      long exprId,
      String functionName,
      List<String> overloadIds,
      List<IdentifiedCompiledExpression> compiledArguments,
      MessageProcessor messageProcessor,
      StackOffsetFinder stackOffsetFinder)
      throws InterpreterException {
    Preconditions.checkState(!overloadIds.isEmpty(), "no overloads for call of %s", functionName);
    if (overloadIds.size() == 1) {
      // Unique binding.
      String overloadId = overloadIds.get(0);
      if (constructors.containsKey(overloadId)) {
        return constructors
            .get(overloadId)
            .construct(metadata, exprId, compiledArguments, messageProcessor, stackOffsetFinder);
      }
    }

    List<OverloadInfo> candidates = new ArrayList<>();
    List<String> unbound = new ArrayList<>();
    for (String overloadId : overloadIds) {
      if (constructors.containsKey(overloadId)) {
        throw new InterpreterException.Builder(
                "incompatible overload for function '%s': %s must be resolved at compile time",
                functionName, overloadId)
            .setLocation(metadata, exprId)
            .build();
      } else if (overloads.containsKey(overloadId)) {
        candidates.add(overloads.get(overloadId));
      } else {
        unbound.add(overloadId);
      }
    }

    if (!unbound.isEmpty()) {
      throw new InterpreterException.Builder("no runtime binding for %s", String.join(",", unbound))
          .setLocation(metadata, exprId)
          .build();
    }

    if (candidates.size() == 1) {
      OverloadInfo overload = candidates.get(0);
      return constructStrictCall(
          overload.function(),
          overload.overloadId(),
          overload.contextIndependent(),
          compiledArguments);
    }
    // Key for memoizing the overload dispatch itself.
    String memoizationKey = candidates.stream().map(OverloadInfo::overloadId).collect(joining("|"));
    boolean contextIndependent = candidates.stream().allMatch(OverloadInfo::contextIndependent);
    return constructStrictCall(
        (gctx, arguments) -> {
          List<OverloadInfo> matching = new ArrayList<>();
          for (OverloadInfo candidate : candidates) {
            if (candidate.canHandle(arguments)) {
              matching.add(candidate);
            }
          }
          if (matching.isEmpty()) {
            return immediateException(
                new InterpreterException.Builder(
                        "No matching overload for function '%s'. Overload candidates: %s",
                        functionName, String.join(",", overloadIds))
                    .setErrorCode(CelErrorCode.OVERLOAD_NOT_FOUND)
                    .setLocation(metadata, exprId)
                    .build());
          }
          if (matching.size() > 1) {
            return immediateException(
                new InterpreterException.Builder(
                        "Ambiguous overloads for function '%s'. Matching candidates: %s",
                        functionName,
                        matching.stream().map(OverloadInfo::overloadId).collect(joining(",")))
                    .setErrorCode(CelErrorCode.AMBIGUOUS_OVERLOAD)
                    .setLocation(metadata, exprId)
                    .build());
          }
          OverloadInfo match = matching.get(0);
          return match.contextIndependent()
              ? match.function().apply(gctx, arguments)
              : gctx.context()
                  .perform(
                      () -> match.function().apply(gctx, arguments), match.overloadId(), arguments);
        },
        memoizationKey,
        contextIndependent,
        compiledArguments);
  }

  /**
   * Constructs a call of a single strict function. This is a thin wrapper around {@link
   * EvaluationHelpers#compileStrictCall}.
   */
  private CompiledExpression constructStrictCall(
      StrictFunction function,
      String overloadId,
      boolean contextIndependent,
      List<IdentifiedCompiledExpression> compiledArguments)
      throws InterpreterException {
    return EvaluationHelpers.compileStrictCall(
        function,
        overloadId,
        contextIndependent ? Effect.CONTEXT_INDEPENDENT : Effect.CONTEXT_DEPENDENT,
        compiledArguments);
  }

  /**
   * Creates an independent copy of the current state of the dispatcher. Further updates to either
   * the original or the forked copy do not affect the respective other.
   */
  @Override
  public AsyncDispatcher fork() {
    return new AsyncDispatcherBase(this);
  }

  // Not to be overridden in subclasses!  This method only checks whether it is locally
  // (i.e., only with respect to constructors and overloads of this dispatcher) to add
  // a new binding for overloadId.
  private boolean isLocallyBound(String overloadId) {
    return constructors.containsKey(overloadId) || overloads.containsKey(overloadId);
  }

  /**
   * Determines whether or not the given overload ID corresponds to a known function binding.
   * Subclasses that provide additional bindings should override this.
   */
  @Override
  public boolean isBound(String overloadId) {
    return isLocallyBound(overloadId);
  }

  /** Helper for making sure that no overload ID is bound more than once. */
  private void checkNotAlreadyBound(String overloadId) {
    Preconditions.checkState(
        !isLocallyBound(overloadId), "More than one binding for %s.", overloadId);
  }

  /** Helper class for storing information about a single overloadable strict function. */
  @AutoValue
  abstract static class OverloadInfo {
    /** The overload ID in question. */
    abstract String overloadId();

    /** Java classes of the expected arguments. */
    abstract ImmutableList<Class<?>> argumentTypes();

    /** True if the function is context-independent. */
    abstract boolean contextIndependent();

    /** The function that is bound to the overload ID. */
    abstract StrictFunction function();

    static OverloadInfo of(
        String overloadId,
        List<Class<?>> argumentTypes,
        boolean contextIndependent,
        StrictFunction function) {
      return new AutoValue_AsyncDispatcherBase_OverloadInfo(
          overloadId, ImmutableList.copyOf(argumentTypes), contextIndependent, function);
    }

    /** Determines whether this overload can handle a call with the given actual arguments. */
    boolean canHandle(List<Object> arguments) {
      int arity = argumentTypes().size();
      if (arity != arguments.size()) {
        return false;
      }
      for (int i = 0; i < arity; ++i) {
        if (!argMatchesType(arguments.get(i), argumentTypes().get(i))) {
          return false;
        }
      }
      return true;
    }

    /** Helper for determining runtime argument type matches. */
    private static boolean argMatchesType(Object argument, Class<?> parameterType) {
      if (argument != null) {
        return parameterType.isAssignableFrom(argument.getClass());
      }
      // null can be assigned to messages, maps, and objects
      return parameterType == Object.class
          || MessageLite.class.isAssignableFrom(parameterType)
          || Map.class.isAssignableFrom(parameterType);
    }
  }
}
