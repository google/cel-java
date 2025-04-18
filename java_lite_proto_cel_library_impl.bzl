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

"""
Starlark rule for generating descriptors that is compatible with Protolite Messages.
This is an implementation detail. Clients should use 'java_lite_proto_cel_library' instead.
"""

load("@rules_java//java:defs.bzl", "java_library")
load("@rules_proto//proto:defs.bzl", "proto_descriptor_set")
load("//publish:cel_version.bzl", "CEL_VERSION")
load("@com_google_protobuf//bazel:java_lite_proto_library.bzl", "java_lite_proto_library")

def java_lite_proto_cel_library_impl(
        name,
        java_descriptor_class_name,
        proto_src,
        java_proto_library_dep,
        debug = False):
    """Generates a CelLiteDescriptor

    Args:
       name: name of this target.
       java_descriptor_class_name: Name of the generated descriptor java class.
       proto_src: Name of the proto_library target.
       java_proto_library_dep: (optional) Uses the provided java_lite_proto_library or java_proto_library to generate the lite descriptors. If none is provided, java_lite_proto_library is used by default behind the scenes. Most use cases should not need to provide this.
       debug: (optional) If true, prints additional information during codegen for debugging purposes.
    """
    if not name:
        fail("You must provide a name.")

    if not java_descriptor_class_name:
        fail("You must provide a descriptor_class_prefix.")

    if not proto_src:
        fail("You must provide a proto_library dependency.")

    _generate_cel_lite_descriptor_class(
        name,
        java_descriptor_class_name,
        proto_src,
        debug,
    )

    if not java_proto_library_dep:
        java_proto_library_dep = name + "_java_lite_proto_dep"
        java_lite_proto_library(
            name = java_proto_library_dep,
            deps = [proto_src],
        )

    descriptor_codegen_deps = [
        "//protobuf:cel_lite_descriptor",
        java_proto_library_dep,
    ]

    java_library(
        name = name,
        srcs = [":" + name + "_cel_lite_descriptor"],
        deps = descriptor_codegen_deps,
    )

def _generate_cel_lite_descriptor_class(
        name,
        descriptor_class_name,
        proto_src,
        debug):
    outfile = "%s.java" % descriptor_class_name

    transitive_descriptor_set_name = "%s_transitive_descriptor_set" % name
    proto_descriptor_set(
        name = transitive_descriptor_set_name,
        deps = [proto_src],
    )

    direct_descriptor_set_name = proto_src

    debug_flag = "--debug" if debug else ""

    cmd = (
        "$(location //protobuf:cel_lite_descriptor_generator) " +
        "--descriptor $(location %s) " % direct_descriptor_set_name +
        "--transitive_descriptor_set $(location %s) " % transitive_descriptor_set_name +
        "--descriptor_class_name %s " % descriptor_class_name +
        "--out $(location %s) " % outfile +
        "--version %s " % CEL_VERSION +
        debug_flag
    )

    native.genrule(
        name = name + "_cel_lite_descriptor",
        srcs = [
            transitive_descriptor_set_name,
            direct_descriptor_set_name,
        ],
        cmd = cmd,
        outs = [outfile],
        tools = ["//protobuf:cel_lite_descriptor_generator"],
    )
