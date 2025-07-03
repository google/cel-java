package dev.cel.runtime.planner;

import com.google.common.collect.ImmutableList;
import com.google.errorprone.annotations.Immutable;
import dev.cel.common.types.CelTypeProvider;
import dev.cel.common.values.CelValueConverter;
import dev.cel.runtime.planner.Attribute.NamespacedAttribute;
import dev.cel.runtime.planner.Attribute.MaybeAttribute;

@Immutable
final class AttributeFactory {

  private final String container;
  private final CelValueConverter celValueConverter;
  private final CelTypeProvider typeProvider;

  NamespacedAttribute newAbsoluteAttribute(String... names) {
    return new NamespacedAttribute(
        typeProvider,
        ImmutableList.copyOf(names)
    );
  }

  MaybeAttribute newMaybeAttribute(String... names) {
    // TODO: Resolve container names
    return new MaybeAttribute(
        ImmutableList.of(new NamespacedAttribute(typeProvider, ImmutableList.copyOf(names)))
    );
  }

  static AttributeFactory newAttributeFactory(String container, CelValueConverter celValueConverter, CelTypeProvider typeProvider) {
    return new AttributeFactory(container, celValueConverter, typeProvider);
  }

  private AttributeFactory(
      String container,
      CelValueConverter celValueConverter,
      CelTypeProvider typeProvider
  ) {
    this.container = container;
    this.celValueConverter = celValueConverter;
    this.typeProvider = typeProvider;
  }
}
