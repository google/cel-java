# Copyright 2024 Google LLC
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

"""
This module contains build rules for generating the conformance test targets.
"""

load("@rules_java//java:defs.bzl", "java_test")

# Converts the list of tests to skip from the format used by the original Go test runner to a single
# flag value where each test is separated by a comma. It also performs expansion, for example
# `foo/bar,baz` becomes two entries which are `foo/bar` and `foo/baz`.
def _expand_tests_to_skip(tests_to_skip):
    result = []
    for test_to_skip in tests_to_skip:
        comma = test_to_skip.find(",")
        if comma == -1:
            result.append(test_to_skip)
            continue
        slash = test_to_skip.rfind("/", 0, comma)
        if slash == -1:
            slash = 0
        else:
            slash = slash + 1
        for part in test_to_skip[slash:].split(","):
            result.append(test_to_skip[0:slash] + part)
    return result

def _conformance_test_args(data, skip_tests):
    args = []
    args.append("-Ddev.cel.conformance.ConformanceTests.skip_tests={}".format(",".join(_expand_tests_to_skip(skip_tests))))
    args.append("-Ddev.cel.conformance.ConformanceTests.tests={}".format(",".join(["$(location " + test + ")" for test in data])))
    return args

def conformance_test(name, data, dashboard, skip_tests = []):
    if dashboard:
        java_test(
            name = "_" + name,
            jvm_flags = _conformance_test_args(data, skip_tests),
            data = data,
            size = "small",
            test_class = "dev.cel.conformance.ConformanceTests",
            runtime_deps = ["//conformance/src/test/java/dev/cel/conformance:run"],
            tags = [
                "manual",
                "notap",
            ],
        )
        native.sh_test(
            name = name,
            size = "small",
            srcs = ["//conformance/src/test/java/dev/cel/conformance:conformance_test.sh"],
            args = ["$(location :_" + name + ")"],
            data = [":_" + name],
            tags = [
                "guitar",
                "manual",
                "notap",
            ],
        )
    else:
        java_test(
            name = name,
            jvm_flags = _conformance_test_args(data, skip_tests),
            data = data,
            size = "small",
            test_class = "dev.cel.conformance.ConformanceTests",
            runtime_deps = ["//conformance/src/test/java/dev/cel/conformance:run"],
        )
