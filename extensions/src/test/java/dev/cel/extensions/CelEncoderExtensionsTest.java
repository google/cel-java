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

import static com.google.common.truth.Truth.assertThat;
import static java.nio.charset.StandardCharsets.ISO_8859_1;
import static org.junit.Assert.assertThrows;

import com.google.common.collect.ImmutableMap;
import com.google.testing.junit.testparameterinjector.TestParameterInjector;
import dev.cel.common.CelAbstractSyntaxTree;
import dev.cel.common.CelFunctionDecl;
import dev.cel.common.CelOptions;
import dev.cel.common.CelValidationException;
import dev.cel.common.types.SimpleType;
import dev.cel.common.values.CelByteString;
import dev.cel.compiler.CelCompiler;
import dev.cel.compiler.CelCompilerFactory;
import dev.cel.runtime.CelEvaluationException;
import dev.cel.runtime.CelRuntime;
import dev.cel.runtime.CelRuntimeFactory;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(TestParameterInjector.class)
public class CelEncoderExtensionsTest {
  private static final CelOptions CEL_OPTIONS =
      CelOptions.current().build();

  private static final CelCompiler CEL_COMPILER =
      CelCompilerFactory.standardCelCompilerBuilder()
          .addVar("stringVar", SimpleType.STRING)
          .addLibraries(CelExtensions.encoders(CEL_OPTIONS))
          .build();
  private static final CelRuntime CEL_RUNTIME =
      CelRuntimeFactory.standardCelRuntimeBuilder()
          .setOptions(CEL_OPTIONS)
          .addLibraries(CelExtensions.encoders(CEL_OPTIONS))
          .build();

  @Test
  public void library() {
    CelExtensionLibrary<?> library =
        CelExtensions.getExtensionLibrary("encoders", CelOptions.DEFAULT);
    assertThat(library.name()).isEqualTo("encoders");
    assertThat(library.latest().version()).isEqualTo(0);
    assertThat(library.version(0).functions().stream().map(CelFunctionDecl::name))
        .containsExactly("base64.decode", "base64.encode");
    assertThat(library.version(0).macros()).isEmpty();
  }

  @Test
  public void encode_success() throws Exception {
    String encodedBytes =
        (String)
            CEL_RUNTIME
                .createProgram(CEL_COMPILER.compile("base64.encode(b'hello')").getAst())
                .eval();

    assertThat(encodedBytes).isEqualTo("aGVsbG8=");
  }

  @Test
  public void decode_success() throws Exception {
    CelByteString decodedBytes =
        (CelByteString)
            CEL_RUNTIME
                .createProgram(CEL_COMPILER.compile("base64.decode('aGVsbG8=')").getAst())
                .eval();

    assertThat(decodedBytes.size()).isEqualTo(5);
    assertThat(new String(decodedBytes.toByteArray(), ISO_8859_1)).isEqualTo("hello");
  }

  @Test
  public void decode_withoutPadding_success() throws Exception {
    CelByteString decodedBytes =
        (CelByteString)
            CEL_RUNTIME
                // RFC2045 6.8, padding can be ignored.
                .createProgram(CEL_COMPILER.compile("base64.decode('aGVsbG8')").getAst())
                .eval();

    assertThat(decodedBytes.size()).isEqualTo(5);
    assertThat(new String(decodedBytes.toByteArray(), ISO_8859_1)).isEqualTo("hello");
  }

  @Test
  public void roundTrip_success() throws Exception {
    String encodedString =
        (String)
            CEL_RUNTIME
                .createProgram(CEL_COMPILER.compile("base64.encode(b'Hello World!')").getAst())
                .eval();
    CelByteString decodedBytes =
        (CelByteString)
            CEL_RUNTIME
                .createProgram(CEL_COMPILER.compile("base64.decode(stringVar)").getAst())
                .eval(ImmutableMap.of("stringVar", encodedString));

    assertThat(new String(decodedBytes.toByteArray(), ISO_8859_1)).isEqualTo("Hello World!");
  }

  @Test
  public void encode_invalidParam_throwsCompilationException() {
    CelValidationException e =
        assertThrows(
            CelValidationException.class,
            () -> CEL_COMPILER.compile("base64.encode('hello')").getAst());

    assertThat(e).hasMessageThat().contains("found no matching overload for 'base64.encode'");
  }

  @Test
  public void decode_invalidParam_throwsCompilationException() {
    CelValidationException e =
        assertThrows(
            CelValidationException.class,
            () -> CEL_COMPILER.compile("base64.decode(b'aGVsbG8=')").getAst());

    assertThat(e).hasMessageThat().contains("found no matching overload for 'base64.decode'");
  }

  @Test
  public void decode_malformedBase64Char_throwsEvaluationException() throws Exception {
    CelAbstractSyntaxTree ast = CEL_COMPILER.compile("base64.decode('z!')").getAst();

    CelEvaluationException e =
        assertThrows(CelEvaluationException.class, () -> CEL_RUNTIME.createProgram(ast).eval());

    assertThat(e)
        .hasMessageThat()
        .contains("Function 'base64_decode_string' failed with arg(s) 'z!'");
    assertThat(e).hasCauseThat().hasMessageThat().contains("Illegal base64 character");
  }
}
