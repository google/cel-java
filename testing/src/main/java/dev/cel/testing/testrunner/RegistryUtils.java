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
package dev.cel.testing.testrunner;

import com.google.common.collect.ImmutableList;
import com.google.common.io.Files;
import com.google.protobuf.DescriptorProtos.FileDescriptorSet;
import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.Descriptors.FileDescriptor;
import com.google.protobuf.ExtensionRegistry;
import com.google.protobuf.Message;
import com.google.protobuf.TypeRegistry;
import dev.cel.common.CelDescriptorUtil;
import dev.cel.common.CelDescriptors;
import dev.cel.common.internal.DefaultInstanceMessageFactory;
import io.github.classgraph.ClassGraph;
import io.github.classgraph.ClassInfo;
import io.github.classgraph.ClassInfoList;
import io.github.classgraph.ScanResult;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;

/** Utility class for creating registries from a file descriptor set. */
public final class RegistryUtils {


  private RegistryUtils() {}

  /** Returns the {@link FileDescriptorSet} for the given file descriptor set path. */
  public static FileDescriptorSet getFileDescriptorSet(String fileDescriptorSetPath)
      throws IOException {
    // We can pass an empty extension registry here because extensions are recovered later when
    // creating the extension registry in {@link #createExtensionRegistry}.
    return FileDescriptorSet.parseFrom(
        Files.toByteArray(new File(fileDescriptorSetPath)), ExtensionRegistry.newInstance());
  }

  /** Returns the {@link TypeRegistry} for the given file descriptor set. */
  public static TypeRegistry getTypeRegistry(Set<FileDescriptor> fileDescriptors)
      throws IOException {
    return createTypeRegistry(fileDescriptors);
  }

  /** Returns the {@link ExtensionRegistry} for the given file descriptor set. */
  public static ExtensionRegistry getExtensionRegistry(Set<FileDescriptor> fileDescriptors)
      throws IOException {
    return createExtensionRegistry(fileDescriptors);
  }

  public static Message getDefaultInstance(
      Descriptor compileTimeDescriptor, List<Descriptor> compileTimeDescriptors) {
    System.out.println("Compile time descriptor: " + compileTimeDescriptor.getFullName());
    Descriptor messageDescriptor =
        compileTimeDescriptors.stream()
            .filter(
                descriptor1 ->
                    descriptor1.getFullName().equals(compileTimeDescriptor.getFullName()))
            .findFirst()
            .orElseThrow(
                () ->
                    new NoSuchElementException(
                        "Could not find a compiled descriptor for: "
                            + compileTimeDescriptor.getFullName()));
    Message output =
        DefaultInstanceMessageFactory.getInstance()
            .getPrototype(messageDescriptor)
            .orElseThrow(
                () ->
                    new NoSuchElementException(
                        "Could not find a default message for: "
                            + messageDescriptor.getFullName()));
    // System.out.println("Default instance: " + output);
    return output;
  }

  public static FieldDescriptor getFieldDescriptorFromCompiledDescriptors(
      String fieldDescriptorName, List<Descriptor> compileTimeDescriptors) {
    return compileTimeDescriptors.stream()
        .flatMap(
            descriptor ->
                ImmutableList.<FieldDescriptor>builder()
                    .addAll(descriptor.getFields())
                    .addAll(descriptor.getExtensions())
                    .addAll(descriptor.getFile().getExtensions())
                    .build()
                    .stream())
        .filter(fieldDescriptor -> fieldDescriptor.getFullName().equals(fieldDescriptorName))
        .findFirst()
        .orElseThrow(
            () ->
                new NoSuchElementException(
                    "Could not find a compiled field descriptor for: " + fieldDescriptorName));
  }

  public static ImmutableList<Descriptor> loadDescriptorsFromJvm() {
    ScanResult scanResult = new ClassGraph().enableClassInfo().scan();
    ClassInfoList classInfoList = scanResult.getAllStandardClasses();
    ImmutableList.Builder<Descriptor> descriptors = ImmutableList.builder();
    for (ClassInfo classInfo : classInfoList) {
      try {
        Class<?> classInfoClass = classInfo.loadClass();
          Descriptor descriptor =
              (Descriptor) classInfoClass.getMethod("getDescriptor").invoke(null);
        descriptors.add(descriptor);
        // System.out.println("Loaded descriptor: " + descriptor.getFullName());
      } catch (Exception e) {
        // Ignore classes that do not have a getDescriptor method.
      }
    }
    return descriptors.build();
  }

  private static TypeRegistry createTypeRegistry(Set<FileDescriptor> fileDescriptors) {
    System.out.println(
        "Creating type registry from " + fileDescriptors.size() + " file descriptors.");
    return TypeRegistry.newBuilder().add(loadDescriptorsFromJvm()).build();
  }

  private static ExtensionRegistry createExtensionRegistry(Set<FileDescriptor> fileDescriptors) {
    ExtensionRegistry extensionRegistry = ExtensionRegistry.newInstance();
    CelDescriptors allDescriptors =
        CelDescriptorUtil.getAllDescriptorsFromFileDescriptor(fileDescriptors);
    ImmutableList<Descriptor> compileTimeDescriptors = loadDescriptorsFromJvm();

    for (Descriptor descriptor : compileTimeDescriptors) {
      System.out.println("Compile time descriptor: " + descriptor.getFullName() + " " + descriptor);
    }

    allDescriptors
        .extensionDescriptors()
        .forEach(
            (descriptorName, descriptor) -> {
              FieldDescriptor fieldDescriptor =
                  getFieldDescriptorFromCompiledDescriptors(
                      descriptor.getFullName(), compileTimeDescriptors);
              if (descriptor.getType().equals(FieldDescriptor.Type.MESSAGE)) {
                Message defaultInstance =
                    getDefaultInstance(fieldDescriptor.getMessageType(), compileTimeDescriptors);
                extensionRegistry.add(fieldDescriptor, defaultInstance);
              } else {
                extensionRegistry.add(fieldDescriptor);
              }
            });

    extensionRegistry
        .getAllImmutableExtensionsByExtendedType("cel.expr.conformance.proto2.TestAllTypes")
        .forEach(
            (ExtensionRegistry.ExtensionInfo extensionInfo) -> {
              System.out.println(
                  "Extension descriptor: " + extensionInfo.descriptor.getContainingType());
            });

    return extensionRegistry;
  }
}
