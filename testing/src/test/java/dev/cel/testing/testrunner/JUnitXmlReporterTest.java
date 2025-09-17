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

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.when;

import dev.cel.testing.testrunner.JUnitXmlReporter.TestContext;
import dev.cel.testing.testrunner.JUnitXmlReporter.TestResult;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(JUnit4.class)
public class JUnitXmlReporterTest {

  private static final String SUITE_NAME = "TestSuiteName";
  private static final String TEST_CLASS_NAME = "TestClass1";
  private static final String TEST_METHOD_NAME = "testMethod1";

  @Rule public final MockitoRule mockito = MockitoJUnit.rule();

  @Mock private TestContext context;
  @Mock private TestResult result1;
  @Mock private TestResult result2;
  @Mock private TestResult resultFailure;

  @Rule public TemporaryFolder tempFolder = new TemporaryFolder();

  @Test
  public void testGenerateReport_success() throws IOException {
    String outputFileName = "test-report.xml";
    File subFolder = tempFolder.newFolder("subFolder");
    File outputFile = new File(subFolder.getAbsolutePath(), outputFileName);
    JUnitXmlReporter reporter = new JUnitXmlReporter(outputFile.getAbsolutePath());
    long startTime = 100L;
    long test1EndTime = startTime + 400;
    long endTime = startTime + 900;

    when(context.getSuiteName()).thenReturn(SUITE_NAME);
    when(context.getStartTime()).thenReturn(startTime);
    when(context.getEndTime()).thenReturn(endTime);
    reporter.onStart(context);

    when(result1.getTestClassName()).thenReturn(TEST_CLASS_NAME);
    when(result1.getName()).thenReturn(TEST_METHOD_NAME);
    when(result1.getStartMillis()).thenReturn(startTime);
    when(result1.getEndMillis()).thenReturn(test1EndTime);
    when(result1.getStatus()).thenReturn(JUnitXmlReporter.TestResult.SUCCESS);
    reporter.onTestSuccess(result1);

    when(result2.getTestClassName()).thenReturn("TestClass2");
    when(result2.getName()).thenReturn("testMethod2");
    when(result2.getStartMillis()).thenReturn(test1EndTime);
    when(result2.getEndMillis()).thenReturn(endTime);
    when(result2.getStatus()).thenReturn(JUnitXmlReporter.TestResult.SUCCESS);
    reporter.onTestSuccess(result2);

    reporter.onFinish();
    assertThat(outputFile.exists()).isTrue();
    String concatenatedFileContent = String.join("\n", Files.readAllLines(outputFile.toPath()));

    assertThat(concatenatedFileContent)
        .contains(
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?><testsuite errors=\"0\" failures=\"0\""
                + " name=\"TestSuiteName\" tests=\"2\" time=\"0.9\"><testsuite errors=\"0\""
                + " failures=\"0\" name=\"TestClass1\" tests=\"1\" time=\"0.4\"><testcase"
                + " classname=\"TestClass1\" name=\"testMethod1\""
                + " time=\"0.4\"/></testsuite><testsuite errors=\"0\" failures=\"0\""
                + " name=\"TestClass2\" tests=\"1\" time=\"0.5\"><testcase classname=\"TestClass2\""
                + " name=\"testMethod2\" time=\"0.5\"/></testsuite></testsuite>");

    outputFile.delete();
  }

  @Test
  public void testGenerateReport_failure() throws IOException {
    String outputFileName = "test-report.xml";
    File subFolder = tempFolder.newFolder("subFolder");
    File outputFile = new File(subFolder.getAbsolutePath(), outputFileName);
    JUnitXmlReporter reporter = new JUnitXmlReporter(outputFile.getAbsolutePath());

    when(context.getSuiteName()).thenReturn(SUITE_NAME);
    when(context.getStartTime()).thenReturn(0L);
    when(context.getEndTime()).thenReturn(1000L);
    reporter.onStart(context);

    when(resultFailure.getTestClassName()).thenReturn(TEST_CLASS_NAME);
    when(resultFailure.getName()).thenReturn(TEST_METHOD_NAME);
    when(resultFailure.getStartMillis()).thenReturn(0L);
    when(resultFailure.getEndMillis()).thenReturn(500L);
    when(resultFailure.getStatus()).thenReturn(JUnitXmlReporter.TestResult.FAILURE);
    Throwable throwable = new RuntimeException("Test Exception");
    when(resultFailure.getThrowable()).thenReturn(throwable);
    reporter.onTestFailure(resultFailure);
    reporter.onFinish();

    assertThat(reporter.getNumFailed()).isEqualTo(1);
    assertThat(outputFile.exists()).isTrue();

    String concatenatedFileContent = String.join("\n", Files.readAllLines(outputFile.toPath()));

    assertThat(concatenatedFileContent).contains("failures=\"1\"");
    assertThat(concatenatedFileContent).contains("failure message=\"Test Exception\"");
  }

  @Test
  public void testGenerateReport_coverageReport_noCoverage() throws IOException {
    String outputFileName = "test-report-with-coverage.xml";
    File subFolder = tempFolder.newFolder("subFolder");
    File outputFile = new File(subFolder.getAbsolutePath(), outputFileName);
    JUnitXmlReporter reporter = new JUnitXmlReporter(outputFile.getAbsolutePath());
    long startTime = 100L;
    long test1EndTime = startTime + 400;
    long endTime = startTime + 900;

    CelCoverageIndex.CoverageReport coverageReport =
        CelCoverageIndex.CoverageReport.builder().build();

    when(context.getSuiteName()).thenReturn(SUITE_NAME);
    when(context.getStartTime()).thenReturn(startTime);
    when(context.getEndTime()).thenReturn(endTime);
    reporter.onStart(context);

    when(result1.getTestClassName()).thenReturn(TEST_CLASS_NAME);
    when(result1.getName()).thenReturn(TEST_METHOD_NAME);
    when(result1.getStartMillis()).thenReturn(startTime);
    when(result1.getEndMillis()).thenReturn(test1EndTime);
    when(result1.getStatus()).thenReturn(JUnitXmlReporter.TestResult.SUCCESS);
    reporter.onTestSuccess(result1);

    reporter.onFinish(coverageReport);
    assertThat(outputFile.exists()).isTrue();
    String concatenatedFileContent = String.join("\n", Files.readAllLines(outputFile.toPath()));

    assertThat(concatenatedFileContent).contains("No coverage stats found");

    outputFile.delete();
  }

  @Test
  public void testGenerateReport_coverageReport_withCoverage() throws IOException {
    String outputFileName = "test-report-with-coverage.xml";
    File subFolder = tempFolder.newFolder("subFolder");
    File outputFile = new File(subFolder.getAbsolutePath(), outputFileName);
    JUnitXmlReporter reporter = new JUnitXmlReporter(outputFile.getAbsolutePath());
    long startTime = 100L;
    long test1EndTime = startTime + 400;
    long endTime = startTime + 900;

    CelCoverageIndex.CoverageReport coverageReport =
        CelCoverageIndex.CoverageReport.builder()
            .setNodes(10L)
            .setCoveredNodes(10L)
            .setBranches(10L)
            .setCoveredBooleanOutcomes(5L)
            .addUnencounteredNodes("Node 1")
            .addUnencounteredNodes("Node 2")
            .addUnencounteredBranches("Branch 1")
            .addUnencounteredBranches("Branch 2")
            .build();

    when(context.getSuiteName()).thenReturn(SUITE_NAME);
    when(context.getStartTime()).thenReturn(startTime);
    when(context.getEndTime()).thenReturn(endTime);
    reporter.onStart(context);

    when(result1.getTestClassName()).thenReturn(TEST_CLASS_NAME);
    when(result1.getName()).thenReturn(TEST_METHOD_NAME);
    when(result1.getStartMillis()).thenReturn(startTime);
    when(result1.getEndMillis()).thenReturn(test1EndTime);
    when(result1.getStatus()).thenReturn(JUnitXmlReporter.TestResult.SUCCESS);
    reporter.onTestSuccess(result1);

    reporter.onFinish(coverageReport);
    assertThat(outputFile.exists()).isTrue();
    String concatenatedFileContent = String.join("\n", Files.readAllLines(outputFile.toPath()));

    assertThat(concatenatedFileContent)
        .contains(
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?><testsuite errors=\"0\" failures=\"0\""
                + " name=\"TestSuiteName\" tests=\"1\" time=\"0.9\"><testsuite"
                + " Ast_Branch_Coverage=\"50.00% (5 out of 10 branch outcomes covered)\""
                + " Ast_Node_Coverage=\"100.00% (10 out of 10 nodes covered)\" Cel_Expr=\"\""
                + " Interesting_Unencountered_Branch_Paths=\"Branch 1&#10;Branch 2\""
                + " Interesting_Unencountered_Nodes=\"Node 1&#10;Node 2\" errors=\"0\""
                + " failures=\"0\" name=\"TestClass1\" tests=\"1\" time=\"0.4\"><testcase"
                + " classname=\"TestClass1\" name=\"testMethod1\""
                + " time=\"0.4\"/></testsuite></testsuite>");

    outputFile.delete();
  }
}
