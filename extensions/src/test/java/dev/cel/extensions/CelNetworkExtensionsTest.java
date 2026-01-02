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

package dev.cel.extensions;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import com.google.testing.junit.testparameterinjector.TestParameterInjector;
import com.google.testing.junit.testparameterinjector.TestParameters;
import dev.cel.common.CelAbstractSyntaxTree;
import dev.cel.common.CelValidationException;
import dev.cel.compiler.CelCompiler;
import dev.cel.compiler.CelCompilerFactory;
import dev.cel.runtime.CelEvaluationException;
import dev.cel.runtime.CelRuntime;
import dev.cel.runtime.CelRuntimeFactory;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(TestParameterInjector.class)
public final class CelNetworkExtensionsTest {

  private static final CelCompiler COMPILER =
      CelCompilerFactory.standardCelCompilerBuilder()
          .addLibraries(new CelNetworkExtensions())
          .build();

  private static final CelRuntime RUNTIME =
      CelRuntimeFactory.standardCelRuntimeBuilder()
          .addLibraries(new CelNetworkExtensions())
          .build();

  private Object eval(String expression) throws CelEvaluationException, CelValidationException {
    CelAbstractSyntaxTree ast = COMPILER.compile(expression).getAst();
    return RUNTIME.createProgram(ast).eval();
  }

  // --- Global Checks (isIP, isCIDR) ---
  @Test
  @TestParameters({
    "{expr: 'isIP(\"1.2.3.4\")', expected: true}",
    "{expr: 'isIP(\"2001:db8::1\")', expected: true}",
    "{expr: 'isIP(\"not.an.ip\")', expected: false}",
    "{expr: 'isIP(\"127.0.0.1:80\")', expected: false}",
    "{expr: 'isIP(\"[2001:db8::1]:80\")', expected: false}",
    "{expr: 'isIP(\"1.2.3.4%\")', expected: false}",
  })
  public void isIP_testCases(String expr, boolean expected) throws Exception {
    assertThat(eval(expr)).isEqualTo(expected);
  }

  @Test
  @TestParameters({
    "{expr: 'isCIDR(\"10.0.0.0/8\")', expected: true}",
    "{expr: 'isCIDR(\"10.0.0.1/8\")', expected: true}",
    "{expr: 'isCIDR(\"2001:db8::/32\")', expected: true}",
    "{expr: 'isCIDR(\"10.0.0.0/33\")', expected: false}",
    "{expr: 'isCIDR(\"10.0.0.0/999\")', expected: false}",
    "{expr: 'isCIDR(\"10.0.0.0\")', expected: false}",
    "{expr: 'isCIDR(\"invalid\")', expected: false}",
  })
  public void isCIDR_testCases(String expr, boolean expected) throws Exception {
    assertThat(eval(expr)).isEqualTo(expected);
  }

  // --- IP Constructors & Equality ---
  @Test
  @TestParameters({
    "{expr: 'ip(\"127.0.0.1\") == ip(\"127.0.0.1\")', expected: true}",
    "{expr: 'ip(\"127.0.0.1\") == ip(\"1.2.3.4\")', expected: false}",
    "{expr: 'ip(\"2001:db8::1\") == ip(\"2001:DB8::1\")', expected: true}",
    "{expr: 'ip(\"2001:db8::1\") == ip(\"2001:db8::2\")', expected: false}",
  })
  public void ip_equality(String expr, boolean expected) throws Exception {
    assertThat(eval(expr)).isEqualTo(expected);
  }

  // --- String Conversion ---
  @Test
  @TestParameters({
    "{expr: 'ip(\"1.2.3.4\").string()', expected: \"1.2.3.4\"}",
    "{expr: 'ip(\"2001:db8::1\").string()', expected: \"2001:db8::1\"}",
    "{expr: 'cidr(\"10.0.0.0/8\").string()', expected: \"10.0.0.0/8\"}",
    "{expr: 'cidr(\"10.0.0.1/8\").string()', expected: \"10.0.0.1/8\"}",
    "{expr: 'cidr(\"::1/128\").string()', expected: \"::1/128\"}",
  })
  public void string_conversion(String expr, String expected) throws Exception {
    assertThat(eval(expr)).isEqualTo(expected);
  }

  // --- Family ---
  @Test
  @TestParameters({
    "{expr: 'ip(\"127.0.0.1\").family()', expected: 4}",
    "{expr: 'ip(\"::1\").family()', expected: 6}",
  })
  public void ip_family(String expr, long expected) throws Exception {
    assertThat(eval(expr)).isEqualTo(expected);
  }

  // --- Canonicalization ---
  @Test
  @TestParameters({
    "{expr: 'ip(\"127.0.0.1\").isCanonical()', expected: true}",
    "{expr: 'ip(\"2001:db8::1\").isCanonical()', expected: true}",
    "{expr: 'ip(ip(\"2001:DB8::1\").string()).isCanonical()', expected: true}",
    "{expr: 'ip(ip(\"2001:db8:0:0:0:0:0:1\").string()).isCanonical()', expected:" + " true}",
  })
  public void ip_isCanonical(String expr, boolean expected) throws Exception {
    assertThat(eval(expr)).isEqualTo(expected);
  }

  // --- IP Types (Loopback, Unspecified, etc) ---
  @Test
  @TestParameters({
    "{expr: 'ip(\"127.0.0.1\").isLoopback()', expected: true}",
    "{expr: 'ip(\"::1\").isLoopback()', expected: true}",
    "{expr: 'ip(\"192.168.0.1\").isLoopback()', expected: false}",
    "{expr: 'ip(\"0.0.0.0\").isUnspecified()', expected: true}",
    "{expr: 'ip(\"::\").isUnspecified()', expected: true}",
    "{expr: 'ip(\"1.2.3.4\").isUnspecified()', expected: false}",
    "{expr: 'ip(\"8.8.8.8\").isGlobalUnicast()', expected: true}",
    "{expr: 'ip(\"192.168.0.1\").isGlobalUnicast()', expected: false}", // Private
    "{expr: 'ip(\"127.0.0.1\").isGlobalUnicast()', expected: false}", // Loopback
    "{expr: 'ip(\"ff02::1\").isLinkLocalMulticast()', expected: true}",
    "{expr: 'ip(\"224.0.0.1\").isLinkLocalMulticast()', expected: true}",
    "{expr: 'ip(\"224.0.1.1\").isLinkLocalMulticast()', expected: false}",
    "{expr: 'ip(\"fe80::1\").isLinkLocalUnicast()', expected: true}",
    "{expr: 'ip(\"169.254.0.1\").isLinkLocalUnicast()', expected: true}",
  })
  public void ip_types(String expr, boolean expected) throws Exception {
    assertThat(eval(expr)).isEqualTo(expected);
  }

  // --- CIDR Accessors ---
  @Test
  @TestParameters({
    "{expr: 'cidr(\"192.168.0.0/24\").prefixLength()', expected: 24}",
    "{expr: 'cidr(\"2001:db8::/32\").prefixLength()', expected: 32}",
  })
  public void cidr_prefixLength(String expr, long expected) throws Exception {
    assertThat(eval(expr)).isEqualTo(expected);
  }

  @Test
  @TestParameters({
    "{expr: 'cidr(\"192.168.0.0/24\").ip() == ip(\"192.168.0.0\")', expected: true}",
    "{expr: 'cidr(\"192.168.1.5/24\").ip() == ip(\"192.168.1.5\")', expected: true}",
    "{expr: 'cidr(\"2001:db8::1/128\").ip() == ip(\"2001:db8::1\")', expected: true}",
  })
  public void cidr_ip_extraction(String expr, boolean expected) throws Exception {
    assertThat(eval(expr)).isEqualTo(expected);
  }

  @Test
  @TestParameters({
    "{expr: 'cidr(\"192.168.1.5/24\").masked().string()', expected: \"192.168.1.0/24\"}",
    "{expr: 'cidr(\"192.168.1.0/24\").masked().string()', expected: \"192.168.1.0/24\"}",
    "{expr: 'cidr(\"2001:db8:abcd:1234::1/64\").masked().string()', expected:"
        + " \"2001:db8:abcd:1234::/64\"}",
  })
  public void cidr_masked(String expr, String expected) throws Exception {
    assertThat(eval(expr)).isEqualTo(expected);
  }

  // --- Containment (IP in CIDR) ---
  @Test
  @TestParameters({
    "{expr: 'cidr(\"10.0.0.0/8\").containsIP(ip(\"10.1.2.3\"))', expected: true}",
    "{expr: 'cidr(\"10.0.0.0/8\").containsIP(ip(\"11.0.0.0\"))', expected: false}",
    "{expr: 'cidr(\"10.0.0.0/8\").containsIP(\"10.255.255.255\")', expected: true}",
    "{expr: 'cidr(\"2001:db8::/32\").containsIP(\"2001:db8:ffff::1\")', expected: true}",
    "{expr: 'cidr(\"2001:db8::/32\").containsIP(\"2001:db9::\")', expected: false}",
  })
  public void cidr_containsIP(String expr, boolean expected) throws Exception {
    assertThat(eval(expr)).isEqualTo(expected);
  }

  // --- Containment (CIDR in CIDR) ---
  @Test
  @TestParameters({
    "{expr: 'cidr(\"10.0.0.0/8\").containsCIDR(cidr(\"10.1.0.0/16\"))', expected: true}",
    "{expr: 'cidr(\"10.1.0.0/16\").containsCIDR(cidr(\"10.0.0.0/8\"))', expected: false}",
    "{expr: 'cidr(\"10.0.0.0/8\").containsCIDR(\"10.0.0.0/8\")', expected: true}",
    "{expr: 'cidr(\"2001:db8::/32\").containsCIDR(\"2001:db8:abcd::/48\")', expected: true}",
    "{expr: 'cidr(\"10.0.0.0/8\").containsCIDR(\"11.0.0.0/8\")', expected: false}",
    "{expr: 'cidr(\"192.168.1.0/24\").containsCIDR(\"192.168.1.128/25\")', expected: true}",
    "{expr: 'cidr(\"192.168.1.128/25\").containsCIDR(\"192.168.1.0/24\")', expected: false}",
  })
  public void cidr_containsCIDR(String expr, boolean expected) throws Exception {
    assertThat(eval(expr)).isEqualTo(expected);
  }

  // --- Runtime Errors ---
  @Test
  public void err_ip_invalid() {
    CelEvaluationException e =
        assertThrows(CelEvaluationException.class, () -> eval("ip('999.999.999.999')"));
    assertThat(e).hasCauseThat().isInstanceOf(IllegalArgumentException.class);
    assertThat(e).hasCauseThat().hasMessageThat().contains("Invalid IP address string");
  }

  @Test
  public void err_cidr_invalidFormat() {
    CelEvaluationException e =
        assertThrows(CelEvaluationException.class, () -> eval("cidr('1.2.3.4')"));
    assertThat(e).hasCauseThat().isInstanceOf(IllegalArgumentException.class);
    assertThat(e).hasCauseThat().hasMessageThat().contains("Invalid CIDR string format");
  }

  @Test
  public void err_cidr_invalidMask() {
    CelEvaluationException e =
        assertThrows(CelEvaluationException.class, () -> eval("cidr('10.0.0.0/999')"));
    assertThat(e).hasCauseThat().isInstanceOf(IllegalArgumentException.class);
    assertThat(e).hasCauseThat().hasMessageThat().contains("Invalid prefix length");
  }

  @Test
  public void err_containsIP_stringInvalid() {
    CelEvaluationException e =
        assertThrows(
            CelEvaluationException.class, () -> eval("cidr('10.0.0.0/8').containsIP('not-an-ip')"));
    assertThat(e).hasCauseThat().isInstanceOf(IllegalArgumentException.class);
    assertThat(e).hasCauseThat().hasMessageThat().contains("Invalid IP string in containsIP");
  }

  @Test
  public void err_containsCIDR_stringInvalid() {
    CelEvaluationException e =
        assertThrows(
            CelEvaluationException.class,
            () -> eval("cidr('10.0.0.0/8').containsCIDR('not-a-cidr')"));
    assertThat(e).hasCauseThat().isInstanceOf(IllegalArgumentException.class);
    assertThat(e).hasCauseThat().hasMessageThat().contains("Invalid CIDR string in containsCIDR");
  }

  @Test
  @TestParameters({
    "{ip: '192.168.1.1', expected: false}",
    "{ip: '127.0.0.1', expected: false}",
    "{ip: '0.0.0.0', expected: false}",
    "{ip: '169.254.0.1', expected: true}",
    "{ip: '::1', expected: false}",
    "{ip: '2001:db8::1', expected: false}",
    "{ip: 'fe80::1', expected: true}",
    "{ip: 'ff02::1', expected: false}", // Multicast
  })
  public void ip_isLinkLocalUnicast_testCases(String ip, boolean expected) throws Exception {
    assertThat(eval(String.format("ip('%s').isLinkLocalUnicast()", ip))).isEqualTo(expected);
  }
}
