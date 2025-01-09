package dev.cel.legacy.runtime.async;

import static com.google.common.util.concurrent.Futures.immediateFuture;
import static com.google.common.util.concurrent.MoreExecutors.directExecutor;
import static dev.cel.common.types.CelProtoTypes.createMessage;
import static dev.cel.legacy.runtime.async.Effect.CONTEXT_DEPENDENT;

import dev.cel.expr.Type;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.protobuf.ByteString;
import com.google.protobuf.Descriptors.FileDescriptor;
// import com.google.testing.testsize.MediumTest;
import dev.cel.common.CelAbstractSyntaxTree;
import dev.cel.common.types.CelProtoTypes;
import dev.cel.expr.conformance.proto2.TestAllTypesExtensions;
import dev.cel.expr.conformance.proto3.TestAllTypes;
import dev.cel.legacy.runtime.async.MessageProcessor.FieldAssigner;
import dev.cel.legacy.runtime.async.MessageProcessor.MessageBuilderCreator;
import dev.cel.runtime.Activation;
import dev.cel.runtime.InterpreterException;
import dev.cel.runtime.Registrar.BinaryFunction;
import dev.cel.runtime.Registrar.UnaryFunction;
import dev.cel.testing.CelBaselineTestCase;
import dev.cel.testing.testdata.proto3.StandaloneGlobalEnum;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

/** Tests for {@link FuturesInterpreter} and related functionality. */
// @MediumTest
@RunWith(Parameterized.class)
public class FuturesInterpreterWithMessageProcessorTest extends CelBaselineTestCase {
  private final EvalAsync evalAsync;

  private static final ImmutableList<FileDescriptor> TEST_FILE_DESCRIPTORS =
      ImmutableList.of(
          TestAllTypes.getDescriptor().getFile(),
          dev.cel.expr.conformance.proto2.TestAllTypes.getDescriptor().getFile(),
          StandaloneGlobalEnum.getDescriptor().getFile());

  // EvalSync and Async are mutable by design (Ex: adding function to the dispatcher). This has been
  // overridden to make the test cases descriptive, as mutability is not a core concern of these
  // tests.
  @SuppressWarnings("ImmutableEnumChecker")
  private enum EvalTestCase {
    ASYNC_PROTO_TYPE_PARSER_DIRECTED_PROCESSOR(
        false,
        () ->
            new EvalAsync(TEST_FILE_DESCRIPTORS, TEST_OPTIONS, /* typeDirectedProcessor= */ true)),
    ASYNC_CEL_TYPE_PARSER_DIRECTED_PROCESSOR(
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

  public FuturesInterpreterWithMessageProcessorTest(EvalTestCase testCase) {
    super(testCase.declareWithCelType);
    this.evalAsync = testCase.eval.get();
  }

  /** Helper to run a test for configured instance variables. */
  private void runTest(Activation activation) throws Exception {
    CelAbstractSyntaxTree ast = prepareTest(evalAsync.fileDescriptors());
    if (ast == null) {
      return;
    }
    testOutput().println("bindings: " + activation);
    try {
      Object result = evalAsync.eval(ast, activation);
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

  // Helper for testing late-binding of the MessageProcessor (binary functions).
  // This lambda implements @Immutable interface 'CallConstructor', but the declaration of type
  // 'java.util.function.Function<com.google.api.tools.contract.runtime.interpreter.MessageProcessor,com.google.api.tools.contract.runtime.interpreter.Registrar.BinaryFunction<A,B>>' is not annotated with @com.google.errorprone.annotations.Immutable
  @SuppressWarnings("Immutable")
  private static <A, B> void addFunctionWithMessageProcessor(
      FunctionRegistrar registrar,
      String overloadId,
      Class<A> clazzA,
      Class<B> clazzB,
      Function<MessageProcessor, BinaryFunction<A, B>> functionMaker) {
    registrar.addCallConstructor(
        overloadId,
        (md, id, args, mp, ignoredStackOffsetFinder) -> {
          ExecutableExpression executableA = args.get(0).expression().toExecutable();
          ExecutableExpression executableB = args.get(1).expression().toExecutable();
          BinaryFunction<A, B> function = functionMaker.apply(mp);
          return CompiledExpression.executable(
              stack ->
                  executableA
                      .execute(stack)
                      .transformAsync(
                          a ->
                              executableB
                                  .execute(stack)
                                  .transformAsync(
                                      b ->
                                          immediateFuture(
                                              function.apply(clazzA.cast(a), clazzB.cast(b))),
                                      directExecutor()),
                          directExecutor()),
              CONTEXT_DEPENDENT);
        });
  }

  // Helper for testing late-binding of the MessageProcessor (unary functions).
  // This lambda implements @Immutable interface 'CallConstructor', but the declaration of type
  // 'java.util.function.Function<com.google.api.tools.contract.runtime.interpreter.MessageProcessor,com.google.api.tools.contract.runtime.interpreter.Registrar.UnaryFunction<A>>' is not annotated with @com.google.errorprone.annotations.Immutable
  @SuppressWarnings("Immutable")
  private static <A> void addFunctionWithMessageProcessor(
      FunctionRegistrar registrar,
      String overloadId,
      Class<A> clazzA,
      Function<MessageProcessor, UnaryFunction<A>> functionMaker) {
    registrar.addCallConstructor(
        overloadId,
        (md, id, args, mp, ignoredStackOffsetFinder) -> {
          ExecutableExpression executableA = args.get(0).expression().toExecutable();
          UnaryFunction<A> function = functionMaker.apply(mp);
          return CompiledExpression.executable(
              stack ->
                  executableA
                      .execute(stack)
                      .transformAsync(
                          a -> immediateFuture(function.apply(clazzA.cast(a))), directExecutor()),
              CONTEXT_DEPENDENT);
        });
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

    MessageBuilderCreator builderCreator =
        evalAsync.messageProcessor().makeMessageBuilderCreator(null, 0L, protoName);
    FieldAssigner singleAssigner =
        evalAsync
            .messageProcessor()
            .makeFieldAssigner(null, 0L, protoName, "single_int64", CelProtoTypes.INT64);

    AsyncDispatcher dispatcher = (AsyncDispatcher) evalAsync.registrar();
    dispatcher.add(
        "assignSingleInt64",
        TestAllTypes.class,
        Long.class,
        (p, i) -> singleAssigner.assign(p.toBuilder(), i).build());
    addFunctionWithMessageProcessor(
        dispatcher,
        "assignRepeatedInt64",
        TestAllTypes.class,
        List.class,
        mp ->
            (p, l) ->
                mp.makeFieldAssigner(
                        null,
                        0L,
                        protoName,
                        "repeated_int64",
                        CelProtoTypes.createList(CelProtoTypes.INT64))
                    .assign(p.toBuilder(), l)
                    .build());
    addFunctionWithMessageProcessor(
        dispatcher,
        "assignMap",
        TestAllTypes.class,
        Map.class,
        mp ->
            (p, m) ->
                mp.makeFieldAssigner(
                        null,
                        0L,
                        protoName,
                        "map_int32_int64",
                        CelProtoTypes.createMap(CelProtoTypes.INT64, CelProtoTypes.INT64))
                    .assign(p.toBuilder(), m)
                    .build());
    addFunctionWithMessageProcessor(
        dispatcher,
        "clearField",
        TestAllTypes.class,
        String.class,
        mp -> (p, n) -> mp.makeFieldClearer(null, 0L, protoName, n).clear(p.toBuilder()).build());
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

  // This lambda implements @Immutable interface 'UnaryFunction', but the declaration of type
  // 'com.google.api.tools.contract.runtime.interpreter.MessageProcessor' is not annotated with
  // @com.google.errorprone.annotations.Immutable
  @SuppressWarnings("Immutable")
  @Test
  public void extensionManipulation() throws Exception {
    String extI = "cel.expr.conformance.proto2.int32_ext";
    String extN = "cel.expr.conformance.proto2.nested_ext";
    String extR = "cel.expr.conformance.proto2.repeated_test_all_types";
    String protoName = dev.cel.expr.conformance.proto2.TestAllTypes.getDescriptor().getFullName();
    Type protoType = createMessage(protoName);
    String holderName = dev.cel.expr.conformance.proto2.TestAllTypes.getDescriptor().getFullName();
    Type holderType = createMessage(holderName);
    Type holderListType = CelProtoTypes.createList(holderType);
    dev.cel.expr.conformance.proto2.TestAllTypes withoutExt =
        dev.cel.expr.conformance.proto2.TestAllTypes.newBuilder().setSingleInt32(50).build();
    dev.cel.expr.conformance.proto2.TestAllTypes withExt =
        dev.cel.expr.conformance.proto2.TestAllTypes.newBuilder()
            .setSingleInt32(100)
            .setExtension(TestAllTypesExtensions.int32Ext, 200)
            .setExtension(TestAllTypesExtensions.nestedExt, withoutExt)
            .addExtension(
                TestAllTypesExtensions.repeatedTestAllTypes,
                dev.cel.expr.conformance.proto2.TestAllTypes.newBuilder()
                    .setSingleString("alpha")
                    .build())
            .addExtension(
                TestAllTypesExtensions.repeatedTestAllTypes,
                dev.cel.expr.conformance.proto2.TestAllTypes.newBuilder()
                    .setSingleString("alpha")
                    .build())
            .build();

    declareVariable("y", protoType);
    declareVariable("n", protoType);

    declareMemberFunction("getI", ImmutableList.of(protoType), CelProtoTypes.INT64);
    declareMemberFunction("hasI", ImmutableList.of(protoType), CelProtoTypes.BOOL);
    declareMemberFunction("assignI", ImmutableList.of(protoType, CelProtoTypes.INT64), protoType);
    declareMemberFunction("clearI", ImmutableList.of(protoType), protoType);

    declareMemberFunction("getN", ImmutableList.of(protoType), protoType);
    declareMemberFunction("hasN", ImmutableList.of(protoType), CelProtoTypes.BOOL);
    declareMemberFunction("assignN", ImmutableList.of(protoType, protoType), protoType);
    declareMemberFunction("clearN", ImmutableList.of(protoType), protoType);

    declareMemberFunction("getR", ImmutableList.of(protoType), holderListType);
    declareMemberFunction("assignR", ImmutableList.of(protoType, holderListType), protoType);
    declareMemberFunction("clearR", ImmutableList.of(protoType), protoType);

    AsyncDispatcher dispatcher = (AsyncDispatcher) evalAsync.registrar();
    addFunctionWithMessageProcessor(
        dispatcher,
        "getI",
        dev.cel.expr.conformance.proto2.TestAllTypes.class,
        mp -> p -> mp.makeExtensionGetter(null, 0L, extI).getField(p));
    addFunctionWithMessageProcessor(
        dispatcher,
        "hasI",
        dev.cel.expr.conformance.proto2.TestAllTypes.class,
        mp -> p -> mp.makeExtensionTester(null, 0L, extI).hasField(p));
    addFunctionWithMessageProcessor(
        dispatcher,
        "assignI",
        dev.cel.expr.conformance.proto2.TestAllTypes.class,
        Long.class,
        mp ->
            (p, i) ->
                mp.makeExtensionAssigner(null, 0L, extI, CelProtoTypes.INT64)
                    .assign(p.toBuilder(), i)
                    .build());
    addFunctionWithMessageProcessor(
        dispatcher,
        "clearI",
        dev.cel.expr.conformance.proto2.TestAllTypes.class,
        mp -> p -> mp.makeExtensionClearer(null, 0L, extI).clear(p.toBuilder()).build());
    addFunctionWithMessageProcessor(
        dispatcher,
        "getN",
        dev.cel.expr.conformance.proto2.TestAllTypes.class,
        mp -> p -> mp.makeExtensionGetter(null, 0L, extN).getField(p));
    addFunctionWithMessageProcessor(
        dispatcher,
        "hasN",
        dev.cel.expr.conformance.proto2.TestAllTypes.class,
        mp -> p -> mp.makeExtensionTester(null, 0L, extN).hasField(p));
    addFunctionWithMessageProcessor(
        dispatcher,
        "assignN",
        dev.cel.expr.conformance.proto2.TestAllTypes.class,
        dev.cel.expr.conformance.proto2.TestAllTypes.class,
        mp ->
            (p, i) ->
                mp.makeExtensionAssigner(null, 0L, extN, protoType)
                    .assign(p.toBuilder(), i)
                    .build());
    addFunctionWithMessageProcessor(
        dispatcher,
        "clearN",
        dev.cel.expr.conformance.proto2.TestAllTypes.class,
        mp -> p -> mp.makeExtensionClearer(null, 0L, extN).clear(p.toBuilder()).build());
    addFunctionWithMessageProcessor(
        dispatcher,
        "getR",
        dev.cel.expr.conformance.proto2.TestAllTypes.class,
        mp -> p -> mp.makeExtensionGetter(null, 0L, extR).getField(p));
    addFunctionWithMessageProcessor(
        dispatcher,
        "assignR",
        dev.cel.expr.conformance.proto2.TestAllTypes.class,
        List.class,
        mp ->
            (p, l) ->
                mp.makeExtensionAssigner(null, 0L, extR, holderListType)
                    .assign(p.toBuilder(), l)
                    .build());
    addFunctionWithMessageProcessor(
        dispatcher,
        "clearR",
        dev.cel.expr.conformance.proto2.TestAllTypes.class,
        mp -> p -> mp.makeExtensionClearer(null, 0L, extR).clear(p.toBuilder()).build());

    container = dev.cel.expr.conformance.proto2.TestAllTypes.getDescriptor().getFullName();

    source =
        "[y.hasI(), y.getI() == 200, !n.hasI(), n.getI() == 0,\n"
            + " n.assignI(43).hasI(), n.assignI(42).getI() == 42,\n"
            + " y.assignI(99).hasI(), y.assignI(31).getI() == 31,\n"
            + " !n.clearI().hasI(), !y.clearI().hasI(), y.clearI().getI() == 0,\n"
            + " y.hasN(), y.getN().getI() == 0, !y.getN().hasN(), y.getN().getN().getI() == 0,\n"
            + " !n.hasN(), n.assignN(y).getN().hasN(),\n"
            + " !n.clearN().hasN(), !y.clearN().hasN(),\n"
            + " n.getR() == [], y.getR().map(h, h.single_string) == [\"alpha\", \"beta\"],\n"
            + " n.assignR([\"a\", \"b\"].map(s, TestAllTypes{single_string:s}))."
            + "getR().map(h, h.single_string) == [\"a\", \"b\"],\n"
            + " y.clearR().getR() == []]";
    runTest(Activation.copyOf(ImmutableMap.of("y", withExt, "n", withoutExt)));
  }
}
