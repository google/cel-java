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

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.io.Files;
import com.google.protobuf.DescriptorProtos.FileDescriptorProto;
import com.google.protobuf.DescriptorProtos.FileDescriptorSet;
import com.google.protobuf.Descriptors.FileDescriptor;
import com.google.protobuf.ExtensionRegistry;
import dev.cel.common.CelDescriptorUtil;
import dev.cel.common.internal.ProtoJavaQualifiedNames;
import dev.cel.protobuf.JavaFileGenerator.JavaFileGeneratorOption;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.Callable;
import picocli.CommandLine;
import picocli.CommandLine.Model.OptionSpec;
import picocli.CommandLine.Option;

final class CelLiteDescriptorGenerator implements Callable<Integer> {

  @Option(
      names = {"--out"},
      description = "Outpath for the CelLiteDescriptor")
  private String outPath = "";

  @Option(
      names = {"--descriptor"},
      description =
          "Path to the descriptor (from proto_library) that the CelLiteDescriptor is to be"
              + " generated from")
  private String targetDescriptorPath = "";

  @Option(
      names = {"--transitive_descriptor_set"},
      description = "Path to the transitive set of descriptors")
  private String transitiveDescriptorSetPath = "";

  @Option(
      names = {"--descriptor_class_name"},
      description = "Class name for the CelLiteDescriptor")
  private String descriptorClassName = "";

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
    String targetDescriptorProtoPath = extractProtoPath(targetDescriptorPath);
    debugPrinter.print("Target descriptor proto path: " + targetDescriptorProtoPath);

    FileDescriptor targetFileDescriptor = null;
    ImmutableSet<FileDescriptor> transitiveFileDescriptors =
        CelDescriptorUtil.getFileDescriptorsFromFileDescriptorSet(
            load(transitiveDescriptorSetPath));
    for (FileDescriptor fd : transitiveFileDescriptors) {
      if (fd.getFullName().equals(targetDescriptorProtoPath)) {
        debugPrinter.print("Transitive Descriptor Path: " + fd.getFullName());
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

    codegenCelLiteDescriptor(targetFileDescriptor);

    return 0;
  }

  private void codegenCelLiteDescriptor(FileDescriptor targetFileDescriptor) throws Exception {
    String javaPackageName = ProtoJavaQualifiedNames.getJavaPackageName(targetFileDescriptor);
    ProtoDescriptorCollector descriptorCollector =
        ProtoDescriptorCollector.newInstance(debugPrinter);

    debugPrinter.print(
        String.format("Descriptor Java class name: %s.%s", javaPackageName, descriptorClassName));

    JavaFileGenerator.createFile(
        outPath,
        JavaFileGeneratorOption.newBuilder()
            .setVersion(version)
            .setDescriptorClassName(descriptorClassName)
            .setPackageName(javaPackageName)
            .setMessageInfoList(descriptorCollector.collectMessageInfo(targetFileDescriptor))
            .build());
  }

  private String extractProtoPath(String descriptorPath) {
    FileDescriptorSet fds = load(descriptorPath);
    FileDescriptorProto fileDescriptorProto = Iterables.getOnlyElement(fds.getFileList());
    return fileDescriptorProto.getName();
  }

  private FileDescriptorSet load(String descriptorSetPath) {
    try {
      byte[] descriptorBytes = Files.toByteArray(new File(descriptorSetPath));
      // TODO: Implement ProtoExtensions
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
