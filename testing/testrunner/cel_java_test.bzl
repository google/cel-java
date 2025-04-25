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
load("@rules_shell//shell:sh_test.bzl", "sh_test")
load("@bazel_skylib//lib:paths.bzl", "paths")

def cel_java_test(
        name,
        cel_expr,
        test_src,
        is_raw_expr = False,
        test_suite = "",
        filegroup = "",
        config = "",
        deps = [],
        test_data_path = "",
        data = [],
        file_descriptor_set = None):
    """trigger the java impl of the CEL test runner.

    This rule will generate a java_binary and a run_test rule. This rule will be used to trigger
    the java impl of the cel_test rule.

    Args:
        name: str name for the generated artifact
        test_class: str fully qualified user's test class name.
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
        file_descriptor_set: str label or filename pointing to a file_descriptor_set message. Note:
          this must be in binary format. If you need to support a textformat file_descriptor_set,
          embed it in the environment file. (default None)
        test_data_path: absolute path of the directory containing the test files. This is needed only
          if the test files are not located in the same directory as the BUILD file. This
          would be of the form "//foo/bar".
    """

    # TODO: File path computation will be removed once the cel_java_test
    # rule is updated to be integrated with the cel_test rule in order to avoid repetition.
    _, cel_expr_format = paths.split_extension(cel_expr)
    if filegroup != "":
        data = data + [filegroup]
    elif test_data_path != "" and test_data_path != native.package_name():
        if test_suite != "":
            data = data + [test_data_path + ":" + test_suite]
        if config != "":
            data = data + [test_data_path + ":" + config]
        if is_valid_cel_file_format(file_extension = cel_expr_format):
            data = data + [test_data_path + ":" + cel_expr]
    else:
        test_data_path = native.package_name()
        if test_suite != "":
            data = data + [test_suite]
        if config != "":
            data = data + [config]
        if is_valid_cel_file_format(file_extension = cel_expr_format):
            data = data + [cel_expr]

    # Since the test_data_path is of the form "//foo/bar", we need to strip the leading "/" to get
    # the absolute path.
    test_data_path = test_data_path.lstrip("/")
    jvm_flags = []

    if test_suite != "":
        test_suite = test_data_path + "/" + test_suite
        jvm_flags.append("-Dtest_suite_path=%s" % test_suite)

    if config != "":
        config = test_data_path + "/" + config
        jvm_flags.append("-Dconfig_path=%s" % config)

    if file_descriptor_set != None:
        data.append(file_descriptor_set)
        jvm_flags.append("-Dfile_descriptor_set_path=$(location {})".format(file_descriptor_set))

    if is_valid_cel_file_format(file_extension = cel_expr_format) == True:
        jvm_flags.append("-Dcel_expr=%s" % test_data_path + "/" + cel_expr)
    elif is_raw_expr == True:
        jvm_flags.append("-Dcel_expr='%s'" % cel_expr)
    else:
        jvm_flags.append("-Dcel_expr=$(location {})".format(cel_expr))
        data = data + [cel_expr]

    jvm_flags.append("-Dis_raw_expr=%s" % is_raw_expr)

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
