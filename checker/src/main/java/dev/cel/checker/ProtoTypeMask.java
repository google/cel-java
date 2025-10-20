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

package dev.cel.checker;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.auto.value.AutoValue;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.errorprone.annotations.Immutable;
import com.google.protobuf.FieldMask;

/**
 * {@code ProtoTypeMask} describes the fraction of a protobuf type's object graph that should be
 * visible within CEL expressions. The top-level fields of the type identified by the {@link
 * #getTypeName} are treated as variable declarations.
 */
@AutoValue
@Immutable
public abstract class ProtoTypeMask {

  private static final Splitter PATH_SPLITTER = Splitter.on('.');

  /** WILDCARD_FIELD indicates that all fields within the proto type are visible. */
  static final String WILDCARD_FIELD = "*";

  /** HIDDEN_FIELD indicates that all fields within the proto type are not visible. */
  static final String HIDDEN_FIELD = "!";

  private static final FieldMask HIDDEN_FIELD_MASK =
      FieldMask.newBuilder().addPaths(HIDDEN_FIELD).build();

  private static final FieldPath HIDDEN_FIELD_PATH = FieldPath.of(HIDDEN_FIELD);
  private static final FieldMask WILDCARD_FIELD_MASK =
      FieldMask.newBuilder().addPaths(WILDCARD_FIELD).build();
  private static final FieldPath WILDCARD_FIELD_PATH = FieldPath.of(WILDCARD_FIELD);

  abstract String getTypeName();

  abstract ImmutableSet<FieldPath> getFieldPathsExposed();

  abstract boolean fieldsAreVariableDeclarations();

  boolean areAllFieldPathsExposed() {
    return getFieldPathsExposed().stream().allMatch(fp -> fp.equals(WILDCARD_FIELD_PATH));
  }

  boolean areAllFieldPathsHidden() {
    return getFieldPathsExposed().stream().allMatch(fp -> fp.equals(HIDDEN_FIELD_PATH));
  }

  public ProtoTypeMask withFieldsAsVariableDeclarations() {
    return new AutoValue_ProtoTypeMask(getTypeName(), getFieldPathsExposed(), true);
  }

  /**
   * Construct a new {@code ProtoTypeMask} with a {@code FieldMask} indicating which fields should
   * be visible to CEL expressions.
   *
   * <p>All top-level fields from the combined set of {@code paths} in the field mask should be
   * treated as variable identifiers bound to the protobuf field name and its associated field type.
   *
   * <p>A {@code FieldMask} contains one or more {@code paths} which contain identifier characters
   * that have been dot delimited, e.g. resource.name, request.auth.claims. Here are a few things to
   * keep in mind:
   *
   * <ul>
   *   <li>All descendent fields after the last element in the field mask path are visible.
   *   <li>The asterisk '*' can be used as an explicit indicator that all descendent fields are
   *       visible to CEL.
   *   <li>Repeated fields are not supported.
   * </ul>
   */
  public static ProtoTypeMask of(String typeName, FieldMask fieldMask) {
    checkArgument(!Strings.isNullOrEmpty(typeName));
    if (fieldMask == null || fieldMask.getPathsCount() == 0) {
      fieldMask = WILDCARD_FIELD_MASK;
    }
    ImmutableSet.Builder<FieldPath> fieldPaths = ImmutableSet.builder();
    fieldMask.getPathsList().forEach(path -> fieldPaths.add(FieldPath.of(path)));
    return new AutoValue_ProtoTypeMask(typeName, fieldPaths.build(), false);
  }

  /**
   * Construct a new {@code ProtoTypeMask} which exposes all fields in the given {@code typeName}
   * for use within CEL expressions.
   *
   * <p>The {@code typeName} should be a fully-qualified path, e.g. {@code
   * "google.rpc.context.AttributeContext"}.
   *
   * <p>All top-level fields in the given {@code typeName} should be treated as variable identifiers
   * bound to the protobuf field name and the associated field type.
   */
  public static ProtoTypeMask ofAllFields(String fullyQualifiedTypeName) {
    return of(fullyQualifiedTypeName, WILDCARD_FIELD_MASK);
  }

  /**
   * Construct a new {@code ProtoTypeMask} which hides all fields in the given {@code typeName} for
   * use within CEL expressions.
   *
   * <p>The {@code typeName} should be a fully-qualified path, e.g. {@code
   * "google.rpc.context.AttributeContext"}.
   */
  public static ProtoTypeMask ofAllFieldsHidden(String fullyQualifiedTypeName) {
    return of(fullyQualifiedTypeName, HIDDEN_FIELD_MASK);
  }

  /**
   * FieldPath is the equivalent of a field selection represented within a {@link FieldMask#path}.
   */
  @AutoValue
  @Immutable
  abstract static class FieldPath {

    abstract ImmutableList<String> getFieldSelection();

    static FieldPath of(String path) {
      checkArgument(!Strings.isNullOrEmpty(path), "path must be non-null and not empty");
      return new AutoValue_ProtoTypeMask_FieldPath(ImmutableList.copyOf(PATH_SPLITTER.split(path)));
    }
  }
}
