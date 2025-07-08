package dev.cel.runtime;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.errorprone.annotations.CheckReturnValue;
import dev.cel.common.annotations.Internal;

import java.util.Optional;

@Internal
@AutoValue
public abstract class DefaultDispatcher {

    abstract ImmutableMap<String, CelFunctionBinding> overloads();

    public Optional<CelFunctionBinding> findOverload(String overloadId) {
        return Optional.ofNullable(overloads().get(overloadId));
    }

    public static Builder newBuilder() {
        return new AutoValue_DefaultDispatcher.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract ImmutableMap.Builder<String, CelFunctionBinding> overloadsBuilder();

        @CanIgnoreReturnValue
        public Builder addOverload(CelFunctionBinding functionBinding) {
            overloadsBuilder().put(
                functionBinding.getOverloadId(),
                functionBinding);
            return this;
        }

        @CanIgnoreReturnValue
        public Builder addFunction(String functionName, CelFunctionOverload definition) {
            overloadsBuilder().put(
                functionName, CelFunctionBinding.from(functionName, ImmutableList.of(), definition)
            );

            return this;
        }

        @CheckReturnValue
        public abstract DefaultDispatcher build();
    }
}
