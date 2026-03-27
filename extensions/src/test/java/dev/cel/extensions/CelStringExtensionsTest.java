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

package dev.cel.extensions;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.testing.junit.testparameterinjector.TestParameter;
import com.google.testing.junit.testparameterinjector.TestParameterInjector;
import com.google.testing.junit.testparameterinjector.TestParameters;
import dev.cel.common.CelAbstractSyntaxTree;
import dev.cel.common.CelFunctionDecl;
import dev.cel.common.CelOptions;
import dev.cel.common.CelValidationException;
import dev.cel.common.types.SimpleType;
import dev.cel.compiler.CelCompiler;
import dev.cel.compiler.CelCompilerFactory;
import dev.cel.extensions.CelStringExtensions.Function;
import dev.cel.runtime.CelEvaluationException;
import dev.cel.runtime.CelRuntime;
import dev.cel.runtime.CelRuntimeFactory;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(TestParameterInjector.class)
public final class CelStringExtensionsTest {

  private static final CelCompiler COMPILER =
      CelCompilerFactory.standardCelCompilerBuilder()
          .addLibraries(CelExtensions.strings())
          .addVar("s", SimpleType.STRING)
          .addVar("separator", SimpleType.STRING)
          .addVar("index", SimpleType.INT)
          .addVar("offset", SimpleType.INT)
          .addVar("indexOfParam", SimpleType.STRING)
          .addVar("beginIndex", SimpleType.INT)
          .addVar("endIndex", SimpleType.INT)
          .addVar("limit", SimpleType.INT)
          .build();

  private static final CelRuntime RUNTIME =
      CelRuntimeFactory.standardCelRuntimeBuilder().addLibraries(CelExtensions.strings()).build();

  @Test
  public void library() {
    CelExtensionLibrary<?> library =
        CelExtensions.getExtensionLibrary("strings", CelOptions.DEFAULT);
    assertThat(library.name()).isEqualTo("strings");
    assertThat(library.latest().version()).isEqualTo(0);
    assertThat(library.version(0).functions().stream().map(CelFunctionDecl::name))
        .containsExactly(
            "charAt",
            "indexOf",
            "join",
            "lastIndexOf",
            "lowerAscii",
            "replace",
            "reverse",
            "split",
            "strings.quote",
            "substring",
            "trim",
            "upperAscii");
    assertThat(library.version(0).macros()).isEmpty();
  }

  @Test
  @TestParameters("{string: 'abcd', beginIndex: 0, expectedResult: 'abcd'}")
  @TestParameters("{string: 'abcd', beginIndex: 1, expectedResult: 'bcd'}")
  @TestParameters("{string: 'abcd', beginIndex: 2, expectedResult: 'cd'}")
  @TestParameters("{string: 'abcd', beginIndex: 3, expectedResult: 'd'}")
  @TestParameters("{string: 'abcd', beginIndex: 4, expectedResult: ''}")
  @TestParameters("{string: '', beginIndex: 0, expectedResult: ''}")
  @TestParameters("{string: '😁😑😦', beginIndex: 0, expectedResult: '😁😑😦'}")
  @TestParameters("{string: '😁😑😦', beginIndex: 1, expectedResult: '😑😦'}")
  @TestParameters("{string: '😁😑😦', beginIndex: 2, expectedResult: '😦'}")
  @TestParameters("{string: '😁😑😦', beginIndex: 3, expectedResult: ''}")
  public void substring_beginIndex_success(String string, int beginIndex, String expectedResult)
      throws Exception {
    CelAbstractSyntaxTree ast = COMPILER.compile("s.substring(beginIndex)").getAst();
    CelRuntime.Program program = RUNTIME.createProgram(ast);

    Object evaluatedResult = program.eval(ImmutableMap.of("s", string, "beginIndex", beginIndex));

    assertThat(evaluatedResult).isEqualTo(expectedResult);
  }

  @Test
  @TestParameters("{string: '', expectedResult: ''}")
  @TestParameters("{string: 'hello world', expectedResult: 'hello world'}")
  @TestParameters("{string: 'HELLO WORLD', expectedResult: 'hello world'}")
  @TestParameters("{string: 'HeLlO wOrLd', expectedResult: 'hello world'}")
  @TestParameters(
      "{string: 'A!@#$%^&*()-_+=?/<>.,;:''\"\\', expectedResult: 'a!@#$%^&*()-_+=?/<>.,;:''\"\\'}")
  public void lowerAscii_success(String string, String expectedResult) throws Exception {
    CelAbstractSyntaxTree ast = COMPILER.compile("s.lowerAscii()").getAst();
    CelRuntime.Program program = RUNTIME.createProgram(ast);

    Object evaluatedResult = program.eval(ImmutableMap.of("s", string));

    assertThat(evaluatedResult).isEqualTo(expectedResult);
  }

  @Test
  // Some of these characters from Latin Extended block have a lowercase mapping.
  // In CEL's String extension, we do not transform these because they are outside Latin-1
  @TestParameters("{string: 'ÀßàḀḁḂḃ', expectedResult: 'ÀßàḀḁḂḃ'}")
  @TestParameters("{string: '가나다라 마바사', expectedResult: '가나다라 마바사'}")
  @TestParameters("{string: 'A가B나C다D라E 마F바G사H', expectedResult: 'a가b나c다d라e 마f바g사h'}")
  @TestParameters("{string: '😁😑😦', expectedResult: '😁😑😦'}")
  @TestParameters("{string: '😁😑 😦', expectedResult: '😁😑 😦'}")
  @TestParameters("{string: 'A😁B 😑C가😦D', expectedResult: 'a😁b 😑c가😦d'}")
  public void lowerAscii_outsideAscii_success(String string, String expectedResult)
      throws Exception {
    CelAbstractSyntaxTree ast = COMPILER.compile("s.lowerAscii()").getAst();
    CelRuntime.Program program = RUNTIME.createProgram(ast);

    Object evaluatedResult = program.eval(ImmutableMap.of("s", string));

    assertThat(evaluatedResult).isEqualTo(expectedResult);
  }

  @Test
  @TestParameters("{string: '', separator: '', expectedResult: []}")
  @TestParameters("{string: '', separator: ' ', expectedResult: ['']}")
  @TestParameters("{string: '', separator: '  ', expectedResult: ['']}")
  @TestParameters("{string: ' ', separator: '', expectedResult: [' ']}")
  @TestParameters("{string: ' ', separator: ' ', expectedResult: ['','']}")
  @TestParameters("{string: ' ', separator: '  ', expectedResult: [' ']}")
  @TestParameters("{string: 'test', separator: '', expectedResult: ['t', 'e', 's', 't']}")
  @TestParameters("{string: 'test', separator: ' ', expectedResult: ['test']}")
  @TestParameters("{string: 'te st', separator: '', expectedResult: ['t', 'e', ' ', 's', 't']}")
  @TestParameters("{string: 'hello world', separator: ' ', expectedResult: ['hello', 'world']}")
  @TestParameters("{string: 'hello world', separator: 'hello world', expectedResult: ['', '']}")
  @TestParameters(
      "{string: 'hello hello hello+hello', separator: ' ', expectedResult: ['hello', 'hello',"
          + " 'hello+hello']}")
  @TestParameters(
      "{string: 'hello hello hello+hello', separator: 'hello', expectedResult: ['', ' ', ' ',"
          + " '+','']}")
  @TestParameters(
      "{string: 'h-e_l-lo w+o-rld', separator: '-', expectedResult: ['h', 'e_l', 'lo w+o',"
          + " 'rld']}")
  @TestParameters(
      "{string: 'The quick brown fox jumps over the lazy dog', separator: 'fox', expectedResult:"
          + " ['The quick brown ', ' jumps over the lazy dog']}")
  public void split_ascii_success(String string, String separator, List<String> expectedResult)
      throws Exception {
    CelAbstractSyntaxTree ast = COMPILER.compile("s.split(separator)").getAst();
    CelRuntime.Program program = RUNTIME.createProgram(ast);

    Object evaluatedResult = program.eval(ImmutableMap.of("s", string, "separator", separator));

    assertThat(evaluatedResult).isEqualTo(expectedResult);
  }

  @Test
  @TestParameters("{string: '가나a 다라', separator: '', expectedResult: ['가','나','a',' ', '다','라']}")
  @TestParameters("{string: '가나a 다라', separator: ' ', expectedResult: ['가나a', '다라']}")
  @TestParameters("{string: 'β∧±⊗∉η□⇐‰∕', separator: '∉η', expectedResult: ['β∧±⊗', '□⇐‰∕']}")
  @TestParameters("{string: '😁😦😑😦', separator: '', expectedResult: ['😁','😦','😑','😦']}")
  @TestParameters("{string: '😁😦😑😦', separator: ' ', expectedResult: ['😁😦😑😦']}")
  @TestParameters("{string: '😁a😦나😑 😦', separator: ' ', expectedResult: ['😁a😦나😑', '😦']}")
  @TestParameters("{string: '😁a😦나😑 😦', separator: ' ', expectedResult: ['😁a😦나😑', '😦']}")
  @TestParameters("{string: '😁a😦나😑 😦', separator: '나😑 ', expectedResult: ['😁a😦', '😦']}")
  @TestParameters("{string: '😁a😦나😑 😦', separator: ' ', expectedResult: ['😁a😦나😑' ,'😦']}")
  @TestParameters("{string: '😁a😦나😑 😦', separator: '😁a😦나😑 😦', expectedResult: ['','']}")
  public void split_unicode_success(String string, String separator, List<String> expectedResult)
      throws Exception {
    CelAbstractSyntaxTree ast = COMPILER.compile("s.split(separator)").getAst();
    CelRuntime.Program program = RUNTIME.createProgram(ast);

    Object evaluatedResult = program.eval(ImmutableMap.of("s", string, "separator", separator));

    assertThat(evaluatedResult).isEqualTo(expectedResult);
  }

  @Test
  @SuppressWarnings("unchecked") // Test only, need List<String> cast to test mutability
  public void split_collectionIsMutable() throws Exception {
    CelAbstractSyntaxTree ast = COMPILER.compile("'test'.split('')").getAst();
    CelRuntime.Program program = RUNTIME.createProgram(ast);

    List<String> evaluatedResult = (List<String>) program.eval();
    evaluatedResult.add("a");
    evaluatedResult.add("b");
    evaluatedResult.add("c");
    evaluatedResult.remove("c");

    assertThat(evaluatedResult).containsExactly("t", "e", "s", "t", "a", "b").inOrder();
  }

  @Test
  public void split_separatorIsNonString_throwsException() {
    CelValidationException exception =
        assertThrows(
            CelValidationException.class, () -> COMPILER.compile("'12'.split(2)").getAst());

    assertThat(exception).hasMessageThat().contains("found no matching overload for 'split'");
  }

  @Test
  @TestParameters("{string: '', separator: '', limit: -1, expectedResult: []}")
  @TestParameters("{string: '', separator: '', limit: 0, expectedResult: []}")
  @TestParameters("{string: '', separator: ' ', limit: 0, expectedResult: []}")
  @TestParameters("{string: ' ', separator: ' ', limit: 0, expectedResult: []}")
  @TestParameters("{string: 'test', separator: '', limit: 0, expectedResult: []}")
  @TestParameters("{string: 'test', separator: ' ', limit: 0, expectedResult: []}")
  @TestParameters("{string: 'test', separator: 'test', limit: 0, expectedResult: []}")
  @TestParameters("{string: 'hello world', separator: '', limit: 0, expectedResult: []}")
  @TestParameters("{string: '', separator: '', limit: 1, expectedResult: ['']}")
  @TestParameters("{string: '', separator: ' ', limit: 1, expectedResult: ['']}")
  @TestParameters("{string: '', separator: '  ', limit: 1, expectedResult: ['']}")
  @TestParameters("{string: '', separator: '  ', limit: 2, expectedResult: ['']}")
  @TestParameters("{string: ' ', separator: '', limit: 1, expectedResult: [' ']}")
  @TestParameters("{string: ' ', separator: ' ', limit: 1, expectedResult: [' ']}")
  @TestParameters("{string: ' ', separator: ' ', limit: 2, expectedResult: ['','']}")
  @TestParameters("{string: 'test', separator: '', limit: 1, expectedResult: ['test']}")
  @TestParameters("{string: 'test', separator: '', limit: 2, expectedResult: ['t', 'est']}")
  @TestParameters("{string: 'test', separator: '', limit: 3, expectedResult: ['t', 'e', 'st']}")
  @TestParameters("{string: 'test', separator: '', limit: 4, expectedResult: ['t', 'e', 's', 't']}")
  @TestParameters(
      "{string: 'test', separator: '', limit: -1, expectedResult: ['t', 'e', 's', 't']}")
  @TestParameters("{string: 'test', separator: ' ', limit: 1, expectedResult: ['test']}")
  @TestParameters("{string: 'test', separator: ' ', limit: 2, expectedResult: ['test']}")
  @TestParameters("{string: 'te st', separator: '', limit: 1, expectedResult: ['te st']}")
  @TestParameters(
      "{string: 'te st', separator: '', limit: -1, expectedResult: ['t', 'e', ' ', 's', 't']}")
  @TestParameters(
      "{string: 'te st', separator: '', limit: 5, expectedResult: ['t', 'e', ' ', 's', 't']}")
  @TestParameters(
      "{string: 'hello world', separator: ' ', limit: 1, expectedResult: ['hello world']}")
  @TestParameters(
      "{string: 'hello world', separator: ' ', limit: 2, expectedResult: ['hello', 'world']}")
  @TestParameters(
      "{string: 'hello world', separator: ' ', limit: 3, expectedResult: ['hello', 'world']}")
  @TestParameters(
      "{string: 'hello world', separator: ' ', limit: -1, expectedResult: ['hello', 'world']}")
  @TestParameters(
      "{string: 'hello world', separator: 'hello world', limit: 1, expectedResult: ['hello"
          + " world']}")
  @TestParameters(
      "{string: 'hello world', separator: 'hello world', limit: 2, expectedResult: ['', '']}")
  @TestParameters(
      "{string: 'hello world', separator: 'hello world', limit: 3, expectedResult: ['', '']}")
  @TestParameters(
      "{string: 'hello world', separator: 'hello world', limit: -1, expectedResult: ['', '']}")
  @TestParameters(
      "{string: 'hello hello hello+hello', separator: '+', limit: 2, expectedResult: ['hello hello"
          + " hello','hello']}")
  @TestParameters(
      "{string: 'hello hello hello+hello', separator: 'hello', limit: 3, expectedResult: ['', ' ',"
          + "' hello+hello']}")
  @TestParameters(
      "{string: 'hello hello hello+hello', separator: 'hello', limit: 5, expectedResult: ['', ' ',"
          + " ' ', '+','']}")
  @TestParameters(
      "{string: 'hello hello hello+hello', separator: 'hello', limit: -1, expectedResult: ['', ' ',"
          + " ' ', '+','']}")
  @TestParameters(
      "{string: 'h-e_l-lo w+o-rld', separator: '-', limit: 3, expectedResult: ['h', 'e_l', 'lo"
          + " w+o-rld']}")
  @TestParameters(
      "{string: 'h-e_l-lo w+o-rld', separator: '-', limit: 5, expectedResult: ['h', 'e_l', 'lo"
          + " w+o', 'rld']}")
  @TestParameters(
      "{string: 'h-e_l-lo w+o-rld', separator: '-', limit: -1, expectedResult: ['h', 'e_l', 'lo"
          + " w+o', 'rld']}")
  @TestParameters(
      "{string: 'The quick brown fox jumps over the lazy dog', separator: 'fox', limit: 0,"
          + " expectedResult: []}")
  @TestParameters(
      "{string: 'The quick brown fox jumps over the lazy dog', separator: 'fox', limit: 1,"
          + " expectedResult: ['The quick brown fox jumps over the lazy dog']}")
  @TestParameters(
      "{string: 'The quick brown fox jumps over the lazy dog', separator: 'fox', limit: 2,"
          + " expectedResult: ['The quick brown ', ' jumps over the lazy dog']}")
  @TestParameters(
      "{string: 'The quick brown fox jumps over the lazy dog', separator: 'fox', limit: -1,"
          + " expectedResult: ['The quick brown ', ' jumps over the lazy dog']}")
  public void split_asciiWithLimit_success(
      String string, String separator, int limit, List<String> expectedResult) throws Exception {
    CelAbstractSyntaxTree ast = COMPILER.compile("s.split(separator, limit)").getAst();
    CelRuntime.Program program = RUNTIME.createProgram(ast);

    Object evaluatedResult =
        program.eval(ImmutableMap.of("s", string, "separator", separator, "limit", limit));

    assertThat(evaluatedResult).isEqualTo(expectedResult);
  }

  @Test
  @TestParameters("{string: '가나a 다라', separator: ' ', limit: 0, expectedResult: []}")
  @TestParameters("{string: 'β∧±⊗∉η□⇐‰∕', separator: '∉η', limit: 0, expectedResult: []}")
  @TestParameters("{string: '😁😦 😑😦', separator: 'b', limit: 0, expectedResult: []}")
  @TestParameters("{string: '가나a 다라', separator: '', limit: 1, expectedResult: ['가나a 다라']}")
  @TestParameters("{string: '가나a 다라', separator: ' ', limit: 2, expectedResult: ['가나a', '다라']}")
  @TestParameters("{string: '가나a 다라', separator: ' ', limit: -1, expectedResult: ['가나a', '다라']}")
  @TestParameters(
      "{string: 'β∧±⊗∉η□⇐‰∕', separator: '∉η', limit: 1, expectedResult: ['β∧±⊗∉η□⇐‰∕']}")
  @TestParameters(
      "{string: 'β∧±⊗∉η□⇐‰∕', separator: '∉η', limit: 2, expectedResult: ['β∧±⊗', '□⇐‰∕']}")
  @TestParameters(
      "{string: 'β∧±⊗∉η□⇐‰∕', separator: '∉η', limit: 3, expectedResult: ['β∧±⊗', '□⇐‰∕']}")
  @TestParameters(
      "{string: 'β∧±⊗∉η□⇐‰∕', separator: '∉η', limit: -1, expectedResult: ['β∧±⊗', '□⇐‰∕']}")
  @TestParameters("{string: '😁😦 😑😦', separator: '', limit: 1, expectedResult: ['😁😦 😑😦']}")
  @TestParameters(
      "{string: '😁😦 😑😦', separator: '', limit: 2, expectedResult: ['😁','😦 😑😦']}")
  @TestParameters(
      "{string: '😁😦 😑😦', separator: '', limit: 3, expectedResult: ['😁','😦',' 😑😦']}")
  @TestParameters(
      "{string: '😁😦 😑😦', separator: '', limit: 4, expectedResult: ['😁','😦',' ','😑😦']}")
  @TestParameters(
      "{string: '😁😦 😑😦', separator: '', limit: 5, expectedResult: ['😁','😦',' ','😑','😦']}")
  @TestParameters(
      "{string: '😁😦 😑😦', separator: '', limit: 6, expectedResult: ['😁','😦',' ','😑','😦']}")
  @TestParameters(
      "{string: '😁😦 😑😦', separator: '', limit: -1, expectedResult: ['😁','😦',' ','😑','😦']}")
  @TestParameters(
      "{string: '😁a😦나😑 😦', separator: '나😑 ', limit: 1, expectedResult: ['😁a😦나😑 😦']}")
  @TestParameters(
      "{string: '😁a😦나😑 😦', separator: '나😑 ', limit: 2, expectedResult: ['😁a😦', '😦']}")
  @TestParameters(
      "{string: '😁a😦나😑 😦', separator: '나😑 ', limit: 3, expectedResult: ['😁a😦', '😦']}")
  @TestParameters(
      "{string: '😁a😦나😑 😦', separator: '나😑 ', limit: -1, expectedResult: ['😁a😦', '😦']}")
  @TestParameters(
      "{string: '😁a😦나😑 😦', separator: '😁a😦나😑 😦', limit: 1, expectedResult: ['😁a😦나😑"
          + " 😦']}")
  @TestParameters(
      "{string: '😁a😦나😑 😦', separator: '😁a😦나😑 😦', limit: 2, expectedResult: ['','']}")
  @TestParameters(
      "{string: '😁a😦나😑 😦', separator: '😁a😦나😑 😦', limit: 3, expectedResult: ['','']}")
  @TestParameters(
      "{string: '😁a😦나😑 😦', separator: '😁a😦나😑 😦', limit: -1, expectedResult: ['','']}")
  public void split_unicodeWithLimit_success(
      String string, String separator, int limit, List<String> expectedResult) throws Exception {
    CelAbstractSyntaxTree ast = COMPILER.compile("s.split(separator, limit)").getAst();
    CelRuntime.Program program = RUNTIME.createProgram(ast);

    Object evaluatedResult =
        program.eval(ImmutableMap.of("s", string, "separator", separator, "limit", limit));

    assertThat(evaluatedResult).isEqualTo(expectedResult);
  }

  @Test
  @TestParameters("{separator: '', limit: 0}")
  @TestParameters("{separator: '', limit: 1}")
  @TestParameters("{separator: '', limit: 2}")
  @TestParameters("{separator: 'te', limit: 0}")
  @TestParameters("{separator: 'te', limit: 1}")
  @TestParameters("{separator: 'te', limit: 2}")
  @SuppressWarnings("unchecked") // Test only, need List<String> cast to test mutability
  public void split_withLimit_collectionIsMutable(String separator, int limit) throws Exception {
    CelAbstractSyntaxTree ast = COMPILER.compile("'test'.split(separator, limit)").getAst();
    CelRuntime.Program program = RUNTIME.createProgram(ast);

    List<String> evaluatedResult =
        (List<String>) program.eval(ImmutableMap.of("separator", separator, "limit", limit));
    evaluatedResult.add("a");

    assertThat(Iterables.getLast(evaluatedResult)).isEqualTo("a");
  }

  @Test
  public void split_withLimit_separatorIsNonString_throwsException() {
    CelValidationException exception =
        assertThrows(
            CelValidationException.class, () -> COMPILER.compile("'12'.split(2, 3)").getAst());

    assertThat(exception).hasMessageThat().contains("found no matching overload for 'split'");
  }

  @Test
  public void split_withLimitOverflow_throwsException() throws Exception {
    CelAbstractSyntaxTree ast = COMPILER.compile("'test'.split('', limit)").getAst();
    CelRuntime.Program program = RUNTIME.createProgram(ast);

    CelEvaluationException exception =
        assertThrows(
            CelEvaluationException.class,
            () -> program.eval(ImmutableMap.of("limit", 2147483648L))); // INT_MAX + 1

    assertThat(exception)
        .hasMessageThat()
        .contains("split failure: Limit must not exceed the int32 range: 2147483648");
  }

  @Test
  @TestParameters("{string: 'abcd', beginIndex: 0, endIndex: 0, expectedResult: ''}")
  @TestParameters("{string: 'abcd', beginIndex: 0, endIndex: 1, expectedResult: 'a'}")
  @TestParameters("{string: 'abcd', beginIndex: 0, endIndex: 2, expectedResult: 'ab'}")
  @TestParameters("{string: 'abcd', beginIndex: 0, endIndex: 3, expectedResult: 'abc'}")
  @TestParameters("{string: 'abcd', beginIndex: 0, endIndex: 4, expectedResult: 'abcd'}")
  @TestParameters("{string: 'abcd', beginIndex: 1, endIndex: 4, expectedResult: 'bcd'}")
  @TestParameters("{string: 'abcd', beginIndex: 1, endIndex: 3, expectedResult: 'bc'}")
  @TestParameters("{string: 'abcd', beginIndex: 2, endIndex: 3, expectedResult: 'c'}")
  @TestParameters("{string: 'abcd', beginIndex: 3, endIndex: 3, expectedResult: ''}")
  @TestParameters("{string: '', beginIndex: 0, endIndex: 0, expectedResult: ''}")
  public void substring_beginAndEndIndex_ascii_success(
      String string, int beginIndex, int endIndex, String expectedResult) throws Exception {
    CelAbstractSyntaxTree ast = COMPILER.compile("s.substring(beginIndex, endIndex)").getAst();
    CelRuntime.Program program = RUNTIME.createProgram(ast);

    Object evaluatedResult =
        program.eval(ImmutableMap.of("s", string, "beginIndex", beginIndex, "endIndex", endIndex));

    assertThat(evaluatedResult).isEqualTo(expectedResult);
  }

  @Test
  // SMP
  @TestParameters("{string: '😁😑😦', beginIndex: 0, endIndex: 0, expectedResult: ''}")
  @TestParameters("{string: '😁😑😦', beginIndex: 0, endIndex: 1, expectedResult: '😁'}")
  @TestParameters("{string: '😁😑😦', beginIndex: 0, endIndex: 2, expectedResult: '😁😑'}")
  @TestParameters("{string: '😁😑😦', beginIndex: 0, endIndex: 3, expectedResult: '😁😑😦'}")
  @TestParameters("{string: '😁😑😦', beginIndex: 1, endIndex: 3, expectedResult: '😑😦'}")
  @TestParameters("{string: '😁😑😦', beginIndex: 2, endIndex: 3, expectedResult: '😦'}")
  @TestParameters("{string: '😁😑😦', beginIndex: 3, endIndex: 3, expectedResult: ''}")
  // BMP/SMP Mixed
  @TestParameters("{string: 'a😁나', beginIndex: 0, endIndex: 0, expectedResult: ''}")
  @TestParameters("{string: 'a😁나', beginIndex: 0, endIndex: 1, expectedResult: 'a'}")
  @TestParameters("{string: 'a😁나', beginIndex: 0, endIndex: 2, expectedResult: 'a😁'}")
  @TestParameters("{string: 'a😁나', beginIndex: 0, endIndex: 3, expectedResult: 'a😁나'}")
  @TestParameters("{string: 'a😁나', beginIndex: 1, endIndex: 3, expectedResult: '😁나'}")
  @TestParameters("{string: 'a😁나', beginIndex: 2, endIndex: 3, expectedResult: '나'}")
  @TestParameters("{string: 'a😁나', beginIndex: 3, endIndex: 3, expectedResult: ''}")
  public void substring_beginAndEndIndex_unicode_success(
      String string, int beginIndex, int endIndex, String expectedResult) throws Exception {
    CelAbstractSyntaxTree ast = COMPILER.compile("s.substring(beginIndex, endIndex)").getAst();
    CelRuntime.Program program = RUNTIME.createProgram(ast);

    Object evaluatedResult =
        program.eval(ImmutableMap.of("s", string, "beginIndex", beginIndex, "endIndex", endIndex));

    assertThat(evaluatedResult).isEqualTo(expectedResult);
  }

  @Test
  @TestParameters("{string: 'abcd', beginIndex: 7}")
  @TestParameters("{string: '', beginIndex: 2}")
  public void substring_beginIndexOutOfRange_ascii_throwsException(String string, int beginIndex)
      throws Exception {
    CelAbstractSyntaxTree ast = COMPILER.compile("s.substring(beginIndex)").getAst();
    CelRuntime.Program program = RUNTIME.createProgram(ast);

    CelEvaluationException exception =
        assertThrows(
            CelEvaluationException.class,
            () -> program.eval(ImmutableMap.of("s", string, "beginIndex", beginIndex)));

    String exceptionMessage =
        String.format(
            "substring failure: Range [%d, %d) out of bounds", beginIndex, string.length());
    assertThat(exception).hasMessageThat().contains(exceptionMessage);
  }

  @Test
  // BMP
  @TestParameters("{string: '가나다', beginIndex: 4, uniqueCharCount: 3}")
  // SMP
  @TestParameters("{string: '😁', beginIndex: 2, uniqueCharCount: 1}")
  @TestParameters("{string: '😁😑😦', beginIndex: 4, uniqueCharCount: 3}")
  // BMP/SMP Mixed
  @TestParameters("{string: '😁가나', beginIndex: 4, uniqueCharCount: 3}")
  public void substring_beginIndexOutOfRange_unicode_throwsException(
      String string, int beginIndex, int uniqueCharCount) throws Exception {
    CelAbstractSyntaxTree ast = COMPILER.compile("s.substring(beginIndex)").getAst();
    CelRuntime.Program program = RUNTIME.createProgram(ast);

    CelEvaluationException exception =
        assertThrows(
            CelEvaluationException.class,
            () -> program.eval(ImmutableMap.of("s", string, "beginIndex", beginIndex)));

    String exceptionMessage =
        String.format(
            "substring failure: Range [%d, %d) out of bounds", beginIndex, uniqueCharCount);
    assertThat(exception).hasMessageThat().contains(exceptionMessage);
  }

  @Test
  @TestParameters("{string: 'abcd', beginIndex: -1, endIndex: 1}")
  @TestParameters("{string: 'abcd', beginIndex: 0, endIndex: 5}")
  @TestParameters("{string: 'abcd', beginIndex: 2, endIndex: 1}")
  @TestParameters("{string: '😁😑😦', beginIndex: -1, endIndex: 3}")
  @TestParameters("{string: '😁😑😦', beginIndex: 0, endIndex: 5}")
  @TestParameters("{string: '😁😑😦', beginIndex: 2, endIndex: 1}")
  public void substring_beginAndEndIndexOutOfRange_throwsException(
      String string, int beginIndex, int endIndex) throws Exception {
    CelAbstractSyntaxTree ast = COMPILER.compile("s.substring(beginIndex, endIndex)").getAst();
    CelRuntime.Program program = RUNTIME.createProgram(ast);

    CelEvaluationException exception =
        assertThrows(
            CelEvaluationException.class,
            () ->
                program.eval(
                    ImmutableMap.of("s", string, "beginIndex", beginIndex, "endIndex", endIndex)));

    String exceptionMessage =
        String.format("substring failure: Range [%d, %d) out of bounds", beginIndex, endIndex);
    assertThat(exception).hasMessageThat().contains(exceptionMessage);
  }

  @Test
  public void substring_beginIndexOverflow_throwsException() throws Exception {
    CelAbstractSyntaxTree ast = COMPILER.compile("'abcd'.substring(beginIndex)").getAst();
    CelRuntime.Program program = RUNTIME.createProgram(ast);

    CelEvaluationException exception =
        assertThrows(
            CelEvaluationException.class,
            () -> program.eval(ImmutableMap.of("beginIndex", 2147483648L))); // INT_MAX + 1

    assertThat(exception)
        .hasMessageThat()
        .contains("substring failure: Index must not exceed the int32 range: 2147483648");
  }

  @Test
  @TestParameters("{beginIndex: 0, endIndex: 2147483648}") // INT_MAX + 1
  @TestParameters("{beginIndex: 2147483648, endIndex: 2147483648}")
  public void substring_beginOrEndIndexOverflow_throwsException(long beginIndex, long endIndex)
      throws Exception {
    CelAbstractSyntaxTree ast = COMPILER.compile("'abcd'.substring(beginIndex, endIndex)").getAst();
    CelRuntime.Program program = RUNTIME.createProgram(ast);

    CelEvaluationException exception =
        assertThrows(
            CelEvaluationException.class,
            () -> program.eval(ImmutableMap.of("beginIndex", beginIndex, "endIndex", endIndex)));

    assertThat(exception)
        .hasMessageThat()
        .contains("substring failure: Indices must not exceed the int32 range");
  }

  @Test
  @TestParameters("{string: '', index: 0, expectedResult: ''}")
  @TestParameters("{string: 'world', index: 0, expectedResult: 'w'}")
  @TestParameters("{string: 'world', index: 1, expectedResult: 'o'}")
  @TestParameters("{string: 'world', index: 2, expectedResult: 'r'}")
  @TestParameters("{string: 'world', index: 3, expectedResult: 'l'}")
  @TestParameters("{string: 'world', index: 4, expectedResult: 'd'}")
  @TestParameters("{string: 'world', index: 5, expectedResult: ''}")
  public void charAt_ascii_success(String string, long index, String expectedResult)
      throws Exception {
    CelAbstractSyntaxTree ast = COMPILER.compile("s.charAt(index)").getAst();
    CelRuntime.Program program = RUNTIME.createProgram(ast);

    Object evaluatedResult = program.eval(ImmutableMap.of("s", string, "index", index));

    assertThat(evaluatedResult).isEqualTo(expectedResult);
  }

  @Test
  // BMP
  @TestParameters("{string: 'あいう', index: 2, expectedResult: 'う'}")
  @TestParameters("{string: '가나다', index: 3, expectedResult: ''}")
  @TestParameters("{string: '가나다', index: 1, expectedResult: '나'}")
  // SMP
  @TestParameters("{string: '😁😑😦', index: 0, expectedResult: '😁'}")
  @TestParameters("{string: '😁😑😦', index: 1, expectedResult: '😑'}")
  @TestParameters("{string: '😁😑😦', index: 2, expectedResult: '😦'}")
  @TestParameters("{string: '😁😑😦', index: 3, expectedResult: ''}")
  // BMP/SMP mixed
  @TestParameters("{string: 'a😁나', index: 0, expectedResult: 'a'}")
  @TestParameters("{string: 'a😁나', index: 1, expectedResult: '😁'}")
  @TestParameters("{string: 'a😁나', index: 2, expectedResult: '나'}")
  @TestParameters("{string: 'a😁나', index: 3, expectedResult: ''}")
  public void charAt_unicode_success(String string, long index, String expectedResult)
      throws Exception {
    CelAbstractSyntaxTree ast = COMPILER.compile("s.charAt(index)").getAst();
    CelRuntime.Program program = RUNTIME.createProgram(ast);

    Object evaluatedResult = program.eval(ImmutableMap.of("s", string, "index", index));

    assertThat(evaluatedResult).isEqualTo(expectedResult);
  }

  @Test
  @TestParameters("{string: 'world', index: -1}")
  @TestParameters("{string: 'world', index: 6}")
  @TestParameters("{string: '😁😑😦', index: -1}")
  @TestParameters("{string: '😁😑😦', index: 4}")
  public void charAt_outOfBounds_throwsException(String string, long index) throws Exception {
    CelAbstractSyntaxTree ast = COMPILER.compile("s.charAt(index)").getAst();
    CelRuntime.Program program = RUNTIME.createProgram(ast);

    CelEvaluationException exception =
        assertThrows(
            CelEvaluationException.class,
            () -> program.eval(ImmutableMap.of("s", string, "index", index)));

    assertThat(exception).hasMessageThat().contains("charAt failure: Index out of range");
  }

  @Test
  public void charAt_indexOverflow_throwsException() throws Exception {
    CelAbstractSyntaxTree ast = COMPILER.compile("'test'.charAt(index)").getAst();
    CelRuntime.Program program = RUNTIME.createProgram(ast);

    CelEvaluationException exception =
        assertThrows(
            CelEvaluationException.class,
            () -> program.eval(ImmutableMap.of("index", 2147483648L))); // INT_MAX + 1

    assertThat(exception)
        .hasMessageThat()
        .contains("charAt failure: Index must not exceed the int32 range: 2147483648");
  }

  @Test
  @TestParameters("{string: '', indexOf: '', expectedResult: 0}")
  @TestParameters("{string: 'hello mellow', indexOf: '', expectedResult: 0}")
  @TestParameters("{string: 'hello mellow', indexOf: 'hello', expectedResult: 0}")
  @TestParameters("{string: 'hello mellow', indexOf: 'hello mellow', expectedResult: 0}")
  @TestParameters("{string: 'hello mellow', indexOf: 'ello', expectedResult: 1}")
  @TestParameters("{string: 'hello mellow', indexOf: 'l', expectedResult: 2}")
  @TestParameters("{string: 'hello mellow', indexOf: 'll', expectedResult: 2}")
  @TestParameters("{string: 'hello mellow', indexOf: 'lo', expectedResult: 3}")
  @TestParameters("{string: 'hello mellow', indexOf: 'o ', expectedResult: 4}")
  @TestParameters("{string: 'hello mellow', indexOf: ' ', expectedResult: 5}")
  @TestParameters("{string: 'hello mellow', indexOf: 'mellow', expectedResult: 6}")
  @TestParameters("{string: 'hello mellow', indexOf: 'ellow', expectedResult: 7}")
  @TestParameters("{string: 'hello mellow', indexOf: 'llow', expectedResult: 8}")
  @TestParameters("{string: 'hello mellow', indexOf: 'low', expectedResult: 9}")
  @TestParameters("{string: 'hello mellow', indexOf: 'ow', expectedResult: 10}")
  @TestParameters("{string: 'hello mellow', indexOf: 'w', expectedResult: 11}")
  @TestParameters("{string: 'hello mellow', indexOf: 'hellomellow', expectedResult: -1}")
  @TestParameters("{string: 'hello mellow', indexOf: 'jello', expectedResult: -1}")
  @TestParameters("{string: 'hello mellow', indexOf: '  ', expectedResult: -1}")
  public void indexOf_ascii_success(String string, String indexOf, int expectedResult)
      throws Exception {
    CelAbstractSyntaxTree ast = COMPILER.compile("s.indexOf(indexOfParam)").getAst();
    CelRuntime.Program program = RUNTIME.createProgram(ast);

    Object evaluatedResult = program.eval(ImmutableMap.of("s", string, "indexOfParam", indexOf));

    assertThat(evaluatedResult).isEqualTo(expectedResult);
  }

  @Test
  // SMP
  @TestParameters("{string: '😁😑 😦', indexOf: ' ', expectedResult: 2}")
  @TestParameters("{string: '😁😑 😦', indexOf: '  ', expectedResult: -1}")
  @TestParameters("{string: '😁😑 😦', indexOf: '😁', expectedResult: 0}")
  @TestParameters("{string: '😁😑 😦', indexOf: '😑', expectedResult: 1}")
  @TestParameters("{string: '😁😑 😦', indexOf: '😦', expectedResult: 3}")
  @TestParameters("{string: '😁😑 😦', indexOf: '😦', expectedResult: 3}")
  @TestParameters("{string: '😁😑 😦', indexOf: '😁😑', expectedResult: 0}")
  @TestParameters("{string: '😁😑 😦', indexOf: '😑 😦', expectedResult: 1}")
  @TestParameters("{string: '😁😑 😦', indexOf: ' 😦', expectedResult: 2}")
  @TestParameters("{string: '😁😑 😦', indexOf: ' 😦 ', expectedResult: -1}")
  @TestParameters("{string: '😁😑 😦', indexOf: '😁😑 😦', expectedResult: 0}")
  @TestParameters("{string: '😁😑 😦', indexOf: '😁😑😦', expectedResult: -1}")
  // BMP/SMP Mixed
  @TestParameters("{string: 'a😁😑 나😦😁😑다', indexOf: ' ', expectedResult: 3}")
  @TestParameters("{string: 'a😁😑 나😦😁😑다', indexOf: '😁😑', expectedResult: 1}")
  @TestParameters("{string: 'a😁😑 나😦😁😑다', indexOf: '나😦', expectedResult: 4}")
  @TestParameters("{string: 'a😁😑 나😦😁😑다', indexOf: '나😁', expectedResult: -1}")
  @TestParameters("{string: 'a😁😑 나😦😁😑다', indexOf: ' 나😦😁😑다', expectedResult: 3}")
  @TestParameters("{string: 'a😁😑 나😦😁😑다', indexOf: 'a😁😑 나😦😁😑다', expectedResult: 0}")
  @TestParameters("{string: 'a😁😑 나😦😁😑다', indexOf: 'a😁😑 나😦😁😑다😁', expectedResult: -1}")
  public void indexOf_unicode_success(String string, String indexOf, int expectedResult)
      throws Exception {
    CelAbstractSyntaxTree ast = COMPILER.compile("s.indexOf(indexOfParam)").getAst();
    CelRuntime.Program program = RUNTIME.createProgram(ast);

    Object evaluatedResult = program.eval(ImmutableMap.of("s", string, "indexOfParam", indexOf));

    assertThat(evaluatedResult).isEqualTo(expectedResult);
  }

  @Test
  @TestParameters("{indexOf: '  '}")
  @TestParameters("{indexOf: 'a'}")
  @TestParameters("{indexOf: 'abc'}")
  @TestParameters("{indexOf: '나'}")
  @TestParameters("{indexOf: '😁'}")
  public void indexOf_onEmptyString_throwsException(String indexOf) throws Exception {
    CelAbstractSyntaxTree ast = COMPILER.compile("''.indexOf(indexOfParam)").getAst();
    CelRuntime.Program program = RUNTIME.createProgram(ast);

    CelEvaluationException exception =
        assertThrows(
            CelEvaluationException.class,
            () -> program.eval(ImmutableMap.of("indexOfParam", indexOf)));

    assertThat(exception).hasMessageThat().contains("indexOf failure: Offset out of range");
  }

  @Test
  @TestParameters("{string: '', indexOf: '', offset: 0, expectedResult: 0}")
  @TestParameters("{string: '', indexOf: '', offset: -5, expectedResult: -5}") // This is valid
  @TestParameters("{string: 'hello mellow', indexOf: '', offset: -10, expectedResult: -10}")
  @TestParameters("{string: 'hello mellow', indexOf: 'h', offset: 0, expectedResult: 0}")
  @TestParameters("{string: 'hello mellow', indexOf: 'h', offset: 1, expectedResult: -1}")
  @TestParameters("{string: 'hello mellow', indexOf: 'hello', offset: 0, expectedResult: 0}")
  @TestParameters("{string: 'hello mellow', indexOf: 'ello', offset: 0, expectedResult: 1}")
  @TestParameters("{string: 'hello mellow', indexOf: 'ello', offset: 1, expectedResult: 1}")
  @TestParameters("{string: 'hello mellow', indexOf: 'ello', offset: 2, expectedResult: 7}")
  @TestParameters("{string: 'hello mellow', indexOf: '', offset: 2, expectedResult: 2}")
  @TestParameters("{string: 'hello mellow', indexOf: 'l', offset: 0, expectedResult: 2}")
  @TestParameters("{string: 'hello mellow', indexOf: 'l', offset: 1, expectedResult: 2}")
  @TestParameters("{string: 'hello mellow', indexOf: 'l', offset: 2, expectedResult: 2}")
  @TestParameters("{string: 'hello mellow', indexOf: 'l', offset: 3, expectedResult: 3}")
  @TestParameters("{string: 'hello mellow', indexOf: 'l', offset: 4, expectedResult: 8}")
  @TestParameters("{string: 'hello mellow', indexOf: 'l', offset: 9, expectedResult: 9}")
  @TestParameters("{string: 'hello mellow', indexOf: 'l', offset: 10, expectedResult: -1}")
  public void indexOf_asciiWithOffset_success(
      String string, String indexOf, int offset, int expectedResult) throws Exception {
    CelAbstractSyntaxTree ast = COMPILER.compile("s.indexOf(indexOfParam, offset)").getAst();
    CelRuntime.Program program = RUNTIME.createProgram(ast);

    Object evaluatedResult =
        program.eval(ImmutableMap.of("s", string, "indexOfParam", indexOf, "offset", offset));

    assertThat(evaluatedResult).isEqualTo(expectedResult);
  }

  @Test
  // SMP
  @TestParameters("{string: '😁😑 😦', indexOf: '😁😑 😦', offset: 0, expectedResult: 0}")
  @TestParameters("{string: '😁😑 😦', indexOf: '😁😑 😦', offset: 1, expectedResult: -1}")
  @TestParameters("{string: '😁😑 😦', indexOf: '😁😑😦', offset: 0, expectedResult: -1}")
  @TestParameters("{string: '😁😑 😦', indexOf: ' ', offset: 0, expectedResult: 2}")
  @TestParameters("{string: '😁😑 😦', indexOf: '😁', offset: 0, expectedResult: 0}")
  @TestParameters("{string: '😁😑 😦', indexOf: '😑', offset: 0, expectedResult: 1}")
  @TestParameters("{string: '😁😑 😦', indexOf: '😑', offset: 1, expectedResult: 1}")
  @TestParameters("{string: '😁😑 😦', indexOf: '😑', offset: 2, expectedResult: -1}")
  @TestParameters("{string: '😁😑 😦', indexOf: '😁😑', offset: 0, expectedResult: 0}")
  @TestParameters("{string: '😁😑 😦', indexOf: '😁😑 ', offset: 0, expectedResult: 0}")
  @TestParameters("{string: '😁😑 😦', indexOf: '😑 😦', offset: 0, expectedResult: 1}")
  @TestParameters("{string: '😁😑 😦', indexOf: '😑 😦', offset: 1, expectedResult: 1}")
  @TestParameters("{string: '😁😑 😦', indexOf: '😑 😦', offset: 2, expectedResult: -1}")
  @TestParameters("{string: '😁😑 😦', indexOf: ' 😦', offset: 0, expectedResult: 2}")
  @TestParameters("{string: '😁😑 😦', indexOf: ' 😦', offset: 1, expectedResult: 2}")
  @TestParameters("{string: '😁😑 😦', indexOf: '😦', offset: 2, expectedResult: 3}")
  @TestParameters("{string: '😁😑 😦', indexOf: '😦', offset: 3, expectedResult: 3}")
  @TestParameters("{string: '😁😑 😦', indexOf: '😦 ', offset: 3, expectedResult: -1}")
  // BMP/SMP Mixed
  @TestParameters("{string: 'a😁😑 나😦😁😑다', indexOf: ' ', offset: 0, expectedResult: 3}")
  @TestParameters("{string: 'a😁😑 나😦😁😑다', indexOf: ' ', offset: 3, expectedResult: 3}")
  @TestParameters("{string: 'a😁😑 나😦😁😑다', indexOf: ' ', offset: 4, expectedResult: -1}")
  @TestParameters("{string: 'a😁😑 나😦😁😑다', indexOf: '😁😑', offset: 1, expectedResult: 1}")
  @TestParameters("{string: 'a😁😑 나😦😁😑다', indexOf: '😁😑', offset: 3, expectedResult: 6}")
  @TestParameters("{string: 'a😁😑 나😦😁😑다', indexOf: '😁😑', offset: 6, expectedResult: 6}")
  @TestParameters("{string: 'a😁😑 나😦😁😑다', indexOf: '😁😑', offset: 7, expectedResult: -1}")
  @TestParameters("{string: 'a😁😑 나😦😁😑다', indexOf: '나😦', offset: 0, expectedResult: 4}")
  @TestParameters("{string: 'a😁😑 나😦😁😑다', indexOf: '나😦', offset: 4, expectedResult: 4}")
  @TestParameters("{string: 'a😁😑 나😦😁😑다', indexOf: '나😁', offset: 0, expectedResult: -1}")
  @TestParameters("{string: 'a😁😑 나😦😁😑다', indexOf: ' 나😦😁😑다', offset: 0, expectedResult: 3}")
  @TestParameters("{string: 'a😁😑 나😦😁😑다', indexOf: ' 나😦😁😑다', offset: 3, expectedResult: 3}")
  @TestParameters("{string: 'a😁😑 나😦😁😑다', indexOf: ' 나😦😁😑다', offset: 4, expectedResult: -1}")
  @TestParameters(
      "{string: 'a😁😑 나😦😁😑다', indexOf: 'a😁😑 나😦😁😑다', offset: 0, expectedResult: 0}")
  @TestParameters(
      "{string: 'a😁😑 나😦😁😑다', indexOf: 'a😁😑 나😦😁😑다', offset: 1, expectedResult: -1}")
  @TestParameters(
      "{string: 'a😁😑 나😦😁😑다', indexOf: 'a😁😑 나😦😁😑다😁', offset: 0, expectedResult: -1}")
  public void indexOf_unicodeWithOffset_success(
      String string, String indexOf, int offset, int expectedResult) throws Exception {
    CelAbstractSyntaxTree ast = COMPILER.compile("s.indexOf(indexOfParam, offset)").getAst();
    CelRuntime.Program program = RUNTIME.createProgram(ast);

    Object evaluatedResult =
        program.eval(ImmutableMap.of("s", string, "indexOfParam", indexOf, "offset", offset));

    assertThat(evaluatedResult).isEqualTo(expectedResult);
  }

  @Test
  @TestParameters("{string: '', indexOf: ' ', offset: 1}")
  @TestParameters("{string: 'hello mellow', indexOf: 'hello', offset: 12}")
  @TestParameters("{string: 'hello mellow', indexOf: 'mellow', offset: 12}")
  @TestParameters("{string: '😁😑', indexOf: '😁', offset: 3}")
  @TestParameters("{string: 'a😁😑d', indexOf: '😁', offset: 5}")
  @TestParameters("{string: '😁😑 😦', indexOf: '😦', offset: 4}")
  public void indexOf_withOffsetOutOfBounds_throwsException(
      String string, String indexOf, int offset) throws Exception {
    CelAbstractSyntaxTree ast = COMPILER.compile("s.indexOf(indexOfParam, offset)").getAst();
    CelRuntime.Program program = RUNTIME.createProgram(ast);

    CelEvaluationException exception =
        assertThrows(
            CelEvaluationException.class,
            () ->
                program.eval(
                    ImmutableMap.of("s", string, "indexOfParam", indexOf, "offset", offset)));

    assertThat(exception).hasMessageThat().contains("indexOf failure: Offset out of range");
  }

  @Test
  public void indexOf_offsetOverflow_throwsException() throws Exception {
    CelAbstractSyntaxTree ast = COMPILER.compile("'test'.indexOf('t', offset)").getAst();
    CelRuntime.Program program = RUNTIME.createProgram(ast);

    CelEvaluationException exception =
        assertThrows(
            CelEvaluationException.class,
            () -> program.eval(ImmutableMap.of("offset", 2147483648L))); // INT_MAX + 1

    assertThat(exception)
        .hasMessageThat()
        .contains("indexOf failure: Offset must not exceed the int32 range: 2147483648");
  }

  @Test
  @TestParameters("{list: '[]', expectedResult: ''}")
  @TestParameters("{list: '['' '']', expectedResult: ' '}")
  @TestParameters("{list: '[''x'']', expectedResult: 'x'}")
  @TestParameters("{list: '[''x'', ''y'']', expectedResult: 'xy'}")
  @TestParameters("{list: '[''x'', dyn(''y'')]', expectedResult: 'xy'}")
  @TestParameters("{list: '[dyn(''x''), dyn(''y'')]', expectedResult: 'xy'}")
  @TestParameters("{list: '[''x'', '' '', '' y '', ''z '']', expectedResult: 'x  y z '}")
  @TestParameters("{list: '[''hello '', ''world'']', expectedResult: 'hello world'}")
  public void join_ascii_success(String list, String expectedResult) throws Exception {
    CelAbstractSyntaxTree ast = COMPILER.compile(String.format("%s.join()", list)).getAst();
    CelRuntime.Program program = RUNTIME.createProgram(ast);

    String result = (String) program.eval();

    assertThat(result).isEqualTo(expectedResult);
  }

  @Test
  @TestParameters("{list: '[''가'', ''😁'']', expectedResult: '가😁'}")
  @TestParameters("{list: '[''😁😦😑 😦'', ''나'']', expectedResult: '😁😦😑 😦나'}")
  public void join_unicode_success(String list, String expectedResult) throws Exception {
    CelAbstractSyntaxTree ast = COMPILER.compile(String.format("%s.join()", list)).getAst();
    CelRuntime.Program program = RUNTIME.createProgram(ast);

    String result = (String) program.eval();

    assertThat(result).isEqualTo(expectedResult);
  }

  @Test
  @TestParameters("{list: '[]', separator: '', expectedResult: ''}")
  @TestParameters("{list: '[]', separator: '-', expectedResult: ''}")
  @TestParameters("{list: '['' '']', separator: '', expectedResult: ' '}")
  @TestParameters("{list: '['' '']', separator: '-', expectedResult: ' '}")
  @TestParameters("{list: '[''x'']', separator: '', expectedResult: 'x'}")
  @TestParameters("{list: '[''x'']', separator: '+', expectedResult: 'x'}")
  @TestParameters("{list: '[''x'', ''y'']', separator: ' ', expectedResult: 'x y'}")
  @TestParameters("{list: '[''x'', ''y'']', separator: '+', expectedResult: 'x+y'}")
  @TestParameters("{list: '[''x'', dyn(''y'')]', separator: ' $ ', expectedResult: 'x $ y'}")
  @TestParameters("{list: '[dyn(''x''), dyn(''y'')]', separator: 'x', expectedResult: 'xxy'}")
  @TestParameters(
      "{list: '[''x'', '' '', '' y '', ''z '']', separator: '', expectedResult: 'x  y z '}")
  @TestParameters(
      "{list: '[''x'', '' '', '' y '', ''z '']', separator: '+', expectedResult: 'x+ + y +z '}")
  @TestParameters(
      "{list: '[''hello '', ''world'']', separator: '/', expectedResult: 'hello /world'}")
  public void join_asciiWithSeparator_success(String list, String separator, String expectedResult)
      throws Exception {
    CelAbstractSyntaxTree ast =
        COMPILER.compile(String.format("%s.join('%s')", list, separator)).getAst();
    CelRuntime.Program program = RUNTIME.createProgram(ast);

    String result = (String) program.eval();

    assertThat(result).isEqualTo(expectedResult);
  }

  @Test
  @TestParameters("{list: '[''가'', ''😁'']', separator: '', expectedResult: '가😁'}")
  @TestParameters("{list: '[''가'', ''😁'']', separator: ' ', expectedResult: '가 😁'}")
  @TestParameters("{list: '[''가'', ''😁'']', separator: '+', expectedResult: '가+😁'}")
  @TestParameters("{list: '[''😁😦😑 😦'', ''나'']', separator: 't', expectedResult: '😁😦😑 😦t나'}")
  @TestParameters(
      "{list: '[''😁😦'', ''a'', '' '', ''😑'', ''나'']', separator: '-', expectedResult: '😁😦-a-"
          + " -😑-나'}")
  public void join_unicodeWithSeparator_success(
      String list, String separator, String expectedResult) throws Exception {
    CelAbstractSyntaxTree ast =
        COMPILER.compile(String.format("%s.join('%s')", list, separator)).getAst();
    CelRuntime.Program program = RUNTIME.createProgram(ast);

    String result = (String) program.eval();

    assertThat(result).isEqualTo(expectedResult);
  }

  @Test
  public void join_separatorIsNonString_throwsException() {
    CelValidationException exception =
        assertThrows(
            CelValidationException.class, () -> COMPILER.compile("['x','y'].join(2)").getAst());

    assertThat(exception).hasMessageThat().contains("found no matching overload for 'join'");
  }

  @Test
  @TestParameters("{string: '@', lastIndexOf: '@@', expectedResult: -1}")
  @TestParameters("{string: '', lastIndexOf: '', expectedResult: 0}")
  @TestParameters("{string: 'hello mellow', lastIndexOf: '', expectedResult: 12}")
  @TestParameters("{string: 'hello mellow', lastIndexOf: 'hello', expectedResult: 0}")
  @TestParameters("{string: 'hello mellow', lastIndexOf: 'hello mellow', expectedResult: 0}")
  @TestParameters("{string: 'hello mellow', lastIndexOf: 'ello', expectedResult: 7}")
  @TestParameters("{string: 'hello mellow', lastIndexOf: 'l', expectedResult: 9}")
  @TestParameters("{string: 'hello mellow', lastIndexOf: 'll', expectedResult: 8}")
  @TestParameters("{string: 'hello mellow', lastIndexOf: 'lo', expectedResult: 9}")
  @TestParameters("{string: 'hello mellow', lastIndexOf: 'o', expectedResult: 10}")
  @TestParameters("{string: 'hello mellow', lastIndexOf: 'o ', expectedResult: 4}")
  @TestParameters("{string: 'hello mellow', lastIndexOf: ' ', expectedResult: 5}")
  @TestParameters("{string: 'hello mellow', lastIndexOf: 'mellow', expectedResult: 6}")
  @TestParameters("{string: 'hello mellow', lastIndexOf: 'ellow', expectedResult: 7}")
  @TestParameters("{string: 'hello mellow', lastIndexOf: 'llow', expectedResult: 8}")
  @TestParameters("{string: 'hello mellow', lastIndexOf: 'low', expectedResult: 9}")
  @TestParameters("{string: 'hello mellow', lastIndexOf: 'ow', expectedResult: 10}")
  @TestParameters("{string: 'hello mellow', lastIndexOf: 'w', expectedResult: 11}")
  @TestParameters("{string: 'hello mellow', lastIndexOf: 'hellomellow', expectedResult: -1}")
  @TestParameters("{string: 'hello mellow', lastIndexOf: 'jello', expectedResult: -1}")
  @TestParameters("{string: 'hello mellow', lastIndexOf: '  ', expectedResult: -1}")
  public void lastIndexOf_ascii_success(String string, String lastIndexOf, int expectedResult)
      throws Exception {
    CelAbstractSyntaxTree ast = COMPILER.compile("s.lastIndexOf(indexOfParam)").getAst();
    CelRuntime.Program program = RUNTIME.createProgram(ast);

    Object evaluatedResult =
        program.eval(ImmutableMap.of("s", string, "indexOfParam", lastIndexOf));

    assertThat(evaluatedResult).isEqualTo(expectedResult);
  }

  @Test
  // SMP
  @TestParameters("{string: '😁😦😑 😦', lastIndexOf: ' ', expectedResult: 3}")
  @TestParameters("{string: '😁😦😑 😦', lastIndexOf: '  ', expectedResult: -1}")
  @TestParameters("{string: '😁😦😑 😦', lastIndexOf: '😁', expectedResult: 0}")
  @TestParameters("{string: '😁😦😑 😦', lastIndexOf: '😑', expectedResult: 2}")
  @TestParameters("{string: '😁😦😑 😦', lastIndexOf: '😦', expectedResult: 4}")
  @TestParameters("{string: '😁😦😑 😦', lastIndexOf: '😁😑', expectedResult: -1}")
  @TestParameters("{string: '😁😦😑 😦', lastIndexOf: '😑 😦', expectedResult: 2}")
  @TestParameters("{string: '😁😦😑 😦', lastIndexOf: ' 😦', expectedResult: 3}")
  @TestParameters("{string: '😁😦😑 😦', lastIndexOf: '😦😑', expectedResult: 1}")
  @TestParameters("{string: '😁😦😑 😦', lastIndexOf: ' 😦 ', expectedResult: -1}")
  @TestParameters("{string: '😁😦😑 😦', lastIndexOf: '😁😦😑 😦', expectedResult: 0}")
  @TestParameters("{string: '😁😦😑 😦', lastIndexOf: '😁😦😑😦', expectedResult: -1}")
  @TestParameters("{string: '😁😦😑 😦', lastIndexOf: '😁😑😦', expectedResult: -1}")
  // BMP/SMP Mixed
  @TestParameters("{string: 'a😁😑 나😦😁😑다', lastIndexOf: ' ', expectedResult: 3}")
  @TestParameters("{string: 'a😁😑 나😦😁😑다', lastIndexOf: '😁😑', expectedResult: 6}")
  @TestParameters("{string: 'a😁😑 나😦😁😑다', lastIndexOf: '나😦', expectedResult: 4}")
  @TestParameters("{string: 'a😁😑 나😦😁😑다', lastIndexOf: '나😁', expectedResult: -1}")
  @TestParameters("{string: 'a😁😑 나😦😁😑다', lastIndexOf: ' 나😦😁😑다', expectedResult: 3}")
  @TestParameters("{string: 'a😁😑 나😦😁😑다', lastIndexOf: 'a😁😑 나😦😁😑다', expectedResult: 0}")
  @TestParameters("{string: 'a😁😑 나😦😁😑다', lastIndexOf: 'a😁😑 나😦😁😑다😁', expectedResult: -1}")
  public void lastIndexOf_unicode_success(String string, String lastIndexOf, int expectedResult)
      throws Exception {
    CelAbstractSyntaxTree ast = COMPILER.compile("s.lastIndexOf(indexOfParam)").getAst();
    CelRuntime.Program program = RUNTIME.createProgram(ast);

    Object evaluatedResult =
        program.eval(ImmutableMap.of("s", string, "indexOfParam", lastIndexOf));

    assertThat(evaluatedResult).isEqualTo(expectedResult);
  }

  @Test
  @TestParameters("{lastIndexOf: '@@'}")
  @TestParameters("{lastIndexOf: '  '}")
  @TestParameters("{lastIndexOf: 'a'}")
  @TestParameters("{lastIndexOf: 'abc'}")
  @TestParameters("{lastIndexOf: '나'}")
  @TestParameters("{lastIndexOf: '😁'}")
  public void lastIndexOf_strLengthLessThanSubstrLength_returnsMinusOne(String lastIndexOf)
      throws Exception {
    CelAbstractSyntaxTree ast = COMPILER.compile("''.lastIndexOf(indexOfParam)").getAst();
    CelRuntime.Program program = RUNTIME.createProgram(ast);

    Object evaluatedResult = program.eval(ImmutableMap.of("s", "", "indexOfParam", lastIndexOf));

    assertThat(evaluatedResult).isEqualTo(-1);
  }

  @Test
  @TestParameters("{string: '', lastIndexOf: '', offset: 0, expectedResult: 0}")
  @TestParameters("{string: '', lastIndexOf: '', offset: -5, expectedResult: -5}") // This is valid
  @TestParameters("{string: 'hello mellow', lastIndexOf: '', offset: 2, expectedResult: 2}")
  @TestParameters("{string: 'hello mellow', lastIndexOf: '', offset: -10, expectedResult: -10}")
  @TestParameters("{string: 'hello mellow', lastIndexOf: 'h', offset: 0, expectedResult: 0}")
  @TestParameters("{string: 'hello mellow', lastIndexOf: 'h', offset: 1, expectedResult: 0}")
  @TestParameters("{string: 'hello mellow', lastIndexOf: 'hello', offset: 0, expectedResult: 0}")
  @TestParameters("{string: 'hello mellow', lastIndexOf: 'ello', offset: 0, expectedResult: -1}")
  @TestParameters("{string: 'hello mellow', lastIndexOf: 'ello', offset: 1, expectedResult: 1}")
  @TestParameters("{string: 'hello mellow', lastIndexOf: 'ello', offset: 6, expectedResult: 1}")
  @TestParameters("{string: 'hello mellow', lastIndexOf: 'ello', offset: 7, expectedResult: 7}")
  @TestParameters("{string: 'hello mellow', lastIndexOf: 'l', offset: 2, expectedResult: 2}")
  @TestParameters("{string: 'hello mellow', lastIndexOf: 'l', offset: 3, expectedResult: 3}")
  @TestParameters("{string: 'hello mellow', lastIndexOf: 'l', offset: 4, expectedResult: 3}")
  @TestParameters("{string: 'hello mellow', lastIndexOf: 'l', offset: 8, expectedResult: 8}")
  @TestParameters("{string: 'hello mellow', lastIndexOf: 'l', offset: 9, expectedResult: 9}")
  @TestParameters("{string: 'hello mellow', lastIndexOf: 'l', offset: 10, expectedResult: 9}")
  @TestParameters("{string: 'hello mellow', lastIndexOf: 'l', offset: 0, expectedResult: -1}")
  @TestParameters("{string: 'hello mellow', lastIndexOf: 'l', offset: 1, expectedResult: -1}")
  @TestParameters("{string: 'hello mellow', lastIndexOf: 'lo', offset: 3, expectedResult: 3}")
  @TestParameters("{string: 'hello mellow', lastIndexOf: 'lo', offset: 10, expectedResult: 9}")
  @TestParameters("{string: 'hello mellow', lastIndexOf: 'lo ', offset: 10, expectedResult: 3}")
  @TestParameters(
      "{string: 'hello mellow', lastIndexOf: 'hello mellowwww ', offset: 11, expectedResult: -1}")
  public void lastIndexOf_asciiWithOffset_success(
      String string, String lastIndexOf, int offset, int expectedResult) throws Exception {
    CelAbstractSyntaxTree ast = COMPILER.compile("s.lastIndexOf(indexOfParam, offset)").getAst();
    CelRuntime.Program program = RUNTIME.createProgram(ast);

    Object evaluatedResult =
        program.eval(ImmutableMap.of("s", string, "indexOfParam", lastIndexOf, "offset", offset));

    assertThat(evaluatedResult).isEqualTo(expectedResult);
  }

  @Test
  // SMP
  @TestParameters("{string: '😁😦😑 😦', lastIndexOf: '😁😦😑 😦', offset: 0, expectedResult: 0}")
  @TestParameters("{string: '😁😦😑 😦', lastIndexOf: '😁😦😑 😦', offset: 1, expectedResult: 0}")
  @TestParameters("{string: '😁😦😑 😦', lastIndexOf: '😁😑😦', offset: 0, expectedResult: -1}")
  @TestParameters("{string: '😁😦😑 😦', lastIndexOf: ' ', offset: 0, expectedResult: -1}")
  @TestParameters("{string: '😁😦😑 😦', lastIndexOf: ' ', offset: 2, expectedResult: -1}")
  @TestParameters("{string: '😁😦😑 😦', lastIndexOf: ' ', offset: 3, expectedResult: 3}")
  @TestParameters("{string: '😁😦😑 😦', lastIndexOf: '😁', offset: 0, expectedResult: 0}")
  @TestParameters("{string: '😁😦😑 😦', lastIndexOf: '😁', offset: 4, expectedResult: 0}")
  @TestParameters("{string: '😁😦😑 😦', lastIndexOf: '😑', offset: 0, expectedResult: -1}")
  @TestParameters("{string: '😁😦😑 😦', lastIndexOf: '😑', offset: 1, expectedResult: -1}")
  @TestParameters("{string: '😁😦😑 😦', lastIndexOf: '😑', offset: 2, expectedResult: 2}")
  @TestParameters("{string: '😁😦😑 😦', lastIndexOf: '😑', offset: 3, expectedResult: 2}")
  @TestParameters("{string: '😁😦😑 😦', lastIndexOf: '😁😑', offset: 4, expectedResult: -1}")
  @TestParameters("{string: '😁😦😑 😦', lastIndexOf: '😦😑', offset: 0, expectedResult: -1}")
  @TestParameters("{string: '😁😦😑 😦', lastIndexOf: '😦😑', offset: 1, expectedResult: 1}")
  @TestParameters("{string: '😁😦😑 😦', lastIndexOf: '😦😑 ', offset: 1, expectedResult: 1}")
  @TestParameters("{string: '😁😦😑 😦', lastIndexOf: '😑 😦', offset: 0, expectedResult: -1}")
  @TestParameters("{string: '😁😦😑 😦', lastIndexOf: '😑 😦', offset: 1, expectedResult: -1}")
  @TestParameters("{string: '😁😦😑 😦', lastIndexOf: '😑 😦', offset: 2, expectedResult: 2}")
  @TestParameters("{string: '😁😦😑 😦', lastIndexOf: ' 😦', offset: 1, expectedResult: -1}")
  @TestParameters("{string: '😁😦😑 😦', lastIndexOf: ' 😦', offset: 3, expectedResult: 3}")
  @TestParameters("{string: '😁😦😑 😦', lastIndexOf: '😦', offset: 0, expectedResult: -1}")
  @TestParameters("{string: '😁😦😑 😦', lastIndexOf: '😦', offset: 1, expectedResult: 1}")
  @TestParameters("{string: '😁😦😑 😦', lastIndexOf: '😦', offset: 2, expectedResult: 1}")
  @TestParameters("{string: '😁😦😑 😦', lastIndexOf: '😦', offset: 3, expectedResult: 1}")
  @TestParameters("{string: '😁😦😑 😦', lastIndexOf: '😦', offset: 4, expectedResult: 4}")
  @TestParameters("{string: '😁😦😑 😦', lastIndexOf: '😦 ', offset: 4, expectedResult: -1}")
  // BMP/SMP Mixed
  @TestParameters("{string: 'a😁😑 나😦😁😑다', lastIndexOf: ' ', offset: 0, expectedResult: -1}")
  @TestParameters("{string: 'a😁😑 나😦😁😑다', lastIndexOf: ' ', offset: 2, expectedResult: -1}")
  @TestParameters("{string: 'a😁😑 나😦😁😑다', lastIndexOf: ' ', offset: 3, expectedResult: 3}")
  @TestParameters("{string: 'a😁😑 나😦😁😑다', lastIndexOf: ' ', offset: 8, expectedResult: 3}")
  @TestParameters("{string: 'a😁😑 나😦😁😑다', lastIndexOf: '😁😑', offset: 0, expectedResult: -1}")
  @TestParameters("{string: 'a😁😑 나😦😁😑다', lastIndexOf: '😁😑', offset: 1, expectedResult: 1}")
  @TestParameters("{string: 'a😁😑 나😦😁😑다', lastIndexOf: '😁😑', offset: 5, expectedResult: 1}")
  @TestParameters("{string: 'a😁😑 나😦😁😑다', lastIndexOf: '😁😑', offset: 6, expectedResult: 6}")
  @TestParameters("{string: 'a😁😑 나😦😁😑다', lastIndexOf: '😁😑', offset: 8, expectedResult: 6}")
  @TestParameters("{string: 'a😁😑 나😦😁😑다', lastIndexOf: '나😦', offset: 0, expectedResult: -1}")
  @TestParameters("{string: 'a😁😑 나😦😁😑다', lastIndexOf: '나😦', offset: 3, expectedResult: -1}")
  @TestParameters("{string: 'a😁😑 나😦😁😑다', lastIndexOf: '나😦', offset: 4, expectedResult: 4}")
  @TestParameters("{string: 'a😁😑 나😦😁😑다', lastIndexOf: '나😦', offset: 8, expectedResult: 4}")
  @TestParameters("{string: 'a😁😑 나😦😁😑다', lastIndexOf: '나😁', offset: 8, expectedResult: -1}")
  @TestParameters(
      "{string: 'a😁😑 나😦😁😑다', lastIndexOf: ' 나😦😁😑다', offset: 0, expectedResult: -1}")
  @TestParameters(
      "{string: 'a😁😑 나😦😁😑다', lastIndexOf: ' 나😦😁😑다', offset: 2, expectedResult: -1}")
  @TestParameters(
      "{string: 'a😁😑 나😦😁😑다', lastIndexOf: ' 나😦😁😑다', offset: 3, expectedResult: 3}")
  @TestParameters(
      "{string: 'a😁😑 나😦😁😑다', lastIndexOf: ' 나😦😁😑다', offset: 4, expectedResult: 3}")
  @TestParameters(
      "{string: 'a😁😑 나😦😁😑다', lastIndexOf: ' 나😦😁😑다', offset: 8, expectedResult: 3}")
  @TestParameters(
      "{string: 'a😁😑 나😦😁😑다', lastIndexOf: 'a😁😑 나😦😁😑다', offset: 0, expectedResult: 0}")
  @TestParameters(
      "{string: 'a😁😑 나😦😁😑다', lastIndexOf: 'a😁😑 나😦😁😑다', offset: 1, expectedResult: 0}")
  @TestParameters(
      "{string: 'a😁😑 나😦😁😑다', lastIndexOf: 'a😁😑 나😦😁😑다', offset: 8, expectedResult: 0}")
  @TestParameters(
      "{string: 'a😁😑 나😦😁😑다', lastIndexOf: 'a😁😑 나😦😁😑다😁', offset: 0, expectedResult: -1}")
  @TestParameters(
      "{string: 'a😁😑 나😦😁😑다', lastIndexOf: 'a😁😑 나😦😁😑다😁', offset: 8, expectedResult: -1}")
  public void lastIndexOf_unicodeWithOffset_success(
      String string, String lastIndexOf, int offset, int expectedResult) throws Exception {
    CelAbstractSyntaxTree ast = COMPILER.compile("s.lastIndexOf(indexOfParam, offset)").getAst();
    CelRuntime.Program program = RUNTIME.createProgram(ast);

    Object evaluatedResult =
        program.eval(ImmutableMap.of("s", string, "indexOfParam", lastIndexOf, "offset", offset));

    assertThat(evaluatedResult).isEqualTo(expectedResult);
  }

  @Test
  @TestParameters("{string: '', lastIndexOf: ' ', offset: 1}")
  @TestParameters("{string: 'hello mellow', lastIndexOf: 'hello', offset: 12}")
  @TestParameters("{string: 'hello mellow', lastIndexOf: 'mellow', offset: 12}")
  @TestParameters("{string: '😁😑', lastIndexOf: '😁', offset: 3}")
  @TestParameters("{string: 'a😁😑d', lastIndexOf: '😁', offset: 5}")
  @TestParameters("{string: '😁😑 😦', lastIndexOf: '😦', offset: 4}")
  public void lastIndexOf_withOffsetOutOfBounds_throwsException(
      String string, String lastIndexOf, int offset) throws Exception {
    CelAbstractSyntaxTree ast = COMPILER.compile("s.lastIndexOf(indexOfParam, offset)").getAst();
    CelRuntime.Program program = RUNTIME.createProgram(ast);

    CelEvaluationException exception =
        assertThrows(
            CelEvaluationException.class,
            () ->
                program.eval(
                    ImmutableMap.of("s", string, "indexOfParam", lastIndexOf, "offset", offset)));

    assertThat(exception).hasMessageThat().contains("lastIndexOf failure: Offset out of range");
  }

  @Test
  public void lastIndexOf_offsetOverflow_throwsException() throws Exception {
    CelAbstractSyntaxTree ast = COMPILER.compile("'test'.lastIndexOf('t', offset)").getAst();
    CelRuntime.Program program = RUNTIME.createProgram(ast);

    CelEvaluationException exception =
        assertThrows(
            CelEvaluationException.class,
            () -> program.eval(ImmutableMap.of("offset", 2147483648L))); // INT_MAX + 1

    assertThat(exception)
        .hasMessageThat()
        .contains("lastIndexOf failure: Offset must not exceed the int32 range: 2147483648");
  }

  @Test
  @TestParameters("{string: '', searchString: '', replacement: '', expectedResult: ''}")
  @TestParameters("{string: '', searchString: '', replacement: 'hi', expectedResult: 'hi'}")
  @TestParameters("{string: '', searchString: 'test', replacement: 'hi', expectedResult: ''}")
  @TestParameters("{string: 'a b', searchString: '', replacement: 'a', expectedResult: 'aaa aba'}")
  @TestParameters(
      "{string: 'hello hello hello', searchString: 'hello', replacement: 'hi', expectedResult: 'hi"
          + " hi hi'}")
  @TestParameters(
      "{string: 'hello hello hello', searchString: 'hello ', replacement: 'hi', expectedResult:"
          + " 'hihihello'}")
  @TestParameters(
      "{string: 'hello hello hello', searchString: '', replacement: 'hi', expectedResult:"
          + " 'hihhiehilhilhiohi hihhiehilhilhiohi hihhiehilhilhiohi'}")
  @TestParameters(
      "{string: '!@#$%^&*/();?:\\\\', searchString: '!@#$%^&*/();?:\\\\', replacement: 'test',"
          + " expectedResult: 'test'}")
  public void replace_ascii_success(
      String string, String searchString, String replacement, String expectedResult)
      throws Exception {
    CelAbstractSyntaxTree ast =
        COMPILER
            .compile(String.format("'%s'.replace('%s', '%s')", string, searchString, replacement))
            .getAst();
    CelRuntime.Program program = RUNTIME.createProgram(ast);

    Object evaluatedResult = program.eval();

    assertThat(evaluatedResult).isEqualTo(expectedResult);
  }

  @Test
  @TestParameters("{string: '😁😑', searchString: '', replacement: 'a', expectedResult: 'a😁a😑a'}")
  @TestParameters(
      "{string: '😁😑😦 😁😑😦', searchString: '😁😑', replacement: '😆', expectedResult: '😆😦"
          + " 😆😦'}")
  @TestParameters(
      "{string: 'a😁😑 나😦😁😑다', searchString: '', replacement: 'test', expectedResult:"
          + " 'testatest😁test😑test test나test😦test😁test😑test다test'}")
  @TestParameters(
      "{string: 'a😁😑 나😦😁😑다', searchString: '😁😑', replacement: ' 나😦😁', expectedResult: 'a"
          + " 나😦😁 나😦 나😦😁다'}")
  public void replace_unicode_success(
      String string, String searchString, String replacement, String expectedResult)
      throws Exception {
    CelAbstractSyntaxTree ast =
        COMPILER
            .compile(String.format("'%s'.replace('%s', '%s')", string, searchString, replacement))
            .getAst();
    CelRuntime.Program program = RUNTIME.createProgram(ast);

    Object evaluatedResult = program.eval();

    assertThat(evaluatedResult).isEqualTo(expectedResult);
  }

  @Test
  @TestParameters("{string: '', searchString: '', replacement: '', limit: -1, expectedResult: ''}")
  @TestParameters(
      "{string: '', searchString: '', replacement: 'hi', limit: -1, expectedResult: 'hi'}")
  @TestParameters("{string: '', searchString: '', replacement: 'hi', limit: 0, expectedResult: ''}")
  @TestParameters(
      "{string: '', searchString: '', replacement: 'hi', limit: 1, expectedResult: 'hi'}")
  @TestParameters(
      "{string: '', searchString: '', replacement: 'hi', limit: 2, expectedResult: 'hi'}")
  @TestParameters(
      "{string: '', searchString: 'test', replacement: 'hi', limit: -1, expectedResult: ''}")
  @TestParameters(
      "{string: '', searchString: 'test', replacement: 'hi', limit: 0, expectedResult: ''}")
  @TestParameters(
      "{string: '', searchString: 'test', replacement: 'hi', limit: 1, expectedResult: ''}")
  @TestParameters(
      "{string: 'a b', searchString: '', replacement: 'a', limit: 0, expectedResult: 'a b'}")
  @TestParameters(
      "{string: 'a b', searchString: '', replacement: 'a', limit: 1, expectedResult: 'aa b'}")
  @TestParameters(
      "{string: 'a b', searchString: '', replacement: 'a', limit: 2, expectedResult: 'aaa b'}")
  @TestParameters(
      "{string: 'a b', searchString: '', replacement: 'a', limit: 3, expectedResult: 'aaa ab'}")
  @TestParameters(
      "{string: 'a b', searchString: '', replacement: 'a', limit: 4, expectedResult: 'aaa aba'}")
  @TestParameters(
      "{string: 'a b', searchString: '', replacement: 'a', limit: 5, expectedResult: 'aaa aba'}")
  @TestParameters(
      "{string: 'a b', searchString: '', replacement: 'a', limit: -1, expectedResult: 'aaa aba'}")
  @TestParameters(
      "{string: 'hello', searchString: 'random', replacement: 'hi', limit: 1, expectedResult:"
          + " 'hello'}")
  @TestParameters(
      "{string: 'hello hello hello', searchString: 'hello', replacement: 'hi', limit: -1,"
          + " expectedResult: 'hi hi hi'}")
  @TestParameters(
      "{string: 'hello hello hello', searchString: 'hello', replacement: 'hi', limit: 0,"
          + " expectedResult: 'hello hello hello'}")
  @TestParameters(
      "{string: 'hello hello hello', searchString: 'hello', replacement: 'hi', limit: 1,"
          + " expectedResult: 'hi hello hello'}")
  @TestParameters(
      "{string: 'hello hello hello', searchString: 'hello', replacement: 'hi', limit: 2,"
          + " expectedResult: 'hi hi hello'}")
  @TestParameters(
      "{string: 'hello hello hello', searchString: 'hello', replacement: 'hi', limit: 3,"
          + " expectedResult: 'hi hi hi'}")
  @TestParameters(
      "{string: 'hello hello hello', searchString: 'hello hello', replacement: 'hi hello', limit:"
          + " 1, expectedResult: 'hi hello hello'}")
  @TestParameters(
      "{string: 'hello hello hello', searchString: 'hello hello hello', replacement: '', limit:"
          + " 1, expectedResult: ''}")
  @TestParameters(
      "{string: 'hello hello', searchString: 'he', replacement: 'we', limit: 1,"
          + " expectedResult: 'wello hello'}")
  @TestParameters(
      "{string: 'hello hello hello', searchString: '', replacement: 'hi', limit: -1,"
          + " expectedResult: 'hihhiehilhilhiohi hihhiehilhilhiohi hihhiehilhilhiohi'}")
  @TestParameters(
      "{string: 'hello hello hello', searchString: '', replacement: 'hi', limit: 0,"
          + " expectedResult: 'hello hello hello'}")
  @TestParameters(
      "{string: 'hello hello hello', searchString: '', replacement: 'hi', limit: 1,"
          + " expectedResult: 'hihello hello hello'}")
  @TestParameters(
      "{string: 'hello hello hello', searchString: '', replacement: 'hi', limit: 2,"
          + " expectedResult: 'hihhiello hello hello'}")
  @TestParameters(
      "{string: '!@#$%^&*/();?:\\\\', searchString: '!@#$%^&*/();?:\\\\', replacement: 'test',"
          + " limit: 1, expectedResult: 'test'}")
  public void replace_ascii_withLimit_success(
      String string, String searchString, String replacement, int limit, String expectedResult)
      throws Exception {
    CelAbstractSyntaxTree ast =
        COMPILER
            .compile(
                String.format(
                    "'%s'.replace('%s', '%s', %d)", string, searchString, replacement, limit))
            .getAst();
    CelRuntime.Program program = RUNTIME.createProgram(ast);

    Object evaluatedResult = program.eval();

    assertThat(evaluatedResult).isEqualTo(expectedResult);
  }

  @Test
  @TestParameters(
      "{string: '😁😑', searchString: '', replacement: 'a', limit: -1, expectedResult: 'a😁a😑a'}")
  @TestParameters(
      "{string: '😁😑😦 😁😑😦', searchString: '😁😑', replacement: '😆', limit: -1,"
          + " expectedResult: '😆😦 😆😦'}")
  @TestParameters(
      "{string: '😁😑😦 😁😑😦', searchString: '😁😑', replacement: '😆', limit: 0,"
          + " expectedResult: '😁😑😦 😁😑😦'}")
  @TestParameters(
      "{string: '😁😑😦 😁😑😦', searchString: '😁😑', replacement: '😆', limit: 1,"
          + " expectedResult: '😆😦 😁😑😦'}")
  @TestParameters(
      "{string: '😁😑😦 😁😑😦', searchString: '😁😑', replacement: '😆', limit: 2,"
          + " expectedResult: '😆😦 😆😦'}")
  @TestParameters(
      "{string: 'a😁😑 나😦😁😑다', searchString: '', replacement: 'test', limit: -1, expectedResult:"
          + " 'testatest😁test😑test test나test😦test😁test😑test다test'}")
  @TestParameters(
      "{string: 'a😁😑 나😦😁😑다', searchString: '', replacement: 'test', limit: 0, expectedResult:"
          + " 'a😁😑 나😦😁😑다'}")
  @TestParameters(
      "{string: 'a😁😑 나😦😁😑다', searchString: '', replacement: 'test', limit: 1, expectedResult:"
          + " 'testa😁😑 나😦😁😑다'}")
  @TestParameters(
      "{string: 'a😁😑 나😦😁😑다', searchString: '', replacement: 'test', limit: 2, expectedResult:"
          + " 'testatest😁😑 나😦😁😑다'}")
  @TestParameters(
      "{string: 'a😁😑 나😦😁😑다', searchString: '', replacement: 'test', limit: 3, expectedResult:"
          + " 'testatest😁test😑 나😦😁😑다'}")
  @TestParameters(
      "{string: 'a😁😑 나😦😁😑다', searchString: '', replacement: 'test', limit: 4, expectedResult:"
          + " 'testatest😁test😑test 나😦😁😑다'}")
  @TestParameters(
      "{string: 'a😁😑 나😦😁😑다', searchString: '😁😑', replacement: ' 나😦😁', limit: -1,"
          + " expectedResult: 'a 나😦😁 나😦 나😦😁다'}")
  @TestParameters(
      "{string: 'a😁😑 나😦😁😑다', searchString: '😁😑', replacement: ' 나😦😁', limit: 0,"
          + " expectedResult: 'a😁😑 나😦😁😑다'}")
  @TestParameters(
      "{string: 'a😁😑 나😦😁😑다', searchString: '😁😑', replacement: ' 나😦😁', limit: 1,"
          + " expectedResult: 'a 나😦😁 나😦😁😑다'}")
  @TestParameters(
      "{string: 'a😁😑 나😦😁😑다', searchString: '😁😑', replacement: ' 나😦😁', limit: 2,"
          + " expectedResult: 'a 나😦😁 나😦 나😦😁다'}")
  public void replace_unicode_withLimit_success(
      String string, String searchString, String replacement, int limit, String expectedResult)
      throws Exception {
    CelAbstractSyntaxTree ast =
        COMPILER
            .compile(
                String.format(
                    "'%s'.replace('%s', '%s', %d)", string, searchString, replacement, limit))
            .getAst();
    CelRuntime.Program program = RUNTIME.createProgram(ast);

    Object evaluatedResult = program.eval();

    assertThat(evaluatedResult).isEqualTo(expectedResult);
  }

  @Test
  public void replace_limitOverflow_throwsException() throws Exception {
    CelAbstractSyntaxTree ast = COMPILER.compile("'test'.replace('','',index)").getAst();
    CelRuntime.Program program = RUNTIME.createProgram(ast);

    CelEvaluationException exception =
        assertThrows(
            CelEvaluationException.class,
            () -> program.eval(ImmutableMap.of("index", 2147483648L))); // INT_MAX + 1

    assertThat(exception)
        .hasMessageThat()
        .contains("replace failure: Index must not exceed the int32 range: 2147483648");
  }

  private enum TrimTestCase {
    ASCII_EMPTY("", ""),
    ASCII_NO_TRIM("test test", "test test"),
    ASCII_LEFT_TRIM("   test test", "test test"),
    ASCII_RIGHT_TRIM("test test  ", "test test"),
    ASCII_ALL_TRIM("  test test  ", "test test"),
    BMP_SMP_NO_TRIM("text 가나다 😦😁😑", "text 가나다 😦😁😑"),
    BMP_SMP_LEFT_TRIM("  text 가나다 😦😁😑", "text 가나다 😦😁😑"),
    BMP_SMP_RIGHT_TRIM("text 가나다 😦😁😑  ", "text 가나다 😦😁😑"),
    BMP_SMP_ALL_TRIM("   text 가나다 😦😁😑  ", "text 가나다 😦😁😑"),
    ESCAPE_SEQUENCES(
        " \f\n\r\ttext  ",
        "text"), // Note: vertical tab (\v) is included below as an escaped sequence (U+000B).
    ALL_WHITESPACES(
        "\u000b\u0085\u00a0\u1680\u200a\u2028\u2029\u202F\u205F\u3000\u2000\u2001\u2002\u2003\u2004\u2004\u2006\u2007\u2008\u2009",
        ""),
    WHITESPACES_ASCII_1("\u000b\u0085\u00a0\u1680text", "text"),
    WHITESPACES_ASCII_2("text\u2000\u2001\u2002\u2003\u2004\u2004\u2006\u2007\u2008\u2009", "text"),
    WHITESPACES_ASCII_3("\u200atext\u2028\u2029\u202F\u205F\u3000", "text"),
    WHITESPACES_BMP_SMP_MIXED_1(
        "\u000b\u0085\u00a0\u1680\u200a\u2028\u2029text 가나다 😦😁😑 "
            + "\u202F\u205F\u3000\u2000\u2001\u2002\u2003\u2004\u2004\u2006\u2007\u2008\u2009",
        "text 가나다 😦😁😑"),
    // Trim test with whitespace-like characters not included.
    WHITESPACE_LIKE(
        "\u180etext\u200b\u200c\u200d\u2060\ufeff", "\u180etext\u200b\u200c\u200d\u2060\ufeff"),
    // Whitespaces in between non-whitespace characters are not removed
    WHITESPACES_IN_BETWEEN(
        "test\u000b\u0085\u00a0\u1680\u200a\u2028\u2029😦😁😑"
            + "\u202F\u205F\u3000\u2000\u2001\u2002\u2003\u2004\u2004\u2006\u2007\u2008\u2009test",
        "test\u000b\u0085\u00a0\u1680\u200a\u2028\u2029😦😁😑"
            + "\u202F\u205F\u3000\u2000\u2001\u2002\u2003\u2004\u2004\u2006\u2007\u2008\u2009test");

    private final String text;
    private final String expectedResult;

    TrimTestCase(String text, String expectedResult) {
      this.text = text;
      this.expectedResult = expectedResult;
    }
  }

  @Test
  public void trim_success(@TestParameter TrimTestCase testCase) throws Exception {
    CelAbstractSyntaxTree ast = COMPILER.compile("s.trim()").getAst();
    CelRuntime.Program program = RUNTIME.createProgram(ast);

    Object evaluatedResult = program.eval(ImmutableMap.of("s", testCase.text));

    assertThat(evaluatedResult).isEqualTo(testCase.expectedResult);
  }

  @Test
  @TestParameters("{string: '', expectedResult: ''}")
  @TestParameters("{string: 'hello world', expectedResult: 'HELLO WORLD'}")
  @TestParameters("{string: 'HELLO WORLD', expectedResult: 'HELLO WORLD'}")
  @TestParameters("{string: 'HeLlO wOrLd', expectedResult: 'HELLO WORLD'}")
  @TestParameters(
      "{string: 'a!@#$%^&*()-_+=?/<>.,;:''\"\\', expectedResult: 'A!@#$%^&*()-_+=?/<>.,;:''\"\\'}")
  public void upperAscii_success(String string, String expectedResult) throws Exception {
    CelAbstractSyntaxTree ast = COMPILER.compile("s.upperAscii()").getAst();
    CelRuntime.Program program = RUNTIME.createProgram(ast);

    Object evaluatedResult = program.eval(ImmutableMap.of("s", string));

    assertThat(evaluatedResult).isEqualTo(expectedResult);
  }

  @Test
  // Some of these characters from Latin Extended plane have a lowercase mapping.
  // In CEL's String extension, we do not transform these because they are outside Latin-1
  @TestParameters("{string: 'ÀßàḀḁḂḃ', expectedResult: 'ÀßàḀḁḂḃ'}")
  @TestParameters("{string: '가나다라 마바사', expectedResult: '가나다라 마바사'}")
  @TestParameters("{string: 'a가b나c다d라e 마f바g사h', expectedResult: 'A가B나C다D라E 마F바G사H'}")
  @TestParameters("{string: '😁😑😦', expectedResult: '😁😑😦'}")
  @TestParameters("{string: '😁😑 😦', expectedResult: '😁😑 😦'}")
  @TestParameters("{string: 'a😁b 😑c가😦d', expectedResult: 'A😁B 😑C가😦D'}")
  public void upperAscii_outsideAscii_success(String string, String expectedResult)
      throws Exception {
    CelAbstractSyntaxTree ast = COMPILER.compile("s.upperAscii()").getAst();
    CelRuntime.Program program = RUNTIME.createProgram(ast);

    Object evaluatedResult = program.eval(ImmutableMap.of("s", string));

    assertThat(evaluatedResult).isEqualTo(expectedResult);
  }

  @Test
  public void stringExtension_functionSubset_success() throws Exception {
    CelStringExtensions stringExtensions =
        CelExtensions.strings(Function.CHAR_AT, Function.SUBSTRING);
    CelCompiler celCompiler =
        CelCompilerFactory.standardCelCompilerBuilder().addLibraries(stringExtensions).build();
    CelRuntime celRuntime =
        CelRuntimeFactory.standardCelRuntimeBuilder().addLibraries(stringExtensions).build();

    Object evaluatedResult =
        celRuntime
            .createProgram(
                celCompiler
                    .compile("'test'.substring(2) == 'st' && 'hello'.charAt(1) == 'e'")
                    .getAst())
            .eval();

    assertThat(evaluatedResult).isEqualTo(true);
  }

  @Test
  @TestParameters("{string: 'abcd', expectedResult: 'dcba'}")
  @TestParameters("{string: '', expectedResult: ''}")
  @TestParameters("{string: 'a', expectedResult: 'a'}")
  @TestParameters("{string: 'hello world', expectedResult: 'dlrow olleh'}")
  @TestParameters("{string: 'ab가cd', expectedResult: 'dc가ba'}")
  public void reverse_success(String string, String expectedResult) throws Exception {
    CelAbstractSyntaxTree ast = COMPILER.compile("s.reverse()").getAst();
    CelRuntime.Program program = RUNTIME.createProgram(ast);

    Object evaluatedResult = program.eval(ImmutableMap.of("s", string));

    assertThat(evaluatedResult).isEqualTo(expectedResult);
  }

  @Test
  public void reverse_unicode() throws Exception {
    CelAbstractSyntaxTree ast = COMPILER.compile("s.reverse()").getAst();
    CelRuntime.Program program = RUNTIME.createProgram(ast);

    Object evaluatedResult = program.eval(ImmutableMap.of("s", "😁😑😦"));

    assertThat(evaluatedResult).isEqualTo("😦😑😁");
  }

  @Test
  @TestParameters("{string: 'hello', expectedResult: '\"hello\"'}")
  @TestParameters("{string: '', expectedResult: '\"\"'}")
  @TestParameters("{string: 'contains \\\"quotes\\\"', expectedResult: '\"contains \\\\\\\"quotes\\\\\\\"\"'}")
  public void quote_success(String string, String expectedResult) throws Exception {
    CelAbstractSyntaxTree ast = COMPILER.compile("strings.quote(s)").getAst();
    CelRuntime.Program program = RUNTIME.createProgram(ast);

    Object evaluatedResult = program.eval(ImmutableMap.of("s", string));

    assertThat(evaluatedResult).isEqualTo(expectedResult);
  }

  @Test
  public void quote_escapesSpecialCharacters() throws Exception {
    CelAbstractSyntaxTree ast = COMPILER.compile("strings.quote(s)").getAst();
    CelRuntime.Program program = RUNTIME.createProgram(ast);

    Object evaluatedResult =
        program.eval(
            ImmutableMap.of(
                "s", "\u0007bell\u000Bvtab\bback\ffeed\rret\nline\ttab\\slash 가 😁"));

    assertThat(evaluatedResult)
        .isEqualTo("\"\\abell\\vvtab\\bback\\ffeed\\rret\\nline\\ttab\\\\slash 가 😁\"");
  }

  @Test
  public void stringExtension_compileUnallowedFunction_throws() {
    CelCompiler celCompiler =
        CelCompilerFactory.standardCelCompilerBuilder()
            .addLibraries(CelExtensions.strings(Function.REPLACE))
            .build();

    assertThrows(
        CelValidationException.class,
        () -> celCompiler.compile("'test'.substring(2) == 'st'").getAst());
  }

  @Test
  public void stringExtension_evaluateUnallowedFunction_throws() throws Exception {
    CelCompiler celCompiler =
        CelCompilerFactory.standardCelCompilerBuilder()
            .addLibraries(CelExtensions.strings(Function.SUBSTRING))
            .build();
    CelRuntime celRuntime =
        CelRuntimeFactory.standardCelRuntimeBuilder()
            .addLibraries(CelExtensions.strings(Function.REPLACE))
            .build();
    CelAbstractSyntaxTree ast = celCompiler.compile("'test'.substring(2) == 'st'").getAst();

    assertThrows(CelEvaluationException.class, () -> celRuntime.createProgram(ast).eval());
  }
}
