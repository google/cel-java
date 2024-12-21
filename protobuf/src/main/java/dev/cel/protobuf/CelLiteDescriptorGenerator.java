package dev.cel.protobuf;


import com.google.common.base.CaseFormat;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.io.Files;
import com.google.protobuf.DescriptorProtos.FileDescriptorProto;
import com.google.protobuf.DescriptorProtos.FileDescriptorSet;
import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.Descriptors.FieldDescriptor.JavaType;
import com.google.protobuf.Descriptors.FileDescriptor;
import com.google.protobuf.ExtensionRegistry;
import dev.cel.common.CelDescriptorUtil;
import dev.cel.common.internal.ProtoJavaQualifiedNames;
import dev.cel.protobuf.CelLiteDescriptor.FieldInfo;
import dev.cel.protobuf.CelLiteDescriptor.FieldInfo.Type;
import dev.cel.protobuf.CelLiteDescriptor.MessageInfo;
import dev.cel.protobuf.JavaFileGenerator.JavaFileGeneratorOption;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.Callable;
import picocli.CommandLine;
import picocli.CommandLine.Help.Ansi;
import picocli.CommandLine.Model.OptionSpec;
import picocli.CommandLine.Option;

final class CelLiteDescriptorGenerator implements Callable<Integer> {

  @Option(names = {"--out"}, description = "Outpath for the CelLiteDescriptor")
  private String outPath = "";

  @Option(names = {"--descriptor"}, description = "Path to the descriptor (from proto_library) that the CelLiteDescriptor is to be generated from")
  private String targetDescriptorPath = "";

  @Option(names = {"--transitive_descriptor_set"}, description = "Path to the transitive set of descriptors")
  private String transitiveDescriptorSetPath = "";

  @Option(names = {"--descriptor_class_name"}, description = "Class name for the CelLiteDescriptor")
  private String descriptorClassName = "";

  @Option(names = {"--version"}, description = "CEL-Java version")
  private String version = "";

  @Option(names = {"--debug"}, description = "Prints debug output")
  private boolean debug = false;

  @Override
  public Integer call() throws Exception {
    String targetDescriptorProtoPath = extractProtoPath(targetDescriptorPath);
    print("Target descriptor proto path: " + targetDescriptorProtoPath);

    FileDescriptor targetFileDescriptor = null;
    ImmutableSet<FileDescriptor> transitiveFileDescriptors = CelDescriptorUtil.getFileDescriptorsFromFileDescriptorSet(load(transitiveDescriptorSetPath));
    for (FileDescriptor fd : transitiveFileDescriptors) {
      if (fd.getFullName().equals(targetDescriptorProtoPath)) {
        print("Transitive Descriptor Path: " + fd.getFullName());
        targetFileDescriptor = fd;
        break;
      }
    }

    if (targetFileDescriptor == null) {
      throw new IllegalArgumentException(String.format("Target descriptor %s not found from transitive set of descriptors!", targetDescriptorProtoPath));
    }

    codegenCelLiteDescriptor(targetFileDescriptor);

    return 0;
  }

  private void codegenCelLiteDescriptor(FileDescriptor targetFileDescriptor)
      throws Exception {
    String javaPackageName = ProtoJavaQualifiedNames.getJavaPackageName(targetFileDescriptor);
    ImmutableList.Builder<MessageInfo> messageInfoListBuilder = ImmutableList.builder();

    for (Descriptor descriptor : targetFileDescriptor.getMessageTypes()) {
      ImmutableMap.Builder<String, FieldInfo> fieldMap = ImmutableMap.builder();
      for (FieldDescriptor fieldDescriptor : descriptor.getFields()) {
        String methodSuffixName = CaseFormat.LOWER_UNDERSCORE.to(CaseFormat.UPPER_CAMEL, fieldDescriptor.getName());

        String javaType = fieldDescriptor.getJavaType().toString();
        String fieldJavaClassName = "";
        switch (javaType) {
          case "ENUM":
            fieldJavaClassName = ProtoJavaQualifiedNames.getFullyQualifiedJavaClassName(fieldDescriptor.getEnumType());
            break;
          case "MESSAGE":
            fieldJavaClassName = ProtoJavaQualifiedNames.getFullyQualifiedJavaClassName(fieldDescriptor.getMessageType());
            break;
          default:
            break;
        }

        Type fieldType;
        if (fieldDescriptor.isMapField()) {
          fieldType = Type.MAP;
        } else if (fieldDescriptor.isRepeated()) {
          fieldType = Type.LIST;
        } else {
          fieldType = Type.SCALAR;
        }

        fieldMap.put(fieldDescriptor.getName(), new FieldInfo(fieldDescriptor.getFullName(), javaType, methodSuffixName, fieldJavaClassName, fieldType.toString()));

        print(String.format("Method suffix name in %s, for field %s: %s", descriptor.getFullName(), fieldDescriptor.getFullName(), methodSuffixName));
        print(String.format("FieldType: %s", fieldType));
        if (!fieldJavaClassName.isEmpty()) {
          print(String.format("Java class name for field %s: %s", fieldDescriptor.getName(), fieldJavaClassName));
        }
      }

      messageInfoListBuilder.add(
          new MessageInfo(
              descriptor.getFullName(),
              ProtoJavaQualifiedNames.getFullyQualifiedJavaClassName(descriptor),
              fieldMap.build()
          ));
    }

    JavaFileGenerator.createFile(outPath,
        JavaFileGeneratorOption.newBuilder()
            .setVersion(version)
            .setDescriptorClassName(descriptorClassName)
            .setPackageName(javaPackageName)
            .setMessageInfoList(messageInfoListBuilder.build())
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
      // TODO Extensions?
      return FileDescriptorSet.parseFrom(descriptorBytes, ExtensionRegistry.getEmptyRegistry());
    } catch (IOException e) {
      throw new RuntimeException("Failed to load FileDescriptorSet from path: " + descriptorSetPath);
    }
  }

  private void printAllFlags(CommandLine cmd) {
    print("Flag values:");
    print("-------------------------------------------------------------");
    for (OptionSpec option : cmd.getCommandSpec().options()) {
      print(option.longestName() + ": " + option.getValue().toString());
    }
    print("-------------------------------------------------------------");
  }

  private void print(String message) {
    if (debug) {
      System.out.println(Ansi.ON.string("@|cyan [CelLiteDescriptorGenerator] |@" + message));
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


  private CelLiteDescriptorGenerator() {}
}