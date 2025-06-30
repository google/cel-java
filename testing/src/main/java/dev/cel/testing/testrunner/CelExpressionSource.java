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

import com.google.auto.value.AutoValue;

/**
 * CelExpressionSource is an encapsulation around cel_expr file format argument accepted in
 * cel_java_test/cel_test bzl macro. It either holds a {@link CheckedExpr} in binarypb/textproto
 * format, a serialized {@link CelPolicy} file in yaml/celpolicy format or a raw cel expression in
 * cel file format or string format.
 */
@AutoValue
public abstract class CelExpressionSource {

  /** Returns the value of the expression source. This can be a file path or a raw expression. */
  public abstract String value();

  /** Returns the type of the expression source. */
  public abstract ExpressionSourceType type();

  /**
   * Creates a {@link CelExpressionSource} from a file path. The type of the expression source is
   * inferred from the file extension.
   */
  public static CelExpressionSource fromSource(String value) {
    return new AutoValue_CelExpressionSource(value, ExpressionSourceType.fromSource(value));
  }

  /** Creates a {@link CelExpressionSource} from a raw CEL expression string. */
  public static CelExpressionSource fromRawExpr(String value) {
    return new AutoValue_CelExpressionSource(value, ExpressionSourceType.RAW_EXPR);
  }

  /**
   * ExpressionSourceType is an enumeration of the supported expression file types.
   *
   * <p>This enumeration is used to determine the type of the expression file based on the file
   * extension.
   */
  public enum ExpressionSourceType {
    BINARYPB,
    TEXTPROTO,
    POLICY,
    CEL,
    RAW_EXPR;

    private static ExpressionSourceType fromSource(String filePath) {
      if (filePath.endsWith(".binarypb")) {
        return BINARYPB;
      }
      if (filePath.endsWith(".textproto")) {
        return TEXTPROTO;
      }
      if (filePath.endsWith(".yaml") || filePath.endsWith(".celpolicy")) {
        return POLICY;
      }
      if (filePath.endsWith(".cel")) {
        return CEL;
      }
      throw new IllegalArgumentException("Unsupported expression file type: " + filePath);
    }
  }
}
