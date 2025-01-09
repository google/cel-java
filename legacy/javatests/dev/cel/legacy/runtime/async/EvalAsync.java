package dev.cel.legacy.runtime.async;

import static com.google.common.util.concurrent.Futures.immediateFuture;
import static com.google.common.util.concurrent.MoreExecutors.directExecutor;

import com.google.common.collect.ImmutableList;
import com.google.common.context.Context;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.protobuf.Descriptors.FileDescriptor;
import com.google.protobuf.ExtensionRegistry;
import com.google.protobuf.contrib.descriptor.pool.MutableDescriptorPool;
import com.google.security.context.testing.FakeUnvalidatedSecurityContextBuilder;
import dev.cel.common.CelAbstractSyntaxTree;
import dev.cel.common.CelOptions;
import dev.cel.common.CelProtoAbstractSyntaxTree;
import dev.cel.legacy.runtime.async.TypeDirectedMessageProcessor.MessageInfo;
import dev.cel.runtime.Activation;
import dev.cel.runtime.InterpreterException;
import dev.cel.runtime.LinkedMessageFactory;
import dev.cel.runtime.MessageFactory;
import dev.cel.runtime.MessageProvider;
import dev.cel.runtime.Registrar;
import java.util.Optional;
import java.util.concurrent.ExecutionException;

/**
 * The {@code EvalAsync} class implements the {@code Eval} interface and exposes some additional
 * methods for async evaluation testing.
 */
public class EvalAsync implements Eval {

  private final ImmutableList<FileDescriptor> fileDescriptors;
  private final MutableDescriptorPool pool = new MutableDescriptorPool();
  private final AsyncDispatcher dispatcher;
  private final MessageProvider typeProvider;
  private final MessageProcessor messageProcessor;
  private final AsyncInterpreter asyncInterpreter;
  private final CelOptions celOptions;

  public EvalAsync(ImmutableList<FileDescriptor> fileDescriptors, CelOptions celOptions) {
    this(fileDescriptors, celOptions, /* typeDirectedProcessor= */ false);
  }

  public EvalAsync(
      ImmutableList<FileDescriptor> fileDescriptors,
      CelOptions celOptions,
      boolean typeDirectedProcessor) {
    this(
        fileDescriptors,
        celOptions,
        typeDirectedProcessor,
        DefaultAsyncDispatcher.create(celOptions).fork());
  }

  private EvalAsync(
      ImmutableList<FileDescriptor> fileDescriptors,
      CelOptions celOptions,
      boolean typeDirectedProcessor,
      AsyncDispatcher asyncDispatcher) {
    this.dispatcher = asyncDispatcher;
    this.celOptions = celOptions;
    this.fileDescriptors = fileDescriptors;
    this.fileDescriptors.forEach(pool::populateFromFileDescriptor);
    this.typeProvider = LinkedMessageFactory.typeProvider(celOptions);
    MessageFactory typeFactory = LinkedMessageFactory.typeFactory();

    if (typeDirectedProcessor) {
      this.messageProcessor =
          new TypeDirectedMessageProcessor(
              typeName ->
                  Optional.ofNullable(pool.getDescriptorForTypeName(typeName))
                      .map(
                          descriptor ->
                              MessageInfo.of(descriptor, () -> typeFactory.newBuilder(typeName))),
              extName ->
                  Optional.ofNullable(
                      ExtensionRegistry.getGeneratedRegistry().findExtensionByName(extName)),
              celOptions);
    } else {
      this.messageProcessor =
          new MessageProcessorAdapter(
              typeName -> Optional.ofNullable(pool.getDescriptorForTypeName(typeName)),
              typeProvider);
    }
    this.asyncInterpreter =
        new FuturesInterpreter(
            StandardTypeResolver.getInstance(celOptions),
            this.messageProcessor,
            this.dispatcher,
            celOptions);
  }

  @Override
  public ImmutableList<FileDescriptor> fileDescriptors() {
    return fileDescriptors;
  }

  @Override
  public Registrar registrar() {
    return dispatcher;
  }

  @Override
  public CelOptions celOptions() {
    return celOptions;
  }

  @Override
  public Object adapt(Object value) throws InterpreterException {
    return typeProvider.adapt(value);
  }

  @Override
  public Object eval(CelAbstractSyntaxTree ast, Activation activation) throws Exception {
    Context requestContext =
        Context.newBuilder(Context.getCurrentContext())
            // Make security context different from BACKGROUND_SECURITY_CONTEXT.
            .replaceSecurityContext(
                FakeUnvalidatedSecurityContextBuilder.withPeer("testpeer").buildUnvalidated())
            .build();
    return forceExpressionFuture(
        asyncInterpreter
            .createInterpretable(CelProtoAbstractSyntaxTree.fromCelAst(ast).toCheckedExpr())
            .eval(
                new DefaultAsyncContext(directExecutor(), requestContext),
                name -> immediateFuture(activation.resolve(name))));
  }

  /** Returns the {@code MessageProcessor} used for protobuf creation and manipulation. */
  public MessageProcessor messageProcessor() {
    return messageProcessor;
  }

  /**
   * Indicates whether the {@code type_map} from the {@code CheckedExpr} is used to determine
   * runtime typing during function and field resolution.
   */
  public boolean typeDirected() {
    return messageProcessor instanceof TypeDirectedMessageProcessor;
  }

  /** Creates a new {@code EvalAsync} instance using the supplied dispatcher. */
  public EvalAsync withDispatcher(AsyncDispatcher dispatcher) {
    if (dispatcher == this.dispatcher) {
      return this;
    }
    return new EvalAsync(fileDescriptors, celOptions, typeDirected(), dispatcher);
  }

  private Object forceExpressionFuture(ListenableFuture<Object> future)
      throws InterpreterException {
    try {
      return future.get();
    } catch (InterruptedException intrExn) {
      Thread.currentThread().interrupt();
      throw new RuntimeException(intrExn);
    } catch (ExecutionException execExn) {
      Throwable cause = execExn.getCause();
      if (cause instanceof InterpreterException) {
        throw (InterpreterException) cause;
      } else if (cause instanceof RuntimeException) {
        throw (RuntimeException) cause;
      } else {
        throw new RuntimeException(cause);
      }
    }
  }
}
