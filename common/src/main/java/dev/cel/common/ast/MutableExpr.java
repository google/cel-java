package dev.cel.common.ast;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import dev.cel.common.ast.CelExpr.CelNotSet;
import dev.cel.common.ast.CelExpr.ExprKind;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

public final class MutableExpr {
  private long id;
  private ExprKind.Kind exprKind;
  private CelNotSet notSet;
  private CelConstant constant;
  private MutableIdent ident;
  private MutableCall call;
  private MutableSelect select;
  private MutableCreateList createList;
  private MutableCreateStruct createStruct;
  private MutableCreateMap createMap;
  private MutableComprehension comprehension;

  public long id() {
    return id;
  }

  public void setId(long id) {
    this.id = id;
  }

  public ExprKind.Kind exprKind() {
    return exprKind;
  }

  public CelNotSet notSet() {
    if (!this.exprKind.equals(ExprKind.Kind.NOT_SET)) {
      throw new IllegalStateException("Invalid ExprKind: " + this.exprKind);
    }
    return notSet;
  }


  public CelConstant constant() {
    if (!this.exprKind.equals(ExprKind.Kind.CONSTANT)) {
      throw new IllegalStateException("Invalid ExprKind: " + this.exprKind);
    }
    return constant;
  }

  void setConstant(CelConstant constant) {
    this.exprKind = ExprKind.Kind.CONSTANT;
    this.constant = constant;
  }

  public MutableIdent ident() {
    if (!this.exprKind.equals(ExprKind.Kind.IDENT)) {
      throw new IllegalStateException("Invalid ExprKind: " + this.exprKind);
    }
    return ident;
  }

  void setIdent(MutableIdent ident) {
    this.exprKind = ExprKind.Kind.IDENT;
    this.ident = ident;
  }

  public MutableCall call() {
    if (!this.exprKind.equals(ExprKind.Kind.CALL)) {
      throw new IllegalStateException("Invalid ExprKind: " + this.exprKind);
    }
    return call;
  }

  public void setCall(MutableCall call) {
    this.exprKind = ExprKind.Kind.CALL;
    this.call = call;
  }

  public MutableSelect select() {
    if (!this.exprKind.equals(ExprKind.Kind.SELECT)) {
      throw new IllegalStateException("Invalid ExprKind: " + this.exprKind);
    }
    return select;
  }

  public void setSelect(MutableSelect select) {
    this.exprKind = ExprKind.Kind.SELECT;
    this.select = select;
  }

  public MutableCreateList createList() {
    if (!this.exprKind.equals(ExprKind.Kind.CREATE_LIST)) {
      throw new IllegalStateException("Invalid ExprKind: " + this.exprKind);
    }
    return createList;
  }

  public void setCreateList(MutableCreateList createList) {
    this.exprKind = ExprKind.Kind.CREATE_LIST;
    this.createList = createList;
  }

  public MutableCreateStruct createStruct() {
    if (!this.exprKind.equals(ExprKind.Kind.CREATE_STRUCT)) {
      throw new IllegalStateException("Invalid ExprKind: " + this.exprKind);
    }
    return createStruct;
  }

  public void setCreateStruct(MutableCreateStruct createStruct) {
    this.exprKind = ExprKind.Kind.CREATE_STRUCT;
    this.createStruct = createStruct;
  }

  public MutableCreateMap createMap() {
    if (!this.exprKind.equals(ExprKind.Kind.CREATE_MAP)) {
      throw new IllegalStateException("Invalid ExprKind: " + this.exprKind);
    }
    return createMap;
  }

  public void setCreateMap(MutableCreateMap createMap) {
    this.exprKind = ExprKind.Kind.CREATE_MAP;
    this.createMap = createMap;
  }

  public MutableComprehension comprehension() {
    if (!this.exprKind.equals(ExprKind.Kind.COMPREHENSION)) {
      throw new IllegalStateException("Invalid ExprKind: " + this.exprKind);
    }
    return comprehension;
  }

  public void setComprehension(MutableComprehension comprehension) {
    this.exprKind = ExprKind.Kind.COMPREHENSION;
    this.comprehension = comprehension;
  }

  public final static class MutableIdent {
    private String name;

    public String name() {
      return name;
    }

    public void setName(String name) {
      this.name = name;
    }

    public static MutableIdent create(String name) {
      return new MutableIdent(name);
    }

    @Override
    public boolean equals(Object obj) {
      if (obj == this) {
        return true;
      }
      if (obj instanceof MutableIdent) {
        MutableIdent that = (MutableIdent) obj;
        return this.name.equals(that.name);
      }

      return false;
    }

    @Override
    public int hashCode() {
      return name.hashCode();
    }

    private MutableIdent(String name) {
      this.name = name;
    }
  }

  public final static class MutableSelect {
    private MutableExpr operand;
    private String field;
    private boolean testOnly;

    public MutableExpr operand() {
      return operand;
    }

    public void setOperand(MutableExpr operand) {
      this.operand = operand;
    }

    String field() {
      return field;
    }

    void setField(String field) {
      this.field = field;
    }

    public boolean testOnly() {
      return testOnly;
    }

    public void setTestOnly(boolean testOnly) {
      this.testOnly = testOnly;
    }

    @Override
    public boolean equals(Object obj) {
      if (obj == this) {
        return true;
      }
      if (obj instanceof MutableSelect) {
        MutableSelect that = (MutableSelect) obj;
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

    public static MutableSelect create(MutableExpr operand, String field) {
      return new MutableSelect(operand, field, false);
    }

    public static MutableSelect create(MutableExpr operand, String field, boolean testOnly) {
      return new MutableSelect(operand, field, testOnly);
    }

    private MutableSelect(MutableExpr operand, String field, boolean testOnly) {
      this.operand = operand;
      this.field = field;
      this.testOnly = testOnly;
    }
  }

  public final static class MutableCall {
    private Optional<MutableExpr> target;
    private String function;
    private List<MutableExpr> args;

    public Optional<MutableExpr> target() {
      return target;
    }

    public void setTarget(MutableExpr target) {
      this.target = Optional.of(target);
    }

    public String function() { return function;
    }

    public void setFunction(String function) {
      this.function = function;
    }

    public List<MutableExpr> args() {
      return args;
    }

    public void clearArgs() {
      args.clear();
    }

    void setArgs(List<MutableExpr> args) {
      this.args = args;
    }

    public void addArgs(MutableExpr... exprs) {
      checkNotNull(exprs);
      addArgs(Arrays.asList(exprs));
    }

    @CanIgnoreReturnValue
    public void addArgs(Iterable<MutableExpr> exprs) {
      checkNotNull(exprs);
      exprs.forEach(args::add);
    }


    public void setArg(int index, MutableExpr arg) {
      checkNotNull(arg);
      checkArgument(index >= 0 && index < args.size());
      args.set(index, arg);
    }

    @Override
    public boolean equals(Object obj) {
      if (obj == this) {
        return true;
      }
      if (obj instanceof MutableCall) {
        MutableCall that = (MutableCall) obj;
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

    public static MutableCall create(String function, MutableExpr... args) {
      return create(function, Arrays.asList(args));
    }

    public static MutableCall create(String function, List<MutableExpr> args) {
      return new MutableCall(function, args);
    }

    public static MutableCall create(MutableExpr target, String function, MutableExpr... args) {
      return create(target, function, Arrays.asList(args));
    }

    public static MutableCall create(MutableExpr target, String function, List<MutableExpr> args) {
      return new MutableCall(target, function, args);
    }

    private MutableCall(String function, List<MutableExpr> args) {
      this.target = Optional.empty();
      this.function = function;
      this.args = new ArrayList<>(args);
    }

    private MutableCall(MutableExpr target, String function, List<MutableExpr> args) {
      this(function, args);
      this.target = Optional.of(target);
    }
  }

  public final static class MutableCreateList {
    private List<MutableExpr> elements;
    private List<Integer> optionalIndices;

    public List<MutableExpr> elements() {
      return elements;
    }

    void setElements(List<MutableExpr> elements) {
      this.elements = elements;
    }

    public void setElement(int index, MutableExpr element) {
      checkNotNull(element);
      checkArgument(index >= 0 && index < elements().size());
      elements.set(index, element);
    }

    public List<Integer> optionalIndices() {
      return optionalIndices;
    }

    void setOptionalIndices(List<Integer> optionalIndices) {
      this.optionalIndices = optionalIndices;
    }

    @Override
    public boolean equals(Object obj) {
      if (obj == this) {
        return true;
      }
      if (obj instanceof MutableCreateList) {
        MutableCreateList that = (MutableCreateList) obj;
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

    public static MutableCreateList create(MutableExpr... elements) {
      return create(Arrays.asList(elements));
    }

    public static MutableCreateList create(List<MutableExpr> elements) {
      return create(elements, new ArrayList<>());
    }

    public static MutableCreateList create(List<MutableExpr> mutableExprList, List<Integer> optionalIndices) {
      return new MutableCreateList(mutableExprList, optionalIndices);
    }

    private MutableCreateList(List<MutableExpr> mutableExprList, List<Integer> optionalIndices) {
      this.elements = new ArrayList<>(mutableExprList);
      this.optionalIndices = new ArrayList<>(optionalIndices);
    }
  }

  public final static class MutableCreateStruct {
    private String messageName;
    private List<Entry> entries;

    public String messageName() {
      return messageName;
    }

    public void setMessageName(String messageName) {
      this.messageName = messageName;
    }

    public List<Entry> entries() {
      return entries;
    }

    public void setEntries(List<Entry> entries) {
      this.entries = entries;
    }

    public void setEntry(int index, Entry entry) {
      checkNotNull(entry);
      checkArgument(index >= 0 && index < entries().size());
      entries.set(index, entry);
    }

    public final static class Entry {
      private long id;
      private String fieldKey;
      private MutableExpr value;
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
        this.fieldKey = fieldKey;
      }

      public MutableExpr value() {
        return value;
      }

      public void setValue(MutableExpr value) {
        this.value = value;
      }

      public boolean optionalEntry() {
        return optionalEntry;
      }

      public void setOptionalEntry(boolean optionalEntry) {
        this.optionalEntry = optionalEntry;
      }

      public static Entry create(long id, String fieldKey, MutableExpr value) {
        return new Entry(id, fieldKey, value, false);
      }

      public static Entry create(long id, String fieldKey, MutableExpr value, boolean optionalEntry) {
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

      private Entry(long id, String fieldKey, MutableExpr value, boolean optionalEntry) {
        this.id = id;
        this.fieldKey = fieldKey;
        this.value = value;
        this.optionalEntry = optionalEntry;
      }
    }

    @Override
    public boolean equals(Object obj) {
      if (obj == this) {
        return true;
      }
      if (obj instanceof MutableCreateStruct) {
        MutableCreateStruct that = (MutableCreateStruct) obj;
        return this.messageName.equals(that.messageName())
            && this.entries.equals(that.entries());
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

    public static MutableCreateStruct create(String messageName, List<MutableCreateStruct.Entry> entries) {
      return new MutableCreateStruct(messageName, entries);
    }

    private MutableCreateStruct(String messageName, List<MutableCreateStruct.Entry> entries) {
      this.messageName = messageName;
      this.entries = new ArrayList<>(entries);
    }
  }

  public final static class MutableCreateMap {
    private List<Entry> entries;

    public List<Entry> entries() {
      return entries;
    }

    public void setEntries(List<Entry> entries) {
      this.entries = entries;
    }

    public void setEntry(int index, Entry entry) {
      checkNotNull(entry);
      checkArgument(index >= 0 && index < entries().size());
      entries.set(index, entry);
    }

    public final static class Entry {
      private long id;
      private MutableExpr key;
      private MutableExpr value;
      private boolean optionalEntry;

      public long id() {
        return id;
      }

      public void setId(long id) {
        this.id = id;
      }

      public MutableExpr key() {
        return key;
      }

      public void setKey(MutableExpr key) {
        this.key = key;
      }

      public MutableExpr value() {
        return value;
      }

      public void setValue(MutableExpr value) {
        this.value = value;
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

      public static Entry create(MutableExpr key, MutableExpr value) {
        return create(0, key, value, false);
      }

      public static Entry create(long id, MutableExpr key, MutableExpr value) {
        return create(id, key, value, false);
      }

      public static Entry create(long id, MutableExpr key, MutableExpr value, boolean optionalEntry) {
        return new Entry(id, key, value, optionalEntry);
      }

      private Entry(long id, MutableExpr key, MutableExpr value, boolean optionalEntry) {
        this.id = id;
        this.key = key;
        this.value = value;
        this.optionalEntry = optionalEntry;
      }
    }

    @Override
    public boolean equals(Object obj) {
      if (obj == this) {
        return true;
      }
      if (obj instanceof MutableCreateMap) {
        MutableCreateMap that = (MutableCreateMap) obj;
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

    public static MutableCreateMap create(List<MutableCreateMap.Entry> entries) {
      return new MutableCreateMap(new ArrayList<>(entries));
    }

    private MutableCreateMap(List<MutableCreateMap.Entry> entries) {
      this.entries = entries;
    }
  }

  public final static class MutableComprehension {

    /** The name of the iteration variable. */
    private String iterVar;

    /** The range over which var iterates. */
    private MutableExpr iterRange;

    /** The name of the variable used for accumulation of the result. */
    private String accuVar;

    /** The initial value of the accumulator. */
    private MutableExpr accuInit;

    /**
     * An expression which can contain iter_var and accu_var.
     *
     * <p>Returns false when the result has been computed and may be used as a hint to short-circuit
     * the remainder of the comprehension.
     */
    private MutableExpr loopCondition;

    /**
     * An expression which can contain iter_var and accu_var.
     *
     * <p>Computes the next value of accu_var.
     */
    private MutableExpr loopStep;

    /**
     * An expression which can contain accu_var.
     *
     * <p>Computes the result.
     */
    private MutableExpr result;

    public String iterVar() {
      return iterVar;
    }

    public void setIterVar(String iterVar) {
      this.iterVar = iterVar;
    }

    public MutableExpr iterRange() {
      return iterRange;
    }

    public void setIterRange(MutableExpr iterRange) {
      this.iterRange = iterRange;
    }

    public String accuVar() {
      return accuVar;
    }

    public void setAccuVar(String accuVar) {
      this.accuVar = accuVar;
    }

    public MutableExpr accuInit() {
      return accuInit;
    }

    public void setAccuInit(MutableExpr accuInit) {
      this.accuInit = accuInit;
    }

    public MutableExpr loopCondition() {
      return loopCondition;
    }

    public void setLoopCondition(MutableExpr loopCondition) {
      this.loopCondition = loopCondition;
    }

    public MutableExpr loopStep() {
      return loopStep;
    }

    public void setLoopStep(MutableExpr loopStep) {
      this.loopStep = loopStep;
    }

    public MutableExpr result() {
      return result;
    }

    public void setResult(MutableExpr result) {
      this.result = result;
    }

    @Override
    public boolean equals(Object obj) {
      if (obj == this) {
        return true;
      }
      if (obj instanceof MutableComprehension) {
        MutableComprehension that = (MutableComprehension) obj;
        return this.iterVar.equals(that.iterVar())
            && this.iterRange.equals(that.iterRange())
            && this.accuVar.equals(that.accuVar())
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

    public static MutableComprehension create(String iterVar, MutableExpr iterRange, String accuVar,
        MutableExpr accuInit, MutableExpr loopCondition, MutableExpr loopStep, MutableExpr result) {
      return new MutableComprehension(iterVar, iterRange, accuVar, accuInit, loopCondition, loopStep, result);
    }

    private MutableComprehension(String iterVar, MutableExpr iterRange, String accuVar,
        MutableExpr accuInit, MutableExpr loopCondition, MutableExpr loopStep, MutableExpr result) {
      this.iterVar = iterVar;
      this.iterRange = iterRange;
      this.accuVar = accuVar;
      this.accuInit = accuInit;
      this.loopCondition = loopCondition;
      this.loopStep = loopStep;
      this.result = result;
    }
  }

  public static MutableExpr ofConstant(CelConstant constant) {
    return new MutableExpr(0, constant);
  }

  public static MutableExpr ofConstant(long id, CelConstant constant) {
    return new MutableExpr(id, constant);
  }

  public static MutableExpr ofIdent(String name) {
    return new MutableExpr(0, MutableIdent.create(name));
  }

  public static MutableExpr ofIdent(long id, String name) {
    return new MutableExpr(id, MutableIdent.create(name));
  }

  public static MutableExpr ofCall(MutableCall mutableCall) {
    return new MutableExpr(0, mutableCall);
  }

  public static MutableExpr ofCall(long id, MutableCall mutableCall) {
    return new MutableExpr(id, mutableCall);
  }

  public static MutableExpr ofSelect(MutableSelect mutableSelect) {
    return new MutableExpr(0, mutableSelect);
  }

  public static MutableExpr ofSelect(long id, MutableSelect mutableSelect) {
    return new MutableExpr(id, mutableSelect);
  }

  public static MutableExpr ofCreateList(MutableCreateList mutableCreateList) {
    return new MutableExpr(0, mutableCreateList);
  }

  public static MutableExpr ofCreateList(long id, MutableCreateList mutableCreateList) {
    return new MutableExpr(id, mutableCreateList);
  }

  public static MutableExpr ofCreateStruct(MutableCreateStruct mutableCreateStruct) {
    return new MutableExpr(0, mutableCreateStruct);
  }

  public static MutableExpr ofCreateStruct(long id, MutableCreateStruct mutableCreateStruct) {
    return new MutableExpr(id, mutableCreateStruct);
  }

  public static MutableExpr ofCreateMap(MutableCreateMap mutableCreateMap) {
    return new MutableExpr(0, mutableCreateMap);
  }

  public static MutableExpr ofCreateMap(long id, MutableCreateMap mutableCreateMap) {
    return new MutableExpr(id, mutableCreateMap);
  }

  public static MutableExpr ofComprehension(long id, MutableComprehension mutableComprehension) {
    return new MutableExpr(id, mutableComprehension);
  }

  public static MutableExpr ofNotSet() {
    return new MutableExpr();
  }

  public static MutableExpr ofNotSet(long id) {
    return new MutableExpr(id);
  }

  private MutableExpr(long id, CelConstant mutableConstant) {
    this.id = id;
    setConstant(mutableConstant);
  }

  private MutableExpr(long id, MutableIdent mutableIdent) {
    this.id = id;
    setIdent(mutableIdent);
  }

  private MutableExpr(long id, MutableCall mutableCall) {
    this.id = id;
    setCall(mutableCall);
  }

  private MutableExpr(long id, MutableSelect mutableSelect) {
    this.id = id;
    setSelect(mutableSelect);
  }

  private MutableExpr(long id, MutableCreateList mutableCreateList) {
    this.id = id;
    setCreateList(mutableCreateList);
  }

  private MutableExpr(long id, MutableCreateStruct mutableCreateStruct) {
    this.id = id;
    setCreateStruct(mutableCreateStruct);
  }

  private MutableExpr(long id, MutableCreateMap mutableCreateMap) {
    this.id = id;
    setCreateMap(mutableCreateMap);
  }

  private MutableExpr(long id, MutableComprehension mutableComprehension) {
    this.id = id;
    setComprehension(mutableComprehension);
  }

  private MutableExpr(long id) {
    this();
    this.id = id;
  }

  private MutableExpr() {
    this.notSet = CelExpr.newBuilder().build().exprKind().notSet();
    this.exprKind = ExprKind.Kind.NOT_SET;
  }

  @Override
  public String toString() {
    return MutableExprConverter.fromMutableExpr(this).toString();
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

  public MutableExpr deepCopy() {
    // TODO: Perform a proper direct copy
    return MutableExprConverter.fromCelExpr(MutableExprConverter.fromMutableExpr(this));
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == this) {
      return true;
    }
    if (obj instanceof MutableExpr) {
      MutableExpr that = (MutableExpr) obj;
      if (this.id != that.id() || !this.exprKind.equals(that.exprKind())) {
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
