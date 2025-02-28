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

/** Factory class for producing a lite runtime environment. */
public final class CelLiteRuntimeFactory {

  /** Create a new builder for constructing a {@code CelLiteRuntime} instance. */
  public static CelLiteRuntimeBuilder newLiteRuntimeBuilder() {
    return LiteRuntimeImpl.newBuilder();
  }

  private CelLiteRuntimeFactory() {}
}
