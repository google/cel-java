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

package dev.cel.extensions;

import com.google.common.collect.ImmutableSet;
import dev.cel.common.CelFunctionDecl;
import dev.cel.parser.CelMacro;
import java.util.Comparator;

/**
 * Interface for defining CEL extension libraries.
 *
 * <p>An extension library is a collection of CEL functions, variables, and macros that can be added
 * to a CEL environment to provide additional functionality.
 */
public interface CelExtensionLibrary<T extends CelExtensionLibrary.FeatureSet> {

  /** Returns the name of the extension library. */
  String name();

  ImmutableSet<T> versions();

  default T latest() {
    return versions().stream().max(Comparator.comparing(FeatureSet::version)).get();
  }

  default T version(int version) {
    if (version == Integer.MAX_VALUE) {
      return latest();
    }

    for (T v : versions()) {
      if (v.version() == version) {
        return v;
      }
    }
    throw new IllegalArgumentException("Unsupported '" + name() + "' extension version " + version);
  }

  /**
   * Interface for defining a version of a CEL extension library.
   */
  interface FeatureSet {
    /** Returns the extension library version or -1 if unspecified. */
    int version();

    /** Returns the set of function declarations defined by this extension library. */
    ImmutableSet<CelFunctionDecl> functions();

    /** Returns the set of macros defined by this extension library. */
    ImmutableSet<CelMacro> macros();

    // TODO - Add a method for variables.
  }
}
