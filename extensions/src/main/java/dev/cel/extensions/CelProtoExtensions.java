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

package dev.cel.extensions;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.errorprone.annotations.Immutable;
import dev.cel.common.CelIssue;
import dev.cel.common.ast.CelExpr;
import dev.cel.common.internal.Constants;
import dev.cel.compiler.CelCompilerLibrary;
import dev.cel.parser.CelMacro;
import dev.cel.parser.CelMacroExprFactory;
import dev.cel.parser.CelParserBuilder;
import java.util.Optional;

/** Internal implementation of CEL proto extensions. */
@Immutable
public final class CelProtoExtensions
    implements CelCompilerLibrary, CelExtensionLibrary.FeatureSet {

  private static final String PROTO_NAMESPACE = "proto";
  private static final CelExpr ERROR = CelExpr.newBuilder().setConstant(Constants.ERROR).build();

  private static final CelExtensionLibrary<CelProtoExtensions> LIBRARY =
      new CelExtensionLibrary<CelProtoExtensions>() {
        private final CelProtoExtensions version0 = new CelProtoExtensions();

        @Override
        public String name() {
          return "protos";
        }

        @Override
        public ImmutableSet<CelProtoExtensions> versions() {
          return ImmutableSet.of(version0);
        }
      };

  static CelExtensionLibrary<CelProtoExtensions> library() {
    return LIBRARY;
  }

  @Override
  public int version() {
    return 0;
  }

  @Override
  public ImmutableSet<CelMacro> macros() {
    return ImmutableSet.of(
        CelMacro.newReceiverMacro("hasExt", 2, CelProtoExtensions::expandHasProtoExt),
        CelMacro.newReceiverMacro("getExt", 2, CelProtoExtensions::expandGetProtoExt));
  }

  @Override
  public void setParserOptions(CelParserBuilder parserBuilder) {
    parserBuilder.addMacros(macros());
  }

  private static Optional<CelExpr> expandHasProtoExt(
      CelMacroExprFactory exprFactory, CelExpr target, ImmutableList<CelExpr> arguments) {
    return expandProtoExt(exprFactory, target, arguments, true);
  }

  private static Optional<CelExpr> expandGetProtoExt(
      CelMacroExprFactory exprFactory, CelExpr target, ImmutableList<CelExpr> arguments) {
    return expandProtoExt(exprFactory, target, arguments, false);
  }

  private static Optional<CelExpr> expandProtoExt(
      CelMacroExprFactory exprFactory,
      CelExpr target,
      ImmutableList<CelExpr> arguments,
      boolean testOnly) {
    if (!isTargetInNamespace(target)) {
      // Return empty to indicate that we're not interested in expanding this macro, and
      // that the parser should default to a function call on the receiver.
      return Optional.empty();
    }

    checkArgument(arguments.size() == 2);
    CelExpr arg1 = checkNotNull(arguments.get(0));
    CelExpr arg2 = checkNotNull(arguments.get(1));

    StringBuilder extensionFieldNameBuilder = new StringBuilder();
    CelExpr result = getExtensionFieldName(arg2, extensionFieldNameBuilder);
    if (result.equals(ERROR)) {
      return Optional.of(
          exprFactory.reportError(
              CelIssue.formatError(
                  exprFactory.getSourceLocation(arg2), "invalid extension field")));
    }
    return Optional.of(exprFactory.newSelect(arg1, extensionFieldNameBuilder.toString(), testOnly));
  }

  /**
   * Populates the fully qualified extension field name into the string builder.
   *
   * @param target Target expression.
   * @param sb String builder to populate the field name in.
   * @return {@link #ERROR} expression if the target expr AST is malformed.
   */
  private static CelExpr getExtensionFieldName(CelExpr target, StringBuilder sb) {
    if (target.exprKind().getKind() != CelExpr.ExprKind.Kind.SELECT) {
      return ERROR;
    }

    return buildExtensionFieldName(target, sb);
  }

  private static CelExpr buildExtensionFieldName(CelExpr target, StringBuilder sb) {
    switch (target.exprKind().getKind()) {
      case IDENT:
        sb.append(target.ident().name());
        return target;
      case SELECT:
        CelExpr.CelSelect select = target.select();
        if (select.testOnly()) {
          return ERROR;
        }
        CelExpr operand = buildExtensionFieldName(select.operand(), sb);
        if (operand.exprKind().getKind() != CelExpr.ExprKind.Kind.IDENT) {
          return ERROR;
        }
        sb.append(".").append(select.field());
        return operand;
      default:
        return ERROR;
    }
  }

  private static boolean isTargetInNamespace(CelExpr target) {
    return target.exprKind().getKind().equals(CelExpr.ExprKind.Kind.IDENT)
        && target.ident().name().equals(PROTO_NAMESPACE);
  }

  CelProtoExtensions() {}
}
