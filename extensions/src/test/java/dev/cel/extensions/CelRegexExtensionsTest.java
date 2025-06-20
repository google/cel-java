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
import com.google.testing.junit.testparameterinjector.TestParameter;
import com.google.testing.junit.testparameterinjector.TestParameterInjector;
import com.google.testing.junit.testparameterinjector.TestParameters;
import dev.cel.common.CelAbstractSyntaxTree;
import dev.cel.compiler.CelCompiler;
import dev.cel.compiler.CelCompilerFactory;
import dev.cel.runtime.CelEvaluationException;
import dev.cel.runtime.CelRuntime;
import dev.cel.runtime.CelRuntimeFactory;
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
  @TestParameters("{target: 'abc', regex: '^', replaceStr: 'start_', res: 'start_abc'}")
  @TestParameters("{target: 'abc', regex: '$', replaceStr: '_end', res: 'abc_end'}")
  @TestParameters("{target: 'a-b', regex: '\\\\b', replaceStr: '|', res: '|a|-|b|'}")
  @TestParameters(
      "{target: 'foo bar', regex: '(fo)o (ba)r', replaceStr: '\\\\2 \\\\1', res: 'ba fo'}")
  @TestParameters("{target: 'foo bar', regex: 'foo', replaceStr: '\\\\\\\\', res: '\\ bar'}")
  @TestParameters("{target: 'banana', regex: 'ana', replaceStr: 'x', res: 'bxna'}")
  @TestParameters("{target: 'abc', regex: 'b(.)', replaceStr: 'x\\\\1', res: 'axc'}")
  @TestParameters(
      "{target: 'hello world hello', regex: 'hello', replaceStr: 'hi', res: 'hi world hi'}")
  @TestParameters("{target: 'ac', regex: 'a(b)?c', replaceStr: '[\\\\1]', res: '[]'}")
  @TestParameters("{target: 'apple pie', regex: 'p', replaceStr: 'X', res: 'aXXle Xie'}")
  @TestParameters(
      "{target: 'remove all spaces', regex: '\\\\s', replaceStr: '', res: 'removeallspaces'}")
  @TestParameters("{target: 'digit:99919291992', regex: '\\\\d+', replaceStr: '3', res: 'digit:3'}")
  @TestParameters(
      "{target: 'foo bar baz', regex: '\\\\w+', replaceStr: '(\\\\0)', res: '(foo) (bar) (baz)'}")
  @TestParameters("{target: '', regex: 'a', replaceStr: 'b', res: ''}")
  @TestParameters(
      "{target: 'User: Alice, Age: 30', regex: 'User: (?P<name>\\\\w+), Age: (?P<age>\\\\d+)',"
          + " replaceStr: '${name} is ${age} years old', res: '${name} is ${age} years old'}")
  @TestParameters(
      "{target: 'User: Alice, Age: 30', regex: 'User: (?P<name>\\\\w+), Age: (?P<age>\\\\d+)',"
          + " replaceStr: '\\\\1 is \\\\2 years old', res: 'Alice is 30 years old'}")
  @TestParameters("{target: 'hello ☃', regex: '☃', replaceStr: '❄', res: 'hello ❄'}")
  public void replaceAll_success(String target, String regex, String replaceStr, String res)
      throws Exception {
    String expr = String.format("regex.replace('%s', '%s', '%s')", target, regex, replaceStr);
    CelRuntime.Program program = RUNTIME.createProgram(COMPILER.compile(expr).getAst());

    Object result = program.eval();

    assertThat(result).isEqualTo(res);
  }

  @Test
  public void replace_nested_success() throws Exception {
    String expr =
        "regex.replace("
            + "    regex.replace('%(foo) %(bar) %2','%\\\\((\\\\w+)\\\\)','${\\\\1}'),"
            + "    '%(\\\\d+)', '$\\\\1')";
    CelRuntime.Program program = RUNTIME.createProgram(COMPILER.compile(expr).getAst());

    Object result = program.eval();

    assertThat(result).isEqualTo("${foo} ${bar} $2");
  }

  @Test
  @TestParameters("{t: 'banana', re: 'a', rep: 'x', i: 0, res: 'banana'}")
  @TestParameters("{t: 'banana', re: 'a', rep: 'x', i: 1, res: 'bxnana'}")
  @TestParameters("{t: 'banana', re: 'a', rep: 'x', i: 2, res: 'bxnxna'}")
  @TestParameters("{t: 'banana', re: 'a', rep: 'x', i: 100, res: 'bxnxnx'}")
  @TestParameters("{t: 'banana', re: 'a', rep: 'x', i: -1, res: 'bxnxnx'}")
  @TestParameters("{t: 'banana', re: 'a', rep: 'x', i: -100, res: 'bxnxnx'}")
  @TestParameters(
      "{t: 'cat-dog dog-cat cat-dog dog-cat', re: '(cat)-(dog)', rep: '\\\\2-\\\\1', i: 1,"
          + " res: 'dog-cat dog-cat cat-dog dog-cat'}")
  @TestParameters(
      "{t: 'cat-dog dog-cat cat-dog dog-cat', re: '(cat)-(dog)', rep: '\\\\2-\\\\1', i: 2, res:"
          + " 'dog-cat dog-cat dog-cat dog-cat'}")
  @TestParameters("{t: 'a.b.c', re: '\\\\.', rep: '-', i: 1, res: 'a-b.c'}")
  @TestParameters("{t: 'a.b.c', re: '\\\\.', rep: '-', i: -1, res: 'a-b-c'}")
  public void replaceCount_success(String t, String re, String rep, long i, String res)
      throws Exception {
    String expr = String.format("regex.replace('%s', '%s', '%s', %d)", t, re, rep, i);
    CelRuntime.Program program = RUNTIME.createProgram(COMPILER.compile(expr).getAst());

    Object result = program.eval();

    assertThat(result).isEqualTo(res);
  }

  @Test
  @TestParameters("{target: 'foo bar', regex: '(', replaceStr: '$2 $1'}")
  @TestParameters("{target: 'foo bar', regex: '[a-z', replaceStr: '$2 $1'}")
  public void replace_invalidRegex_throwsException(String target, String regex, String replaceStr)
      throws Exception {
    String expr = String.format("regex.replace('%s', '%s', '%s')", target, regex, replaceStr);
    CelAbstractSyntaxTree ast = COMPILER.compile(expr).getAst();

    CelEvaluationException e =
        assertThrows(CelEvaluationException.class, () -> RUNTIME.createProgram(ast).eval());

    assertThat(e).hasCauseThat().isInstanceOf(IllegalArgumentException.class);
    assertThat(e).hasCauseThat().hasMessageThat().contains("Failed to compile regex: ");
  }

  @Test
  public void replace_invalidCaptureGroupReplaceStr_throwsException() throws Exception {
    String expr = "regex.replace('test', '(.)', '\\\\2')";
    CelAbstractSyntaxTree ast = COMPILER.compile(expr).getAst();

    CelEvaluationException e =
        assertThrows(CelEvaluationException.class, () -> RUNTIME.createProgram(ast).eval());

    assertThat(e).hasCauseThat().isInstanceOf(IllegalArgumentException.class);
    assertThat(e)
        .hasCauseThat()
        .hasMessageThat()
        .contains("Replacement string references group 2 but regex has only 1 group(s)");
  }

  @Test
  public void replace_trailingBackslashReplaceStr_throwsException() throws Exception {
    String expr = "regex.replace('id=123', 'id=(?P<value>\\\\d+)', '\\\\')";
    CelAbstractSyntaxTree ast = COMPILER.compile(expr).getAst();

    CelEvaluationException e =
        assertThrows(CelEvaluationException.class, () -> RUNTIME.createProgram(ast).eval());

    assertThat(e).hasCauseThat().isInstanceOf(IllegalArgumentException.class);
    assertThat(e)
        .hasCauseThat()
        .hasMessageThat()
        .contains("Invalid replacement string: \\ not allowed at end");
  }

  @Test
  public void replace_invalidGroupReferenceReplaceStr_throwsException() throws Exception {
    String expr = "regex.replace('id=123', 'id=(?P<value>\\\\d+)', '\\\\a')";
    CelAbstractSyntaxTree ast = COMPILER.compile(expr).getAst();

    CelEvaluationException e =
        assertThrows(CelEvaluationException.class, () -> RUNTIME.createProgram(ast).eval());

    assertThat(e).hasCauseThat().isInstanceOf(IllegalArgumentException.class);
    assertThat(e)
        .hasCauseThat()
        .hasMessageThat()
        .contains("Invalid replacement string: \\ must be followed by a digit");
  }

  @Test
  @TestParameters("{target: 'hello world', regex: 'hello(.*)', expectedResult: ' world'}")
  @TestParameters("{target: 'item-A, item-B', regex: 'item-(\\\\w+)', expectedResult: 'A'}")
  @TestParameters("{target: 'bananana', regex: 'ana', expectedResult: 'ana'}")
  @TestParameters(
      "{target: 'The color is red', regex: 'The color is (\\\\w+)', expectedResult: 'red'}")
  @TestParameters(
      "{target: 'The color is red', regex: 'The color is \\\\w+', expectedResult: 'The color is"
          + " red'}")
  @TestParameters(
      "{target: 'phone: 415-5551212', regex: 'phone: (\\\\d{3})?', expectedResult: '415'}")
  @TestParameters("{target: 'brand', regex: 'brand', expectedResult: 'brand'}")
  public void extract_success(String target, String regex, String expectedResult) throws Exception {
    String expr = String.format("regex.extract('%s', '%s')", target, regex);
    CelRuntime.Program program = RUNTIME.createProgram(COMPILER.compile(expr).getAst());

    Object result = program.eval();

    assertThat(result).isInstanceOf(Optional.class);
    assertThat((Optional<?>) result).hasValue(expectedResult);
  }

  @Test
  @TestParameters("{target: 'hello world', regex: 'goodbye (.*)'}")
  @TestParameters("{target: 'HELLO', regex: 'hello'}")
  @TestParameters("{target: '', regex: '\\\\w+'}")
  public void extract_no_match(String target, String regex) throws Exception {
    String expr = String.format("regex.extract('%s', '%s')", target, regex);
    CelRuntime.Program program = RUNTIME.createProgram(COMPILER.compile(expr).getAst());

    Object result = program.eval();

    assertThat(result).isInstanceOf(Optional.class);
    assertThat((Optional<?>) result).isEmpty();
  }

  @Test
  @TestParameters("{target: 'phone: 415-5551212', regex: 'phone: ((\\\\d{3})-)?'}")
  @TestParameters("{target: 'testuser@testdomain', regex: '(.*)@([^.]*)'}")
  public void extract_multipleCaptureGroups_throwsException(String target, String regex)
      throws Exception {
    String expr = String.format("regex.extract('%s', '%s')", target, regex);
    CelAbstractSyntaxTree ast = COMPILER.compile(expr).getAst();

    CelEvaluationException e =
        assertThrows(CelEvaluationException.class, () -> RUNTIME.createProgram(ast).eval());

    assertThat(e).hasCauseThat().isInstanceOf(IllegalArgumentException.class);
    assertThat(e)
        .hasCauseThat()
        .hasMessageThat()
        .contains("Regular expression has more than one capturing group:");
  }

  @SuppressWarnings("ImmutableEnumChecker") // Test only
  private enum ExtractAllTestCase {
    NO_MATCH("regex.extractAll('id:123, id:456', 'assa')", ImmutableList.of()),
    NO_CAPTURE_GROUP(
        "regex.extractAll('id:123, id:456', 'id:\\\\d+')", ImmutableList.of("id:123", "id:456")),
    CAPTURE_GROUP(
        "regex.extractAll('key=\"\", key=\"val\"', 'key=\"([^\"]*)\"')",
        ImmutableList.of("", "val")),
    SINGLE_NAMED_GROUP(
        "regex.extractAll('testuser@testdomain', '(?P<username>.*)@')",
        ImmutableList.of("testuser")),
    SINGLE_NAMED_MULTIPLE_MATCH_GROUP(
        "regex.extractAll('banananana', '(ana)')", ImmutableList.of("ana", "ana"));
    private final String expr;
    private final ImmutableList<String> expectedResult;

    ExtractAllTestCase(String expr, ImmutableList<String> expectedResult) {
      this.expr = expr;
      this.expectedResult = expectedResult;
    }
  }

  @Test
  public void extractAll_success(@TestParameter ExtractAllTestCase testCase) throws Exception {
    CelAbstractSyntaxTree ast = COMPILER.compile(testCase.expr).getAst();

    Object result = RUNTIME.createProgram(ast).eval();

    assertThat(result).isEqualTo(testCase.expectedResult);
  }

  @Test
  @TestParameters("{target: 'phone: 415-5551212', regex: 'phone: ((\\\\d{3})-)?'}")
  @TestParameters("{target: 'testuser@testdomain', regex: '(.*)@([^.]*)'}")
  @TestParameters(
      "{target: 'Name: John Doe, Age:321', regex: 'Name: (?P<Name>.*), Age:(?P<Age>\\\\d+)'}")
  @TestParameters(
      "{target: 'The user testuser belongs to testdomain', regex: 'The (user|domain)"
          + " (?P<Username>.*) belongs (to) (?P<Domain>.*)'}")
  public void extractAll_multipleCaptureGroups_throwsException(String target, String regex)
      throws Exception {
    String expr = String.format("regex.extractAll('%s', '%s')", target, regex);
    CelAbstractSyntaxTree ast = COMPILER.compile(expr).getAst();

    CelEvaluationException e =
        assertThrows(CelEvaluationException.class, () -> RUNTIME.createProgram(ast).eval());

    assertThat(e).hasCauseThat().isInstanceOf(IllegalArgumentException.class);
    assertThat(e)
        .hasCauseThat()
        .hasMessageThat()
        .contains("Regular expression has more than one capturing group:");
  }
}
