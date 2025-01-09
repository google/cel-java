package dev.cel.legacy.runtime.async;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.util.concurrent.Futures.immediateFailedFuture;
import static com.google.common.util.concurrent.Futures.immediateFuture;
import static com.google.common.util.concurrent.MoreExecutors.directExecutor;
import static dev.cel.common.types.CelProtoTypes.createMessage;
import static dev.cel.legacy.runtime.async.Effect.CONTEXT_DEPENDENT;
import static dev.cel.legacy.runtime.async.Effect.CONTEXT_INDEPENDENT;
import static dev.cel.legacy.runtime.async.EvaluationHelpers.immediateValue;

import dev.cel.expr.Type;
import com.google.common.collect.ImmutableList;
import com.google.common.context.Context;
import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import com.google.protobuf.ByteString;
import com.google.protobuf.Descriptors.FileDescriptor;
// import com.google.testing.testsize.MediumTest;
import dev.cel.common.CelAbstractSyntaxTree;
import dev.cel.common.types.CelProtoTypes;
import dev.cel.expr.conformance.proto3.TestAllTypes;
import dev.cel.legacy.runtime.async.MessageProcessor.FieldAssigner;
import dev.cel.legacy.runtime.async.MessageProcessor.MessageBuilderCreator;
import dev.cel.runtime.Activation;
import dev.cel.runtime.InterpreterException;
import dev.cel.testing.CelBaselineTestCase;
import dev.cel.testing.testdata.proto3.StandaloneGlobalEnum;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.LongStream;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

/** Tests for {@link FuturesInterpreter} and related functionality. */
// @MediumTest
@RunWith(Parameterized.class)
public class FuturesInterpreterTest extends CelBaselineTestCase {

  private final EvalAsync evalAsync;
  private final AsyncDispatcher dispatcher;

  @Rule public TestName interpreterTestName = new TestName();

  private static final ImmutableList<FileDescriptor> TEST_FILE_DESCRIPTORS =
      ImmutableList.of(
          TestAllTypes.getDescriptor().getFile(), StandaloneGlobalEnum.getDescriptor().getFile());

  // EvalSync and Async are mutable by design (Ex: adding function to the dispatcher). This has been
  // overridden to make the test cases descriptive, as mutability is not a core concern of these
  // tests.
  @SuppressWarnings("ImmutableEnumChecker")
  private enum EvalTestCase {
    ASYNC_PROTO_TYPE_PARSER(false, () -> new EvalAsync(TEST_FILE_DESCRIPTORS, TEST_OPTIONS)),
    ASYNC_PROTO_TYPE_DIRECTED_PROCESSOR_PARSER(
        false,
        () ->
            new EvalAsync(TEST_FILE_DESCRIPTORS, TEST_OPTIONS, /* typeDirectedProcessor= */ true)),
    ASYNC_CEL_TYPE_PARSER(true, () -> new EvalAsync(TEST_FILE_DESCRIPTORS, TEST_OPTIONS)),
    ASYNC_CEL_TYPE_DIRECTED_PROCESSOR_PARSER(
        true,
        () ->
            new EvalAsync(TEST_FILE_DESCRIPTORS, TEST_OPTIONS, /* typeDirectedProcessor= */ true));

    private final boolean declareWithCelType;
    private final Supplier<EvalAsync> eval;

    EvalTestCase(boolean declareWithCelType, Supplier<EvalAsync> eval) {
      this.declareWithCelType = declareWithCelType;
      this.eval = eval;
    }
  }

  @Parameters()
  public static ImmutableList<EvalTestCase> evalTestCases() {
    return ImmutableList.copyOf(EvalTestCase.values());
  }

  public FuturesInterpreterTest(EvalTestCase testCase) {
    super(testCase.declareWithCelType);
    this.evalAsync = testCase.eval.get();
    this.dispatcher = (AsyncDispatcher) evalAsync.registrar();
  }

  /** Helper to run a test for configured instance variables. */
  private void runTest(Activation activation, AsyncDispatcher localDispatcher) throws Exception {
    CelAbstractSyntaxTree ast = prepareTest(evalAsync.fileDescriptors());
    if (ast == null) {
      return;
    }
    EvalAsync evalAsyncLocal = evalAsync.withDispatcher(localDispatcher);
    testOutput().println("bindings: " + activation);
    Object result;
    try {
      result = evalAsyncLocal.eval(ast, activation);
      if (result instanceof ByteString) {
        // Note: this call may fail for printing byte sequences that are not valid UTF-8, but works
        // pretty well for test purposes.
        result = ((ByteString) result).toStringUtf8();
      }
      testOutput().println("result:   " + result);
    } catch (InterpreterException e) {
      testOutput().println("error:    " + e.getMessage());
    }
    testOutput().println();
  }

  private void runTest(Activation activation) throws Exception {
    runTest(activation, dispatcher);
  }

  @Test
  public void nobarrierFunction() throws Exception {
    declareFunction(
        "F",
        globalOverload(
            "F",
            ImmutableList.of(CelProtoTypes.BOOL, CelProtoTypes.INT64, CelProtoTypes.INT64),
            CelProtoTypes.INT64));
    dispatcher.addNobarrierAsync(
        "F",
        CONTEXT_INDEPENDENT,
        (ign, args) ->
            FluentFuture.from(args.get(0))
                .transformAsync(b -> ((boolean) b) ? args.get(1) : args.get(2), directExecutor()));

    source = "F(true, 2, 1 / 0)"; // Failing argument does not matter, so overall success.
    runTest(Activation.EMPTY);

    source = "F(false, 3, 1 / 0)"; // Failing argument matters.
    runTest(Activation.EMPTY);
  }

  @Test
  public void dispatcherSnapshot() throws Exception {
    AsyncDispatcher orig = DefaultAsyncDispatcher.create(TEST_OPTIONS);

    AsyncDispatcher snapshot = orig.fork();
    // Function added after snapshot is taken is expected to not impact result.
    orig.add("just_undefined", String.class, x -> "was defined after all: " + x);

    declareFunction(
        "just_undefined",
        globalOverload(
            "just_undefined", ImmutableList.of(CelProtoTypes.STRING), CelProtoTypes.STRING));

    // No definition in snapshot, so this fails with an unbound overload.
    source = "just_undefined(\"hi there \") == \"foo\"";
    runTest(Activation.EMPTY, snapshot);
  }

  // This lambda implements @Immutable interface 'StrictUnaryFunction', but the declaration of type
  // 'com.google.common.util.concurrent.SettableFuture<java.lang.Object>' is not annotated with
  // @com.google.errorprone.annotations.Immutable
  @SuppressWarnings("Immutable")
  @Test
  public void longComprehension() throws Exception {
    long upper = 1000L;
    Type listType = CelProtoTypes.createList(CelProtoTypes.INT64);
    ImmutableList<Long> l = LongStream.range(0L, upper).boxed().collect(toImmutableList());
    dispatcher.add("constantLongList", ImmutableList.of(), args -> l);

    // Comprehension over compile-time constant long list.
    declareFunction(
        "constantLongList", globalOverload("constantLongList", ImmutableList.of(), listType));
    source = "size(constantLongList().map(x, x+1)) == 1000";
    runTest(Activation.EMPTY);

    // Comprehension over long list that is not compile-time constant.
    declareVariable("longlist", CelProtoTypes.createList(CelProtoTypes.INT64));
    source = "size(longlist.map(x, x+1)) == 1000";
    runTest(Activation.of("longlist", l));

    // Comprehension over long list where the computation is very slow.
    SettableFuture<Object> firstFuture = SettableFuture.create();
    dispatcher.addDirect(
        "f_slow_inc",
        Long.class,
        CONTEXT_INDEPENDENT,
        n -> {
          if (n == 0) {
            // stall on the first element
            return firstFuture;
          }
          return immediateValue(n + 1L);
        });
    dispatcher.addNobarrierAsync(
        "f_unleash",
        CONTEXT_INDEPENDENT,
        (gctx, args) -> {
          firstFuture.set(1L);
          return args.get(0);
        });
    declareFunction(
        "f_slow_inc",
        globalOverload("f_slow_inc", ImmutableList.of(CelProtoTypes.INT64), CelProtoTypes.INT64));
    declareFunction(
        "f_unleash",
        globalOverload(
            "f_unleash",
            ImmutableList.of(CelProtoTypes.createTypeParam("A")),
            ImmutableList.of("A"),
            CelProtoTypes.createTypeParam("A")));
    source = "f_unleash(longlist.map(x, f_slow_inc(x)))[0] == 1";

    runTest(Activation.of("longlist", l));
  }

  @Test
  public void functionRegistrar() throws Exception {
    // This test case exercises every registration method of FunctionRegistrar and then
    // invokes each of the registered methods in such a way that both the constant-fold path
    // and the regular non-constant-fold path is invoked.
    dispatcher.addCallConstructor(
        "f_constructor", (md, id, args) -> CompiledExpression.constant("constructed!"));
    dispatcher.addStrictFunction(
        "f_strict",
        ImmutableList.of(String.class, Long.class),
        true,
        (gctx, args) -> immediateValue("strict: " + (String) args.get(0) + (Long) args.get(1)));
    dispatcher.addDirect(
        "f_directN",
        ImmutableList.of(String.class, Boolean.class),
        CONTEXT_INDEPENDENT,
        (gctx, args) -> immediateValue("directN: " + (String) args.get(0) + (Boolean) args.get(1)));
    dispatcher.addDirect(
        "f_direct",
        CONTEXT_INDEPENDENT,
        (gctx, args) -> immediateValue("direct: " + (Boolean) args.get(0) + (Long) args.get(1)));
    dispatcher.addDirect(
        "f_direct1", String.class, CONTEXT_INDEPENDENT, s -> immediateValue("direct1:" + s));
    dispatcher.addDirect(
        "f_direct2",
        String.class,
        Long.class,
        CONTEXT_INDEPENDENT,
        (s, i) -> immediateValue("direct2: " + s + i));
    dispatcher.addAsync(
        "f_asyncN",
        ImmutableList.of(String.class, Boolean.class),
        CONTEXT_INDEPENDENT,
        (gctx, args) -> immediateValue("asyncN: " + (String) args.get(0) + (Boolean) args.get(1)));
    dispatcher.addAsync(
        "f_async",
        CONTEXT_INDEPENDENT,
        (gctx, args) -> immediateValue("async: " + (Boolean) args.get(0) + (Long) args.get(1)));
    dispatcher.addAsync(
        "f_async1", String.class, CONTEXT_INDEPENDENT, s -> immediateValue("async1:" + s));
    dispatcher.addAsync(
        "f_async2",
        String.class,
        Long.class,
        CONTEXT_INDEPENDENT,
        (s, i) -> immediateValue("async2: " + s + i));
    dispatcher.addDirect(
        "f_effect", String.class, CONTEXT_DEPENDENT, s -> immediateValue("effective: " + s));
    dispatcher.addNobarrierAsync(
        "f_nobarrier",
        CONTEXT_INDEPENDENT,
        (gctx, futures) ->
            Futures.transform(futures.get(0), x -> "nobarrier: " + (String) x, directExecutor()));
    dispatcher.add("f_simple1", String.class, s -> "simple1: " + s);
    dispatcher.add("f_simple2", String.class, String.class, (x, y) -> "simple2: " + x + "@" + y);
    dispatcher.add(
        "f_simpleN",
        ImmutableList.of(String.class, Long.class, Long.class),
        args -> "simpleN: " + (String) args[0] + (Long) args[1] + (Long) args[2]);

    // A dynamic value that cannot be constant-folded away.
    declareVariable("dynamic", CelProtoTypes.STRING);

    declareGlobalFunction("f_constructor", ImmutableList.of(), CelProtoTypes.STRING);
    declareGlobalFunction(
        "f_strict",
        ImmutableList.of(CelProtoTypes.STRING, CelProtoTypes.INT64),
        CelProtoTypes.STRING);
    declareGlobalFunction(
        "f_directN",
        ImmutableList.of(CelProtoTypes.STRING, CelProtoTypes.BOOL),
        CelProtoTypes.STRING);
    declareGlobalFunction(
        "f_direct",
        ImmutableList.of(CelProtoTypes.BOOL, CelProtoTypes.INT64),
        CelProtoTypes.STRING);
    declareGlobalFunction(
        "f_direct1", ImmutableList.of(CelProtoTypes.STRING), CelProtoTypes.STRING);
    declareGlobalFunction(
        "f_direct2",
        ImmutableList.of(CelProtoTypes.STRING, CelProtoTypes.INT64),
        CelProtoTypes.STRING);
    declareGlobalFunction(
        "f_asyncN",
        ImmutableList.of(CelProtoTypes.STRING, CelProtoTypes.BOOL),
        CelProtoTypes.STRING);
    declareGlobalFunction(
        "f_async", ImmutableList.of(CelProtoTypes.BOOL, CelProtoTypes.INT64), CelProtoTypes.STRING);
    declareGlobalFunction("f_async1", ImmutableList.of(CelProtoTypes.STRING), CelProtoTypes.STRING);
    declareGlobalFunction(
        "f_async2",
        ImmutableList.of(CelProtoTypes.STRING, CelProtoTypes.INT64),
        CelProtoTypes.STRING);
    declareGlobalFunction("f_effect", ImmutableList.of(CelProtoTypes.STRING), CelProtoTypes.STRING);
    declareGlobalFunction(
        "f_nobarrier", ImmutableList.of(CelProtoTypes.STRING), CelProtoTypes.STRING);
    declareGlobalFunction(
        "f_simple1", ImmutableList.of(CelProtoTypes.STRING), CelProtoTypes.STRING);
    declareGlobalFunction(
        "f_simple2",
        ImmutableList.of(CelProtoTypes.STRING, CelProtoTypes.STRING),
        CelProtoTypes.STRING);
    declareGlobalFunction(
        "f_simpleN",
        ImmutableList.of(CelProtoTypes.STRING, CelProtoTypes.INT64, CelProtoTypes.INT64),
        CelProtoTypes.STRING);

    source =
        "f_constructor() + \"\\n\" + "
            + "f_strict(\"static\", 13) + \" \" + f_strict(dynamic, 14) + \"\\n\" + "
            + "f_directN(\"static\", true) + \" \" + f_directN(dynamic, false) + \"\\n\" + "
            + "f_direct(true, 20) + \" \" + f_direct(dynamic == \"foo\", 21) + \"\\n\" + "
            + "f_direct1(\"static\") + \" \" + f_direct1(dynamic) + \"\\n\" + "
            + "f_direct2(\"static\", 30) + \" \" + f_direct2(dynamic, 31) + \"\\n\" + "
            + "f_asyncN(\"static\", true) + \" \" + f_asyncN(dynamic, false) + \"\\n\" + "
            + "f_async(true, 20) + \" \" + f_async(dynamic == \"foo\", 21) + \"\\n\" + "
            + "f_async1(\"static\") + \" \" + f_async1(dynamic) + \"\\n\" + "
            + "f_async2(\"static\", 30) + \" \" + f_async2(dynamic, 31) + \"\\n\" + "
            + "f_effect(\"static\") + \" \" + f_effect(dynamic) + \"\\n\" + "
            + "f_nobarrier(\"static\") + \" \" + f_nobarrier(dynamic) + \"\\n\" + "
            + "f_simple1(\"static\") + \" \" + f_simple1(dynamic) + \"\\n\" + "
            + "f_simple2(\"static\", \"foo\") + \" \" + f_simple2(dynamic, \"bar\") + \"\\n\" + "
            + "f_simpleN(\"static\", 54, 32) + \" \" + f_simpleN(dynamic, 98,76)";

    runTest(Activation.of("dynamic", "dynamic"));
  }

  @Test
  public void contextPropagation() throws Exception {
    dispatcher.addAsync(
        "f",
        ImmutableList.of(),
        CONTEXT_DEPENDENT,
        (gctx, args) ->
            immediateValue(
                Context.getCurrentContext().getSecurityContext().getLoggablePeer().getUsername()));
    declareGlobalFunction("f", ImmutableList.of(), CelProtoTypes.STRING);
    source = "f()";
    runTest(Activation.EMPTY);
  }

  @Test
  public void delayedEvaluation() throws Exception {
    dispatcher.addCallConstructor(
        "f_delay",
        (md, id, args) ->
            args.get(0)
                .expression()
                .map(
                    (e, effect) ->
                        CompiledExpression.executable(
                            stack ->
                                FluentFuture.from(
                                    immediateFuture(
                                        Delayed.of(
                                            gctx -> e.execute(stack.withGlobalContext(gctx))))),
                            CONTEXT_INDEPENDENT),
                    c -> CompiledExpression.constant(Delayed.fromFuture(immediateFuture(c))),
                    t ->
                        CompiledExpression.constant(Delayed.fromFuture(immediateFailedFuture(t)))));
    dispatcher.addDirect(
        "f_force",
        ImmutableList.of(Delayed.class),
        CONTEXT_DEPENDENT,
        (gctx, args) -> ((Delayed) args.get(0)).force(gctx));

    declareFunction(
        "f_delay",
        globalOverload("f_delay", ImmutableList.of(CelProtoTypes.INT64), CelProtoTypes.DYN));
    declareFunction(
        "f_force",
        globalOverload("f_force", ImmutableList.of(CelProtoTypes.DYN), CelProtoTypes.INT64));

    // Delayed computation is just a constant.
    source = "f_force(f_delay(1 + 2)) == 3";
    runTest(Activation.EMPTY);

    // A dynamic (global) input.
    declareVariable("four", CelProtoTypes.INT64);

    // Delayed computation depends on global state.
    source = "f_force(f_delay(1 + four)) == 5";
    runTest(Activation.of("four", 4L));

    // Delayed computation occurs within local scope and captures the value of a local variable.
    source = "[1, 2, 3].map(i, f_delay(i + four)).map(d, f_force(d)) == [5, 6, 7]";
    runTest(Activation.of("four", 4L));
  }

  @Test
  public void stashedLocal() throws Exception {
    // Executes its argument within a stack that has been extended with a slot for %stash%.
    // (The value stored there is 123.)
    dispatcher.addCallConstructor(
        "f_stash",
        (md, id, args) -> {
          ExecutableExpression executable =
              args.get(0).scopedExpression().inScopeOf("%stash%").toExecutable();
          return CompiledExpression.executable(
              stack -> executable.execute(stack.extend(immediateValue(123L))), CONTEXT_DEPENDENT);
        });
    // Refers to the value at the stack offset that corresponds to %stash%.
    dispatcher.addCallConstructor(
        "f_grab",
        (md, id, args, offsetFinder) -> {
          int offset = offsetFinder.findStackOffset(md, id, "%stash%");
          return CompiledExpression.executable(
              stack -> stack.getLocalAtSlotOffset(offset), CONTEXT_INDEPENDENT);
        });
    // f_stash: (T) -> T
    declareFunction(
        "f_stash",
        globalOverload(
            "f_stash",
            ImmutableList.of(CelProtoTypes.createTypeParam("T")),
            ImmutableList.of("T"),
            CelProtoTypes.createTypeParam("T")));
    // f_grab: () -> int
    declareFunction("f_grab", globalOverload("f_grab", ImmutableList.of(), CelProtoTypes.INT64));

    source = "f_stash([1, 2, 3].map(x, x + f_grab())) == [124, 125, 126]";
    runTest(Activation.EMPTY);
  }

  // This lambda implements @Immutable interface 'BinaryFunction', but the declaration of type
  // 'com.google.api.tools.contract.runtime.interpreter.MessageProcessor' is not annotated with
  // @com.google.errorprone.annotations.Immutable
  @SuppressWarnings("Immutable")
  @Test
  public void fieldManipulation() throws Exception {
    String protoName = TestAllTypes.getDescriptor().getFullName();
    Type protoType = createMessage(protoName);

    declareFunction(
        "assignSingleInt64",
        memberOverload(
            "assignSingleInt64", ImmutableList.of(protoType, CelProtoTypes.INT64), protoType));
    declareFunction(
        "assignRepeatedInt64",
        memberOverload(
            "assignRepeatedInt64",
            ImmutableList.of(protoType, CelProtoTypes.createList(CelProtoTypes.INT64)),
            protoType));
    declareFunction(
        "assignMap",
        memberOverload(
            "assignMap",
            ImmutableList.of(
                protoType, CelProtoTypes.createMap(CelProtoTypes.INT64, CelProtoTypes.INT64)),
            protoType));
    declareFunction(
        "clearField",
        memberOverload("clearField", ImmutableList.of(protoType, CelProtoTypes.STRING), protoType));
    declareFunction(
        "singletonInt64",
        globalOverload("singletonInt64", ImmutableList.of(CelProtoTypes.INT64), protoType));

    MessageProcessor messageProcessor = evalAsync.messageProcessor();
    MessageBuilderCreator builderCreator =
        messageProcessor.makeMessageBuilderCreator(null, 0L, protoName);
    FieldAssigner singleAssigner =
        messageProcessor.makeFieldAssigner(
            null, 0L, protoName, "single_int64", CelProtoTypes.INT64);
    FieldAssigner repeatedAssigner =
        messageProcessor.makeFieldAssigner(
            null, 0L, protoName, "repeated_int64", CelProtoTypes.createList(CelProtoTypes.INT64));
    FieldAssigner mapAssigner =
        messageProcessor.makeFieldAssigner(
            null,
            0L,
            protoName,
            "map_int32_int64",
            CelProtoTypes.createMap(CelProtoTypes.INT64, CelProtoTypes.INT64));

    dispatcher.add(
        "assignSingleInt64",
        TestAllTypes.class,
        Long.class,
        (p, i) -> singleAssigner.assign(p.toBuilder(), i).build());
    dispatcher.add(
        "assignRepeatedInt64",
        TestAllTypes.class,
        List.class,
        (p, l) -> repeatedAssigner.assign(p.toBuilder(), l).build());
    dispatcher.add(
        "assignMap",
        TestAllTypes.class,
        Map.class,
        (p, m) -> mapAssigner.assign(p.toBuilder(), m).build());
    dispatcher.add(
        "clearField",
        TestAllTypes.class,
        String.class,
        (p, n) ->
            messageProcessor.makeFieldClearer(null, 0L, protoName, n).clear(p.toBuilder()).build());
    dispatcher.add(
        "singletonInt64",
        Long.class,
        i -> singleAssigner.assign(builderCreator.builder(), i).build());

    container = TestAllTypes.getDescriptor().getFile().getPackage();

    source =
        "TestAllTypes{single_bool: true}.assignSingleInt64(1) == "
            + "TestAllTypes{single_bool: true, single_int64: 1}";
    runTest(Activation.EMPTY);

    source =
        "TestAllTypes{repeated_int64: [1, 2]}.assignRepeatedInt64([3, 1, 4]) == "
            + "TestAllTypes{repeated_int64: [3, 1, 4]}";
    runTest(Activation.EMPTY);

    source =
        "TestAllTypes{single_bool: true, single_int64: 1}.clearField(\"single_bool\") == "
            + "TestAllTypes{single_int64: 1}";
    runTest(Activation.EMPTY);

    source =
        "TestAllTypes{single_bool: false}.assignMap({13: 26, 22: 42}).map_int32_int64[22] == 42";
    runTest(Activation.EMPTY);

    source =
        "TestAllTypes{single_bool: true, repeated_int64: [1, 2]}.clearField(\"repeated_int64\") == "
            + "TestAllTypes{single_bool: true}";
    runTest(Activation.EMPTY);

    source = "singletonInt64(12) == TestAllTypes{single_int64: 12}";
    runTest(Activation.EMPTY);
  }

  /** Represents a delayed computation, used by the delayedEvaluation test case below. */
  interface Delayed {
    ListenableFuture<Object> force(GlobalContext gctx);

    static Delayed fromFuture(ListenableFuture<Object> future) {
      return gctx -> future;
    }

    static Delayed of(Delayed d) {
      return d;
    }
  }
}
