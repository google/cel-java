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

package dev.cel.extensions;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.primitives.Ints;
import com.google.common.primitives.UnsignedLong;
import com.google.protobuf.Duration;
import com.google.protobuf.Message;
import com.google.protobuf.Timestamp;
import dev.cel.checker.CelCheckerBuilder;
import dev.cel.common.CelFunctionDecl;
import dev.cel.common.CelIssue;
import dev.cel.common.CelOptions;
import dev.cel.common.CelOverloadDecl;
import dev.cel.common.CelVarDecl;
import dev.cel.common.ast.CelExpr;
import dev.cel.common.types.ListType;
import dev.cel.common.types.MapType;
import dev.cel.common.types.OptionalType;
import dev.cel.common.types.SimpleType;
import dev.cel.common.types.TypeParamType;
import dev.cel.common.types.TypeType;
import dev.cel.common.values.CelByteString;
import dev.cel.common.values.NullValue;
import dev.cel.compiler.CelCompilerLibrary;
import dev.cel.parser.CelMacro;
import dev.cel.parser.CelMacroExprFactory;
import dev.cel.parser.CelParserBuilder;
import dev.cel.parser.Operator;
import dev.cel.runtime.CelFunctionBinding;
import dev.cel.runtime.CelInternalRuntimeLibrary;
import dev.cel.runtime.CelRuntimeBuilder;
import dev.cel.runtime.RuntimeEquality;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Internal implementation of CEL optional values. */
public final class CelOptionalLibrary
    implements CelCompilerLibrary, CelInternalRuntimeLibrary, CelExtensionLibrary.FeatureSet {

  /** Enumerations of function names used for supporting optionals. */
  public enum Function {
    VALUE("value"),
    HAS_VALUE("hasValue"),
    OPTIONAL_NONE("optional.none"),
    OPTIONAL_OF("optional.of"),
    OPTIONAL_UNWRAP("optional.unwrap"),
    OPTIONAL_OF_NON_ZERO_VALUE("optional.ofNonZeroValue"),
    OR("or"),
    OR_VALUE("orValue"),
    FIRST("first"),
    LAST("last");

    private final String functionName;

    public String getFunction() {
      return functionName;
    }

    Function(String functionName) {
      this.functionName = functionName;
    }
  }

  private static final CelExtensionLibrary<CelOptionalLibrary> LIBRARY =
      new CelExtensionLibrary<CelOptionalLibrary>() {
        final TypeParamType paramTypeK = TypeParamType.create("K");
        final TypeParamType paramTypeV = TypeParamType.create("V");
        final OptionalType optionalTypeV = OptionalType.create(paramTypeV);
        final ListType listTypeV = ListType.create(paramTypeV);
        final MapType mapTypeKv = MapType.create(paramTypeK, paramTypeV);

        private final CelOptionalLibrary version0 =
            new CelOptionalLibrary(
                0,
                ImmutableSet.of(
                    CelFunctionDecl.newFunctionDeclaration(
                        Function.OPTIONAL_OF.getFunction(),
                        CelOverloadDecl.newGlobalOverload(
                            "optional_of", optionalTypeV, paramTypeV)),
                    CelFunctionDecl.newFunctionDeclaration(
                        Function.OPTIONAL_OF_NON_ZERO_VALUE.getFunction(),
                        CelOverloadDecl.newGlobalOverload(
                            "optional_ofNonZeroValue", optionalTypeV, paramTypeV)),
                    CelFunctionDecl.newFunctionDeclaration(
                        Function.OPTIONAL_NONE.getFunction(),
                        CelOverloadDecl.newGlobalOverload("optional_none", optionalTypeV)),
                    CelFunctionDecl.newFunctionDeclaration(
                        Function.VALUE.getFunction(),
                        CelOverloadDecl.newMemberOverload(
                            "optional_value", paramTypeV, optionalTypeV)),
                    CelFunctionDecl.newFunctionDeclaration(
                        Function.HAS_VALUE.getFunction(),
                        CelOverloadDecl.newMemberOverload(
                            "optional_hasValue", SimpleType.BOOL, optionalTypeV)),
                    CelFunctionDecl.newFunctionDeclaration(
                        Function.OPTIONAL_UNWRAP.getFunction(),
                        CelOverloadDecl.newGlobalOverload(
                            "optional_unwrap_list", listTypeV, ListType.create(optionalTypeV))),
                    // Note: Implementation of "or" and "orValue" are special-cased inside the
                    // interpreter. Hence, their bindings are not provided here.
                    CelFunctionDecl.newFunctionDeclaration(
                        "or",
                        CelOverloadDecl.newMemberOverload(
                            "optional_or_optional", optionalTypeV, optionalTypeV, optionalTypeV)),
                    CelFunctionDecl.newFunctionDeclaration(
                        "orValue",
                        CelOverloadDecl.newMemberOverload(
                            "optional_orValue_value", paramTypeV, optionalTypeV, paramTypeV)),
                    // Note: Function bindings for optional field selection and indexer is defined
                    // in {@code StandardFunctions}.
                    CelFunctionDecl.newFunctionDeclaration(
                        Operator.OPTIONAL_SELECT.getFunction(),
                        CelOverloadDecl.newGlobalOverload(
                            "select_optional_field",
                            optionalTypeV,
                            SimpleType.DYN,
                            SimpleType.STRING)),
                    CelFunctionDecl.newFunctionDeclaration(
                        Operator.OPTIONAL_INDEX.getFunction(),
                        CelOverloadDecl.newGlobalOverload(
                            "list_optindex_optional_int", optionalTypeV, listTypeV, SimpleType.INT),
                        CelOverloadDecl.newGlobalOverload(
                            "optional_list_optindex_optional_int",
                            optionalTypeV,
                            OptionalType.create(listTypeV),
                            SimpleType.INT),
                        CelOverloadDecl.newGlobalOverload(
                            "map_optindex_optional_value", optionalTypeV, mapTypeKv, paramTypeK),
                        CelOverloadDecl.newGlobalOverload(
                            "optional_map_optindex_optional_value",
                            optionalTypeV,
                            OptionalType.create(mapTypeKv),
                            paramTypeK)),
                    // Index overloads to accommodate using an optional value as the operand
                    CelFunctionDecl.newFunctionDeclaration(
                        Operator.INDEX.getFunction(),
                        CelOverloadDecl.newGlobalOverload(
                            "optional_list_index_int",
                            optionalTypeV,
                            OptionalType.create(listTypeV),
                            SimpleType.INT),
                        CelOverloadDecl.newGlobalOverload(
                            "optional_map_index_value",
                            optionalTypeV,
                            OptionalType.create(mapTypeKv),
                            paramTypeK))),
                ImmutableSet.of(
                    CelMacro.newReceiverMacro("optMap", 2, CelOptionalLibrary::expandOptMap)),
                ImmutableSet.of(
                    // Type declaration for optional_type -> type(optional_type(V))
                    CelVarDecl.newVarDeclaration(
                        OptionalType.NAME, TypeType.create(optionalTypeV))));

        private final CelOptionalLibrary version1 =
            new CelOptionalLibrary(
                1,
                version0.functions,
                ImmutableSet.<CelMacro>builder()
                    .addAll(version0.macros)
                    .add(
                        CelMacro.newReceiverMacro(
                            "optFlatMap", 2, CelOptionalLibrary::expandOptFlatMap))
                    .build(),
                version0.variables);

        private final CelOptionalLibrary version2 =
            new CelOptionalLibrary(
                2,
                ImmutableSet.<CelFunctionDecl>builder()
                    .addAll(version1.functions)
                    .add(
                        CelFunctionDecl.newFunctionDeclaration(
                            Function.FIRST.functionName,
                            CelOverloadDecl.newMemberOverload(
                                "optional_list_first",
                                "Return the first value in a list if present, otherwise"
                                    + " optional.none()",
                                optionalTypeV,
                                listTypeV)),
                        CelFunctionDecl.newFunctionDeclaration(
                            Function.LAST.functionName,
                            CelOverloadDecl.newMemberOverload(
                                "optional_list_last",
                                "Return the last value in a list if present, otherwise"
                                    + " optional.none()",
                                optionalTypeV,
                                listTypeV)))
                    .build(),
                version1.macros,
                version1.variables);

        @Override
        public String name() {
          return "optional";
        }

        @Override
        public ImmutableSet<CelOptionalLibrary> versions() {
          return ImmutableSet.of(version0, version1, version2);
        }
      };

  static CelExtensionLibrary<CelOptionalLibrary> library() {
    return LIBRARY;
  }

  // TODO migrate from this constant to the CelExtensions.optional()
  public static final CelOptionalLibrary INSTANCE = CelOptionalLibrary.library().latest();

  private static final String UNUSED_ITER_VAR = "#unused";

  private final int version;
  private final ImmutableSet<CelFunctionDecl> functions;
  private final ImmutableSet<CelMacro> macros;
  private final ImmutableSet<CelVarDecl> variables;

  CelOptionalLibrary(
      int version,
      ImmutableSet<CelFunctionDecl> functions,
      ImmutableSet<CelMacro> macros,
      ImmutableSet<CelVarDecl> variables) {
    this.version = version;
    this.functions = functions;
    this.macros = macros;
    this.variables = variables;
  }

  @Override
  public int version() {
    return version;
  }

  @Override
  public ImmutableSet<CelFunctionDecl> functions() {
    return functions;
  }

  @Override
  public ImmutableSet<CelVarDecl> variables() {
    return variables;
  }

  @Override
  public ImmutableSet<CelMacro> macros() {
    return macros;
  }

  @Override
  public void setParserOptions(CelParserBuilder parserBuilder) {
    if (!parserBuilder.getOptions().enableOptionalSyntax()) {
      // Enable optional syntax by default if the optional library has been added to the
      // environment
      parserBuilder.setOptions(
          parserBuilder.getOptions().toBuilder().enableOptionalSyntax(true).build());
    }
    parserBuilder.addMacros(macros());
  }

  @Override
  public void setCheckerOptions(CelCheckerBuilder checkerBuilder) {
    checkerBuilder.addVarDeclarations(variables());
    checkerBuilder.addFunctionDeclarations(functions());
  }

  @Override
  public void setRuntimeOptions(CelRuntimeBuilder runtimeBuilder) {
    throw new UnsupportedOperationException("Unsupported");
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  @Override
  public void setRuntimeOptions(
      CelRuntimeBuilder runtimeBuilder, RuntimeEquality runtimeEquality, CelOptions celOptions) {
    runtimeBuilder.addFunctionBindings(
        CelFunctionBinding.from("optional_of", Object.class, Optional::of),
        CelFunctionBinding.from(
            "optional_ofNonZeroValue",
            Object.class,
            val -> {
              if (isZeroValue(val)) {
                return Optional.empty();
              }
              return Optional.of(val);
            }),
        CelFunctionBinding.from(
            "optional_unwrap_list", Collection.class, CelOptionalLibrary::elideOptionalCollection),
        CelFunctionBinding.from("optional_none", ImmutableList.of(), val -> Optional.empty()),
        CelFunctionBinding.from("optional_value", Object.class, val -> ((Optional<?>) val).get()),
        CelFunctionBinding.from(
            "optional_hasValue", Object.class, val -> ((Optional<?>) val).isPresent()),
        CelFunctionBinding.from(
            "select_optional_field", // This only handles map selection. Proto selection is
            // special cased inside the interpreter.
            Map.class,
            String.class,
            runtimeEquality::findInMap),
        CelFunctionBinding.from(
            "map_optindex_optional_value", Map.class, Object.class, runtimeEquality::findInMap),
        CelFunctionBinding.from(
            "optional_map_optindex_optional_value",
            Optional.class,
            Object.class,
            (Optional optionalMap, Object key) ->
                indexOptionalMap(optionalMap, key, runtimeEquality)),
        CelFunctionBinding.from(
            "optional_map_index_value",
            Optional.class,
            Object.class,
            (Optional optionalMap, Object key) ->
                indexOptionalMap(optionalMap, key, runtimeEquality)),
        CelFunctionBinding.from(
            "optional_list_index_int",
            Optional.class,
            Long.class,
            CelOptionalLibrary::indexOptionalList),
        CelFunctionBinding.from(
            "list_optindex_optional_int",
            List.class,
            Long.class,
            (List list, Long index) -> {
              int castIndex = Ints.checkedCast(index);
              if (castIndex < 0 || castIndex >= list.size()) {
                return Optional.empty();
              }
              return Optional.of(list.get(castIndex));
            }),
        CelFunctionBinding.from(
            "optional_list_optindex_optional_int",
            Optional.class,
            Long.class,
            CelOptionalLibrary::indexOptionalList));

    if (version >= 2) {
      runtimeBuilder.addFunctionBindings(
          CelFunctionBinding.from(
              "optional_list_first", Collection.class, CelOptionalLibrary::listOptionalFirst),
          CelFunctionBinding.from(
              "optional_list_last", Collection.class, CelOptionalLibrary::listOptionalLast));
    }
  }

  private static ImmutableList<Object> elideOptionalCollection(Collection<Optional<Object>> list) {
    return list.stream().filter(Optional::isPresent).map(Optional::get).collect(toImmutableList());
  }

  // TODO: This will need to be adapted to handle an intermediate CelValue instead,
  // akin to Zeroer interface in Go. Currently, it is unable to handle zero-values for a
  // user-defined custom type.
  private static boolean isZeroValue(Object val) {
    if (val instanceof Boolean) {
      return !((Boolean) val);
    } else if (val instanceof Long) {
      return (Long) val == 0L;
    } else if (val instanceof Double) {
      return (Double) val == 0.0d;
    } else if (val instanceof String) {
      return ((String) val).isEmpty();
    } else if (val instanceof UnsignedLong) {
      return val.equals(UnsignedLong.ZERO);
    } else if (val instanceof Collection<?>) {
      return ((Collection<?>) val).isEmpty();
    } else if (val instanceof Map<?, ?>) {
      return ((Map<?, ?>) val).isEmpty();
    } else if (val instanceof CelByteString) {
      return ((CelByteString) val).isEmpty();
    } else if (val instanceof Duration) {
      return val.equals(((Duration) val).getDefaultInstanceForType());
    } else if (val instanceof Timestamp) {
      return val.equals(((Timestamp) val).getDefaultInstanceForType());
    } else if (val instanceof Message) {
      return val.equals(((Message) val).getDefaultInstanceForType());
    } else if (val instanceof NullValue) {
      // A null value always represents an absent value
      return true;
    }

    // Unknown. Assume that it is non-zero.
    return false;
  }

  private static Optional<CelExpr> expandOptMap(
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
                  "optMap() variable name must be a simple identifier")));
    }
    CelExpr mapExpr = checkNotNull(arguments.get(1));
    String varName = varIdent.ident().name();

    return Optional.of(
        exprFactory.newGlobalCall(
            Operator.CONDITIONAL.getFunction(),
            exprFactory.newReceiverCall(Function.HAS_VALUE.getFunction(), target),
            exprFactory.newGlobalCall(
                Function.OPTIONAL_OF.getFunction(),
                exprFactory.fold(
                    UNUSED_ITER_VAR,
                    exprFactory.newList(),
                    varName,
                    exprFactory.newReceiverCall(
                        Function.VALUE.getFunction(), exprFactory.copy(target)),
                    exprFactory.newBoolLiteral(true),
                    exprFactory.newIdentifier(varName),
                    mapExpr)),
            exprFactory.newGlobalCall(Function.OPTIONAL_NONE.getFunction())));
  }

  private static Optional<CelExpr> expandOptFlatMap(
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
                  "optFlatMap() variable name must be a simple identifier")));
    }
    CelExpr mapExpr = checkNotNull(arguments.get(1));
    String varName = varIdent.ident().name();

    return Optional.of(
        exprFactory.newGlobalCall(
            Operator.CONDITIONAL.getFunction(),
            exprFactory.newReceiverCall(Function.HAS_VALUE.getFunction(), target),
            exprFactory.fold(
                UNUSED_ITER_VAR,
                exprFactory.newList(),
                varName,
                exprFactory.newReceiverCall(Function.VALUE.getFunction(), exprFactory.copy(target)),
                exprFactory.newBoolLiteral(true),
                exprFactory.newIdentifier(varName),
                mapExpr),
            exprFactory.newGlobalCall(Function.OPTIONAL_NONE.getFunction())));
  }

  private static Object indexOptionalMap(
      Optional<?> optionalMap, Object key, RuntimeEquality runtimeEquality) {
    if (!optionalMap.isPresent()) {
      return Optional.empty();
    }

    Map<?, ?> map = (Map<?, ?>) optionalMap.get();

    return runtimeEquality.findInMap(map, key);
  }

  private static Object indexOptionalList(Optional<?> optionalList, long index) {
    if (!optionalList.isPresent()) {
      return Optional.empty();
    }
    List<?> list = (List<?>) optionalList.get();
    int castIndex = Ints.checkedCast(index);
    if (castIndex < 0 || castIndex >= list.size()) {
      return Optional.empty();
    }
    return Optional.of(list.get(castIndex));
  }

  @SuppressWarnings("rawtypes")
  private static Object listOptionalFirst(Collection<Object> list) {
    if (list.isEmpty()) {
      return Optional.empty();
    }
    if (list instanceof List) {
      return Optional.ofNullable(((List) list).get(0));
    }
    return Optional.ofNullable(Iterables.getFirst(list, null));
  }

  private static Object listOptionalLast(Collection<Object> list) {
    return Optional.ofNullable(Iterables.getLast(list, null));
  }
}
