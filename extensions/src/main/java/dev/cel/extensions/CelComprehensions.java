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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import dev.cel.checker.CelCheckerBuilder;
import dev.cel.common.CelFunctionDecl;
import dev.cel.common.CelIssue;
import dev.cel.common.CelOverloadDecl;
import dev.cel.common.ast.CelExpr;
import dev.cel.common.ast.CelExpr.ExprKind.Kind;
import dev.cel.common.types.MapType;
import dev.cel.common.types.TypeParamType;
import dev.cel.compiler.CelCompilerLibrary;
import dev.cel.parser.CelMacro;
import dev.cel.parser.CelMacroExprFactory;
import dev.cel.parser.CelParserBuilder;
import dev.cel.runtime.CelFunctionBinding;
import dev.cel.runtime.CelRuntimeBuilder;
import dev.cel.runtime.CelRuntimeLibrary;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** */
final class CelComprehensions implements CelCompilerLibrary, CelRuntimeLibrary {

  private static final TypeParamType TYPE_PARAM_A = TypeParamType.create("A");
  private static final TypeParamType TYPE_PARAM_B = TypeParamType.create("B");
  private static final MapType MAP_OF_AB = MapType.create(TYPE_PARAM_A, TYPE_PARAM_B);
  private static final String CEL_NAMESPACE = "cel";
  private static final String MAP_INSERT_FUNCTION = "cel.@mapInsert";
  private static final String MAP_INSERT_OVERLOAD_MAP_MAP = "@mapInsert_map_map";
  private static final String MAP_INSERT_OVERLOAD_KEY_VALUE = "@mapInsert_map_key_value";

  public enum Function {
    MAP_INSERT(
        CelFunctionDecl.newFunctionDeclaration(
            MAP_INSERT_FUNCTION,
            CelOverloadDecl.newGlobalOverload(
                MAP_INSERT_OVERLOAD_MAP_MAP, "map insertion", MAP_OF_AB, MAP_OF_AB, MAP_OF_AB),
            CelOverloadDecl.newGlobalOverload(
                MAP_INSERT_OVERLOAD_KEY_VALUE,
                "map insertion",
                MAP_OF_AB,
                MAP_OF_AB,
                TYPE_PARAM_A,
                TYPE_PARAM_B)),
        CelFunctionBinding.from(
            MAP_INSERT_OVERLOAD_MAP_MAP, Map.class, Map.class, CelComprehensions::mapInsert),
        CelFunctionBinding.from(
            MAP_INSERT_OVERLOAD_KEY_VALUE,
            ImmutableList.of(Map.class, Object.class, Object.class),
            CelComprehensions::mapInsert));

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

  private final ImmutableSet<Function> functions;

  CelComprehensions() {
    this.functions = ImmutableSet.copyOf(Function.values());
  }

  CelComprehensions(Set<Function> functions) {
    this.functions = ImmutableSet.copyOf(functions);
  }

  @Override
  public void setParserOptions(CelParserBuilder parserBuilder) {
    parserBuilder.addMacros(
        CelMacro.newReceiverVarArgMacro("mapInsert", CelComprehensions::expandMapInsertMacro));
  }

  @Override
  public void setCheckerOptions(CelCheckerBuilder checkerBuilder) {
    functions.forEach(function -> checkerBuilder.addFunctionDeclarations(function.functionDecl));
  }

  @Override
  public void setRuntimeOptions(CelRuntimeBuilder runtimeBuilder) {
    functions.forEach(function -> runtimeBuilder.addFunctionBindings(function.functionBindings));
  }

  private static Map<Object, Object> mapInsert(Map<?, ?> first, Map<?, ?> second) {
    // TODO: return a mutable map instead of an actual copy.
    Map<Object, Object> result = Maps.newHashMapWithExpectedSize(first.size() + second.size());
    result.putAll(first);
    for (Map.Entry<?, ?> entry : second.entrySet()) {
      if (result.containsKey(entry.getKey())) {
        throw new IllegalArgumentException(
            String.format("insert failed: key '%s' already exists", entry.getKey()));
      }
      result.put(entry.getKey(), entry.getValue());
    }
    return result;
  }

  private static Map<Object, Object> mapInsert(Object[] args) {
    Map<?, ?> map = (Map<?, ?>) args[0];
    Object key = args[1];
    Object value = args[2];
    // TODO: return a mutable map instead of an actual copy.
    if (map.containsKey(key)) {
      throw new IllegalArgumentException(
          String.format("insert failed: key '%s' already exists", key));
    }
    Map<Object, Object> result = Maps.newHashMapWithExpectedSize(map.size() + 1);
    result.putAll(map);
    result.put(key, value);
    return result;
  }

  private static Optional<CelExpr> expandMapInsertMacro(
      CelMacroExprFactory exprFactory, CelExpr target, ImmutableList<CelExpr> arguments) {
    if (!isTargetInNamespace(target)) {
      // Return empty to indicate that we're not interested in expanding this macro, and
      // that the parser should default to a function call on the receiver.
      return Optional.empty();
    }

    switch (arguments.size()) {
      case 2:
        Optional<CelExpr> invalidArg =
            checkInvalidArgument(exprFactory, MAP_INSERT_OVERLOAD_MAP_MAP, arguments);
        if (invalidArg.isPresent()) {
          return invalidArg;
        }

        return Optional.of(exprFactory.newGlobalCall(MAP_INSERT_FUNCTION, arguments));
      case 3:
        invalidArg = checkInvalidArgument(exprFactory, MAP_INSERT_OVERLOAD_KEY_VALUE, arguments);
        if (invalidArg.isPresent()) {
          return invalidArg;
        }
        
        return Optional.of(exprFactory.newGlobalCall(MAP_INSERT_FUNCTION, arguments));
      default:
        return newError(
            exprFactory,
            "cel.mapInsert() arguments must be either two maps or a map and a key-value pair",
            target);
    }
  }

  private static boolean isTargetInNamespace(CelExpr target) {
    return target.exprKind().getKind().equals(Kind.IDENT)
        && target.ident().name().equals(CEL_NAMESPACE);
  }

  private static Optional<CelExpr> checkInvalidArgument(
      CelMacroExprFactory exprFactory, String functionName, List<CelExpr> arguments) {

    if (functionName.equals(MAP_INSERT_OVERLOAD_MAP_MAP)) {
      for (CelExpr arg : arguments) {
        if (arg.exprKind().getKind() != Kind.MAP) {
          return newError(
              exprFactory, String.format("Invalid argument '%s': must be a map", arg), arg);
        }
      }
    }
    if (functionName.equals(MAP_INSERT_OVERLOAD_KEY_VALUE)) {
      if (arguments.get(0).exprKind().getKind() != Kind.MAP) {
        return newError(
            exprFactory,
            String.format("Invalid argument '%s': must be a map", arguments.get(0)),
            arguments.get(0));
      }
      if (arguments.get(1).exprKind().getKind() != Kind.CONSTANT) {
        return newError(
            exprFactory,
            String.format("'%s' is an invalid Key", arguments.get(1)),
            arguments.get(1));
      }
    }

    return Optional.empty();
  }

  private static Optional<CelExpr> newError(
      CelMacroExprFactory exprFactory, String errorMessage, CelExpr argument) {
    return Optional.of(
        exprFactory.reportError(
            CelIssue.formatError(exprFactory.getSourceLocation(argument), errorMessage)));
  }
}
