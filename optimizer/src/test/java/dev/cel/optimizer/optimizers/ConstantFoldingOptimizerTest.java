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

package dev.cel.optimizer.optimizers;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import com.google.testing.junit.testparameterinjector.TestParameterInjector;
import com.google.testing.junit.testparameterinjector.TestParameters;
import dev.cel.bundle.Cel;
import dev.cel.bundle.CelFactory;
import dev.cel.common.CelAbstractSyntaxTree;
import dev.cel.common.CelOptions;
import dev.cel.common.types.ListType;
import dev.cel.common.types.MapType;
import dev.cel.common.types.SimpleType;
import dev.cel.extensions.CelOptionalLibrary;
import dev.cel.optimizer.CelOptimizationException;
import dev.cel.optimizer.CelOptimizer;
import dev.cel.optimizer.CelOptimizerFactory;
import dev.cel.optimizer.optimizers.ConstantFoldingOptimizer.ConstantFoldingOptions;
import dev.cel.parser.CelStandardMacro;
import dev.cel.parser.CelUnparser;
import dev.cel.parser.CelUnparserFactory;
import dev.cel.testing.testdata.proto3.TestAllTypesProto.TestAllTypes;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(TestParameterInjector.class)
public class ConstantFoldingOptimizerTest {
  private static final Cel CEL =
      CelFactory.standardCelBuilder()
          .addVar("x", SimpleType.DYN)
          .addVar("y", SimpleType.DYN)
          .addVar("list_var", ListType.create(SimpleType.STRING))
          .addVar("map_var", MapType.create(SimpleType.STRING, SimpleType.STRING))
          .addMessageTypes(TestAllTypes.getDescriptor())
          .setContainer("dev.cel.testing.testdata.proto3")
          .addCompilerLibraries(CelOptionalLibrary.INSTANCE)
          .addRuntimeLibraries(CelOptionalLibrary.INSTANCE)
          .build();

  private static final CelOptimizer CEL_OPTIMIZER =
      CelOptimizerFactory.standardCelOptimizerBuilder(CEL)
          .addAstOptimizers(ConstantFoldingOptimizer.getInstance())
          .build();

  private static final CelUnparser CEL_UNPARSER = CelUnparserFactory.newUnparser();

  @Test
  @TestParameters("{source: 'null', expected: 'null'}")
  @TestParameters("{source: '1 + 2', expected: '3'}")
  @TestParameters("{source: '1 + 2 + 3', expected: '6'}")
  @TestParameters("{source: '1 + 2 + x', expected: '3 + x'}")
  @TestParameters("{source: 'true && true', expected: 'true'}")
  @TestParameters("{source: 'true && false', expected: 'false'}")
  @TestParameters("{source: 'true || false', expected: 'true'}")
  @TestParameters("{source: 'false || false', expected: 'false'}")
  @TestParameters("{source: 'true && false || true', expected: 'true'}")
  @TestParameters("{source: 'false && true || false', expected: 'false'}")
  @TestParameters("{source: 'true && x', expected: 'x'}")
  @TestParameters("{source: 'x && true', expected: 'x'}")
  @TestParameters("{source: 'false && x', expected: 'false'}")
  @TestParameters("{source: 'x && false', expected: 'false'}")
  @TestParameters("{source: 'true || x', expected: 'true'}")
  @TestParameters("{source: 'x || true', expected: 'true'}")
  @TestParameters("{source: 'false || x', expected: 'x'}")
  @TestParameters("{source: 'x || false', expected: 'x'}")
  @TestParameters("{source: 'true && x && true && x', expected: 'x && x'}")
  @TestParameters("{source: 'false || x || false || x', expected: 'x || x'}")
  @TestParameters("{source: 'false || x || false || y', expected: 'x || y'}")
  @TestParameters("{source: 'true ? x + 1 : x + 2', expected: 'x + 1'}")
  @TestParameters("{source: 'false ? x + 1 : x + 2', expected: 'x + 2'}")
  @TestParameters(
      "{source: 'false ? x + ''world'' : ''hello'' + ''world''', expected: '\"helloworld\"'}")
  @TestParameters("{source: 'true ? (false ? x + 1 : x + 2) : x', expected: 'x + 2'}")
  @TestParameters("{source: 'false ? x : (true ? x + 1 : x + 2)', expected: 'x + 1'}")
  @TestParameters("{source: '[1, 1 + 2, 1 + (2 + 3)]', expected: '[1, 3, 6]'}")
  @TestParameters("{source: '1 in []', expected: 'false'}")
  @TestParameters("{source: 'x in []', expected: 'false'}")
  @TestParameters("{source: '6 in [1, 1 + 2, 1 + (2 + 3)]', expected: 'true'}")
  @TestParameters("{source: '5 in [1, 1 + 2, 1 + (2 + 3)]', expected: 'false'}")
  @TestParameters("{source: '5 in [1, x, y, 5]', expected: 'true'}")
  @TestParameters("{source: '!(5 in [1, x, y, 5])', expected: 'false'}")
  @TestParameters("{source: 'x in [1, x, y, 5]', expected: 'true'}")
  @TestParameters("{source: 'x in [1, 1 + 2, 1 + (2 + 3)]', expected: 'x in [1, 3, 6]'}")
  @TestParameters("{source: 'duration(string(7 * 24) + ''h'')', expected: 'duration(\"168h\")'}")
  @TestParameters("{source: '[1, ?optional.of(3)]', expected: '[1, 3]'}")
  @TestParameters("{source: '[1, ?optional.ofNonZeroValue(0)]', expected: '[1]'}")
  @TestParameters("{source: '[1, optional.of(3)]', expected: '[1, optional.of(3)]'}")
  @TestParameters("{source: '[?optional.none(), ?x]', expected: '[?x]'}")
  @TestParameters("{source: '[?optional.of(1 + 2 + 3)]', expected: '[6]'}")
  @TestParameters("{source: '[?optional.of(3)]', expected: '[3]'}")
  @TestParameters("{source: '[?optional.of(x)]', expected: '[?optional.of(x)]'}")
  @TestParameters("{source: '[?optional.ofNonZeroValue(0)]', expected: '[]'}")
  @TestParameters("{source: '[?optional.ofNonZeroValue(3)]', expected: '[3]'}")
  @TestParameters("{source: '[optional.none(), ?x]', expected: '[optional.none(), ?x]'}")
  @TestParameters("{source: '[optional.of(1 + 2 + 3)]', expected: '[optional.of(6)]'}")
  @TestParameters("{source: '[optional.of(3)]', expected: '[optional.of(3)]'}")
  @TestParameters("{source: '[optional.of(x)]', expected: '[optional.of(x)]'}")
  @TestParameters("{source: '[optional.ofNonZeroValue(1 + 2 + 3)]', expected: '[optional.of(6)]'}")
  @TestParameters("{source: '[optional.ofNonZeroValue(3)]', expected: '[optional.of(3)]'}")
  @TestParameters("{source: 'optional.none()', expected: 'optional.none()'}")
  @TestParameters(
      "{source: '[1, x, optional.of(1), ?optional.of(1), optional.ofNonZeroValue(3),"
          + " ?optional.ofNonZeroValue(3), ?optional.ofNonZeroValue(0), ?y, ?x.?y]', "
          + "expected: '[1, x, optional.of(1), 1, optional.of(3), 3, ?y, ?x.?y]'}")
  @TestParameters(
      "{source: '[1, x, ?optional.ofNonZeroValue(3), ?x.?y].size() > 3',"
          + " expected: '[1, x, 3, ?x.?y].size() > 3'}")
  @TestParameters("{source: '{?1: optional.none()}', expected: '{}'}")
  @TestParameters(
      "{source: '{?1: optional.of(\"hello\"), ?2: optional.ofNonZeroValue(0), 3:"
          + " optional.ofNonZeroValue(0), ?4: optional.of(x)}', expected: '{1: \"hello\", 3:"
          + " optional.none(), ?4: optional.of(x)}'}")
  @TestParameters(
      "{source: '{?x: optional.of(1), ?y: optional.ofNonZeroValue(0)}', expected: '{?x:"
          + " optional.of(1), ?y: optional.none()}'}")
  @TestParameters(
      "{source: 'TestAllTypes{single_int64: 1 + 2 + 3 + x}', "
          + " expected: 'TestAllTypes{single_int64: 6 + x}'}")
  @TestParameters(
      "{source: 'TestAllTypes{?single_int64: optional.ofNonZeroValue(1)}', "
          + " expected: 'TestAllTypes{single_int64: 1}'}")
  @TestParameters(
      "{source: 'TestAllTypes{?single_int64: optional.ofNonZeroValue(0)}', "
          + " expected: 'TestAllTypes{}'}")
  @TestParameters(
      "{source: 'TestAllTypes{?single_int64: optional.ofNonZeroValue(1), ?single_int32:"
          + " optional.of(4), ?single_uint64: optional.ofNonZeroValue(x)}', expected:"
          + " 'TestAllTypes{single_int64: 1, single_int32: 4, ?single_uint64:"
          + " optional.ofNonZeroValue(x)}'}")
  @TestParameters("{source: '{\"hello\": \"world\"}.hello == x', expected: '\"world\" == x'}")
  @TestParameters("{source: '{\"hello\": \"world\"}[\"hello\"] == x', expected: '\"world\" == x'}")
  @TestParameters("{source: '{\"hello\": \"world\"}.?hello', expected: 'optional.of(\"world\")'}")
  @TestParameters(
      "{source: '{\"hello\": \"world\"}.?hello.orValue(\"default\") == x', "
          + "expected: '\"world\" == x'}")
  @TestParameters(
      "{source: '{?\"hello\": optional.of(\"world\")}[\"hello\"] == x', expected: '\"world\" =="
          + " x'}")
  @TestParameters("{source: '[1] + [2] + [3]', expected: '[1, 2, 3]'}")
  @TestParameters("{source: '[1] + [?optional.of(2)] + [3]', expected: '[1, 2, 3]'}")
  @TestParameters("{source: '[1] + [x]', expected: '[1] + [x]'}")
  @TestParameters("{source: 'x + dyn([1, 2] + [3, 4])', expected: 'x + [1, 2, 3, 4]'}")
  @TestParameters(
      "{source: '{\"a\": dyn([1, 2]), \"b\": x}', expected: '{\"a\": [1, 2], \"b\": x}'}")
  @TestParameters("{source: 'map_var[?\"key\"]', expected: 'map_var[?\"key\"]'}")
  @TestParameters("{source: '\"abc\" in list_var', expected: '\"abc\" in list_var'}")
  // TODO: Support folding lists with mixed types. This requires mutable lists.
  // @TestParameters("{source: 'dyn([1]) + [1.0]'}")
  public void constantFold_success(String source, String expected) throws Exception {
    CelAbstractSyntaxTree ast = CEL.compile(source).getAst();

    CelAbstractSyntaxTree optimizedAst = CEL_OPTIMIZER.optimize(ast);

    assertThat(CEL_UNPARSER.unparse(optimizedAst)).isEqualTo(expected);
  }

  @Test
  @TestParameters("{source: '[1 + 1, 1 + 2].exists(i, i < 10)', expected: 'true'}")
  @TestParameters("{source: '[1, 1 + 1, 1 + 2, 2 + 3].exists(i, i < 10)', expected: 'true'}")
  @TestParameters("{source: '[1, 1 + 1, 1 + 2, 2 + 3].exists(i, i < 1 % 2)', expected: 'false'}")
  @TestParameters("{source: '[1, 2, 3].map(i, i * 2)', expected: '[2, 4, 6]'}")
  @TestParameters(
      "{source: '[1, 2, 3].map(i, [1, 2, 3].map(j, i * j))', "
          + "expected: '[[1, 2, 3], [2, 4, 6], [3, 6, 9]]'}")
  @TestParameters(
      "{source: '[1, 2, 3].map(i, [1, 2, 3].map(j, i * j).filter(k, k % 2 == 0))', "
          + "expected: '[[2], [2, 4, 6], [6]]'}")
  @TestParameters(
      "{source: '[1, 2, 3].map(i, [1, 2, 3].map(j, i * j).filter(k, k % 2 == x))', "
          + "expected: '[1, 2, 3].map(i, [1, 2, 3].map(j, i * j).filter(k, k % 2 == x))'}")
  @TestParameters(
      "{source: '[{}, {\"a\": 1}, {\"b\": 2}].filter(m, has(m.a))', expected: '[{\"a\": 1}]'}")
  @TestParameters(
      "{source: '[{}, {?\"a\": optional.of(1)}, {\"b\": optional.of(2)}].filter(m, has(m.a))',"
          + " expected: '[{\"a\": 1}]'}")
  @TestParameters(
      "{source: '[{}, {?\"a\": optional.of(1)}, {\"b\": optional.of(2)}].map(i, i)',"
          + " expected: '[{}, {\"a\": 1}, {\"b\": optional.of(2)}].map(i, i)'}")
  @TestParameters(
      "{source: '[{}, {\"a\": 1}, {\"b\": 2}].filter(m, has({\"a\": true}.a))',"
          + " expected: '[{}, {\"a\": 1}, {\"b\": 2}]'}")
  @TestParameters("{source: 'has(x.single_int32)', expected: 'has(x.single_int32)'}")
  @TestParameters(
      "{source: '[{}, {\"a\": 1}, {\"b\": 2}].filter(m, has(x.a))', expected:"
          + " '[{}, {\"a\": 1}, {\"b\": 2}].filter(m, has(x.a))'}")
  public void constantFold_macros_macroCallMetadataPopulated(String source, String expected)
      throws Exception {
    Cel cel =
        CelFactory.standardCelBuilder()
            .addVar("x", SimpleType.DYN)
            .addVar("y", SimpleType.DYN)
            .addMessageTypes(TestAllTypes.getDescriptor())
            .setStandardMacros(CelStandardMacro.STANDARD_MACROS)
            .setOptions(CelOptions.current().populateMacroCalls(true).build())
            .addCompilerLibraries(CelOptionalLibrary.INSTANCE)
            .addRuntimeLibraries(CelOptionalLibrary.INSTANCE)
            .build();
    CelOptimizer celOptimizer =
        CelOptimizerFactory.standardCelOptimizerBuilder(cel)
            .addAstOptimizers(ConstantFoldingOptimizer.getInstance())
            .build();
    CelAbstractSyntaxTree ast = cel.compile(source).getAst();

    CelAbstractSyntaxTree optimizedAst = celOptimizer.optimize(ast);

    assertThat(CEL_UNPARSER.unparse(optimizedAst)).isEqualTo(expected);
  }

  @Test
  @TestParameters("{source: '[1 + 1, 1 + 2].exists(i, i < 10) == true'}")
  @TestParameters("{source: '[1, 1 + 1, 1 + 2, 2 + 3].exists(i, i < 10) == true'}")
  @TestParameters("{source: '[1, 1 + 1, 1 + 2, 2 + 3].exists(i, i < 1 % 2) == false'}")
  @TestParameters("{source: '[1, 2, 3].map(i, i * 2) == [2, 4, 6]'}")
  @TestParameters(
      "{source: '[1, 2, 3].map(i, [1, 2, 3].map(j, i * j)) == [[1, 2, 3], [2, 4, 6], [3, 6, 9]]'}")
  @TestParameters(
      "{source: '[1, 2, 3].map(i, [1, 2, 3].map(j, i * j).filter(k, k % 2 == 0)) == "
          + "[[2], [2, 4, 6], [6]]'}")
  @TestParameters("{source: '[{}, {\"a\": 1}, {\"b\": 2}].filter(m, has(m.a)) == [{\"a\": 1}]'}")
  @TestParameters(
      "{source: '[{}, {?\"a\": optional.of(1)}, {\"b\": optional.of(2)}].filter(m, has(m.a)) == "
          + " [{\"a\": 1}]'}")
  @TestParameters(
      "{source: '[{}, {?\"a\": optional.of(1)}, {\"b\": optional.of(2)}].map(i, i) == "
          + " [{}, {\"a\": 1}, {\"b\": optional.of(2)}].map(i, i)'}")
  @TestParameters(
      "{source: '[{}, {\"a\": 1}, {\"b\": 2}].filter(m, has({\"a\": true}.a)) == "
          + " [{}, {\"a\": 1}, {\"b\": 2}]'}")
  public void constantFold_macros_withoutMacroCallMetadata(String source) throws Exception {
    Cel cel =
        CelFactory.standardCelBuilder()
            .addVar("x", SimpleType.DYN)
            .addVar("y", SimpleType.DYN)
            .addMessageTypes(TestAllTypes.getDescriptor())
            .setStandardMacros(CelStandardMacro.STANDARD_MACROS)
            .setOptions(CelOptions.current().populateMacroCalls(false).build())
            .addCompilerLibraries(CelOptionalLibrary.INSTANCE)
            .addRuntimeLibraries(CelOptionalLibrary.INSTANCE)
            .build();
    CelOptimizer celOptimizer =
        CelOptimizerFactory.standardCelOptimizerBuilder(cel)
            .addAstOptimizers(ConstantFoldingOptimizer.getInstance())
            .build();
    CelAbstractSyntaxTree ast = cel.compile(source).getAst();

    CelAbstractSyntaxTree optimizedAst = celOptimizer.optimize(ast);

    assertThat(optimizedAst.getSource().getMacroCalls()).isEmpty();
    assertThat(cel.createProgram(optimizedAst).eval()).isEqualTo(true);
  }

  @Test
  @TestParameters("{source: '1'}")
  @TestParameters("{source: '5.0'}")
  @TestParameters("{source: 'true'}")
  @TestParameters("{source: 'x'}")
  @TestParameters("{source: 'x + 1 + 2'}")
  @TestParameters("{source: 'duration(\"16800s\")'}")
  @TestParameters("{source: 'timestamp(\"1970-01-01T00:00:00Z\")'}")
  @TestParameters(
      "{source: 'type(1)'}") // This folds in cel-go implementation but Java does not yet have a
  // literal representation for type values
  @TestParameters("{source: '[1, 2, 3]'}")
  @TestParameters("{source: 'optional.of(\"hello\")'}")
  @TestParameters("{source: 'optional.none()'}")
  @TestParameters("{source: '[optional.none()]'}")
  @TestParameters("{source: '[?x.?y]'}")
  @TestParameters("{source: 'TestAllTypes{single_int32: x, repeated_int32: [1, 2, 3]}'}")
  public void constantFold_noOp(String source) throws Exception {
    CelAbstractSyntaxTree ast = CEL.compile(source).getAst();

    CelAbstractSyntaxTree optimizedAst = CEL_OPTIMIZER.optimize(ast);

    assertThat(CEL_UNPARSER.unparse(optimizedAst)).isEqualTo(source);
  }

  @Test
  public void constantFold_withMacroCallPopulated_comprehensionsAreReplacedWithNotSet()
      throws Exception {
    Cel cel =
        CelFactory.standardCelBuilder()
            .addVar("x", SimpleType.DYN)
            .setStandardMacros(CelStandardMacro.STANDARD_MACROS)
            .setOptions(CelOptions.current().populateMacroCalls(true).build())
            .build();
    CelOptimizer celOptimizer =
        CelOptimizerFactory.standardCelOptimizerBuilder(cel)
            .addAstOptimizers(ConstantFoldingOptimizer.getInstance())
            .build();
    CelAbstractSyntaxTree ast =
        cel.compile("[1, 1 + 1, 1 + 1+ 1].map(i, i).filter(j, j % 2 == x)").getAst();

    CelAbstractSyntaxTree optimizedAst = celOptimizer.optimize(ast);

    assertThat(CEL_UNPARSER.unparse(optimizedAst))
        .isEqualTo("[1, 2, 3].map(i, i).filter(j, j % 2 == x)");
    assertThat(optimizedAst.getSource().getMacroCalls()).hasSize(2);
    assertThat(optimizedAst.getSource().getMacroCalls().get(1L).toString())
        .isEqualTo(
            "CALL [0] {\n"
                + "  function: filter\n"
                + "  target: {\n"
                + "    NOT_SET [2] {}\n"
                + "  }\n"
                + "  args: {\n"
                + "    IDENT [25] {\n"
                + "      name: j\n"
                + "    }\n"
                + "    CALL [17] {\n"
                + "      function: _==_\n"
                + "      args: {\n"
                + "        CALL [18] {\n"
                + "          function: _%_\n"
                + "          args: {\n"
                + "            IDENT [19] {\n"
                + "              name: j\n"
                + "            }\n"
                + "            CONSTANT [20] { value: 2 }\n"
                + "          }\n"
                + "        }\n"
                + "        IDENT [21] {\n"
                + "          name: x\n"
                + "        }\n"
                + "      }\n"
                + "    }\n"
                + "  }\n"
                + "}");
    assertThat(optimizedAst.getSource().getMacroCalls().get(2L).toString())
        .isEqualTo(
            "CALL [0] {\n"
                + "  function: map\n"
                + "  target: {\n"
                + "    CREATE_LIST [3] {\n"
                + "      elements: {\n"
                + "        CONSTANT [4] { value: 1 }\n"
                + "        CONSTANT [5] { value: 2 }\n"
                + "        CONSTANT [6] { value: 3 }\n"
                + "      }\n"
                + "    }\n"
                + "  }\n"
                + "  args: {\n"
                + "    IDENT [28] {\n"
                + "      name: i\n"
                + "    }\n"
                + "    IDENT [12] {\n"
                + "      name: i\n"
                + "    }\n"
                + "  }\n"
                + "}");
  }

  @Test
  public void constantFold_astProducesConsistentlyNumberedIds() throws Exception {
    CelAbstractSyntaxTree ast = CEL.compile("[1] + [2] + [3]").getAst();

    CelAbstractSyntaxTree optimizedAst = CEL_OPTIMIZER.optimize(ast);

    assertThat(optimizedAst.getExpr().toString())
        .isEqualTo(
            "CREATE_LIST [1] {\n"
                + "  elements: {\n"
                + "    CONSTANT [2] { value: 1 }\n"
                + "    CONSTANT [3] { value: 2 }\n"
                + "    CONSTANT [4] { value: 3 }\n"
                + "  }\n"
                + "}");
  }

  @Test
  public void iterationLimitReached_throws() throws Exception {
    StringBuilder sb = new StringBuilder();
    sb.append("0");
    for (int i = 1; i < 200; i++) {
      sb.append(" + ").append(i);
    } // 0 + 1 + 2 + 3 + ... 200
    Cel cel =
        CelFactory.standardCelBuilder()
            .setOptions(CelOptions.current().maxParseRecursionDepth(200).build())
            .build();
    CelAbstractSyntaxTree ast = cel.compile(sb.toString()).getAst();
    CelOptimizer optimizer =
        CelOptimizerFactory.standardCelOptimizerBuilder(cel)
            .addAstOptimizers(
                ConstantFoldingOptimizer.newInstance(
                    ConstantFoldingOptions.newBuilder().maxIterationLimit(200).build()))
            .build();

    CelOptimizationException e =
        assertThrows(CelOptimizationException.class, () -> optimizer.optimize(ast));
    assertThat(e).hasMessageThat().contains("Optimization failure: Max iteration count reached.");
  }
}
