load("@rules_java//java:defs.bzl", "java_binary")

package(
    default_visibility = ["//visibility:public"],
)

java_binary(
    name = "cel_lite_descriptor",
    srcs = ["CelLiteDescriptorGenerator.java"],
    main_class = "dev.cel.protobuf.CelLiteDescriptorGenerator",
)
