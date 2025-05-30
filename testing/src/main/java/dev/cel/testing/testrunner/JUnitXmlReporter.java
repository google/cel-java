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

import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.base.Throwables;
import com.google.common.collect.Lists;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Text;

/** Reporter class to generate an xml report in junit format. */
final class JUnitXmlReporter {
  private String outputFileName = null;
  private File outputFile = null;
  private TestContext testContext = null;
  // NOMUTANTS -- To be fixed in b/394771693 and when more failure tests are added.
  private int numFailed = 0;

  private final List<TestResult> allTests = Lists.newArrayList();

  /** Creates an instance that will write to {@code outputFileName}. */
  JUnitXmlReporter(String outputFileName) {
    this.outputFileName = outputFileName;
  }

  /** Called for each test case */
  void onTestStart(TestResult result) {}

  /** Called on test success */
  void onTestSuccess(TestResult tr) {
    allTests.add(tr);
  }

  /** Called when the test fails */
  void onTestFailure(TestResult tr) {
    allTests.add(tr);
    numFailed++;
  }

  /** Called in the beginning of test suite. */
  void onStart(TestContext context) {
    outputFile = new File(outputFileName);
    testContext = context;
  }

  /** Called after all tests are run */
  void onFinish() {
    generateReport();
  }

  /** Returns the number of failed tests */
  int getNumFailed() {
    return numFailed;
  }

  /**
   * Generates junit equivalent xml report that sponge/fusion can understand. Called after all tests
   * are run
   */
  void generateReport() {
    try {
      DocumentBuilderFactory docBuilderFactory = DocumentBuilderFactory.newInstance();
      DocumentBuilder docBuilder = docBuilderFactory.newDocumentBuilder();
      Document doc = docBuilder.newDocument();

      Element rootElement = doc.createElement(XmlConstants.TESTSUITE);
      rootElement.setAttribute(XmlConstants.ATTR_NAME, testContext.getSuiteName());

      rootElement.setAttribute(XmlConstants.ATTR_TESTS, "" + allTests.size());
      rootElement.setAttribute(XmlConstants.ATTR_FAILURES, "" + numFailed);
      rootElement.setAttribute(XmlConstants.ATTR_ERRORS, "0");

      long elapsedTimeMillis = testContext.getEndTime() - testContext.getStartTime();

      rootElement.setAttribute(XmlConstants.ATTR_TIME, "" + (elapsedTimeMillis / 1000.0));

      String prevClassName = null;
      String currentClassName = null;
      Element prevSuite = null;
      Element currentSuite = null;
      int testsInSuite = 0;
      int failedTests = 0;
      // NOMUTANTS -- Need not to be fixed.
      long startTime = 0;
      // NOMUTANTS -- Need not to be fixed.
      long endTime = 0;

      // go through each test result
      for (TestResult tr : allTests) {
        prevClassName = currentClassName;
        currentClassName = tr.getTestClassName();

        // as all results are in single array this will create
        // testsuite element as in junit.
        if (!currentClassName.equals(prevClassName)) {
          prevSuite = currentSuite;
          currentSuite = doc.createElement(XmlConstants.TESTSUITE);
          rootElement.appendChild(currentSuite);
          currentSuite.setAttribute(XmlConstants.ATTR_NAME, tr.getTestClassName());
          if (prevSuite != null) {
            prevSuite.setAttribute(XmlConstants.ATTR_TESTS, "" + testsInSuite);
            prevSuite.setAttribute(XmlConstants.ATTR_FAILURES, "" + failedTests);
            prevSuite.setAttribute(XmlConstants.ATTR_ERRORS, "0");
            prevSuite.setAttribute(XmlConstants.ATTR_TIME, "" + (endTime - startTime) / 1000.0);
            testsInSuite = 0;
            failedTests = 0;
          }
          startTime = tr.getStartMillis();
        }
        endTime = tr.getEndMillis();

        Element testCaseElement = doc.createElement(XmlConstants.TESTCASE);
        elapsedTimeMillis = tr.getEndMillis() - tr.getStartMillis();
        testCaseElement.setAttribute(XmlConstants.ATTR_NAME, tr.getName());
        testCaseElement.setAttribute(XmlConstants.ATTR_CLASSNAME, tr.getTestClassName());
        testCaseElement.setAttribute(
            XmlConstants.ATTR_TIME, "" + ((double) elapsedTimeMillis) / 1000);

        // for failure add fail message
        if (tr.getStatus() == TestResult.FAILURE) {
          failedTests++;
          Element nested = doc.createElement(XmlConstants.FAILURE);
          testCaseElement.appendChild(nested);
          Throwable t = tr.getThrowable();
          if (t != null) {
            nested.setAttribute(XmlConstants.ATTR_TYPE, t.getClass().getName());
            String message = t.getMessage();
            if ((message != null) && (message.length() > 0)) {
              nested.setAttribute(XmlConstants.ATTR_MESSAGE, message);
            }
            Text trace = doc.createTextNode(Throwables.getStackTraceAsString(t));
            nested.appendChild(trace);
          }
        }
        currentSuite.appendChild(testCaseElement);
        testsInSuite++;
      }

      currentSuite.setAttribute(XmlConstants.ATTR_TESTS, "" + testsInSuite);
      currentSuite.setAttribute(XmlConstants.ATTR_FAILURES, "" + failedTests);
      currentSuite.setAttribute(XmlConstants.ATTR_ERRORS, "0");
      currentSuite.setAttribute(XmlConstants.ATTR_TIME, "" + (endTime - startTime) / 1000.0);

      // Writes to a file
      try (BufferedWriter fw = Files.newBufferedWriter(outputFile.toPath(), UTF_8)) {
        Transformer transformer = TransformerFactory.newInstance().newTransformer();
        transformer.transform(new DOMSource(rootElement), new StreamResult(fw));
      } catch (TransformerException te) {
        te.printStackTrace();
        System.err.println("Error while writing out JUnitXML because of " + te);
      } catch (IOException ioe) {
        ioe.printStackTrace();
        System.err.println("failed to create JUnitXML because of " + ioe);
      }

    } catch (ParserConfigurationException pce) {
      pce.printStackTrace();
      System.err.println("failed to create JUnitXML because of " + pce);
    }
  }

  /** Description of a test suite execution. */
  static interface TestContext {
    String getSuiteName();

    long getEndTime();

    long getStartTime();
  }

  /** Description of a single test result. */
  static interface TestResult {
    String getTestClassName();

    String getName();

    long getStartMillis();

    long getEndMillis();

    Throwable getThrowable();

    int getStatus();

    public static int FAILURE = 0;
    public static int SUCCESS = 1;
  }

  /** Elements and attributes for JUnit-style XML doc. */
  private static final class XmlConstants {
    static final String TESTSUITE = "testsuite";
    static final String TESTCASE = "testcase";
    static final String FAILURE = "failure";
    static final String ATTR_NAME = "name";
    static final String ATTR_TIME = "time";
    static final String ATTR_ERRORS = "errors";
    static final String ATTR_FAILURES = "failures";
    static final String ATTR_TESTS = "tests";
    static final String ATTR_TYPE = "type";
    static final String ATTR_MESSAGE = "message";
    static final String ATTR_CLASSNAME = "classname";
  }
}
