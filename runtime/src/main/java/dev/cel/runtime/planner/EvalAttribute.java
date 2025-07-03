package dev.cel.runtime.planner;

import com.google.common.collect.ImmutableList;
import com.google.errorprone.annotations.Immutable;
import dev.cel.common.values.CelValue;
import dev.cel.common.values.CelValueConverter;
import dev.cel.runtime.CelEvaluationException;
import dev.cel.runtime.GlobalResolver;

@Immutable
final class EvalAttribute implements CelValueInterpretable {

  private final long id;
  private final CelValueConverter celValueConverter;
  private final Attribute attr;

  @Override
  public CelValue eval(GlobalResolver resolver) throws CelEvaluationException {
    Object obj = attr.resolve(resolver);
    return celValueConverter.fromJavaObjectToCelValue(obj);
  }

  static EvalAttribute newAbsoluteAttribute(long id, CelValueConverter celValueConverter, String... names) {
    return new EvalAttribute(id, celValueConverter, new AbsoluteAttribute(ImmutableList.copyOf(names)));
  }

  static EvalAttribute newMaybeAttribute(long id, CelValueConverter celValueConverter, String container, String... names) {
    // TODO: Resolve container names
    return new EvalAttribute(
        id,
        celValueConverter,
        new MaybeAttribute(
            ImmutableList.of(new NamespacedAttribute(ImmutableList.copyOf(names)))
        )
    );
  }

  @Immutable
  private interface Attribute {
    Object resolve(GlobalResolver ctx);
  }

  private static class MaybeAttribute implements Attribute {
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

    private MaybeAttribute(ImmutableList<Attribute> attributes) {
      this.attributes = attributes;
    }
  }

  private static class NamespacedAttribute implements Attribute {
    private final ImmutableList<String> namespacedNames;

    @Override
    public Object resolve(GlobalResolver ctx) {
      for (String name : namespacedNames) {
        Object value = ctx.resolve(name);
        if (value != null) {
          return value;
        }

        // TODO: apply qualifiers
      }

      throw new IllegalArgumentException("no such attribute(s): %s");
    }

    private NamespacedAttribute(ImmutableList<String> namespacedNames) {
      this.namespacedNames = namespacedNames;
    }
  }

  private static class AbsoluteAttribute implements Attribute {
    private final ImmutableList<String> namespacedNames;

    @Override
    public Object resolve(GlobalResolver ctx) {
      for (String name : namespacedNames) {
        Object value = ctx.resolve(name);
        if (value != null) {
          return value;
        }
      }

      throw new IllegalArgumentException("no such attribute(s): %s");
    }

    private AbsoluteAttribute(ImmutableList<String> namespacedNames) {
      this.namespacedNames = namespacedNames;
    }
  }

  private EvalAttribute(long id, CelValueConverter celValueConverter, Attribute attr) {
    this.id = id;
    this.celValueConverter = celValueConverter;
    this.attr = attr;
  }
}
