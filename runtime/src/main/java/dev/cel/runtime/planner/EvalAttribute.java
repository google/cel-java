package dev.cel.runtime.planner;

import com.google.errorprone.annotations.Immutable;
import dev.cel.common.values.CelValue;
import dev.cel.common.values.CelValueConverter;
import dev.cel.runtime.GlobalResolver;

@Immutable
final class EvalAttribute implements CelValueInterpretable {

  private final long id;
  private final CelValueConverter celValueConverter;
  private final Attribute attr;

  @Override
  public CelValue eval(GlobalResolver resolver) {
    Object obj = attr.resolve(resolver);
    return celValueConverter.fromJavaObjectToCelValue(obj);
  }

  static EvalAttribute create(long id, CelValueConverter celValueConverter, Attribute attr) {
    return new EvalAttribute(id, celValueConverter, attr);
  }

  private EvalAttribute(long id, CelValueConverter celValueConverter, Attribute attr) {
    this.id = id;
    this.celValueConverter = celValueConverter;
    this.attr = attr;
  }
}
