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

import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.google.common.collect.ImmutableSet;
import com.google.errorprone.annotations.Immutable;
import com.google.protobuf.ByteString;
import dev.cel.checker.CelCheckerBuilder;
import dev.cel.common.CelFunctionDecl;
import dev.cel.common.CelOptions;
import dev.cel.common.CelOverloadDecl;
import dev.cel.common.types.SimpleType;
import dev.cel.common.values.CelByteString;
import dev.cel.compiler.CelCompilerLibrary;
import dev.cel.runtime.CelFunctionBinding;
import dev.cel.runtime.CelRuntimeBuilder;
import dev.cel.runtime.CelRuntimeLibrary;
import java.util.Base64;
import java.util.Base64.Decoder;
import java.util.Base64.Encoder;

/** Internal implementation of Encoder Extensions. */
@Immutable
public class CelEncoderExtensions
    implements CelCompilerLibrary, CelRuntimeLibrary, CelExtensionLibrary.FeatureSet {

  private static final Encoder BASE64_ENCODER = Base64.getEncoder();

  private static final Decoder BASE64_DECODER = Base64.getDecoder();

  private final ImmutableSet<Function> functions;
  private final CelOptions celOptions;

  enum Function {
    DECODE(
        CelFunctionDecl.newFunctionDeclaration(
            "base64.decode",
            CelOverloadDecl.newGlobalOverload(
                "base64_decode_string", SimpleType.BYTES, SimpleType.STRING)),
        CelFunctionBinding.from(
            "base64_decode_string",
            String.class,
            str -> CelByteString.of(BASE64_DECODER.decode(str))),
        CelFunctionBinding.from(
            "base64_decode_string",
            String.class,
            str -> ByteString.copyFrom(BASE64_DECODER.decode(str)))),
    ENCODE(
        CelFunctionDecl.newFunctionDeclaration(
            "base64.encode",
            CelOverloadDecl.newGlobalOverload(
                "base64_encode_bytes", SimpleType.STRING, SimpleType.BYTES)),
        CelFunctionBinding.from(
            "base64_encode_bytes",
            CelByteString.class,
            bytes -> BASE64_ENCODER.encodeToString(bytes.toByteArray())),
        CelFunctionBinding.from(
            "base64_encode_bytes",
            ByteString.class,
            bytes -> BASE64_ENCODER.encodeToString(bytes.toByteArray()))),
    ;

    private final CelFunctionDecl functionDecl;
    private final CelFunctionBinding nativeBytesFunctionBinding;
    private final CelFunctionBinding protoBytesFunctionBinding;

    String getFunction() {
      return functionDecl.name();
    }

    Function(
        CelFunctionDecl functionDecl,
        CelFunctionBinding nativeBytesFunctionBinding,
        CelFunctionBinding protoBytesFunctionBinding) {
      this.functionDecl = functionDecl;
      this.nativeBytesFunctionBinding = nativeBytesFunctionBinding;
      this.protoBytesFunctionBinding = protoBytesFunctionBinding;
    }
  }

  private static final class Library implements CelExtensionLibrary<CelEncoderExtensions> {
    private final CelEncoderExtensions version0;

    @Override
    public String name() {
      return "encoders";
    }

    @Override
    public ImmutableSet<CelEncoderExtensions> versions() {
      return ImmutableSet.of(version0);
    }

    private Library(CelOptions celOptions) {
      this.version0 = new CelEncoderExtensions(celOptions);
    }
  }

  static CelExtensionLibrary<CelEncoderExtensions> library(CelOptions celOptions) {
    return new Library(celOptions);
  }

  @Override
  public int version() {
    return 0;
  }

  @Override
  public ImmutableSet<CelFunctionDecl> functions() {
    return functions.stream().map(f -> f.functionDecl).collect(toImmutableSet());
  }

  @Override
  public void setCheckerOptions(CelCheckerBuilder checkerBuilder) {
    functions.forEach(function -> checkerBuilder.addFunctionDeclarations(function.functionDecl));
  }

  @SuppressWarnings("Immutable") // Instances of java.util.Base64 are immutable
  @Override
  public void setRuntimeOptions(CelRuntimeBuilder runtimeBuilder) {
    functions.forEach(
        function -> {
          if (celOptions.evaluateCanonicalTypesToNativeValues()) {
            runtimeBuilder.addFunctionBindings(function.nativeBytesFunctionBinding);
          } else {
            runtimeBuilder.addFunctionBindings(function.protoBytesFunctionBinding);
          }
        });
  }

  CelEncoderExtensions(CelOptions celOptions) {
    this.celOptions = celOptions;
    this.functions = ImmutableSet.copyOf(Function.values());
  }
}
