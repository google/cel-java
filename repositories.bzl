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

load("@bazel_tools//tools/build_defs/repo:http.bzl", "http_jar")

def antlr4_jar_dependency():
    http_jar(
        name = "antlr4_jar",
        sha256 = "62975e192b4af2622b72b5f0131553ee3cbce97f76dc2a41632dcc55e25473e1",
        urls = ["https://www.antlr.org/download/antlr-4.11.1-complete.jar"],
    )

def _non_module_dependencies_impl(_ctx):
    antlr4_jar_dependency()

non_module_dependencies = module_extension(
    implementation = _non_module_dependencies_impl,
)

