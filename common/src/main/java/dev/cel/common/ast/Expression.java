// Copyright 2024 Google LLC
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

import dev.cel.common.annotations.Internal;
import java.util.Optional;

/**
 * An abstract representation of a common expression.
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
@Internal
public interface Expression {

  /**
   * Required. An id assigned to this node by the parser which is unique in a given expression tree.
   * This is used to associate type information and other attributes to a node in the parse tree.
   */
  long id();

  /** Represents the enumeration value for the underlying expression kind. */
  CelExpr.ExprKind.Kind getKind();

  /** Gets the underlying constant expression. */
  CelConstant constant();

  /** Gets the underlying identifier expression. */
  Ident ident();

  /** Gets the underlying call expression. */
  <E extends Expression> Call<E> call();

  /** Gets the underlying identifier expression. */
  <E extends Expression> List<E> list();

  /** Gets the underlying select expression. */
  <E extends Expression> Select<E> select();

  /** Gets the underlying struct expression. */
  <E extends Expression> Struct<Struct.Entry<E>> struct();

  /** Gets the underlying map expression. */
  <E extends Expression> Map<Map.Entry<E>> map();

  /** Gets the underlying comprehension expression. */
  <E extends Expression> Comprehension<E> comprehension();

  /** An identifier expression. e.g. `request`. */
  interface Ident {

    /**
     * Required. Holds a single, unqualified identifier, possibly preceded by a '.'.
     *
     * <p>Qualified names are represented by the [Expr.Select][] expression.
     */
    String name();
  }

  /** A call expression, including calls to predefined functions and operators. */
  interface Call<E extends Expression> {
    /**
     * The target of a method call-style expression.
     *
     * <p>For example, `x` in `x.f()`.
     */
    Optional<E> target();

    /** Required. The name of the function or method being called. */
    String function();

    /**
     * Arguments to the call.
     *
     * <p>For example, `foo` in `f(foo)` or `x.f(foo)`.
     */
    java.util.List<E> args();
  }

  /**
   * A list creation expression.
   *
   * <p>Lists may either be homogenous, e.g. `[1, 2, 3]`, or heterogeneous, e.g. `dyn([1, 'hello',
   * 2.0])`
   */
  interface List<E extends Expression> {

    /** The elements part of the list */
    java.util.List<E> elements();

    /**
     * The indices within the elements list which are marked as optional elements.
     *
     * <p>When an optional-typed value is present, the value it contains is included in the list. If
     * the optional-typed value is absent, the list element is omitted from the list result.
     */
    java.util.List<Integer> optionalIndices();
  }

  /** A field selection expression. e.g. `request.auth`. */
  interface Select<E extends Expression> {
    /**
     * Required. The target of the selection expression.
     *
     * <p>For example, in the select expression `request.auth`, the `request` portion of the
     * expression is the `operand`.
     */
    E operand();

    /**
     * Required. The name of the field to select.
     *
     * <p>For example, in the select expression `request.auth`, the `auth` portion of the expression
     * would be the `field`.
     */
    String field();

    /**
     * Whether the select is to be interpreted as a field presence test.
     *
     * <p>This results from the macro `has(request.auth)`.
     */
    boolean testOnly();
  }

  /**
   * A message creation expression.
   *
   * <p>Messages are constructed with a type name and composed of field ids: `types.MyType{field_id:
   * 'value'}`.
   */
  interface Struct<E extends Expression.Struct.Entry<?>> {

    /** The type name of the message to be created, empty when creating map literals. */
    String messageName();

    /** The entries in the creation expression. */
    java.util.List<E> entries();

    /** Represents an entry of the struct */
    interface Entry<T extends Expression> {
      /**
       * Required. An id assigned to this node by the parser which is unique in a given expression
       * tree. This is used to associate type information and other attributes to the node.
       */
      long id();

      /** Entry key kind. */
      String fieldKey();

      /**
       * Required. The value assigned to the key.
       *
       * <p>If the optional_entry field is true, the expression must resolve to an optional-typed
       * value. If the optional value is present, the key will be set; however, if the optional
       * value is absent, the key will be unset.
       */
      T value();

      /** Whether the key-value pair is optional. */
      boolean optionalEntry();
    }
  }

  /**
   * A map creation expression.
   *
   * <p>Maps are constructed as `{'key_name': 'value'}`.
   */
  interface Map<E extends Expression.Map.Entry<?>> {

    java.util.List<E> entries();

    /** Represents an entry of the map. */
    interface Entry<T extends Expression> {
      /**
       * Required. An id assigned to this node by the parser which is unique in a given expression
       * tree. This is used to associate type information and other attributes to the node.
       */
      long id();

      /** Required. The key. */
      T key();

      /**
       * Required. The value assigned to the key.
       *
       * <p>If the optional_entry field is true, the expression must resolve to an optional-typed
       * value. If the optional value is present, the key will be set; however, if the optional
       * value is absent, the key will be unset.
       */
      T value();

      boolean optionalEntry();
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
  interface Comprehension<E extends Expression> {
    /** The name of the iteration variable. */
    String iterVar();

    /** The name of the second iteration variable. */
    String iterVar2();

    /** The range over which var iterates. */
    E iterRange();

    /** The name of the variable used for accumulation of the result. */
    String accuVar();

    /** The initial value of the accumulator. */
    E accuInit();

    /**
     * An expression which can contain iter_var and accu_var.
     *
     * <p>Returns false when the result has been computed and may be used as a hint to short-circuit
     * the remainder of the comprehension.
     */
    E loopCondition();

    /**
     * An expression which can contain iter_var and accu_var.
     *
     * <p>Computes the next value of accu_var.
     */
    E loopStep();

    /**
     * An expression which can contain accu_var.
     *
     * <p>Computes the result.
     */
    E result();
  }
}
