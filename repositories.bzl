# Copyright 2024 Google LLC
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      https://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

"""Manages bazel dependencies that are not present in bazel central registry."""

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

def googleapis_dependency():
    http_archive(
        name = "com_google_googleapis",
        sha256 = "8503282213779a3c230251218c924f385f457a053b4f82ff95d068f71815e558",
        strip_prefix = "googleapis-d73a41615b101c34c58b3534c2cc7ee1d89cccb0",
        urls = [
            "https://github.com/googleapis/googleapis/archive/d73a41615b101c34c58b3534c2cc7ee1d89cccb0.tar.gz",
        ],
    )

def _non_module_dependencies_impl(_ctx):
    antlr4_jar_dependency()
    bazel_common_dependency()
    googleapis_dependency()

non_module_dependencies = module_extension(
    implementation = _non_module_dependencies_impl,
)
