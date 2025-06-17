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
package dev.cel.testing.utils;

import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableList;
import com.google.protobuf.Descriptors.Descriptor;
import io.github.classgraph.ClassGraph;
import io.github.classgraph.ClassInfo;
import io.github.classgraph.ClassInfoList;
import io.github.classgraph.ScanResult;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.logging.Logger;

/** Utility class for loading classes using {@link ClassGraph}. */
public final class ClassLoaderUtils {

  private static final Logger logger = Logger.getLogger(ClassLoaderUtils.class.getName());

  // Using `enableAllInfo()` to scan all class files upfront. This avoids repeated parsing
  // of class files by individual methods, improving efficiency.
  private static final Supplier<ScanResult> CLASS_SCAN_RESULT =
      Suppliers.memoize(() -> new ClassGraph().enableAllInfo().scan());

  /**
   * Loads all descriptor type classes from the JVM.
   *
   * @return A list of {@link Descriptor} objects representing the descriptors loaded from the JVM.
   * @throws IOException If there is an error during the loading process.
   */
  public static ImmutableList<Descriptor> loadDescriptors() throws IOException {
    ClassInfoList classInfoList = CLASS_SCAN_RESULT.get().getAllStandardClasses();
    ImmutableList.Builder<Descriptor> compileTimeLoadedDescriptors = ImmutableList.builder();

    for (ClassInfo classInfo : classInfoList) {
      try {
        Class<?> classInfoClass = classInfo.loadClass();
        Descriptor descriptor = (Descriptor) classInfoClass.getMethod("getDescriptor").invoke(null);
        compileTimeLoadedDescriptors.add(descriptor);
      } catch (InvocationTargetException e) {
        logger.severe(
            "Failed to load descriptor: " + classInfo.getName() + " with error: " + e);
      } catch (Exception e) {
        // Ignore classes that do not have a getDescriptor method.
      }
    }
    return compileTimeLoadedDescriptors.build();
  }

  /**
   * Loads all subclasses of the given class from the JVM.
   *
   * @param clazz The class to load subclasses for.
   * @return A list of {@link ClassInfo} objects representing the subclasses.
   */
  public static ClassInfoList loadSubclasses(Class<?> clazz) {
    return CLASS_SCAN_RESULT.get().getSubclasses(clazz.getName());
  }

  /**
   * Loads all classes with the given method annotation from the JVM.
   *
   * @param annotationName The name of the annotation to load classes with.
   * @return A list of {@link ClassInfo} objects representing the classes with the annotation.
   */
  public static ClassInfoList loadClassesWithMethodAnnotation(String annotationName) {
    return CLASS_SCAN_RESULT.get().getClassesWithMethodAnnotation(annotationName);
  }

  private ClassLoaderUtils() {}
}
