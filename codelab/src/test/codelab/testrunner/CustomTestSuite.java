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

package codelab.testrunner;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import dev.cel.testing.testrunner.Annotations.TestSuiteSupplier;
import dev.cel.testing.testrunner.CelTestSuite;
import dev.cel.testing.testrunner.CelTestSuite.CelTestSection.CelTestCase.Input.Binding;

public final class CustomTestSuite {

  @TestSuiteSupplier
  public CelTestSuite getCelTestSuite() {
    return CelTestSuite.newBuilder()
        .setDescription("Basic Tests")
        .setName("basic_tests")
        .setSections(
            ImmutableSet.of(
                CelTestSuite.CelTestSection.newBuilder()
                    .setName("basic_test_sections")
                    .setDescription("Basic Tests Sections")
                    .setTests(
                        ImmutableSet.of(
                            CelTestSuite.CelTestSection.CelTestCase.newBuilder()
                                .setName("true_by_default")
                                .setDescription(
                                    "Test that the default value of a function is true.")
                                .setInput(
                                    CelTestSuite.CelTestSection.CelTestCase.Input.ofBindings(
                                        ImmutableMap.of("a", Binding.ofValue("foo"))))
                                .setOutput(
                                    CelTestSuite.CelTestSection.CelTestCase.Output.ofResultValue(
                                        true))
                                .build(),
                            CelTestSuite.CelTestSection.CelTestCase.newBuilder()
                                .setName("false_by_default")
                                .setDescription(
                                    "Test that the default value of a function is false.")
                                .setInput(
                                    CelTestSuite.CelTestSection.CelTestCase.Input.ofBindings(
                                        ImmutableMap.of("a", Binding.ofValue("baz"))))
                                .setOutput(
                                    CelTestSuite.CelTestSection.CelTestCase.Output.ofResultValue(
                                        false))
                                .build()))
                    .build()))
        .build();
  }
}
