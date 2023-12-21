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

  void setId(long id) {
    this.id = id;
  }

  public ExprKind.Kind exprKind() {
    return exprKind;
  }

  public MutableConstant constant() {
    return constant;
  }

  void setConstant(MutableConstant constant) {
    this.exprKind = ExprKind.Kind.CONSTANT;
    this.constant = constant;
  }

  public MutableIdent ident() {
    return ident;
  }

  void setIdent(MutableIdent ident) {
    this.exprKind = ExprKind.Kind.IDENT;
    this.ident = ident;
  }

  public MutableCall call() {
    return call;
  }

  void setCall(MutableCall call) {
    this.exprKind = ExprKind.Kind.CALL;
    this.call = call;
  }

  MutableSelect select() {
    return select;
  }

  void setSelect(MutableSelect select) {
    this.exprKind = ExprKind.Kind.SELECT;
    this.select = select;
  }

  public MutableCreateList createList() {
    return createList;
  }

  void setCreateList(MutableCreateList createList) {
    this.exprKind = ExprKind.Kind.CREATE_LIST;
    this.createList = createList;
  }

  MutableCreateStruct createStruct() {
    return createStruct;
  }

  void setCreateStruct(MutableCreateStruct createStruct) {
    this.exprKind = ExprKind.Kind.CREATE_STRUCT;
    this.createStruct = createStruct;
  }

  MutableCreateMap createMap() {
    return createMap;
  }

  void setCreateMap(MutableCreateMap createMap) {
    this.exprKind = ExprKind.Kind.CREATE_MAP;
    this.createMap = createMap;
  }

  MutableComprehension comprehension() {
    return comprehension;
  }

  void setComprehension(MutableComprehension comprehension) {
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

    static MutableConstant ofValue(NullValue value) {
      return new MutableConstant(value);
    }

    static MutableConstant ofValue(boolean value) {
      return new MutableConstant(value);
    }

    public static MutableConstant ofValue(long value) {
      return new MutableConstant(value);
    }

    static MutableConstant ofValue(UnsignedLong value) {
      return new MutableConstant(value);
    }

    static MutableConstant ofValue(double value) {
      return new MutableConstant(value);
    }

    static MutableConstant ofValue(String value) {
      return new MutableConstant(value);
    }

    static MutableConstant ofValue(ByteString value) {
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

    String Name() {
      return name;
    }

    void setName(String name) {
      this.name = name;
    }

    static MutableIdent create(String name) {
      return new MutableIdent(name);
    }

    private MutableIdent(String name) {
      this.name = name;
    }
  }

  final static class MutableSelect {
    private MutableExpr operand;
    private String field;
    private boolean testOnly;

    MutableExpr operand() {
      return operand;
    }

    void setOperand(MutableExpr operand) {
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

    static MutableSelect create(MutableExpr operand, String field, boolean testOnly) {
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

    Optional<MutableExpr> target() {
      return target;
    }

    void setTarget(MutableExpr target) {
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

  final static class MutableCreateStruct {
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

    final static class Entry {
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

  final static class MutableCreateMap {
    private List<Entry> entries;

    public List<Entry> entries() {
      return entries;
    }

    public void setEntries(List<Entry> entries) {
      this.entries = entries;
    }

    final static class Entry {
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

  final static class MutableComprehension {
    /** The name of the iteration variable. */
    public String iterVar;

    /** The range over which var iterates. */
    public MutableExpr iterRange;

    /** The name of the variable used for accumulation of the result. */
    public String accuVar;

    /** The initial value of the accumulator. */
    public MutableExpr accuInit;

    /**
     * An expression which can contain iter_var and accu_var.
     *
     * <p>Returns false when the result has been computed and may be used as a hint to short-circuit
     * the remainder of the comprehension.
     */
    public MutableExpr loopCondition;

    /**
     * An expression which can contain iter_var and accu_var.
     *
     * <p>Computes the next value of accu_var.
     */
    public MutableExpr loopStep;

    /**
     * An expression which can contain accu_var.
     *
     * <p>Computes the result.
     */
    public MutableExpr result;

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
    return new MutableExpr(mutableConstant);
  }

  static MutableExpr ofIdent(MutableIdent mutableIdent) {
    return new MutableExpr(mutableIdent);
  }

  static MutableExpr ofCall(MutableCall mutableCall) {
    return new MutableExpr(mutableCall);
  }

  static MutableExpr ofSelect(MutableSelect mutableSelect) {
    return new MutableExpr(mutableSelect);
  }

  public static MutableExpr ofCreateList(MutableCreateList mutableCreateList) {
    return new MutableExpr(mutableCreateList);
  }

  static MutableExpr ofCreateStruct(MutableCreateStruct mutableCreateStruct) {
    return new MutableExpr(mutableCreateStruct);
  }

  static MutableExpr ofCreateMap(MutableCreateMap mutableCreateMap) {
    return new MutableExpr(mutableCreateMap);
  }

  static MutableExpr ofComprehension(MutableComprehension mutableComprehension) {
    return new MutableExpr(mutableComprehension);
  }

  static MutableExpr ofNotSet() {
    return new MutableExpr();
  }

  private MutableExpr(MutableConstant mutableConstant) {
    setConstant(mutableConstant);
  }

  private MutableExpr(MutableIdent mutableIdent) {
    setIdent(mutableIdent);
  }

  private MutableExpr(MutableCall mutableCall) {
    setCall(mutableCall);
  }

  private MutableExpr(MutableSelect mutableSelect) {
    setSelect(mutableSelect);
  }

  private MutableExpr(MutableCreateList mutableCreateList) {
    setCreateList(mutableCreateList);
  }

  private MutableExpr(MutableCreateStruct mutableCreateStruct) {
    setCreateStruct(mutableCreateStruct);
  }

  private MutableExpr(MutableCreateMap mutableCreateMap) {
    setCreateMap(mutableCreateMap);
  }

  private MutableExpr(MutableComprehension mutableComprehension) {
    setComprehension(mutableComprehension);
  }

  private MutableExpr() {
    this.exprKind = ExprKind.Kind.NOT_SET;
  }
}
