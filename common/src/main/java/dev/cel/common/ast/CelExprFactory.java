// Copyright 2023 Google LLC
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

package dev.cel.common.ast;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Strings.isNullOrEmpty;

import com.google.common.primitives.UnsignedLong;
import dev.cel.common.annotations.Internal;
import dev.cel.common.values.CelByteString;
import java.util.Arrays;

/** Factory for generating expression nodes. */
@Internal
public class CelExprFactory {
  private long exprId = 0L;

  public static CelExprFactory newInstance() {
    return new CelExprFactory();
  }

  /** Create a new constant expression. */
  public final CelExpr newConstant(CelConstant constant) {
    return CelExpr.newBuilder().setId(nextExprId()).setConstant(constant).build();
  }

  /** Creates a new constant {@link CelExpr} for a bool value. */
  public final CelExpr newBoolLiteral(boolean value) {
    return newConstant(CelConstant.ofValue(value));
  }

  /** Creates a new constant {@link CelExpr} for a bytes value. */
  public final CelExpr newBytesLiteral(String value) {
    return newBytesLiteral(CelByteString.copyFromUtf8(value));
  }

  /** Creates a new constant {@link CelExpr} for a bytes value. */
  public final CelExpr newBytesLiteral(byte[] value) {
    return newBytesLiteral(CelByteString.of(value));
  }

  /** Creates a new constant {@link CelExpr} for a bytes value. */
  public final CelExpr newBytesLiteral(CelByteString value) {
    return newConstant(CelConstant.ofValue(value));
  }

  /** Creates a new constant {@link CelExpr} for a double value. */
  public final CelExpr newDoubleLiteral(double value) {
    return newConstant(CelConstant.ofValue(value));
  }

  /** Creates a new constant {@link CelExpr} for an int value. */
  public final CelExpr newIntLiteral(long value) {
    return newConstant(CelConstant.ofValue(value));
  }

  /** Creates a new constant {@link CelExpr} for a string value. */
  public final CelExpr newStringLiteral(String value) {
    return newConstant(CelConstant.ofValue(value));
  }

  /** Creates a new constant {@link CelExpr} for a uint value. */
  public final CelExpr newUintLiteral(long value) {
    return newConstant(CelConstant.ofValue(UnsignedLong.fromLongBits(value)));
  }

  /** Creates a new list {@link CelExpr} comprised of the elements. */
  public final CelExpr newList(CelExpr... elements) {
    return newList(Arrays.asList(elements));
  }

  /** Creates a new list {@link CelExpr} comprised of the elements. */
  public final CelExpr newList(Iterable<CelExpr> elements) {
    return CelExpr.newBuilder()
        .setId(nextExprId())
        .setList(CelExpr.CelList.newBuilder().addElements(elements).build())
        .build();
  }

  /** Creates a new map {@link CelExpr} comprised of the entries. */
  public final CelExpr newMap(CelExpr.CelMap.Entry... entries) {
    return newMap(Arrays.asList(entries));
  }

  /** Creates a new map {@link CelExpr} comprised of the entries. */
  public final CelExpr newMap(Iterable<CelExpr.CelMap.Entry> entries) {
    return CelExpr.newBuilder()
        .setId(nextExprId())
        .setMap(CelExpr.CelMap.newBuilder().addEntries(entries).build())
        .build();
  }

  /** Creates a new map {@link CelExpr.CelStruct.Entry} comprised of the given key and value. */
  public final CelExpr.CelMap.Entry newMapEntry(CelExpr key, CelExpr value) {
    return CelExpr.CelMap.Entry.newBuilder()
        .setId(nextExprId())
        .setKey(key)
        .setValue(value)
        .build();
  }

  /** Creates a new message {@link CelExpr} of the given type comprised of the given fields. */
  public final CelExpr newMessage(String typeName, CelExpr.CelStruct.Entry... fields) {
    return newMessage(typeName, Arrays.asList(fields));
  }

  /** Creates a new message {@link CelExpr} of the given type comprised of the given fields. */
  public final CelExpr newMessage(String typeName, Iterable<CelExpr.CelStruct.Entry> fields) {
    checkArgument(!isNullOrEmpty(typeName));
    return CelExpr.newBuilder()
        .setId(nextExprId())
        .setStruct(
            CelExpr.CelStruct.newBuilder().setMessageName(typeName).addEntries(fields).build())
        .build();
  }

  /**
   * Creates a new message {@link CelExpr.CelStruct.Entry} comprised of the given field and value.
   */
  public final CelExpr.CelStruct.Entry newMessageField(String field, CelExpr value) {
    checkArgument(!isNullOrEmpty(field));
    return CelExpr.CelStruct.Entry.newBuilder()
        .setId(nextExprId())
        .setFieldKey(field)
        .setValue(value)
        .build();
  }

  /** Fold creates a fold for one variable comprehension instruction. */
  public final CelExpr fold(
      String iterVar,
      CelExpr iterRange,
      String accuVar,
      CelExpr accuInit,
      CelExpr condition,
      CelExpr step,
      CelExpr result) {
    checkArgument(!isNullOrEmpty(iterVar));
    checkArgument(!isNullOrEmpty(accuVar));
    return CelExpr.newBuilder()
        .setId(nextExprId())
        .setComprehension(
            CelExpr.CelComprehension.newBuilder()
                .setIterVar(iterVar)
                .setIterRange(iterRange)
                .setAccuVar(accuVar)
                .setAccuInit(accuInit)
                .setLoopCondition(condition)
                .setLoopStep(step)
                .setResult(result)
                .build())
        .build();
  }

  /** Fold creates a fold for two variable comprehension instruction. */
  public final CelExpr fold(
      String iterVar,
      String iterVar2,
      CelExpr iterRange,
      String accuVar,
      CelExpr accuInit,
      CelExpr condition,
      CelExpr step,
      CelExpr result) {
    checkArgument(!isNullOrEmpty(iterVar));
    checkArgument(!isNullOrEmpty(iterVar2));
    checkArgument(!isNullOrEmpty(accuVar));
    return CelExpr.newBuilder()
        .setId(nextExprId())
        .setComprehension(
            CelExpr.CelComprehension.newBuilder()
                .setIterVar(iterVar)
                .setIterVar2(iterVar2)
                .setIterRange(iterRange)
                .setAccuVar(accuVar)
                .setAccuInit(accuInit)
                .setLoopCondition(condition)
                .setLoopStep(step)
                .setResult(result)
                .build())
        .build();
  }

  /** Creates an identifier {@link CelExpr} for the given name. */
  public final CelExpr newIdentifier(String name) {
    checkArgument(!isNullOrEmpty(name));
    return CelExpr.newBuilder()
        .setId(nextExprId())
        .setIdent(CelExpr.CelIdent.newBuilder().setName(name).build())
        .build();
  }

  /** Creates a global (free) function call {@link CelExpr} for the given function and arguments. */
  public final CelExpr newGlobalCall(String function, CelExpr... arguments) {
    return newGlobalCall(function, Arrays.asList(arguments));
  }

  /** Creates a global (free) function call {@link CelExpr} for the given function and arguments. */
  public final CelExpr newGlobalCall(String function, Iterable<CelExpr> arguments) {
    checkArgument(!isNullOrEmpty(function));
    return CelExpr.newBuilder()
        .setId(nextExprId())
        .setCall(CelExpr.CelCall.newBuilder().setFunction(function).addArgs(arguments).build())
        .build();
  }

  /**
   * Creates a receiver-style function call {@link CelExpr} for the given function, target, and
   * arguments.
   */
  public final CelExpr newReceiverCall(String function, CelExpr target, CelExpr... arguments) {
    return newReceiverCall(function, target, Arrays.asList(arguments));
  }

  /**
   * Creates a receiver-style function call {@link CelExpr} for the given function, target, and
   * arguments.
   */
  public final CelExpr newReceiverCall(
      String function, CelExpr target, Iterable<CelExpr> arguments) {
    checkArgument(!isNullOrEmpty(function));
    return CelExpr.newBuilder()
        .setId(nextExprId())
        .setCall(
            CelExpr.CelCall.newBuilder()
                .setFunction(function)
                .setTarget(target)
                .addArgs(arguments)
                .build())
        .build();
  }

  /**
   * Creates a field traversal or field presence test {@link CelExpr} for the given operand and
   * field.
   */
  public final CelExpr newSelect(CelExpr operand, String field, boolean testOnly) {
    checkArgument(!isNullOrEmpty(field));
    return CelExpr.newBuilder()
        .setId(nextExprId())
        .setSelect(
            CelExpr.CelSelect.newBuilder()
                .setOperand(operand)
                .setField(field)
                .setTestOnly(testOnly)
                .build())
        .build();
  }

  /** Returns the next unique expression ID. */
  protected long nextExprId() {
    return ++exprId;
  }

  /** Attempts to decrement the next expr ID if possible. */
  protected void maybeDeleteId(long id) {
    if (id == exprId - 1) {
      exprId--;
    }
  }

  protected CelExprFactory() {}
}
