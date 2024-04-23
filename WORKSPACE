# Copyright 2022 Google LLC
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

workspace(name = "cel_java")

register_toolchains("//:repository_default_toolchain_definition")

load("@bazel_tools//tools/build_defs/repo:http.bzl", "http_archive", "http_jar")

http_archive(
    name = "bazel_skylib",
    sha256 = "74d544d96f4a5bb630d465ca8bbcfe231e3594e5aae57e1edbf17a6eb3ca2506",
    urls = [
        "https://mirror.bazel.build/github.com/bazelbuild/bazel-skylib/releases/download/1.3.0/bazel-skylib-1.3.0.tar.gz",
        "https://github.com/bazelbuild/bazel-skylib/releases/download/1.3.0/bazel-skylib-1.3.0.tar.gz",
    ],
)

load("@bazel_skylib//:workspace.bzl", "bazel_skylib_workspace")

bazel_skylib_workspace()

RULES_JVM_EXTERNAL_TAG = "aa44247b3913da0da606e9c522313b6a9396a571"

RULES_JVM_EXTERNAL_SHA = "87378580865af690a78230e04eba1cd6d9c60d0db303ea129dc717705d711d9c"

# rules_jvm_external as of 12/11/2023
http_archive(
    name = "rules_jvm_external",
    sha256 = RULES_JVM_EXTERNAL_SHA,
    strip_prefix = "rules_jvm_external-%s" % RULES_JVM_EXTERNAL_TAG,
    url = "https://github.com/bazelbuild/rules_jvm_external/archive/%s.zip" % RULES_JVM_EXTERNAL_TAG,
)

load("@rules_jvm_external//:defs.bzl", "maven_install")
load("@rules_jvm_external//:repositories.bzl", "rules_jvm_external_deps")

rules_jvm_external_deps()

load("@rules_jvm_external//:setup.bzl", "rules_jvm_external_setup")

rules_jvm_external_setup()

ANTLR4_VERSION = "4.13.1"

# Important: there can only be one maven_install rule. Add new maven deps here.
maven_install(
    # keep sorted
    artifacts = [
        "com.google.api.grpc:proto-google-common-protos:2.38.0",
        "com.google.auto.value:auto-value:1.10.4",
        "com.google.auto.value:auto-value-annotations:1.10.4",
        "com.google.code.findbugs:annotations:3.0.1",
        "com.google.errorprone:error_prone_annotations:2.26.1",
        "com.google.guava:guava:33.1.0-jre",
        "com.google.guava:guava-testlib:33.1.0-jre",
        "com.google.protobuf:protobuf-java:3.25.3",
        "com.google.protobuf:protobuf-java-util:3.25.3",
        "com.google.re2j:re2j:1.7",
        "com.google.testparameterinjector:test-parameter-injector:1.15",
        "com.google.truth.extensions:truth-java8-extension:1.4.2",
        "com.google.truth.extensions:truth-proto-extension:1.4.2",
        "com.google.truth:truth:1.4.2",
        "org.antlr:antlr4-runtime:" + ANTLR4_VERSION,
        "org.jspecify:jspecify:0.2.0",
        "org.threeten:threeten-extra:1.8.0",
    ],
    repositories = [
        "https://maven.google.com",
        "https://repo1.maven.org/maven2",
    ],
)

http_archive(
    name = "com_google_protobuf",
    sha256 = "b1d6dd2cbb5d87e17af41cadb720322ce7e13af826268707bd8db47e5654770b",
    strip_prefix = "protobuf-21.11",
    urls = ["https://github.com/protocolbuffers/protobuf/archive/v21.11.tar.gz"],
)

# Required by com_google_protobuf
load("@com_google_protobuf//:protobuf_deps.bzl", "protobuf_deps")

protobuf_deps()

# googleapis as of 12/08/2022
http_archive(
    name = "com_google_googleapis",
    sha256 = "8503282213779a3c230251218c924f385f457a053b4f82ff95d068f71815e558",
    strip_prefix = "googleapis-d73a41615b101c34c58b3534c2cc7ee1d89cccb0",
    urls = [
        "https://github.com/googleapis/googleapis/archive/d73a41615b101c34c58b3534c2cc7ee1d89cccb0.tar.gz",
    ],
)

load("@com_google_googleapis//:repository_rules.bzl", "switched_rules_by_language")

switched_rules_by_language(
    name = "com_google_googleapis_imports",
    java = True,
)

# Required by googleapis
http_archive(
    name = "rules_pkg",
    sha256 = "8a298e832762eda1830597d64fe7db58178aa84cd5926d76d5b744d6558941c2",
    urls = [
        "https://github.com/bazelbuild/rules_pkg/releases/download/0.7.0/rules_pkg-0.7.0.tar.gz",
    ],
)

load("@rules_pkg//:deps.bzl", "rules_pkg_dependencies")

rules_pkg_dependencies()

BAZEL_COMMON_TAG = "aaa4d801588f7744c6f4428e4f133f26b8518f42"

BAZEL_COMMON_SHA = "1f85abb0043f3589b9bf13a80319dc48a5f01a052c68bab3c08015a56d92ab7f"

http_archive(
    name = "bazel_common",
    sha256 = BAZEL_COMMON_SHA,
    strip_prefix = "bazel-common-%s" % BAZEL_COMMON_TAG,
    url = "https://github.com/google/bazel-common/archive/%s.tar.gz" % BAZEL_COMMON_TAG,
)

# cel-spec api/expr canonical protos
http_archive(
    name = "cel_spec",
    sha256 = "3ee09eb69dbe77722e9dee23dc48dc2cd9f765869fcf5ffb1226587c81791a0b",
    strip_prefix = "cel-spec-0.15.0",
    urls = [
        "https://github.com/google/cel-spec/archive/refs/tags/v0.15.0.tar.gz",
    ],
)

# required by cel_spec
http_archive(
    name = "io_bazel_rules_go",
    sha256 = "19ef30b21eae581177e0028f6f4b1f54c66467017be33d211ab6fc81da01ea4d",
    urls = [
        "https://mirror.bazel.build/github.com/bazelbuild/rules_go/releases/download/v0.38.0/rules_go-v0.38.0.zip",
        "https://github.com/bazelbuild/rules_go/releases/download/v0.38.0/rules_go-v0.38.0.zip",
    ],
)

http_jar(
    name = "antlr4_jar",
    sha256 = "bc13a9c57a8dd7d5196888211e5ede657cb64a3ce968608697e4f668251a8487",
    urls = ["https://www.antlr.org/download/antlr-" + ANTLR4_VERSION + "-complete.jar"],
)

# Load license rules.
http_archive(
    name = "rules_license",
    sha256 = "6157e1e68378532d0241ecd15d3c45f6e5cfd98fc10846045509fb2a7cc9e381",
    urls = [
        "https://github.com/bazelbuild/rules_license/releases/download/0.0.4/rules_license-0.0.4.tar.gz",
        "https://mirror.bazel.build/github.com/bazelbuild/rules_license/releases/download/0.0.4/rules_license-0.0.4.tar.gz",
    ],
)
