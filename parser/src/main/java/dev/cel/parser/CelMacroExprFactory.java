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

package dev.cel.parser;

import com.google.errorprone.annotations.FormatMethod;
import com.google.errorprone.annotations.FormatString;
import dev.cel.common.CelIssue;
import dev.cel.common.CelSourceLocation;
import dev.cel.common.ast.CelExpr;
import dev.cel.common.ast.CelExprFactory;

/**
 * Assists with the expansion of {@link CelMacro} in a manner which is consistent with the source
 * position and expression ID generation code leveraged by both the parser and type-checker.
 */
public abstract class CelMacroExprFactory extends CelExprFactory {

  // Package-private default constructor to prevent extensions outside of the codebase.
  CelMacroExprFactory() {}

  /**
   * Creates a {@link CelIssue} and reports it, returning a sentinel {@link CelExpr} that indicates
   * an error.
   */
  @FormatMethod
  public final CelExpr reportError(@FormatString String format, Object... args) {
    return reportError(
        CelIssue.formatError(currentSourceLocationForMacro(), String.format(format, args)));
  }

  /**
   * Creates a {@link CelIssue} and reports it, returning a sentinel {@link CelExpr} that indicates
   * an error.
   */
  public final CelExpr reportError(String message) {
    return reportError(CelIssue.formatError(currentSourceLocationForMacro(), message));
  }

  /** Reports a {@link CelIssue} and returns a sentinel {@link CelExpr} that indicates an error. */
  public abstract CelExpr reportError(CelIssue error);

  /** Returns the default accumulator variable name used by macros implementing comprehensions. */
  public abstract String getAccumulatorVarName();

  /** Retrieves the source location for the given {@link CelExpr} ID. */
  public final CelSourceLocation getSourceLocation(CelExpr expr) {
    return getSourceLocation(expr.id());
  }

  /** Duplicates {@link CelExpr} with a brand new set of identifiers. */
  public final CelExpr copy(CelExpr expr) {
    CelExpr.Builder builder = CelExpr.newBuilder().setId(copyExprId(expr.id()));
    switch (expr.exprKind().getKind()) {
      case CONSTANT:
        builder.setConstant(expr.constant());
        break;
      case IDENT:
        builder.setIdent(expr.ident());
        break;
      case SELECT:
        builder.setSelect(
            CelExpr.CelSelect.newBuilder()
                .setOperand(copy(expr.select().operand()))
                .setField(expr.select().field())
                .setTestOnly(expr.select().testOnly())
                .build());
        break;
      case CALL:
        {
          CelExpr.CelCall.Builder callBuilder =
              CelExpr.CelCall.newBuilder().setFunction(expr.call().function());
          expr.call().target().ifPresent(target -> callBuilder.setTarget(copy(target)));
          for (CelExpr arg : expr.call().args()) {
            callBuilder.addArgs(copy(arg));
          }
          builder.setCall(callBuilder.build());
        }
        break;
      case LIST:
        {
          CelExpr.CelList.Builder listBuilder =
              CelExpr.CelList.newBuilder().addOptionalIndices(expr.list().optionalIndices());
          for (CelExpr element : expr.list().elements()) {
            listBuilder.addElements(copy(element));
          }
          builder.setList(listBuilder.build());
        }
        break;
      case STRUCT:
        {
          CelExpr.CelStruct.Builder structBuilder =
              CelExpr.CelStruct.newBuilder().setMessageName(expr.struct().messageName());
          for (CelExpr.CelStruct.Entry entry : expr.struct().entries()) {
            structBuilder.addEntries(
                CelExpr.CelStruct.Entry.newBuilder()
                    .setId(copyExprId(entry.id()))
                    .setFieldKey(entry.fieldKey())
                    .setValue(copy(entry.value()))
                    .setOptionalEntry(entry.optionalEntry())
                    .build());
          }
          builder.setStruct(structBuilder.build());
        }
        break;
      case MAP:
        {
          CelExpr.CelMap.Builder mapBuilder = CelExpr.CelMap.newBuilder();
          for (CelExpr.CelMap.Entry entry : expr.map().entries()) {
            mapBuilder.addEntries(
                CelExpr.CelMap.Entry.newBuilder()
                    .setId(copyExprId(entry.id()))
                    .setKey(copy(entry.key()))
                    .setValue(copy(entry.value()))
                    .setOptionalEntry(entry.optionalEntry())
                    .build());
          }
          builder.setMap(mapBuilder.build());
        }
        break;
      case COMPREHENSION:
        builder.setComprehension(
            CelExpr.CelComprehension.newBuilder()
                .setIterVar(expr.comprehension().iterVar())
                .setIterRange(copy(expr.comprehension().iterRange()))
                .setAccuVar(expr.comprehension().accuVar())
                .setAccuInit(copy(expr.comprehension().accuInit()))
                .setLoopCondition(copy(expr.comprehension().loopCondition()))
                .setLoopStep(copy(expr.comprehension().loopStep()))
                .setResult(copy(expr.comprehension().result()))
                .build());
        break;
      case NOT_SET:
        break;
    }
    return builder.build();
  }

  /**
   * Returns the next unique expression ID which is associated with the same metadata (i.e. source
   * location, types, references, etc.) as `id`.
   */
  protected abstract long copyExprId(long id);

  /** Retrieves the source location for the given {@link CelExpr} ID. */
  protected abstract CelSourceLocation getSourceLocation(long exprId);

  /** Returns the current (last known) source location. This should only be used for macros. */
  protected abstract CelSourceLocation currentSourceLocationForMacro();
}
