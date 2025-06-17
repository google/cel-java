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

import dev.cel.expr.Status;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.protobuf.ExtensionRegistry;
import com.google.protobuf.TextFormat;
import com.google.protobuf.TextFormat.ParseException;
import com.google.protobuf.TypeRegistry;
import dev.cel.expr.conformance.test.InputValue;
import dev.cel.expr.conformance.test.TestCase;
import dev.cel.expr.conformance.test.TestSection;
import dev.cel.expr.conformance.test.TestSuite;
import dev.cel.testing.testrunner.CelTestSuite.CelTestSection;
import dev.cel.testing.testrunner.CelTestSuite.CelTestSection.CelTestCase;
import dev.cel.testing.testrunner.CelTestSuite.CelTestSection.CelTestCase.Input.Binding;
import java.io.IOException;
import java.util.Map;

/**
 * CelTestSuiteTextProtoParser intakes a textproto document that describes the structure of a CEL
 * test suite, parses it then creates a {@link CelTestSuite}.
 */
final class CelTestSuiteTextProtoParser {

  /** Creates a new instance of {@link CelTestSuiteTextProtoParser}. */
  static CelTestSuiteTextProtoParser newInstance() {
    return new CelTestSuiteTextProtoParser();
  }

  CelTestSuite parse(String textProto) throws IOException, CelTestSuiteException {
    TestSuite testSuite = parseTestSuite(textProto);
    return parseCelTestSuite(testSuite);
  }

  private TestSuite parseTestSuite(String textProto) throws IOException {
    String fileDescriptorSetPath = System.getProperty("file_descriptor_set_path");
    TypeRegistry typeRegistry = TypeRegistry.getEmptyTypeRegistry();
    ExtensionRegistry extensionRegistry = ExtensionRegistry.getEmptyRegistry();
    if (fileDescriptorSetPath != null) {
      extensionRegistry = RegistryUtils.getExtensionRegistry();
      typeRegistry = RegistryUtils.getTypeRegistry();
    }
    TextFormat.Parser parser = TextFormat.Parser.newBuilder().setTypeRegistry(typeRegistry).build();
    TestSuite.Builder builder = TestSuite.newBuilder();
    try {
      parser.merge(textProto, extensionRegistry, builder);
    } catch (ParseException e) {
      throw new IllegalArgumentException("Failed to parse test suite", e);
    }
    return builder.build();
  }

  @VisibleForTesting
  static CelTestSuite parseCelTestSuite(TestSuite testSuite) throws CelTestSuiteException {
    CelTestSuite.Builder builder =
        CelTestSuite.newBuilder()
            .setName(testSuite.getName())
            .setDescription(testSuite.getDescription());
    ImmutableSet.Builder<CelTestSection> sectionSetBuilder = ImmutableSet.builder();

    for (TestSection section : testSuite.getSectionsList()) {
      CelTestSection.Builder sectionBuilder =
          CelTestSection.newBuilder()
              .setName(section.getName())
              .setDescription(section.getDescription());
      ImmutableSet.Builder<CelTestCase> testCaseSetBuilder = ImmutableSet.builder();

      for (TestCase testCase : section.getTestsList()) {
        CelTestCase.Builder testCaseBuilder =
            CelTestCase.newBuilder()
                .setName(testCase.getName())
                .setDescription(testCase.getDescription());
        addInputs(testCaseBuilder, testCase);
        addOutputs(testCaseBuilder, testCase);
        testCaseSetBuilder.add(testCaseBuilder.build());
      }

      sectionBuilder.setTests(testCaseSetBuilder.build());
      sectionSetBuilder.add(sectionBuilder.build());
    }
    return builder.setSections(sectionSetBuilder.build()).build();
  }

  private static void addInputs(CelTestCase.Builder testCaseBuilder, TestCase testCase)
      throws CelTestSuiteException {
    if (testCase.getInputCount() > 0 && testCase.hasInputContext()) {
      throw new CelTestSuiteException(
          String.format(
              "Test case: %s cannot have both input map and input context.", testCase.getName()));
    } else if (testCase.getInputCount() > 0) {
      testCaseBuilder.setInput(parseInputMap(testCase));
    } else if (testCase.hasInputContext()) {
      testCaseBuilder.setInput(parseInputContext(testCase));
    } else {
      testCaseBuilder.setInput(CelTestCase.Input.ofNoInput());
    }
  }

  private static CelTestCase.Input parseInputMap(TestCase testCase) {
    ImmutableMap.Builder<String, Binding> inputMapBuilder = ImmutableMap.builder();
    for (Map.Entry<String, InputValue> entry : testCase.getInputMap().entrySet()) {
      InputValue inputValue = entry.getValue();
      if (inputValue.hasValue()) {
        inputMapBuilder.put(entry.getKey(), Binding.ofValue(inputValue.getValue()));
      } else if (inputValue.hasExpr()) {
        inputMapBuilder.put(entry.getKey(), Binding.ofExpr(inputValue.getExpr()));
      }
    }
    return CelTestCase.Input.ofBindings(inputMapBuilder.buildOrThrow());
  }

  private static CelTestCase.Input parseInputContext(TestCase testCase) {
    if (testCase.getInputContext().hasContextMessage()) {
      return CelTestCase.Input.ofContextMessage(testCase.getInputContext().getContextMessage());
    } else if (testCase.getInputContext().hasContextExpr()) {
      return CelTestCase.Input.ofContextExpr(testCase.getInputContext().getContextExpr());
    }
    return CelTestCase.Input.ofNoInput();
  }

  private static void addOutputs(CelTestCase.Builder testCaseBuilder, TestCase testCase) {
    if (testCase.hasOutput()) {
      switch (testCase.getOutput().getResultKindCase()) {
        case RESULT_VALUE:
          testCaseBuilder.setOutput(
              CelTestCase.Output.ofResultValue(testCase.getOutput().getResultValue()));
          break;
        case RESULT_EXPR:
          testCaseBuilder.setOutput(
              CelTestCase.Output.ofResultExpr(testCase.getOutput().getResultExpr()));
          break;
        case EVAL_ERROR:
          testCaseBuilder.setOutput(CelTestCase.Output.ofEvalError(parseEvalError(testCase)));
          break;
        default:
          break;
      }
    }
  }

  private static ImmutableList<Object> parseEvalError(TestCase testCase) {
    ImmutableList.Builder<Object> evalErrorSetBuilder = ImmutableList.builder();
    for (Status error : testCase.getOutput().getEvalError().getErrorsList()) {
      evalErrorSetBuilder.add(error.getMessage());
    }
    return evalErrorSetBuilder.build();
  }

  private CelTestSuiteTextProtoParser() {}
}
