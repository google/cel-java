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
import dev.cel.bundle.Cel;
import dev.cel.common.CelFunctionDecl;
import dev.cel.common.CelOptions;
import dev.cel.common.CelValidationException;
import dev.cel.common.types.SimpleType;
import dev.cel.common.values.CelByteString;
import dev.cel.runtime.CelEvaluationException;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(TestParameterInjector.class)
public class CelEncoderExtensionsTest extends CelExtensionTestBase {
  private static final CelOptions CEL_OPTIONS =
      CelOptions.current().enableHeterogeneousNumericComparisons(true).build();

  @Override
  protected Cel newCelEnv() {
    return runtimeFlavor
        .builder()
        .setOptions(CEL_OPTIONS)
        .addCompilerLibraries(CelExtensions.encoders(CEL_OPTIONS))
        .addRuntimeLibraries(CelExtensions.encoders(CEL_OPTIONS))
        .addVar("stringVar", SimpleType.STRING)
        .build();
  }

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
    String encodedBytes = (String) eval("base64.encode(b'hello')");

    assertThat(encodedBytes).isEqualTo("aGVsbG8=");
  }

  @Test
  public void decode_success() throws Exception {
    CelByteString decodedBytes = (CelByteString) eval("base64.decode('aGVsbG8=')");

    assertThat(decodedBytes.size()).isEqualTo(5);
    assertThat(new String(decodedBytes.toByteArray(), ISO_8859_1)).isEqualTo("hello");
  }

  @Test
  public void decode_withoutPadding_success() throws Exception {
    CelByteString decodedBytes = (CelByteString) eval("base64.decode('aGVsbG8')");

    assertThat(decodedBytes.size()).isEqualTo(5);
    assertThat(new String(decodedBytes.toByteArray(), ISO_8859_1)).isEqualTo("hello");
  }

  @Test
  public void roundTrip_success() throws Exception {
    String encodedString = (String) eval("base64.encode(b'Hello World!')");
    CelByteString decodedBytes =
        (CelByteString)
            eval("base64.decode(stringVar)", ImmutableMap.of("stringVar", encodedString));

    assertThat(new String(decodedBytes.toByteArray(), ISO_8859_1)).isEqualTo("Hello World!");
  }

  @Test
  public void encode_invalidParam_throwsCompilationException() {
    Assume.assumeFalse(isParseOnly);
    CelValidationException e =
        assertThrows(
            CelValidationException.class, () -> cel.compile("base64.encode('hello')").getAst());

    assertThat(e).hasMessageThat().contains("found no matching overload for 'base64.encode'");
  }

  @Test
  public void decode_invalidParam_throwsCompilationException() {
    Assume.assumeFalse(isParseOnly);
    CelValidationException e =
        assertThrows(
            CelValidationException.class, () -> cel.compile("base64.decode(b'aGVsbG8=')").getAst());

    assertThat(e).hasMessageThat().contains("found no matching overload for 'base64.decode'");
  }

  @Test
  public void decode_malformedBase64Char_throwsEvaluationException() throws Exception {
    CelEvaluationException e =
        assertThrows(CelEvaluationException.class, () -> eval("base64.decode('z!')"));

    assertThat(e).hasMessageThat().contains("failed with arg(s) 'z!'");
    assertThat(e).hasCauseThat().hasMessageThat().contains("Illegal base64 character");
  }


}
