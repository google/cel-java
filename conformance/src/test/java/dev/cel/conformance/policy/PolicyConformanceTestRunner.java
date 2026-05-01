// Copyright 2026 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     https://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package dev.cel.conformance.policy;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.auto.value.AutoValue;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.io.Files;
import com.google.protobuf.ListValue;
import com.google.protobuf.Struct;
import com.google.protobuf.TypeRegistry;
import com.google.protobuf.Value;
import dev.cel.testing.testrunner.CelTestSuite;
import dev.cel.testing.testrunner.CelTestSuite.CelTestSection;
import dev.cel.testing.testrunner.CelTestSuite.CelTestSection.CelTestCase;
import dev.cel.testing.testrunner.CelTestSuiteTextProtoParser;
import dev.cel.testing.testrunner.CelTestSuiteYamlParser;
import java.io.File;
import java.util.Arrays;
import java.util.List;
import org.junit.runner.Description;
import org.junit.runner.notification.RunNotifier;
import org.junit.runners.ParentRunner;
import org.junit.runners.model.InitializationError;

/** Custom JUnit runner for CEL policy conformance tests. */
public final class PolicyConformanceTestRunner extends ParentRunner<PolicyConformanceTest> {

  private static final Splitter SPLITTER = Splitter.on(",").omitEmptyStrings();
  private static final String TESTS_YAML_FILE_NAME = "tests.yaml";
  private static final String TESTS_TEXTPROTO_FILE_NAME = "tests.textproto";
  private static final TypeRegistry TYPE_REGISTRY =
      TypeRegistry.newBuilder()
          .add(Struct.getDescriptor())
          .add(Value.getDescriptor())
          .add(ListValue.getDescriptor())
          .build();

  private static final String TEST_DIRS_PROP =
      System.getProperty("dev.cel.policy.conformance.tests");
  private static final String TESTDATA_DIR =
      System.getProperty("dev.cel.policy.conformance.testdata_dir", "testdata");
  private static final String SKIP_TESTS_PROP =
      System.getProperty("dev.cel.policy.conformance.skip_tests");

  private static final ImmutableList<String> TESTS_TO_SKIP =
      Strings.isNullOrEmpty(SKIP_TESTS_PROP)
          ? ImmutableList.of()
          : ImmutableList.copyOf(SPLITTER.splitToList(SKIP_TESTS_PROP));

  private static final ImmutableList<String> TEST_DIRS =
      Strings.isNullOrEmpty(TEST_DIRS_PROP)
          ? discoverTestDirs(TESTDATA_DIR)
          : ImmutableList.copyOf(SPLITTER.splitToList(TEST_DIRS_PROP));

  private static ImmutableList<String> discoverTestDirs(String testdataDir) {
    File dir = new File(testdataDir);
    if (!dir.exists() || !dir.isDirectory()) {
      return ImmutableList.of();
    }
    String[] directories = dir.list((current, name) -> new File(current, name).isDirectory());
    if (directories == null) {
      return ImmutableList.of();
    }
    Arrays.sort(directories);
    return ImmutableList.copyOf(directories);
  }

  private final ImmutableList<PolicyConformanceTest> tests;

  private ImmutableList<PolicyConformanceTest> loadTests() {
    if (TEST_DIRS.isEmpty()) {
      return ImmutableList.of();
    }

    ImmutableList.Builder<PolicyConformanceTest> testsBuilder = ImmutableList.builder();

    for (String dir : TEST_DIRS) {
      String fullDirPath = TESTDATA_DIR + "/" + dir;
      try {
        ImmutableList<CelTestSuiteContext> suites = readTestSuites(fullDirPath);
        for (CelTestSuiteContext namedSuite : suites) {
          for (CelTestSection section : namedSuite.testSuite().sections()) {
            for (CelTestCase testCase : section.tests()) {
              String baseName = String.format("%s/%s/%s", dir, section.name(), testCase.name());
              String displayName = baseName + namedSuite.formatSuffix();
              if (!shouldSkipTest(baseName, TESTS_TO_SKIP)) {
                testsBuilder.add(new PolicyConformanceTest(displayName, testCase, fullDirPath));
              }
            }
          }
        }
      } catch (Exception e) {
        throw new RuntimeException("Failed to load test suite in " + fullDirPath, e);
      }
    }
    return testsBuilder.build();
  }

  private static boolean shouldSkipTest(String name, List<String> testsToSkip) {
    for (String testToSkip : testsToSkip) {
      if (name.startsWith(testToSkip)) {
        String consumedName = name.substring(testToSkip.length());
        if (consumedName.isEmpty() || consumedName.startsWith("/")) {
          return true;
        }
      }
    }
    return false;
  }

  private static ImmutableList<CelTestSuiteContext> readTestSuites(String dirPath)
      throws Exception {
    File dir = new File(dirPath);
    File yamlFile = new File(dir, TESTS_YAML_FILE_NAME);
    File textprotoFile = new File(dir, TESTS_TEXTPROTO_FILE_NAME);

    boolean bothExist = yamlFile.exists() && textprotoFile.exists();
    ImmutableList.Builder<CelTestSuiteContext> suitesBuilder = ImmutableList.builder();

    if (yamlFile.exists()) {
      suitesBuilder.add(
          CelTestSuiteContext.create(
              CelTestSuiteYamlParser.newInstance()
                  .parse(Files.asCharSource(yamlFile, UTF_8).read()),
              bothExist ? " (yaml)" : ""));
    }
    if (textprotoFile.exists()) {
      suitesBuilder.add(
          CelTestSuiteContext.create(
              CelTestSuiteTextProtoParser.newInstance()
                  .parse(Files.asCharSource(textprotoFile, UTF_8).read(), TYPE_REGISTRY),
              bothExist ? " (textproto)" : ""));
    }

    ImmutableList<CelTestSuiteContext> suites = suitesBuilder.build();
    if (suites.isEmpty()) {
      throw new IllegalArgumentException(
          String.format(
              "No %s or %s found in %s", TESTS_YAML_FILE_NAME, TESTS_TEXTPROTO_FILE_NAME, dirPath));
    }
    return suites;
  }

  @Override
  protected ImmutableList<PolicyConformanceTest> getChildren() {
    return tests;
  }

  @Override
  protected Description describeChild(PolicyConformanceTest child) {
    return Description.createTestDescription(getTestClass().getJavaClass(), child.getName());
  }

  @Override
  protected void runChild(PolicyConformanceTest child, RunNotifier notifier) {
    runLeaf(child, describeChild(child), notifier);
  }

  public PolicyConformanceTestRunner(Class<?> clazz) throws InitializationError {
    super(clazz);
    this.tests = loadTests();
  }

  @AutoValue
  abstract static class CelTestSuiteContext {
    abstract CelTestSuite testSuite();

    abstract String formatSuffix();

    static CelTestSuiteContext create(CelTestSuite testSuite, String formatSuffix) {
      return new AutoValue_PolicyConformanceTestRunner_CelTestSuiteContext(testSuite, formatSuffix);
    }
  }
}
