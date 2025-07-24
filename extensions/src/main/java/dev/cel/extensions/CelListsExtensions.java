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

package dev.cel.extensions;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import dev.cel.checker.CelCheckerBuilder;
import dev.cel.common.CelFunctionDecl;
import dev.cel.common.CelIssue;
import dev.cel.common.CelOptions;
import dev.cel.common.CelOverloadDecl;
import dev.cel.common.ast.CelExpr;
import dev.cel.common.internal.ComparisonFunctions;
import dev.cel.common.types.ListType;
import dev.cel.common.types.SimpleType;
import dev.cel.common.types.TypeParamType;
import dev.cel.compiler.CelCompilerLibrary;
import dev.cel.parser.CelMacro;
import dev.cel.parser.CelMacroExprFactory;
import dev.cel.parser.CelParserBuilder;
import dev.cel.parser.Operator;
import dev.cel.runtime.CelFunctionBinding;
import dev.cel.runtime.CelInternalRuntimeLibrary;
import dev.cel.runtime.CelRuntimeBuilder;
import dev.cel.runtime.RuntimeEquality;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Internal implementation of CEL lists extensions. */
final class CelListsExtensions
    implements CelCompilerLibrary, CelInternalRuntimeLibrary, CelExtensionLibrary.FeatureSet {

  @SuppressWarnings({"unchecked"}) // Unchecked: Type-checker guarantees casting safety.
  public enum Function {
    // Note! Creating dependencies on the outer class may cause circular initialization issues.
    SLICE(
        CelFunctionDecl.newFunctionDeclaration(
            "slice",
            CelOverloadDecl.newMemberOverload(
                "list_slice",
                "Returns a new sub-list using the indices provided",
                ListType.create(TypeParamType.create("T")),
                ListType.create(TypeParamType.create("T")),
                SimpleType.INT,
                SimpleType.INT)),
        CelFunctionBinding.from(
            "list_slice",
            ImmutableList.of(Collection.class, Long.class, Long.class),
            (args) -> {
              Collection<Object> target = (Collection<Object>) args[0];
              long from = (Long) args[1];
              long to = (Long) args[2];
              return CelListsExtensions.slice(target, from, to);
            })),
    FLATTEN(
        CelFunctionDecl.newFunctionDeclaration(
            "flatten",
            CelOverloadDecl.newMemberOverload(
                "list_flatten",
                "Flattens a list by a single level",
                ListType.create(TypeParamType.create("T")),
                ListType.create(ListType.create(TypeParamType.create("T")))),
            CelOverloadDecl.newMemberOverload(
                "list_flatten_list_int",
                "Flattens a list to the specified level. A negative depth value flattens the list"
                    + " recursively to its deepest level.",
                ListType.create(SimpleType.DYN),
                ListType.create(SimpleType.DYN),
                SimpleType.INT)),
        CelFunctionBinding.from("list_flatten", Collection.class, list -> flatten(list, 1)),
        CelFunctionBinding.from(
            "list_flatten_list_int", Collection.class, Long.class, CelListsExtensions::flatten)),
    RANGE(
        CelFunctionDecl.newFunctionDeclaration(
            "lists.range",
            CelOverloadDecl.newGlobalOverload(
                "lists_range",
                "Returns a list of integers from 0 to n-1.",
                ListType.create(SimpleType.INT),
                SimpleType.INT)),
        CelFunctionBinding.from("lists_range", Long.class, CelListsExtensions::genRange)),
    DISTINCT(
        CelFunctionDecl.newFunctionDeclaration(
            "distinct",
            CelOverloadDecl.newMemberOverload(
                "list_distinct",
                "Returns the distinct elements of a list",
                ListType.create(TypeParamType.create("T")),
                ListType.create(TypeParamType.create("T"))))),
    REVERSE(
        CelFunctionDecl.newFunctionDeclaration(
            "reverse",
            CelOverloadDecl.newMemberOverload(
                "list_reverse",
                "Returns the elements of a list in reverse order",
                ListType.create(TypeParamType.create("T")),
                ListType.create(TypeParamType.create("T")))),
        CelFunctionBinding.from(
            "list_reverse",
            Collection.class,
            CelListsExtensions::reverse)),
      SORT(
          CelFunctionDecl.newFunctionDeclaration(
              "sort",
              CelOverloadDecl.newMemberOverload(
                  "list_sort",
                  "Sorts a list with comparable elements.",
                  ListType.create(TypeParamType.create("T")),
                  ListType.create(TypeParamType.create("T"))))),
      SORT_BY(
          CelFunctionDecl.newFunctionDeclaration(
              "lists.@sortByAssociatedKeys",
              CelOverloadDecl.newGlobalOverload(
                  "list_sortByAssociatedKeys",
                  "Sorts a list by a key value. Used by the 'sortBy' macro",
                  ListType.create(TypeParamType.create("T")),
                  ListType.create(TypeParamType.create("T")))))
    ;

    private final CelFunctionDecl functionDecl;
    private final ImmutableSet<CelFunctionBinding> functionBindings;

    String getFunction() {
      return functionDecl.name();
    }

    Function(CelFunctionDecl functionDecl, CelFunctionBinding... functionBindings) {
      this.functionDecl = functionDecl;
      this.functionBindings = ImmutableSet.copyOf(functionBindings);
    }
  }

  private static final CelExtensionLibrary<CelListsExtensions> LIBRARY =
      new CelExtensionLibrary<CelListsExtensions>() {
        private final CelListsExtensions version0 =
            new CelListsExtensions(0, ImmutableSet.of(Function.SLICE));
        private final CelListsExtensions version1 =
            new CelListsExtensions(
                1,
                ImmutableSet.<Function>builder()
                    .addAll(version0.functions)
                    .add(Function.FLATTEN)
                    .build());
        private final CelListsExtensions version2 =
            new CelListsExtensions(
                2,
                ImmutableSet.<Function>builder()
                    .addAll(version1.functions)
                    .add(
                        Function.RANGE,
                        Function.DISTINCT,
                        Function.REVERSE,
                        Function.SORT,
                        Function.SORT_BY)
                    .build());

        @Override
        public String name() {
          return "lists";
        }

        @Override
        public ImmutableSet<CelListsExtensions> versions() {
          return ImmutableSet.of(version0, version1, version2);
        }
      };

  static CelExtensionLibrary<CelListsExtensions> library() {
    return LIBRARY;
  }

  private final int version;
  private final ImmutableSet<Function> functions;

  CelListsExtensions(Set<Function> functions) {
    this(-1, functions);
  }

  private CelListsExtensions(int version, Set<Function> functions) {
    this.version = version;
    this.functions = ImmutableSet.copyOf(functions);
  }

  @Override
  public int version() {
    return version;
  }

  @Override
  public ImmutableSet<CelFunctionDecl> functions() {
    return functions.stream().map(f -> f.functionDecl).collect(toImmutableSet());
  }

  @Override
  public ImmutableSet<CelMacro> macros() {
    if (version >= 2) {
      return ImmutableSet.of(
          CelMacro.newReceiverMacro("sortBy", 2, CelListsExtensions::sortByMacro));
    }
    return ImmutableSet.of();
  }

  @Override
  public void setParserOptions(CelParserBuilder parserBuilder) {
    parserBuilder.addMacros(macros());
  }

  @Override
  public void setCheckerOptions(CelCheckerBuilder checkerBuilder) {
    functions.forEach(function -> checkerBuilder.addFunctionDeclarations(function.functionDecl));
  }

  @Override
  public void setRuntimeOptions(CelRuntimeBuilder runtimeBuilder) {
    throw new UnsupportedOperationException("Unsupported");
  }

  @SuppressWarnings("unchecked")
  @Override
  public void setRuntimeOptions(CelRuntimeBuilder runtimeBuilder, RuntimeEquality runtimeEquality,
      CelOptions celOptions) {
    for (Function function : functions) {
      runtimeBuilder.addFunctionBindings(function.functionBindings);
      for (CelOverloadDecl overload : function.functionDecl.overloads()) {
        switch (overload.overloadId()) {
          case "list_distinct":
            runtimeBuilder.addFunctionBindings(
                CelFunctionBinding.from(
                    "list_distinct", Collection.class, (list) -> distinct(list, runtimeEquality)));
            break;
          case "list_sort":
            runtimeBuilder.addFunctionBindings(
                CelFunctionBinding.from(
                    "list_sort", Collection.class, (list) -> sort(list, celOptions)));
            break;
          case "list_sortByAssociatedKeys":
            runtimeBuilder.addFunctionBindings(
                CelFunctionBinding.from(
                    "list_sortByAssociatedKeys",
                    Collection.class,
                    (list) -> sortByAssociatedKeys(list, celOptions)));
            break;
          default:
            // Nothing to add
        }
      }
    }
  }

  private static ImmutableList<Object> slice(Collection<Object> list, long from, long to) {
    Preconditions.checkArgument(from >= 0 && to >= 0, "Negative indexes not supported");
    Preconditions.checkArgument(to >= from, "Start index must be less than or equal to end index");
    Preconditions.checkArgument(to <= list.size(), "List is length %s", list.size());
    if (list instanceof List) {
      List<Object> subList = ((List<Object>) list).subList((int) from, (int) to);
      if (subList instanceof ImmutableList) {
        return (ImmutableList<Object>) subList;
      }
      return ImmutableList.copyOf(subList);
    } else {
      ImmutableList.Builder<Object> builder = ImmutableList.builder();
      long index = 0;
      for (Iterator<Object> iterator = list.iterator(); iterator.hasNext(); index++) {
        Object element = iterator.next();
        if (index >= to) {
          break;
        }
        if (index >= from) {
          builder.add(element);
        }
      }
      return builder.build();
    }
  }

  @SuppressWarnings("unchecked")
  private static ImmutableList<Object> flatten(Collection<Object> list, long depth) {
    Preconditions.checkArgument(depth >= 0, "Level must be non-negative");
    ImmutableList.Builder<Object> builder = ImmutableList.builder();
    for (Object element : list) {
      if (!(element instanceof Collection) || depth == 0) {
        builder.add(element);
      } else {
        Collection<Object> listItem = (Collection<Object>) element;
        builder.addAll(flatten(listItem, depth - 1));
      }
    }

    return builder.build();
  }

  public static ImmutableList<Long> genRange(long end) {
    ImmutableList.Builder<Long> builder = ImmutableList.builder();
    for (long i = 0; i < end; i++) {
      builder.add(i);
    }
    return builder.build();
  }

  private static ImmutableList<Object> distinct(
      Collection<Object> list, RuntimeEquality runtimeEquality) {
    // TODO Optimize this method, which currently has the O(N^2) complexity.
    int size = list.size();
    ImmutableList.Builder<Object> builder = ImmutableList.builderWithExpectedSize(size);
    List<Object> theList;
    if (list instanceof List) {
      theList = (List<Object>) list;
    } else {
      theList = ImmutableList.copyOf(list);
    }
    for (int i = 0; i < size; i++) {
      Object element = theList.get(i);
      boolean found = false;
      for (int j = 0; j < i; j++) {
        if (runtimeEquality.objectEquals(element, theList.get(j))) {
          found = true;
          break;
        }
      }
      if (!found) {
        builder.add(element);
      }
    }
    return builder.build();
  }

  private static List<Object> reverse(Collection<Object> list) {
    if (list instanceof List) {
      return Lists.reverse((List<Object>) list);
    } else {
      ImmutableList.Builder<Object> builder = ImmutableList.builderWithExpectedSize(list.size());
      Object[] objects = list.toArray();
      for (int i = objects.length - 1; i >= 0; i--) {
        builder.add(objects[i]);
      }
      return builder.build();
    }
  }

  private static ImmutableList<Object> sort(Collection<Object> objects, CelOptions options) {
    return ImmutableList.sortedCopyOf(
        new CelObjectComparator(options.enableHeterogeneousNumericComparisons()), objects);
  }

  private static class CelObjectComparator implements Comparator<Object> {
    private final boolean enableHeterogeneousNumericComparisons;

    CelObjectComparator(boolean enableHeterogeneousNumericComparisons) {
      this.enableHeterogeneousNumericComparisons = enableHeterogeneousNumericComparisons;
    }

    @SuppressWarnings({"unchecked"})
    @Override
    public int compare(Object o1, Object o2) {
      if (o1 instanceof Number && o2 instanceof Number && enableHeterogeneousNumericComparisons) {
        return ComparisonFunctions.numericCompare((Number) o1, (Number) o2);
      }

      if (!(o1 instanceof Comparable)) {
        throw new IllegalArgumentException("List elements must be comparable");
      }
      if (o1.getClass() != o2.getClass()) {
        throw new IllegalArgumentException("List elements must have the same type");
      }
      return ((Comparable) o1).compareTo(o2);
    }
  }

  private static Optional<CelExpr> sortByMacro(
      CelMacroExprFactory exprFactory, CelExpr target, ImmutableList<CelExpr> arguments) {
    checkNotNull(exprFactory);
    checkNotNull(target);
    checkArgument(arguments.size() == 2);
    CelExpr varIdent = checkNotNull(arguments.get(0));
    if (varIdent.exprKind().getKind() != CelExpr.ExprKind.Kind.IDENT) {
      return Optional.of(
          exprFactory.reportError(
              CelIssue.formatError(
                  exprFactory.getSourceLocation(varIdent),
                  "sortBy(var, ...) variable name must be a simple identifier")));
    }

    String varName = varIdent.ident().name();
    CelExpr sortKeyExpr = checkNotNull(arguments.get(1));

    // Compute the key using the second argument of the `sortBy(e, key)` macro.
    // Combine the key and the value in a two-element list
    CelExpr step = exprFactory.newList(sortKeyExpr, varIdent);
    // Wrap the pair in another list in order to be able to use the `list+list` operator
    step = exprFactory.newList(step);
    // Append the key-value pair to the i
    step = exprFactory.newGlobalCall(
            Operator.ADD.getFunction(),
            exprFactory.newIdentifier(exprFactory.getAccumulatorVarName()),
            step);
    // Create an intermediate list and populate it with key-value pairs
    step = exprFactory.fold(
        varName,
        target,
        exprFactory.getAccumulatorVarName(),
        exprFactory.newList(),
        exprFactory.newBoolLiteral(true),   // Include all elements
        step,
        exprFactory.newIdentifier(exprFactory.getAccumulatorVarName()));
    // Finally, sort the list of key-value pairs and map it to a list of values
    step = exprFactory.newGlobalCall(
        Function.SORT_BY.getFunction(),
        step);

    return Optional.of(step);
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private static ImmutableList<Object> sortByAssociatedKeys(
      Collection<List<Object>> keyValuePairs, CelOptions options) {
    List<Object>[] array = keyValuePairs.toArray(new List[0]);
    Arrays.sort(
        array,
        new CelObjectByKeyComparator(
            new CelObjectComparator(options.enableHeterogeneousNumericComparisons())));
    ImmutableList.Builder<Object> builder = ImmutableList.builderWithExpectedSize(array.length);
    for (List<Object> pair : array) {
      builder.add(pair.get(1));
    }
    return builder.build();
  }

  private static class CelObjectByKeyComparator implements Comparator<Object> {
    private final CelObjectComparator keyComparator;

    CelObjectByKeyComparator(CelObjectComparator keyComparator) {
      this.keyComparator = keyComparator;
    }

    @SuppressWarnings({"unchecked"})
    @Override
    public int compare(Object o1, Object o2) {
      return keyComparator.compare(((List<Object>) o1).get(0), ((List<Object>) o2).get(0));
    }
  }
}
