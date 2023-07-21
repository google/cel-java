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
import dev.cel.expr.Decl.FunctionDecl;
import dev.cel.expr.Decl.FunctionDecl.Overload;
import dev.cel.compiler.CelCompilerBuilder;
import java.util.Arrays;

/** Wrapper for proto-based function declarations. */
class TestProtoFunctionDeclWrapper extends TestDecl {
  private final Decl functionDecl;

  TestProtoFunctionDeclWrapper(String functionName, Overload... overloads) {
    this.functionDecl =
        Decl.newBuilder()
            .setName(functionName)
            .setFunction(FunctionDecl.newBuilder().addAllOverloads(Arrays.asList(overloads)))
            .build();
  }

  @Override
  void loadDeclsToCompiler(CelCompilerBuilder compiler) {
    compiler.addDeclarations(functionDecl);
  }

  @Override
  Decl getDecl() {
    return functionDecl;
  }
}
