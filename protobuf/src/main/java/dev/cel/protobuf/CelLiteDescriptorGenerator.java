// Copyright 2025 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      https://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package dev.cel.protobuf;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.Files;
import com.google.protobuf.DescriptorProtos.FileDescriptorProto;
import com.google.protobuf.DescriptorProtos.FileDescriptorSet;
import com.google.protobuf.Descriptors.FileDescriptor;
import com.google.protobuf.ExtensionRegistry;
import dev.cel.common.CelDescriptorUtil;
import dev.cel.common.internal.ProtoJavaQualifiedNames;
import dev.cel.protobuf.JavaFileGenerator.GeneratedClass;
import dev.cel.protobuf.JavaFileGenerator.JavaFileGeneratorOption;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import picocli.CommandLine;
import picocli.CommandLine.Model.OptionSpec;
import picocli.CommandLine.Option;

final class CelLiteDescriptorGenerator implements Callable<Integer> {

  private static final String DEFAULT_CEL_LITE_DESCRIPTOR_CLASS_SUFFIX = "CelLiteDescriptor";

  @Option(
      names = {"--out"},
      description = "Outpath for the CelLiteDescriptor")
  private String outPath = "";

  @Option(
      names = {"--descriptor_set"},
      split = ",",
      description =
          "Paths to the descriptor set (from proto_library) that the CelLiteDescriptor is to be"
              + " generated from (comma-separated)")
  private List<String> targetDescriptorSetPath = new ArrayList<>();

  @Option(
      names = {"--transitive_descriptor_set"},
      split = ",",
      description = "Paths to the transitive set of descriptors (comma-separated)")
  private List<String> transitiveDescriptorSetPath = new ArrayList<>();

  @Option(
      names = {"--overridden_descriptor_class_suffix"},
      description = "Suffix name for the generated CelLiteDescriptor Java class")
  private String overriddenDescriptorClassSuffix = "";

  @Option(
      names = {"--version"},
      description = "CEL-Java version")
  private String version = "";

  @Option(
      names = {"--debug"},
      description = "Prints debug output")
  private boolean debug = false;

  private DebugPrinter debugPrinter;

  @Override
  public Integer call() throws Exception {
    Preconditions.checkArgument(!targetDescriptorSetPath.isEmpty());

    ImmutableList.Builder<GeneratedClass> generatedClassesBuilder = ImmutableList.builder();
    for (String descriptorFilePath : targetDescriptorSetPath) {
      debugPrinter.print("Target descriptor file path: " + descriptorFilePath);
      String targetDescriptorProtoPath = extractProtoPath(descriptorFilePath);
      debugPrinter.print("Target descriptor proto path: " + targetDescriptorProtoPath);
      FileDescriptorSet transitiveDescriptorSet =
          combineFileDescriptors(transitiveDescriptorSetPath);

      FileDescriptor targetFileDescriptor = null;
      ImmutableSet<FileDescriptor> transitiveFileDescriptors =
          CelDescriptorUtil.getFileDescriptorsFromFileDescriptorSet(transitiveDescriptorSet);
      for (FileDescriptor fd : transitiveFileDescriptors) {
        if (fd.getFullName().equals(targetDescriptorProtoPath)) {
          targetFileDescriptor = fd;
          break;
        }
      }

      if (targetFileDescriptor == null) {
        throw new IllegalArgumentException(
            String.format(
                "Target descriptor %s not found from transitive set of descriptors!",
                targetDescriptorProtoPath));
      }

      GeneratedClass generatedClass = codegenCelLiteDescriptor(targetFileDescriptor);
      debugPrinter.print("Generated Class:\n" + generatedClass.code());

      generatedClassesBuilder.add(generatedClass);
    }

    JavaFileGenerator.writeSrcJar(outPath, generatedClassesBuilder.build());

    return 0;
  }

  private GeneratedClass codegenCelLiteDescriptor(FileDescriptor targetFileDescriptor)
      throws Exception {
    String javaPackageName = ProtoJavaQualifiedNames.getJavaPackageName(targetFileDescriptor);
    String javaClassName;

    // Derive the java class name. Use first encountered message/enum in the FDS as a default,
    // with a suffix applied for uniqueness (we don't want to collide with java protoc default
    // generated class name).
    if (!targetFileDescriptor.getMessageTypes().isEmpty()) {
      javaClassName = targetFileDescriptor.getMessageTypes().get(0).getName();
    } else if (!targetFileDescriptor.getEnumTypes().isEmpty()) {
      javaClassName = targetFileDescriptor.getEnumTypes().get(0).getName();
    } else {
      throw new IllegalArgumentException("File descriptor does not contain any messages or enums!");
    }

    String javaSuffixName =
        overriddenDescriptorClassSuffix.isEmpty()
            ? DEFAULT_CEL_LITE_DESCRIPTOR_CLASS_SUFFIX
            : overriddenDescriptorClassSuffix;
    javaClassName += javaSuffixName;

    ProtoDescriptorCollector descriptorCollector =
        ProtoDescriptorCollector.newInstance(debugPrinter);

    debugPrinter.print(
        String.format(
            "Fully qualified descriptor java class name: %s.%s", javaPackageName, javaClassName));

    return JavaFileGenerator.generateClass(
        JavaFileGeneratorOption.newBuilder()
            .setVersion(version)
            .setDescriptorClassName(javaClassName)
            .setPackageName(javaPackageName)
            .setDescriptorMetadataList(
                descriptorCollector.collectCodegenMetadata(targetFileDescriptor))
            .build());
  }

  private String extractProtoPath(String descriptorPath) {
    FileDescriptorSet fds = load(descriptorPath);
    if (fds.getFileList().isEmpty()) {
      throw new IllegalArgumentException(
          "FileDescriptorSet did not contain any descriptors: " + descriptorPath);
    }

    // A direct descriptor set may contain one or more files (ex: extensions), but the first
    // argument is always the original .proto file.
    FileDescriptorProto fileDescriptorProto = fds.getFile(0);
    return fileDescriptorProto.getName();
  }

  private FileDescriptorSet combineFileDescriptors(List<String> descriptorPaths) {
    FileDescriptorSet.Builder combinedDescriptorBuilder = FileDescriptorSet.newBuilder();

    for (String descriptorPath : descriptorPaths) {
      FileDescriptorSet loadedFds = load(descriptorPath);
      combinedDescriptorBuilder.addAllFile(loadedFds.getFileList());
    }

    return combinedDescriptorBuilder.build();
  }

  private static FileDescriptorSet load(String descriptorSetPath) {
    try {
      byte[] descriptorBytes = Files.toByteArray(new File(descriptorSetPath));
      return FileDescriptorSet.parseFrom(descriptorBytes, ExtensionRegistry.getEmptyRegistry());
    } catch (IOException e) {
      throw new IllegalArgumentException(
          "Failed to load FileDescriptorSet from path: " + descriptorSetPath, e);
    }
  }

  private void printAllFlags(CommandLine cmd) {
    debugPrinter.print("Flag values:");
    debugPrinter.print("-------------------------------------------------------------");
    for (OptionSpec option : cmd.getCommandSpec().options()) {
      debugPrinter.print(option.longestName() + ": " + option.getValue());
    }
    debugPrinter.print("-------------------------------------------------------------");
  }

  private void initializeDebugPrinter() {
    this.debugPrinter = DebugPrinter.newInstance(debug);
  }

  public static void main(String[] args) {
    CelLiteDescriptorGenerator celLiteDescriptorGenerator = new CelLiteDescriptorGenerator();
    CommandLine cmd = new CommandLine(celLiteDescriptorGenerator);
    cmd.parseArgs(args);
    celLiteDescriptorGenerator.initializeDebugPrinter();
    celLiteDescriptorGenerator.printAllFlags(cmd);

    int exitCode = cmd.execute(args);
    System.exit(exitCode);
  }

  CelLiteDescriptorGenerator() {}
}
