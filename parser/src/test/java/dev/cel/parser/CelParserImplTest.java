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

package dev.cel.parser;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;
import static org.junit.Assert.assertThrows;

import com.google.testing.junit.testparameterinjector.TestParameter;
import com.google.testing.junit.testparameterinjector.TestParameterInjector;
import com.google.testing.junit.testparameterinjector.TestParameters;
import dev.cel.common.CelIssue;
import dev.cel.common.CelOptions;
import dev.cel.common.CelSource;
import dev.cel.common.CelValidationException;
import dev.cel.common.CelValidationResult;
import dev.cel.common.ast.CelExpr;
import java.util.Optional;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(TestParameterInjector.class)
public final class CelParserImplTest {

  // This file exercises non-parsing related methods in CelParser. See CelParserParameterizedTest
  // for parsing related tests.

  @Test
  public void build_withMacros_containsAllMacros() {
    CelParserImpl parser = CelParserImpl.newBuilder().addMacros(CelMacro.STANDARD_MACROS).build();
    assertThat(parser.findMacro("has:1:false")).hasValue(CelMacro.HAS);
    assertThat(parser.findMacro("all:2:true")).hasValue(CelMacro.ALL);
    assertThat(parser.findMacro("exists:2:true")).hasValue(CelMacro.EXISTS);
    assertThat(parser.findMacro("exists_one:2:true")).hasValue(CelMacro.EXISTS_ONE);
    assertThat(parser.findMacro("map:2:true")).hasValue(CelMacro.MAP);
    assertThat(parser.findMacro("map:3:true")).hasValue(CelMacro.MAP_FILTER);
    assertThat(parser.findMacro("filter:2:true")).hasValue(CelMacro.FILTER);
  }

  @Test
  public void build_withStandardMacros_containsAllMacros() {
    CelParserImpl parser =
        CelParserImpl.newBuilder().setStandardMacros(CelStandardMacro.STANDARD_MACROS).build();
    assertThat(parser.findMacro("has:1:false")).hasValue(CelMacro.HAS);
    assertThat(parser.findMacro("all:2:true")).hasValue(CelMacro.ALL);
    assertThat(parser.findMacro("exists:2:true")).hasValue(CelMacro.EXISTS);
    assertThat(parser.findMacro("exists_one:2:true")).hasValue(CelMacro.EXISTS_ONE);
    assertThat(parser.findMacro("map:2:true")).hasValue(CelMacro.MAP);
    assertThat(parser.findMacro("map:3:true")).hasValue(CelMacro.MAP_FILTER);
    assertThat(parser.findMacro("filter:2:true")).hasValue(CelMacro.FILTER);
  }

  @Test
  public void build_withStandardMacrosAndCustomMacros_containsAllMacros() {
    CelMacro customMacro =
        CelMacro.newReceiverMacro(
            "customMacro", 1, (a, b, c) -> Optional.of(CelExpr.newBuilder().build()));
    CelParserImpl parser =
        CelParserImpl.newBuilder()
            .setStandardMacros(CelStandardMacro.STANDARD_MACROS)
            .addMacros(customMacro)
            .build();

    assertThat(parser.findMacro("has:1:false")).hasValue(CelMacro.HAS);
    assertThat(parser.findMacro("all:2:true")).hasValue(CelMacro.ALL);
    assertThat(parser.findMacro("exists:2:true")).hasValue(CelMacro.EXISTS);
    assertThat(parser.findMacro("exists_one:2:true")).hasValue(CelMacro.EXISTS_ONE);
    assertThat(parser.findMacro("map:2:true")).hasValue(CelMacro.MAP);
    assertThat(parser.findMacro("map:3:true")).hasValue(CelMacro.MAP_FILTER);
    assertThat(parser.findMacro("filter:2:true")).hasValue(CelMacro.FILTER);
    assertThat(parser.findMacro("customMacro:1:true")).hasValue(customMacro);
  }

  @Test
  public void build_withMacro_containsMacro() {
    CelParserImpl parser = CelParserImpl.newBuilder().addMacros(CelMacro.HAS).build();
    assertThat(parser.findMacro("has:1:false")).hasValue(CelMacro.HAS);
  }

  @Test
  public void build_withStandardMacro_containsMacro() {
    CelParserImpl parser =
        CelParserImpl.newBuilder().setStandardMacros(CelStandardMacro.HAS).build();
    assertThat(parser.findMacro("has:1:false")).hasValue(CelMacro.HAS);
  }

  @Test
  public void build_withStandardMacro_secondCallReplaces() {
    CelParserImpl parser =
        CelParserImpl.newBuilder()
            .setStandardMacros(CelStandardMacro.HAS, CelStandardMacro.ALL)
            .setStandardMacros(CelStandardMacro.HAS)
            .build();

    assertThat(parser.findMacro("has:1:false")).hasValue(CelMacro.HAS);
    assertThat(parser.findMacro("all:2:true")).isEmpty();
  }

  @Test
  public void build_standardMacroKeyConflictsWithCustomMacro_throws() {
    CelMacro customMacro =
        CelMacro.newGlobalMacro("has", 1, (a, b, c) -> Optional.of(CelExpr.newBuilder().build()));

    assertThrows(
        IllegalArgumentException.class,
        () ->
            CelParserImpl.newBuilder()
                .setStandardMacros(CelStandardMacro.HAS)
                .addMacros(customMacro)
                .build());
  }

  @Test
  public void build_containsNoMacros() {
    CelParserImpl parser = CelParserImpl.newBuilder().build();
    assertThat(parser.findMacro("has:1:false")).isEmpty();
  }

  @Test
  public void setParserLibrary_success() {
    CelParserImpl parser =
        CelParserImpl.newBuilder()
            .addLibraries(
                new CelParserLibrary() {
                  @Override
                  public void setParserOptions(CelParserBuilder parserBuilder) {
                    parserBuilder.addMacros(
                        CelMacro.newReceiverVarArgMacro(
                            "dummyMacro", (a, b, c) -> Optional.of(CelExpr.newBuilder().build())));
                  }
                })
            .build();

    assertThat(parser.findMacro("dummyMacro:*:true")).isPresent();
  }

  @Test
  public void parse_throwsWhenExpressionSizeCodePointLimitExceeded() {
    CelParserImpl parser =
        CelParserImpl.newBuilder()
            .setOptions(CelOptions.newBuilder().maxExpressionCodePointSize(2).build())
            .build();
    CelValidationResult parseResult = parser.parse(CelSource.newBuilder("foo").build());
    CelValidationException exception =
        assertThrows(CelValidationException.class, parseResult::getAst);
    assertThat(exception.getErrors()).hasSize(1);
    assertThat(exception.getErrors().get(0).getMessage())
        .isEqualTo("expression code point size exceeds limit: size: 3, limit 2");
  }

  private enum MaxParseRecursionDepthTestCase {
    LARGE_CALC(
        "1 + 2 + 3 + 4 + 5 + 6 + 7 + 8 + 9 + 10 + 11 + 12 + 13 + 14 + 15 + 16 + 17 + 18 + 19 + 20 +"
            + " 21 + 22 + 23 + 24 + 25 + 26 + 27 + 28 + 29 + 30 + 31 + 32 + 33 + 34"),
    NESTED_PARENS("((((((((((((((((((((((((((((((((7))))))))))))))))))))))))))))))))"),
    NESTED_PARENS_WITH_CALC(
        "((((((((((((((((((((((((((((((((7)))))))))))))))))))))))))))))))) +"
            + "(((((((((((((((((((((((((((((((7)))))))))))))))))))))))))))))))"),
    FIELD_SELECTIONS("a.b.c.d.e.f.g.h.i.j.k.l.m.n.o.p.q.r.s.t.u.v.w.x.y.z.A.B.C.D.E.F.G.H"),
    INDEX_OPERATIONS(
        "a[1][2][3][4][5][6][7][8][9][10][11][12][13][14][15][16][17][18][19][20][21][22][23][24][25][26][27][28][29][30][31][32][33]"),
    RELATION_OPERATORS(
        "a < 1 < 2 < 3 < 4 < 5 < 6 < 7 < 8 < 9 < 10 < 11 < 12 < 13 < 14 < 15 < 16 < 17 < 18 < 19 <"
            + " 20 < 21 < 22 < 23 < 24 < 25 < 26 < 27 < 28 < 29 < 30 < 31 < 32 < 33"),
    // More than 32 index / relation operators. Note, the recursion count is the
    // maximum recursion level on the left or right side index expression (20) plus
    // the number of relation operators (13)
    INDEX_RELATION_OPERATORS(
        "a[1][2][3][4][5][6][7][8][9][10][11][12][13][14][15][16][17][18][19][20] !="
            + " a[1][2][3][4][5][6][7][8][9][10][11][12][13][14][15][16][17][18][19][20] !="
            + " a[1][2][3][4][5][6][7][8][9][10][11][12][13][14][15][16][17][18][19][20] !="
            + " a[1][2][3][4][5][6][7][8][9][10][11][12][13][14][15][16][17][18][19][20] !="
            + " a[1][2][3][4][5][6][7][8][9][10][11][12][13][14][15][16][17][18][19][20] !="
            + " a[1][2][3][4][5][6][7][8][9][10][11][12][13][14][15][16][17][18][19][20] !="
            + " a[1][2][3][4][5][6][7][8][9][10][11][12][13][14][15][16][17][18][19][20] !="
            + " a[1][2][3][4][5][6][7][8][9][10][11][12][13][14][15][16][17][18][19][20] !="
            + " a[1][2][3][4][5][6][7][8][9][10][11][12][13][14][15][16][17][18][19][20] !="
            + " a[1][2][3][4][5][6][7][8][9][10][11][12][13][14][15][16][17][18][19][20] !="
            + " a[1][2][3][4][5][6][7][8][9][10][11][12][13][14][15][16][17][18][19][20] !="
            + " a[1][2][3][4][5][6][7][8][9][10][11][12][13][14][15][16][17][18][19][20] !="
            + " a[1][2][3][4][5][6][7][8][9][10][11][12][13][14][15][16][17][18][19][20] !="
            + " a[1][2][3][4][5][6][7][8][9][10][11][12][13][14][15][16][17][18][19][20]");

    static final int MAX_RECURSION_LIMIT = 32;
    final String source;

    MaxParseRecursionDepthTestCase(String source) {
      this.source = source;
    }
  }

  @Test
  public void parse_largeExprHitsMaxRecursionLimit_throws(
      @TestParameter MaxParseRecursionDepthTestCase testCase) {
    int maxParseRecursionLimit = MaxParseRecursionDepthTestCase.MAX_RECURSION_LIMIT;
    CelParserImpl parser =
        CelParserImpl.newBuilder()
            .setOptions(
                CelOptions.newBuilder().maxParseRecursionDepth(maxParseRecursionLimit).build())
            .build();

    CelValidationResult parseResult = parser.parse(CelSource.newBuilder(testCase.source).build());

    CelValidationException exception =
        assertThrows(CelValidationException.class, parseResult::getAst);
    assertThat(exception)
        .hasMessageThat()
        .contains("Expression recursion limit exceeded. limit: " + maxParseRecursionLimit);
    assertThat(exception.getErrors()).hasSize(1);
    CelIssue issue = exception.getErrors().get(0);
    assertThat(issue.getMessage())
        .contains("Expression recursion limit exceeded. limit: " + maxParseRecursionLimit);
    assertThat(issue.getSourceLocation().getLine()).isEqualTo(1);
    assertThat(issue.getSourceLocation().getColumn()).isEqualTo(0);
  }

  @Test
  public void parse_exprUnderMaxRecursionLimit_doesNotThrow(
      @TestParameter MaxParseRecursionDepthTestCase testCase) throws CelValidationException {
    int maxParseRecursionLimit = MaxParseRecursionDepthTestCase.MAX_RECURSION_LIMIT + 1;

    CelParserImpl parser =
        CelParserImpl.newBuilder()
            .setOptions(
                CelOptions.newBuilder().maxParseRecursionDepth(maxParseRecursionLimit).build())
            .build();
    CelValidationResult parseResult = parser.parse(CelSource.newBuilder(testCase.source).build());
    assertThat(parseResult.hasError()).isFalse();
    assertThat(parseResult.getAst()).isNotNull();
  }

  @Test
  @TestParameters("{expression: 'A.map(a?b, c)'}")
  @TestParameters("{expression: 'A.all(a?b, c)'}")
  @TestParameters("{expression: 'A.exists(a?b, c)'}")
  @TestParameters("{expression: 'A.exists_one(a?b, c)'}")
  @TestParameters("{expression: 'A.filter(a?b, c)'}")
  public void parse_macroArgumentContainsSyntaxError_throws(String expression) {
    CelParserImpl parser = CelParserImpl.newBuilder().addMacros(CelMacro.STANDARD_MACROS).build();

    CelValidationResult parseResult = parser.parse(expression);

    assertThat(parseResult.hasError()).isTrue();
    assertThat(parseResult.getErrorString()).containsMatch("ERROR: <input>.*mismatched input ','");
    assertThrows(CelValidationException.class, parseResult::getAst);
  }
}
