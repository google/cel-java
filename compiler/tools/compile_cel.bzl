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
"""Rule for compiling CEL expressions at build time.
"""

load("@rules_proto//proto:defs.bzl", "proto_descriptor_set")

def compile_cel(
        name,
        expression,
        proto_srcs = [],
        output = None):
    """Compiles a CEL expression, generating a cel.expr.ChecekdExpr proto. This proto is written to a `.binarypb` file.

    Args:
          name: str name for the generated artifact
          expression: str CEL expression to compile
          proto_srcs: (optional) list of str label(s) pointing to a proto_library rule (important: NOT java_proto_library). This must be provided when compiling a CEL expression containing protobuf messages.
          output: (optional) str file name for the output checked expression. `.binarypb` extension is automatically appended in the filename.
    """

    if output == None:
        output = name

    output = output + ".binarypb"

    transitive_descriptor_set_flag = ""
    genrule_srcs = []

    if len(proto_srcs) > 0:
        transitive_descriptor_set_name = "%s_transitive_descriptor_set" % name
        proto_descriptor_set(
            name = transitive_descriptor_set_name,
            deps = proto_srcs,
        )
        transitive_descriptor_set_flag = "--transitive_descriptor_set $(location %s) " % transitive_descriptor_set_name
        genrule_srcs.append(transitive_descriptor_set_name)

    cmd = (
        "$(location //compiler/tools:cel_compiler_tool) " +
        "--cel_expression \"%s\" " % expression +
        transitive_descriptor_set_flag +
        "--output $(location %s) " % output
    )

    native.genrule(
        name = name,
        cmd = cmd,
        srcs = genrule_srcs,
        outs = [output],
        tools = ["//compiler/tools:cel_compiler_tool"],
    )
