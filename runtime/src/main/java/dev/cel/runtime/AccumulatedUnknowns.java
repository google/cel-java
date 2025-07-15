package dev.cel.runtime;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * An internal representation used for fast accumulation of unknown expr IDs and attributes.
 * For safety, this object should never be returned as an evaluated result and instead be adapted into an immutable CelUnknownSet.
 */
final class AccumulatedUnknowns {

  private final List<Long> exprIds;
  private final List<CelAttribute> attributes;

  List<Long> exprIds() {
    return exprIds;
  }

  List<CelAttribute> attributes() {
    return attributes;
  }

  AccumulatedUnknowns merge(AccumulatedUnknowns arg) {
    this.exprIds.addAll(arg.exprIds);
    this.attributes.addAll(arg.attributes);
    return this;
  }

  static AccumulatedUnknowns create(Long... ids) {
    return create(Arrays.asList(ids));
  }

  static AccumulatedUnknowns create(Collection<Long> ids) {
    return create(ids, new ArrayList<>());
  }

  static AccumulatedUnknowns create(Collection<Long> exprIds, Collection<CelAttribute> attributes) {
    return new AccumulatedUnknowns(new ArrayList<>(exprIds), new ArrayList<>(attributes));
  }

  private AccumulatedUnknowns(List<Long> exprIds, List<CelAttribute> attributes) {
    this.exprIds = exprIds;
    this.attributes = attributes;
  }
}
