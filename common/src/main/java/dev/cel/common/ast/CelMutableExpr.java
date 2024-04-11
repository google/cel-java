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
import java.util.List;
import java.util.Optional;

/**
 * An abstract representation of a common expression that allows mutation in any of its properties.
 * The expressions are semantically the same as that of the immutable {@link CelExpr}.
 *
 * <p>This allows for an efficient optimization of an AST without having to traverse and rebuild the
 * entire tree.
 *
 * <p>This class is not thread-safe by design.
 */
public final class CelMutableExpr {
  private long id;
  private ExprKind.Kind exprKind;
  private Object exprValue;

  public long id() {
    return id;
  }

  public void setId(long id) {
    this.id = id;
  }

  public ExprKind.Kind getKind() {
    return exprKind;
  }

  public CelNotSet notSet() {
    checkExprKind(Kind.NOT_SET);
    return (CelNotSet) exprValue;
  }

  public CelConstant constant() {
    checkExprKind(Kind.CONSTANT);
    return (CelConstant) exprValue;
  }

  public CelMutableIdent ident() {
    checkExprKind(Kind.IDENT);
    return (CelMutableIdent) exprValue;
  }

  public CelMutableSelect select() {
    checkExprKind(Kind.SELECT);
    return (CelMutableSelect) exprValue;
  }

  public CelMutableCall call() {
    checkExprKind(Kind.CALL);
    return (CelMutableCall) exprValue;
  }

  public CelMutableCreateList createList() {
    checkExprKind(Kind.CREATE_LIST);
    return (CelMutableCreateList) exprValue;
  }

  public CelMutableCreateStruct createStruct() {
    checkExprKind(Kind.CREATE_STRUCT);
    return (CelMutableCreateStruct) exprValue;
  }

  public CelMutableCreateMap createMap() {
    checkExprKind(Kind.CREATE_MAP);
    return (CelMutableCreateMap) exprValue;
  }

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

  public void setCreateList(CelMutableCreateList createList) {
    this.exprKind = ExprKind.Kind.CREATE_LIST;
    this.exprValue = checkNotNull(createList);
  }

  public void setCreateStruct(CelMutableCreateStruct createStruct) {
    this.exprKind = ExprKind.Kind.CREATE_STRUCT;
    this.exprValue = checkNotNull(createStruct);
  }

  public void setCreateMap(CelMutableCreateMap createMap) {
    this.exprKind = ExprKind.Kind.CREATE_MAP;
    this.exprValue = checkNotNull(createMap);
  }

  public void setComprehension(CelMutableComprehension comprehension) {
    this.exprKind = ExprKind.Kind.COMPREHENSION;
    this.exprValue = checkNotNull(comprehension);
  }

  /** A mutable identifier expression. */
  public static final class CelMutableIdent {
    private String name = "";

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
  public static final class CelMutableSelect {
    private CelMutableExpr operand;
    private String field = "";
    private boolean testOnly;

    public CelMutableExpr operand() {
      return operand;
    }

    public void setOperand(CelMutableExpr operand) {
      this.operand = checkNotNull(operand);
    }

    public String field() {
      return field;
    }

    public void setField(String field) {
      this.field = checkNotNull(field);
    }

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

  /** A mutable call expression, including calls to predefined functions and operators. */
  public static final class CelMutableCall {
    private Optional<CelMutableExpr> target;
    private String function;
    private List<CelMutableExpr> args;

    public Optional<CelMutableExpr> target() {
      return target;
    }

    public void setTarget(CelMutableExpr target) {
      this.target = Optional.of(target);
    }

    public String function() {
      return function;
    }

    public void setFunction(String function) {
      this.function = checkNotNull(function);
    }

    public List<CelMutableExpr> args() {
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
      List<CelMutableExpr> copiedArgs = deepCopyList(args);
      return target().isPresent()
          ? create(newInstance(target.get()), function, copiedArgs)
          : create(function, copiedArgs);
    }

    public static CelMutableCall create(String function, CelMutableExpr... args) {
      return create(function, Arrays.asList(checkNotNull(args)));
    }

    public static CelMutableCall create(String function, List<CelMutableExpr> args) {
      return new CelMutableCall(function, args);
    }

    public static CelMutableCall create(
        CelMutableExpr target, String function, CelMutableExpr... args) {
      return create(target, function, Arrays.asList(checkNotNull(args)));
    }

    public static CelMutableCall create(
        CelMutableExpr target, String function, List<CelMutableExpr> args) {
      return new CelMutableCall(target, function, args);
    }

    private CelMutableCall(String function, List<CelMutableExpr> args) {
      this.target = Optional.empty();
      this.function = checkNotNull(function);
      this.args = new ArrayList<>(checkNotNull(args));
    }

    private CelMutableCall(CelMutableExpr target, String function, List<CelMutableExpr> args) {
      this(function, args);
      this.target = Optional.of(target);
    }
  }

  /**
   * A mutable list creation expression.
   *
   * <p>Lists may either be homogenous, e.g. `[1, 2, 3]`, or heterogeneous, e.g. `dyn([1, 'hello',
   * 2.0])`
   */
  public static final class CelMutableCreateList {
    private final List<CelMutableExpr> elements;
    private final List<Integer> optionalIndices;

    public List<CelMutableExpr> elements() {
      return elements;
    }

    public void setElement(int index, CelMutableExpr element) {
      checkArgument(index >= 0 && index < elements().size());
      elements.set(index, checkNotNull(element));
    }

    public List<Integer> optionalIndices() {
      return optionalIndices;
    }

    @Override
    public boolean equals(Object obj) {
      if (obj == this) {
        return true;
      }
      if (obj instanceof CelMutableCreateList) {
        CelMutableCreateList that = (CelMutableCreateList) obj;
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

    private CelMutableCreateList deepCopy() {
      return create(deepCopyList(elements), optionalIndices);
    }

    public static CelMutableCreateList create(CelMutableExpr... elements) {
      return create(Arrays.asList(checkNotNull(elements)));
    }

    public static CelMutableCreateList create(List<CelMutableExpr> elements) {
      return create(elements, new ArrayList<>());
    }

    public static CelMutableCreateList create(
        List<CelMutableExpr> mutableExprList, List<Integer> optionalIndices) {
      return new CelMutableCreateList(mutableExprList, optionalIndices);
    }

    private CelMutableCreateList(
        List<CelMutableExpr> mutableExprList, List<Integer> optionalIndices) {
      this.elements = new ArrayList<>(checkNotNull(mutableExprList));
      this.optionalIndices = new ArrayList<>(checkNotNull(optionalIndices));
    }
  }

  /**
   * A mutable list creation expression.
   *
   * <p>Lists may either be homogenous, e.g. `[1, 2, 3]`, or heterogeneous, e.g. `dyn([1, 'hello',
   * 2.0])`
   */
  public static final class CelMutableCreateStruct {
    private String messageName = "";
    private List<CelMutableCreateStruct.Entry> entries;

    public String messageName() {
      return messageName;
    }

    public void setMessageName(String messageName) {
      this.messageName = checkNotNull(messageName);
    }

    public List<CelMutableCreateStruct.Entry> entries() {
      return entries;
    }

    public void setEntries(List<CelMutableCreateStruct.Entry> entries) {
      this.entries = checkNotNull(entries);
    }

    public void setEntry(int index, CelMutableCreateStruct.Entry entry) {
      checkArgument(index >= 0 && index < entries().size());
      entries.set(index, checkNotNull(entry));
    }

    /** Represents a mutable entry of the struct */
    public static final class Entry {
      private long id;
      private String fieldKey = "";
      private CelMutableExpr value;
      private boolean optionalEntry;

      public long id() {
        return id;
      }

      public void setId(long id) {
        this.id = id;
      }

      public String fieldKey() {
        return fieldKey;
      }

      public void setFieldKey(String fieldKey) {
        this.fieldKey = checkNotNull(fieldKey);
      }

      public CelMutableExpr value() {
        return value;
      }

      public void setValue(CelMutableExpr value) {
        this.value = checkNotNull(value);
      }

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
      if (obj instanceof CelMutableCreateStruct) {
        CelMutableCreateStruct that = (CelMutableCreateStruct) obj;
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

    private CelMutableCreateStruct deepCopy() {
      ArrayList<CelMutableCreateStruct.Entry> copiedEntries = new ArrayList<>();
      for (CelMutableCreateStruct.Entry entry : entries) {
        copiedEntries.add(entry.deepCopy());
      }

      return create(messageName, copiedEntries);
    }

    public static CelMutableCreateStruct create(
        String messageName, List<CelMutableCreateStruct.Entry> entries) {
      return new CelMutableCreateStruct(messageName, entries);
    }

    private CelMutableCreateStruct(String messageName, List<CelMutableCreateStruct.Entry> entries) {
      this.messageName = checkNotNull(messageName);
      this.entries = new ArrayList<>(checkNotNull(entries));
    }
  }

  /**
   * A mutable map creation expression.
   *
   * <p>Maps are constructed as `{'key_name': 'value'}`.
   */
  public static final class CelMutableCreateMap {
    private List<CelMutableCreateMap.Entry> entries;

    public List<CelMutableCreateMap.Entry> entries() {
      return entries;
    }

    public void setEntries(List<CelMutableCreateMap.Entry> entries) {
      this.entries = checkNotNull(entries);
    }

    public void setEntry(int index, CelMutableCreateMap.Entry entry) {
      checkArgument(index >= 0 && index < entries().size());
      entries.set(index, checkNotNull(entry));
    }

    /** Represents an entry of the map */
    public static final class Entry {
      private long id;
      private CelMutableExpr key;
      private CelMutableExpr value;
      private boolean optionalEntry;

      public long id() {
        return id;
      }

      public void setId(long id) {
        this.id = id;
      }

      public CelMutableExpr key() {
        return key;
      }

      public void setKey(CelMutableExpr key) {
        this.key = checkNotNull(key);
      }

      public CelMutableExpr value() {
        return value;
      }

      public void setValue(CelMutableExpr value) {
        this.value = checkNotNull(value);
      }

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
      if (obj instanceof CelMutableCreateMap) {
        CelMutableCreateMap that = (CelMutableCreateMap) obj;
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

    private CelMutableCreateMap deepCopy() {
      ArrayList<CelMutableCreateMap.Entry> copiedEntries = new ArrayList<>();
      for (CelMutableCreateMap.Entry entry : entries) {
        copiedEntries.add(entry.deepCopy());
      }

      return create(copiedEntries);
    }

    public static CelMutableCreateMap create(List<CelMutableCreateMap.Entry> entries) {
      return new CelMutableCreateMap(new ArrayList<>(entries));
    }

    private CelMutableCreateMap(List<CelMutableCreateMap.Entry> entries) {
      this.entries = checkNotNull(entries);
    }
  }

  /** A mutable comprehension expression applied to a list or map. */
  public static final class CelMutableComprehension {

    private String iterVar;

    private CelMutableExpr iterRange;

    private String accuVar;

    private CelMutableExpr accuInit;

    private CelMutableExpr loopCondition;

    private CelMutableExpr loopStep;

    private CelMutableExpr result;

    public String iterVar() {
      return iterVar;
    }

    public void setIterVar(String iterVar) {
      this.iterVar = checkNotNull(iterVar);
    }

    public CelMutableExpr iterRange() {
      return iterRange;
    }

    public void setIterRange(CelMutableExpr iterRange) {
      this.iterRange = checkNotNull(iterRange);
    }

    public String accuVar() {
      return accuVar;
    }

    public void setAccuVar(String accuVar) {
      this.accuVar = checkNotNull(accuVar);
    }

    public CelMutableExpr accuInit() {
      return accuInit;
    }

    public void setAccuInit(CelMutableExpr accuInit) {
      this.accuInit = checkNotNull(accuInit);
    }

    public CelMutableExpr loopCondition() {
      return loopCondition;
    }

    public void setLoopCondition(CelMutableExpr loopCondition) {
      this.loopCondition = checkNotNull(loopCondition);
    }

    public CelMutableExpr loopStep() {
      return loopStep;
    }

    public void setLoopStep(CelMutableExpr loopStep) {
      this.loopStep = checkNotNull(loopStep);
    }

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
      return new CelMutableComprehension(
          iterVar, iterRange, accuVar, accuInit, loopCondition, loopStep, result);
    }

    private CelMutableComprehension(
        String iterVar,
        CelMutableExpr iterRange,
        String accuVar,
        CelMutableExpr accuInit,
        CelMutableExpr loopCondition,
        CelMutableExpr loopStep,
        CelMutableExpr result) {
      this.iterVar = checkNotNull(iterVar);
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

  public static CelMutableExpr ofCreateList(CelMutableCreateList mutableCreateList) {
    return ofCreateList(0, mutableCreateList);
  }

  public static CelMutableExpr ofCreateList(long id, CelMutableCreateList mutableCreateList) {
    return new CelMutableExpr(id, mutableCreateList);
  }

  public static CelMutableExpr ofCreateStruct(CelMutableCreateStruct mutableCreateStruct) {
    return ofCreateStruct(0, mutableCreateStruct);
  }

  public static CelMutableExpr ofCreateStruct(long id, CelMutableCreateStruct mutableCreateStruct) {
    return new CelMutableExpr(id, mutableCreateStruct);
  }

  public static CelMutableExpr ofCreateMap(CelMutableCreateMap mutableCreateMap) {
    return ofCreateMap(0, mutableCreateMap);
  }

  public static CelMutableExpr ofCreateMap(long id, CelMutableCreateMap mutableCreateMap) {
    return new CelMutableExpr(id, mutableCreateMap);
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

  private CelMutableExpr(long id, CelMutableCreateList mutableCreateList) {
    this.id = id;
    setCreateList(mutableCreateList);
  }

  private CelMutableExpr(long id, CelMutableCreateStruct mutableCreateStruct) {
    this.id = id;
    setCreateStruct(mutableCreateStruct);
  }

  private CelMutableExpr(long id, CelMutableCreateMap mutableCreateMap) {
    this.id = id;
    setCreateMap(mutableCreateMap);
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
      case CREATE_LIST:
        this.exprValue = other.createList().deepCopy();
        break;
      case CREATE_STRUCT:
        this.exprValue = other.createStruct().deepCopy();
        break;
      case CREATE_MAP:
        this.exprValue = other.createMap().deepCopy();
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
      case CREATE_LIST:
        return createList();
      case CREATE_STRUCT:
        return createStruct();
      case CREATE_MAP:
        return createMap();
      case COMPREHENSION:
        return comprehension();
    }

    throw new IllegalStateException("Unexpected expr kind: " + this.exprKind);
  }

  private static List<CelMutableExpr> deepCopyList(List<CelMutableExpr> elements) {
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
  public int hashCode() {
    int h = 1;
    h *= 1000003;
    h ^= (int) ((id >>> 32) ^ id);
    h *= 1000003;
    h ^= this.exprValue().hashCode();

    return h;
  }
}
