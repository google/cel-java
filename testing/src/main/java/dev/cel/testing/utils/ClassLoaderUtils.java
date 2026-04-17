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
import io.github.classgraph.ClassGraph;
import io.github.classgraph.ClassInfo;
import io.github.classgraph.ClassInfoList;
import io.github.classgraph.ScanResult;

/** Utility class for loading classes using {@link ClassGraph}. */
public final class ClassLoaderUtils {

  // Using `enableAllInfo()` to scan all class files upfront. This avoids repeated parsing
  // of class files by individual methods, improving efficiency.
  private static final Supplier<ScanResult> CLASS_SCAN_RESULT =
      Suppliers.memoize(() -> new ClassGraph().enableAllInfo().scan());

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
