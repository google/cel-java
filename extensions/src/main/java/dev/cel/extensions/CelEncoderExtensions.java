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

package dev.cel.extensions;

import com.google.errorprone.annotations.Immutable;
import com.google.protobuf.ByteString;
import dev.cel.checker.CelCheckerBuilder;
import dev.cel.common.CelFunctionDecl;
import dev.cel.common.CelOverloadDecl;
import dev.cel.common.types.SimpleType;
import dev.cel.compiler.CelCompilerLibrary;
import dev.cel.runtime.CelRuntime;
import dev.cel.runtime.CelRuntimeBuilder;
import dev.cel.runtime.CelRuntimeLibrary;
import java.util.Base64;
import java.util.Base64.Decoder;
import java.util.Base64.Encoder;

/** Internal implementation of Encoder Extensions. */
@Immutable
public class CelEncoderExtensions implements CelCompilerLibrary, CelRuntimeLibrary {
  private static final Encoder BASE64_ENCODER = Base64.getEncoder();

  private static final Decoder BASE64_DECODER = Base64.getDecoder();

  @Override
  public void setCheckerOptions(CelCheckerBuilder checkerBuilder) {
    checkerBuilder.addFunctionDeclarations(
        CelFunctionDecl.newFunctionDeclaration(
            "base64.decode",
            CelOverloadDecl.newGlobalOverload(
                "base64_decode_string", SimpleType.BYTES, SimpleType.STRING)),
        CelFunctionDecl.newFunctionDeclaration(
            "base64.encode",
            CelOverloadDecl.newGlobalOverload(
                "base64_encode_bytes", SimpleType.STRING, SimpleType.BYTES)));
  }

  @SuppressWarnings("Immutable") // Instances of java.util.Base64 are immutable
  @Override
  public void setRuntimeOptions(CelRuntimeBuilder runtimeBuilder) {
    runtimeBuilder.addFunctionBindings(
        CelRuntime.CelFunctionBinding.from(
            "base64_decode_string",
            String.class,
            str -> ByteString.copyFrom(BASE64_DECODER.decode(str))),
        CelRuntime.CelFunctionBinding.from(
            "base64_encode_bytes",
            ByteString.class,
            bytes -> BASE64_ENCODER.encodeToString(bytes.toByteArray())));
  }
}

