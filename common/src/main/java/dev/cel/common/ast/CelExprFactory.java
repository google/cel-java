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
import com.google.protobuf.ByteString;
import dev.cel.common.annotations.Internal;
import java.util.Arrays;

/** Factory for generating expression nodes. */
@Internal
public class CelExprFactory {

  private final CelExprIdGeneratorFactory.ExprIdGenerator idGenerator;

  public static CelExprFactory newInstance() {
    return new CelExprFactory();
  }

  public static CelExprFactory newInstance(
      CelExprIdGeneratorFactory.ExprIdGenerator exprIdGenerator) {
    return new CelExprFactory(exprIdGenerator);
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
  public final CelExpr newBytesLiteral(ByteString value) {
    return newConstant(CelConstant.ofValue(value));
  }

  /** Creates a new constant {@link CelExpr} for a bytes value. */
  public final CelExpr newBytesLiteral(byte[] value) {
    return newBytesLiteral(value, 0, value.length);
  }

  /** Creates a new constant {@link CelExpr} for a bytes value. */
  public final CelExpr newBytesLiteral(byte[] value, int offset, int size) {
    return newBytesLiteral(ByteString.copyFrom(value, offset, size));
  }

  /** Creates a new constant {@link CelExpr} for a bytes value. */
  public final CelExpr newBytesLiteral(String value) {
    return newBytesLiteral(ByteString.copyFromUtf8(value));
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
        .setCreateList(CelExpr.CelCreateList.newBuilder().addElements(elements).build())
        .build();
  }

  /** Creates a new map {@link CelExpr} comprised of the entries. */
  public final CelExpr newMap(CelExpr.CelCreateMap.Entry... entries) {
    return newMap(Arrays.asList(entries));
  }

  /** Creates a new map {@link CelExpr} comprised of the entries. */
  public final CelExpr newMap(Iterable<CelExpr.CelCreateMap.Entry> entries) {
    return CelExpr.newBuilder()
        .setId(nextExprId())
        .setCreateMap(CelExpr.CelCreateMap.newBuilder().addEntries(entries).build())
        .build();
  }

  /**
   * Creates a new map {@link CelExpr.CelCreateStruct.Entry} comprised of the given key and value.
   */
  public final CelExpr.CelCreateMap.Entry newMapEntry(CelExpr key, CelExpr value) {
    return CelExpr.CelCreateMap.Entry.newBuilder()
        .setId(nextExprId())
        .setKey(key)
        .setValue(value)
        .build();
  }

  /** Creates a new message {@link CelExpr} of the given type comprised of the given fields. */
  public final CelExpr newMessage(String typeName, CelExpr.CelCreateStruct.Entry... fields) {
    return newMessage(typeName, Arrays.asList(fields));
  }

  /** Creates a new message {@link CelExpr} of the given type comprised of the given fields. */
  public final CelExpr newMessage(String typeName, Iterable<CelExpr.CelCreateStruct.Entry> fields) {
    checkArgument(!isNullOrEmpty(typeName));
    return CelExpr.newBuilder()
        .setId(nextExprId())
        .setCreateStruct(
            CelExpr.CelCreateStruct.newBuilder()
                .setMessageName(typeName)
                .addEntries(fields)
                .build())
        .build();
  }

  /**
   * Creates a new message {@link CelExpr.CelCreateStruct.Entry} comprised of the given field and
   * value.
   */
  public final CelExpr.CelCreateStruct.Entry newMessageField(String field, CelExpr value) {
    checkArgument(!isNullOrEmpty(field));
    return CelExpr.CelCreateStruct.Entry.newBuilder()
        .setId(nextExprId())
        .setFieldKey(field)
        .setValue(value)
        .build();
  }

  /** Fold creates a fold comprehension instruction. */
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

  /** Fold creates a fold comprehension instruction. */
  public final CelExpr fold(
      String iterVar,
      CelExpr.Builder iterRange,
      String accuVar,
      CelExpr accuInit,
      CelExpr condition,
      CelExpr step,
      CelExpr result) {
    return fold(iterVar, iterRange.build(), accuVar, accuInit, condition, step, result);
  }

  /** Fold creates a fold comprehension instruction. */
  public final CelExpr fold(
      String iterVar,
      CelExpr iterRange,
      String accuVar,
      CelExpr.Builder accuInit,
      CelExpr condition,
      CelExpr step,
      CelExpr result) {
    return fold(iterVar, iterRange, accuVar, accuInit.build(), condition, step, result);
  }

  /** Fold creates a fold comprehension instruction. */
  public final CelExpr fold(
      String iterVar,
      CelExpr iterRange,
      String accuVar,
      CelExpr accuInit,
      CelExpr.Builder condition,
      CelExpr step,
      CelExpr result) {
    return fold(iterVar, iterRange, accuVar, accuInit, condition.build(), step, result);
  }

  /** Fold creates a fold comprehension instruction. */
  public final CelExpr fold(
      String iterVar,
      CelExpr iterRange,
      String accuVar,
      CelExpr accuInit,
      CelExpr condition,
      CelExpr.Builder step,
      CelExpr result) {
    return fold(iterVar, iterRange, accuVar, accuInit, condition, step.build(), result);
  }

  /** Fold creates a fold comprehension instruction. */
  public final CelExpr fold(
      String iterVar,
      CelExpr iterRange,
      String accuVar,
      CelExpr accuInit,
      CelExpr condition,
      CelExpr step,
      CelExpr.Builder result) {
    return fold(iterVar, iterRange, accuVar, accuInit, condition, step, result.build());
  }

  /** Fold creates a fold comprehension instruction. */
  public final CelExpr fold(
      String iterVar,
      CelExpr.Builder iterRange,
      String accuVar,
      CelExpr.Builder accuInit,
      CelExpr condition,
      CelExpr step,
      CelExpr result) {
    return fold(iterVar, iterRange.build(), accuVar, accuInit.build(), condition, step, result);
  }

  /** Fold creates a fold comprehension instruction. */
  public final CelExpr fold(
      String iterVar,
      CelExpr.Builder iterRange,
      String accuVar,
      CelExpr accuInit,
      CelExpr.Builder condition,
      CelExpr step,
      CelExpr result) {
    return fold(iterVar, iterRange.build(), accuVar, accuInit, condition.build(), step, result);
  }

  /** Fold creates a fold comprehension instruction. */
  public final CelExpr fold(
      String iterVar,
      CelExpr.Builder iterRange,
      String accuVar,
      CelExpr accuInit,
      CelExpr condition,
      CelExpr.Builder step,
      CelExpr result) {
    return fold(iterVar, iterRange.build(), accuVar, accuInit, condition, step.build(), result);
  }

  /** Fold creates a fold comprehension instruction. */
  public final CelExpr fold(
      String iterVar,
      CelExpr.Builder iterRange,
      String accuVar,
      CelExpr accuInit,
      CelExpr condition,
      CelExpr step,
      CelExpr.Builder result) {
    return fold(iterVar, iterRange.build(), accuVar, accuInit, condition, step, result.build());
  }

  /** Fold creates a fold comprehension instruction. */
  public final CelExpr fold(
      String iterVar,
      CelExpr iterRange,
      String accuVar,
      CelExpr.Builder accuInit,
      CelExpr.Builder condition,
      CelExpr step,
      CelExpr result) {
    return fold(iterVar, iterRange, accuVar, accuInit.build(), condition.build(), step, result);
  }

  /** Fold creates a fold comprehension instruction. */
  public final CelExpr fold(
      String iterVar,
      CelExpr iterRange,
      String accuVar,
      CelExpr.Builder accuInit,
      CelExpr condition,
      CelExpr.Builder step,
      CelExpr result) {
    return fold(iterVar, iterRange, accuVar, accuInit.build(), condition, step.build(), result);
  }

  /** Fold creates a fold comprehension instruction. */
  public final CelExpr fold(
      String iterVar,
      CelExpr iterRange,
      String accuVar,
      CelExpr.Builder accuInit,
      CelExpr condition,
      CelExpr step,
      CelExpr.Builder result) {
    return fold(iterVar, iterRange, accuVar, accuInit.build(), condition, step, result.build());
  }

  /** Fold creates a fold comprehension instruction. */
  public final CelExpr fold(
      String iterVar,
      CelExpr iterRange,
      String accuVar,
      CelExpr accuInit,
      CelExpr.Builder condition,
      CelExpr.Builder step,
      CelExpr result) {
    return fold(iterVar, iterRange, accuVar, accuInit, condition.build(), step.build(), result);
  }

  /** Fold creates a fold comprehension instruction. */
  public final CelExpr fold(
      String iterVar,
      CelExpr iterRange,
      String accuVar,
      CelExpr accuInit,
      CelExpr.Builder condition,
      CelExpr step,
      CelExpr.Builder result) {
    return fold(iterVar, iterRange, accuVar, accuInit, condition.build(), step, result.build());
  }

  /** Fold creates a fold comprehension instruction. */
  public final CelExpr fold(
      String iterVar,
      CelExpr iterRange,
      String accuVar,
      CelExpr accuInit,
      CelExpr condition,
      CelExpr.Builder step,
      CelExpr.Builder result) {
    return fold(iterVar, iterRange, accuVar, accuInit, condition, step.build(), result.build());
  }

  /** Fold creates a fold comprehension instruction. */
  public final CelExpr fold(
      String iterVar,
      CelExpr.Builder iterRange,
      String accuVar,
      CelExpr accuInit,
      CelExpr.Builder condition,
      CelExpr.Builder step,
      CelExpr result) {
    return fold(
        iterVar, iterRange.build(), accuVar, accuInit, condition.build(), step.build(), result);
  }

  /** Fold creates a fold comprehension instruction. */
  public final CelExpr fold(
      String iterVar,
      CelExpr.Builder iterRange,
      String accuVar,
      CelExpr accuInit,
      CelExpr.Builder condition,
      CelExpr step,
      CelExpr.Builder result) {
    return fold(
        iterVar, iterRange.build(), accuVar, accuInit, condition.build(), step, result.build());
  }

  /** Fold creates a fold comprehension instruction. */
  public final CelExpr fold(
      String iterVar,
      CelExpr.Builder iterRange,
      String accuVar,
      CelExpr.Builder accuInit,
      CelExpr.Builder condition,
      CelExpr.Builder step,
      CelExpr result) {
    return fold(
        iterVar,
        iterRange.build(),
        accuVar,
        accuInit.build(),
        condition.build(),
        step.build(),
        result);
  }

  /** Fold creates a fold comprehension instruction. */
  public final CelExpr fold(
      String iterVar,
      CelExpr.Builder iterRange,
      String accuVar,
      CelExpr.Builder accuInit,
      CelExpr condition,
      CelExpr step,
      CelExpr.Builder result) {
    return fold(
        iterVar, iterRange.build(), accuVar, accuInit.build(), condition, step, result.build());
  }

  /** Fold creates a fold comprehension instruction. */
  public final CelExpr fold(
      String iterVar,
      CelExpr.Builder iterRange,
      String accuVar,
      CelExpr.Builder accuInit,
      CelExpr condition,
      CelExpr.Builder step,
      CelExpr result) {
    return fold(
        iterVar, iterRange.build(), accuVar, accuInit.build(), condition, step.build(), result);
  }

  /** Fold creates a fold comprehension instruction. */
  public final CelExpr fold(
      String iterVar,
      CelExpr.Builder iterRange,
      String accuVar,
      CelExpr.Builder accuInit,
      CelExpr.Builder condition,
      CelExpr step,
      CelExpr result) {
    return fold(
        iterVar, iterRange.build(), accuVar, accuInit.build(), condition.build(), step, result);
  }

  /** Fold creates a fold comprehension instruction. */
  public final CelExpr fold(
      String iterVar,
      CelExpr.Builder iterRange,
      String accuVar,
      CelExpr accuInit,
      CelExpr.Builder condition,
      CelExpr.Builder step,
      CelExpr.Builder result) {
    return fold(
        iterVar,
        iterRange.build(),
        accuVar,
        accuInit,
        condition.build(),
        step.build(),
        result.build());
  }

  /** Fold creates a fold comprehension instruction. */
  public final CelExpr fold(
      String iterVar,
      CelExpr.Builder iterRange,
      String accuVar,
      CelExpr.Builder accuInit,
      CelExpr.Builder condition,
      CelExpr.Builder step,
      CelExpr.Builder result) {
    return fold(
        iterVar,
        iterRange.build(),
        accuVar,
        accuInit.build(),
        condition.build(),
        step.build(),
        result.build());
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
    return idGenerator.generate(-1);
  }

  protected CelExprFactory() {
    this(CelExprIdGeneratorFactory.newMonotonicIdGenerator(0));
  }

  private CelExprFactory(CelExprIdGeneratorFactory.MonotonicIdGenerator idGenerator) {
    this((unused) -> idGenerator.nextExprId());
  }

  private CelExprFactory(CelExprIdGeneratorFactory.ExprIdGenerator exprIdGenerator) {
    idGenerator = exprIdGenerator;
  }
}
