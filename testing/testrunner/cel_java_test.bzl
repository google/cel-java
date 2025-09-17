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
    """trigger the java impl of the CEL test runner.

    This rule will generate a java_binary and a run_test rule. This rule will be used to trigger
    the java impl of the cel_test rule.

    Note: This rule is to be used only for OSS until cel/expr folder is made available in OSS. Internally,
    the cel_test rule is supposed to be used.

    Args:
        name: str name for the generated artifact
        test_suite: str label of a file containing a test suite. The file should have a .yaml or a
          .textproto extension.
        cel_expr: cel expression to be evaluated. This could be a raw expression or a compiled
          expression or cel policy.
        is_raw_expr: bool whether the cel_expr is a raw expression or not. If true, the cel_expr
          will be used as is and would not be treated as a file path.
        filegroup: str label of a filegroup containing the test suite, the config and the checked
          expression.
        config: str label of a file containing a google.api.expr.conformance.Environment message.
          The file should have the .textproto extension.
        test_src: user's test class build target.
        deps: list of dependencies for the java_binary rule.
        data: list of data dependencies for the java_binary rule.
        proto_deps: str label of the proto dependencies for the test. Note: This only supports proto_library rules.
        enable_coverage: bool whether to enable coverage for the test. This is needed only if the
          test runner is being used for gathering coverage data.
        test_data_path: absolute path of the directory containing the test files. This is needed only
          if the test files are not located in the same directory as the BUILD file. This
          would be of the form "//foo/bar".
    """
    jvm_flags = []

    data, test_data_path = _update_data_with_test_files(data, filegroup, test_data_path, config, test_suite, cel_expr, is_raw_expr)

    # Since the test_data_path is of the form "//foo/bar", we need to strip the leading "/" to get
    # the absolute path.
    test_data_path = test_data_path.lstrip("/")

    if test_suite != "":
        test_suite = test_data_path + "/" + test_suite
        jvm_flags.append("-Dtest_suite_path=%s" % test_suite)

    if config != "":
        config = test_data_path + "/" + config
        jvm_flags.append("-Dconfig_path=%s" % config)

    _, cel_expr_format = paths.split_extension(cel_expr)

    if is_valid_cel_file_format(file_extension = cel_expr_format) == True:
        jvm_flags.append("-Dcel_expr=%s" % test_data_path + "/" + cel_expr)
    elif is_raw_expr == True:
        jvm_flags.append("-Dcel_expr='%s'" % cel_expr)
    elif not is_valid_cel_file_format(file_extension = cel_expr_format) and not is_raw_expr:
        jvm_flags.append("-Dcel_expr=$(location {})".format(cel_expr))

    if proto_deps:
        proto_descriptor_set(
            name = name + "_proto_descriptor_set",
            deps = proto_deps,
        )
        descriptor_set_path = ":" + name + "_proto_descriptor_set"
        data.append(descriptor_set_path)
        jvm_flags.append("-Dfile_descriptor_set_path=$(location {})".format(descriptor_set_path))

        java_proto_library(
            name = name + "_proto_descriptor_set_java_proto",
            deps = proto_deps,
        )
        deps = deps + [":" + name + "_proto_descriptor_set_java_proto"]

    jvm_flags.append("-Dis_raw_expr=%s" % is_raw_expr)
    jvm_flags.append("-Dis_coverage_enabled=%s" % enable_coverage)

    java_binary(
        name = name + "_test_runner_binary",
        srcs = ["//testing/testrunner:test_runner_binary"],
        data = data,
        jvm_flags = jvm_flags,
        testonly = True,
        main_class = "dev.cel.testing.testrunner.TestRunnerBinary",
        runtime_deps = [
            test_src,
        ],
        deps = [
            "//testing/testrunner:test_executor",
            "@maven//:com_google_guava_guava",
            "@bazel_tools//tools/java/runfiles:runfiles",
        ] + deps,
    )

    sh_test(
        name = name,
        tags = ["nomsan"],
        srcs = ["//testing/testrunner:run_testrunner_binary.sh"],
        data = [
            ":%s_test_runner_binary" % name,
        ],
        args = [
            name,
        ],
    )

def _update_data_with_test_files(data, filegroup, test_data_path, config, test_suite, cel_expr, is_raw_expr):
    """Updates the data with the test files."""

    _, cel_expr_format = paths.split_extension(cel_expr)
    if filegroup != "":
        data = data + [filegroup]
    elif test_data_path != "" and test_data_path != native.package_name():
        if config != "":
            data = data + [test_data_path + ":" + config]
        if test_suite != "":
            data = data + [test_data_path + ":" + test_suite]
        if is_valid_cel_file_format(file_extension = cel_expr_format):
            data = data + [test_data_path + ":" + cel_expr]
    else:
        test_data_path = native.package_name()
        if config != "":
            data = data + [config]
        if test_suite != "":
            data = data + [test_suite]
        if is_valid_cel_file_format(file_extension = cel_expr_format):
            data = data + [cel_expr]

    if not is_valid_cel_file_format(file_extension = cel_expr_format) and not is_raw_expr:
        data = data + [cel_expr]
    return data, test_data_path

def is_valid_cel_file_format(file_extension):
    """Checks if the file extension is a valid CEL file format.

    Args:
        file_extension: The file extension to check.

    Returns:
        True if the file extension is a valid CEL file format, False otherwise.
    """
    return file_extension in [
        ".cel",
        ".celpolicy",
        ".yaml",
    ]
