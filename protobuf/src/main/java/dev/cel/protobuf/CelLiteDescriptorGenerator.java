package dev.cel.protobuf;

import com.google.common.io.Files;
import com.google.protobuf.DescriptorProtos.FileDescriptorSet;
import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.FileDescriptor;
import com.google.protobuf.ExtensionRegistry;
import dev.cel.common.CelDescriptorUtil;
import dev.cel.common.internal.ProtoJavaQualifiedNames;
import dev.cel.protobuf.JavaFileGenerator.JavaFileGeneratorOption;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.Callable;
import picocli.CommandLine;
import picocli.CommandLine.Help.Ansi;
import picocli.CommandLine.Model.OptionSpec;
import picocli.CommandLine.Option;

final class CelLiteDescriptorGenerator implements Callable<Integer> {

  @Option(names = {"--out"}, description = "Outpath for the CelLiteDescriptor")
  private String outPath = "";

  @Option(names = {"--descriptor_set"}, description = "Descriptor Set")
  private String descriptorSetPath = "";

  @Option(names = {"--descriptor_class_name"}, description = "Class name for the CelLiteDescriptor")
  private String descriptorClassName = "";

  @Option(names = {"--version"}, description = "CEL-Java version")
  private String version = "";

  @Option(names = {"--debug"}, description = "Prints debug output")
  private boolean debug = false;

  @Override
  public Integer call() throws Exception {
    FileDescriptorSet fds = load(descriptorSetPath);
    String messageClassName = "";
    String protoName = "";
    String packageName = "";
    for (FileDescriptor fd : CelDescriptorUtil.getFileDescriptorsFromFileDescriptorSet(fds)) {
      for (Descriptor descriptor : fd.getMessageTypes()) {
        // TODO: Collect into a set
        protoName = descriptor.getFullName();
        packageName = ProtoJavaQualifiedNames.getJavaPackageName(descriptor);
        messageClassName = ProtoJavaQualifiedNames.getFullyQualifiedJavaClassName(descriptor);
        debugPrint("packageName: " + packageName);
      }
    }

    JavaFileGenerator.createFile(outPath,
        JavaFileGeneratorOption.newBuilder()
            .setVersion(version)
            .setDescriptorClassName(descriptorClassName)
            .setPackageName(packageName)
            .setFullyQualifiedProtoName(protoName)
            .setFullyQualifiedProtoJavaClassName(messageClassName)
            .build());
    return 0;
  }

  private FileDescriptorSet load(String descriptorSetPath) {
    Path path = Paths.get(descriptorSetPath);
    System.out.println("Path: " + path.getFileName());
    try {
      byte[] descriptorBytes = Files.toByteArray(new File(descriptorSetPath));
      // TODO Extensions?
      return FileDescriptorSet.parseFrom(descriptorBytes, ExtensionRegistry.getEmptyRegistry());
    } catch (IOException e) {
      System.out.println("ERROR!!");
      throw new RuntimeException(e);
    }
  }

  public static void main(String[] args) throws Exception {
    CelLiteDescriptorGenerator celLiteDescriptorGenerator = new CelLiteDescriptorGenerator();
    CommandLine cmd = new CommandLine(celLiteDescriptorGenerator);
    cmd.parseArgs(args);
    celLiteDescriptorGenerator.printAllFlags(cmd);

    int exitCode = cmd.execute(args);
    System.exit(exitCode);
  }

  private void printAllFlags(CommandLine cmd) {
    debugPrint("Flag values:");
    debugPrint("-------------------------------------------------------------");
    for (OptionSpec option : cmd.getCommandSpec().options()) {
      debugPrint(option.longestName() + ": " + option.getValue().toString());
    }
    debugPrint("-------------------------------------------------------------");
  }

  private void debugPrint(String message) {
    if (debug) {
      System.out.println(Ansi.ON.string("@|cyan [CelLiteDescriptorGenerator] |@" + message));
    }
  }

  private CelLiteDescriptorGenerator() {}
}