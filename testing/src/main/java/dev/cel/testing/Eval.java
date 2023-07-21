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
import com.google.errorprone.annotations.CheckReturnValue;
import com.google.protobuf.Descriptors.FileDescriptor;
import dev.cel.common.CelOptions;
import dev.cel.runtime.Activation;
import dev.cel.runtime.InterpreterException;
import dev.cel.runtime.Registrar;

/**
 * The {@code Eval} interface is used to model the core concerns of CEL evaluation during testing.
 */
@CheckReturnValue
public interface Eval {
  /** Returns the set of file descriptors configured for evaluation. */
  ImmutableList<FileDescriptor> fileDescriptors();

  /** Returns the function / type registrar used during evaluation. */
  Registrar registrar();

  CelOptions celOptions();

  /** Adapts a Java POJO to a CEL value. */
  Object adapt(Object value) throws InterpreterException;

  /**
   * Evaluates a {@code CheckedExpr} against a set of inputs represented by the {@code Activation}.
   */
  Object eval(CheckedExpr checkedExpr, Activation activation) throws Exception;
}
