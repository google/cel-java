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

"""Starlark rule for generating descriptors that is compatible with Protolite Messages."""

load("//:java_lite_proto_cel_library_impl.bzl", "java_lite_proto_cel_library_impl")
load("@com_google_protobuf//bazel:java_lite_proto_library.bzl", "java_lite_proto_library")

def java_lite_proto_cel_library(
        name,
        java_descriptor_class_name,
        proto_src,
        debug = False):
    """Generates a CelLiteDescriptor

    Args:
       name: name of this target.
       java_descriptor_class_name: Name of the generated descriptor java class.
       proto_src: Name of the proto_library target.
       debug: (optional) If true, prints additional information during codegen for debugging purposes.
    """
    java_proto_library_dep = name + "_java_lite_proto_dep"
    java_lite_proto_library(
        name = java_proto_library_dep,
        deps = [proto_src],
    )

    java_lite_proto_cel_library_impl(
        name,
        java_descriptor_class_name,
        proto_src,
        java_proto_library_dep,
        debug,
    )
