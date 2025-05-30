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

import com.google.common.collect.ImmutableSet;
import dev.cel.testing.testrunner.Annotations.TestSuiteSupplier;
import java.util.logging.Logger;

final class CustomTestSuite {

  private static final Logger logger = Logger.getLogger(CustomTestSuite.class.getName());

  @TestSuiteSupplier
  public CelTestSuite getCelTestSuite() {
    logger.info("TestSuite Parser Triggered.");
    return CelTestSuite.newBuilder()
        .setDescription("CustomFunctionClass Test Suite")
        .setName("CustomFunctionClass Test Suite")
        .setSections(
            ImmutableSet.of(
                CelTestSuite.CelTestSection.newBuilder()
                    .setName("valid")
                    .setDescription("valid")
                    .setTests(
                        ImmutableSet.of(
                            CelTestSuite.CelTestSection.CelTestCase.newBuilder()
                                .setName("valid")
                                .setDescription("valid")
                                .setInput(CelTestSuite.CelTestSection.CelTestCase.Input.ofNoInput())
                                .setOutput(
                                    CelTestSuite.CelTestSection.CelTestCase.Output.ofResultValue(
                                        true))
                                .build()))
                    .build()))
        .build();
  }
}
