// Copyright 2025 Google LLC
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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import dev.cel.checker.CelCheckerBuilder;
import dev.cel.common.CelFunctionDecl;
import dev.cel.common.CelIssue;
import dev.cel.common.CelOptions;
import dev.cel.common.CelOverloadDecl;
import dev.cel.common.ast.CelExpr;
import dev.cel.common.types.MapType;
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
import java.util.Map;
import java.util.Optional;

/** Internal implementation of CEL two variable comprehensions extensions. */
final class CelComprehensionsExtensions
    implements CelCompilerLibrary, CelInternalRuntimeLibrary, CelExtensionLibrary.FeatureSet {

  private static final String MAP_INSERT_FUNCTION = "cel.@mapInsert";
  private static final String MAP_INSERT_OVERLOAD_MAP_MAP = "cel_@mapInsert_map_map";
  private static final String MAP_INSERT_OVERLOAD_KEY_VALUE = "cel_@mapInsert_map_key_value";
  private static final TypeParamType TYPE_PARAM_K = TypeParamType.create("K");
  private static final TypeParamType TYPE_PARAM_V = TypeParamType.create("V");
  private static final MapType MAP_KV_TYPE = MapType.create(TYPE_PARAM_K, TYPE_PARAM_V);

  enum Function {
    MAP_INSERT(
        CelFunctionDecl.newFunctionDeclaration(
            MAP_INSERT_FUNCTION,
            CelOverloadDecl.newGlobalOverload(
                MAP_INSERT_OVERLOAD_MAP_MAP,
                "Returns a map that's the result of merging given two maps.",
                MAP_KV_TYPE,
                MAP_KV_TYPE,
                MAP_KV_TYPE),
            CelOverloadDecl.newGlobalOverload(
                MAP_INSERT_OVERLOAD_KEY_VALUE,
                "Adds the given key-value pair to the map.",
                MAP_KV_TYPE,
                MAP_KV_TYPE,
                TYPE_PARAM_K,
                TYPE_PARAM_V)));

    private final CelFunctionDecl functionDecl;

    String getFunction() {
      return functionDecl.name();
    }

    Function(CelFunctionDecl functionDecl) {
      this.functionDecl = functionDecl;
    }
  }

  private static final CelExtensionLibrary<CelComprehensionsExtensions> LIBRARY =
      new CelExtensionLibrary<CelComprehensionsExtensions>() {
        private final CelComprehensionsExtensions version0 = new CelComprehensionsExtensions();

        @Override
        public String name() {
          return "comprehensions";
        }

        @Override
        public ImmutableSet<CelComprehensionsExtensions> versions() {
          return ImmutableSet.of(version0);
        }
      };

  static CelExtensionLibrary<CelComprehensionsExtensions> library() {
    return LIBRARY;
  }

  private final ImmutableSet<Function> functions;

  CelComprehensionsExtensions() {
    this.functions = ImmutableSet.copyOf(Function.values());
  }

  @Override
  public void setCheckerOptions(CelCheckerBuilder checkerBuilder) {
    functions.forEach(function -> checkerBuilder.addFunctionDeclarations(function.functionDecl));
  }

  @Override
  public void setRuntimeOptions(CelRuntimeBuilder runtimeBuilder) {
    throw new UnsupportedOperationException("Unsupported");
  }

  @Override
  public void setRuntimeOptions(
      CelRuntimeBuilder runtimeBuilder, RuntimeEquality runtimeEquality, CelOptions celOptions) {
    for (Function function : functions) {
      for (CelOverloadDecl overload : function.functionDecl.overloads()) {
        switch (overload.overloadId()) {
          case MAP_INSERT_OVERLOAD_MAP_MAP:
            runtimeBuilder.addFunctionBindings(
                CelFunctionBinding.from(
                    MAP_INSERT_OVERLOAD_MAP_MAP,
                    Map.class,
                    Map.class,
                    (map1, map2) -> mapInsertMap(map1, map2, runtimeEquality)));
            break;
          case MAP_INSERT_OVERLOAD_KEY_VALUE:
            runtimeBuilder.addFunctionBindings(
                CelFunctionBinding.from(
                    MAP_INSERT_OVERLOAD_KEY_VALUE,
                    ImmutableList.of(Map.class, Object.class, Object.class),
                    args -> mapInsertKeyValue(args, runtimeEquality)));
            break;
          default:
            // Nothing to add.
        }
      }
    }
  }

  @Override
  public int version() {
    return 0;
  }

  @Override
  public ImmutableSet<CelMacro> macros() {
    return ImmutableSet.of(
        CelMacro.newReceiverMacro(
            Operator.ALL.getFunction(), 3, CelComprehensionsExtensions::expandAllMacro),
        CelMacro.newReceiverMacro(
            Operator.EXISTS.getFunction(), 3, CelComprehensionsExtensions::expandExistsMacro),
        CelMacro.newReceiverMacro(
            Operator.EXISTS_ONE.getFunction(),
            3,
            CelComprehensionsExtensions::expandExistsOneMacro),
        CelMacro.newReceiverMacro(
            "transformList", 3, CelComprehensionsExtensions::transformListMacro),
        CelMacro.newReceiverMacro(
            "transformList", 4, CelComprehensionsExtensions::transformListMacro),
        CelMacro.newReceiverMacro(
            "transformMap", 3, CelComprehensionsExtensions::transformMapMacro),
        CelMacro.newReceiverMacro(
            "transformMap", 4, CelComprehensionsExtensions::transformMapMacro),
        CelMacro.newReceiverMacro(
            "transformMapEntry", 3, CelComprehensionsExtensions::transformMapEntryMacro),
        CelMacro.newReceiverMacro(
            "transformMapEntry", 4, CelComprehensionsExtensions::transformMapEntryMacro));
  }

  @Override
  public void setParserOptions(CelParserBuilder parserBuilder) {
    parserBuilder.addMacros(macros());
  }

  // TODO: Implement a more efficient map insertion based on mutability once mutable
  // maps are supported in Java stack.
  private static ImmutableMap<Object, Object> mapInsertMap(
      Map<?, ?> targetMap, Map<?, ?> mapToMerge, RuntimeEquality equality) {
    ImmutableMap.Builder<Object, Object> resultBuilder =
        ImmutableMap.builderWithExpectedSize(targetMap.size() + mapToMerge.size());

    for (Map.Entry<?, ?> entry : mapToMerge.entrySet()) {
      if (equality.findInMap(targetMap, entry.getKey()).isPresent()) {
        throw new IllegalArgumentException(
            String.format("insert failed: key '%s' already exists", entry.getKey()));
      } else {
        resultBuilder.put(entry.getKey(), entry.getValue());
      }
    }
    return resultBuilder.putAll(targetMap).buildOrThrow();
  }

  private static ImmutableMap<Object, Object> mapInsertKeyValue(
      Object[] args, RuntimeEquality equality) {
    Map<?, ?> map = (Map<?, ?>) args[0];
    Object key = args[1];
    Object value = args[2];

    if (equality.findInMap(map, key).isPresent()) {
      throw new IllegalArgumentException(
          String.format("insert failed: key '%s' already exists", key));
    }

    ImmutableMap.Builder<Object, Object> builder =
        ImmutableMap.builderWithExpectedSize(map.size() + 1);
    return builder.put(key, value).putAll(map).buildOrThrow();
  }

  private static Optional<CelExpr> expandAllMacro(
      CelMacroExprFactory exprFactory, CelExpr target, ImmutableList<CelExpr> arguments) {
    checkNotNull(exprFactory);
    checkNotNull(target);
    checkArgument(arguments.size() == 3);
    CelExpr arg0 = validatedIterationVariable(exprFactory, arguments.get(0));
    if (arg0.exprKind().getKind() != CelExpr.ExprKind.Kind.IDENT) {
      return Optional.of(arg0);
    }
    CelExpr arg1 = validatedIterationVariable(exprFactory, arguments.get(1));
    if (arg1.exprKind().getKind() != CelExpr.ExprKind.Kind.IDENT) {
      return Optional.of(arg1);
    }
    CelExpr arg2 = checkNotNull(arguments.get(2));
    CelExpr accuInit = exprFactory.newBoolLiteral(true);
    CelExpr condition =
        exprFactory.newGlobalCall(
            Operator.NOT_STRICTLY_FALSE.getFunction(),
            exprFactory.newIdentifier(exprFactory.getAccumulatorVarName()));
    CelExpr step =
        exprFactory.newGlobalCall(
            Operator.LOGICAL_AND.getFunction(),
            exprFactory.newIdentifier(exprFactory.getAccumulatorVarName()),
            arg2);
    CelExpr result = exprFactory.newIdentifier(exprFactory.getAccumulatorVarName());
    return Optional.of(
        exprFactory.fold(
            arg0.ident().name(),
            arg1.ident().name(),
            target,
            exprFactory.getAccumulatorVarName(),
            accuInit,
            condition,
            step,
            result));
  }

  private static Optional<CelExpr> expandExistsMacro(
      CelMacroExprFactory exprFactory, CelExpr target, ImmutableList<CelExpr> arguments) {
    checkNotNull(exprFactory);
    checkNotNull(target);
    checkArgument(arguments.size() == 3);
    CelExpr arg0 = validatedIterationVariable(exprFactory, arguments.get(0));
    if (arg0.exprKind().getKind() != CelExpr.ExprKind.Kind.IDENT) {
      return Optional.of(arg0);
    }
    CelExpr arg1 = validatedIterationVariable(exprFactory, arguments.get(1));
    if (arg1.exprKind().getKind() != CelExpr.ExprKind.Kind.IDENT) {
      return Optional.of(arg1);
    }
    CelExpr arg2 = checkNotNull(arguments.get(2));
    CelExpr accuInit = exprFactory.newBoolLiteral(false);
    CelExpr condition =
        exprFactory.newGlobalCall(
            Operator.NOT_STRICTLY_FALSE.getFunction(),
            exprFactory.newGlobalCall(
                Operator.LOGICAL_NOT.getFunction(),
                exprFactory.newIdentifier(exprFactory.getAccumulatorVarName())));
    CelExpr step =
        exprFactory.newGlobalCall(
            Operator.LOGICAL_OR.getFunction(),
            exprFactory.newIdentifier(exprFactory.getAccumulatorVarName()),
            arg2);
    CelExpr result = exprFactory.newIdentifier(exprFactory.getAccumulatorVarName());
    return Optional.of(
        exprFactory.fold(
            arg0.ident().name(),
            arg1.ident().name(),
            target,
            exprFactory.getAccumulatorVarName(),
            accuInit,
            condition,
            step,
            result));
  }

  private static Optional<CelExpr> expandExistsOneMacro(
      CelMacroExprFactory exprFactory, CelExpr target, ImmutableList<CelExpr> arguments) {
    checkNotNull(exprFactory);
    checkNotNull(target);
    checkArgument(arguments.size() == 3);
    CelExpr arg0 = validatedIterationVariable(exprFactory, arguments.get(0));
    if (arg0.exprKind().getKind() != CelExpr.ExprKind.Kind.IDENT) {
      return Optional.of(arg0);
    }
    CelExpr arg1 = validatedIterationVariable(exprFactory, arguments.get(1));
    if (arg1.exprKind().getKind() != CelExpr.ExprKind.Kind.IDENT) {
      return Optional.of(arg1);
    }
    CelExpr arg2 = checkNotNull(arguments.get(2));
    CelExpr accuInit = exprFactory.newIntLiteral(0);
    CelExpr condition = exprFactory.newBoolLiteral(true);
    CelExpr step =
        exprFactory.newGlobalCall(
            Operator.CONDITIONAL.getFunction(),
            arg2,
            exprFactory.newGlobalCall(
                Operator.ADD.getFunction(),
                exprFactory.newIdentifier(exprFactory.getAccumulatorVarName()),
                exprFactory.newIntLiteral(1)),
            exprFactory.newIdentifier(exprFactory.getAccumulatorVarName()));
    CelExpr result =
        exprFactory.newGlobalCall(
            Operator.EQUALS.getFunction(),
            exprFactory.newIdentifier(exprFactory.getAccumulatorVarName()),
            exprFactory.newIntLiteral(1));
    return Optional.of(
        exprFactory.fold(
            arg0.ident().name(),
            arg1.ident().name(),
            target,
            exprFactory.getAccumulatorVarName(),
            accuInit,
            condition,
            step,
            result));
  }

  private static Optional<CelExpr> transformListMacro(
      CelMacroExprFactory exprFactory, CelExpr target, ImmutableList<CelExpr> arguments) {
    checkNotNull(exprFactory);
    checkNotNull(target);
    checkArgument(arguments.size() == 3 || arguments.size() == 4);
    CelExpr arg0 = validatedIterationVariable(exprFactory, arguments.get(0));
    if (arg0.exprKind().getKind() != CelExpr.ExprKind.Kind.IDENT) {
      return Optional.of(arg0);
    }
    CelExpr arg1 = validatedIterationVariable(exprFactory, arguments.get(1));
    if (arg1.exprKind().getKind() != CelExpr.ExprKind.Kind.IDENT) {
      return Optional.of(arg1);
    }
    CelExpr transform;
    CelExpr filter = null;
    if (arguments.size() == 4) {
      filter = checkNotNull(arguments.get(2));
      transform = checkNotNull(arguments.get(3));
    } else {
      transform = checkNotNull(arguments.get(2));
    }
    CelExpr accuInit = exprFactory.newList();
    CelExpr condition = exprFactory.newBoolLiteral(true);
    CelExpr step =
        exprFactory.newGlobalCall(
            Operator.ADD.getFunction(),
            exprFactory.newIdentifier(exprFactory.getAccumulatorVarName()),
            exprFactory.newList(transform));
    if (filter != null) {
      step =
          exprFactory.newGlobalCall(
              Operator.CONDITIONAL.getFunction(),
              filter,
              step,
              exprFactory.newIdentifier(exprFactory.getAccumulatorVarName()));
    }
    return Optional.of(
        exprFactory.fold(
            arg0.ident().name(),
            arg1.ident().name(),
            target,
            exprFactory.getAccumulatorVarName(),
            accuInit,
            condition,
            step,
            exprFactory.newIdentifier(exprFactory.getAccumulatorVarName())));
  }

  private static Optional<CelExpr> transformMapMacro(
      CelMacroExprFactory exprFactory, CelExpr target, ImmutableList<CelExpr> arguments) {
    checkNotNull(exprFactory);
    checkNotNull(target);
    checkArgument(arguments.size() == 3 || arguments.size() == 4);
    CelExpr arg0 = validatedIterationVariable(exprFactory, arguments.get(0));
    if (arg0.exprKind().getKind() != CelExpr.ExprKind.Kind.IDENT) {
      return Optional.of(arg0);
    }
    CelExpr arg1 = validatedIterationVariable(exprFactory, arguments.get(1));
    if (arg1.exprKind().getKind() != CelExpr.ExprKind.Kind.IDENT) {
      return Optional.of(arg1);
    }
    CelExpr transform;
    CelExpr filter = null;
    if (arguments.size() == 4) {
      filter = checkNotNull(arguments.get(2));
      transform = checkNotNull(arguments.get(3));
    } else {
      transform = checkNotNull(arguments.get(2));
    }
    CelExpr accuInit = exprFactory.newMap();
    CelExpr condition = exprFactory.newBoolLiteral(true);
    CelExpr step =
        exprFactory.newGlobalCall(
            MAP_INSERT_FUNCTION,
            exprFactory.newIdentifier(exprFactory.getAccumulatorVarName()),
            arg0,
            transform);
    if (filter != null) {
      step =
          exprFactory.newGlobalCall(
              Operator.CONDITIONAL.getFunction(),
              filter,
              step,
              exprFactory.newIdentifier(exprFactory.getAccumulatorVarName()));
    }
    return Optional.of(
        exprFactory.fold(
            arg0.ident().name(),
            arg1.ident().name(),
            target,
            exprFactory.getAccumulatorVarName(),
            accuInit,
            condition,
            step,
            exprFactory.newIdentifier(exprFactory.getAccumulatorVarName())));
  }

  private static Optional<CelExpr> transformMapEntryMacro(
      CelMacroExprFactory exprFactory, CelExpr target, ImmutableList<CelExpr> arguments) {
    checkNotNull(exprFactory);
    checkNotNull(target);
    checkArgument(arguments.size() == 3 || arguments.size() == 4);
    CelExpr arg0 = validatedIterationVariable(exprFactory, arguments.get(0));
    if (arg0.exprKind().getKind() != CelExpr.ExprKind.Kind.IDENT) {
      return Optional.of(arg0);
    }
    CelExpr arg1 = validatedIterationVariable(exprFactory, arguments.get(1));
    if (arg1.exprKind().getKind() != CelExpr.ExprKind.Kind.IDENT) {
      return Optional.of(arg1);
    }
    CelExpr transform;
    CelExpr filter = null;
    if (arguments.size() == 4) {
      filter = checkNotNull(arguments.get(2));
      transform = checkNotNull(arguments.get(3));
    } else {
      transform = checkNotNull(arguments.get(2));
    }
    CelExpr accuInit = exprFactory.newMap();
    CelExpr condition = exprFactory.newBoolLiteral(true);
    CelExpr step =
        exprFactory.newGlobalCall(
            MAP_INSERT_FUNCTION,
            exprFactory.newIdentifier(exprFactory.getAccumulatorVarName()),
            transform);
    if (filter != null) {
      step =
          exprFactory.newGlobalCall(
              Operator.CONDITIONAL.getFunction(),
              filter,
              step,
              exprFactory.newIdentifier(exprFactory.getAccumulatorVarName()));
    }
    return Optional.of(
        exprFactory.fold(
            arg0.ident().name(),
            arg1.ident().name(),
            target,
            exprFactory.getAccumulatorVarName(),
            accuInit,
            condition,
            step,
            exprFactory.newIdentifier(exprFactory.getAccumulatorVarName())));
  }

  private static CelExpr validatedIterationVariable(
      CelMacroExprFactory exprFactory, CelExpr argument) {

    CelExpr arg = checkNotNull(argument);
    if (arg.exprKind().getKind() != CelExpr.ExprKind.Kind.IDENT) {
      return reportArgumentError(exprFactory, arg);
    } else if (arg.exprKind().ident().name().equals("__result__")) {
      return reportAccumulatorOverwriteError(exprFactory, arg);
    } else {
      return arg;
    }
  }

  private static CelExpr reportArgumentError(CelMacroExprFactory exprFactory, CelExpr argument) {
    return exprFactory.reportError(
        CelIssue.formatError(
            exprFactory.getSourceLocation(argument), "The argument must be a simple name"));
  }

  private static CelExpr reportAccumulatorOverwriteError(
      CelMacroExprFactory exprFactory, CelExpr argument) {
    return exprFactory.reportError(
        CelIssue.formatError(
            exprFactory.getSourceLocation(argument),
            String.format(
                "The iteration variable %s overwrites accumulator variable",
                argument.ident().name())));
  }
}
