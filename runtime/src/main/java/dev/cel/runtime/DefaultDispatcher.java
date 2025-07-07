package dev.cel.runtime;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.errorprone.annotations.CheckReturnValue;
import com.google.errorprone.annotations.Immutable;
import dev.cel.common.annotations.Internal;

import java.util.List;
import java.util.Optional;

@Internal
@AutoValue
public abstract class DefaultDispatcher {

    abstract ImmutableMap<String, ResolvedOverload> overloads();

    public Optional<ResolvedOverload> findOverload(String overloadId) {
        return Optional.ofNullable(overloads().get(overloadId));
    }

    public static Builder newBuilder() {
        return new AutoValue_DefaultDispatcher.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        abstract ImmutableMap.Builder<String, ResolvedOverload> overloadsBuilder();

        abstract ImmutableMap<String, ResolvedOverload> overloads();

        @CanIgnoreReturnValue
        public <T> Builder add(String overloadId, Class<T> argType, Registrar.UnaryFunction<T> function) {
            overloadsBuilder().put(
                overloadId,
                ResolvedOverloadImpl.of(
                    overloadId, new Class<?>[] {argType}, args -> function.apply((T) args[0])));
            return this;
        }

        @CanIgnoreReturnValue
        public <T1, T2> Builder add(String overloadId, Class<T1> argType1, Class<T2> argType2, Registrar.BinaryFunction<T1, T2> function) {
            overloadsBuilder().put(
                overloadId,
                ResolvedOverloadImpl.of(
                    overloadId,
                    new Class<?>[] {argType1, argType2},
                    args -> function.apply((T1) args[0], (T2) args[1])));
            return this;
        }

        @CanIgnoreReturnValue
        public Builder add(String overloadId, List<Class<?>> argTypes, Registrar.Function function) {
            overloadsBuilder().put(
                    overloadId,
                    ResolvedOverloadImpl.of(overloadId, argTypes.toArray(new Class<?>[0]), function));
            return this;
        }

        @CheckReturnValue
        public abstract DefaultDispatcher build();
    }

    // TODO: Refactor to reuse across LegacyDispatcher
    @AutoValue
    @Immutable
    abstract static class ResolvedOverloadImpl implements ResolvedOverload {
        /** The overload id of the function. */
        @Override
        public abstract String getOverloadId();

        /** The types of the function parameters. */
        @Override
        public abstract ImmutableList<Class<?>> getParameterTypes();

        /** The function definition. */
        @Override
        public abstract FunctionOverload getDefinition();

        static ResolvedOverload of(
                String overloadId, Class<?>[] parameterTypes, FunctionOverload definition) {
            return of(overloadId, ImmutableList.copyOf(parameterTypes), definition);
        }

        static ResolvedOverload of(
                String overloadId, ImmutableList<Class<?>> parameterTypes, FunctionOverload definition) {
            return new AutoValue_DefaultDispatcher_ResolvedOverloadImpl(
                    overloadId, parameterTypes, definition);
        }
    }
}
