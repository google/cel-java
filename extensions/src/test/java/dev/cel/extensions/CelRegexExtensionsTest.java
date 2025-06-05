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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.testing.junit.testparameterinjector.TestParameter;
import com.google.testing.junit.testparameterinjector.TestParameterInjector;
import com.google.testing.junit.testparameterinjector.TestParameters;
import dev.cel.common.CelAbstractSyntaxTree;
import dev.cel.compiler.CelCompiler;
import dev.cel.compiler.CelCompilerFactory;
import dev.cel.runtime.CelEvaluationException;
import dev.cel.runtime.CelRuntime;
import dev.cel.runtime.CelRuntimeFactory;
import java.util.Map;
import java.util.Optional;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(TestParameterInjector.class)
public final class CelRegexExtensionsTest {

  private static final CelCompiler COMPILER =
      CelCompilerFactory.standardCelCompilerBuilder().addLibraries(CelExtensions.regex()).build();
  private static final CelRuntime RUNTIME =
      CelRuntimeFactory.standardCelRuntimeBuilder().addLibraries(CelExtensions.regex()).build();

  @Test
  @TestParameters("{target: 'foo bar', regex: '(fo)o (ba)r', replaceStr: '$2 $1', res: 'ba fo'}")
  @TestParameters("{target: 'banana', regex: 'ana', replaceStr: 'x', res: 'bxna'}")
  @TestParameters("{target: 'abc', regex: 'b(.)', replaceStr: 'x$1', res: 'axc'}")
  @TestParameters(
      "{target: 'hello world hello', regex: 'hello', replaceStr: 'hi', res: 'hi world hi'}")
  @TestParameters("{target: 'apple pie', regex: 'p', replaceStr: 'X', res: 'aXXle Xie'}")
  @TestParameters(
      "{target: 'remove all spaces', regex: '\\\\s', replaceStr: '', res: 'removeallspaces'}")
  @TestParameters("{target: 'digit:99919291992', regex: '\\\\d+', replaceStr: '3', res: 'digit:3'}")
  @TestParameters(
      "{target: 'foo bar baz', regex: '\\\\w+', replaceStr: '($0)', res: '(foo) (bar) (baz)'}")
  @TestParameters("{target: '', regex: 'a', replaceStr: 'b', res: ''}")
  public void replaceAll_success(String target, String regex, String replaceStr, String res)
      throws Exception {
    String expr = String.format("regex.replace('%s', '%s', '%s')", target, regex, replaceStr);
    CelRuntime.Program program = RUNTIME.createProgram(COMPILER.compile(expr).getAst());

    Object result = program.eval();

    assertThat(result).isEqualTo(res);
  }

  @Test
  @TestParameters("{t: 'banana', re: 'a', rep: 'x', i: 0, res: 'banana'}")
  @TestParameters("{t: 'banana', re: 'a', rep: 'x', i: 1, res: 'bxnana'}")
  @TestParameters("{t: 'banana', re: 'a', rep: 'x', i: 2, res: 'bxnxna'}")
  @TestParameters("{t: 'banana', re: 'a', rep: 'x', i: 100, res: 'bxnxnx'}")
  @TestParameters("{t: 'banana', re: 'a', rep: 'x', i: -1, res: 'bxnxnx'}")
  @TestParameters("{t: 'banana', re: 'a', rep: 'x', i: -100, res: 'banana'}")
  @TestParameters(
      "{t: 'cat-dog dog-cat cat-dog dog-cat', re: '(cat)-(dog)', rep: '$2-$1', i: 1,"
          + " res: 'dog-cat dog-cat cat-dog dog-cat'}")
  @TestParameters(
      "{t: 'cat-dog dog-cat cat-dog dog-cat', re: '(cat)-(dog)', rep: '$2-$1', i: 2, res: 'dog-cat"
          + " dog-cat dog-cat dog-cat'}")
  @TestParameters("{t: 'a.b.c', re: '\\\\.', rep: '-', i: 1, res: 'a-b.c'}")
  @TestParameters("{t: 'a.b.c', re: '\\\\.', rep: '-', i: -1, res: 'a-b-c'}")
  public void replaceCount_success(String t, String re, String rep, long i, String res)
      throws Exception {
    String expr = String.format("regex.replace('%s', '%s', '%s', %d)", t, re, rep, i);
    System.out.println("expr: " + expr);
    CelRuntime.Program program = RUNTIME.createProgram(COMPILER.compile(expr).getAst());

    Object result = program.eval();

    assertThat(result).isEqualTo(res);
  }

  @Test
  @TestParameters("{target: 'foo bar', regex: '(', replaceStr: '$2 $1'}")
  @TestParameters("{target: 'foo bar', regex: '[a-z', replaceStr: '$2 $1'}")
  public void replace_invalid_regex(String target, String regex, String replaceStr)
      throws Exception {
    String expr = String.format("regex.replace('%s', '%s', '%s')", target, regex, replaceStr);
    CelAbstractSyntaxTree ast = COMPILER.compile(expr).getAst();

    CelEvaluationException e =
        assertThrows(CelEvaluationException.class, () -> RUNTIME.createProgram(ast).eval());

    assertThat(e).hasCauseThat().isInstanceOf(IllegalArgumentException.class);
    assertThat(e).hasCauseThat().hasMessageThat().contains("Failed to compile regex: ");
  }

  @Test
  @TestParameters("{target: 'test', regex: '(.)', replaceStr: '$2'}")
  public void replace_invalid_captureGroup(String target, String regex, String replaceStr)
      throws Exception {
    String expr = String.format("regex.replace('%s', '%s', '%s')", target, regex, replaceStr);
    CelAbstractSyntaxTree ast = COMPILER.compile(expr).getAst();

    CelEvaluationException e =
        assertThrows(CelEvaluationException.class, () -> RUNTIME.createProgram(ast).eval());

    assertThat(e).hasCauseThat().isInstanceOf(IndexOutOfBoundsException.class);
    assertThat(e).hasCauseThat().hasMessageThat().contains("n > number of groups");
  }

  @Test
  @TestParameters("{target: 'hello world', regex: 'hello(.*)', expectedResult: ' world'}")
  @TestParameters("{target: 'item-A, item-B', regex: 'item-(\\\\w+)', expectedResult: 'A'}")
  @TestParameters(
      "{target: 'The color is red', regex: 'The color is (\\\\w+)', expectedResult: 'red'}")
  @TestParameters(
      "{target: 'The color is red', regex: 'The color is \\\\w+', expectedResult: 'The color is"
          + " red'}")
  @TestParameters(
      "{target: 'phone: 415-5551212', regex: 'phone: ((\\\\d{3})-)?', expectedResult: '415-'}")
  @TestParameters("{target: 'brand', regex: 'brand', expectedResult: 'brand'}")
  public void capture_success(String target, String regex, String expectedResult) throws Exception {
    String expr = String.format("regex.capture('%s', '%s')", target, regex);
    CelRuntime.Program program = RUNTIME.createProgram(COMPILER.compile(expr).getAst());

    Object result = program.eval();

    assertThat(result).isInstanceOf(Optional.class);
    assertThat((Optional<?>) result).hasValue(expectedResult);
  }

  @Test
  @TestParameters("{target: 'hello world', regex: 'goodbye (.*)'}")
  @TestParameters("{target: 'phone: 5551212', regex: 'phone: ((\\\\d{3})-)?'}")
  @TestParameters("{target: 'HELLO', regex: 'hello'}")
  @TestParameters("{target: '', regex: '\\\\w+'}")
  public void capture_no_match(String target, String regex) throws Exception {
    String expr = String.format("regex.capture('%s', '%s')", target, regex);
    CelRuntime.Program program = RUNTIME.createProgram(COMPILER.compile(expr).getAst());

    Object result = program.eval();

    assertThat(result).isInstanceOf(Optional.class);
    assertThat((Optional<?>) result).isEmpty();
  }

  @SuppressWarnings("ImmutableEnumChecker") // Test only
  private enum CaptureAllTestCase {
    NO_MATCH("regex.captureAll('id:123, id:456', 'assa')", ImmutableList.of()),
    OPTIONAL_MATCH_GROUP_NULL(
        "regex.captureAll('phone: 5551212', 'phone: ((\\\\d{3})-)?')", ImmutableList.of()),
    NO_CAPTURE_GROUP(
        "regex.captureAll('id:123, id:456', 'id:\\\\d+')", ImmutableList.of("id:123", "id:456")),
    SINGLE_NAMED_GROUP(
        "regex.captureAll('testuser@', '(?P<username>.*)@')", ImmutableList.of("testuser")),
    SINGLE_NAMED_MULTIPLE_MATCH_GROUP(
        "regex.captureAll('banananana', '(ana)')", ImmutableList.of("ana", "ana")),
    MULTIPLE_NAMED_GROUP(
        "regex.captureAll('Name: John Doe, Age:321', 'Name: (?P<Name>.*),"
            + " Age:(?P<Age>\\\\d+)')",
        ImmutableList.of("John Doe", "321")),
    UNNAMED_GROUP(
        "regex.captureAll('testuser@testdomain', '(.*)@([^.]*)')",
        ImmutableList.of("testuser", "testdomain")),
    NAMED_UNNAMED_COMBINED_GROUP(
        "regex.captureAll('The user testuser belongs to testdomain',"
            + "'The (user|domain) (?P<Username>.*) belongs (to) (?P<Domain>.*)')",
        ImmutableList.of("user", "testuser", "to", "testdomain"));
    private final String expr;
    private final ImmutableList<String> expectedResult;

    CaptureAllTestCase(String expr, ImmutableList<String> expectedResult) {
      this.expr = expr;
      this.expectedResult = expectedResult;
    }
  }

  @Test
  public void captureAll_success(@TestParameter CaptureAllTestCase testCase) throws Exception {
    CelAbstractSyntaxTree ast = COMPILER.compile(testCase.expr).getAst();

    Object result = RUNTIME.createProgram(ast).eval();

    assertThat(result).isEqualTo(testCase.expectedResult);
  }

  @SuppressWarnings("ImmutableEnumChecker") // Test only
  private enum CaptureAllNamedTestCase {
    SINGLE_NAMED_GROUP(
        "regex.captureAllNamed('testuser@', '(?P<username>.*)@')",
        ImmutableMap.of("username", "testuser")),
    MULTIPLE_NAMED_GROUP(
        "regex.captureAllNamed('Name: John Doe, Age:321', 'Name: (?P<Name>.*),"
            + " Age:(?P<Age>\\\\d+)')",
        ImmutableMap.of("Name", "John Doe", "Age", "321")),
    NO_MATCH("regex.captureAllNamed('id:123, id:456', 'assa')", ImmutableMap.of()),
    NO_CAPTURE_GROUP("regex.captureAllNamed('id:123, id:456', 'id:\\\\d+')", ImmutableMap.of()),
    UNNAMED_GROUPS_ONLY(
        "regex.captureAllNamed('testuser@testdomain', '(.*)@([^.]*)')", ImmutableMap.of()),
    NAMED_UNNAMED_COMBINED_GROUP(
        "regex.captureAllNamed('The user testuser belongs to testdomain',"
            + "'The (user|domain) (?P<Username>.*) belongs to (?P<Domain>.*)')",
        ImmutableMap.of("Username", "testuser", "Domain", "testdomain")),
    EMPTY_TARGET_STRING("regex.captureAllNamed('', '(?P<name>\\\\w+)')", ImmutableMap.of()),
    NAMED_GROUP_CAPTURES_EMPTY_STRING(
        "regex.captureAllNamed('id=', 'id=(?P<idValue>.*)')", ImmutableMap.of("idValue", ""));
    private final String expr;
    private final Map<String, String> expectedResult;

    CaptureAllNamedTestCase(String expr, Map<String, String> expectedResult) {
      this.expr = expr;
      this.expectedResult = expectedResult;
    }
  }

  @Test
  public void captureAllNamed_success(@TestParameter CaptureAllNamedTestCase testCase)
      throws Exception {
    CelAbstractSyntaxTree ast = COMPILER.compile(testCase.expr).getAst();

    Object result = RUNTIME.createProgram(ast).eval();

    assertThat(result).isEqualTo(testCase.expectedResult);
  }
}
