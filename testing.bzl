# Copyright 2022 Google LLC
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      https://www.apache.org/licenses/LICENSE-2.0.txt
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

# From: https://github.com/google/guice/blob/master/test_defs.bzl

"""Starlark macros to generate test suites."""

_TEMPLATE = """
package {VAR_PACKAGE};
import org.junit.runners.Suite;
import org.junit.runner.RunWith;

@RunWith(Suite.class)
@Suite.SuiteClasses({{{VAR_CLASSES}}})
public class {VAR_NAME} {{}}
"""

def _impl(ctx):
    classes = ",".join(sorted(ctx.attr.test_classes))

    ctx.actions.write(
        output = ctx.outputs.out,
        content = _TEMPLATE.format(
            VAR_PACKAGE = ctx.attr.package_name,
            VAR_CLASSES = classes,
            VAR_NAME = ctx.attr.name,
        ),
    )

_gen_suite = rule(
    attrs = {
        "test_classes": attr.string_list(),
        "package_name": attr.string(),
    },
    outputs = {"out": "%{name}.java"},
    implementation = _impl,
)

def junit4_test_suites(name, sizes, deps, srcs=None):
    """
    Generates tests for test files in srcs ending in "Test.java"

    Args:
      name: Name of the test suite to generate
      sizes: Not used, exists only so that the open-source CEL mirror exactly the internal one
      srcs: List of test source files. If not specified, it uses 'glob(["**/*Test.java"])'

    Returns:
      None
    """

    package_name = native.package_name()

    # Strip the path prefix from the package name so that we get the correct test class name
    # "common/src/test/java/dev/cel/common/internal" becomes "dev/cel/common/internal"
    package_name = package_name.rpartition("/test/java/")[2]

    test_files = srcs or native.glob(["**/*Test.java"])
    test_classes = []
    for src in test_files:
        test_name = src.replace(".java", "")
        test_classes.append((package_name + "/" + test_name + ".class").replace("/", "."))

    suite_name = name
    _gen_suite(
        name = suite_name,
        test_classes = test_classes,
        package_name = package_name.replace("/", "."),
    )

    native.java_test(
        name = "AllTestsSuite",
        test_class = (package_name + "/" + suite_name).replace("/", "."),
        srcs = [":" + suite_name],
        deps = deps,
        tags = sizes,
    )
