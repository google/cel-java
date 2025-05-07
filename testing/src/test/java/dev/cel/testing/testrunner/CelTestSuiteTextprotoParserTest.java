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
import static org.junit.Assert.assertThrows;

import dev.cel.expr.conformance.test.InputContext;
import dev.cel.expr.conformance.test.InputValue;
import dev.cel.expr.conformance.test.TestCase;
import dev.cel.expr.conformance.test.TestSection;
import dev.cel.expr.conformance.test.TestSuite;
import java.io.IOException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class CelTestSuiteTextprotoParserTest {

  @Test
  public void parseTestSuite_illegalInput_failure() throws IOException {
    TestSuite testSuite =
        TestSuite.newBuilder()
            .setName("some_test_suite")
            .setDescription("Some test suite")
            .addSections(
                TestSection.newBuilder()
                    .setName("section_name")
                    .addTests(
                        TestCase.newBuilder()
                            .setName("test_case_name")
                            .setDescription("Some test case")
                            .putInput("test_key", InputValue.getDefaultInstance())
                            .setInputContext(InputContext.getDefaultInstance())
                            .build())
                    .build())
            .build();

    CelTestSuiteException exception =
        assertThrows(
            CelTestSuiteException.class,
            () -> CelTestSuiteTextProtoParser.parseCelTestSuite(testSuite));

    assertThat(exception)
        .hasMessageThat()
        .contains("Test case: test_case_name cannot have both input map and input context.");
  }
}
