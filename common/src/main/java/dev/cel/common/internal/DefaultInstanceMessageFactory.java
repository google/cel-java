// Copyright 2022 Google LLC
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

package dev.cel.common.internal;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.CaseFormat;
import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import com.google.common.io.Files;
import com.google.protobuf.DescriptorProtos.FileOptions;
import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.EnumDescriptor;
import com.google.protobuf.Descriptors.FileDescriptor;
import com.google.protobuf.Descriptors.ServiceDescriptor;
import com.google.protobuf.Message;
import dev.cel.common.annotations.Internal;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayDeque;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Singleton factory for creating default messages from a protobuf descriptor.
 *
 * <p>CEL Library Internals. Do Not Use.
 */
@Internal
final class DefaultInstanceMessageFactory {

  // Controls how many times we should recursively inspect a nested message for building fully
  // qualified java class name before aborting.
  public static final int SAFE_RECURSE_LIMIT = 50;

  private static final DefaultInstanceMessageFactory instance = new DefaultInstanceMessageFactory();

  private final Map<String, LazyGeneratedMessageDefaultInstance> messageByDescriptorName =
      new ConcurrentHashMap<>();

  /** Gets a single instance of this MessageFactory */
  public static DefaultInstanceMessageFactory getInstance() {
    return instance;
  }

  /**
   * Creates a default instance of a protobuf message given a descriptor. This is essentially the
   * same as calling FooMessage.getDefaultInstance(), except reflection is leveraged.
   *
   * @return Default instance of a type. Returns an empty optional if the descriptor used to
   *     construct the type via reflection is different to the provided descriptor or if the
   *     descriptor class isn't loaded in the binary.
   */
  public Optional<Message> getPrototype(Descriptor descriptor) {
    String descriptorName = descriptor.getFullName();
    LazyGeneratedMessageDefaultInstance lazyDefaultInstance =
        messageByDescriptorName.computeIfAbsent(
            descriptorName,
            (unused) ->
                new LazyGeneratedMessageDefaultInstance(
                    getFullyQualifiedJavaClassName(descriptor)));

    Message defaultInstance = lazyDefaultInstance.getDefaultInstance();
    if (defaultInstance == null) {
      return Optional.empty();
    }
    // Reference equality is intended. We want to make sure the descriptors are equal
    // to guarantee types to be hermetic if linked types is disabled.
    if (defaultInstance.getDescriptorForType() != descriptor) {
      return Optional.empty();
    }
    return Optional.of(defaultInstance);
  }

  /**
   * Retrieves the full Java class name from the given descriptor
   *
   * @return fully qualified class name.
   *     <p>Example 1: com.google.api.expr.Value
   *     <p>Example 2: com.google.rpc.context.AttributeContext$Resource (Nested classes)
   *     <p>Example 3: com.google.api.expr.cel.internal.testdata$SingleFileProto$SingleFile$Path
   *     (Nested class with java multiple files disabled)
   */
  private String getFullyQualifiedJavaClassName(Descriptor descriptor) {
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
  private String getJavaPackageName(Descriptor descriptor) {
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
  private String getJavaOuterClassName(Descriptor descriptor) {
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

  private boolean hasConflictingClassName(FileDescriptor file, String name) {
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

  /** A placeholder to lazily load the generated messages' defaultInstances. */
  private static final class LazyGeneratedMessageDefaultInstance {
    private final String fullClassName;
    private volatile Message defaultInstance = null;
    private volatile boolean loaded = false;

    public LazyGeneratedMessageDefaultInstance(String fullClassName) {
      this.fullClassName = fullClassName;
    }

    public Message getDefaultInstance() {
      if (!loaded) {
        synchronized (this) {
          if (!loaded) {
            loadDefaultInstance();
            loaded = true;
          }
        }
      }
      return defaultInstance;
    }

    private void loadDefaultInstance() {
      try {
        defaultInstance =
            (Message) Class.forName(fullClassName).getMethod("getDefaultInstance").invoke(null);
      } catch (IllegalAccessException | InvocationTargetException e) {
        throw new LinkageError(
            String.format("getDefaultInstance for class: %s failed.", fullClassName), e);
      } catch (NoSuchMethodException e) {
        throw new LinkageError(
            String.format("getDefaultInstance method does not exist in class: %s.", fullClassName),
            e);
      } catch (ClassNotFoundException e) {
        // The class may not exist in some instances (Ex: evaluating a checked expression from a
        // cached source).
      }
    }
  }

  /** Clears the descriptor map. This should not be used outside testing. */
  @VisibleForTesting
  void resetDescriptorMapForTesting() {
    messageByDescriptorName.clear();
  }

  private DefaultInstanceMessageFactory() {}
}
