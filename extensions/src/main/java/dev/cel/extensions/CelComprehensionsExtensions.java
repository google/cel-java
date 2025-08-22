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

  private static final String TRANSFORM_LIST = "transformList";

  public ImmutableSet<CelMacro> macros() {
    return ImmutableSet.of(
        CelMacro.newReceiverMacro(
            Operator.ALL.getFunction(), 3, CelComprehensionsExtensions::expandAllMacro),
        CelMacro.newReceiverMacro(
            Operator.EXISTS.getFunction(), 3, CelComprehensionsExtensions::expandExistsMacro),
        CelMacro.newReceiverMacro(
            Operator.EXISTS_ONE.getFunction(),
            3,
            CelComprehensionsExtensions::expandExistsOneMacro),
        CelMacro.newReceiverMacro(
            TRANSFORM_LIST, 3, CelComprehensionsExtensions::transformListMacro),
        CelMacro.newReceiverMacro(
            TRANSFORM_LIST, 4, CelComprehensionsExtensions::transformListMacro));
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

  private static Optional<CelExpr> expandExistsMacro(
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
    CelExpr accuInit = exprFactory.newBoolLiteral(false);
    CelExpr condition =
        exprFactory.newGlobalCall(
            Operator.NOT_STRICTLY_FALSE.getFunction(),
            exprFactory.newGlobalCall(
                Operator.LOGICAL_NOT.getFunction(),
                exprFactory.newIdentifier(exprFactory.getAccumulatorVarName())));
    CelExpr step =
        exprFactory.newGlobalCall(
            Operator.LOGICAL_OR.getFunction(),
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

  private static Optional<CelExpr> expandExistsOneMacro(
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
    CelExpr accuInit = exprFactory.newIntLiteral(0);
    CelExpr condition = exprFactory.newBoolLiteral(true);
    CelExpr step =
        exprFactory.newGlobalCall(
            Operator.CONDITIONAL.getFunction(),
            arg2,
            exprFactory.newGlobalCall(
                Operator.ADD.getFunction(),
                exprFactory.newIdentifier(exprFactory.getAccumulatorVarName()),
                exprFactory.newIntLiteral(1)),
            exprFactory.newIdentifier(exprFactory.getAccumulatorVarName()));
    CelExpr result =
        exprFactory.newGlobalCall(
            Operator.EQUALS.getFunction(),
            exprFactory.newIdentifier(exprFactory.getAccumulatorVarName()),
            exprFactory.newIntLiteral(1));
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

  private static Optional<CelExpr> transformListMacro(
      CelMacroExprFactory exprFactory, CelExpr target, ImmutableList<CelExpr> arguments) {
    checkNotNull(exprFactory);
    checkNotNull(target);
    checkArgument(arguments.size() == 3 || arguments.size() == 4);
    CelExpr arg0 = checkNotNull(arguments.get(0));
    if (arg0.exprKind().getKind() != CelExpr.ExprKind.Kind.IDENT) {
      return Optional.of(reportArgumentError(exprFactory, arg0));
    }
    CelExpr arg1 = checkNotNull(arguments.get(1));
    if (arg1.exprKind().getKind() != CelExpr.ExprKind.Kind.IDENT) {
      return Optional.of(reportArgumentError(exprFactory, arg1));
    }
    CelExpr transform;
    CelExpr filter = null;
    if (arguments.size() == 4) {
      filter = checkNotNull(arguments.get(2));
      transform = checkNotNull(arguments.get(3));
    } else {
      transform = checkNotNull(arguments.get(2));
    }
    CelExpr accuInit = exprFactory.newList();
    CelExpr condition = exprFactory.newBoolLiteral(true);
    CelExpr step =
        exprFactory.newGlobalCall(
            Operator.ADD.getFunction(),
            exprFactory.newIdentifier(exprFactory.getAccumulatorVarName()),
            exprFactory.newList(transform));
    if (filter != null) {
      step =
          exprFactory.newGlobalCall(
              Operator.CONDITIONAL.getFunction(),
              filter,
              step,
              exprFactory.newIdentifier(exprFactory.getAccumulatorVarName()));
    }
    return Optional.of(
        exprFactory.fold(
            arg0.ident().name(),
            arg1.ident().name(),
            target,
            exprFactory.getAccumulatorVarName(),
            accuInit,
            condition,
            step,
            exprFactory.newIdentifier(exprFactory.getAccumulatorVarName())));
  }

  private static CelExpr reportArgumentError(CelMacroExprFactory exprFactory, CelExpr argument) {
    return exprFactory.reportError(
        CelIssue.formatError(
            exprFactory.getSourceLocation(argument), "The argument must be a simple name"));
  }
}
