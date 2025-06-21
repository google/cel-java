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

package dev.cel.testing.compiled;

import dev.cel.expr.CheckedExpr;
import com.google.common.io.Resources;
import com.google.protobuf.ExtensionRegistryLite;
import dev.cel.common.CelAbstractSyntaxTree;
import dev.cel.common.CelProtoAbstractSyntaxTree;
import java.io.IOException;
import java.net.URL;

/**
 * CompiledExprUtils handles reading a CheckedExpr stored in a .binarypb format from the JAR's
 * resources.
 */
public final class CompiledExprUtils {

  /**
   * Reads a CheckedExpr stored in the running JAR's resources, then returns an adapted {@link
   * CelAbstractSyntaxTree}.
   */
  public static CelAbstractSyntaxTree readCheckedExpr(String compiledCelTarget) throws IOException {
    String resourcePath = String.format("%s.binarypb", compiledCelTarget);
    URL url = Resources.getResource(CompiledExprUtils.class, resourcePath);
    byte[] checkedExprBytes = Resources.toByteArray(url);
    CheckedExpr checkedExpr =
        CheckedExpr.parseFrom(checkedExprBytes, ExtensionRegistryLite.getEmptyRegistry());
    return CelProtoAbstractSyntaxTree.fromCheckedExpr(checkedExpr).getAst();
  }

  private CompiledExprUtils() {}
}
