// Copyright 2023 Google LLC
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

package dev.cel.testing;

import com.google.common.collect.ImmutableList;
import com.google.protobuf.Descriptors.FileDescriptor;
import dev.cel.common.CelAbstractSyntaxTree;
import dev.cel.common.CelDescriptorUtil;
import dev.cel.common.CelDescriptors;
import dev.cel.common.CelOptions;
import dev.cel.common.internal.DefaultDescriptorPool;
import dev.cel.common.internal.DefaultMessageFactory;
import dev.cel.common.internal.DynamicProto;
import dev.cel.common.internal.ProtoMessageFactory;
import dev.cel.common.values.CelValueProvider;
import dev.cel.common.values.ProtoMessageValueProvider;
import dev.cel.runtime.Activation;
import dev.cel.runtime.DefaultDispatcher;
import dev.cel.runtime.DefaultInterpreter;
import dev.cel.runtime.Interpreter;
import dev.cel.runtime.InterpreterException;
import dev.cel.runtime.Registrar;
import dev.cel.runtime.RuntimeTypeProvider;
import dev.cel.runtime.RuntimeTypeProviderLegacyImpl;

/**
 * The {@link EvalSync} class represents common concerns for synchronous evaluation using {@code
 * CelValue}.
 */
public final class EvalCelValueSync implements Eval {

  private final ImmutableList<FileDescriptor> fileDescriptors;
  private final DefaultDispatcher dispatcher;
  private final Interpreter interpreter;
  private final RuntimeTypeProvider typeProvider;
  private final CelOptions celOptions;

  public EvalCelValueSync(ImmutableList<FileDescriptor> fileDescriptors, CelOptions celOptions) {
    this.fileDescriptors = fileDescriptors;
    this.dispatcher = DefaultDispatcher.create(celOptions);
    this.celOptions = celOptions;
    this.typeProvider = newTypeProvider(fileDescriptors);
    this.interpreter = new DefaultInterpreter(typeProvider, dispatcher, celOptions);
  }

  private RuntimeTypeProvider newTypeProvider(ImmutableList<FileDescriptor> fileDescriptors) {
    CelDescriptors celDescriptors =
        CelDescriptorUtil.getAllDescriptorsFromFileDescriptor(fileDescriptors);
    DefaultDescriptorPool celDescriptorPool = DefaultDescriptorPool.create(celDescriptors);
    ProtoMessageFactory messageFactory = DefaultMessageFactory.create(celDescriptorPool);
    DynamicProto dynamicProto = DynamicProto.create(messageFactory);
    CelValueProvider messageValueProvider =
        ProtoMessageValueProvider.newInstance(dynamicProto, celOptions);

    return new RuntimeTypeProviderLegacyImpl(
        celOptions, messageValueProvider, celDescriptorPool, dynamicProto);
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
    return interpreter.createInterpretable(ast).eval(activation);
  }
}
