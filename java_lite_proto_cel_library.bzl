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

def java_lite_proto_cel_library(
        name,
        deps):
    print("name: " + name)

    artifacts = generate_cel_lite_descriptor_class(
        name,
        deps,
        "",
        "",
    )

    java_library(
        name = name,
        srcs = [":" + name + "_foo"],
        deps = deps,
    )

def generate_cel_lite_descriptor_class(
        name,
        proto_srcs,
        helper_class_name,
        helper_class_path):
    internal_descriptor_set_name = "%s_descriptor_set_internal" % name

    proto_descriptor_set(
        name = internal_descriptor_set_name,
        deps = proto_srcs,
    )

    cmd = (
        "$(location //protobuf/src/main/java/dev/cel/protobuf:cel_lite_descriptor) " +
        "--descriptor_set $(location %s) " % internal_descriptor_set_name +
        "--outpath $(location foo.java) " +
        "--debug"
    )

    native.genrule(
        name = name + "_foo",
        srcs = [":%s" % internal_descriptor_set_name],
        cmd = cmd,
        outs = ["foo.java"],
        tools = ["//protobuf/src/main/java/dev/cel/protobuf:cel_lite_descriptor"],
    )

    return {
        "internal_descriptor_set": internal_descriptor_set_name,
    }
