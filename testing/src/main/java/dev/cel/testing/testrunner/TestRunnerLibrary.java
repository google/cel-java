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

package dev.cel.testing.testrunner;

import static com.google.common.io.Files.asCharSource;
import static dev.cel.testing.utils.ExprValueUtils.fromValue;
import static dev.cel.testing.utils.ExprValueUtils.toExprValue;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.Files.readAllBytes;

import dev.cel.expr.CheckedExpr;
import dev.cel.expr.ExprValue;
import dev.cel.expr.Value;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.protobuf.Any;
import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.ExtensionRegistry;
import com.google.protobuf.Message;
import com.google.protobuf.TextFormat;
import com.google.testing.junit.runner.util.TestPropertyExporter;
import dev.cel.bundle.Cel;
import dev.cel.bundle.CelEnvironment;
import dev.cel.bundle.CelEnvironment.ExtensionConfig;
import dev.cel.bundle.CelEnvironmentYamlParser;
import dev.cel.common.CelAbstractSyntaxTree;
import dev.cel.common.CelOptions;
import dev.cel.common.CelProtoAbstractSyntaxTree;
import dev.cel.common.CelValidationException;
import dev.cel.common.internal.DefaultInstanceMessageFactory;
import dev.cel.policy.CelPolicy;
import dev.cel.policy.CelPolicyCompilerFactory;
import dev.cel.policy.CelPolicyParser;
import dev.cel.policy.CelPolicyParserFactory;
import dev.cel.policy.CelPolicyValidationException;
import dev.cel.runtime.CelEvaluationException;
import dev.cel.runtime.CelRuntime.Program;
import dev.cel.testing.testrunner.CelTestSuite.CelTestSection.CelTestCase;
import dev.cel.testing.testrunner.CelTestSuite.CelTestSection.CelTestCase.Input.Binding;
import dev.cel.testing.testrunner.ResultMatcher.ResultMatcherParams;
import dev.cel.testing.utils.ProtoDescriptorUtils;
import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.logging.Logger;
import org.jspecify.annotations.Nullable;

/** Runner library for creating the environment and running the assertions. */
public final class TestRunnerLibrary {
  TestRunnerLibrary() {}

  private static final Logger logger = Logger.getLogger(TestRunnerLibrary.class.getName());

  private static final String ATTR_CEL_EXPR = "Cel Expr";
  private static final String ATTR_CEL_COVERAGE = "Cel Coverage";
  private static final String ATTR_AST_NODE_COVERAGE = "Ast Node Coverage";
  private static final String ATTR_INTERESTING_UNENCOUNTERED_NODES =
      "Interesting Unencountered Nodes";
  private static final String ATTR_AST_BRANCH_COVERAGE = "Ast Branch Coverage";
  private static final String ATTR_INTERESTING_UNENCOUNTERED_BRANCH_PATHS =
      "Interesting Unencountered Branch Paths";
  private static final String ATTR_CEL_TEST_COVERAGE_GRAPH_URL = "Cel Test Coverage Graph URL";

  /**
   * Run the assertions for a given raw/checked expression test case.
   *
   * @param testCase The test case to run.
   * @param celTestContext The test context containing the {@link Cel} bundle and other
   *     configurations.
   */
  public static void runTest(CelTestCase testCase, CelTestContext celTestContext) throws Exception {
    if (celTestContext.celExpression().isPresent()) {
      evaluateTestCase(testCase, celTestContext);
    } else {
      throw new IllegalArgumentException("No cel expression provided.");
    }
  }

  /**
   * Run the assertions for a given raw/checked expression test case with coverage enabled.
   *
   * <p>This method is used for generating coverage data. It will be used to run the test case
   * multiple times with different inputs and collect the coverage data.
   *
   * @param testCase The test case to run.
   * @param celTestContext The test context containing the {@link Cel} bundle and other
   *     configurations.
   * @param celCoverageIndex The coverage index to use for the test case.
   */
  public static void runTest(
      CelTestCase testCase,
      CelTestContext celTestContext,
      @Nullable CelCoverageIndex celCoverageIndex)
      throws Exception {
    if (celTestContext.celExpression().isPresent()) {
      evaluateTestCase(testCase, celTestContext, celCoverageIndex);
    } else {
      throw new IllegalArgumentException("No cel expression provided.");
    }
  }

  @VisibleForTesting
  static void evaluateTestCase(CelTestCase testCase, CelTestContext celTestContext)
      throws Exception {
    evaluateTestCase(testCase, celTestContext, /* celCoverageIndex= */ null);
  }

  static void evaluateTestCase(
      CelTestCase testCase,
      CelTestContext celTestContext,
      @Nullable CelCoverageIndex celCoverageIndex)
      throws Exception {
    celTestContext = extendCelTestContext(celTestContext);
    CelAbstractSyntaxTree ast;
    CelExpressionSource celExpressionSource = celTestContext.celExpression().get();
    switch (celExpressionSource.type()) {
      case POLICY:
        ast =
            compilePolicy(
                celTestContext.cel(),
                celTestContext.celPolicyParser().get(),
                readFile(celExpressionSource.value()));
        break;
      case TEXTPROTO:
      case BINARYPB:
        ast = readAstFromCheckedExpression(celExpressionSource);
        break;
      case CEL:
        ast = celTestContext.cel().compile(readFile(celExpressionSource.value())).getAst();
        break;
      case RAW_EXPR:
        ast = celTestContext.cel().compile(celExpressionSource.value()).getAst();
        break;
      default:
        throw new IllegalArgumentException(
            "Unsupported expression type: " + celExpressionSource.type());
    }
    if (celCoverageIndex != null) {
      celCoverageIndex.init(ast);
    }
    evaluate(ast, testCase, celTestContext, celCoverageIndex);

    // For programmatic tests, if coverage is not enabled via the build macro, update the Sponge
    // properties with the coverage report.
    // This flag does not exist when the test is run via direct invocation of {@link
    // TestRunnerLibrary#runTest}
    String isCoverageEnabled = System.getProperty("is_coverage_enabled");
    if (isCoverageEnabled == null && celCoverageIndex != null) {
      updateSpongeProperties(celCoverageIndex.generateCoverageReport());
    }
  }

  private static CelAbstractSyntaxTree readAstFromCheckedExpression(
      CelExpressionSource celExpressionSource) throws IOException {
    switch (celExpressionSource.type()) {
      case BINARYPB:
        byte[] bytes = readAllBytes(Paths.get(celExpressionSource.value()));
        CheckedExpr checkedExpr =
            CheckedExpr.parseFrom(bytes, ExtensionRegistry.getEmptyRegistry());
        return CelProtoAbstractSyntaxTree.fromCheckedExpr(checkedExpr).getAst();
      case TEXTPROTO:
        String content = readFile(celExpressionSource.value());
        CheckedExpr.Builder builder = CheckedExpr.newBuilder();
        TextFormat.merge(content, builder);
        return CelProtoAbstractSyntaxTree.fromCheckedExpr(builder.build()).getAst();
      default:
        throw new IllegalArgumentException(
            "Unsupported expression type: " + celExpressionSource.type());
    }
  }

  private static CelTestContext extendCelTestContext(CelTestContext celTestContext)
      throws Exception {
    CelOptions celOptions = celTestContext.celOptions();
    CelTestContext.Builder celTestContextBuilder =
        celTestContext.toBuilder().setCel(extendCel(celTestContext, celOptions));
    if (celTestContext
        .celExpression()
        .get()
        .type()
        .equals(CelExpressionSource.ExpressionSourceType.POLICY)) {
      celTestContextBuilder.setCelPolicyParser(
          celTestContext
              .celPolicyParser()
              .orElse(CelPolicyParserFactory.newYamlParserBuilder().build()));
    }

    return celTestContextBuilder.build();
  }

  private static Cel extendCel(CelTestContext celTestContext, CelOptions celOptions)
      throws Exception {
    Cel extendedCel = celTestContext.cel();

    // Add the file descriptor set to the cel object if provided.
    //
    // Note: This needs to be added first because the config file may contain type information
    // regarding proto messages that need to be added to the cel object.
    if (celTestContext.fileDescriptorSetPath().isPresent()) {
      extendedCel =
          extendedCel
              .toCelBuilder()
              .addMessageTypes(
                  ProtoDescriptorUtils.getAllDescriptorsFromJvm(
                          celTestContext.fileDescriptorSetPath().get())
                      .messageTypeDescriptors())
              .setExtensionRegistry(
                  RegistryUtils.getExtensionRegistry(celTestContext.fileDescriptorSetPath().get()))
              .build();
    }

    CelEnvironment environment = CelEnvironment.newBuilder().build();

    // Extend the cel object with the config file if provided.
    if (celTestContext.configFile().isPresent()) {
      String configContent = readFile(celTestContext.configFile().get());
      environment = CelEnvironmentYamlParser.newInstance().parse(configContent);
    }

    // Policy compiler requires optional support. Add the optional library by default to the
    // environment.
    return environment.toBuilder()
        .addExtensions(ExtensionConfig.of("optional"))
        .build()
        .extend(extendedCel, celOptions);
  }

  private static String readFile(String path) throws IOException {
    return asCharSource(new File(path), UTF_8).read();
  }

  private static CelAbstractSyntaxTree compilePolicy(
      Cel cel, CelPolicyParser celPolicyParser, String policyContent)
      throws CelPolicyValidationException {
    CelPolicy celPolicy = celPolicyParser.parse(policyContent);
    return CelPolicyCompilerFactory.newPolicyCompiler(cel).build().compile(celPolicy);
  }

  private static void evaluate(
      CelAbstractSyntaxTree ast,
      CelTestCase testCase,
      CelTestContext celTestContext,
      @Nullable CelCoverageIndex celCoverageIndex)
      throws Exception {
    Cel cel = celTestContext.cel();
    Program program = cel.createProgram(ast);
    ExprValue exprValue = null;
    CelEvaluationException error = null;
    Object evaluationResult = null;
    try {
      evaluationResult = getEvaluationResult(testCase, celTestContext, program, celCoverageIndex);
      exprValue = toExprValue(evaluationResult, ast.getResultType());
    } catch (CelEvaluationException e) {
      String errorMessage =
          String.format(
              "Evaluation failed for test case: %s. Error: %s", testCase.name(), e.getMessage());
      error = new CelEvaluationException(errorMessage, e);
      logger.severe(e.toString());
    }

    // Perform the assertion on the result of the evaluation.
    ResultMatcherParams.Builder paramsBuilder =
        ResultMatcherParams.newBuilder()
            .setExpectedOutput(Optional.ofNullable(testCase.output()))
            .setResultType(ast.getResultType());

    if (error != null) {
      paramsBuilder.setComputedOutput(ResultMatcherParams.ComputedOutput.ofError(error));
    } else {
      switch (exprValue.getKindCase()) {
        case VALUE:
          paramsBuilder.setComputedOutput(
              ResultMatcherParams.ComputedOutput.ofExprValue(exprValue));
          break;
        case UNKNOWN:
          paramsBuilder.setComputedOutput(
              ResultMatcherParams.ComputedOutput.ofUnknownSet(
                  ImmutableList.copyOf(exprValue.getUnknown().getExprsList())));
          break;
        default:
          throw new IllegalArgumentException(
              String.format("Unexpected result type: %s", exprValue.getKindCase()));
      }
    }

    celTestContext.resultMatcher().match(paramsBuilder.build(), cel);
  }

  private static Object getEvaluationResult(
      CelTestCase testCase,
      CelTestContext celTestContext,
      Program program,
      @Nullable CelCoverageIndex celCoverageIndex)
      throws CelEvaluationException, IOException, CelValidationException {
    if (celTestContext.celLateFunctionBindings().isPresent()) {
      return program.eval(
          getBindings(testCase, celTestContext), celTestContext.celLateFunctionBindings().get());
    }
    switch (testCase.input().kind()) {
      case CONTEXT_MESSAGE:
        return getEvaluationResultWithMessage(
            unpackAny(testCase.input().contextMessage(), celTestContext),
            program,
            celCoverageIndex);
      case CONTEXT_EXPR:
        return getEvaluationResultWithMessage(
            getEvaluatedContextExpr(testCase, celTestContext), program, celCoverageIndex);
      case BINDINGS:
        return getEvaluationResultWithBindings(
            getBindings(testCase, celTestContext), program, celCoverageIndex);
      case NO_INPUT:
        ImmutableMap.Builder<String, Object> newBindings = ImmutableMap.builder();
        for (Map.Entry<String, Object> entry : celTestContext.variableBindings().entrySet()) {
          if (entry.getValue() instanceof Any) {
            newBindings.put(entry.getKey(), unpackAny((Any) entry.getValue(), celTestContext));
          } else {
            newBindings.put(entry);
          }
        }
        return getEvaluationResultWithBindings(
            newBindings.buildOrThrow(), program, celCoverageIndex);
    }
    throw new IllegalArgumentException("Unexpected input type: " + testCase.input().kind());
  }

  private static Object getEvaluationResultWithBindings(
      Map<String, Object> bindings, Program program, @Nullable CelCoverageIndex celCoverageIndex)
      throws CelEvaluationException {
    if (celCoverageIndex != null) {
      return program.trace(bindings, celCoverageIndex.newEvaluationListener());
    }
    return program.eval(bindings);
  }

  private static Object getEvaluationResultWithMessage(
      Message message, Program program, @Nullable CelCoverageIndex celCoverageIndex)
      throws CelEvaluationException {
    if (celCoverageIndex != null) {
      return program.trace(message, celCoverageIndex.newEvaluationListener());
    }
    return program.eval(message);
  }

  private static Message unpackAny(Any any, CelTestContext celTestContext) throws IOException {
    if (!celTestContext.fileDescriptorSetPath().isPresent()) {
      throw new IllegalArgumentException(
          "Proto descriptors are required for unpacking Any messages.");
    }
    Descriptor descriptor =
        RegistryUtils.getTypeRegistry(celTestContext.fileDescriptorSetPath().get())
            .getDescriptorForTypeUrl(any.getTypeUrl());
    return getDefaultInstance(descriptor)
        .getParserForType()
        .parseFrom(
            any.getValue(),
            RegistryUtils.getExtensionRegistry(celTestContext.fileDescriptorSetPath().get()));
  }

  private static Message getDefaultInstance(Descriptor descriptor) throws IOException {
    return DefaultInstanceMessageFactory.getInstance()
        .getPrototype(descriptor)
        .orElseThrow(
            () ->
                new NoSuchElementException(
                    "Could not find a default message for: " + descriptor.getFullName()));
  }

  private static Message getEvaluatedContextExpr(
      CelTestCase testCase, CelTestContext celTestContext)
      throws CelEvaluationException, CelValidationException {
    try {
      return (Message) evaluateInput(celTestContext.cel(), testCase.input().contextExpr());
    } catch (ClassCastException e) {
      throw new IllegalArgumentException("Context expression must evaluate to a proto message.", e);
    }
  }

  private static ImmutableMap<String, Object> getBindings(
      CelTestCase testCase, CelTestContext celTestContext)
      throws IOException, CelEvaluationException, CelValidationException {
    Cel cel = celTestContext.cel();
    ImmutableMap.Builder<String, Object> inputBuilder = ImmutableMap.builder();
    for (Map.Entry<String, Binding> entry : testCase.input().bindings().entrySet()) {
      if (entry.getValue().kind().equals(Binding.Kind.EXPR)) {
        inputBuilder.put(entry.getKey(), evaluateInput(cel, entry.getValue().expr()));
      } else {
        inputBuilder.put(
            entry.getKey(), getValueFromBinding(entry.getValue().value(), celTestContext));
      }
    }
    return inputBuilder.buildOrThrow();
  }

  private static Object evaluateInput(Cel cel, String expr)
      throws CelEvaluationException, CelValidationException {
    CelAbstractSyntaxTree exprInputAst = cel.compile(expr).getAst();
    return cel.createProgram(exprInputAst).eval();
  }

  private static Object getValueFromBinding(Object value, CelTestContext celTestContext)
      throws IOException {
    if (value instanceof Value) {
      if (celTestContext.fileDescriptorSetPath().isPresent()) {
        return fromValue((Value) value, celTestContext.fileDescriptorSetPath().get());
      }
      return fromValue((Value) value);
    }
    return value;
  }

  /**
   * Updates bazel/blaze test properties with the provided coverage report.
   *
   * <p>This method is called when {@link TestRunnerLibrary#runTest} is invoked directly to export
   * coverage data.
   */
  private static void updateSpongeProperties(CelCoverageIndex.CoverageReport report) {
    TestPropertyExporter exporter = TestPropertyExporter.INSTANCE;
    if (report.nodes() == 0) {
      exporter.exportProperty(ATTR_CEL_COVERAGE, "No coverage stats found");
    } else {
      // CEL expression
      exporter.exportProperty(ATTR_CEL_EXPR, report.celExpression());
      // Node coverage
      double nodeCoverage = (double) report.coveredNodes() / (double) report.nodes() * 100.0;
      String nodeCoverageString =
          String.format(
              "%.2f%% (%d out of %d nodes covered)",
              nodeCoverage, report.coveredNodes(), report.nodes());
      exporter.exportProperty(ATTR_AST_NODE_COVERAGE, nodeCoverageString);
      if (!report.unencounteredNodes().isEmpty()) {
        exporter.exportProperty(
            ATTR_INTERESTING_UNENCOUNTERED_NODES, String.join("\n", report.unencounteredNodes()));
      }
      // Branch coverage
      double branchCoverage = 0.0;
      if (report.branches() > 0) {
        branchCoverage =
            (double) report.coveredBooleanOutcomes() / (double) report.branches() * 100.0;
      }
      String branchCoverageString =
          String.format(
              "%.2f%% (%d out of %d branch outcomes covered)",
              branchCoverage, report.coveredBooleanOutcomes(), report.branches());
      exporter.exportProperty(ATTR_AST_BRANCH_COVERAGE, branchCoverageString);
      if (!report.unencounteredBranches().isEmpty()) {
        exporter.exportProperty(
            ATTR_INTERESTING_UNENCOUNTERED_BRANCH_PATHS,
            String.join("\n", report.unencounteredBranches()));
      }
    }
  }
}
