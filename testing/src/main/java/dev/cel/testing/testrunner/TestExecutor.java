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

import com.google.common.collect.ImmutableList;
import com.google.common.io.Files;
import dev.cel.testing.testrunner.Annotations.TestSuiteSupplier;
import dev.cel.testing.testrunner.CelTestSuite.CelTestSection;
import dev.cel.testing.testrunner.CelTestSuite.CelTestSection.CelTestCase;
import io.github.classgraph.ClassInfoList;
import org.junit.runner.*;
import org.junit.runner.manipulation.Filter;
import org.junit.runners.model.TestClass;
import org.junit.runners.parameterized.BlockJUnit4ClassRunnerWithParametersFactory;
import org.junit.runners.parameterized.ParametersRunnerFactory;
import org.junit.runners.parameterized.TestWithParameters;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Arrays;

import static com.google.common.collect.MoreCollectors.onlyElement;
import static dev.cel.testing.utils.ClassLoaderUtils.loadClassesWithMethodAnnotation;
import static dev.cel.testing.utils.ClassLoaderUtils.loadSubclasses;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.time.ZoneId.systemDefault;

/** Test executor for running tests using custom runner. */
public final class TestExecutor {

  private TestExecutor() {}

  private static final Class<?> CEL_TESTSUITE_ANNOTATION_CLASS = TestSuiteSupplier.class;

  private static CelTestSuite readTestSuite(String testSuitePath)
      throws IOException, CelTestSuiteException {
    switch (testSuitePath.substring(testSuitePath.lastIndexOf(".") + 1)) {
      case "textproto":
        return CelTestSuiteTextProtoParser.newInstance()
            .parse(Files.asCharSource(new File(testSuitePath), UTF_8).read());
      case "yaml":
        return CelTestSuiteYamlParser.newInstance()
            .parse(Files.asCharSource(new File(testSuitePath), UTF_8).read());
      default:
        throw new IllegalArgumentException(
            "Unsupported test suite file type: " + testSuitePath);
    }
  }

  private static class TestContext implements JUnitXmlReporter.TestContext {

    final LocalDate startDate;
    LocalDate endDate;

    TestContext() {
      startDate = Instant.now().atZone(systemDefault()).toLocalDate();
    }

    @Override
    public long getEndTime() {
      return endDate.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli();
    }

    @Override
    public long getStartTime() {
      return startDate.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli();
    }

    @Override
    public String getSuiteName() {
      return "test_suite";
    }

    void done() {
      endDate = Instant.now().atZone(systemDefault()).toLocalDate();
    }
  }

  private static class TestResult implements JUnitXmlReporter.TestResult {

    long endMillis;

    long startMillis;

    int status;

    final String testName;

    Throwable throwable;

    final String testClassName;

    TestResult(String testName, String testClassName) {
      this.testName = testName;
      this.startMillis = Instant.now().toEpochMilli();
      this.testClassName = testClassName;
    }

    @Override
    public String getTestClassName() {
      return testClassName;
    }

    @Override
    public long getEndMillis() {
      return endMillis;
    }

    @Override
    public String getName() {
      return testName;
    }

    @Override
    public long getStartMillis() {
      return startMillis;
    }

    @Override
    public int getStatus() {
      return status;
    }

    @Override
    public Throwable getThrowable() {
      return throwable;
    }

    private void setEndMillis(long endMillis) {
      this.endMillis = endMillis;
    }

    private void setStatus(int status) {
      this.status = status;
    }

    private void setThrowable(Throwable throwable) {
      this.throwable = throwable;
    }

    private void setStartMillis(long startMillis) {
      this.startMillis = startMillis;
    }
  }

  /** Runs test cases for a given test class and test suite. */
  public static void runTests() throws Exception {
    String testSuitePath = System.getProperty("test_suite_path");

    CelTestSuite testSuite;
    testSuite = readCustomTestSuite();

    if (testSuitePath != null) {
      if (testSuite != null) {
        throw new IllegalArgumentException(
            "Both test_suite_path and TestSuiteSupplier are set. Only one of them can be set.");
      } else {
        testSuite = readTestSuite(testSuitePath);
      }
    } else if (testSuite == null) {
      throw new IllegalArgumentException("Neither test_suite_path nor TestSuiteSupplier is set.");
    }

    Class<?> testClass = getUserTestClass();
    String envXmlFile = System.getenv("XML_OUTPUT_FILE");
    JUnitXmlReporter testReporter = new JUnitXmlReporter(envXmlFile);
    TestContext testContext = new TestContext();
    testReporter.onStart(testContext);

    boolean allTestsPassed = true;

    for (CelTestSection testSection : testSuite.sections()) {
      for (CelTestCase testCase : testSection.tests()) {
        String testName = testSection.name() + "." + testCase.name();

        Object[] parameter = new Object[] {testCase};
        TestWithParameters test =
            new TestWithParameters(
                testName, new TestClass(testClass), ImmutableList.copyOf(parameter));

        TestResult testResult = new TestResult(testName, testClass.getName());
        testReporter.onTestStart(testResult);
        testResult.setStartMillis(Instant.now().toEpochMilli());

        ParametersRunnerFactory factory = new BlockJUnit4ClassRunnerWithParametersFactory();
        Runner runner = factory.createRunnerForTestWithParameters(test);

        JUnitCore junitCore = new JUnitCore();
        Request request =
            Request.runner(runner)
                .filterWith(
                    new Filter() {
                      @Override
                      public boolean shouldRun(Description description) {
                        return true;
                      }

                      @Override
                      public String describe() {
                        return "Filter to run only test method";
                      }
                    });
        Result result = junitCore.run(request);
        testResult.setEndMillis(Instant.now().toEpochMilli());

        if (result.wasSuccessful()) {
          testResult.setStatus(JUnitXmlReporter.TestResult.SUCCESS);
          testReporter.onTestSuccess(testResult);
        } else {
          allTestsPassed = false;
          testResult.setStatus(JUnitXmlReporter.TestResult.FAILURE);
          testResult.setThrowable(result.getFailures().get(0).getException());
          System.out.println("Size: " + result.getFailures().size());
          System.out.println("ExecutorError: " + result.getFailures().get(0).getException());
          System.out.println("ExecutorErrorStackTrace: " + result.getFailures().get(0).getTrace());
          System.out.println("ExecutorErrorCause: " + result.getFailures().get(0).getException().getCause());
          testReporter.onTestFailure(testResult);
        }
      }
    }

    testContext.done();
    testReporter.onFinish();
    if (!allTestsPassed) {
      throw new RuntimeException(testReporter.getNumFailed() + " tests failed");
    }
  }

  private static CelTestSuite readCustomTestSuite() throws Exception {
    ClassInfoList classInfoList =
        loadClassesWithMethodAnnotation(CEL_TESTSUITE_ANNOTATION_CLASS.getName());
    if (classInfoList.isEmpty()) {
      return null;
    }
    if (classInfoList.size() > 1) {
      throw new IllegalArgumentException(
          "Expected 1 class for TestSuiteSupplier, but got "
              + classInfoList.size()
              + " classes: "
              + classInfoList);
    }
    Class<?> customFunctionClass = classInfoList.loadClasses().get(0);
    Method method = getMethodWithAnnotation(customFunctionClass);
    return (CelTestSuite) method.invoke(customFunctionClass.getDeclaredConstructor().newInstance());
  }

  private static Method getMethodWithAnnotation(Class<?> clazz) {
    Method testSuiteSupplierMethod =
        Arrays.asList(clazz.getDeclaredMethods()).stream()
            .filter(method -> method.isAnnotationPresent(TestSuiteSupplier.class))
            .collect(onlyElement());

    if (!testSuiteSupplierMethod.getReturnType().equals(CelTestSuite.class)) {
      throw new IllegalArgumentException(
          String.format(
              "Method: %s annotated with @TestSuiteSupplier must return CelTestSuite, but got %s",
              testSuiteSupplierMethod.getName(), testSuiteSupplierMethod.getReturnType()));
    }
    return testSuiteSupplierMethod;
  }

  private static Class<?> getUserTestClass() {
    ClassInfoList subClassInfoList = loadSubclasses(CelUserTestTemplate.class);
    if (subClassInfoList.size() != 1) {
      throw new IllegalArgumentException(
          "Expected 1 subclass for CelUserTestTemplate, but got "
              + subClassInfoList.size()
              + " subclasses: "
              + subClassInfoList);
    }

    return subClassInfoList.loadClasses().get(0);
  }
}
