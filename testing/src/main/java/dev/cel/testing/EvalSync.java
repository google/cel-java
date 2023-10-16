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

package dev.cel.testing;

import dev.cel.expr.CheckedExpr;
import com.google.common.collect.ImmutableList;
import com.google.protobuf.Descriptors.FileDescriptor;
import dev.cel.common.CelDescriptorUtil;
import dev.cel.common.CelDescriptors;
import dev.cel.common.CelOptions;
import dev.cel.common.internal.DefaultDescriptorPool;
import dev.cel.common.internal.DefaultMessageFactory;
import dev.cel.runtime.Activation;
import dev.cel.runtime.DefaultDispatcher;
import dev.cel.runtime.DefaultInterpreter;
import dev.cel.runtime.DescriptorMessageProvider;
import dev.cel.runtime.Interpreter;
import dev.cel.runtime.InterpreterException;
import dev.cel.runtime.Registrar;
import dev.cel.runtime.RuntimeTypeProvider;

/** The {@code EvalSync} class represents common concerns for synchronous evaluation. */
public final class EvalSync implements Eval {

  private final ImmutableList<FileDescriptor> fileDescriptors;
  private final DefaultDispatcher dispatcher;
  private final Interpreter interpreter;
  private final RuntimeTypeProvider typeProvider;
  private final CelOptions celOptions;

  public EvalSync(ImmutableList<FileDescriptor> fileDescriptors, CelOptions celOptions) {
    this.fileDescriptors = fileDescriptors;
    this.dispatcher = DefaultDispatcher.create(celOptions);
    CelDescriptors celDescriptors =
        CelDescriptorUtil.getAllDescriptorsFromFileDescriptor(fileDescriptors);
    this.typeProvider =
        new DescriptorMessageProvider(
            DefaultMessageFactory.create(DefaultDescriptorPool.create(celDescriptors)), celOptions);
    this.interpreter = new DefaultInterpreter(typeProvider, dispatcher, celOptions);
    this.celOptions = celOptions;
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
  public Object eval(CheckedExpr checkedExpr, Activation activation) throws Exception {
    return interpreter.createInterpretable(checkedExpr).eval(activation);
  }
}
