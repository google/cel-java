# Copyright 2025 Google LLC
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

"""Rules for triggering the java impl of the CEL test runner."""

load("@rules_java//java:java_binary.bzl", "java_binary")
load("@rules_proto//proto:defs.bzl", "proto_descriptor_set")
load("@rules_shell//shell:sh_test.bzl", "sh_test")
load("@bazel_skylib//lib:paths.bzl", "paths")
load("@com_google_protobuf//bazel:java_proto_library.bzl", "java_proto_library")

def _is_label(s):
    return s.startswith("//") or s.startswith(":")

def cel_java_test(
        name,
        cel_expr,
        test_src,
        is_raw_expr = False,
        test_suite = "",
        filegroup = "",
        config = "",
        deps = [],
        proto_deps = [],
        enable_coverage = False,
        test_data_path = "",
        data = []):
    """Triggers the Java impl of the CEL test runner.

    This rule generates a java_binary and a run_test rule.

    Note: This rule is to be used only for OSS until cel/expr folder is made available in OSS.
    Internally, the cel_test rule is supposed to be used.

    Args:
        name: str name for the generated artifact.
        cel_expr: cel expression to be evaluated (raw expression, compiled expression, or policy).
        test_src: user's test class build target.
        is_raw_expr: bool whether the cel_expr is a raw expression (not treated as a file path).
        test_suite: str label of a test suite file (.yaml or .textproto).
        filegroup: str label of a filegroup containing the test suite, config, and checked expression.
        config: str label of a google.api.expr.conformance.Environment textproto file.
        deps: list of dependencies for the java_binary rule.
        proto_deps: list of proto_library dependencies for the test.
        enable_coverage: bool whether to enable coverage for the test.
        test_data_path: absolute path of the directory containing the test files (e.g., "//foo/bar").
        data: list of data dependencies for the java_binary rule.
    """

    jvm_flags = []

    # Avoid mutating the original data list passed into the macro
    resolved_data = list(data)
    resolved_deps = list(deps)

    # Normalize paths
    pkg_name = native.package_name()
    test_data_dir = test_data_path.lstrip("/") if test_data_path else pkg_name

    # Add filegroup if provided
    if filegroup:
        resolved_data.append(filegroup)

    def _process_file_arg(file_val, flag_name):
        """Helper to append JVM flags and resolve data targets for file inputs."""
        if not file_val:
            return

        if _is_label(file_val):
            jvm_flags.append("-D{}=$(location {})".format(flag_name, file_val))
            resolved_data.append(file_val)
        else:
            jvm_flags.append("-D{}={}/{}".format(flag_name, test_data_dir, file_val))

            # If no filegroup is provided, we must add the file directly to data
            if not filegroup:
                target = file_val if test_data_dir == pkg_name else "//{}:{}".format(test_data_dir, file_val)
                resolved_data.append(target)

    # Process standard file inputs
    _process_file_arg(test_suite, "test_suite_path")
    _process_file_arg(config, "config_path")

    # Process cel_expr (has specialized fallback logic)
    _, cel_expr_format = paths.split_extension(cel_expr)
    is_valid_cel_ext = cel_expr_format in [".cel", ".celpolicy", ".yaml"]

    if _is_label(cel_expr):
        jvm_flags.append("-Dcel_expr=$(location {})".format(cel_expr))
        resolved_data.append(cel_expr)
    elif is_raw_expr:
        jvm_flags.append("-Dcel_expr='{}'".format(cel_expr))
    elif is_valid_cel_ext:
        jvm_flags.append("-Dcel_expr={}/{}".format(test_data_dir, cel_expr))
        if not filegroup:
            target = cel_expr if test_data_dir == pkg_name else "//{}:{}".format(test_data_dir, cel_expr)
            resolved_data.append(target)
    else:
        # Fallback: Treat as a local target
        jvm_flags.append("-Dcel_expr=$(location {})".format(cel_expr))
        resolved_data.append(cel_expr)

    # Process Proto Dependencies
    if proto_deps:
        descriptor_set_name = name + "_proto_descriptor_set"
        descriptor_set_path = ":" + descriptor_set_name

        proto_descriptor_set(
            name = descriptor_set_name,
            deps = proto_deps,
        )
        java_proto_library(
            name = descriptor_set_name + "_java_proto",
            deps = proto_deps,
        )

        resolved_data.append(descriptor_set_path)
        resolved_deps.append(":" + descriptor_set_name + "_java_proto")
        jvm_flags.append("-Dfile_descriptor_set_path=$(location {})".format(descriptor_set_path))

    # Add boolean flags
    jvm_flags.append("-Dis_raw_expr={}".format(is_raw_expr))
    jvm_flags.append("-Dis_coverage_enabled={}".format(enable_coverage))

    # Generate the runner binary
    java_binary(
        name = name + "_test_runner_binary",
        srcs = ["//testing/testrunner:test_runner_binary"],
        data = resolved_data,
        jvm_flags = jvm_flags,
        testonly = True,
        main_class = "dev.cel.testing.testrunner.TestRunnerBinary",
        runtime_deps = [test_src],
        deps = [
            "//testing/testrunner:test_executor",
            "@maven//:com_google_guava_guava",
            "@bazel_tools//tools/java/runfiles:runfiles",
        ] + resolved_deps,
    )

    # Generate the execution shell test
    sh_test(
        name = name,
        tags = ["nomsan"],
        srcs = ["//testing/testrunner:run_testrunner_binary.sh"],
        data = [":{}_test_runner_binary".format(name)],
        args = [name],
    )
