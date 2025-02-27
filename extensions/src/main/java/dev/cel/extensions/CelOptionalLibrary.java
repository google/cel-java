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
import com.google.common.primitives.UnsignedLong;
import com.google.protobuf.ByteString;
import com.google.protobuf.Duration;
import com.google.protobuf.Message;
import com.google.protobuf.NullValue;
import com.google.protobuf.Timestamp;
import dev.cel.checker.CelCheckerBuilder;
import dev.cel.common.CelFunctionDecl;
import dev.cel.common.CelIssue;
import dev.cel.common.CelOverloadDecl;
import dev.cel.common.CelVarDecl;
import dev.cel.common.ast.CelExpr;
import dev.cel.common.types.ListType;
import dev.cel.common.types.MapType;
import dev.cel.common.types.OptionalType;
import dev.cel.common.types.SimpleType;
import dev.cel.common.types.TypeParamType;
import dev.cel.common.types.TypeType;
import dev.cel.compiler.CelCompilerLibrary;
import dev.cel.parser.CelMacro;
import dev.cel.parser.CelMacroExprFactory;
import dev.cel.parser.CelParserBuilder;
import dev.cel.parser.Operator;
import dev.cel.runtime.CelFunctionBinding;
import dev.cel.runtime.CelRuntimeBuilder;
import dev.cel.runtime.CelRuntimeLibrary;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;

/** Internal implementation of CEL optional values. */
public final class CelOptionalLibrary implements CelCompilerLibrary, CelRuntimeLibrary {
  public static final CelOptionalLibrary INSTANCE = new CelOptionalLibrary();

  /** Enumerations of function names used for supporting optionals. */
  public enum Function {
    VALUE("value"),
    HAS_VALUE("hasValue"),
    OPTIONAL_NONE("optional.none"),
    OPTIONAL_OF("optional.of"),
    OPTIONAL_UNWRAP("optional.unwrap"),
    OPTIONAL_OF_NON_ZERO_VALUE("optional.ofNonZeroValue"),
    OR("or"),
    OR_VALUE("orValue");
    private final String functionName;

    public String getFunction() {
      return functionName;
    }

    Function(String functionName) {
      this.functionName = functionName;
    }
  }

  private static final String UNUSED_ITER_VAR = "#unused";

  @Override
  public void setParserOptions(CelParserBuilder parserBuilder) {
    if (!parserBuilder.getOptions().enableOptionalSyntax()) {
      // Enable optional syntax by default if the optional library has been added to the
      // environment
      parserBuilder.setOptions(
          parserBuilder.getOptions().toBuilder().enableOptionalSyntax(true).build());
    }
    parserBuilder.addMacros(
        CelMacro.newReceiverMacro("optMap", 2, CelOptionalLibrary::expandOptMap));
    parserBuilder.addMacros(
        CelMacro.newReceiverMacro("optFlatMap", 2, CelOptionalLibrary::expandOptFlatMap));
  }

  @Override
  public void setCheckerOptions(CelCheckerBuilder checkerBuilder) {
    TypeParamType paramTypeK = TypeParamType.create("K");
    TypeParamType paramTypeV = TypeParamType.create("V");
    OptionalType optionalTypeV = OptionalType.create(paramTypeV);
    ListType listTypeV = ListType.create(paramTypeV);
    MapType mapTypeKv = MapType.create(paramTypeK, paramTypeV);

    // Type declaration for optional_type -> type(optional_type(V))
    checkerBuilder.addVarDeclarations(
        CelVarDecl.newVarDeclaration(OptionalType.NAME, TypeType.create(optionalTypeV)));

    checkerBuilder.addFunctionDeclarations(
        CelFunctionDecl.newFunctionDeclaration(
            Function.OPTIONAL_OF.getFunction(),
            CelOverloadDecl.newGlobalOverload("optional_of", optionalTypeV, paramTypeV)),
        CelFunctionDecl.newFunctionDeclaration(
            Function.OPTIONAL_OF_NON_ZERO_VALUE.getFunction(),
            CelOverloadDecl.newGlobalOverload(
                "optional_ofNonZeroValue", optionalTypeV, paramTypeV)),
        CelFunctionDecl.newFunctionDeclaration(
            Function.OPTIONAL_NONE.getFunction(),
            CelOverloadDecl.newGlobalOverload("optional_none", optionalTypeV)),
        CelFunctionDecl.newFunctionDeclaration(
            Function.VALUE.getFunction(),
            CelOverloadDecl.newMemberOverload("optional_value", paramTypeV, optionalTypeV)),
        CelFunctionDecl.newFunctionDeclaration(
            Function.HAS_VALUE.getFunction(),
            CelOverloadDecl.newMemberOverload("optional_hasValue", SimpleType.BOOL, optionalTypeV)),
        CelFunctionDecl.newFunctionDeclaration(
            Function.OPTIONAL_UNWRAP.getFunction(),
            CelOverloadDecl.newGlobalOverload(
                "optional_unwrap_list", listTypeV, ListType.create(optionalTypeV))),
        // Note: Implementation of "or" and "orValue" are special-cased inside the interpreter.
        // Hence, their bindings are not provided here.
        CelFunctionDecl.newFunctionDeclaration(
            "or",
            CelOverloadDecl.newMemberOverload(
                "optional_or_optional", optionalTypeV, optionalTypeV, optionalTypeV)),
        CelFunctionDecl.newFunctionDeclaration(
            "orValue",
            CelOverloadDecl.newMemberOverload(
                "optional_orValue_value", paramTypeV, optionalTypeV, paramTypeV)),
        // Note: Function bindings for optional field selection and indexer is defined in
        // {@code StandardFunctions}.
        CelFunctionDecl.newFunctionDeclaration(
            Operator.OPTIONAL_SELECT.getFunction(),
            CelOverloadDecl.newGlobalOverload(
                "select_optional_field", optionalTypeV, SimpleType.DYN, SimpleType.STRING)),
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
                paramTypeK)));
  }

  @Override
  @SuppressWarnings("unchecked")
  public void setRuntimeOptions(CelRuntimeBuilder runtimeBuilder) {
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
            "optional_hasValue", Object.class, val -> ((Optional<?>) val).isPresent()));
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
    } else if (val instanceof ByteString) {
      return ((ByteString) val).size() == 0;
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

  private CelOptionalLibrary() {}
}
