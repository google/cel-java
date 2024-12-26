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

package dev.cel.testing;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.truth.Truth.assertWithMessage;

import dev.cel.expr.Decl;
import dev.cel.expr.Decl.FunctionDecl.Overload;
import dev.cel.expr.Type;
import com.google.common.collect.ImmutableSet;
import com.google.protobuf.DescriptorProtos.FileDescriptorSet;
import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.FileDescriptor;
import dev.cel.common.CelAbstractSyntaxTree;
import dev.cel.common.CelOptions;
import dev.cel.common.CelOverloadDecl;
import dev.cel.common.CelValidationException;
import dev.cel.common.types.CelProtoTypes;
import dev.cel.common.types.CelType;
import dev.cel.common.types.CelTypeProvider;
import dev.cel.common.types.CelTypes;
import dev.cel.common.types.ProtoMessageTypeProvider;
import dev.cel.compiler.CelCompiler;
import dev.cel.compiler.CelCompilerBuilder;
import dev.cel.compiler.CelCompilerFactory;
import dev.cel.parser.CelStandardMacro;
import java.util.ArrayList;
import java.util.List;

/**
 * Base class for CEL tests which generate a string output that can be stored and used as a baseline
 * to ensure consistent behavior on future test runs.
 */
public abstract class CelBaselineTestCase extends BaselineTestCase {
  private final boolean declareWithCelTypes;
  private final List<TestDecl> decls = new ArrayList<>();

  protected String source;
  protected String container = "";
  protected CelType expectedType;
  protected CelCompiler celCompiler;

  protected static final int COMPREHENSION_MAX_ITERATIONS = 1_000;
  protected static final CelOptions TEST_OPTIONS =
      CelOptions.current()
          .enableTimestampEpoch(true)
          .enableHeterogeneousNumericComparisons(true)
          .enableOptionalSyntax(true)
          .comprehensionMaxIterations(1_000)
          .build();

  /**
   * @param declareWithCelTypes If true, variables, functions and their overloads are declared
   *     internally using java native types {@link CelType}. This will also make the declarations to
   *     be loaded via their type equivalent APIs to the compiler. (Example: {@link
   *     CelCompilerBuilder#addFunctionDeclarations} vs. {@link CelCompilerBuilder#addDeclarations}
   *     ). Setting false will declare these using protobuf types {@link Type} instead.
   */
  protected CelBaselineTestCase(boolean declareWithCelTypes) {
    this.declareWithCelTypes = declareWithCelTypes;
  }

  protected CelAbstractSyntaxTree prepareTest(List<FileDescriptor> descriptors) {
    return prepareTest(new ProtoMessageTypeProvider(ImmutableSet.copyOf(descriptors)));
  }

  protected CelAbstractSyntaxTree prepareTest(Iterable<Descriptor> descriptors) {
    return prepareTest(new ProtoMessageTypeProvider(descriptors));
  }

  protected CelAbstractSyntaxTree prepareTest(FileDescriptorSet descriptorSet) {
    return prepareTest(new ProtoMessageTypeProvider(descriptorSet));
  }

  private CelAbstractSyntaxTree prepareTest(CelTypeProvider typeProvider) {
    prepareCompiler(typeProvider);

    CelAbstractSyntaxTree ast;
    try {
      ast = celCompiler.parse(source, testSourceDescription()).getAst();
    } catch (CelValidationException e) {
      printTestValidationError(e);
      return null;
    }

    try {
      return celCompiler.check(ast).getAst();
    } catch (CelValidationException e) {
      printTestValidationError(e);
      return null;
    }
  }

  private void validateTestSetup() {
    assertWithMessage("The source field must be non-null").that(source).isNotNull();
    if (declareWithCelTypes) {
      assertWithMessage(
              "Test is incorrectly setup. Declarations must be done with CEL native types with"
                  + " declareWithCelTypes enabled")
          .that(
              decls.stream()
                  .filter(
                      d ->
                          d instanceof TestProtoFunctionDeclWrapper
                              || d instanceof TestProtoVariableDeclWrapper)
                  .count())
          .isEqualTo(0);
    } else {
      assertWithMessage(
              "Test is incorrectly setup. Declarations must be done with proto types with"
                  + " declareWithCelTypes disabled.")
          .that(
              decls.stream()
                  .filter(
                      d ->
                          d instanceof TestCelFunctionDeclWrapper
                              || d instanceof TestCelVariableDeclWrapper)
                  .count())
          .isEqualTo(0);
    }
  }

  protected void prepareCompiler(CelTypeProvider typeProvider) {
    validateTestSetup();
    printTestSetup();

    CelCompilerBuilder celCompilerBuilder =
        CelCompilerFactory.standardCelCompilerBuilder()
            .setOptions(TEST_OPTIONS)
            .setStandardMacros(CelStandardMacro.STANDARD_MACROS)
            .setContainer(container)
            .setTypeProvider(typeProvider);

    if (expectedType != null) {
      celCompilerBuilder.setResultType(expectedType);
    }

    // Add the function declarations appropriate to the type we're working with (Either CelType or
    // Protobuf Type)
    decls.forEach(d -> d.loadDeclsToCompiler(celCompilerBuilder));

    celCompiler = celCompilerBuilder.build();
  }

  /**
   * Declares a variable with a given {@code name} and {@code type}.
   *
   * @param name Variable name
   */
  protected void declareVariable(String name, Type type) {
    TestDecl varDecl =
        this.declareWithCelTypes
            ? new TestCelVariableDeclWrapper(name, type)
            : new TestProtoVariableDeclWrapper(name, type);
    decls.add(varDecl);
  }

  /** Clears all function and variable declarations. */
  protected void clearAllDeclarations() {
    decls.clear();
  }

  /** Returns the test source description. */
  protected String testSourceDescription() {
    return "test_location";
  }

  protected void printTestSetup() {
    // Print the source.
    testOutput().printf("Source: %s%n", source);
    for (TestDecl testDecl : decls) {
      testOutput().println(formatDecl(testDecl.getDecl()));
    }
    testOutput().println("=====>");
  }

  protected String formatDecl(Decl decl) {
    StringBuilder declStr = new StringBuilder();
    declStr.append(String.format("declare %s {%n", decl.getName()));
    formatDeclImpl(decl, declStr);
    declStr.append("}");
    return declStr.toString();
  }

  protected String formatDecl(String name, List<Decl> declarations) {
    StringBuilder declStr = new StringBuilder();
    declStr.append(String.format("declare %s {%n", name));
    for (Decl decl : declarations) {
      formatDeclImpl(decl, declStr);
    }
    declStr.append("}");
    return declStr.toString();
  }

  private void formatDeclImpl(Decl decl, StringBuilder declStr) {
    switch (decl.getDeclKindCase()) {
      case IDENT:
        declStr.append(
            String.format("  value %s%n", CelProtoTypes.format(decl.getIdent().getType())));
        break;
      case FUNCTION:
        for (Overload overload : decl.getFunction().getOverloadsList()) {
          declStr.append(
              String.format(
                  "  function %s %s%n",
                  overload.getOverloadId(),
                  CelTypes.formatFunction(
                      CelProtoTypes.typeToCelType(overload.getResultType()),
                      overload.getParamsList().stream()
                          .map(CelProtoTypes::typeToCelType)
                          .collect(toImmutableList()),
                      overload.getIsInstanceFunction(),
                      /* typeParamToDyn= */ false)));
        }
        break;
      default:
        break;
    }
  }

  /**
   * Declares a function with one or more overloads
   *
   * @param functionName Function name
   * @param overloads Function overloads in protobuf representation. If {@link #declareWithCelTypes}
   *     is set, the protobuf overloads are internally converted into java native versions {@link
   *     CelOverloadDecl}.
   */
  protected void declareFunction(String functionName, Overload... overloads) {
    TestDecl functionDecl =
        this.declareWithCelTypes
            ? new TestCelFunctionDeclWrapper(functionName, overloads)
            : new TestProtoFunctionDeclWrapper(functionName, overloads);
    this.decls.add(functionDecl);
  }

  protected void declareGlobalFunction(String name, List<Type> paramTypes, Type resultType) {
    declareFunction(name, globalOverload(name, paramTypes, resultType));
  }

  protected void declareMemberFunction(String name, List<Type> paramTypes, Type resultType) {
    declareFunction(name, memberOverload(name, paramTypes, resultType));
  }

  protected Overload memberOverload(String overloadId, List<Type> paramTypes, Type resultType) {
    return overload(overloadId, paramTypes, resultType).setIsInstanceFunction(true).build();
  }

  protected Overload memberOverload(
      String overloadId, List<Type> paramTypes, List<String> typeParams, Type resultType) {
    return overload(overloadId, paramTypes, resultType)
        .addAllTypeParams(typeParams)
        .setIsInstanceFunction(true)
        .build();
  }

  protected Overload globalOverload(String overloadId, List<Type> paramTypes, Type resultType) {
    return overload(overloadId, paramTypes, resultType).build();
  }

  protected Overload globalOverload(
      String overloadId, List<Type> paramTypes, List<String> typeParams, Type resultType) {
    return overload(overloadId, paramTypes, resultType).addAllTypeParams(typeParams).build();
  }

  private Overload.Builder overload(String overloadId, List<Type> paramTypes, Type resultType) {
    return Overload.newBuilder()
        .setOverloadId(overloadId)
        .setResultType(resultType)
        .addAllParams(paramTypes);
  }

  protected void printTestValidationError(CelValidationException error) {
    testOutput().println(error.getMessage());
  }
}
