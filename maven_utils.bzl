load("@rules_jvm_external//:specs.bzl", "maven")

# Installs the maven JAR as a compile-time only dependency (ex: tools, codegen).
def maven_artifact_compile_only(group, artifact, version):
    return maven.artifact(
        artifact = artifact,
        group = group,
        neverlink = True,
        version = version,
    )

# Installs the maven JAR as a test-time only dependency ().
def maven_artifact_test_only(group, artifact, version):
    return maven.artifact(
        artifact = artifact,
        group = group,
        testonly = True,
        version = version,
    )
