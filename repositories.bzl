# Copyright 2024 Google LLC
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      https:#www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

load("@bazel_tools//tools/build_defs/repo:http.bzl", "http_archive", "http_jar")

def antlr4_jar_dependency():
    http_jar(
        name = "antlr4_jar",
        sha256 = "62975e192b4af2622b72b5f0131553ee3cbce97f76dc2a41632dcc55e25473e1",
        urls = ["https://www.antlr.org/download/antlr-4.11.1-complete.jar"],
    )

def bazel_common_dependency():
    bazel_common_tag = "aaa4d801588f7744c6f4428e4f133f26b8518f42"
    bazel_common_sha = "1f85abb0043f3589b9bf13a80319dc48a5f01a052c68bab3c08015a56d92ab7f"
    http_archive(
        name = "bazel_common",
        sha256 = bazel_common_sha,
        strip_prefix = "bazel-common-%s" % bazel_common_tag,
        url = "https://github.com/google/bazel-common/archive/%s.tar.gz" % bazel_common_tag,
    )

def cel_spec_dependency():
    # required by cel_spec
    http_archive(
        name = "io_bazel_rules_go",
        sha256 = "19ef30b21eae581177e0028f6f4b1f54c66467017be33d211ab6fc81da01ea4d",
        urls = [
            "https://mirror.bazel.build/github.com/bazelbuild/rules_go/releases/download/v0.38.0/rules_go-v0.38.0.zip",
            "https://github.com/bazelbuild/rules_go/releases/download/v0.38.0/rules_go-v0.38.0.zip",
        ],
    )

    http_archive(
        name = "cel_spec",
        sha256 = "ad735dcea00992c36c7e94a56bceebedad475a01ee63b49c6796c1fcb7b6a41c",
        strip_prefix = "cel-spec-0.16.2",
        urls = [
            "https://github.com/google/cel-spec/archive/refs/tags/v0.16.2.tar.gz",
        ],
    )

def _non_module_dependencies_impl(_ctx):
    antlr4_jar_dependency()
    bazel_common_dependency()
    cel_spec_dependency()

non_module_dependencies = module_extension(
    implementation = _non_module_dependencies_impl,
)
