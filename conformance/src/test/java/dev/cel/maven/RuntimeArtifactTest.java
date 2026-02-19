// Copyright 2026 Google LLC
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

package dev.cel.maven;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import dev.cel.expr.CheckedExpr;
import com.google.common.collect.ImmutableList;
import com.google.protobuf.TextFormat;
import com.google.testing.junit.testparameterinjector.TestParameterInjector;
import dev.cel.common.CelAbstractSyntaxTree;
import dev.cel.common.CelOptions;
import dev.cel.common.CelProtoAbstractSyntaxTree;
import dev.cel.expr.conformance.proto3.TestAllTypes;
import dev.cel.runtime.CelEvaluationException;
import dev.cel.runtime.CelFunctionBinding;
import dev.cel.runtime.CelRuntime;
import dev.cel.runtime.CelRuntimeFactory;
import dev.cel.runtime.CelStandardFunctions;
import dev.cel.runtime.CelStandardFunctions.StandardFunction;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(TestParameterInjector.class)
public class RuntimeArtifactTest {

  // Serialized expr: [TestAllTypes{single_int64: 1}.single_int64, 2].exists(x, x == 2)
  private static final String CHECKED_EXPR =
      "reference_map {\n"
          + "  key: 2\n"
          + "  value {\n"
          + "    name: \"cel.expr.conformance.proto3.TestAllTypes\"\n"
          + "  }\n"
          + "}\n"
          + "reference_map {\n"
          + "  key: 7\n"
          + "  value {\n"
          + "    overload_id: \"getThree_overload\"\n"
          + "  }\n"
          + "}\n"
          + "reference_map {\n"
          + "  key: 10\n"
          + "  value {\n"
          + "    name: \"x\"\n"
          + "  }\n"
          + "}\n"
          + "reference_map {\n"
          + "  key: 11\n"
          + "  value {\n"
          + "    overload_id: \"greater_equals_int64\"\n"
          + "  }\n"
          + "}\n"
          + "reference_map {\n"
          + "  key: 14\n"
          + "  value {\n"
          + "    name: \"@result\"\n"
          + "  }\n"
          + "}\n"
          + "reference_map {\n"
          + "  key: 15\n"
          + "  value {\n"
          + "    overload_id: \"not_strictly_false\"\n"
          + "  }\n"
          + "}\n"
          + "reference_map {\n"
          + "  key: 16\n"
          + "  value {\n"
          + "    name: \"@result\"\n"
          + "  }\n"
          + "}\n"
          + "reference_map {\n"
          + "  key: 17\n"
          + "  value {\n"
          + "    overload_id: \"logical_and\"\n"
          + "  }\n"
          + "}\n"
          + "reference_map {\n"
          + "  key: 18\n"
          + "  value {\n"
          + "    name: \"@result\"\n"
          + "  }\n"
          + "}\n"
          + "type_map {\n"
          + "  key: 1\n"
          + "  value {\n"
          + "    list_type {\n"
          + "      elem_type {\n"
          + "        primitive: INT64\n"
          + "      }\n"
          + "    }\n"
          + "  }\n"
          + "}\n"
          + "type_map {\n"
          + "  key: 2\n"
          + "  value {\n"
          + "    message_type: \"cel.expr.conformance.proto3.TestAllTypes\"\n"
          + "  }\n"
          + "}\n"
          + "type_map {\n"
          + "  key: 4\n"
          + "  value {\n"
          + "    primitive: INT64\n"
          + "  }\n"
          + "}\n"
          + "type_map {\n"
          + "  key: 5\n"
          + "  value {\n"
          + "    primitive: INT64\n"
          + "  }\n"
          + "}\n"
          + "type_map {\n"
          + "  key: 6\n"
          + "  value {\n"
          + "    primitive: INT64\n"
          + "  }\n"
          + "}\n"
          + "type_map {\n"
          + "  key: 7\n"
          + "  value {\n"
          + "    primitive: INT64\n"
          + "  }\n"
          + "}\n"
          + "type_map {\n"
          + "  key: 10\n"
          + "  value {\n"
          + "    primitive: INT64\n"
          + "  }\n"
          + "}\n"
          + "type_map {\n"
          + "  key: 11\n"
          + "  value {\n"
          + "    primitive: BOOL\n"
          + "  }\n"
          + "}\n"
          + "type_map {\n"
          + "  key: 12\n"
          + "  value {\n"
          + "    primitive: INT64\n"
          + "  }\n"
          + "}\n"
          + "type_map {\n"
          + "  key: 13\n"
          + "  value {\n"
          + "    primitive: BOOL\n"
          + "  }\n"
          + "}\n"
          + "type_map {\n"
          + "  key: 14\n"
          + "  value {\n"
          + "    primitive: BOOL\n"
          + "  }\n"
          + "}\n"
          + "type_map {\n"
          + "  key: 15\n"
          + "  value {\n"
          + "    primitive: BOOL\n"
          + "  }\n"
          + "}\n"
          + "type_map {\n"
          + "  key: 16\n"
          + "  value {\n"
          + "    primitive: BOOL\n"
          + "  }\n"
          + "}\n"
          + "type_map {\n"
          + "  key: 17\n"
          + "  value {\n"
          + "    primitive: BOOL\n"
          + "  }\n"
          + "}\n"
          + "type_map {\n"
          + "  key: 18\n"
          + "  value {\n"
          + "    primitive: BOOL\n"
          + "  }\n"
          + "}\n"
          + "type_map {\n"
          + "  key: 19\n"
          + "  value {\n"
          + "    primitive: BOOL\n"
          + "  }\n"
          + "}\n"
          + "expr {\n"
          + "  id: 19\n"
          + "  comprehension_expr {\n"
          + "    iter_var: \"x\"\n"
          + "    iter_range {\n"
          + "      id: 1\n"
          + "      list_expr {\n"
          + "        elements {\n"
          + "          id: 5\n"
          + "          select_expr {\n"
          + "            operand {\n"
          + "              id: 2\n"
          + "              struct_expr {\n"
          + "                message_name: \"cel.expr.conformance.proto3.TestAllTypes\"\n"
          + "                entries {\n"
          + "                  id: 3\n"
          + "                  field_key: \"single_int64\"\n"
          + "                  value {\n"
          + "                    id: 4\n"
          + "                    const_expr {\n"
          + "                      int64_value: 1\n"
          + "                    }\n"
          + "                  }\n"
          + "                }\n"
          + "              }\n"
          + "            }\n"
          + "            field: \"single_int64\"\n"
          + "          }\n"
          + "        }\n"
          + "        elements {\n"
          + "          id: 6\n"
          + "          const_expr {\n"
          + "            int64_value: 2\n"
          + "          }\n"
          + "        }\n"
          + "        elements {\n"
          + "          id: 7\n"
          + "          call_expr {\n"
          + "            function: \"getThree\"\n"
          + "          }\n"
          + "        }\n"
          + "      }\n"
          + "    }\n"
          + "    accu_var: \"@result\"\n"
          + "    accu_init {\n"
          + "      id: 13\n"
          + "      const_expr {\n"
          + "        bool_value: true\n"
          + "      }\n"
          + "    }\n"
          + "    loop_condition {\n"
          + "      id: 15\n"
          + "      call_expr {\n"
          + "        function: \"@not_strictly_false\"\n"
          + "        args {\n"
          + "          id: 14\n"
          + "          ident_expr {\n"
          + "            name: \"@result\"\n"
          + "          }\n"
          + "        }\n"
          + "      }\n"
          + "    }\n"
          + "    loop_step {\n"
          + "      id: 17\n"
          + "      call_expr {\n"
          + "        function: \"_&&_\"\n"
          + "        args {\n"
          + "          id: 16\n"
          + "          ident_expr {\n"
          + "            name: \"@result\"\n"
          + "          }\n"
          + "        }\n"
          + "        args {\n"
          + "          id: 11\n"
          + "          call_expr {\n"
          + "            function: \"_>=_\"\n"
          + "            args {\n"
          + "              id: 10\n"
          + "              ident_expr {\n"
          + "                name: \"x\"\n"
          + "              }\n"
          + "            }\n"
          + "            args {\n"
          + "              id: 12\n"
          + "              const_expr {\n"
          + "                int64_value: 0\n"
          + "              }\n"
          + "            }\n"
          + "          }\n"
          + "        }\n"
          + "      }\n"
          + "    }\n"
          + "    result {\n"
          + "      id: 18\n"
          + "      ident_expr {\n"
          + "        name: \"@result\"\n"
          + "      }\n"
          + "    }\n"
          + "  }\n"
          + "}\n"
          + "source_info {\n"
          + "  location: \"<input>\"\n"
          + "  line_offsets: 75\n"
          + "  positions {\n"
          + "    key: 1\n"
          + "    value: 0\n"
          + "  }\n"
          + "  positions {\n"
          + "    key: 2\n"
          + "    value: 13\n"
          + "  }\n"
          + "  positions {\n"
          + "    key: 3\n"
          + "    value: 26\n"
          + "  }\n"
          + "  positions {\n"
          + "    key: 4\n"
          + "    value: 28\n"
          + "  }\n"
          + "  positions {\n"
          + "    key: 5\n"
          + "    value: 30\n"
          + "  }\n"
          + "  positions {\n"
          + "    key: 6\n"
          + "    value: 45\n"
          + "  }\n"
          + "  positions {\n"
          + "    key: 7\n"
          + "    value: 56\n"
          + "  }\n"
          + "  positions {\n"
          + "    key: 9\n"
          + "    value: 64\n"
          + "  }\n"
          + "  positions {\n"
          + "    key: 10\n"
          + "    value: 67\n"
          + "  }\n"
          + "  positions {\n"
          + "    key: 11\n"
          + "    value: 69\n"
          + "  }\n"
          + "  positions {\n"
          + "    key: 12\n"
          + "    value: 72\n"
          + "  }\n"
          + "  positions {\n"
          + "    key: 13\n"
          + "    value: 63\n"
          + "  }\n"
          + "  positions {\n"
          + "    key: 14\n"
          + "    value: 63\n"
          + "  }\n"
          + "  positions {\n"
          + "    key: 15\n"
          + "    value: 63\n"
          + "  }\n"
          + "  positions {\n"
          + "    key: 16\n"
          + "    value: 63\n"
          + "  }\n"
          + "  positions {\n"
          + "    key: 17\n"
          + "    value: 63\n"
          + "  }\n"
          + "  positions {\n"
          + "    key: 18\n"
          + "    value: 63\n"
          + "  }\n"
          + "  positions {\n"
          + "    key: 19\n"
          + "    value: 63\n"
          + "  }\n"
          + "}\n";

  @Test
  public void eval() throws Exception {
    CelRuntime runtime =
        CelRuntimeFactory.standardCelRuntimeBuilder()
            .setOptions(CelOptions.DEFAULT)
            .setStandardEnvironmentEnabled(false)
            .addFunctionBindings(
                CelFunctionBinding.fromOverloads(
                    "getThree",
                    CelFunctionBinding.from("getThree_overload", ImmutableList.of(), arg -> 3L)))
            .setStandardFunctions(
                CelStandardFunctions.newBuilder()
                    .includeFunctions(
                        StandardFunction.GREATER_EQUALS,
                        StandardFunction.LOGICAL_NOT,
                        StandardFunction.NOT_STRICTLY_FALSE)
                    .build())
            .addMessageTypes(TestAllTypes.getDescriptor())
            .build();
    CheckedExpr checkedExpr = TextFormat.parse(CHECKED_EXPR, CheckedExpr.class);
    CelAbstractSyntaxTree ast = CelProtoAbstractSyntaxTree.fromCheckedExpr(checkedExpr).getAst();

    boolean result = (boolean) runtime.createProgram(ast).eval();

    assertThat(result).isTrue();
  }

  @Test
  public void eval_error() throws Exception {
    CelRuntime runtime =
        CelRuntimeFactory.standardCelRuntimeBuilder()
            .addMessageTypes(TestAllTypes.getDescriptor())
            .build();
    CheckedExpr checkedExpr = TextFormat.parse(CHECKED_EXPR, CheckedExpr.class);
    CelAbstractSyntaxTree ast = CelProtoAbstractSyntaxTree.fromCheckedExpr(checkedExpr).getAst();

    assertThat(assertThrows(CelEvaluationException.class, () -> runtime.createProgram(ast).eval()))
        .hasMessageThat()
        .contains("No matching overload for function 'getThree'");
  }
}
