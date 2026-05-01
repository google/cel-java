# Copyright 2026 Google LLC
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     https://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

"""Macro to run CEL policy conformance tests."""

load("@rules_java//java:defs.bzl", "java_test")

def cel_policy_conformance_test_java(
        name,
        testdata,
        test_cases = [],
        skip_tests = [],
        **kwargs):
    """Macro to run CEL policy conformance tests for Java.

    Args:
        name: The name of the test target.
        testdata: Testdata filegroup target.
        test_cases: (optional) List of test case names (directory names) to run.
        skip_tests: (optional) List of test case names (directory names) to skip.
        **kwargs: Other standard Bazel target attributes.
    """

    lbl = native.package_relative_label(testdata)
    testdata_dir = lbl.package + "/" + lbl.name

    java_test(
        name = name,
        jvm_flags = [
            "-Ddev.cel.policy.conformance.tests=" + ",".join(test_cases),
            "-Ddev.cel.policy.conformance.testdata_dir=" + testdata_dir,
            "-Ddev.cel.policy.conformance.skip_tests=" + ",".join(skip_tests),
        ],
        data = [testdata],
        size = "small",
        test_class = "dev.cel.conformance.policy.PolicyConformanceTests",
        runtime_deps = [Label(":run")],
        **kwargs
    )
