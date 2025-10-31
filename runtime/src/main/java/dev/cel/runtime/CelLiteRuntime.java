// Copyright 2025 Google LLC
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

package dev.cel.runtime;

import javax.annotation.concurrent.ThreadSafe;
import dev.cel.common.CelAbstractSyntaxTree;
import dev.cel.common.annotations.Beta;

/**
 * CelLiteRuntime creates executable {@link Program} instances from {@link CelAbstractSyntaxTree}
 * values.
 *
 * <p>CelLiteRuntime supports protolite messages, and does not directly depend on full-version of
 * the protobuf, making it suitable for use in Android.
 */
@ThreadSafe
@Beta
public interface CelLiteRuntime {

  Program createProgram(CelAbstractSyntaxTree ast) throws CelEvaluationException;

  CelLiteRuntimeBuilder toRuntimeBuilder();
}
