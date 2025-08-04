// Copyright 2024 Google LLC
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
import static com.google.common.base.Preconditions.checkNotNull;

import dev.cel.common.ast.CelExpr.CelNotSet;
import dev.cel.common.ast.CelExpr.ExprKind;
import dev.cel.common.ast.CelExpr.ExprKind.Kind;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Optional;

/**
 * An abstract representation of a common expression that allows mutation in any of its properties.
 * The expressions are semantically the same as that of the immutable {@link CelExpr}. Refer to
 * {@link Expression} for details.
 *
 * <p>This allows for an efficient optimization of an AST without having to traverse and rebuild the
 * entire tree.
 *
 * <p>This class is not thread-safe by design.
 */
@SuppressWarnings("unchecked") // Class ensures only the super type is used
public final class CelMutableExpr implements Expression {
  private long id;
  private ExprKind.Kind exprKind;
  private Object exprValue;

  @Override
  public long id() {
    return id;
  }

  public void setId(long id) {
    this.id = id;
  }

  @Override
  public ExprKind.Kind getKind() {
    return exprKind;
  }

  public CelNotSet notSet() {
    checkExprKind(Kind.NOT_SET);
    return (CelNotSet) exprValue;
  }

  @Override
  public CelConstant constant() {
    checkExprKind(Kind.CONSTANT);
    return (CelConstant) exprValue;
  }

  @Override
  public CelMutableIdent ident() {
    checkExprKind(Kind.IDENT);
    return (CelMutableIdent) exprValue;
  }

  @Override
  public CelMutableSelect select() {
    checkExprKind(Kind.SELECT);
    return (CelMutableSelect) exprValue;
  }

  @Override
  public CelMutableCall call() {
    checkExprKind(Kind.CALL);
    return (CelMutableCall) exprValue;
  }

  @Override
  public CelMutableList list() {
    checkExprKind(Kind.LIST);
    return (CelMutableList) exprValue;
  }

  @Override
  public CelMutableStruct struct() {
    checkExprKind(Kind.STRUCT);
    return (CelMutableStruct) exprValue;
  }

  @Override
  public CelMutableMap map() {
    checkExprKind(Kind.MAP);
    return (CelMutableMap) exprValue;
  }

  @Override
  public CelMutableComprehension comprehension() {
    checkExprKind(Kind.COMPREHENSION);
    return (CelMutableComprehension) exprValue;
  }

  public void setConstant(CelConstant constant) {
    this.exprKind = ExprKind.Kind.CONSTANT;
    this.exprValue = checkNotNull(constant);
  }

  public void setIdent(CelMutableIdent ident) {
    this.exprKind = ExprKind.Kind.IDENT;
    this.exprValue = checkNotNull(ident);
  }

  public void setSelect(CelMutableSelect select) {
    this.exprKind = ExprKind.Kind.SELECT;
    this.exprValue = checkNotNull(select);
  }

  public void setCall(CelMutableCall call) {
    this.exprKind = ExprKind.Kind.CALL;
    this.exprValue = checkNotNull(call);
  }

  public void setList(CelMutableList list) {
    this.exprKind = ExprKind.Kind.LIST;
    this.exprValue = checkNotNull(list);
  }

  public void setStruct(CelMutableStruct struct) {
    this.exprKind = ExprKind.Kind.STRUCT;
    this.exprValue = checkNotNull(struct);
  }

  public void setMap(CelMutableMap map) {
    this.exprKind = ExprKind.Kind.MAP;
    this.exprValue = checkNotNull(map);
  }

  public void setComprehension(CelMutableComprehension comprehension) {
    this.exprKind = ExprKind.Kind.COMPREHENSION;
    this.exprValue = checkNotNull(comprehension);
  }

  /** A mutable identifier expression. */
  public static final class CelMutableIdent implements Ident {
    private String name = "";

    @Override
    public String name() {
      return name;
    }

    public void setName(String name) {
      this.name = checkNotNull(name);
    }

    public static CelMutableIdent create(String name) {
      return new CelMutableIdent(name);
    }

    @Override
    public boolean equals(Object obj) {
      if (obj == this) {
        return true;
      }
      if (obj instanceof CelMutableIdent) {
        CelMutableIdent that = (CelMutableIdent) obj;
        return this.name.equals(that.name);
      }

      return false;
    }

    @Override
    public int hashCode() {
      return name.hashCode();
    }

    private CelMutableIdent deepCopy() {
      return new CelMutableIdent(name);
    }

    private CelMutableIdent(String name) {
      this.name = checkNotNull(name);
    }
  }

  /** A mutable field selection expression. e.g. `request.auth`. */
  public static final class CelMutableSelect implements Expression.Select<CelMutableExpr> {
    private CelMutableExpr operand;
    private String field = "";
    private boolean testOnly;

    @Override
    public CelMutableExpr operand() {
      return operand;
    }

    public void setOperand(CelMutableExpr operand) {
      this.operand = checkNotNull(operand);
    }

    @Override
    public String field() {
      return field;
    }

    public void setField(String field) {
      this.field = checkNotNull(field);
    }

    @Override
    public boolean testOnly() {
      return testOnly;
    }

    public void setTestOnly(boolean testOnly) {
      this.testOnly = testOnly;
    }

    private CelMutableSelect deepCopy() {
      return create(newInstance(operand()), field, testOnly);
    }

    @Override
    public boolean equals(Object obj) {
      if (obj == this) {
        return true;
      }
      if (obj instanceof CelMutableSelect) {
        CelMutableSelect that = (CelMutableSelect) obj;
        return this.operand.equals(that.operand())
            && this.field.equals(that.field())
            && this.testOnly == that.testOnly();
      }
      return false;
    }

    @Override
    public int hashCode() {
      int h = 1;
      h *= 1000003;
      h ^= operand.hashCode();
      h *= 1000003;
      h ^= field.hashCode();
      h *= 1000003;
      h ^= testOnly ? 1231 : 1237;
      return h;
    }

    public static CelMutableSelect create(CelMutableExpr operand, String field) {
      return new CelMutableSelect(operand, field, false);
    }

    public static CelMutableSelect create(CelMutableExpr operand, String field, boolean testOnly) {
      return new CelMutableSelect(operand, field, testOnly);
    }

    private CelMutableSelect(CelMutableExpr operand, String field, boolean testOnly) {
      this.operand = checkNotNull(operand);
      this.field = checkNotNull(field);
      this.testOnly = testOnly;
    }
  }

  /** A mutable call expression. See {@link Expression.Call} */
  public static final class CelMutableCall implements Expression.Call<CelMutableExpr> {
    private Optional<CelMutableExpr> target;
    private String function;
    private java.util.List<CelMutableExpr> args;

    @Override
    public Optional<CelMutableExpr> target() {
      return target;
    }

    public void setTarget(CelMutableExpr target) {
      this.target = Optional.of(target);
    }

    @Override
    public String function() {
      return function;
    }

    public void setFunction(String function) {
      this.function = checkNotNull(function);
    }

    @Override
    public java.util.List<CelMutableExpr> args() {
      return args;
    }

    public void clearArgs() {
      args.clear();
    }

    public void addArgs(CelMutableExpr... exprs) {
      addArgs(Arrays.asList(checkNotNull(exprs)));
    }

    public void addArgs(Iterable<CelMutableExpr> exprs) {
      exprs.forEach(e -> args.add(checkNotNull(e)));
    }

    public void setArgs(Collection<CelMutableExpr> exprs) {
      this.args = new ArrayList<>(checkNotNull(exprs));
    }

    public void setArg(int index, CelMutableExpr arg) {
      checkArgument(index >= 0 && index < args.size());
      args.set(index, checkNotNull(arg));
    }

    @Override
    public boolean equals(Object obj) {
      if (obj == this) {
        return true;
      }
      if (obj instanceof CelMutableCall) {
        CelMutableCall that = (CelMutableCall) obj;
        return this.target.equals(that.target())
            && this.function.equals(that.function())
            && this.args.equals(that.args());
      }

      return false;
    }

    @Override
    public int hashCode() {
      int h = 1;
      h *= 1000003;
      h ^= target.hashCode();
      h *= 1000003;
      h ^= function.hashCode();
      h *= 1000003;
      h ^= args.hashCode();
      return h;
    }

    private CelMutableCall deepCopy() {
      java.util.List<CelMutableExpr> copiedArgs = deepCopyList(args);
      return target().isPresent()
          ? create(newInstance(target.get()), function, copiedArgs)
          : create(function, copiedArgs);
    }

    public static CelMutableCall create(String function, CelMutableExpr... args) {
      return create(function, Arrays.asList(checkNotNull(args)));
    }

    public static CelMutableCall create(String function, java.util.List<CelMutableExpr> args) {
      return new CelMutableCall(function, args);
    }

    public static CelMutableCall create(
        CelMutableExpr target, String function, CelMutableExpr... args) {
      return create(target, function, Arrays.asList(checkNotNull(args)));
    }

    public static CelMutableCall create(
        CelMutableExpr target, String function, java.util.List<CelMutableExpr> args) {
      return new CelMutableCall(target, function, args);
    }

    private CelMutableCall(String function, java.util.List<CelMutableExpr> args) {
      this.target = Optional.empty();
      this.function = checkNotNull(function);
      this.args = new ArrayList<>(checkNotNull(args));
    }

    private CelMutableCall(
        CelMutableExpr target, String function, java.util.List<CelMutableExpr> args) {
      this(function, args);
      this.target = Optional.of(target);
    }
  }

  /** A mutable list creation expression. See {@link List} */
  public static final class CelMutableList implements List<CelMutableExpr> {
    private final java.util.List<CelMutableExpr> elements;
    private final java.util.List<Integer> optionalIndices;

    @Override
    public java.util.List<CelMutableExpr> elements() {
      return elements;
    }

    public void setElement(int index, CelMutableExpr element) {
      checkArgument(index >= 0 && index < elements().size());
      elements.set(index, checkNotNull(element));
    }

    @Override
    public java.util.List<Integer> optionalIndices() {
      return optionalIndices;
    }

    @Override
    public boolean equals(Object obj) {
      if (obj == this) {
        return true;
      }
      if (obj instanceof CelMutableList) {
        CelMutableList that = (CelMutableList) obj;
        return this.elements.equals(that.elements())
            && this.optionalIndices.equals(that.optionalIndices());
      }

      return false;
    }

    @Override
    public int hashCode() {
      int h = 1;
      h *= 1000003;
      h ^= elements.hashCode();
      h *= 1000003;
      h ^= optionalIndices.hashCode();

      return h;
    }

    private CelMutableList deepCopy() {
      return create(deepCopyList(elements), optionalIndices);
    }

    public static CelMutableList create(CelMutableExpr... elements) {
      return create(Arrays.asList(checkNotNull(elements)));
    }

    public static CelMutableList create(java.util.List<CelMutableExpr> elements) {
      return create(elements, new ArrayList<>());
    }

    public static CelMutableList create(
        java.util.List<CelMutableExpr> mutableExprList, java.util.List<Integer> optionalIndices) {
      return new CelMutableList(mutableExprList, optionalIndices);
    }

    private CelMutableList(
        java.util.List<CelMutableExpr> mutableExprList, java.util.List<Integer> optionalIndices) {
      this.elements = new ArrayList<>(checkNotNull(mutableExprList));
      this.optionalIndices = new ArrayList<>(checkNotNull(optionalIndices));
    }
  }

  /** A mutable list creation expression. See {@link Expression.Struct} */
  public static final class CelMutableStruct implements Expression.Struct<CelMutableStruct.Entry> {
    private String messageName = "";
    private java.util.List<CelMutableStruct.Entry> entries;

    @Override
    public String messageName() {
      return messageName;
    }

    public void setMessageName(String messageName) {
      this.messageName = checkNotNull(messageName);
    }

    @Override
    public java.util.List<CelMutableStruct.Entry> entries() {
      return entries;
    }

    public void setEntries(java.util.List<CelMutableStruct.Entry> entries) {
      this.entries = checkNotNull(entries);
    }

    public void setEntry(int index, CelMutableStruct.Entry entry) {
      checkArgument(index >= 0 && index < entries().size());
      entries.set(index, checkNotNull(entry));
    }

    /** Represents a mutable entry of the struct. */
    public static final class Entry implements Expression.Struct.Entry<CelMutableExpr> {
      private long id;
      private String fieldKey = "";
      private CelMutableExpr value;
      private boolean optionalEntry;

      @Override
      public long id() {
        return id;
      }

      public void setId(long id) {
        this.id = id;
      }

      @Override
      public String fieldKey() {
        return fieldKey;
      }

      public void setFieldKey(String fieldKey) {
        this.fieldKey = checkNotNull(fieldKey);
      }

      @Override
      public CelMutableExpr value() {
        return value;
      }

      public void setValue(CelMutableExpr value) {
        this.value = checkNotNull(value);
      }

      @Override
      public boolean optionalEntry() {
        return optionalEntry;
      }

      public void setOptionalEntry(boolean optionalEntry) {
        this.optionalEntry = optionalEntry;
      }

      private Entry deepCopy() {
        return create(id, fieldKey, newInstance(value), optionalEntry);
      }

      public static Entry create(long id, String fieldKey, CelMutableExpr value) {
        return create(id, fieldKey, value, false);
      }

      public static Entry create(
          long id, String fieldKey, CelMutableExpr value, boolean optionalEntry) {
        return new Entry(id, fieldKey, value, optionalEntry);
      }

      @Override
      public boolean equals(Object obj) {
        if (obj == this) {
          return true;
        }
        if (obj instanceof Entry) {
          Entry that = (Entry) obj;
          return this.id == that.id()
              && this.fieldKey.equals(that.fieldKey())
              && this.value.equals(that.value())
              && this.optionalEntry == that.optionalEntry();
        }
        return false;
      }

      @Override
      public int hashCode() {
        int h = 1;
        h *= 1000003;
        h ^= (int) ((id >>> 32) ^ id);
        h *= 1000003;
        h ^= fieldKey.hashCode();
        h *= 1000003;
        h ^= value.hashCode();
        h *= 1000003;
        h ^= optionalEntry ? 1231 : 1237;
        return h;
      }

      private Entry(long id, String fieldKey, CelMutableExpr value, boolean optionalEntry) {
        this.id = id;
        this.fieldKey = checkNotNull(fieldKey);
        this.value = checkNotNull(value);
        this.optionalEntry = optionalEntry;
      }
    }

    @Override
    public boolean equals(Object obj) {
      if (obj == this) {
        return true;
      }
      if (obj instanceof CelMutableStruct) {
        CelMutableStruct that = (CelMutableStruct) obj;
        return this.messageName.equals(that.messageName()) && this.entries.equals(that.entries());
      }
      return false;
    }

    @Override
    public int hashCode() {
      int h = 1;
      h *= 1000003;
      h ^= messageName.hashCode();
      h *= 1000003;
      h ^= entries.hashCode();
      return h;
    }

    private CelMutableStruct deepCopy() {
      ArrayList<CelMutableStruct.Entry> copiedEntries = new ArrayList<>();
      for (CelMutableStruct.Entry entry : entries) {
        copiedEntries.add(entry.deepCopy());
      }

      return create(messageName, copiedEntries);
    }

    public static CelMutableStruct create(
        String messageName, java.util.List<CelMutableStruct.Entry> entries) {
      return new CelMutableStruct(messageName, entries);
    }

    private CelMutableStruct(String messageName, java.util.List<CelMutableStruct.Entry> entries) {
      this.messageName = checkNotNull(messageName);
      this.entries = new ArrayList<>(checkNotNull(entries));
    }
  }

  /** A mutable map creation expression. See {@link Expression.Map} */
  public static final class CelMutableMap implements Expression.Map<CelMutableMap.Entry> {
    private java.util.List<CelMutableMap.Entry> entries;

    @Override
    public java.util.List<CelMutableMap.Entry> entries() {
      return entries;
    }

    public void setEntries(java.util.List<CelMutableMap.Entry> entries) {
      this.entries = checkNotNull(entries);
    }

    public void setEntry(int index, CelMutableMap.Entry entry) {
      checkArgument(index >= 0 && index < entries().size());
      entries.set(index, checkNotNull(entry));
    }

    /** Represents an entry of the map */
    public static final class Entry implements Expression.Map.Entry<CelMutableExpr> {
      private long id;
      private CelMutableExpr key;
      private CelMutableExpr value;
      private boolean optionalEntry;

      @Override
      public long id() {
        return id;
      }

      public void setId(long id) {
        this.id = id;
      }

      @Override
      public CelMutableExpr key() {
        return key;
      }

      public void setKey(CelMutableExpr key) {
        this.key = checkNotNull(key);
      }

      @Override
      public CelMutableExpr value() {
        return value;
      }

      public void setValue(CelMutableExpr value) {
        this.value = checkNotNull(value);
      }

      @Override
      public boolean optionalEntry() {
        return optionalEntry;
      }

      public void setOptionalEntry(boolean optionalEntry) {
        this.optionalEntry = optionalEntry;
      }

      @Override
      public boolean equals(Object obj) {
        if (obj == this) {
          return true;
        }
        if (obj instanceof Entry) {
          Entry that = (Entry) obj;
          return this.id == that.id()
              && this.key.equals(that.key())
              && this.value.equals(that.value())
              && this.optionalEntry == that.optionalEntry();
        }
        return false;
      }

      @Override
      public int hashCode() {
        int h = 1;
        h *= 1000003;
        h ^= (int) ((id >>> 32) ^ id);
        h *= 1000003;
        h ^= key.hashCode();
        h *= 1000003;
        h ^= value.hashCode();
        h *= 1000003;
        h ^= optionalEntry ? 1231 : 1237;
        return h;
      }

      private Entry deepCopy() {
        return create(id, newInstance(key), newInstance(value), optionalEntry);
      }

      public static Entry create(CelMutableExpr key, CelMutableExpr value) {
        return create(0, key, value, false);
      }

      public static Entry create(long id, CelMutableExpr key, CelMutableExpr value) {
        return create(id, key, value, false);
      }

      public static Entry create(
          long id, CelMutableExpr key, CelMutableExpr value, boolean optionalEntry) {
        return new Entry(id, key, value, optionalEntry);
      }

      private Entry(long id, CelMutableExpr key, CelMutableExpr value, boolean optionalEntry) {
        this.id = id;
        this.key = checkNotNull(key);
        this.value = checkNotNull(value);
        this.optionalEntry = optionalEntry;
      }
    }

    @Override
    public boolean equals(Object obj) {
      if (obj == this) {
        return true;
      }
      if (obj instanceof CelMutableMap) {
        CelMutableMap that = (CelMutableMap) obj;
        return this.entries.equals(that.entries());
      }
      return false;
    }

    @Override
    public int hashCode() {
      int h = 1;
      h *= 1000003;
      h ^= entries.hashCode();
      return h;
    }

    private CelMutableMap deepCopy() {
      ArrayList<CelMutableMap.Entry> copiedEntries = new ArrayList<>();
      for (CelMutableMap.Entry entry : entries) {
        copiedEntries.add(entry.deepCopy());
      }

      return create(copiedEntries);
    }

    public static CelMutableMap create(java.util.List<CelMutableMap.Entry> entries) {
      return new CelMutableMap(new ArrayList<>(entries));
    }

    private CelMutableMap(java.util.List<CelMutableMap.Entry> entries) {
      this.entries = checkNotNull(entries);
    }
  }

  /**
   * A mutable comprehension expression applied to a list or map. See {@link
   * Expression.Comprehension}
   */
  public static final class CelMutableComprehension
      implements Expression.Comprehension<CelMutableExpr> {

    private String iterVar;

    private String iterVar2;

    private CelMutableExpr iterRange;

    private String accuVar;

    private CelMutableExpr accuInit;

    private CelMutableExpr loopCondition;

    private CelMutableExpr loopStep;

    private CelMutableExpr result;

    @Override
    public String iterVar() {
      return iterVar;
    }

    @Override
    public String iterVar2() {
      return iterVar2;
    }

    public void setIterVar(String iterVar) {
      this.iterVar = checkNotNull(iterVar);
    }

    public void setIterVar2(String iterVar2) {
      this.iterVar2 = checkNotNull(iterVar2);
    }

    @Override
    public CelMutableExpr iterRange() {
      return iterRange;
    }

    public void setIterRange(CelMutableExpr iterRange) {
      this.iterRange = checkNotNull(iterRange);
    }

    @Override
    public String accuVar() {
      return accuVar;
    }

    public void setAccuVar(String accuVar) {
      this.accuVar = checkNotNull(accuVar);
    }

    @Override
    public CelMutableExpr accuInit() {
      return accuInit;
    }

    public void setAccuInit(CelMutableExpr accuInit) {
      this.accuInit = checkNotNull(accuInit);
    }

    @Override
    public CelMutableExpr loopCondition() {
      return loopCondition;
    }

    public void setLoopCondition(CelMutableExpr loopCondition) {
      this.loopCondition = checkNotNull(loopCondition);
    }

    @Override
    public CelMutableExpr loopStep() {
      return loopStep;
    }

    public void setLoopStep(CelMutableExpr loopStep) {
      this.loopStep = checkNotNull(loopStep);
    }

    @Override
    public CelMutableExpr result() {
      return result;
    }

    public void setResult(CelMutableExpr result) {
      this.result = checkNotNull(result);
    }

    @Override
    public boolean equals(Object obj) {
      if (obj == this) {
        return true;
      }
      if (obj instanceof CelMutableComprehension) {
        CelMutableComprehension that = (CelMutableComprehension) obj;
        return this.iterVar.equals(that.iterVar())
            && this.iterVar2.equals(that.iterVar2())
            && this.accuVar.equals(that.accuVar())
            && this.iterRange.equals(that.iterRange())
            && this.accuInit.equals(that.accuInit())
            && this.loopCondition.equals(that.loopCondition())
            && this.loopStep.equals(that.loopStep())
            && this.result.equals(that.result());
      }
      return false;
    }

    @Override
    public int hashCode() {
      int h = 1;
      h *= 1000003;
      h ^= iterVar.hashCode();
      h *= 1000003;
      h ^= iterVar2.hashCode();
      h *= 1000003;
      h ^= iterRange.hashCode();
      h *= 1000003;
      h ^= accuVar.hashCode();
      h *= 1000003;
      h ^= accuInit.hashCode();
      h *= 1000003;
      h ^= loopCondition.hashCode();
      h *= 1000003;
      h ^= loopStep.hashCode();
      h *= 1000003;
      h ^= result.hashCode();
      return h;
    }

    private CelMutableComprehension deepCopy() {
      return create(
          iterVar,
          iterVar2,
          newInstance(iterRange),
          accuVar,
          newInstance(accuInit),
          newInstance(loopCondition),
          newInstance(loopStep),
          newInstance(result));
    }

    public static CelMutableComprehension create(
        String iterVar,
        CelMutableExpr iterRange,
        String accuVar,
        CelMutableExpr accuInit,
        CelMutableExpr loopCondition,
        CelMutableExpr loopStep,
        CelMutableExpr result) {
      return create(
          iterVar,
          /* iterVar2= */ "",
          iterRange,
          accuVar,
          accuInit,
          loopCondition,
          loopStep,
          result);
    }

    public static CelMutableComprehension create(
        String iterVar,
        String iterVar2,
        CelMutableExpr iterRange,
        String accuVar,
        CelMutableExpr accuInit,
        CelMutableExpr loopCondition,
        CelMutableExpr loopStep,
        CelMutableExpr result) {
      return new CelMutableComprehension(
          iterVar, iterVar2, iterRange, accuVar, accuInit, loopCondition, loopStep, result);
    }

    private CelMutableComprehension(
        String iterVar,
        String iterVar2,
        CelMutableExpr iterRange,
        String accuVar,
        CelMutableExpr accuInit,
        CelMutableExpr loopCondition,
        CelMutableExpr loopStep,
        CelMutableExpr result) {
      this.iterVar = checkNotNull(iterVar);
      this.iterVar2 = checkNotNull(iterVar2);
      this.iterRange = checkNotNull(iterRange);
      this.accuVar = checkNotNull(accuVar);
      this.accuInit = checkNotNull(accuInit);
      this.loopCondition = checkNotNull(loopCondition);
      this.loopStep = checkNotNull(loopStep);
      this.result = checkNotNull(result);
    }
  }

  public static CelMutableExpr ofNotSet() {
    return ofNotSet(0L);
  }

  public static CelMutableExpr ofNotSet(long id) {
    return new CelMutableExpr(id);
  }

  public static CelMutableExpr ofConstant(CelConstant constant) {
    return ofConstant(0L, constant);
  }

  public static CelMutableExpr ofConstant(long id, CelConstant constant) {
    return new CelMutableExpr(id, constant);
  }

  public static CelMutableExpr ofIdent(String name) {
    return ofIdent(0, name);
  }

  public static CelMutableExpr ofIdent(long id, String name) {
    return new CelMutableExpr(id, CelMutableIdent.create(name));
  }

  public static CelMutableExpr ofSelect(CelMutableSelect mutableSelect) {
    return ofSelect(0, mutableSelect);
  }

  public static CelMutableExpr ofSelect(long id, CelMutableSelect mutableSelect) {
    return new CelMutableExpr(id, mutableSelect);
  }

  public static CelMutableExpr ofCall(CelMutableCall mutableCall) {
    return ofCall(0, mutableCall);
  }

  public static CelMutableExpr ofCall(long id, CelMutableCall mutableCall) {
    return new CelMutableExpr(id, mutableCall);
  }

  public static CelMutableExpr ofList(CelMutableList mutableList) {
    return ofList(0, mutableList);
  }

  public static CelMutableExpr ofList(long id, CelMutableList mutableList) {
    return new CelMutableExpr(id, mutableList);
  }

  public static CelMutableExpr ofStruct(CelMutableStruct mutableStruct) {
    return ofStruct(0, mutableStruct);
  }

  public static CelMutableExpr ofStruct(long id, CelMutableStruct mutableStruct) {
    return new CelMutableExpr(id, mutableStruct);
  }

  public static CelMutableExpr ofMap(CelMutableMap mutableMap) {
    return ofMap(0, mutableMap);
  }

  public static CelMutableExpr ofMap(long id, CelMutableMap mutableMap) {
    return new CelMutableExpr(id, mutableMap);
  }

  public static CelMutableExpr ofComprehension(
      long id, CelMutableComprehension mutableComprehension) {
    return new CelMutableExpr(id, mutableComprehension);
  }

  /** Constructs a deep copy of the mutable expression. */
  public static CelMutableExpr newInstance(CelMutableExpr other) {
    return new CelMutableExpr(other);
  }

  private CelMutableExpr(long id, CelConstant mutableConstant) {
    this.id = id;
    setConstant(mutableConstant);
  }

  private CelMutableExpr(long id, CelMutableIdent mutableIdent) {
    this.id = id;
    setIdent(mutableIdent);
  }

  private CelMutableExpr(long id, CelMutableSelect mutableSelect) {
    this.id = id;
    setSelect(mutableSelect);
  }

  private CelMutableExpr(long id, CelMutableCall mutableCall) {
    this.id = id;
    setCall(mutableCall);
  }

  private CelMutableExpr(long id, CelMutableList mutableList) {
    this.id = id;
    setList(mutableList);
  }

  private CelMutableExpr(long id, CelMutableStruct mutableStruct) {
    this.id = id;
    setStruct(mutableStruct);
  }

  private CelMutableExpr(long id, CelMutableMap mutableMap) {
    this.id = id;
    setMap(mutableMap);
  }

  private CelMutableExpr(long id, CelMutableComprehension mutableComprehension) {
    this.id = id;
    setComprehension(mutableComprehension);
  }

  private CelMutableExpr(long id) {
    this();
    this.id = id;
  }

  private CelMutableExpr() {
    this.exprValue = CelExpr.newBuilder().build().exprKind().notSet();
    this.exprKind = ExprKind.Kind.NOT_SET;
  }

  private CelMutableExpr(CelMutableExpr other) {
    checkNotNull(other);
    this.id = other.id;
    this.exprKind = other.exprKind;
    switch (other.getKind()) {
      case NOT_SET:
        this.exprValue = CelExpr.newBuilder().build().exprKind().notSet();
        break;
      case CONSTANT:
        this.exprValue = other.exprValue; // Constant is immutable.
        break;
      case IDENT:
        this.exprValue = other.ident().deepCopy();
        break;
      case SELECT:
        this.exprValue = other.select().deepCopy();
        break;
      case CALL:
        this.exprValue = other.call().deepCopy();
        break;
      case LIST:
        this.exprValue = other.list().deepCopy();
        break;
      case STRUCT:
        this.exprValue = other.struct().deepCopy();
        break;
      case MAP:
        this.exprValue = other.map().deepCopy();
        break;
      case COMPREHENSION:
        this.exprValue = other.comprehension().deepCopy();
        break;
      default:
        throw new IllegalStateException("Unexpected expr kind: " + this.exprKind);
    }
  }

  private Object exprValue() {
    switch (this.exprKind) {
      case NOT_SET:
        return notSet();
      case CONSTANT:
        return constant();
      case IDENT:
        return ident();
      case SELECT:
        return select();
      case CALL:
        return call();
      case LIST:
        return list();
      case STRUCT:
        return struct();
      case MAP:
        return map();
      case COMPREHENSION:
        return comprehension();
    }

    throw new IllegalStateException("Unexpected expr kind: " + this.exprKind);
  }

  private static java.util.List<CelMutableExpr> deepCopyList(
      java.util.List<CelMutableExpr> elements) {
    ArrayList<CelMutableExpr> copiedArgs = new ArrayList<>();
    for (CelMutableExpr arg : elements) {
      copiedArgs.add(newInstance(arg));
    }

    return copiedArgs;
  }

  private void checkExprKind(ExprKind.Kind exprKind) {
    checkArgument(this.exprKind.equals(exprKind), "Invalid ExprKind: %s", exprKind);
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == this) {
      return true;
    }
    if (obj instanceof CelMutableExpr) {
      CelMutableExpr that = (CelMutableExpr) obj;
      if (this.id != that.id() || !this.exprKind.equals(that.getKind())) {
        return false;
      }

      return this.exprValue().equals(that.exprValue());
    }

    return false;
  }

  @Override
  public String toString() {
    return CelMutableExprConverter.fromMutableExpr(this).toString();
  }

  @Override
  public int hashCode() {
    int h = 1;
    h *= 1000003;
    h ^= (int) ((id >>> 32) ^ id);
    h *= 1000003;
    h ^= this.exprValue().hashCode();

    return h;
  }
}
