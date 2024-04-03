// Copyright 2022 Google LLC
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

package dev.cel.runtime.async;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.util.concurrent.Futures.immediateFuture;
import static com.google.common.util.concurrent.MoreExecutors.newDirectExecutorService;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.Assert.assertThrows;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import com.google.testing.junit.testparameterinjector.TestParameter;
import com.google.testing.junit.testparameterinjector.TestParameterInjector;
import dev.cel.bundle.Cel;
import dev.cel.bundle.CelFactory;
import dev.cel.common.CelAbstractSyntaxTree;
import dev.cel.common.CelOptions;
import dev.cel.common.testing.RepeatedTestProvider;
import dev.cel.common.types.SimpleType;
import dev.cel.expr.Type;
import dev.cel.expr.Type.ListType;
import dev.cel.expr.Type.PrimitiveType;
import dev.cel.runtime.CelAttributeParser;
import dev.cel.runtime.CelAttributePattern;
import dev.cel.runtime.CelEvaluationException;
import dev.cel.runtime.UnknownContext;
import dev.cel.runtime.async.CelAsyncRuntime.AsyncProgram;
import dev.cel.testing.testdata.proto3.TestAllTypesProto.TestAllTypes;
import java.time.Duration;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeoutException;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(TestParameterInjector.class)
public final class CelAsyncRuntimeImplTest {

  @Test
  public void asyncProgram_basicUnknownResolution() throws Exception {
    // Arrange
    CelUnknownAttributeValueResolver resolveName =
        CelUnknownAttributeValueResolver.fromResolver(
            (attr) -> {
              Thread.sleep(500);
              return attr.toString();
            });
    Cel cel =
        CelFactory.standardCelBuilder()
            .setOptions(CelOptions.current().enableUnknownTracking(true).build())
            .addMessageTypes(TestAllTypes.getDescriptor())
            .addVar("com.google.var1", SimpleType.STRING)
            .addVar("com.google.var2", SimpleType.STRING)
            .addVar("com.google.var3", SimpleType.STRING)
            .setResultType(SimpleType.BOOL)
            .setContainer("com.google")
            .build();

    CelAsyncRuntime asyncRuntime =
        CelAsyncRuntimeFactory.defaultAsyncRuntime()
            .setRuntime(cel)
            .addResolvableAttributePattern(
                CelAttributePattern.fromQualifiedIdentifier("com.google.var1"), resolveName)
            .addResolvableAttributePattern(
                CelAttributePattern.fromQualifiedIdentifier("com.google.var2"), resolveName)
            .addResolvableAttributePattern(
                CelAttributePattern.fromQualifiedIdentifier("com.google.var3"), resolveName)
            .setExecutorService(newDirectExecutorService())
            .build();

    CelAbstractSyntaxTree ast =
        cel.compile(
                "var1 == 'com.google.var1' && "
                    + "var2 == 'com.google.var2' && "
                    + "var3 == 'com.google.var3'")
            .getAst();

    AsyncProgram program = asyncRuntime.createProgram(ast);
    // empty starting context
    UnknownContext context = asyncRuntime.newAsyncContext();

    // Act
    ListenableFuture<Object> future = program.evaluateToCompletion(context);
    Object result = future.get(2, SECONDS);

    // Assert
    assertThat(result).isInstanceOf(Boolean.class);
    assertThat(result).isEqualTo(true);
  }

  @Test
  public void asyncProgram_basicAsyncResovler() throws Exception {
    // Arrange
    final SettableFuture<Object> var1 = SettableFuture.create();
    final SettableFuture<Object> var2 = SettableFuture.create();
    final SettableFuture<Object> var3 = SettableFuture.create();

    Cel cel =
        CelFactory.standardCelBuilder()
            .setOptions(CelOptions.current().enableUnknownTracking(true).build())
            .addMessageTypes(TestAllTypes.getDescriptor())
            .addVar("com.google.var1", SimpleType.STRING)
            .addVar("com.google.var2", SimpleType.STRING)
            .addVar("com.google.var3", SimpleType.STRING)
            .setResultType(SimpleType.BOOL)
            .setContainer("com.google")
            .build();

    CelAsyncRuntime asyncRuntime =
        CelAsyncRuntimeFactory.defaultAsyncRuntime()
            .setRuntime(cel)
            .addResolvableAttributePattern(
                CelAttributePattern.fromQualifiedIdentifier("com.google.var1"),
                CelUnknownAttributeValueResolver.fromAsyncResolver((attr) -> var1))
            .addResolvableAttributePattern(
                CelAttributePattern.fromQualifiedIdentifier("com.google.var2"),
                CelUnknownAttributeValueResolver.fromAsyncResolver((attr) -> var2))
            .addResolvableAttributePattern(
                CelAttributePattern.fromQualifiedIdentifier("com.google.var3"),
                CelUnknownAttributeValueResolver.fromAsyncResolver((attr) -> var3))
            .setExecutorService(Executors.newSingleThreadExecutor())
            .build();

    CelAbstractSyntaxTree ast =
        cel.compile("var1 == 'first' && var2 == 'second' && var3 == 'third'").getAst();

    AsyncProgram program = asyncRuntime.createProgram(ast);
    // empty starting context
    UnknownContext context = asyncRuntime.newAsyncContext();

    // Act
    ListenableFuture<Object> future = program.evaluateToCompletion(context);
    assertThrows(TimeoutException.class, () -> future.get(1, SECONDS));
    var1.set("first");
    var2.set("second");
    var3.set("third");
    Object result = future.get(1, SECONDS);

    // Assert
    assertThat(result).isInstanceOf(Boolean.class);
    assertThat(result).isEqualTo(true);
  }

  @Test
  public void asyncProgram_resolvesDoneFuturesFirst_withSyncStyleResolvers() throws Exception {
    // Arrange
    Cel cel =
        CelFactory.standardCelBuilder()
            .setOptions(CelOptions.current().enableUnknownTracking(true).build())
            .addVar("com.google.var1", SimpleType.BOOL)
            .addVar("com.google.var2", SimpleType.BOOL)
            .setResultType(SimpleType.BOOL)
            .setContainer("com.google")
            .build();

    CelAsyncRuntime asyncRuntime =
        CelAsyncRuntimeFactory.defaultAsyncRuntime()
            .setRuntime(cel)
            .addResolvableAttributePattern(
                CelAttributePattern.fromQualifiedIdentifier("com.google.var1"),
                CelUnknownAttributeValueResolver.fromResolver((attr) -> {
                  try {
                    Thread.sleep(Long.MAX_VALUE);
                    throw new IllegalStateException("Should never fire");
                  } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                  }
                }))
            .addResolvableAttributePattern(
                CelAttributePattern.fromQualifiedIdentifier("com.google.var2"),
                CelUnknownAttributeValueResolver.fromAsyncResolver((attr) -> immediateFuture(true)))
            .setExecutorService(Executors.newFixedThreadPool(2))
            .build();

    CelAbstractSyntaxTree ast = cel.compile("var1 || var2").getAst();

    AsyncProgram program = asyncRuntime.createProgram(ast);
    // empty starting context
    UnknownContext context = asyncRuntime.newAsyncContext();

    // Act
    ListenableFuture<Object> future = program.evaluateToCompletion(context);
    boolean result = (boolean) future.get(1, SECONDS);

    // Assert
    assertThat(result).isTrue();
  }

  @Test
  public void asyncProgram_honorsCancellation() throws Exception {
    // Arrange
    final SettableFuture<Object> var1 = SettableFuture.create();
    final SettableFuture<Object> var2 = SettableFuture.create();
    final SettableFuture<Object> var3 = SettableFuture.create();

    Cel cel =
        CelFactory.standardCelBuilder()
            .setOptions(CelOptions.current().enableUnknownTracking(true).build())
            .addMessageTypes(TestAllTypes.getDescriptor())
            .addVar("com.google.var1", SimpleType.STRING)
            .addVar("com.google.var2", SimpleType.STRING)
            .addVar("com.google.var3", SimpleType.STRING)
            .setResultType(SimpleType.BOOL)
            .setContainer("com.google")
            .build();

    CelAsyncRuntime asyncRuntime =
        CelAsyncRuntimeFactory.defaultAsyncRuntime()
            .setRuntime(cel)
            .addResolvableAttributePattern(
                CelAttributePattern.fromQualifiedIdentifier("com.google.var1"),
                CelUnknownAttributeValueResolver.fromAsyncResolver((attr) -> var1))
            .addResolvableAttributePattern(
                CelAttributePattern.fromQualifiedIdentifier("com.google.var2"),
                CelUnknownAttributeValueResolver.fromAsyncResolver((attr) -> var2))
            .addResolvableAttributePattern(
                CelAttributePattern.fromQualifiedIdentifier("com.google.var3"),
                CelUnknownAttributeValueResolver.fromAsyncResolver((attr) -> var3))
            .setExecutorService(Executors.newSingleThreadExecutor())
            .build();

    CelAbstractSyntaxTree ast =
        cel.compile("var1 == 'first' && var2 == 'second' && var3 == 'third'").getAst();

    AsyncProgram program = asyncRuntime.createProgram(ast);
    // empty starting context
    UnknownContext context = asyncRuntime.newAsyncContext();

    // Act
    ListenableFuture<Object> future = program.evaluateToCompletion(context);
    var1.set("first");
    future.cancel(true);
    assertThrows(CancellationException.class, () -> future.get(1, SECONDS));
  }

  /** Helper for defining test resolvers. */
  @FunctionalInterface
  interface ResolverFactory {
    CelUnknownAttributeValueResolver get(String id);
  }

  @Test
  public void asyncProgram_concurrency(
      @TestParameter(valuesProvider = RepeatedTestProvider.class) int testRunIndex)
      throws Exception {
    final Duration taskDelay = Duration.ofMillis(500);
    // Arrange
    Cel cel =
        CelFactory.standardCelBuilder()
            .setOptions(CelOptions.current().enableUnknownTracking(true).build())
            .addMessageTypes(TestAllTypes.getDescriptor())
            .addVar("com.google.var1", SimpleType.STRING)
            .addVar("com.google.var2", SimpleType.STRING)
            .addVar("com.google.var3", SimpleType.STRING)
            .setResultType(SimpleType.BOOL)
            .setContainer("com.google")
            .build();

    ResolverFactory resolverFactory =
        (String s) ->
            CelUnknownAttributeValueResolver.fromResolver(
                (attr) -> {
                  Thread.sleep(taskDelay.toMillis());
                  return s;
                });

    CelAsyncRuntime asyncRuntime =
        CelAsyncRuntimeFactory.defaultAsyncRuntime()
            .setRuntime(cel)
            .addResolvableAttributePattern(
                CelAttributePattern.fromQualifiedIdentifier("com.google.var1"),
                resolverFactory.get("first"))
            .addResolvableAttributePattern(
                CelAttributePattern.fromQualifiedIdentifier("com.google.var2"),
                resolverFactory.get("second"))
            .addResolvableAttributePattern(
                CelAttributePattern.fromQualifiedIdentifier("com.google.var3"),
                resolverFactory.get("third"))
            .setExecutorService(Executors.newFixedThreadPool(3))
            .build();

    CelAbstractSyntaxTree ast =
        cel.compile("var1 == 'first' && var2 == 'second' && var3 == 'third'").getAst();

    AsyncProgram program = asyncRuntime.createProgram(ast);
    // empty starting context
    UnknownContext context = asyncRuntime.newAsyncContext();

    // Act
    ListenableFuture<Object> future = program.evaluateToCompletion(context);

    // Total wait is 2 times the worker delay. This is a little conservative for the size of the
    // threadpool executor above, but should prevent flakes.
    Object result = future.get(taskDelay.multipliedBy(2).toMillis(), MILLISECONDS);
    assertThat(result).isInstanceOf(Boolean.class);
    assertThat(result).isEqualTo(true);
  }

  @Test
  public void asyncProgram_elementResolver() throws Exception {
    // Arrange
    Cel cel =
        CelFactory.standardCelBuilder()
            .setOptions(CelOptions.current().enableUnknownTracking(true).build())
            .addMessageTypes(TestAllTypes.getDescriptor())
            .addVar(
                "com.google.listVar",
                Type.newBuilder()
                    .setListType(
                        ListType.newBuilder()
                            .setElemType(Type.newBuilder().setPrimitive(PrimitiveType.STRING)))
                    .build())
            .setResultType(SimpleType.BOOL)
            .setContainer("com.google")
            .build();

    CelUnknownAttributeValueResolver resolver =
        CelUnknownAttributeValueResolver.fromResolver(
            (attr) -> "el" + attr.qualifiers().reverse().get(0).asInt());

    CelAsyncRuntime asyncRuntime =
        CelAsyncRuntimeFactory.defaultAsyncRuntime()
            .setRuntime(cel)
            .addResolvableAttributePattern(
                CelAttributeParser.parsePattern("com.google.listVar[0]"), resolver)
            .addResolvableAttributePattern(
                CelAttributeParser.parsePattern("com.google.listVar[1]"), resolver)
            .addResolvableAttributePattern(
                CelAttributeParser.parsePattern("com.google.listVar[2]"), resolver)
            .setExecutorService(Executors.newSingleThreadExecutor())
            .build();

    CelAbstractSyntaxTree ast =
        cel.compile("listVar[0] == 'el0' && listVar[1] == 'el1' && listVar[2] == 'el2'").getAst();

    AsyncProgram program = asyncRuntime.createProgram(ast);
    // empty starting context
    UnknownContext context = asyncRuntime.newAsyncContext();

    // Act
    ListenableFuture<Object> future = program.evaluateToCompletion(context);
    Object result = future.get(1, SECONDS);

    // Assert
    assertThat(result).isInstanceOf(Boolean.class);
    assertThat(result).isEqualTo(true);
  }

  @Test
  public void asyncProgram_thrownExceptionPropagatesImmediately() throws Exception {
    // Arrange
    Cel cel =
        CelFactory.standardCelBuilder()
            .setOptions(CelOptions.current().enableUnknownTracking(true).build())
            .addMessageTypes(TestAllTypes.getDescriptor())
            .addVar("com.google.var1", SimpleType.STRING)
            .addVar("com.google.var2", SimpleType.STRING)
            .addVar("com.google.var3", SimpleType.STRING)
            .setResultType(SimpleType.BOOL)
            .setContainer("com.google")
            .build();

    CelUnknownAttributeValueResolver resolveName =
        CelUnknownAttributeValueResolver.fromResolver(
            (attr) -> {
              Thread.sleep(500);
              return attr.toString();
            });

    CelAsyncRuntime asyncRuntime =
        CelAsyncRuntimeFactory.defaultAsyncRuntime()
            .setRuntime(cel)
            .addResolvableAttributePattern(
                CelAttributePattern.fromQualifiedIdentifier("com.google.var1"), resolveName)
            .addResolvableAttributePattern(
                CelAttributePattern.fromQualifiedIdentifier("com.google.var2"),
                CelUnknownAttributeValueResolver.fromResolver(
                    (attr) -> {
                      throw new IllegalArgumentException("example_var2");
                    }))
            .addResolvableAttributePattern(
                CelAttributePattern.fromQualifiedIdentifier("com.google.var3"), resolveName)
            .setExecutorService(newDirectExecutorService())
            .build();

    CelAbstractSyntaxTree ast =
        cel.compile(
                "var1 == 'com.google.var1' || "
                    + "var2 == 'com.google.var2' || "
                    + "var3 == 'com.google.var3'")
            .getAst();

    AsyncProgram program = asyncRuntime.createProgram(ast);
    UnknownContext context = asyncRuntime.newAsyncContext();

    // Act
    ListenableFuture<Object> future = program.evaluateToCompletion(context);

    // Assert
    ExecutionException e = assertThrows(ExecutionException.class, () -> future.get(2, SECONDS));
    assertThat(e).hasCauseThat().isInstanceOf(IllegalArgumentException.class);
    assertThat(e).hasCauseThat().hasMessageThat().isEqualTo("example_var2");
  }

  @Test
  public void asyncProgram_returnedExceptionPropagatesToEvaluator() throws Exception {
    // Arrange
    Cel cel =
        CelFactory.standardCelBuilder()
            .setOptions(CelOptions.current().enableUnknownTracking(true).build())
            .addMessageTypes(TestAllTypes.getDescriptor())
            .addVar("com.google.var1", SimpleType.STRING)
            .addVar("com.google.var2", SimpleType.STRING)
            .addVar("com.google.var3", SimpleType.STRING)
            .setResultType(SimpleType.BOOL)
            .setContainer("com.google")
            .build();

    CelUnknownAttributeValueResolver resolveName =
        CelUnknownAttributeValueResolver.fromResolver(
            (attr) -> {
              Thread.sleep(500);
              return attr.toString();
            });

    CelAsyncRuntime asyncRuntime =
        CelAsyncRuntimeFactory.defaultAsyncRuntime()
            .setRuntime(cel)
            .addResolvableAttributePattern(
                CelAttributePattern.fromQualifiedIdentifier("com.google.var1"), resolveName)
            .addResolvableAttributePattern(
                CelAttributePattern.fromQualifiedIdentifier("com.google.var2"),
                CelUnknownAttributeValueResolver.fromResolver(
                    (attr) -> new IllegalStateException("example_var2")))
            .addResolvableAttributePattern(
                CelAttributePattern.fromQualifiedIdentifier("com.google.var3"), resolveName)
            .setExecutorService(newDirectExecutorService())
            .build();

    CelAbstractSyntaxTree ast =
        cel.compile(
                "var1 == 'com.google.var1' && "
                    + "var2 == 'com.google.var2' && "
                    + "var3 == 'com.google.var3'")
            .getAst();

    AsyncProgram program = asyncRuntime.createProgram(ast);
    UnknownContext context = asyncRuntime.newAsyncContext();

    // Act
    ListenableFuture<Object> future = program.evaluateToCompletion(context);

    // Assert
    ExecutionException e = assertThrows(ExecutionException.class, () -> future.get(2, SECONDS));
    assertThat(e).hasCauseThat().isInstanceOf(CelEvaluationException.class);
    assertThat(e).hasCauseThat().hasCauseThat().isInstanceOf(IllegalStateException.class);
    assertThat(e).hasCauseThat().hasCauseThat().hasMessageThat().isEqualTo("example_var2");
  }

  @Test
  public void asyncProgram_returnedExceptionPropagatesToEvaluatorIsPruneable() throws Exception {
    // Arrange
    Cel cel =
        CelFactory.standardCelBuilder()
            .setOptions(CelOptions.current().enableUnknownTracking(true).build())
            .addMessageTypes(TestAllTypes.getDescriptor())
            .addVar("com.google.var1", SimpleType.STRING)
            .addVar("com.google.var2", SimpleType.STRING)
            .addVar("com.google.var3", SimpleType.STRING)
            .setResultType(SimpleType.BOOL)
            .setContainer("com.google")
            .build();

    CelUnknownAttributeValueResolver resolveName =
        CelUnknownAttributeValueResolver.fromResolver(
            (attr) -> {
              Thread.sleep(500);
              return attr.toString();
            });

    CelAsyncRuntime asyncRuntime =
        CelAsyncRuntimeFactory.defaultAsyncRuntime()
            .setRuntime(cel)
            .addResolvableAttributePattern(
                CelAttributePattern.fromQualifiedIdentifier("com.google.var1"), resolveName)
            .addResolvableAttributePattern(
                CelAttributePattern.fromQualifiedIdentifier("com.google.var2"),
                CelUnknownAttributeValueResolver.fromResolver(
                    (attr) -> new IllegalStateException("example")))
            .addResolvableAttributePattern(
                CelAttributePattern.fromQualifiedIdentifier("com.google.var3"), resolveName)
            .setExecutorService(newDirectExecutorService())
            .build();

    CelAbstractSyntaxTree ast =
        cel.compile("var1 == 'incorrect' || " + "var2 == 'error' || " + "var3 == 'com.google.var3'")
            .getAst();

    AsyncProgram program = asyncRuntime.createProgram(ast);
    UnknownContext context = asyncRuntime.newAsyncContext();

    // Act
    ListenableFuture<Object> future = program.evaluateToCompletion(context);
    Object result = future.get(2, SECONDS);

    // Assert
    assertThat(result).isInstanceOf(Boolean.class);
    assertThat(result).isEqualTo(true);
  }
}
