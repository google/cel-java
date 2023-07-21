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

package dev.cel.testing;

import dev.cel.expr.Decl;
import dev.cel.expr.Decl.FunctionDecl.Overload;
import com.google.common.collect.Iterables;
import dev.cel.common.CelFunctionDecl;
import dev.cel.common.CelOverloadDecl;
import dev.cel.compiler.CelCompilerBuilder;
import java.util.Arrays;

/** Wrapper for CEL native type based function declarations {@link CelFunctionDecl} */
class TestCelFunctionDeclWrapper extends TestDecl {
  private final CelFunctionDecl functionDecl;

  TestCelFunctionDeclWrapper(String functionName, Overload... overloads) {
    this.functionDecl =
        CelFunctionDecl.newFunctionDeclaration(
            functionName,
            Iterables.transform(Arrays.asList(overloads), CelOverloadDecl::overloadToCelOverload));
  }

  @Override
  void loadDeclsToCompiler(CelCompilerBuilder compiler) {
    compiler.addFunctionDeclarations(functionDecl);
  }

  @Override
  Decl getDecl() {
    return CelFunctionDecl.celFunctionDeclToDecl(functionDecl);
  }
}
