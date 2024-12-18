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

load("@rules_java//java:defs.bzl", "java_library")
load("@rules_proto//proto:defs.bzl", "proto_descriptor_set")
load("//publish:cel_version.bzl", "CEL_VERSION")

def java_lite_proto_cel_library(
        name,
        descriptor_class_prefix,
        deps):
    artifacts = generate_cel_lite_descriptor_class(
        name,
        descriptor_class_prefix + "CelLiteDescriptor",
        deps,
    )

    descriptor_codegen_deps = [
        "@maven//:com_google_guava_guava",
        "@maven//:javax_annotation_javax_annotation_api",
        "//protobuf:cel_lite_descriptor",
    ]

    java_library(
        name = name,
        srcs = [":" + name + "_cel_lite_descriptor"],
        deps = deps + descriptor_codegen_deps,
    )

def generate_cel_lite_descriptor_class(
        name,
        descriptor_class_name,
        proto_srcs):
    internal_descriptor_set_name = "%s_descriptor_set_internal" % name
    outfile = "%s.java" % descriptor_class_name
    package_name = native.package_name().replace("/", ".")

    proto_descriptor_set(
        name = internal_descriptor_set_name,
        deps = proto_srcs,
    )

    cmd = (
        "$(location //protobuf/src/main/java/dev/cel/protobuf:cel_lite_descriptor_generator) " +
        "--descriptor_set $(location %s) " % internal_descriptor_set_name +
        "--descriptor_class_name %s " % descriptor_class_name +
        "--out $(location %s) " % outfile +
        "--version %s " % CEL_VERSION +
        "--debug"
    )

    native.genrule(
        name = name + "_cel_lite_descriptor",
        srcs = [":%s" % internal_descriptor_set_name],
        cmd = cmd,
        outs = [outfile],
        tools = ["//protobuf/src/main/java/dev/cel/protobuf:cel_lite_descriptor_generator"],
    )

    return {
        "internal_descriptor_set": internal_descriptor_set_name,
    }
