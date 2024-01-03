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

package dev.cel.optimizer.optimizers;

import static com.google.common.truth.Truth.assertThat;
import static dev.cel.common.CelOverloadDecl.newGlobalOverload;

import com.google.common.collect.ImmutableMap;
import com.google.testing.junit.testparameterinjector.TestParameter;
import com.google.testing.junit.testparameterinjector.TestParameterInjector;
import com.google.testing.junit.testparameterinjector.TestParameters;
import dev.cel.bundle.Cel;
import dev.cel.bundle.CelBuilder;
import dev.cel.bundle.CelFactory;
import dev.cel.common.CelAbstractSyntaxTree;
import dev.cel.common.CelFunctionDecl;
import dev.cel.common.CelOptions;
import dev.cel.common.types.SimpleType;
import dev.cel.common.types.StructTypeReference;
import dev.cel.extensions.CelExtensions;
import dev.cel.extensions.CelOptionalLibrary;
import dev.cel.optimizer.CelOptimizer;
import dev.cel.optimizer.CelOptimizerFactory;
import dev.cel.parser.CelStandardMacro;
import dev.cel.parser.CelUnparser;
import dev.cel.parser.CelUnparserFactory;
import dev.cel.testing.testdata.proto3.TestAllTypesProto.NestedTestAllTypes;
import dev.cel.testing.testdata.proto3.TestAllTypesProto.TestAllTypes;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(TestParameterInjector.class)
public class CSEOptimizerTest {

  private static final Cel CEL = newCelBuilder().build();

  private static final CelOptimizer CEL_OPTIMIZER =
      CelOptimizerFactory.standardCelOptimizerBuilder(CEL)
          .addAstOptimizers(CSEOptimizer.INSTANCE)
          .build();

  private static final CelUnparser CEL_UNPARSER = CelUnparserFactory.newUnparser();

  private static CelBuilder newCelBuilder() {
    return CelFactory.standardCelBuilder()
        .addMessageTypes(TestAllTypes.getDescriptor())
        .setContainer("dev.cel.testing.testdata.proto3")
        .setStandardMacros(CelStandardMacro.STANDARD_MACROS)
        .setOptions(
            CelOptions.current().enableTimestampEpoch(true).populateMacroCalls(true).build())
        .addCompilerLibraries(CelOptionalLibrary.INSTANCE, CelExtensions.bindings())
        .addRuntimeLibraries(CelOptionalLibrary.INSTANCE)
        .addFunctionDeclarations(
            CelFunctionDecl.newFunctionDeclaration(
                "custom_func",
                newGlobalOverload("custom_func_overload", SimpleType.INT, SimpleType.INT)))
        .addVar("x", SimpleType.INT)
        .addVar("msg", StructTypeReference.create(TestAllTypes.getDescriptor().getFullName()));
  }

  @Test
  public void cse_producesOptimizedAst() throws Exception {
    CelAbstractSyntaxTree ast = CEL.compile("size([1,2]) + size([1,2])").getAst();

    CelAbstractSyntaxTree optimizedAst = CEL_OPTIMIZER.optimize(ast);

    assertThat(CEL.createProgram(optimizedAst).eval()).isEqualTo(4);
    assertThat(optimizedAst.getExpr().toString())
        .isEqualTo(
            "COMPREHENSION [1] {\n"
                + "  iter_var: #unused\n"
                + "  iter_range: {\n"
                + "    CREATE_LIST [2] {\n"
                + "      elements: {\n"
                + "      }\n"
                + "    }\n"
                + "  }\n"
                + "  accu_var: @r0\n"
                + "  accu_init: {\n"
                + "    CALL [3] {\n"
                + "      function: size\n"
                + "      args: {\n"
                + "        CREATE_LIST [4] {\n"
                + "          elements: {\n"
                + "            CONSTANT [5] { value: 1 }\n"
                + "            CONSTANT [6] { value: 2 }\n"
                + "          }\n"
                + "        }\n"
                + "      }\n"
                + "    }\n"
                + "  }\n"
                + "  loop_condition: {\n"
                + "    CONSTANT [7] { value: false }\n"
                + "  }\n"
                + "  loop_step: {\n"
                + "    IDENT [8] {\n"
                + "      name: @r0\n"
                + "    }\n"
                + "  }\n"
                + "  result: {\n"
                + "    CALL [9] {\n"
                + "      function: _+_\n"
                + "      args: {\n"
                + "        IDENT [10] {\n"
                + "          name: @r0\n"
                + "        }\n"
                + "        IDENT [11] {\n"
                + "          name: @r0\n"
                + "        }\n"
                + "      }\n"
                + "    }\n"
                + "  }\n"
                + "}");
  }

  private enum CSETestCase {
    SIZE_1("size([1,2]) + size([1,2]) + 1 == 5", "cel.bind(@r0, size([1, 2]), @r0 + @r0) + 1 == 5"),
    SIZE_2(
        "2 + size([1,2]) + size([1,2]) + 1 == 7",
        "cel.bind(@r0, size([1, 2]), 2 + @r0 + @r0) + 1 == 7"),
    SIZE_3(
        "size([0]) + size([0]) + size([1,2]) + size([1,2]) == 6",
        "cel.bind(@r1, size([1, 2]), cel.bind(@r0, size([0]), @r0 + @r0) + @r1 + @r1) == 6"),
    SIZE_4(
        "5 + size([0]) + size([0]) + size([1,2]) + size([1,2]) + "
            + "size([1,2,3]) + size([1,2,3]) == 17",
        "cel.bind(@r2, size([1, 2, 3]), cel.bind(@r1, size([1, 2]), cel.bind(@r0, size([0]), 5 +"
            + " @r0 + @r0) + @r1 + @r1) + @r2 + @r2) == 17"),
    /**
     * Unparsed form is:
     *
     * <pre>
     * {@code
     * cel.bind(@r0, timestamp(int(timestamp(1000000000))).getFullYear(),
     *    cel.bind(@r3, timestamp(int(timestamp(75))),
     *      cel.bind(@r2, timestamp(int(timestamp(200))).getFullYear(),
     *        cel.bind(@r1, timestamp(int(timestamp(50))),
     *          @r0 + @r3.getFullYear() + @r1.getFullYear() + @r0 + @r1.getSeconds()
     *        ) + @r2 + @r2
     *      ) + @r3.getMinutes()
     *    ) + @r0
     *) == 13934
     * }
     * </pre>
     */
    TIMESTAMP(
        "timestamp(int(timestamp(1000000000))).getFullYear() +"
            + " timestamp(int(timestamp(75))).getFullYear() + "
            + " timestamp(int(timestamp(50))).getFullYear() + "
            + " timestamp(int(timestamp(1000000000))).getFullYear() + "
            + " timestamp(int(timestamp(50))).getSeconds() + "
            + " timestamp(int(timestamp(200))).getFullYear() + "
            + " timestamp(int(timestamp(200))).getFullYear() + "
            + " timestamp(int(timestamp(75))).getMinutes() + "
            + " timestamp(int(timestamp(1000000000))).getFullYear() == 13934",
        "cel.bind(@r0, timestamp(int(timestamp(1000000000))).getFullYear(), "
            + "cel.bind(@r3, timestamp(int(timestamp(75))), "
            + "cel.bind(@r2, timestamp(int(timestamp(200))).getFullYear(), "
            + "cel.bind(@r1, timestamp(int(timestamp(50))), "
            + "@r0 + @r3.getFullYear() + @r1.getFullYear() + "
            + "@r0 + @r1.getSeconds()) + @r2 + @r2) + @r3.getMinutes()) + @r0) == 13934"),
    MAP_INDEX(
        "{\"a\": 2}[\"a\"] + {\"a\": 2}[\"a\"] * {\"a\": 2}[\"a\"] == 6",
        "cel.bind(@r0, {\"a\": 2}[\"a\"], @r0 + @r0 * @r0) == 6"),
    SELECT(
        "msg.single_int64 + msg.single_int64 == 6",
        "cel.bind(@r0, msg.single_int64, @r0 + @r0) == 6"),
    SELECT_NESTED_MESSAGE_MAP_INDEX_1(
        "msg.oneof_type.payload.map_int32_int64[1] + "
            + "msg.oneof_type.payload.map_int32_int64[1] + "
            + "msg.oneof_type.payload.map_int32_int64[1] == 15",
        "cel.bind(@r0, msg.oneof_type.payload.map_int32_int64[1], @r0 + @r0 + @r0) == 15"),
    SELECT_NESTED_MESSAGE_MAP_INDEX_2(
        "msg.oneof_type.payload.map_int32_int64[0] + "
            + "msg.oneof_type.payload.map_int32_int64[1] + "
            + "msg.oneof_type.payload.map_int32_int64[2] == 8",
        "cel.bind(@r0, msg.oneof_type.payload.map_int32_int64, @r0[0] + @r0[1] + @r0[2]) == 8"),
    TERNARY(
        "(msg.single_int64 > 0 ? msg.single_int64 : 0) == 3",
        "cel.bind(@r0, msg.single_int64, (@r0 > 0) ? @r0 : 0) == 3"),
    NESTED_TERNARY(
        "(msg.single_int64 > 0 ? (msg.single_int32 > 0 ? "
            + "msg.single_int64 + msg.single_int32 : 0) : 0) == 8",
        "cel.bind(@r0, msg.single_int64, (@r0 > 0) ? "
            + "cel.bind(@r1, msg.single_int32, (@r1 > 0) ? (@r0 + @r1) : 0) : 0) == 8"),
  // PRESENCE_TEST_WITH_TERNARY(
  //     "has(msg.single_any) ? msg.single_any : 10 == 10",
  //     "cel.bind(@r0, msg.single_int64, (@r0 > 0) ? "),
  ;

    private final String source;
    private final String unparsed;

    CSETestCase(String source, String unparsed) {
      this.source = source;
      this.unparsed = unparsed;
    }
  }

  @Test
  public void cse_withMacroMapPopulated_success(@TestParameter CSETestCase testCase)
      throws Exception {
    CelAbstractSyntaxTree ast = CEL.compile(testCase.source).getAst();

    CelAbstractSyntaxTree optimizedAst = CEL_OPTIMIZER.optimize(ast);

    assertThat(
            CEL.createProgram(optimizedAst)
                .eval(
                    ImmutableMap.of(
                        "msg",
                        TestAllTypes.newBuilder()
                            .setSingleInt64(3L)
                            .setSingleInt32(5)
                            .setOneofType(
                                NestedTestAllTypes.newBuilder()
                                    .setPayload(
                                        TestAllTypes.newBuilder()
                                            .putMapInt32Int64(0, 1)
                                            .putMapInt32Int64(1, 5)
                                            .putMapInt32Int64(2, 2)))
                            .build())))
        .isEqualTo(true);
    assertThat(CEL_UNPARSER.unparse(optimizedAst)).isEqualTo(testCase.unparsed);
  }

  @Test
  public void cse_withoutMacroMap_success(@TestParameter CSETestCase testCase) throws Exception {
    Cel celWithoutMacroMap =
        newCelBuilder()
            .setOptions(
                CelOptions.current().populateMacroCalls(false).enableTimestampEpoch(true).build())
            .build();
    CelAbstractSyntaxTree ast = celWithoutMacroMap.compile(testCase.source).getAst();

    CelAbstractSyntaxTree optimizedAst =
        CelOptimizerFactory.standardCelOptimizerBuilder(celWithoutMacroMap)
            .addAstOptimizers(CSEOptimizer.INSTANCE)
            .build()
            .optimize(ast);

    assertThat(
            celWithoutMacroMap
                .createProgram(optimizedAst)
                .eval(
                    ImmutableMap.of(
                        "msg",
                        TestAllTypes.newBuilder()
                            .setSingleInt64(3L)
                            .setSingleInt32(5)
                            .setOneofType(
                                NestedTestAllTypes.newBuilder()
                                    .setPayload(
                                        TestAllTypes.newBuilder()
                                            .putMapInt32Int64(0, 1)
                                            .putMapInt32Int64(1, 5)
                                            .putMapInt32Int64(2, 2)))
                            .build())))
        .isEqualTo(true);
    assertThat(CEL_UNPARSER.unparse(optimizedAst)).isEqualTo(testCase.unparsed);
  }

  @Test
  // Nothing to optimize
  @TestParameters("{source: 'size(\"hello\")'}")
  // Constants and identifiers are not optimized
  @TestParameters("{source: '2 + 2 + 2 + 2'}")
  @TestParameters("{source: 'x + x + x + x'}")
  @TestParameters("{source: 'true == true && false == false'}")
  // Constants and identifiers within a function is not optimized
  @TestParameters("{source: 'size(\"hello\" + \"hello\" + \"hello\")'}")
  @TestParameters("{source: 'string(x + x + x)'}")
  // Non-standard functions are considered non-pure for time being
  @TestParameters("{source: 'custom_func(1) + custom_func(1)'}")
  // Nested functions
  @TestParameters("{source: 'int(timestamp(int(timestamp(1000000000))))'}")
  public void cse_noop(String source) throws Exception {
    CelAbstractSyntaxTree ast = CEL.compile(source).getAst();

    CelAbstractSyntaxTree optimizedAst = CEL_OPTIMIZER.optimize(ast);

    assertThat(ast.getExpr()).isEqualTo(optimizedAst.getExpr());
    assertThat(CEL_UNPARSER.unparse(optimizedAst)).isEqualTo(source);
  }

  @Test
  public void smoketest_2() throws Exception {
    // CelAbstractSyntaxTree ast =
    //     CEL.compile("has(msg.single_any) ? msg.single_any : 10 == 10").getAst();
    //
    // CelAbstractSyntaxTree optimizedAst = CEL_OPTIMIZER.optimize(ast);
    //
    // assertThat(
    //         CEL.createProgram(optimizedAst)
    //             .eval(
    //                 ImmutableMap.of(
    //                     "msg",
    //                     TestAllTypes.newBuilder().setSingleInt64(3L).setSingleInt32(5).build())))
    //     .isEqualTo(true);
    // assertThat(CEL_UNPARSER.unparse(optimizedAst)).isEqualTo("");
  }

  @Test
  public void smoketest() throws Exception {
    // CelAbstractSyntaxTree ast =
    //     CEL.compile("size([[1].exists(x, x > 0)]) + size([[1].exists(x, x > 0)]) == 2").getAst();
    //
    // CelAbstractSyntaxTree optimizedAst = CEL_OPTIMIZER.optimize(ast);
    //
    // assertThat(
    //         CEL.createProgram(optimizedAst)
    //             .eval(
    //                 ImmutableMap.of(
    //                     "msg",
    //                     TestAllTypes.newBuilder().setSingleInt64(3L).setSingleInt32(5).build())))
    //     .isEqualTo(true);
    // assertThat(CEL_UNPARSER.unparse(optimizedAst))
    //     .isEqualTo(
    //         "cel.bind(@r2, size([1, 2, 3]), cel.bind(@r1, size([1, 2]), cel.bind(@r0, size([0]),
    // 5"
    //             + " + @r0 + @r0) + @r1 + @r1) + @r2 + @r2) == 17");
  }
}
