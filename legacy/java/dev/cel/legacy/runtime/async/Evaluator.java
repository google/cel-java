package dev.cel.legacy.runtime.async;

import static com.google.common.util.concurrent.MoreExecutors.directExecutor;
import static com.google.common.util.concurrent.MoreExecutors.newSequentialExecutor;
import static dev.cel.legacy.runtime.async.Effect.CONTEXT_INDEPENDENT;
import static dev.cel.legacy.runtime.async.EvaluationHelpers.allAsListOrFirstException;
import static dev.cel.legacy.runtime.async.EvaluationHelpers.asBoolean;
import static dev.cel.legacy.runtime.async.EvaluationHelpers.compiledConstantOrThrowing;
import static dev.cel.legacy.runtime.async.EvaluationHelpers.execAllAsList;
import static dev.cel.legacy.runtime.async.EvaluationHelpers.executeStatically;
import static dev.cel.legacy.runtime.async.EvaluationHelpers.expectBoolean;
import static dev.cel.legacy.runtime.async.EvaluationHelpers.immediateException;
import static dev.cel.legacy.runtime.async.EvaluationHelpers.immediateValue;
import static dev.cel.legacy.runtime.async.EvaluationHelpers.transform;

import dev.cel.expr.CheckedExpr;
import dev.cel.expr.Constant;
import dev.cel.expr.Expr;
import dev.cel.expr.Expr.Call;
import dev.cel.expr.Expr.Comprehension;
import dev.cel.expr.Expr.CreateList;
import dev.cel.expr.Expr.CreateStruct;
import dev.cel.expr.Expr.Select;
import dev.cel.expr.Reference;
import dev.cel.expr.Type;
import dev.cel.expr.Type.TypeKindCase;
import dev.cel.expr.Value;
import com.google.auto.value.AutoOneOf;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.primitives.UnsignedLong;
import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.errorprone.annotations.Immutable;
import dev.cel.checker.Types;
import dev.cel.common.CelErrorCode;
import dev.cel.common.CelOptions;
import dev.cel.common.CelProtoAbstractSyntaxTree;
import dev.cel.runtime.DefaultMetadata;
import dev.cel.runtime.InterpreterException;
import dev.cel.runtime.Metadata;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executor;
import javax.annotation.Nullable;

/**
 * Implementation of an optimizing CEL interpreter based on futures.
 *
 * <p>Evaluation is split into two phases: preparation and execution.
 *
 * <p>The preparation phase traverses the abstract syntax, performs optimizations, and produces an
 * {@link AsyncInterpretable}.
 *
 * <p>The execution phase consists of invocations of {@link AsyncInterpretable#evaluate} or {@link
 * AsyncInterpretable#eval}. It is expected that one preparation is amortized over many executions.
 *
 * <p>Optimizations include:
 *
 * <ul>
 *   <li>Elimination of interpretative overhead. During execution the interpretable does not inspect
 *       the abstract syntax anymore. Control flow is directly baked into the interpretable.
 *   <li>Constant-time access to local variables by stack positions.
 *   <li>Compile-time resolution of function bindings from the dispatcher whenever the relevant
 *       overload is determined uniquely by type-checking.
 *   <li>Compile-time evaluation ("constant folding") of any part of the expression that does not
 *       depend on global variables or context-dependent functions. Calls of context-independent
 *       functions with constant arguments are evaluated at compile time.
 *   <li>Compile-time boolean short-circuiting and elimination of unreachable branches when
 *       conditions are known constants (based on the above constant-folding mechanism).
 *   <li>Elimination of code that is unreachable to do static knowledge about thrown exceptions.
 *   <li>Compile-time execution of comprehension loops with statically known inputs, applying
 *       constant-folding, short-circuiting, and elimination of unreachable code along the way.
 * </ul>
 */
public class Evaluator implements AsyncInterpreter {

  private final TypeResolver typeResolver;
  private final MessageProcessor messageProcessor;
  private final FunctionResolver functionResolver;
  private final boolean errorOnDuplicateKeys;
  private final CelOptions celOptions;

  /** Standard constructor. */
  public Evaluator(
      TypeResolver typeResolver,
      MessageProcessor messageProcessor,
      FunctionResolver functionResolver,
      CelOptions celOptions) {
    this.typeResolver = typeResolver;
    this.messageProcessor = messageProcessor;
    this.functionResolver = functionResolver;
    this.celOptions = celOptions;
    this.errorOnDuplicateKeys = this.celOptions.errorOnDuplicateMapKeys();
  }

  @Override
  public AsyncInterpretable createInterpretable(CheckedExpr checkedExpr)
      throws InterpreterException {
    return createInterpretableOrConstant(checkedExpr, ImmutableMap.of(), ImmutableList.of())
        .toInterpretable();
  }

  // This lambda implements @Immutable interface 'AsyncInterpretable', but 'List' is mutable
  @SuppressWarnings("Immutable")
  @Override
  public AsyncInterpretableOrConstant createInterpretableOrConstant(
      CheckedExpr checkedExpr,
      Map<String, Object> compileTimeConstants,
      List<String> localVariables)
      throws InterpreterException {
    int expectedNumLocals = localVariables.size();
    Compilation compilation = new Compilation(checkedExpr);
    CompiledExpression compiled =
        compilation.compiledExpression(compileTimeConstants, localVariables);
    List<String> globalReferences = compilation.globalReferences();
    return compiled.map(
        (e, effect) ->
            AsyncInterpretableOrConstant.interpretable(
                effect,
                new AsyncInterpretableWithFeatures(
                    (gctx, locals) -> {
                      Preconditions.checkArgument(locals.size() == expectedNumLocals);
                      return e.execute(new DynamicEnv(gctx, locals, globalReferences));
                    },
                    celOptions)),
        c -> AsyncInterpretableOrConstant.constant(Optional.ofNullable(c)),
        t ->
            AsyncInterpretableOrConstant.interpretable(
                Effect.CONTEXT_INDEPENDENT,
                new AsyncInterpretableWithFeatures(
                    (gctx, locals) -> immediateException(t), celOptions)));
  }

  /**
   * This {@code AsyncInterpretable} implementation ensures that {@code CelOptions} are propagated
   * to type-conversion classes used during async interpretation.
   */
  @Immutable
  private static class AsyncInterpretableWithFeatures implements AsyncInterpretable {

    private final AsyncInterpretable delegate;
    private final CelOptions celOptions;

    private AsyncInterpretableWithFeatures(AsyncInterpretable delegate, CelOptions celOptions) {
      this.delegate = delegate;
      this.celOptions = celOptions;
    }

    @Override
    public ListenableFuture<Object> evaluate(
        GlobalContext gctx, List<ListenableFuture<Object>> locals) {
      return delegate.evaluate(gctx, locals);
    }

    @Override
    public ListenableFuture<Object> eval(
        AsyncContext context, AsyncGlobalResolver global, List<ListenableFuture<Object>> locals) {
      return evaluate(GlobalContext.of(context, new ResolverAdapter(global, celOptions)), locals);
    }
  }

  /**
   * Represents the binding of a local variable at compile time. A binding can either be a slot
   * number (i.e., a position within the runtime stack) or a concrete value that is known due to
   * constant folding.
   */
  @AutoOneOf(StaticBinding.Kind.class)
  abstract static class StaticBinding {
    enum Kind {
      SLOT, // Stack slots used at runtime.
      BOUND_VALUE // Compile-time known binding to value.  Using Optional to avoid null.
    }

    abstract Kind getKind();

    abstract int slot();

    static StaticBinding slot(int n) {
      return AutoOneOf_Evaluator_StaticBinding.slot(n);
    }

    abstract Optional<Object> boundValue();

    static StaticBinding boundValue(Optional<Object> v) {
      return AutoOneOf_Evaluator_StaticBinding.boundValue(v);
    }

    /** Returns the actual bound value (which may be null). */
    @Nullable
    Object value() {
      return boundValue().orElse(null);
    }

    /** Constructs a value binding directly from the nullable value. */
    static StaticBinding value(@Nullable Object v) {
      return boundValue(Optional.ofNullable(v));
    }
  }

  /** Static environments map variable names to their stack positions or to known values. */
  private class StaticEnv {

    private final Map<String, StaticBinding> bindings;
    private int numSlots;

    /** Creates an empty environment that maps only the given compile-time constants. */
    public StaticEnv(Map<String, Object> compileTimeConstants, List<String> localVariables) {
      this.bindings = new HashMap<>();
      for (Map.Entry<String, Object> c : compileTimeConstants.entrySet()) {
        this.bindings.put(c.getKey(), StaticBinding.value(c.getValue()));
      }
      this.numSlots = 0;
      for (String localVariable : localVariables) {
        this.bindings.put(localVariable, StaticBinding.slot(this.numSlots++));
      }
    }

    /** Clones the current environment. */
    private StaticEnv(StaticEnv parent) {
      this.bindings = new HashMap<>(parent.bindings);
      this.numSlots = parent.numSlots;
    }

    /** Adds a slot binding for the given name. The slot is the next available slot on the stack. */
    private void push(String name) {
      bindings.put(name, StaticBinding.slot(numSlots++));
    }

    /** Adds a value binding for the given name. This does not take up a stack slot at runtime. */
    private void bind(String name, @Nullable Object value) {
      bindings.put(name, StaticBinding.value(value));
    }

    /**
     * Clones the current environment and extends the result with slots for the given names, from
     * left to right.
     */
    public StaticEnv extendWithSlots(String... names) {
      StaticEnv result = new StaticEnv(this);
      for (String name : names) {
        result.push(name);
      }
      return result;
    }

    /** Clones the current environment and binds the given name to the given value in the result. */
    public StaticEnv extendWithValue(String name, @Nullable Object value) {
      StaticEnv result = new StaticEnv(this);
      result.bind(name, value);
      return result;
    }

    /** Determines whether the given name is currently mapped to a binding. */
    public boolean isInScope(String name) {
      return bindings.containsKey(name);
    }

    /** Returns the binding corresponding to the given name, or -1 if it is not in scope. */
    public StaticBinding bindingOf(String name) {
      return bindings.get(name);
    }

    /**
     * Returns the slot number corresponding to the current top of the stack.
     *
     * <p>Let se be the static environment and de be its corresponding dynamic environment. If a
     * binding in se is a slot s, then the corresponding runtime value will be at
     * de.getLocalAtSlotOffset(se.top() - s).
     */
    public int top() {
      return numSlots;
    }

    /**
     * Returns the numeric stack offset for the named local variable or otherwise throws an {@link
     * InterpreterException}.
     */
    public int findStackOffset(@Nullable Metadata metadata, long exprId, String name)
        throws InterpreterException {
      @Nullable StaticBinding binding = bindingOf(name);
      if (binding != null && binding.getKind() == StaticBinding.Kind.SLOT) {
        return top() - binding.slot();
      }
      throw new InterpreterException.Builder("no stack slot named %s", name)
          .setLocation(metadata, exprId)
          .build();
    }
  }

  /**
   * The compilation class encapsulates the compilation step from CEL expressions to {@link
   * ExecutableExpression}s. Creating an object of this class initializes the compiler. A subsequent
   * call of compiledExpression() performs the actual compilation and returns the {@link
   * CompiledExpression}.
   */
  private class Compilation {

    private final CheckedExpr checkedExpr;
    private final Metadata metadata;

    // Accumulates the list of global variables that are referenced by the code.
    private final List<String> globalReferences = new ArrayList<>();

    /** Initialize the compiler by creating the Compilation. */
    Compilation(CheckedExpr checkedExpr) {
      this.checkedExpr = Preconditions.checkNotNull(checkedExpr);
      this.metadata =
          new DefaultMetadata(CelProtoAbstractSyntaxTree.fromCheckedExpr(checkedExpr).getAst());
    }

    /**
     * Run the compiler and produce the compiled expression that corresponds to the constructor
     * argument. Errors during the compilation step itself result in a thrown {@link
     * InterpreterException}.
     */
    public CompiledExpression compiledExpression(
        Map<String, Object> compileTimeConstants, List<String> localVariables)
        throws InterpreterException {
      return enforceCompleteness(
          compile(checkedExpr.getExpr(), new StaticEnv(compileTimeConstants, localVariables)));
    }

    /** Retrieves the list of global variables in index order. */
    public List<String> globalReferences() {
      return globalReferences;
    }

    // Looks up the index of a global variable, registering it if it is not already known.
    private int globalIndex(String name) {
      int index = globalReferences.indexOf(name);
      if (index < 0) {
        index = globalReferences.size();
        globalReferences.add(name);
      }
      return index;
    }

    /** Compiles a general CEL expression relative to the given static environment. */
    private CompiledExpression compile(Expr expr, StaticEnv env) throws InterpreterException {
      switch (expr.getExprKindCase()) {
        case CONST_EXPR:
          return compileConstant(expr.getConstExpr());
        case IDENT_EXPR:
          return compileIdent(expr.getId(), env);
        case SELECT_EXPR:
          return compileSelect(expr.getId(), expr.getSelectExpr(), env);
        case CALL_EXPR:
          return compileCall(expr.getId(), expr.getCallExpr(), env);
        case LIST_EXPR:
          return compileList(expr.getListExpr(), env);
        case STRUCT_EXPR:
          return compileStruct(expr.getId(), expr.getStructExpr(), env);
        case COMPREHENSION_EXPR:
          return compileComprehension(expr.getComprehensionExpr(), env);
        default:
          throw new IllegalArgumentException(
              "unexpected expression kind: " + expr.getExprKindCase());
      }
    }

    /** Evaluates a CEL constant to an Object representing its runtime value. */
    @Nullable
    private Object evalConstant(Constant constExpr) throws InterpreterException {
      switch (constExpr.getConstantKindCase()) {
        case NULL_VALUE:
          return constExpr.getNullValue();
        case BOOL_VALUE:
          return constExpr.getBoolValue();
        case INT64_VALUE:
          return constExpr.getInt64Value();
        case UINT64_VALUE:
          if (celOptions.enableUnsignedLongs()) {
            return UnsignedLong.fromLongBits(constExpr.getUint64Value());
          }
          return constExpr.getUint64Value();
        case DOUBLE_VALUE:
          return constExpr.getDoubleValue();
        case STRING_VALUE:
          return constExpr.getStringValue();
        case BYTES_VALUE:
          return constExpr.getBytesValue();
        default:
          throw new IllegalArgumentException(
              "unsupported constant case: " + constExpr.getConstantKindCase());
      }
    }

    /** Compiles a CEL constant. */
    private CompiledExpression compileConstant(Constant constExpr) throws InterpreterException {
      return CompiledExpression.constant(evalConstant(constExpr));
    }

    /**
     * Compiles a CEL identifier which may be statically bound to a constant or refer to a variable
     * (local or global).
     */
    private CompiledExpression compileIdent(long id, StaticEnv env) throws InterpreterException {
      return compileIdentReference(checkedExpr.getReferenceMapOrThrow(id), id, env);
    }

    /** Compiles a CEL identifier given its corresponding reference. */
    private CompiledExpression compileIdentReference(Reference reference, long id, StaticEnv env)
        throws InterpreterException {
      if (reference.hasValue()) {
        // Bound to a constant.
        return compileConstant(reference.getValue());
      }
      String name = reference.getName();
      // Local or global Variable.
      if (!env.isInScope(name)) {
        // Global.
        return compileGlobalIdent(id, name);
      }
      StaticBinding binding = env.bindingOf(name);
      switch (binding.getKind()) {
        case SLOT:
          {
            int offset = env.top() - binding.slot();
            return CompiledExpression.executable(
                stack -> stack.getLocalAtSlotOffset(offset), Effect.CONTEXT_INDEPENDENT);
          }
        case BOUND_VALUE:
          return CompiledExpression.constant(binding.value());
      }
      throw unexpected("compileIdentReference");
    }

    /**
     * Compiles a CEL identifier that is known to be a global variable. The result contains a
     * runtime check for unbound global variables.
     */
    private CompiledExpression compileGlobalIdent(long id, String name)
        throws InterpreterException {
      // Check whether the type exists in the type check map as a 'type'.
      Type checkedType = checkedExpr.getTypeMapMap().get(id);
      if (checkedType != null && checkedType.getTypeKindCase() == TypeKindCase.TYPE) {
        return resolveTypeIdent(id, checkedType);
      }
      int index = globalIndex(name);
      return CompiledExpression.executable(
          stack -> stack.getGlobal(index), Effect.CONTEXT_DEPENDENT);
    }

    private CompiledExpression resolveTypeIdent(long id, Type type) throws InterpreterException {
      Value typeValue = typeResolver.adaptType(type);
      if (typeValue != null) {
        return CompiledExpression.constant(typeValue);
      }
      throw new InterpreterException.Builder(
              "expected a runtime type for '%s', but found none.", type)
          .setErrorCode(CelErrorCode.TYPE_NOT_FOUND)
          .setLocation(metadata, id)
          .build();
    }

    private boolean hasMapEntryOrMessageField(
        Metadata metadata, long id, Object mapOrMessage, String field) throws InterpreterException {
      if (mapOrMessage instanceof Map) {
        return ((Map<?, ?>) mapOrMessage).containsKey(field);
      }
      return messageProcessor.dynamicHasField(metadata, id, mapOrMessage, field);
    }

    private Object getMapEntryOrMessageField(
        Metadata metadata, long id, Object mapOrMessage, String field) throws InterpreterException {
      if (mapOrMessage instanceof Map) {
        return getMapEntryFromMap(metadata, id, (Map<?, ?>) mapOrMessage, field);
      }
      return messageProcessor.dynamicGetField(metadata, id, mapOrMessage, field);
    }

    /** Compiles a CEL select expression representing a field presence test. */
    // This lambda implements @Immutable interface 'ExecutableExpression', but accesses instance
    // method(s) 'hasMapEntryOrMessageField' on 'Compilation' which is not @Immutable.
    @SuppressWarnings("Immutable")
    private CompiledExpression compileFieldTest(long id, Select selectExpr, StaticEnv env)
        throws InterpreterException {
      String field = selectExpr.getField();
      Expr operand = selectExpr.getOperand();
      Type checkedOperandType = checkedExpr.getTypeMapOrDefault(operand.getId(), Types.DYN);
      CompiledExpression compiledOperand = compile(operand, env);
      if (Types.isDynOrError(checkedOperandType)) {
        // No compile-time type information available, so perform a fully dynamic operation.
        return compiledOperand.mapNonThrowing(
            executableOperand ->
                stack ->
                    executableOperand
                        .execute(stack)
                        .transformAsync(
                            mapOrMessage ->
                                immediateValue(
                                    hasMapEntryOrMessageField(metadata, id, mapOrMessage, field)),
                            directExecutor()),
            constantOperand ->
                compiledConstantOrThrowing(
                    () -> hasMapEntryOrMessageField(metadata, id, constantOperand, field)));
      }
      // Use available compile-time type information.
      switch (checkedOperandType.getTypeKindCase()) {
        case MESSAGE_TYPE:
          String messageType = checkedOperandType.getMessageType();
          MessageProcessor.FieldTester fieldTester =
              messageProcessor.makeFieldTester(metadata, id, messageType, field);
          return compiledOperand.mapNonThrowing(
              executableOperand ->
                  stack ->
                      executableOperand
                          .execute(stack)
                          .transform(fieldTester::hasField, directExecutor()),
              constantOperand ->
                  CompiledExpression.constant(fieldTester.hasField(constantOperand)));
        case MAP_TYPE:
          return compiledOperand.mapNonThrowing(
              executableOperand ->
                  stack ->
                      executableOperand
                          .execute(stack)
                          .transformAsync(
                              mapObject ->
                                  immediateValue(hasMapEntry(metadata, id, mapObject, field)),
                              directExecutor()),
              constantOperand ->
                  compiledConstantOrThrowing(
                      () -> hasMapEntry(metadata, id, constantOperand, field)));
        default:
          throw new InterpreterException.Builder(
                  "[internal] presence test for field '%s' in type %s", field, checkedOperandType)
              .setLocation(metadata, id)
              .build();
      }
    }

    /** Compiles a CEL select expression representing a field access. */
    // This lambda implements @Immutable interface 'ExecutableExpression', but accesses instance
    // method(s) 'getMapEntryOrMessageField' on 'Compilation' which is not @Immutable.
    @SuppressWarnings("Immutable")
    private CompiledExpression compileFieldAccess(long id, Select selectExpr, StaticEnv env)
        throws InterpreterException {
      String field = selectExpr.getField();
      Expr operand = selectExpr.getOperand();
      Type checkedOperandType = checkedExpr.getTypeMapOrDefault(operand.getId(), Types.DYN);
      CompiledExpression compiledOperand = compile(operand, env);
      if (Types.isDynOrError(checkedOperandType)) {
        // No compile-time type information available, so perform a fully dynamic operation.
        return compiledOperand.mapNonThrowing(
            executableOperand ->
                stack ->
                    executableOperand
                        .execute(stack)
                        .transformAsync(
                            mapOrMessage ->
                                immediateValue(
                                    getMapEntryOrMessageField(metadata, id, mapOrMessage, field)),
                            directExecutor()),
            constantOperand ->
                compiledConstantOrThrowing(
                    () -> getMapEntryOrMessageField(metadata, id, constantOperand, field)));
      }
      // Use available compile-time type information.
      switch (checkedOperandType.getTypeKindCase()) {
        case MESSAGE_TYPE:
          String messageType = checkedOperandType.getMessageType();
          MessageProcessor.FieldGetter fieldGetter =
              messageProcessor.makeFieldGetter(metadata, id, messageType, field);
          return compiledOperand.mapNonThrowing(
              executableOperand ->
                  stack ->
                      executableOperand
                          .execute(stack)
                          .transform(fieldGetter::getField, directExecutor()),
              constantOperand ->
                  CompiledExpression.constant(fieldGetter.getField(constantOperand)));
        case MAP_TYPE:
          return compiledOperand.mapNonThrowing(
              executableOperand ->
                  stack ->
                      executableOperand
                          .execute(stack)
                          .transformAsync(
                              mapObject ->
                                  immediateValue(getMapEntry(metadata, id, mapObject, field)),
                              directExecutor()),
              constantOperand ->
                  compiledConstantOrThrowing(
                      () -> getMapEntry(metadata, id, constantOperand, field)));
        default:
          throw new InterpreterException.Builder(
                  "[internal] access to field '%s' in type %s", field, checkedOperandType)
              .setLocation(metadata, id)
              .build();
      }
    }

    /**
     * Compiles a CEL select expression which may be field selection, field presence test, or an
     * access of a variable via a qualified name.
     */
    private CompiledExpression compileSelect(long id, Select selectExpr, StaticEnv env)
        throws InterpreterException {
      Reference reference = checkedExpr.getReferenceMapOrDefault(id, null);
      if (reference != null) {
        return compileIdentReference(reference, id, env);
      } else if (selectExpr.getTestOnly()) {
        return compileFieldTest(id, selectExpr, env);
      } else {
        return compileFieldAccess(id, selectExpr, env);
      }
    }

    /** Compiles a general expression that is to be used as a function argument. */
    // This lambda implements @Immutable interface 'ScopedExpression', but the declaration of type
    // 'dev.cel.legacy.runtime.async.async.Evaluator.StaticEnv' is not
    // annotated with @com.google.errorprone.annotations.Immutable
    @SuppressWarnings("Immutable")
    private IdentifiedCompiledExpression compileFunctionArg(Expr expr, StaticEnv env)
        throws InterpreterException {
      return IdentifiedCompiledExpression.of(
          slots -> enforceCompleteness(compile(expr, env.extendWithSlots(slots))),
          expr.getId(),
          checkedExpr.getTypeMapMap());
    }

    /**
     * Compiles a function call expression.
     *
     * <p>All special cases such as conditionals and logical AND and OR are handled by the {@link
     * FunctionResolver}.
     */
    // This method reference implements @Immutable interface StackOffsetFinder, but the declaration
    // of type 'dev.cel.legacy.runtime.async.async.Evaluator.StaticEnv' is not
    // annotated with @com.google.errorprone.annotations.Immutable
    @SuppressWarnings("Immutable")
    private CompiledExpression compileCall(long id, Call call, StaticEnv env)
        throws InterpreterException {
      Reference reference = checkedExpr.getReferenceMapOrThrow(id);
      Preconditions.checkArgument(reference.getOverloadIdCount() > 0);

      List<IdentifiedCompiledExpression> compiledArguments = new ArrayList<>();
      if (call.hasTarget()) {
        compiledArguments.add(compileFunctionArg(call.getTarget(), env));
      }
      for (Expr arg : call.getArgsList()) {
        compiledArguments.add(compileFunctionArg(arg, env));
      }

      List<String> overloadIds = reference.getOverloadIdList();
      String functionName = call.getFunction();
      return functionResolver
          .constructCall(
              metadata,
              id,
              functionName,
              overloadIds,
              compiledArguments,
              messageProcessor,
              env::findStackOffset)
          .map(
              (executable, effect) ->
                  // If the constructed result is executable but context-independent, and if
                  // this call site is not within scope of a local binding, then run it now.
                  env.top() == 0 && effect == CONTEXT_INDEPENDENT
                      ? executeStatically(executable, globalReferences())
                      : CompiledExpression.executable(executable, effect),
              CompiledExpression::constant,
              CompiledExpression::throwing);
    }

    /** Compiles an expression that is expected to return a boolean. */
    private CompiledExpression compileBoolean(Expr bool, StaticEnv env)
        throws InterpreterException {
      long id = bool.getId();
      return compile(bool, env)
          .mapNonThrowing(
              executableBool ->
                  stack ->
                      executableBool
                          .execute(stack)
                          .transformAsync(
                              b -> immediateValue(expectBoolean(b, metadata, id)),
                              directExecutor()),
              constantBool ->
                  compiledConstantOrThrowing(() -> expectBoolean(constantBool, metadata, id)));
    }

    /** Compiles a CEL list creation. */
    private CompiledExpression compileList(CreateList listExpr, StaticEnv env)
        throws InterpreterException {
      List<CompiledExpression> compiledElements = new ArrayList<>();
      boolean onlyConstant = true;
      Effect effect = Effect.CONTEXT_INDEPENDENT;
      for (Expr e : listExpr.getElementsList()) {
        CompiledExpression compiledElement = enforceCompleteness(compile(e, env));
        if (compiledElement.isThrowing()) {
          return compiledElement;
        }
        onlyConstant = onlyConstant && compiledElement.isConstant();
        effect = effect.meet(compiledElement.effect());
        compiledElements.add(compiledElement);
      }
      if (onlyConstant) {
        return CompiledExpression.constant(transform(compiledElements, e -> e.constant()));
      }
      ImmutableList<ExecutableExpression> executableElements =
          transform(compiledElements, CompiledExpression::toExecutable);
      return CompiledExpression.executable(
          stack ->
              execAllAsList(executableElements, stack)
                  .<Object>transform(list -> list, directExecutor()),
          effect);
    }

    /** Compiles a CEL map or message creation. */
    private CompiledExpression compileStruct(long id, CreateStruct structExpr, StaticEnv env)
        throws InterpreterException {
      Reference reference = checkedExpr.getReferenceMapOrDefault(id, null);
      if (reference == null) {
        return compileMap(structExpr, env);
      } else {
        return compileMessage(id, reference.getName(), structExpr, env);
      }
    }

    /** Compiles a CEL map creation. */
    // This lambda implements @Immutable interface 'ExecutableExpression', but 'IdEntry' has
    // non-final field 'key'
    @SuppressWarnings("Immutable")
    private CompiledExpression compileMap(CreateStruct structExpr, StaticEnv env)
        throws InterpreterException {
      List<IdEntry<CompiledExpression>> compiledEntries = new ArrayList<>();
      boolean onlyConstant = true;
      Effect effect = Effect.CONTEXT_INDEPENDENT;
      boolean hasDynamicKeys = false;
      Set<Object> staticKeys = new HashSet<>();
      for (CreateStruct.Entry entry : structExpr.getEntriesList()) {
        CompiledExpression compiledKey = compile(entry.getMapKey(), env);
        effect = effect.meet(compiledKey.effect());
        if (compiledKey.isThrowing()) {
          return compiledKey;
        }
        if (compiledKey.isConstant()) {
          Object key = compiledKey.constant();
          if (staticKeys.contains(key)) {
            if (errorOnDuplicateKeys) {
              return CompiledExpression.throwing(
                  new InterpreterException.Builder("duplicate map key [%s]", key)
                      .setErrorCode(CelErrorCode.DUPLICATE_ATTRIBUTE)
                      .setLocation(metadata, entry.getId())
                      .build());
            }
          } else {
            staticKeys.add(compiledKey.constant());
          }
        } else {
          hasDynamicKeys = true;
          onlyConstant = false;
        }
        CompiledExpression compiledValue = enforceCompleteness(compile(entry.getValue(), env));
        effect = effect.meet(compiledValue.effect());
        if (compiledValue.isThrowing()) {
          return compiledValue;
        }
        if (!compiledValue.isConstant()) {
          onlyConstant = false;
        }
        compiledEntries.add(new IdEntry<>(entry.getId(), compiledKey, compiledValue));
      }
      if (onlyConstant) {
        Map<Object, Object> map = new LinkedHashMap<>();
        compiledEntries.forEach(entry -> map.put(entry.key.constant(), entry.val.constant()));
        return CompiledExpression.constant(map);
      } else {
        ImmutableList<IdEntry<ExecutableExpression>> executableEntries =
            transform(
                compiledEntries,
                e -> new IdEntry<>(e.id, e.key.toExecutable(), e.val.toExecutable()));
        if (hasDynamicKeys && errorOnDuplicateKeys) {
          return CompiledExpression.executable(
              stack ->
                  FluentFuture.from(
                          allAsListOrFirstException(
                              transform(executableEntries, e -> executeEntry(e, stack))))
                      .transformAsync(
                          entries -> {
                            Map<Object, Object> map = new LinkedHashMap<>();
                            for (IdEntry<Object> entry : entries) {
                              if (map.containsKey(entry.key)) {
                                return immediateException(
                                    new InterpreterException.Builder(
                                            "duplicate map key [%s]", entry.key)
                                        .setErrorCode(CelErrorCode.DUPLICATE_ATTRIBUTE)
                                        .setLocation(metadata, entry.id)
                                        .build());
                              }
                              map.put(entry.key, entry.val);
                            }
                            return immediateValue(map);
                          },
                          directExecutor()),
              effect);
        }
        return CompiledExpression.executable(
            stack ->
                FluentFuture.from(
                        allAsListOrFirstException(
                            transform(executableEntries, e -> executeEntry(e, stack))))
                    .transform(
                        entries -> {
                          Map<Object, Object> map = new LinkedHashMap<>();
                          entries.forEach(entry -> map.put(entry.key, entry.val));
                          return map;
                        },
                        directExecutor()),
            effect);
      }
    }

    /** Compiles a CEL message creation. */
    private CompiledExpression compileMessage(
        long id, String typeName, CreateStruct structExpr, StaticEnv env)
        throws InterpreterException {
      List<String> labels = new ArrayList<>();
      List<Type> types = new ArrayList<>();
      List<CompiledExpression> compiledValues = new ArrayList<>();
      boolean onlyConstant = true;
      Effect effect = Effect.CONTEXT_INDEPENDENT;
      for (CreateStruct.Entry entry : structExpr.getEntriesList()) {
        Expr valueExpr = entry.getValue();
        Type valueType = checkedExpr.getTypeMapOrDefault(valueExpr.getId(), Types.DYN);
        types.add(valueType);
        CompiledExpression compiledValue = enforceCompleteness(compile(entry.getValue(), env));
        effect = effect.meet(compiledValue.effect());
        if (compiledValue.isThrowing()) {
          return compiledValue;
        }
        onlyConstant = onlyConstant && compiledValue.isConstant();
        String fieldName = entry.getFieldKey();
        labels.add(fieldName);
        compiledValues.add(compiledValue);
      }
      MessageProcessor.MessageCreator messageCreator =
          messageProcessor.makeMessageCreator(metadata, id, typeName, labels, types);
      if (onlyConstant) {
        return compiledConstantOrThrowing(
            () -> messageCreator.createMessage(Lists.transform(compiledValues, e -> e.constant())));
      } else {
        ImmutableList<ExecutableExpression> executableValues =
            transform(compiledValues, CompiledExpression::toExecutable);
        return CompiledExpression.executable(
            stack ->
                FluentFuture.from(
                        allAsListOrFirstException(
                            Lists.transform(executableValues, v -> v.execute(stack))))
                    .transformAsync(
                        vl -> immediateValue(messageCreator.createMessage(vl)), directExecutor()),
            effect);
      }
    }

    /**
     * Represents the mechanism used to compile comprehensions.
     *
     * <p>Compilation consists of a number of mutually recursive methods that collectively implement
     * compile-time unrolling of comprehensions when the range is statically known. These methods
     * all need to have access to the same underlying set of values (= the parts that make up the
     * original comprehension expression). To avoid having to pass these parts around as explicit
     * arguments they are made universally available as instance variables.
     */
    class ComprehensionCompilation {
      private final Expr rangeExpr;
      private final Expr initExpr;
      private final long iterId;
      private final String accuVar;
      private final String iterVar;
      private final Expr conditionExpr;
      private final Expr stepExpr;
      private final Expr resultExpr;
      private final StaticEnv parentEnv;

      /** Initializes the comprehension compiler. */
      ComprehensionCompilation(Comprehension comprehension, StaticEnv parentEnv) {
        this.rangeExpr = comprehension.getIterRange();
        this.initExpr = comprehension.getAccuInit();
        this.iterId = rangeExpr.getId();
        this.accuVar = comprehension.getAccuVar();
        this.iterVar = comprehension.getIterVar();
        this.conditionExpr = comprehension.getLoopCondition();
        this.stepExpr = comprehension.getLoopStep();
        this.resultExpr = comprehension.getResult();
        this.parentEnv = parentEnv;
      }

      /** Runs the comprehension compiler for the comprehesion that it was instantiated for. */
      public CompiledExpression compileComprehension() throws InterpreterException {
        CompiledExpression compiledRange = compile(rangeExpr, parentEnv);
        CompiledExpression compiledInit = compile(initExpr, parentEnv);

        /**
         * Exceptions in range or init lead to immediate failure and never let the loop run at all.
         */
        if (compiledRange.isThrowing()) {
          return compiledRange;
        }

        if (compiledRange.isConstant()) {
          /** The range is constant, so run the loop at compile time as things stay constant. */
          Collection<Object> rangeIterable = null;
          try {
            rangeIterable = getRangeIterable(compiledRange.constant(), metadata, iterId);
          } catch (Exception e) {
            // Range is illegal, so the entire expression throws an exception.
            return CompiledExpression.throwing(e);
          }
          return constantRangeLoop(rangeIterable.iterator(), compiledInit);
        } else {
          /** Range is not constant. Generate runtime loop. */
          return compiledRuntimeLoop(compiledRange, compiledInit);
        }
      }

      /**
       * Arranges for the rest of a comprehension loop over a non-empty constant range to be
       * performed at runtime.
       */
      private CompiledExpression constantRangeLoopTail(
          ImmutableList<Object> remainingRange, CompiledExpression compiledAccu)
          throws InterpreterException {
        return compiledRuntimeLoop(CompiledExpression.constant(remainingRange), compiledAccu);
      }

      /**
       * Executes a comprehension loop over a constant range at compile time as far as everything
       * remains static and potentially defers the remaining loop until runtime.
       */
      private CompiledExpression constantRangeLoop(
          Iterator<Object> range, CompiledExpression compiledAccu) throws InterpreterException {
        while (range.hasNext()) {
          if (!compiledAccu.isConstant()) {
            return constantRangeLoopTail(ImmutableList.copyOf(range), compiledAccu);
          }
          @Nullable Object nextValue = range.next();
          StaticEnv loopEnv =
              parentEnv
                  .extendWithValue(accuVar, compiledAccu.constant())
                  .extendWithValue(iterVar, nextValue);
          CompiledExpression compiledCondition = compileBoolean(conditionExpr, loopEnv);
          if (compiledCondition.isThrowing()) {
            return compiledCondition;
          }
          if (!compiledCondition.isConstant()) {
            // The condition is dynamic, so let constantRangeLoopTail handle everything
            // (including the condition itself) by pushing nextValue back into the remaining
            // range.
            return constantRangeLoopTail(
                ImmutableList.builder().add(nextValue).addAll(range).build(), compiledAccu);
          }
          if (!asBoolean(compiledCondition.constant())) {
            break;
          }
          compiledAccu = compile(stepExpr, loopEnv);
        }
        // Reached the end of the loop, so bind the accumulator to its variable and
        // compile the result expression in the corresponding scope.
        return compiledAccu.map(
            (executableAccu, accuEffect) ->
                compile(resultExpr, parentEnv.extendWithSlots(accuVar))
                    .map(
                        (executableResult, resultEffect) ->
                            CompiledExpression.executable(
                                stack ->
                                    executableResult.execute(
                                        stack.extend(executableAccu.execute(stack))),
                                accuEffect.meet(resultEffect)),
                        CompiledExpression::constant,
                        CompiledExpression::throwing),
            constantAccu -> compile(resultExpr, parentEnv.extendWithValue(accuVar, constantAccu)),
            CompiledExpression::throwing);
      }

      /** Generates the runtime loop when compile-time unrolling is not feasible. */
      private CompiledExpression compiledRuntimeLoop(
          CompiledExpression compiledRange, CompiledExpression compiledInit)
          throws InterpreterException {
        StaticEnv resultEnv = parentEnv.extendWithSlots(accuVar);
        StaticEnv loopEnv = resultEnv.extendWithSlots(iterVar);

        CompiledExpression compiledCondition = compileBoolean(conditionExpr, loopEnv);
        CompiledExpression compiledStep = compile(stepExpr, loopEnv);
        CompiledExpression compiledResult = compile(resultExpr, resultEnv);

        Effect effect =
            compiledRange
                .effect()
                .meet(compiledInit.effect())
                .meet(compiledCondition.effect())
                .meet(compiledStep.effect())
                .meet(compiledResult.effect());

        ExecutableExpression executableCondition = compiledCondition.toExecutable();
        ExecutableExpression executableStep = compiledStep.toExecutable();
        ExecutableExpression executableResult = compiledResult.toExecutable();
        ExecutableExpression executableRange = compiledRange.toExecutable();
        ExecutableExpression executableInit = compiledInit.toExecutable();

        // Calculate the range and the initial value, then construct the range iteration and apply
        // it to the initial value.
        return CompiledExpression.executable(
            stack ->
                executableRange
                    .execute(stack)
                    .transformAsync(
                        iterRangeRaw ->
                            new RangeIteration(
                                    getRangeIterable(iterRangeRaw, metadata, iterId).iterator(),
                                    executableResult,
                                    executableCondition,
                                    executableStep,
                                    stack)
                                .iterate(executableInit.execute(stack)),
                        directExecutor()),
            effect);
      }
    }

    /** Compiles a CEL comprehension expression. */
    private CompiledExpression compileComprehension(Comprehension comprehension, StaticEnv env)
        throws InterpreterException {
      // Initialize the comprehension compiler class and then run it.
      return new ComprehensionCompilation(comprehension, env).compileComprehension();
    }
  }

  // Private helper functions.

  // Compile-time matters.

  /**
   * Wraps an excecutable expression with a runtime check that guards against {@link
   * IncompleteData}.
   */
  // This lambda implements @Immutable interface 'ExecutableExpression', but the declaration of type
  // 'dev.cel.runtime.InterpreterException' is not annotated with
  // @com.google.errorprone.annotations.Immutable
  @SuppressWarnings("Immutable")
  private static CompiledExpression enforceCompleteness(CompiledExpression compiled) {
    return compiled.mapNonThrowing(
        executable ->
            stack ->
                executable
                    .execute(stack)
                    .transformAsync(EvaluationHelpers::immediateValue, directExecutor()),
        CompiledExpression::constant);
  }

  // Runtime matters.

  /**
   * Runs the computations for the key/value executable expressions and constructs a future that
   * delivers the corresponding values, plus the id.
   */
  private static FluentFuture<IdEntry<Object>> executeEntry(
      IdEntry<ExecutableExpression> entry, DynamicEnv stack) {
    return FluentFuture.from(
            allAsListOrFirstException(
                ImmutableList.of(entry.key.execute(stack), entry.val.execute(stack))))
        .transform(
            list -> new IdEntry<Object>(entry.id, list.get(0), list.get(1)), directExecutor());
  }

  /**
   * Obtains (at runtime) the iteration range of a comprehension by inspecting the runtime type of
   * the result of evaluating the range expression.
   */
  @SuppressWarnings("unchecked")
  private static Collection<Object> getRangeIterable(
      Object iterRangeRaw, Metadata metadata, long id) throws InterpreterException {
    if (iterRangeRaw instanceof List) {
      return (List<Object>) iterRangeRaw;
    } else if (iterRangeRaw instanceof Map) {
      return ((Map<Object, Object>) iterRangeRaw).keySet();
    } else {
      throw new InterpreterException.Builder(
              "expected a list or a map for iteration range but got '%s'",
              iterRangeRaw.getClass().getSimpleName())
          .setErrorCode(CelErrorCode.INVALID_ARGUMENT)
          .setLocation(metadata, id)
          .build();
    }
  }

  /**
   * Implements the runtime iteration of the body (condition and step) of a comprehension over its
   * range by constructing a recursive function from initial accu value to the future producing the
   * final result.
   *
   * <p>Since exclusive use of {@code directExecutor()} would cause Java stack overruns for very
   * long iterations, direct execution is broken up in regular intervals.
   */
  private static class RangeIteration {
    private final Iterator<Object> range;
    private final ExecutableExpression executableResult;
    private final ExecutableExpression executableCondition;
    private final ExecutableExpression executableStep;
    private final DynamicEnv stack;
    private final Executor trampoline;

    public RangeIteration(
        Iterator<Object> range,
        ExecutableExpression executableResult,
        ExecutableExpression executableCondition,
        ExecutableExpression executableStep,
        DynamicEnv stack) {
      this.range = range;
      this.executableResult = executableResult;
      this.executableCondition = executableCondition;
      this.executableStep = executableStep;
      this.stack = stack;
      // Using directExecutor() throughout would lead to very deep recursion depths
      // and ultimately to Java stack exhaustion when iterating over a very long list.
      // Therefore, the loop is broken up by using a sequential executor every
      // DIRECT_EXECUTION_BUDGET iterations.  A seqential executor acts as a trampoline,
      // thus avoiding unlimited recursion depth.
      // If the original executor is not the direct executor, then using it directly
      // as a the trampoline is potentially better (because it resets the stack depth to
      // the toplevel rather than just to the start of the comprehesion loop).
      this.trampoline =
          stack.currentContext().executor() == directExecutor()
              ? newSequentialExecutor(directExecutor())
              : stack.currentContext().executor();
    }

    public FluentFuture<Object> iterate(FluentFuture<Object> accuFuture) {
      // By starting with zero budget the trampoline is initialized right at the
      // beginning of the loop.
      return loop(accuFuture, 0);
    }

    private FluentFuture<Object> loop(FluentFuture<Object> accuFuture, int budget) {
      if (!range.hasNext()) {
        return executableResult.execute(stack.extend(accuFuture));
      }
      Object elem = range.next();
      DynamicEnv loopStack = stack.extend(accuFuture, immediateValue(elem));
      // Break direct execution up every once in a while to avoid running
      // out of Java stack in case of very long iteration ranges.
      boolean budgetExhausted = budget <= 0;
      Executor executor = budgetExhausted ? trampoline : directExecutor();
      int newBudget = budgetExhausted ? DIRECT_EXECUTION_BUDGET : (budget - 1);
      return executableCondition
          .execute(loopStack)
          .transformAsync(
              condition ->
                  asBoolean(condition)
                      ? loop(
                          // Applies trampoline at the loop step to avoid building up a deeply
                          // nested pending computation in the accumulator.
                          executableStep.execute(loopStack).transform(a -> a, executor), newBudget)
                      : executableResult.execute(stack.extend(accuFuture)),
              executor);
    }
  }

  // Miscellaneous.

  /**
   * Creates a dummy exception to be thrown in places where we don't expect control to reach (but
   * the Java compiler is not smart enough to know that).
   */
  private static RuntimeException unexpected(String where) {
    return new RuntimeException("[internal] reached unexpected program point: " + where);
  }

  private static Map<?, ?> expectMap(Metadata metadata, long id, Object mapObject)
      throws InterpreterException {
    if (mapObject instanceof Map) {
      return (Map<?, ?>) mapObject;
    }
    throw new InterpreterException.Builder(
            "[internal] Expected an instance of 'Map<?, ?>' but found '%s'",
            mapObject.getClass().getName())
        .setLocation(metadata, id)
        .build();
  }

  private static boolean hasMapEntry(Metadata metadata, long id, Object mapObject, String field)
      throws InterpreterException {
    return expectMap(metadata, id, mapObject).containsKey(field);
  }

  private static Object getMapEntryFromMap(Metadata metadata, long id, Map<?, ?> map, String field)
      throws InterpreterException {
    Object entry = map.get(field);
    if (entry == null) {
      throw new InterpreterException.Builder("key '%s' is not present in map.", field)
          .setErrorCode(CelErrorCode.ATTRIBUTE_NOT_FOUND)
          .setLocation(metadata, id)
          .build();
    }
    return entry;
  }

  private static Object getMapEntry(Metadata metadata, long id, Object mapObject, String field)
      throws InterpreterException {
    return getMapEntryFromMap(metadata, id, expectMap(metadata, id, mapObject), field);
  }

  /** Like Map.Entry, but key and value are the same type, and associates an ID with the entry. */
  private static class IdEntry<T> {
    long id;
    T key;
    T val;

    IdEntry(long id, T key, T val) {
      this.id = id;
      this.key = key;
      this.val = val;
    }
  }

  // Breaks up direct execution during comprehensions 5% of the time.
  private static final int DIRECT_EXECUTION_BUDGET = 20;
}
