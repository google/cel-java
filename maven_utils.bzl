# Copyright 2025 Google LLC
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

"""Utility functions for installing maven artifact in Bazel WORKSPACE."""

load("@rules_jvm_external//:specs.bzl", "maven")

def maven_artifact_compile_only(group, artifact, version):
    """Installs the maven JAR as a compile-time only dependency (ex: tools, codegen)."""
    return maven.artifact(
        artifact = artifact,
        group = group,
        neverlink = True,
        version = version,
    )

def maven_artifact_test_only(group, artifact, version):
    """Installs the maven JAR as a test-time only dependency (ex: tools, codegen)."""
    return maven.artifact(
        artifact = artifact,
        group = group,
        testonly = True,
        version = version,
    )
