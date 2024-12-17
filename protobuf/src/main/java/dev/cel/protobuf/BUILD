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
    name = "cel_lite_descriptor",
    srcs = ["CelLiteDescriptorGenerator.java"],
    main_class = "dev.cel.protobuf.CelLiteDescriptorGenerator",
    deps = [
        ":java_file_generator",
        "@maven//:com_google_code_findbugs_annotations",
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
        "//:auto_value",
        "@maven//:com_google_code_findbugs_annotations",
        "@maven//:com_google_guava_guava",
        "@maven//:org_freemarker_freemarker",
    ],
)
