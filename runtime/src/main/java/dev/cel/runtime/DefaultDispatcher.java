// Copyright 2022 Google LLC
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

package dev.cel.runtime;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.errorprone.annotations.Immutable;
import javax.annotation.concurrent.ThreadSafe;
import com.google.errorprone.annotations.concurrent.GuardedBy;
import com.google.protobuf.MessageLite;
import dev.cel.common.CelErrorCode;
import dev.cel.common.annotations.Internal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Default implementation of {@link Dispatcher}.
 *
 * <p>Should be final, do not mock; mocking {@link Dispatcher} instead.
 *
 * <p>CEL Library Internals. Do Not Use.
 */
@ThreadSafe
@Internal
public final class DefaultDispatcher implements Dispatcher, Registrar {
  @Internal
  public static DefaultDispatcher create() {
    return new DefaultDispatcher();
  }

  /** Internal representation of an overload. */
  @Immutable
  private static final class Overload {
    final ImmutableList<Class<?>> parameterTypes;

    /** See {@link Function}. */
    final Function function;

    private Overload(Class<?>[] parameterTypes, Function function) {
      this.parameterTypes = ImmutableList.copyOf(parameterTypes);
      this.function = function;
    }

    /** Determines whether this overload can handle the given arguments. */
    private boolean canHandle(Object[] arguments) {
      if (parameterTypes.size() != arguments.length) {
        return false;
      }
      for (int i = 0; i < parameterTypes.size(); i++) {
        Class<?> paramType = parameterTypes.get(i);
        Object arg = arguments[i];
        if (arg == null) {
          // null can be assigned to messages, maps, and to objects.
          if (paramType != Object.class
              && !MessageLite.class.isAssignableFrom(paramType)
              && !Map.class.isAssignableFrom(paramType)) {
            return false;
          }
          continue;
        }
        if (!paramType.isAssignableFrom(arg.getClass())) {
          return false;
        }
      }
      return true;
    }
  }

  @GuardedBy("this")
  private final Map<String, Overload> overloads = new HashMap<>();

  @Override
  @SuppressWarnings("unchecked")
  public synchronized <T> void add(
      String overloadId, Class<T> argType, final UnaryFunction<T> function) {
    overloads.put(
        overloadId, new Overload(new Class<?>[] {argType}, args -> function.apply((T) args[0])));
  }

  @Override
  @SuppressWarnings("unchecked")
  public synchronized <T1, T2> void add(
      String overloadId,
      Class<T1> argType1,
      Class<T2> argType2,
      final BinaryFunction<T1, T2> function) {
    overloads.put(
        overloadId,
        new Overload(
            new Class<?>[] {argType1, argType2},
            args -> function.apply((T1) args[0], (T2) args[1])));
  }

  @Override
  public synchronized void add(String overloadId, List<Class<?>> argTypes, Function function) {
    overloads.put(overloadId, new Overload(argTypes.toArray(new Class<?>[0]), function));
  }

  private static Object dispatch(
      Metadata metadata,
      long exprId,
      String functionName,
      List<String> overloadIds,
      Map<String, Overload> overloads,
      Object[] args)
      throws InterpreterException {
    List<String> candidates = new ArrayList<>();
    for (String overloadId : overloadIds) {
      Overload overload = overloads.get(overloadId);
      if (overload == null) {
        throw new InterpreterException.Builder(
                "[internal] Unknown overload id '%s' for function '%s'", overloadId, functionName)
            .setErrorCode(CelErrorCode.OVERLOAD_NOT_FOUND)
            .setLocation(metadata, exprId)
            .build();
      }
      if (overload.canHandle(args)) {
        candidates.add(overloadId);
      }
    }
    if (candidates.size() == 1) {
      String overloadId = candidates.get(0);
      try {
        return overloads.get(overloadId).function.apply(args);
      } catch (RuntimeException e) {
        throw new InterpreterException.Builder(
                e, "Function '%s' failed with arg(s) '%s'", overloadId, Joiner.on(", ").join(args))
            .build();
      }
    }
    if (candidates.size() > 1) {
      throw new InterpreterException.Builder(
              "Ambiguous overloads for function '%s'. Matching candidates: %s",
              functionName, Joiner.on(",").join(candidates))
          .setErrorCode(CelErrorCode.AMBIGUOUS_OVERLOAD)
          .setLocation(metadata, exprId)
          .build();
    }

    throw new InterpreterException.Builder(
            "No matching overload for function '%s'. Overload candidates: %s",
            functionName, Joiner.on(",").join(overloadIds))
        .setErrorCode(CelErrorCode.OVERLOAD_NOT_FOUND)
        .setLocation(metadata, exprId)
        .build();
  }

  @Override
  public synchronized Object dispatch(
      Metadata metadata, long exprId, String functionName, List<String> overloadIds, Object[] args)
      throws InterpreterException {
    return dispatch(metadata, exprId, functionName, overloadIds, overloads, args);
  }

  @Override
  public synchronized Dispatcher.ImmutableCopy immutableCopy() {
    return new ImmutableCopy(overloads);
  }

  @Immutable
  private static final class ImmutableCopy implements Dispatcher.ImmutableCopy {
    private final ImmutableMap<String, Overload> overloads;

    private ImmutableCopy(Map<String, Overload> overloads) {
      this.overloads = ImmutableMap.copyOf(overloads);
    }

    @Override
    public Object dispatch(
        Metadata metadata,
        long exprId,
        String functionName,
        List<String> overloadIds,
        Object[] args)
        throws InterpreterException {
      return DefaultDispatcher.dispatch(
          metadata, exprId, functionName, overloadIds, overloads, args);
    }

    @Override
    public Dispatcher.ImmutableCopy immutableCopy() {
      return this;
    }
  }

  private DefaultDispatcher() {}
}
