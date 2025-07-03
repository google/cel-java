package dev.cel.runtime.planner;

import com.google.common.collect.ImmutableList;
import com.google.errorprone.annotations.Immutable;
import dev.cel.common.types.CelType;
import dev.cel.common.types.CelTypeProvider;
import dev.cel.runtime.GlobalResolver;

@Immutable
interface Attribute {
  Object resolve(GlobalResolver ctx);

  final class MaybeAttribute implements Attribute {
    private final ImmutableList<Attribute> attributes;

    @Override
    public Object resolve(GlobalResolver ctx) {
      for (Attribute attr : attributes) {
        Object value = attr.resolve(ctx);
        if (value != null) {
          return value;
        }
      }

      throw new IllegalArgumentException("no such attribute(s): %s");
    }

    MaybeAttribute(ImmutableList<Attribute> attributes) {
      this.attributes = attributes;
    }
  }

  final class NamespacedAttribute implements Attribute {
    private final ImmutableList<String> namespacedNames;
    private final CelTypeProvider typeProvider;

    @Override
    public Object resolve(GlobalResolver ctx) {
      for (String name : namespacedNames) {
        Object value = ctx.resolve(name);
        if (value != null) {
          // TODO: apply qualifiers
          return value;
        }

        CelType type = typeProvider.findType(name).orElse(null);
        if (type != null) {
          return type;
        }
      }

      throw new IllegalArgumentException("no such attribute(s): %s");
    }

    NamespacedAttribute(
        CelTypeProvider typeProvider,
        ImmutableList<String> namespacedNames
    ) {
      this.typeProvider = typeProvider;
      this.namespacedNames = namespacedNames;
    }
  }
}
