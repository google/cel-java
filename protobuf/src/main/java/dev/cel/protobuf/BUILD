load("@rules_java//java:defs.bzl", "java_binary")

package(
    default_visibility = ["//visibility:public"],
)

filegroup(
    name = "template_files",
    srcs = glob(["templates/*.ftlh"]),
    visibility = ["//visibility:private"],
)

java_binary(
    name = "cel_lite_descriptor_generator",
    srcs = ["CelLiteDescriptorGenerator.java"],
    main_class = "dev.cel.protobuf.CelLiteDescriptorGenerator",
    deps = [
        ":cel_lite_descriptor",
        ":java_file_generator",
        "//common",
        "//common/internal:proto_java_qualified_names",
        "//common/internal:well_known_proto",
        "@maven//:com_google_guava_guava",
        "@maven//:com_google_protobuf_protobuf_java",
        "@maven//:info_picocli_picocli",
    ],
)

java_library(
    name = "java_file_generator",
    srcs = ["JavaFileGenerator.java"],
    resources = [
        ":template_files",
    ],
    deps = [
        ":cel_lite_descriptor",
        "//:auto_value",
        "@maven//:com_google_guava_guava",
        "@maven//:org_freemarker_freemarker",
    ],
)

java_library(
    name = "cel_lite_descriptor",
    srcs = ["CelLiteDescriptor.java"],
    deps = [
        "//common/annotations",
        "@maven//:com_google_errorprone_error_prone_annotations",
        "@maven//:com_google_guava_guava",
        "@maven//:com_google_protobuf_protobuf_java",
    ],
)
