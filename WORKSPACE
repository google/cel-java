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

# Load license rules.
# Must be loaded first due to https://github.com/bazel-contrib/rules_jvm_external/issues/1244

load("@bazel_tools//tools/build_defs/repo:http.bzl", "http_archive")
http_archive(
    name = "rules_license",
    urls = [
        "https://mirror.bazel.build/github.com/bazelbuild/rules_license/releases/download/1.0.0/rules_license-1.0.0.tar.gz",
        "https://github.com/bazelbuild/rules_license/releases/download/1.0.0/rules_license-1.0.0.tar.gz",
    ],
    sha256 = "26d4021f6898e23b82ef953078389dd49ac2b5618ac564ade4ef87cced147b38",
)

http_archive(
    name = "bazel_skylib",
    sha256 = "bc283cdfcd526a52c3201279cda4bc298652efa898b10b4db0837dc51652756f",
    urls = [
        "https://mirror.bazel.build/github.com/bazelbuild/bazel-skylib/releases/download/1.7.1/bazel-skylib-1.7.1.tar.gz",
        "https://github.com/bazelbuild/bazel-skylib/releases/download/1.7.1/bazel-skylib-1.7.1.tar.gz",
    ],
)

load("@bazel_skylib//:workspace.bzl", "bazel_skylib_workspace")

bazel_skylib_workspace()

### Protobuf Setup

http_archive(
    name = "rules_java",
    urls = [
        "https://github.com/bazelbuild/rules_java/releases/download/8.9.0/rules_java-8.9.0.tar.gz",
    ],
    sha256 = "8daa0e4f800979c74387e4cd93f97e576ec6d52beab8ac94710d2931c57f8d8b",
)

http_archive(
    name = "rules_python",
    sha256 = "9c6e26911a79fbf510a8f06d8eedb40f412023cf7fa6d1461def27116bff022c",
    strip_prefix = "rules_python-1.1.0",
    url = "https://github.com/bazelbuild/rules_python/releases/download/1.1.0/rules_python-1.1.0.tar.gz",
)

http_archive(
    name = "rules_proto",
    sha256 = "14a225870ab4e91869652cfd69ef2028277fc1dc4910d65d353b62d6e0ae21f4",
    strip_prefix = "rules_proto-7.1.0",
    url = "https://github.com/bazelbuild/rules_proto/releases/download/7.1.0/rules_proto-7.1.0.tar.gz",
)

http_archive(
    name = "com_google_protobuf",
    sha256 = "008a11cc56f9b96679b4c285fd05f46d317d685be3ab524b2a310be0fbad987e",
    strip_prefix = "protobuf-29.3",
    urls = ["https://github.com/protocolbuffers/protobuf/archive/v29.3.tar.gz"],
)

load("@com_google_protobuf//:protobuf_deps.bzl", "protobuf_deps")
protobuf_deps()

load("@rules_java//java:rules_java_deps.bzl", "rules_java_dependencies")
rules_java_dependencies()

load("@rules_java//java:repositories.bzl", "rules_java_toolchains")
rules_java_toolchains()

load("@rules_python//python:repositories.bzl", "py_repositories")
py_repositories()

load("@rules_proto//proto:repositories.bzl", "rules_proto_dependencies")
rules_proto_dependencies()

load("@rules_proto//proto:toolchains.bzl", "rules_proto_toolchains")
rules_proto_toolchains()


### End of Protobuf Setup


### rules_jvm_external setup

RULES_JVM_EXTERNAL_TAG = "6.6"

RULES_JVM_EXTERNAL_SHA = "3afe5195069bd379373528899c03a3072f568d33bd96fe037bd43b1f590535e7"

http_archive(
    name = "rules_jvm_external",
    sha256 = RULES_JVM_EXTERNAL_SHA,
    strip_prefix = "rules_jvm_external-%s" % RULES_JVM_EXTERNAL_TAG,
    url = "https://github.com/bazelbuild/rules_jvm_external/releases/download/%s/rules_jvm_external-%s.tar.gz" % (RULES_JVM_EXTERNAL_TAG, RULES_JVM_EXTERNAL_TAG),
)

load("@rules_jvm_external//:repositories.bzl", "rules_jvm_external_deps")

rules_jvm_external_deps()

load("@rules_jvm_external//:setup.bzl", "rules_jvm_external_setup")

rules_jvm_external_setup()

load("@rules_jvm_external//:defs.bzl", "maven_install")
load("//:maven_utils.bzl", "maven_artifact_compile_only", "maven_artifact_test_only")

### end of rules_jvm_external setup

ANTLR4_VERSION = "4.13.2"
maven_install(
    name = "maven",
    # keep sorted
    artifacts = [
        "com.google.auto.value:auto-value:1.11.0",
        "com.google.auto.value:auto-value-annotations:1.11.0",
        "com.google.guava:guava:33.4.0-jre",
        "com.google.guava:guava-testlib:33.4.0-jre",
        "com.google.protobuf:protobuf-java:4.29.3",
        "com.google.protobuf:protobuf-java-util:4.29.3",
        "com.google.re2j:re2j:1.8",
        "info.picocli:picocli:4.7.6",
        "org.antlr:antlr4-runtime:" + ANTLR4_VERSION,
        "info.picocli:picocli:4.7.6",
        "org.freemarker:freemarker:2.3.33",
        "org.jspecify:jspecify:1.0.0",
        "org.threeten:threeten-extra:1.8.0",
        "org.yaml:snakeyaml:2.3",
        maven_artifact_test_only("io.github.classgraph", "classgraph", "4.8.179"),
        maven_artifact_test_only("com.google.testparameterinjector", "test-parameter-injector", "1.18"),
        maven_artifact_test_only("com.google.truth", "truth", "1.4.4"),
        maven_artifact_test_only("com.google.truth.extensions", "truth-java8-extension", "1.4.4"),
        maven_artifact_test_only("com.google.truth.extensions", "truth-proto-extension", "1.4.4"),
        maven_artifact_test_only("com.google.truth.extensions", "truth-liteproto-extension", "1.4.4"),
        maven_artifact_compile_only("com.google.code.findbugs", "annotations", "3.0.1"),
        maven_artifact_compile_only("com.google.errorprone", "error_prone_annotations", "2.36.0"),
    ],
    repositories = [
        "https://maven.google.com",
        "https://repo1.maven.org/maven2",
    ],
)

maven_install(
    name = "maven_android",
    # keep sorted
    artifacts = [
        "com.google.guava:guava:33.4.0-android",
        "com.google.protobuf:protobuf-javalite:4.29.3",
    ],
    repositories = [
        "https://maven.google.com",
        "https://repo1.maven.org/maven2",
    ],
)

### rules_android setup
http_archive(
    name = "rules_android",
    sha256 = "20d78e80007335ae83bf0de6fd7be192f7b6550857fa93734dbc287995eee756",
    strip_prefix = "rules_android-0.6.2",
    url = "https://github.com/bazelbuild/rules_android/releases/download/v0.6.2/rules_android-v0.6.2.tar.gz",
)

load("@rules_android//:prereqs.bzl", "rules_android_prereqs")
rules_android_prereqs()

load("@rules_android//:defs.bzl", "rules_android_workspace")
rules_android_workspace()

load("@rules_android//rules:rules.bzl", "android_sdk_repository")
android_sdk_repository(
    name = "androidsdk"
)

register_toolchains(
    "@rules_android//toolchains/android:android_default_toolchain",
    "@rules_android//toolchains/android_sdk:android_sdk_tools",
)

### end of rules_android setup

### googleapis setup

# as of 12/08/2022
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

http_archive(
    name = "rules_pkg",
    sha256 = "8a298e832762eda1830597d64fe7db58178aa84cd5926d76d5b744d6558941c2",
    urls = [
        "https://github.com/bazelbuild/rules_pkg/releases/download/0.7.0/rules_pkg-0.7.0.tar.gz",
    ],
)

load("@rules_pkg//:deps.bzl", "rules_pkg_dependencies")

rules_pkg_dependencies()

### end of googleapis setup

# bazel_common required for maven jar publishing

BAZEL_COMMON_TAG = "aaa4d801588f7744c6f4428e4f133f26b8518f42"

BAZEL_COMMON_SHA = "1f85abb0043f3589b9bf13a80319dc48a5f01a052c68bab3c08015a56d92ab7f"

http_archive(
    name = "bazel_common",
    sha256 = BAZEL_COMMON_SHA,
    strip_prefix = "bazel-common-%s" % BAZEL_COMMON_TAG,
    url = "https://github.com/google/bazel-common/archive/%s.tar.gz" % BAZEL_COMMON_TAG,
)

# cel-spec api/expr canonical protos
CEL_SPEC_VERSION = "0.23.1"

http_archive(
    name = "cel_spec",
    sha256 = "8bafa44e610eb281df8b1268a42b5e2d7b76d60d0b3c817835cfcfd14cc2bc9c",
    strip_prefix = "cel-spec-" + CEL_SPEC_VERSION,
    urls = [
        "https://github.com/google/cel-spec/archive/" +
        "v" + CEL_SPEC_VERSION +
        ".tar.gz",
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
    sha256 = "eae2dfa119a64327444672aff63e9ec35a20180dc5b8090b7a6ab85125df4d76",
    urls = ["https://www.antlr.org/download/antlr-" + ANTLR4_VERSION + "-complete.jar"],
)

