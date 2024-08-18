// Copyright 2022 Google LLC
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

import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.base.Strings;
import com.google.common.io.Files;
import com.google.common.io.Resources;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.URL;
import java.nio.charset.Charset;
import junit.framework.AssertionFailedError;
import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.TestName;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;

/** Test fixture for baseline testing. */
public abstract class BaselineTestCase {

  /** Exception to use for baseline test failure. */
  public static class BaselineComparisonError extends AssertionFailedError {
    private final String testName;
    private final String actual;
    private final String actualFileLocation;
    private final LineDiffer.Diff lineDiff;
    private final String baselineFileName;

    /**
     * Constructs a baseline comparison error.
     *
     * @param testName the test which failed.
     * @param actual the actual result.
     * @param lineDiff the diff between the expected and {@code actual}.
     */
    public BaselineComparisonError(
        String testName,
        String baselineFileName,
        String actual,
        String actualFileLocation,
        LineDiffer.Diff lineDiff) {
      this.testName = testName;
      this.actual = actual;
      this.actualFileLocation = actualFileLocation;
      this.lineDiff = lineDiff;
      this.baselineFileName = baselineFileName;
    }

    @Override
    public String getMessage() {
      String resultMessage =
          String.format(
              "Expected for '%s' differs from actual:%n%n\"******New baseline content"
                  + " is******%n%s%nExpected File: %s%nActual File: %s%nDiff:\n%s",
              testName, actual, baselineFileName, actualFileLocation, lineDiff);

      return resultMessage;
    }
  }

  @Rule public TestName testName = new TestName();

  private static final String DIRECTORY_TO_COPY_NEW_BASELINE;

  static {
    if (!Strings.isNullOrEmpty(System.getenv("COPY_BASELINE_TO_DIR"))) {
      DIRECTORY_TO_COPY_NEW_BASELINE = System.getenv("COPY_BASELINE_TO_DIR");
    } else {
      DIRECTORY_TO_COPY_NEW_BASELINE = "/tmp";
    }
  }

  private OutputStream output;
  private PrintWriter writer;
  private boolean isVerified;

  /** Returns a print writer to which test results are written. */
  protected PrintWriter testOutput() {
    return writer;
  }

  /**
   * A test watcher which calls baseline verification if the test succeeded. This is like @After,
   * but verification is only done if there haven't been other errors.
   */
  @Rule
  public TestWatcher failerWatcher =
      new TestWatcher() {
        @Override
        protected void succeeded(Description d) {
          verify();
        }
      };

  /** Setups the test case. */
  @Before
  public void before() throws Exception {
    output = new ByteArrayOutputStream();
    writer = new PrintWriter(new BufferedWriter(new OutputStreamWriter(output, UTF_8)));
  }

  /** Gets expected result or empty string if the files does not exist. */
  protected String getExpected() throws Exception {
    URL resourceLocation = Resources.getResource(baselineFileName());
    return Resources.toString(resourceLocation, UTF_8);
  }

  /** Gets the directory location of all test-related baseline files. */
  protected String testdataDir() {
    return "";
  }

  /** Gets the relative name the baseline data file should have. Can be overridden. */
  protected String baselineFileName() {
    String fileName = String.format("%s%s.baseline", testdataDir(), testName.getMethodName());
    // Scrub the parameterized test index from the baseline file name.
    return fileName.replaceFirst("\\[.+].baseline$", ".baseline");
  }

  /** Verifies the recorded content against the baseline. */
  protected void verify() {
    if (isVerified) {
      return;
    }
    try {
      isVerified = true;
      writer.flush();
      output.flush();
      String actual = ((ByteArrayOutputStream) output).toString("UTF-8").trim();
      String expected = getExpected().trim();
      LineDiffer.Diff lineDiff = LineDiffer.diffLines(expected, actual);
      if (!lineDiff.isEmpty()) {
//        String actualFileLocation = tryCreateNewBaseline(actual);
        throw new BaselineComparisonError(
            testName.getMethodName(), baselineFileName(), actual, "foo", lineDiff);
      }
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  /** Disables verification of the baseline for the given test case. */
  protected void skipBaselineVerification() {
    isVerified = true;
  }

  /**
   * Creates a baseline file that will need to be used to make the current test pass.
   *
   * <p>If the test is failing for a valid reason (e.g. developer changed some output text), then
   * this file provides a convenient way for the developer to overwrite the old baseline and keep
   * the test passing.
   *
   * <p>The created file is stored under /tmp or location specified by the environment variable
   * DIRECTORY_TO_COPY_NEW_BASELINE. Information where the file is stored is returned as a string.
   */
  private String tryCreateNewBaseline(String actual) throws IOException {
    File file =
        new File(
            File.separator + DIRECTORY_TO_COPY_NEW_BASELINE + File.separator + baselineFileName());
    Files.createParentDirs(file);
    Files.asCharSink(file, Charset.defaultCharset()).write(actual);
    return file.toString();
  }
}
