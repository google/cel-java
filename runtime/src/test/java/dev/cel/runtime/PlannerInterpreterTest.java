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

package dev.cel.runtime;

import com.google.common.collect.ImmutableMap;
import com.google.protobuf.Timestamp;
import com.google.testing.junit.testparameterinjector.TestParameter;
import com.google.testing.junit.testparameterinjector.TestParameterInjector;
import dev.cel.common.CelAbstractSyntaxTree;
import dev.cel.common.CelContainer;
import dev.cel.common.CelOptions;
import dev.cel.common.CelValidationException;
import dev.cel.common.types.CelTypeProvider;
import dev.cel.common.types.SimpleType;
import dev.cel.common.types.StructTypeReference;
import dev.cel.expr.conformance.proto3.TestAllTypes;
import dev.cel.extensions.CelExtensions;
import dev.cel.testing.BaseInterpreterTest;
import java.util.Arrays;
import java.util.Objects;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Interpreter tests using ProgramPlanner */
@RunWith(TestParameterInjector.class)
public class PlannerInterpreterTest extends BaseInterpreterTest {

  @TestParameter boolean isParseOnly;

  @Override
  protected CelRuntimeBuilder newBaseRuntimeBuilder(CelOptions celOptions) {
    return CelRuntimeExperimentalFactory.plannerRuntimeBuilder()
        .addLateBoundFunctions("record")
        .setOptions(celOptions)
        .addLibraries(CelExtensions.optional())
        .addFileTypes(TEST_FILE_DESCRIPTORS)
        .addMessageTypes(TestAllTypes.getDescriptor());
  }

  @Override
  protected void setContainer(CelContainer container) {
    super.setContainer(container);
    this.celRuntime = this.celRuntime.toRuntimeBuilder().setContainer(container).build();
  }

  @Override
  protected CelAbstractSyntaxTree prepareTest(CelTypeProvider typeProvider) {
    super.prepareCompiler(typeProvider);

    CelAbstractSyntaxTree ast;
    try {
      ast = celCompiler.parse(source, testSourceDescription()).getAst();
    } catch (CelValidationException e) {
      printTestValidationError(e);
      return null;
    }

    if (isParseOnly) {
      return ast;
    }

    try {
      return celCompiler.check(ast).getAst();
    } catch (CelValidationException e) {
      printTestValidationError(e);
      return null;
    }
  }

  @Override
  public void optional_errors() {
    if (isParseOnly) {
      // Parsed-only evaluation contains function name in the
      // error message instead of the function overload.
      skipBaselineVerification();
    } else {
      super.optional_errors();
    }
  }

  @Override
  public void unknownField() {
    // Exercised in planner_unknownField instead
    skipBaselineVerification();
  }

  @Override
  public void unknownResultSet() {
    // Exercised in planner_unknownResultSet_success instead
    skipBaselineVerification();
  }

  @Test
  public void planner_unknownField() {
    setContainer(CelContainer.ofName(TestAllTypes.getDescriptor().getFile().getPackage()));
    declareVariable("x", StructTypeReference.create(TestAllTypes.getDescriptor().getFullName()));

    CelAttributePattern patternX = CelAttributePattern.fromQualifiedIdentifier("x");

    source = "x.single_int32";
    runTestWithUnknowns(ImmutableMap.of(), patternX);
    runTestWithUnknowns(
        ImmutableMap.of(), CelAttributePattern.fromQualifiedIdentifier("x.single_int32"));

    source = "x.map_int32_int64[22]";
    runTestWithUnknowns(ImmutableMap.of(), patternX);
    runTestWithUnknowns(
        ImmutableMap.of(), CelAttributePattern.fromQualifiedIdentifier("x.map_int32_int64"));

    source = "x.repeated_nested_message[1]";
    runTestWithUnknowns(ImmutableMap.of(), patternX);
    runTestWithUnknowns(
        ImmutableMap.of(),
        CelAttributePattern.fromQualifiedIdentifier("x.repeated_nested_message"));

    source = "x.single_nested_message.bb";
    runTestWithUnknowns(ImmutableMap.of(), patternX);
    runTestWithUnknowns(
        ImmutableMap.of(),
        CelAttributePattern.fromQualifiedIdentifier("x.single_nested_message.bb"));

    source = "{1: x.single_int32}";
    runTestWithUnknowns(ImmutableMap.of(), patternX);
    runTestWithUnknowns(
        ImmutableMap.of(), CelAttributePattern.fromQualifiedIdentifier("x.single_int32"));

    source = "[1, x.single_int32]";
    runTestWithUnknowns(ImmutableMap.of(), patternX);
    runTestWithUnknowns(
        ImmutableMap.of(), CelAttributePattern.fromQualifiedIdentifier("x.single_int32"));
  }

  @Test
  public void planner_unknownResultSet_success() {
    setContainer(CelContainer.ofName(TestAllTypes.getDescriptor().getFile().getPackage()));
    declareVariable("x", StructTypeReference.create(TestAllTypes.getDescriptor().getFullName()));
    TestAllTypes message =
        TestAllTypes.newBuilder()
            .setSingleString("test")
            .setSingleTimestamp(Timestamp.newBuilder().setSeconds(15))
            .build();
    ImmutableMap<String, ?> variables = ImmutableMap.of("x", message);
    CelAttributePattern unknownInt32 =
        CelAttributePattern.fromQualifiedIdentifier("x.single_int32");
    CelAttributePattern unknownInt64 =
        CelAttributePattern.fromQualifiedIdentifier("x.single_int64");

    source = "x.single_int32 == 1 && true";
    runTestWithUnknowns(variables, unknownInt32);

    source = "x.single_int32 == 1 && false";
    runTestWithUnknowns(variables, unknownInt32);

    source = "x.single_int32 == 1 && x.single_int64 == 1";
    runTestWithUnknowns(variables, unknownInt32, unknownInt64);

    source = "true && x.single_int32 == 1";
    runTestWithUnknowns(variables, unknownInt32);

    source = "false && x.single_int32 == 1";
    runTestWithUnknowns(variables, unknownInt32);

    source = "x.single_int32 == 1 || x.single_string == \"test\"";
    runTestWithUnknowns(variables, unknownInt32);

    source = "x.single_int32 == 1 || x.single_string != \"test\"";
    runTestWithUnknowns(variables, unknownInt32);

    source = "x.single_int32 == 1 || x.single_int64 == 1";
    runTestWithUnknowns(variables, unknownInt32, unknownInt64);

    source = "true || x.single_int32 == 1";
    runTestWithUnknowns(variables, unknownInt32);

    source = "false || x.single_int32 == 1";
    runTestWithUnknowns(variables, unknownInt32);

    // dispatch test
    declareFunction(
        "f", memberOverload("f", Arrays.asList(SimpleType.INT, SimpleType.INT), SimpleType.BOOL));
    celRuntime =
        newBaseRuntimeBuilder(
                CelOptions.current()
                    .enableTimestampEpoch(true)
                    .enableHeterogeneousNumericComparisons(true)
                    .enableOptionalSyntax(true)
                    .comprehensionMaxIterations(1_000)
                    .build())
            .addFunctionBindings(
                CelFunctionBinding.from("f", Integer.class, Integer.class, Objects::equals))
            .setContainer(CelContainer.ofName(TestAllTypes.getDescriptor().getFile().getPackage()))
            .build();

    source = "x.single_int32.f(1)";
    runTestWithUnknowns(variables, unknownInt32);

    source = "1.f(x.single_int32)";
    runTestWithUnknowns(variables, unknownInt32);

    source = "x.single_int64.f(x.single_int32)";
    runTestWithUnknowns(variables, unknownInt32, unknownInt64);

    source = "[0, 2, 4].exists(z, z == 2 || z == x.single_int32)";
    runTestWithUnknowns(variables, unknownInt32);

    source = "[0, 2, 4].exists(z, z == x.single_int32)";
    runTestWithUnknowns(variables, unknownInt32);

    source =
        "[0, 2, 4].exists_one(z, z == 0 || (z == 2 && z == x.single_int32) "
            + "|| (z == 4 && z == x.single_int64))";
    runTestWithUnknowns(variables, unknownInt32, unknownInt64);

    source = "[0, 2].all(z, z == 2 || z == x.single_int32)";
    runTestWithUnknowns(variables, unknownInt32);

    source =
        "[0, 2, 4].filter(z, z == 0 || (z == 2 && z == x.single_int32) "
            + "|| (z == 4 && z == x.single_int64))";
    runTestWithUnknowns(variables, unknownInt32, unknownInt64);

    source =
        "[0, 2, 4].map(z, z == 0 || (z == 2 && z == x.single_int32) "
            + "|| (z == 4 && z == x.single_int64))";
    runTestWithUnknowns(variables, unknownInt32, unknownInt64);

    source = "x.single_int32 == 1 ? 1 : 2";
    runTestWithUnknowns(variables, unknownInt32);

    source = "true ? x.single_int32 : 2";
    runTestWithUnknowns(variables, unknownInt32);

    source = "true ? 1 : x.single_int32";
    runTestWithUnknowns(variables, unknownInt32);

    source = "false ? x.single_int32 : 2";
    runTestWithUnknowns(variables, unknownInt32);

    source = "false ? 1 : x.single_int32";
    runTestWithUnknowns(variables, unknownInt32);

    source = "x.single_int64 == 1 ? x.single_int32 : x.single_int32";
    runTestWithUnknowns(variables, unknownInt32, unknownInt64);

    source = "{x.single_int32: 2, 3: 4}";
    runTestWithUnknowns(variables, unknownInt32);

    source = "{1: x.single_int32, 3: 4}";
    runTestWithUnknowns(variables, unknownInt32);

    source = "{1: x.single_int32, x.single_int64: 4}";
    runTestWithUnknowns(variables, unknownInt32, unknownInt64);

    source = "[1, x.single_int32, 3, 4]";
    runTestWithUnknowns(variables, unknownInt32);

    source = "[1, x.single_int32, x.single_int64, 4]";
    runTestWithUnknowns(variables, unknownInt32, unknownInt64);

    source = "TestAllTypes{single_int32: x.single_int32}.single_int32 == 2";
    runTestWithUnknowns(variables, unknownInt32);

    source = "TestAllTypes{single_int32: x.single_int32, single_int64: x.single_int64}";
    runTestWithUnknowns(variables, unknownInt32, unknownInt64);
  }

  @Test
  public void planner_unknownResultSet_errors() {
    declareVariable("x", StructTypeReference.create(TestAllTypes.getDescriptor().getFullName()));
    TestAllTypes message =
        TestAllTypes.newBuilder()
            .setSingleString("test")
            .setSingleTimestamp(Timestamp.newBuilder().setSeconds(15))
            .build();
    ImmutableMap<String, ?> variables = ImmutableMap.of("x", message);
    CelAttributePattern unknownInt32 =
        CelAttributePattern.fromQualifiedIdentifier("x.single_int32");

    source = "x.single_int32 == 1 && x.single_timestamp <= timestamp(\"bad timestamp string\")";
    runTestWithUnknowns(variables, unknownInt32);

    source = "x.single_timestamp <= timestamp(\"bad timestamp string\") && x.single_int32 == 1";
    runTestWithUnknowns(variables, unknownInt32);

    source =
        "x.single_timestamp <= timestamp(\"bad timestamp string\") "
            + "&& x.single_timestamp > timestamp(\"another bad timestamp string\")";
    runTestWithUnknowns(variables, unknownInt32);

    source = "x.single_int32 == 1 || x.single_timestamp <= timestamp(\"bad timestamp string\")";
    runTestWithUnknowns(variables, unknownInt32);

    source = "x.single_timestamp <= timestamp(\"bad timestamp string\") || x.single_int32 == 1";
    runTestWithUnknowns(variables, unknownInt32);

    source =
        "x.single_timestamp <= timestamp(\"bad timestamp string\") "
            + "|| x.single_timestamp > timestamp(\"another bad timestamp string\")";
    runTestWithUnknowns(variables, unknownInt32);

    source = "x";
    runTestWithUnknowns(ImmutableMap.of(), CelAttributePattern.fromQualifiedIdentifier("x"));
  }
}
