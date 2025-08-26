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

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import com.google.testing.junit.testparameterinjector.TestParameter;
import com.google.testing.junit.testparameterinjector.TestParameterInjector;
import com.google.testing.junit.testparameterinjector.TestParameters;
import dev.cel.common.CelAbstractSyntaxTree;
import dev.cel.common.CelOptions;
import dev.cel.common.CelValidationException;
import dev.cel.common.types.SimpleType;
import dev.cel.common.types.TypeParamType;
import dev.cel.compiler.CelCompiler;
import dev.cel.compiler.CelCompilerFactory;
import dev.cel.parser.CelStandardMacro;
import dev.cel.parser.CelUnparser;
import dev.cel.parser.CelUnparserFactory;
import dev.cel.runtime.CelEvaluationException;
import dev.cel.runtime.CelRuntime;
import dev.cel.runtime.CelRuntimeFactory;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Test for {@link CelExtensions#comprehensions()} */
@RunWith(TestParameterInjector.class)
public class CelComprehensionsExtensionsTest {

  private static final CelOptions CEL_OPTIONS =
      CelOptions.current()
          // Enable macro call population for unparsing
          .populateMacroCalls(true)
          .build();

  private static final CelCompiler CEL_COMPILER =
      CelCompilerFactory.standardCelCompilerBuilder()
          .setOptions(CEL_OPTIONS)
          .setStandardMacros(CelStandardMacro.STANDARD_MACROS)
          .addLibraries(CelExtensions.comprehensions())
          .addLibraries(CelExtensions.lists())
          .addLibraries(CelOptionalLibrary.INSTANCE, CelExtensions.bindings())
          .build();
  private static final CelRuntime CEL_RUNTIME =
      CelRuntimeFactory.standardCelRuntimeBuilder()
          .addLibraries(CelOptionalLibrary.INSTANCE)
          .addLibraries(CelExtensions.lists())
          .build();

  private static final CelUnparser UNPARSER = CelUnparserFactory.newUnparser();

  @Test
  public void allMacro_twoVarComprehension_success(
      @TestParameter({
            // list.all()
            "[1, 2, 3, 4].all(i, v, i < 5 && v > 0)",
            "[1, 2, 3, 4].all(i, v, i < v)",
            "[1, 2, 3, 4].all(i, v, i > v) == false",
            "cel.bind(listA, [1, 2, 3, 4], cel.bind(listB, [1, 2, 3, 4, 5], listA.all(i, v,"
                + " listB[?i].hasValue() && listB[i] == v)))",
            "cel.bind(listA, [1, 2, 3, 4, 5, 6], cel.bind(listB, [1, 2, 3, 4, 5], listA.all(i, v,"
                + " listB[?i].hasValue() && listB[i] == v))) == false",
            // map.all()
            "{'hello': 'world', 'hello!': 'world'}.all(k, v, k.startsWith('hello') && v =="
                + " 'world')",
            "{'hello': 'world', 'hello!': 'worlds'}.all(k, v, k.startsWith('hello') &&"
                + " v.endsWith('world')) == false",
            "{'a': 1, 'b': 2}.all(k, v, k.startsWith('a') && v == 1) == false",
          })
          String expr)
      throws Exception {
    CelAbstractSyntaxTree ast = CEL_COMPILER.compile(expr).getAst();

    Object result = CEL_RUNTIME.createProgram(ast).eval();

    assertThat(result).isEqualTo(true);
  }

  @Test
  public void existsMacro_twoVarComprehension_success(
      @TestParameter({
            // list.exists()
            "[1, 2, 3, 4].exists(i, v, i > 2 && v < 5)",
            "[10, 1, 30].exists(i, v, i == v)",
            "[].exists(i, v, true) == false",
            "cel.bind(l, ['hello', 'world', 'hello!', 'worlds'], l.exists(i, v,"
                + " v.startsWith('hello') && l[?(i+1)].optMap(next,"
                + " next.endsWith('world')).orValue(false)))",
            // map.exists()
            "{'hello': 'world', 'hello!': 'worlds'}.exists(k, v, k.startsWith('hello') &&"
                + " v.endsWith('world'))",
            "{}.exists(k, v, true) == false",
            "{'a': 1, 'b': 2}.exists(k, v, v == 3) == false",
            "{'a': 'b', 'c': 'c'}.exists(k, v, k == v)"
          })
          String expr)
      throws Exception {
    CelAbstractSyntaxTree ast = CEL_COMPILER.compile(expr).getAst();

    Object result = CEL_RUNTIME.createProgram(ast).eval();

    assertThat(result).isEqualTo(true);
  }

  @Test
  public void exists_oneMacro_twoVarComprehension_success(
      @TestParameter({
            // list.exists_one()
            "[0, 5, 6].exists_one(i, v, i == v)",
            "[0, 1, 5].exists_one(i, v, i == v) == false",
            "[10, 11, 12].exists_one(i, v, i == v) == false",
            "cel.bind(l, ['hello', 'world', 'hello!', 'worlds'], l.exists_one(i, v,"
                + " v.startsWith('hello') && l[?(i+1)].optMap(next,"
                + " next.endsWith('world')).orValue(false)))",
            "cel.bind(l, ['hello', 'goodbye', 'hello!', 'goodbye'], l.exists_one(i, v,"
                + " v.startsWith('hello') && l[?(i+1)].optMap(next, next =="
                + " 'goodbye').orValue(false))) == false",
            // map.exists_one()
            "{'hello': 'world', 'hello!': 'worlds'}.exists_one(k, v, k.startsWith('hello') &&"
                + " v.endsWith('world'))",
            "{'hello': 'world', 'hello!': 'wow, world'}.exists_one(k, v, k.startsWith('hello') &&"
                + " v.endsWith('world')) == false",
            "{'a': 1, 'b': 1}.exists_one(k, v, v == 2) == false"
          })
          String expr)
      throws Exception {
    CelAbstractSyntaxTree ast = CEL_COMPILER.compile(expr).getAst();

    Object result = CEL_RUNTIME.createProgram(ast).eval();

    assertThat(result).isEqualTo(true);
  }

  @Test
  public void transformListMacro_twoVarComprehension_success(
      @TestParameter({
            // list.transformList()
            "[1, 2, 3].transformList(i, v, (i * v) + v) == [1, 4, 9]",
            "[1, 2, 3].transformList(i, v, i % 2 == 0, (i * v) + v) == [1, 9]",
            "[1, 2, 3].transformList(i, v, i > 0 && v < 3, (i * v) + v) == [4]",
            "[1, 2, 3].transformList(i, v, i % 2 == 0, (i * v) + v) == [1, 9]",
            "[1, 2, 3].transformList(i, v, (i * v) + v) == [1, 4, 9]",
            "[-1, -2, -3].transformList(i, v, [1, 2].transformList(i, v, i + v)) == [[1, 3], [1,"
                + " 3], [1, 3]]",
            // map.transformList()
            "{'greeting': 'hello', 'farewell': 'goodbye'}.transformList(k, _, k).sort() =="
                + " ['farewell', 'greeting']",
            "{'greeting': 'hello', 'farewell': 'goodbye'}.transformList(_, v, v).sort() =="
                + " ['goodbye', 'hello']"
          })
          String expr)
      throws Exception {
    CelAbstractSyntaxTree ast = CEL_COMPILER.compile(expr).getAst();

    Object result = CEL_RUNTIME.createProgram(ast).eval();

    assertThat(result).isEqualTo(true);
  }

  @Test
  public void unparseAST_twoVarComprehension(
      @TestParameter({
            "cel.bind(listA, [1, 2, 3, 4], cel.bind(listB, [1, 2, 3, 4, 5], listA.all(i, v,"
                + " listB[?i].hasValue() && listB[i] == v)))",
            "[1, 2, 3, 4].exists(i, v, i > 2 && v < 5)",
            "{\"a\": 1, \"b\": 1}.exists_one(k, v, v == 2) == false",
            "[1, 2, 3].transformList(i, v, i > 0 && v < 3, i * v + v) == [4]",
            "[1, 2, 2].transformList(i, v, i / 2 == 1)",
            "{\"a\": \"b\", \"c\": \"d\"}.exists_one(k, v, k == \"b\" || v == \"b\")",
            "{\"a\": \"b\", \"c\": \"d\"}.exists(k, v, k == \"b\" || v == \"b\")",
            "[null, null, \"hello\", string].all(i, v, i == 0 || type(v) != int)"
          })
          String expr)
      throws Exception {
    CelAbstractSyntaxTree ast = CEL_COMPILER.compile(expr).getAst();
    String unparsed = UNPARSER.unparse(ast);
    assertThat(unparsed).isEqualTo(expr);
  }

  @Test
  @TestParameters(
      "{expr: '[].exists_one(i.j, k, i.j < k)', err: 'The argument must be a simple name'}")
  @TestParameters(
      "{expr: '[].exists_one(i, [k], i < [k])', err: 'The argument must be a simple name'}")
  @TestParameters(
      "{expr: '1.exists_one(j, j < 5)', err: 'cannot be range of a comprehension (must be list,"
          + " map, or dynamic)'}")
  @TestParameters(
      "{expr: '1.exists_one(j, k, j < k)', err: 'cannot be range of a comprehension (must be list,"
          + " map, or dynamic)'}")
  @TestParameters(
      "{expr: '[].transformList(__result__, i, __result__ < i)', err: 'The iteration variable"
          + " __result__ overwrites accumulator variable'}")
  @TestParameters(
      "{expr: '[].exists(__result__, i, __result__ < i)', err: 'The iteration variable __result__"
          + " overwrites accumulator variable'}")
  @TestParameters(
      "{expr: '[].exists(j, __result__, __result__ < j)', err: 'The iteration variable __result__"
          + " overwrites accumulator variable'}")
  @TestParameters(
      "{expr: 'no_such_var.all(i, v, v > 0)', err: \"undeclared reference to 'no_such_var'\"}")
  public void twoVarComprehension_compilerErrors(String expr, String err) throws Exception {
    CelValidationException e =
        assertThrows(CelValidationException.class, () -> CEL_COMPILER.compile(expr).getAst());

    assertThat(e).hasMessageThat().contains(err);
  }

  @Test
  public void comprehension_onTypeParam_success() throws Exception {
    CelCompiler celCompiler =
        CelCompilerFactory.standardCelCompilerBuilder()
            .setOptions(CEL_OPTIONS)
            .setStandardMacros(CelStandardMacro.STANDARD_MACROS)
            .addLibraries(CelExtensions.comprehensions())
            .addVar("items", TypeParamType.create("T"))
            .build();

    CelAbstractSyntaxTree ast = celCompiler.compile("items.all(i, v, v > 0)").getAst();

    assertThat(ast.getResultType()).isEqualTo(SimpleType.BOOL);
  }

  @Test
  public void twoVarComprehension_arithematicRuntimeError() throws Exception {
    CelAbstractSyntaxTree ast = CEL_COMPILER.compile("[0].all(i, k, i/k < k)").getAst();

    CelEvaluationException e =
        assertThrows(CelEvaluationException.class, () -> CEL_RUNTIME.createProgram(ast).eval());

    assertThat(e).hasCauseThat().isInstanceOf(ArithmeticException.class);
    assertThat(e).hasCauseThat().hasMessageThat().contains("/ by zero");
  }

  @Test
  public void twoVarComprehension_outOfBoundsRuntimeError() throws Exception {
    CelAbstractSyntaxTree ast = CEL_COMPILER.compile("[1, 2].exists(i, v, [0][v] > 0)").getAst();

    CelEvaluationException e =
        assertThrows(CelEvaluationException.class, () -> CEL_RUNTIME.createProgram(ast).eval());

    assertThat(e).hasCauseThat().isInstanceOf(IndexOutOfBoundsException.class);
    assertThat(e).hasCauseThat().hasMessageThat().contains("Index out of bounds: 1");
  }
}
