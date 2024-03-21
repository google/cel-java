package dev.cel.common.navigation;

import com.google.common.primitives.UnsignedLong;
import com.google.protobuf.ByteString;
import com.google.protobuf.NullValue;
import dev.cel.common.ast.CelConstant;
import dev.cel.common.ast.CelConstant.Kind;
import dev.cel.common.ast.CelExpr.ExprKind;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

public final class MutableExpr {
  private long id;
  private ExprKind.Kind exprKind;
  private MutableConstant constant;
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

  public MutableConstant constant() {
    if (!this.exprKind.equals(ExprKind.Kind.CONSTANT)) {
      throw new IllegalStateException("Invalid ExprKind: " + this.exprKind);
    }
    return constant;
  }

  void setConstant(MutableConstant constant) {
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

  public final static class MutableConstant {
    private CelConstant.Kind constantKind;
    private NullValue nullValue;

    private boolean booleanValue;

    private long int64Value;

    private UnsignedLong uint64Value;

    private double doubleValue;

    private String stringValue;

    private ByteString bytesValue;

    public Kind constantKind() {
      return constantKind;
    }

    public boolean booleanValue() {
      return booleanValue;
    }

    void setBooleanValue(boolean booleanValue) {
      this.constantKind = Kind.BOOLEAN_VALUE;
      this.booleanValue = booleanValue;
    }

    public long int64Value() {
      return int64Value;
    }

    public void setInt64Value(long int64Value) {
      this.constantKind = Kind.INT64_VALUE;
      this.int64Value = int64Value;
    }

    NullValue nullValue() {
      return nullValue;
    }

    void setNullValue(NullValue nullValue) {
      this.constantKind = Kind.NULL_VALUE;
      this.nullValue = nullValue;
    }

    UnsignedLong uint64Value() { return uint64Value;
    }

    void setUint64Value(UnsignedLong uint64Value) {
      this.constantKind = Kind.UINT64_VALUE;
      this.uint64Value = uint64Value;
    }

    double doubleValue() {
      return doubleValue;
    }

    void setDoubleValue(double doubleValue) {
      this.constantKind = Kind.DOUBLE_VALUE;
      this.doubleValue = doubleValue;
    }

    String stringValue() {
      return stringValue;
    }

    void setStringValue(String stringValue) {
      this.constantKind = Kind.STRING_VALUE;
      this.stringValue = stringValue;
    }

    ByteString bytesValue() { return bytesValue;
    }

    void setBytesValue(ByteString bytesValue) {
      this.constantKind = Kind.BYTES_VALUE;
      this.bytesValue = bytesValue;
    }

    public static MutableConstant ofValue(NullValue value) {
      return new MutableConstant(value);
    }

    public static MutableConstant ofValue(boolean value) {
      return new MutableConstant(value);
    }

    public static MutableConstant ofValue(long value) {
      return new MutableConstant(value);
    }

    public static MutableConstant ofValue(UnsignedLong value) {
      return new MutableConstant(value);
    }

    public static MutableConstant ofValue(double value) {
      return new MutableConstant(value);
    }

    public static MutableConstant ofValue(String value) {
      return new MutableConstant(value);
    }

    public static MutableConstant ofValue(ByteString value) {
      return new MutableConstant(value);
    }


    MutableConstant(NullValue value) {
      setNullValue(value);
    }

    MutableConstant(boolean value) {
      setBooleanValue(value);
    }

    MutableConstant(long value) {
      setInt64Value(value);
    }

    MutableConstant(UnsignedLong value) {
      setUint64Value(value);
    }

    MutableConstant(double value) {
      setDoubleValue(value);
    }

    MutableConstant(String value) {
      setStringValue(value);
    }

    MutableConstant(ByteString value) {
      setBytesValue(value);
    }
  }

  public final static class MutableIdent {
    private String name;

    public String name() {
      return name;
    }

    void setName(String name) {
      this.name = name;
    }

    public static MutableIdent create(String name) {
      return new MutableIdent(name);
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

    boolean isTestOnly() {
      return testOnly;
    }

    void setTestOnly(boolean testOnly) {
      this.testOnly = testOnly;
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

    void setFunction(String function) {
      this.function = function;
    }

    public List<MutableExpr> args() {
      return args;
    }

    void setArgs(List<MutableExpr> args) {
      this.args = args;
    }

    public void setArg(int index, MutableExpr arg) {
      checkNotNull(arg);
      checkArgument(index >= 0 && index < args.size());
      args.set(index, arg);
    }

    static MutableCall create(String function, List<MutableExpr> args) {
      return new MutableCall(function, args);
    }

    static MutableCall create(MutableExpr mutableExpr, String function, List<MutableExpr> args) {
      return new MutableCall(mutableExpr, function, args);
    }

    private MutableCall(String function, List<MutableExpr> args) {
      this.target = Optional.empty();
      this.function = function;
      this.args = args;
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

    List<Integer> optionalIndices() {
      return optionalIndices;
    }

    void setOptionalIndices(List<Integer> optionalIndices) {
      this.optionalIndices = optionalIndices;
    }

    public static MutableCreateList create(List<MutableExpr> mutableExprList) {
      return new MutableCreateList(mutableExprList, new ArrayList<>());
    }

    static MutableCreateList create(List<MutableExpr> mutableExprList, List<Integer> optionalIndices) {
      return new MutableCreateList(mutableExprList, optionalIndices);
    }

    private MutableCreateList(List<MutableExpr> mutableExprList, List<Integer> optionalIndices) {
      this.elements = mutableExprList;
      this.optionalIndices = optionalIndices;
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

    public void setEntries(
        List<Entry> entries) {
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

      public boolean isOptionalEntry() {
        return optionalEntry;
      }

      public void setOptionalEntry(boolean optionalEntry) {
        this.optionalEntry = optionalEntry;
      }

      static Entry create(long id, String fieldKey, MutableExpr value) {
        return new Entry(id, fieldKey, value, false);
      }

      static Entry create(long id, String fieldKey, MutableExpr value, boolean optionalEntry) {
        return new Entry(id, fieldKey, value, optionalEntry);
      }

      private Entry(long id, String fieldKey, MutableExpr value, boolean optionalEntry) {
        this.id = id;
        this.fieldKey = fieldKey;
        this.value = value;
        this.optionalEntry = optionalEntry;
      }
    }

    static MutableCreateStruct create(String messageName, List<MutableCreateStruct.Entry> entries) {
      return new MutableCreateStruct(messageName, entries);
    }

    private MutableCreateStruct(String messageName, List<MutableCreateStruct.Entry> entries) {
      this.messageName = messageName;
      this.entries = entries;
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

      public boolean isOptionalEntry() {
        return optionalEntry;
      }

      public void setOptionalEntry(boolean optionalEntry) {
        this.optionalEntry = optionalEntry;
      }

      static Entry create(long id, MutableExpr key, MutableExpr value) {
        return new Entry(id, key, value, false);
      }

      static Entry create(long id, MutableExpr key, MutableExpr value, boolean optionalEntry) {
        return new Entry(id, key, value, optionalEntry);
      }

      private Entry(long id, MutableExpr key, MutableExpr value, boolean optionalEntry) {
        this.id = id;
        this.key = key;
        this.value = value;
        this.optionalEntry = optionalEntry;
      }
    }

    static MutableCreateMap create(List<MutableCreateMap.Entry> entries) {
      return new MutableCreateMap(entries);
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

    public String getIterVar() {
      return iterVar;
    }

    public void setIterVar(String iterVar) {
      this.iterVar = iterVar;
    }

    public MutableExpr getIterRange() {
      return iterRange;
    }

    public void setIterRange(MutableExpr iterRange) {
      this.iterRange = iterRange;
    }

    public String getAccuVar() {
      return accuVar;
    }

    public void setAccuVar(String accuVar) {
      this.accuVar = accuVar;
    }

    public MutableExpr getAccuInit() {
      return accuInit;
    }

    public void setAccuInit(MutableExpr accuInit) {
      this.accuInit = accuInit;
    }

    public MutableExpr getLoopCondition() {
      return loopCondition;
    }

    public void setLoopCondition(MutableExpr loopCondition) {
      this.loopCondition = loopCondition;
    }

    public MutableExpr getLoopStep() {
      return loopStep;
    }

    public void setLoopStep(MutableExpr loopStep) {
      this.loopStep = loopStep;
    }

    public MutableExpr getResult() {
      return result;
    }

    public void setResult(MutableExpr result) {
      this.result = result;
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

  public static MutableExpr ofConstant(MutableConstant mutableConstant) {
    return new MutableExpr(0, mutableConstant);
  }

  public static MutableExpr ofConstant(long id, MutableConstant mutableConstant) {
    return new MutableExpr(id, mutableConstant);
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

  private MutableExpr(long id, MutableConstant mutableConstant) {
    this(id);
    setConstant(mutableConstant);
  }

  private MutableExpr(long id, MutableIdent mutableIdent) {
    this(id);
    setIdent(mutableIdent);
  }

  private MutableExpr(long id, MutableCall mutableCall) {
    this(id);
    setCall(mutableCall);
  }

  private MutableExpr(long id, MutableSelect mutableSelect) {
    this(id);
    setSelect(mutableSelect);
  }

  private MutableExpr(long id, MutableCreateList mutableCreateList) {
    this(id);
    setCreateList(mutableCreateList);
  }

  private MutableExpr(long id, MutableCreateStruct mutableCreateStruct) {
    this(id);
    setCreateStruct(mutableCreateStruct);
  }

  private MutableExpr(long id, MutableCreateMap mutableCreateMap) {
    this(id);
    setCreateMap(mutableCreateMap);
  }

  private MutableExpr(long id, MutableComprehension mutableComprehension) {
    this(id);
    setComprehension(mutableComprehension);
  }

  private MutableExpr(long id) {
    this();
    this.id = id;
  }

  private MutableExpr() {
    this.exprKind = ExprKind.Kind.NOT_SET;
  }

  @Override
  public String toString() {
    return MutableExprConverter.fromMutableExpr(this).toString();
  }
}
