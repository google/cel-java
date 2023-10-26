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

package codelab.solutions;

import static dev.cel.common.CelFunctionDecl.newFunctionDeclaration;
import static dev.cel.common.CelOverloadDecl.newMemberOverload;

import com.google.common.collect.ImmutableList;
import com.google.rpc.context.AttributeContext.Request;
import dev.cel.common.CelAbstractSyntaxTree;
import dev.cel.common.CelValidationException;
import dev.cel.common.types.MapType;
import dev.cel.common.types.SimpleType;
import dev.cel.common.types.StructTypeReference;
import dev.cel.common.types.TypeParamType;
import dev.cel.compiler.CelCompiler;
import dev.cel.compiler.CelCompilerFactory;
import dev.cel.runtime.CelEvaluationException;
import dev.cel.runtime.CelRuntime;
import dev.cel.runtime.CelRuntime.CelFunctionBinding;
import dev.cel.runtime.CelRuntimeFactory;
import java.util.Map;

/**
 * Exercise4 demonstrates how to extend CEL with custom functions.
 *
 * <p>Declare a "contains" member function on map types that return a boolean indicating whether the
 * map contains the key-value pair.
 */
final class Exercise4 {

  /**
   * Compiles the input expression.
   *
   * @throws IllegalArgumentException If the expression is malformed due to syntactic or semantic
   *     errors.
   */
  CelAbstractSyntaxTree compile(String expression) {
    // Useful components of the type-signature for 'contains'.
    TypeParamType typeParamA = TypeParamType.create("A");
    TypeParamType typeParamB = TypeParamType.create("B");
    MapType mapTypeAB = MapType.create(typeParamA, typeParamB);

    CelCompiler celCompiler =
        CelCompilerFactory.standardCelCompilerBuilder()
            .addVar("request", StructTypeReference.create(Request.getDescriptor().getFullName()))
            .addMessageTypes(Request.getDescriptor())
            .setResultType(SimpleType.BOOL)
            .addFunctionDeclarations(
                newFunctionDeclaration(
                    "contains",
                    newMemberOverload(
                        "map_contains_key_value",
                        SimpleType.BOOL,
                        ImmutableList.of(mapTypeAB, typeParamA, typeParamB))))
            .build();

    try {
      return celCompiler.compile(expression).getAst();
    } catch (CelValidationException e) {
      throw new IllegalArgumentException("Failed to compile expression.", e);
    }
  }

  /**
   * Evaluates the compiled AST with the user provided parameter values.
   *
   * @throws IllegalArgumentException If the compiled expression in AST fails to evaluate.
   */
  Object eval(CelAbstractSyntaxTree ast, Map<String, ?> parameterValues) {
    CelRuntime celRuntime =
        CelRuntimeFactory.standardCelRuntimeBuilder()
            .addFunctionBindings(
                CelFunctionBinding.from(
                    "map_contains_key_value",
                    ImmutableList.of(Map.class, String.class, Object.class),
                    Exercise4::mapContainsKeyValue))
            .build();

    try {
      CelRuntime.Program program = celRuntime.createProgram(ast);
      return program.eval(parameterValues);
    } catch (CelEvaluationException e) {
      throw new IllegalArgumentException("Evaluation error has occurred.", e);
    }
  }

  /**
   * mapContainsKeyValue implements the custom function: map.contains(key, value) -> bool.
   *
   * @param args, where:
   *     <ol>
   *       <li>args[0] is the map
   *       <li>args[1] is the key
   *       <li>args[2] is the value at the key
   *     </ol>
   *
   * @return true If the key was found AND the value at the key equals to the value being checked
   */
  @SuppressWarnings("unchecked") // Type-checker guarantees casting safety.
  private static boolean mapContainsKeyValue(Object[] args) {
    // The declaration of the function ensures that only arguments which match
    // the mapContainsKey signature will be provided to the function.
    Map<String, Object> map = (Map<String, Object>) args[0];
    String key = (String) args[1];
    Object value = args[2];

    return map.containsKey(key) && map.containsValue(value);
  }
}
