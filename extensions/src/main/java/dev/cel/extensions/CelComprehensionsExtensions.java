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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import dev.cel.common.CelIssue;
import dev.cel.common.ast.CelExpr;
import dev.cel.compiler.CelCompilerLibrary;
import dev.cel.parser.CelMacro;
import dev.cel.parser.CelMacroExprFactory;
import dev.cel.parser.CelParserBuilder;
import dev.cel.parser.Operator;
import java.util.Optional;

/** Internal implementation of CEL comprehensions extensions. */
public final class CelComprehensionsExtensions implements CelCompilerLibrary {
  // TODO: Implement CelExtensionLibrary.FeatureSet interface.
  public ImmutableSet<CelMacro> macros() {
    return ImmutableSet.of(
        CelMacro.newReceiverMacro(
            Operator.ALL.getFunction(), 3, CelComprehensionsExtensions::expandAllMacro));
  }

  @Override
  public void setParserOptions(CelParserBuilder parserBuilder) {
    parserBuilder.addMacros(macros());
  }

  private static Optional<CelExpr> expandAllMacro(
      CelMacroExprFactory exprFactory, CelExpr target, ImmutableList<CelExpr> arguments) {
    checkNotNull(exprFactory);
    checkNotNull(target);
    checkArgument(arguments.size() == 3);
    CelExpr arg0 = checkNotNull(arguments.get(0));
    if (arg0.exprKind().getKind() != CelExpr.ExprKind.Kind.IDENT) {
      return Optional.of(reportArgumentError(exprFactory, arg0));
    }
    CelExpr arg1 = checkNotNull(arguments.get(1));
    if (arg1.exprKind().getKind() != CelExpr.ExprKind.Kind.IDENT) {
      return Optional.of(reportArgumentError(exprFactory, arg1));
    }
    CelExpr arg2 = checkNotNull(arguments.get(2));
    CelExpr accuInit = exprFactory.newBoolLiteral(true);
    CelExpr condition =
        exprFactory.newGlobalCall(
            Operator.NOT_STRICTLY_FALSE.getFunction(),
            exprFactory.newIdentifier(exprFactory.getAccumulatorVarName()));
    CelExpr step =
        exprFactory.newGlobalCall(
            Operator.LOGICAL_AND.getFunction(),
            exprFactory.newIdentifier(exprFactory.getAccumulatorVarName()),
            arg2);
    CelExpr result = exprFactory.newIdentifier(exprFactory.getAccumulatorVarName());
    return Optional.of(
        exprFactory.fold(
            arg0.ident().name(),
            arg1.ident().name(),
            target,
            exprFactory.getAccumulatorVarName(),
            accuInit,
            condition,
            step,
            result));
  }

  private static CelExpr reportArgumentError(CelMacroExprFactory exprFactory, CelExpr argument) {
    return exprFactory.reportError(
        CelIssue.formatError(
            exprFactory.getSourceLocation(argument), "The argument must be a simple name"));
  }
}
