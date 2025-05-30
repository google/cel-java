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
import com.google.auto.value.AutoValue;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.protobuf.Any;
import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.FileDescriptor;
import com.google.protobuf.DynamicMessage;
import com.google.protobuf.ExtensionRegistry;
import com.google.protobuf.Message;
import com.google.protobuf.TextFormat;
import dev.cel.bundle.Cel;
import dev.cel.bundle.CelEnvironment;
import dev.cel.bundle.CelEnvironment.ExtensionConfig;
import dev.cel.bundle.CelEnvironmentException;
import dev.cel.bundle.CelEnvironmentYamlParser;
import dev.cel.common.CelAbstractSyntaxTree;
import dev.cel.common.CelDescriptorUtil;
import dev.cel.common.CelOptions;
import dev.cel.common.CelProtoAbstractSyntaxTree;
import dev.cel.common.CelValidationException;
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
import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;

/** Runner library for creating the environment and running the assertions. */
public final class TestRunnerLibrary {
  TestRunnerLibrary() {}

  private static final Logger logger = Logger.getLogger(TestRunnerLibrary.class.getName());

  private static final String CEL_EXPR_SYSTEM_PROPERTY = "cel_expr";

  /**
   * Run the assertions for a given raw/checked expression test case.
   *
   * @param testCase The test case to run.
   * @param celTestContext The test context containing the {@link Cel} bundle and other
   *     configurations.
   */
  public static void runTest(CelTestCase testCase, CelTestContext celTestContext) throws Exception {
    String celExpression = System.getProperty(CEL_EXPR_SYSTEM_PROPERTY);
    CelExprFileSource celExprFileSource = CelExprFileSource.fromFile(celExpression);
    evaluateTestCase(testCase, celTestContext, celExprFileSource);
  }

  @VisibleForTesting
  static void evaluateTestCase(
      CelTestCase testCase, CelTestContext celTestContext, CelExprFileSource celExprFileSource)
      throws Exception {
    celTestContext = extendCelTestContext(celTestContext, celExprFileSource);
    CelAbstractSyntaxTree ast;
    switch (celExprFileSource.type()) {
      case POLICY:
        ast =
            compilePolicy(
                celTestContext.cel(),
                celTestContext.celPolicyParser().get(),
                readFile(celExprFileSource.value()));
        break;
      case TEXTPROTO:
      case BINARYPB:
        ast = readAstFromCheckedExpression(celExprFileSource);
        break;
      case CEL:
        ast = celTestContext.cel().compile(readFile(celExprFileSource.value())).getAst();
        break;
      case RAW_EXPR:
        ast = celTestContext.cel().compile(celExprFileSource.value()).getAst();
        break;
      default:
        throw new IllegalArgumentException(
            "Unsupported expression type: " + celExprFileSource.type());
    }
    evaluate(ast, testCase, celTestContext);
  }

  private static CelAbstractSyntaxTree readAstFromCheckedExpression(
      CelExprFileSource celExprFileSource) throws IOException {
    switch (celExprFileSource.type()) {
      case BINARYPB:
        byte[] bytes = readAllBytes(Paths.get(celExprFileSource.value()));
        CheckedExpr checkedExpr =
            CheckedExpr.parseFrom(bytes, ExtensionRegistry.getEmptyRegistry());
        return CelProtoAbstractSyntaxTree.fromCheckedExpr(checkedExpr).getAst();
      case TEXTPROTO:
        String content = readFile(celExprFileSource.value());
        CheckedExpr.Builder builder = CheckedExpr.newBuilder();
        TextFormat.merge(content, builder);
        return CelProtoAbstractSyntaxTree.fromCheckedExpr(builder.build()).getAst();
      default:
        throw new IllegalArgumentException(
            "Unsupported expression type: " + celExprFileSource.type());
    }
  }

  private static CelTestContext extendCelTestContext(
      CelTestContext celTestContext, CelExprFileSource celExprFileSource)
      throws CelEnvironmentException, IOException {
    CelOptions celOptions = celTestContext.celOptions();
    CelTestContext.Builder celTestContextBuilder =
        celTestContext.toBuilder().setCel(extendCel(celTestContext.cel(), celOptions));
    if (celExprFileSource.type().equals(ExpressionFileType.POLICY)) {
      celTestContextBuilder.setCelPolicyParser(
          celTestContext
              .celPolicyParser()
              .orElse(CelPolicyParserFactory.newYamlParserBuilder().build()));
    }

    return celTestContextBuilder.build();
  }

  private static Cel extendCel(Cel cel, CelOptions celOptions)
      throws IOException, CelEnvironmentException {
    Cel extendedCel = cel;

    // Add the file descriptor set to the cel object if provided.
    //
    // Note: This needs to be added first because the config file may contain type information
    // regarding proto messages that need to be added to the cel object.
    String fileDescriptorSetPath = System.getProperty("file_descriptor_set_path");
    if (fileDescriptorSetPath != null) {
      extendedCel =
          cel.toCelBuilder()
              .addFileTypes(RegistryUtils.getFileDescriptorSet(fileDescriptorSetPath))
              .build();
    }

    CelEnvironment environment = CelEnvironment.newBuilder().build();

    // Extend the cel object with the config file if provided.
    String configPath = System.getProperty("config_path");
    if (configPath != null) {
      String configContent = readFile(configPath);
      environment = CelEnvironmentYamlParser.newInstance().parse(configContent);
    }

    // Policy compiler requires optional support. Add the optional library by default to the
    // environment.
    return environment.toBuilder()
        .addExtensions(ExtensionConfig.of("optional"))
        .build()
        .extend(extendedCel, celOptions);
  }

  /**
   * CelExprFileSource is an encapsulation around cel_expr file format argument accepted in
   * cel_java_test bzl macro. It either holds a {@link CheckedExpr} in binarypb/textproto format, a
   * serialized {@link CelPolicy} file in yaml/celpolicy format or a raw cel expression in cel file
   * format or string format.
   */
  @AutoValue
  abstract static class CelExprFileSource {

    abstract String value();

    abstract ExpressionFileType type();

    static CelExprFileSource fromFile(String value) {
      return new AutoValue_TestRunnerLibrary_CelExprFileSource(
          value, ExpressionFileType.fromFile(value));
    }
  }

  enum ExpressionFileType {
    BINARYPB,
    TEXTPROTO,
    POLICY,
    CEL,
    RAW_EXPR;

    static ExpressionFileType fromFile(String filePath) {
      if (filePath.endsWith(".binarypb")) {
        return BINARYPB;
      }
      if (filePath.endsWith(".textproto")) {
        return TEXTPROTO;
      }
      if (filePath.endsWith(".yaml") || filePath.endsWith(".celpolicy")) {
        return POLICY;
      }
      if (filePath.endsWith(".cel")) {
        return CEL;
      }
      if (System.getProperty("is_raw_expr").equals("True")) {
        return RAW_EXPR;
      }
      throw new IllegalArgumentException("Unsupported expression type: " + filePath);
    }
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
      CelAbstractSyntaxTree ast, CelTestCase testCase, CelTestContext celTestContext)
      throws Exception {
    Cel cel = celTestContext.cel();
    Program program = cel.createProgram(ast);
    ExprValue exprValue = null;
    CelEvaluationException error = null;
    Object evaluationResult = null;
    try {
      evaluationResult = getEvaluationResult(testCase, celTestContext, program);
      exprValue = toExprValue(evaluationResult, ast.getResultType());
    } catch (CelEvaluationException e) {
      String errorMessage =
          String.format(
              "Evaluation failed for test case: %s. Error: %s", testCase.name(), e.getMessage());
      error = new CelEvaluationException(errorMessage, e);
      logger.severe(errorMessage);
    }

    // Perform the assertion on the result of the evaluation.
    ResultMatcherParams.Builder paramsBuilder =
        ResultMatcherParams.newBuilder()
            .setExpectedOutput(Optional.ofNullable(testCase.output()))
            .setResultType(ast.getResultType());

    if (exprValue != null) {
      paramsBuilder.setComputedOutput(ResultMatcherParams.ComputedOutput.ofExprValue(exprValue));
    } else {
      paramsBuilder.setComputedOutput(ResultMatcherParams.ComputedOutput.ofError(error));
    }

    celTestContext.resultMatcher().match(paramsBuilder.build(), cel);
  }

  private static Object getEvaluationResult(
      CelTestCase testCase, CelTestContext celTestContext, Program program) throws Exception {
    if (celTestContext.celLateFunctionBindings().isPresent()) {
      return program.eval(
          getBindings(testCase, celTestContext), celTestContext.celLateFunctionBindings().get());
    }
    switch (testCase.input().kind()) {
      case CONTEXT_MESSAGE:
        return program.eval(
            unpackAny(
                testCase.input().contextMessage(), System.getProperty("file_descriptor_set_path")));
      case CONTEXT_EXPR:
        return program.eval(getEvaluatedContextExpr(testCase, celTestContext));
      case BINDINGS:
        return program.eval(getBindings(testCase, celTestContext));
      case NO_INPUT:
        return program.eval(celTestContext.variableBindings());
    }
    throw new IllegalArgumentException("Unexpected input type: " + testCase.input().kind());
  }

  // TODO: Remove DynamicMessage parsing once default instance generation is fixed.
  //
  // Dynamic Message parsing is added here to make the default instance generation code OSS
  // compatible. Otherwise, we'd have to depend on AnyUtil which is not available in OSS.
  // However, the functionality fails in OSS as the generated DynamicMessage descriptor is different
  // from the message descriptor.
  private static Message unpackAny(Any any, String fileDescriptorSetPath) throws IOException {
    Preconditions.checkNotNull(
        fileDescriptorSetPath,
        "File descriptor set is required to unpack Any of type: %s.",
        any.getTypeUrl());
    ImmutableSet<FileDescriptor> fileDescriptors =
        CelDescriptorUtil.getFileDescriptorsFromFileDescriptorSet(
            RegistryUtils.getFileDescriptorSet(fileDescriptorSetPath));
    Descriptor descriptor =
        RegistryUtils.getTypeRegistry(fileDescriptors).getDescriptorForTypeUrl(any.getTypeUrl());
    return DynamicMessage.parseFrom(
        descriptor, any.getValue(), RegistryUtils.getExtensionRegistry(fileDescriptors));
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
      throws CelEvaluationException, CelValidationException, IOException {
    Cel cel = celTestContext.cel();
    ImmutableMap.Builder<String, Object> inputBuilder = ImmutableMap.builder();
    for (Map.Entry<String, Binding> entry : testCase.input().bindings().entrySet()) {
      if (entry.getValue().kind().equals(Binding.Kind.EXPR)) {
        inputBuilder.put(entry.getKey(), evaluateInput(cel, entry.getValue().expr()));
      } else {
        Object value;
        if (entry.getValue().value() instanceof Value) {
          value = fromValue((Value) entry.getValue().value());
        } else {
          value = entry.getValue().value();
        }
        inputBuilder.put(entry.getKey(), value);
      }
    }
    return inputBuilder.buildOrThrow();
  }

  private static Object evaluateInput(Cel cel, String expr)
      throws CelEvaluationException, CelValidationException {
    CelAbstractSyntaxTree exprInputAst = cel.compile(expr).getAst();
    return cel.createProgram(exprInputAst).eval();
  }
}
