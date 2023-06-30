// Copyright 2023 Google LLC
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

package dev.cel.common.ast;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.auto.value.AutoOneOf;
import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.errorprone.annotations.CheckReturnValue;
import com.google.errorprone.annotations.Immutable;
import dev.cel.common.annotations.Internal;
import java.util.Arrays;
import java.util.Optional;

/**
 * An abstract representation of a common expression.
 *
 * <p>This is the native type equivalent of Expr message in syntax.proto.
 *
 * <p>Expressions are abstractly represented as a collection of identifiers, select statements,
 * function calls, literals, and comprehensions. All operators with the exception of the '.'
 * operator are modelled as function calls. This makes it easy to represent new operators into the
 * existing AST.
 *
 * <p>All references within expressions must resolve to a [Decl][] provided at type-check for an
 * expression to be valid. A reference may either be a bare identifier `name` or a qualified
 * identifier `google.api.name`. References may either refer to a value or a function declaration.
 *
 * <p>For example, the expression `google.api.name.startsWith('expr')` references the declaration
 * `google.api.name` within a [Expr.Select][] expression, and the function declaration `startsWith`.
 */
@AutoValue
@Internal
@Immutable
public abstract class CelExpr {

  public abstract long id();

  public abstract ExprKind exprKind();

  public CelConstant constant() {
    return exprKind().constant();
  }

  public CelIdent ident() {
    return exprKind().ident();
  }

  public CelSelect select() {
    return exprKind().select();
  }

  public CelCall call() {
    return exprKind().call();
  }

  public CelCreateList createList() {
    return exprKind().createList();
  }

  public CelCreateStruct createStruct() {
    return exprKind().createStruct();
  }

  public CelComprehension comprehension() {
    return exprKind().comprehension();
  }

  /** Builder for CelExpr. */
  @AutoValue.Builder
  public abstract static class Builder {
    public abstract long id();

    public abstract Builder setId(long value);

    public abstract Builder setExprKind(ExprKind value);

    public Builder setConstant(CelConstant constant) {
      return setExprKind(AutoOneOf_CelExpr_ExprKind.constant(constant));
    }

    public Builder setIdent(CelIdent ident) {
      return setExprKind(AutoOneOf_CelExpr_ExprKind.ident(ident));
    }

    public Builder setCall(CelCall call) {
      return setExprKind(AutoOneOf_CelExpr_ExprKind.call(call));
    }

    public Builder setSelect(CelSelect select) {
      return setExprKind(AutoOneOf_CelExpr_ExprKind.select(select));
    }

    public Builder setCreateList(CelCreateList createList) {
      return setExprKind(AutoOneOf_CelExpr_ExprKind.createList(createList));
    }

    public Builder setCreateStruct(CelCreateStruct createStruct) {
      return setExprKind(AutoOneOf_CelExpr_ExprKind.createStruct(createStruct));
    }

    public Builder setComprehension(CelComprehension comprehension) {
      return setExprKind(AutoOneOf_CelExpr_ExprKind.comprehension(comprehension));
    }

    @CheckReturnValue
    public abstract CelExpr build();
  }

  public abstract Builder toBuilder();

  public static Builder newBuilder() {
    return new AutoValue_CelExpr.Builder()
        .setId(0)
        .setExprKind(AutoOneOf_CelExpr_ExprKind.notSet(new AutoValue_CelExpr_CelNotSet()));
  }

  /** Denotes the kind of the expression. An expression can only be of one kind. */
  @AutoOneOf(CelExpr.ExprKind.Kind.class)
  @Immutable
  public abstract static class ExprKind {

    /** Expression kind. */
    public enum Kind {
      NOT_SET,
      CONSTANT,
      IDENT,
      SELECT,
      CALL,
      CREATE_LIST,
      CREATE_STRUCT,
      COMPREHENSION,
    }

    public abstract ExprKind.Kind getKind();

    public abstract CelNotSet notSet();

    public abstract CelConstant constant();

    public abstract CelIdent ident();

    public abstract CelSelect select();

    public abstract CelCall call();

    public abstract CelCreateList createList();

    public abstract CelCreateStruct createStruct();

    public abstract CelComprehension comprehension();
  }

  /**
   * An unset expression.
   *
   * <p>As the name implies, this expression does nothing. This only exists to maintain
   * compatibility between Expr proto to native CelExpr conversion
   */
  @AutoValue
  @Immutable
  public abstract static class CelNotSet {}

  /** An identifier expression. e.g. `request`. */
  @AutoValue
  @Immutable
  public abstract static class CelIdent {
    /**
     * Required. Holds a single, unqualified identifier, possibly preceded by a '.'.
     *
     * <p>Qualified names are represented by the [Expr.Select][] expression.
     */
    public abstract String name();

    /** Builder for CelIdent. */
    @AutoValue.Builder
    public abstract static class Builder {

      public abstract Builder setName(String value);

      @CheckReturnValue
      public abstract CelIdent build();
    }

    public abstract Builder toBuilder();

    public static Builder newBuilder() {
      return new AutoValue_CelExpr_CelIdent.Builder();
    }
  }

  /** A field selection expression. e.g. `request.auth`. */
  @AutoValue
  @Immutable
  public abstract static class CelSelect {

    /**
     * Required. The target of the selection expression.
     *
     * <p>For example, in the select expression `request.auth`, the `request` portion of the
     * expression is the `operand`.
     */
    public abstract CelExpr operand();

    /**
     * Required. The name of the field to select.
     *
     * <p>For example, in the select expression `request.auth`, the `auth` portion of the expression
     * would be the `field`.
     */
    public abstract String field();

    /**
     * Whether the select is to be interpreted as a field presence test.
     *
     * <p>This results from the macro `has(request.auth)`.
     */
    public abstract boolean testOnly();

    /** Builder for CelSelect. */
    @AutoValue.Builder
    public abstract static class Builder {

      public abstract Builder setOperand(CelExpr value);

      public abstract Builder setField(String value);

      public abstract Builder setTestOnly(boolean value);

      @CheckReturnValue
      public abstract CelSelect build();
    }

    public abstract Builder toBuilder();

    public static Builder newBuilder() {
      return new AutoValue_CelExpr_CelSelect.Builder().setTestOnly(false);
    }
  }

  /**
   * A call expression, including calls to predefined functions and operators.
   *
   * <p>For example, `value == 10`, `size(map_value)`.
   */
  @AutoValue
  @Immutable
  public abstract static class CelCall {

    /**
     * The target of a method call-style expression.
     *
     * <p>For example, `x` in `x.f()`.
     */
    public abstract Optional<CelExpr> target();

    /** Required. The name of the function or method being called. */
    public abstract String function();

    public abstract ImmutableList<CelExpr> args();

    /** Builder for CelCall. */
    @AutoValue.Builder
    public abstract static class Builder {

      public abstract Builder setTarget(CelExpr value);

      public abstract Builder setFunction(String value);

      abstract ImmutableList.Builder<CelExpr> argsBuilder();

      @CanIgnoreReturnValue
      public Builder addArgs(CelExpr... args) {
        checkNotNull(args);
        return addArgs(Arrays.asList(args));
      }

      @CanIgnoreReturnValue
      public Builder addArgs(Iterable<CelExpr> args) {
        checkNotNull(args);
        this.argsBuilder().addAll(args);
        return this;
      }

      @CheckReturnValue
      public abstract CelCall build();
    }

    public abstract Builder toBuilder();

    public static Builder newBuilder() {
      return new AutoValue_CelExpr_CelCall.Builder();
    }
  }

  /**
   * A list creation expression.
   *
   * <p>Lists may either be homogenous, e.g. `[1, 2, 3]`, or heterogeneous, e.g. `dyn([1, 'hello',
   * 2.0])`
   */
  @AutoValue
  @Immutable
  public abstract static class CelCreateList {
    /** The elements part of the list */
    public abstract ImmutableList<CelExpr> elements();

    /**
     * The indices within the elements list which are marked as optional elements.
     *
     * <p>When an optional-typed value is present, the value it contains is included in the list. If
     * the optional-typed value is absent, the list element is omitted from the CreateList result.
     */
    public abstract ImmutableList<Integer> optionalIndices();

    /** Builder for CelCreateList. */
    @AutoValue.Builder
    public abstract static class Builder {
      abstract ImmutableList.Builder<CelExpr> elementsBuilder();

      abstract ImmutableList.Builder<Integer> optionalIndicesBuilder();

      @CanIgnoreReturnValue
      public CelCreateList.Builder addElements(CelExpr... elements) {
        checkNotNull(elements);
        return addElements(Arrays.asList(elements));
      }

      @CanIgnoreReturnValue
      public CelCreateList.Builder addElements(Iterable<CelExpr> elements) {
        checkNotNull(elements);
        this.elementsBuilder().addAll(elements);
        return this;
      }

      @CanIgnoreReturnValue
      public CelCreateList.Builder addOptionalIndices(Integer... indices) {
        checkNotNull(indices);
        return addOptionalIndices(Arrays.asList(indices));
      }

      @CanIgnoreReturnValue
      public CelCreateList.Builder addOptionalIndices(Iterable<Integer> indices) {
        checkNotNull(indices);
        this.optionalIndicesBuilder().addAll(indices);
        return this;
      }

      @CheckReturnValue
      public abstract CelCreateList build();
    }

    public abstract Builder toBuilder();

    public static Builder newBuilder() {
      return new AutoValue_CelExpr_CelCreateList.Builder();
    }
  }

  /**
   * A map or message creation expression.
   *
   * <p>Maps are constructed as `{'key_name': 'value'}`. Message construction is similar, but
   * prefixed with a type name and composed of field ids: `types.MyType{field_id: 'value'}`.
   */
  @AutoValue
  @Immutable
  public abstract static class CelCreateStruct {
    /** The type name of the message to be created, empty when creating map literals. */
    public abstract String messageName();

    /** The entries in the creation expression. */
    public abstract ImmutableList<Entry> entries();

    /** Builder for CelCreateStruct. */
    @AutoValue.Builder
    public abstract static class Builder {

      public abstract Builder setMessageName(String value);

      abstract ImmutableList.Builder<Entry> entriesBuilder();

      @CanIgnoreReturnValue
      public Builder addEntries(Entry... entries) {
        checkNotNull(entries);
        return addEntries(Arrays.asList(entries));
      }

      @CanIgnoreReturnValue
      public Builder addEntries(Iterable<Entry> entries) {
        checkNotNull(entries);
        this.entriesBuilder().addAll(entries);
        return this;
      }

      @CheckReturnValue
      public abstract CelCreateStruct build();
    }

    public abstract Builder toBuilder();

    public static Builder newBuilder() {
      return new AutoValue_CelExpr_CelCreateStruct.Builder().setMessageName("");
    }

    /** Represents an entry of the struct */
    @AutoValue
    @Immutable
    public abstract static class Entry {
      /**
       * Required. An id assigned to this node by the parser which is unique in a given expression
       * tree. This is used to associate type information and other attributes to the node.
       */
      public abstract long id();

      /** Entry key kind. */
      public abstract KeyKind keyKind();

      /**
       * Required. The value assigned to the key.
       *
       * <p>If the optional_entry field is true, the expression must resolve to an optional-typed
       * value. If the optional value is present, the key will be set; however, if the optional
       * value is absent, the key will be unset.
       */
      public abstract CelExpr value();

      /** Whether the key-value pair is optional. */
      public abstract boolean optionalEntry();

      /** Builder for CelCreateStruct.Entry. */
      @AutoValue.Builder
      public abstract static class Builder {

        public abstract Builder setId(long value);

        public abstract Builder setKeyKind(KeyKind value);

        public abstract Builder setValue(CelExpr value);

        public abstract Builder setOptionalEntry(boolean value);

        public Builder setMapKey(CelExpr mapKey) {
          return setKeyKind(AutoOneOf_CelExpr_CelCreateStruct_Entry_KeyKind.mapKey(mapKey));
        }

        public Builder setFieldKey(String fieldKey) {
          return setKeyKind(AutoOneOf_CelExpr_CelCreateStruct_Entry_KeyKind.fieldKey(fieldKey));
        }

        @CheckReturnValue
        public abstract Entry build();
      }

      public abstract Builder toBuilder();

      public static Builder newBuilder() {
        return new AutoValue_CelExpr_CelCreateStruct_Entry.Builder().setOptionalEntry(false);
      }

      /** Entry key kind. */
      @AutoOneOf(KeyKind.Kind.class)
      @Immutable
      public abstract static class KeyKind {
        public abstract KeyKind.Kind getKind();

        /** The field key for a message creator statement. */
        public abstract String fieldKey();

        /** The key expression for a map creation statement. */
        public abstract CelExpr mapKey();

        /** Denotes Entry Key kind. */
        public enum Kind {
          FIELD_KEY,
          MAP_KEY
        }
      }
    }
  }

  /**
   * A comprehension expression applied to a list or map.
   *
   * <p>Comprehensions are not part of the core syntax, but enabled with macros. A macro matches a
   * specific call signature within a parsed AST and replaces the call with an alternate AST block.
   * Macro expansion happens at parse time.
   *
   * <p>The following macros are supported within CEL:
   *
   * <p>Aggregate type macros may be applied to all elements in a list or all keys in a map:
   *
   * <p>`all`, `exists`, `exists_one` - test a predicate expression against the inputs and return
   * `true` if the predicate is satisfied for all, any, or only one value `list.all(x, x < 10)`.
   * `filter` - test a predicate expression against the inputs and return the subset of elements
   * which satisfy the predicate: `payments.filter(p, p > 1000)`. `map` - apply an expression to all
   * elements in the input and return the output aggregate type: `[1, 2, 3].map(i, i * i)`.
   *
   * <p>The `has(m.x)` macro tests whether the property `x` is present in struct `m`. The semantics
   * of this macro depend on the type of `m`. For proto2 messages `has(m.x)` is defined as 'defined,
   * but not set`. For proto3, the macro tests whether the property is set to its default. For map
   * and struct types, the macro tests whether the property `x` is defined on `m`.
   *
   * <p>Comprehension evaluation can be best visualized as the following pseudocode:
   */
  @AutoValue
  @Immutable
  public abstract static class CelComprehension {
    /** The name of the iteration variable. */
    public abstract String iterVar();

    /** The range over which var iterates. */
    public abstract CelExpr iterRange();

    /** The name of the variable used for accumulation of the result. */
    public abstract String accuVar();

    /** The initial value of the accumulator. */
    public abstract CelExpr accuInit();

    /**
     * An expression which can contain iter_var and accu_var.
     *
     * <p>Returns false when the result has been computed and may be used as a hint to short-circuit
     * the remainder of the comprehension.
     */
    public abstract CelExpr loopCondition();

    /**
     * An expression which can contain iter_var and accu_var.
     *
     * <p>Computes the next value of accu_var.
     */
    public abstract CelExpr loopStep();

    /**
     * An expression which can contain accu_var.
     *
     * <p>Computes the result.
     */
    public abstract CelExpr result();

    /** Builder for Comprehension. */
    @AutoValue.Builder
    public abstract static class Builder {

      public abstract Builder setIterVar(String value);

      public abstract Builder setIterRange(CelExpr value);

      public abstract Builder setAccuVar(String value);

      public abstract Builder setAccuInit(CelExpr value);

      public abstract Builder setLoopCondition(CelExpr value);

      public abstract Builder setLoopStep(CelExpr value);

      public abstract Builder setResult(CelExpr value);

      @CheckReturnValue
      public abstract CelComprehension build();
    }

    public abstract Builder toBuilder();

    public static Builder newBuilder() {
      return new AutoValue_CelExpr_CelComprehension.Builder();
    }
  }

  public static CelExpr ofNotSet(long id) {
    return newBuilder()
        .setId(id)
        .setExprKind(AutoOneOf_CelExpr_ExprKind.notSet(new AutoValue_CelExpr_CelNotSet()))
        .build();
  }

  public static CelExpr ofConstantExpr(long id, CelConstant celConstant) {
    return newBuilder()
        .setId(id)
        .setExprKind(AutoOneOf_CelExpr_ExprKind.constant(celConstant))
        .build();
  }

  public static CelExpr ofIdentExpr(long id, String identName) {
    return newBuilder()
        .setId(id)
        .setExprKind(
            AutoOneOf_CelExpr_ExprKind.ident(CelIdent.newBuilder().setName(identName).build()))
        .build();
  }

  public static CelExpr ofSelectExpr(
      long id, CelExpr operandExpr, String field, boolean isTestOnly) {
    return newBuilder()
        .setId(id)
        .setExprKind(
            AutoOneOf_CelExpr_ExprKind.select(
                CelSelect.newBuilder()
                    .setOperand(operandExpr)
                    .setField(field)
                    .setTestOnly(isTestOnly)
                    .build()))
        .build();
  }

  public static CelExpr ofCallExpr(
      long id, Optional<CelExpr> targetExpr, String function, ImmutableList<CelExpr> arguments) {

    CelCall.Builder celCallBuilder = CelCall.newBuilder().setFunction(function).addArgs(arguments);
    targetExpr.ifPresent(celCallBuilder::setTarget);
    return newBuilder()
        .setId(id)
        .setExprKind(AutoOneOf_CelExpr_ExprKind.call(celCallBuilder.build()))
        .build();
  }

  public static CelExpr ofCreateListExpr(
      long id, ImmutableList<CelExpr> elements, ImmutableList<Integer> optionalIndices) {
    return newBuilder()
        .setId(id)
        .setExprKind(
            AutoOneOf_CelExpr_ExprKind.createList(
                CelCreateList.newBuilder()
                    .addElements(elements)
                    .addOptionalIndices(optionalIndices)
                    .build()))
        .build();
  }

  public static CelExpr ofCreateStructExpr(
      long id, String messageName, ImmutableList<CelCreateStruct.Entry> entries) {
    return newBuilder()
        .setId(id)
        .setExprKind(
            AutoOneOf_CelExpr_ExprKind.createStruct(
                CelCreateStruct.newBuilder()
                    .setMessageName(messageName)
                    .addEntries(entries)
                    .build()))
        .build();
  }

  public static CelCreateStruct.Entry ofCreateStructFieldEntryExpr(
      long id, String fieldKey, CelExpr value, boolean isOptionalEntry) {
    return CelCreateStruct.Entry.newBuilder()
        .setId(id)
        .setFieldKey(fieldKey)
        .setValue(value)
        .setOptionalEntry(isOptionalEntry)
        .build();
  }

  public static CelCreateStruct.Entry ofCreateStructMapEntryExpr(
      long id, CelExpr mapKey, CelExpr value, boolean isOptionalEntry) {
    return CelCreateStruct.Entry.newBuilder()
        .setId(id)
        .setMapKey(mapKey)
        .setValue(value)
        .setOptionalEntry(isOptionalEntry)
        .build();
  }

  public static CelExpr ofComprehension(
      long id,
      String iterVar,
      CelExpr iterRange,
      String accuVar,
      CelExpr accuInit,
      CelExpr loopCondition,
      CelExpr loopStep,
      CelExpr result) {
    return newBuilder()
        .setId(id)
        .setExprKind(
            AutoOneOf_CelExpr_ExprKind.comprehension(
                CelComprehension.newBuilder()
                    .setIterVar(iterVar)
                    .setIterRange(iterRange)
                    .setAccuVar(accuVar)
                    .setAccuInit(accuInit)
                    .setLoopCondition(loopCondition)
                    .setLoopStep(loopStep)
                    .setResult(result)
                    .build()))
        .build();
  }
}
