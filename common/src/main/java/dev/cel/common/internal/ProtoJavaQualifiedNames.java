package dev.cel.common.internal;

import com.google.common.base.CaseFormat;
import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import com.google.common.io.Files;
import com.google.protobuf.DescriptorProtos.FileOptions;
import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.EnumDescriptor;
import com.google.protobuf.Descriptors.FileDescriptor;
import com.google.protobuf.Descriptors.ServiceDescriptor;
import java.util.ArrayDeque;

public final class ProtoJavaQualifiedNames {
  // Controls how many times we should recursively inspect a nested message for building fully
  // qualified java class name before aborting.
  private static final int SAFE_RECURSE_LIMIT = 50;

  /**
   * Retrieves the full Java class name from the given descriptor
   *
   * @return fully qualified class name.
   *     <p>Example 1: dev.cel.expr.Value
   *     <p>Example 2: com.google.rpc.context.AttributeContext$Resource (Nested classes)
   *     <p>Example 3: com.google.api.expr.cel.internal.testdata$SingleFileProto$SingleFile$Path
   *     (Nested class with java multiple files disabled)
   */
  public static String getFullyQualifiedJavaClassName(Descriptor descriptor) {
    StringBuilder fullClassName = new StringBuilder();

    fullClassName.append(getJavaPackageName(descriptor));

    String javaOuterClass = getJavaOuterClassName(descriptor);
    if (!Strings.isNullOrEmpty(javaOuterClass)) {
      fullClassName.append(javaOuterClass).append("$");
    }

    // Recursively build the target class name in case if the message is nested.
    ArrayDeque<String> classNames = new ArrayDeque<>();
    Descriptor d = descriptor;

    int recurseCount = 0;
    while (d != null) {
      classNames.push(d.getName());
      d = d.getContainingType();
      recurseCount++;
      if (recurseCount >= SAFE_RECURSE_LIMIT) {
        throw new IllegalStateException(
            String.format(
                "Recursion limit of %d hit while inspecting descriptor: %s",
                SAFE_RECURSE_LIMIT, descriptor.getFullName()));
      }
    }

    Joiner.on("$").appendTo(fullClassName, classNames);

    return fullClassName.toString();
  }

  /**
   * Gets the java package name from the descriptor. See
   * https://developers.google.com/protocol-buffers/docs/reference/java-generated#package for rules
   * on package name generation
   */
  public static String getJavaPackageName(Descriptor descriptor) {
    FileOptions options = descriptor.getFile().getOptions();
    StringBuilder javaPackageName = new StringBuilder();
    if (options.hasJavaPackage()) {
      javaPackageName.append(descriptor.getFile().getOptions().getJavaPackage()).append(".");
    } else {
      javaPackageName
          // CEL-Internal-1
          .append(descriptor.getFile().getPackage())
          .append(".");
    }

    // CEL-Internal-2

    return javaPackageName.toString();
  }

  /**
   * Gets a wrapping outer class name from the descriptor. The outer class name differs depending on
   * the proto options set. See
   * https://developers.google.com/protocol-buffers/docs/reference/java-generated#invocation
   */
  private static String getJavaOuterClassName(Descriptor descriptor) {
    FileOptions options = descriptor.getFile().getOptions();

    if (options.getJavaMultipleFiles()) {
      // If java_multiple_files is enabled, protoc does not generate a wrapper outer class
      return "";
    }

    if (options.hasJavaOuterClassname()) {
      return options.getJavaOuterClassname();
    } else {
      // If an outer class name is not explicitly set, the name is converted into
      // Pascal case based on the snake cased file name
      // Ex: messages_proto.proto becomes MessagesProto
      String protoFileNameWithoutExtension =
          Files.getNameWithoutExtension(descriptor.getFile().getFullName());
      String outerClassName =
          CaseFormat.LOWER_UNDERSCORE.to(CaseFormat.UPPER_CAMEL, protoFileNameWithoutExtension);
      if (hasConflictingClassName(descriptor.getFile(), outerClassName)) {
        outerClassName += "OuterClass";
      }
      return outerClassName;
    }
  }

  private static boolean hasConflictingClassName(FileDescriptor file, String name) {
    for (EnumDescriptor enumDesc : file.getEnumTypes()) {
      if (name.equals(enumDesc.getName())) {
        return true;
      }
    }
    for (ServiceDescriptor serviceDesc : file.getServices()) {
      if (name.equals(serviceDesc.getName())) {
        return true;
      }
    }
    for (Descriptor messageDesc : file.getMessageTypes()) {
      if (name.equals(messageDesc.getName())) {
        return true;
      }
    }
    return false;
  }


  private ProtoJavaQualifiedNames() {}
}
