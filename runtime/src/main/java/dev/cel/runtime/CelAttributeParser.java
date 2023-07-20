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

package dev.cel.runtime;

import static com.google.common.collect.ImmutableList.toImmutableList;

import dev.cel.expr.Constant;
import dev.cel.expr.Expr;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.primitives.UnsignedLong;
import dev.cel.common.CelAbstractSyntaxTree;
import dev.cel.common.CelValidationException;
import dev.cel.common.CelValidationResult;
import dev.cel.parser.CelParser;
import dev.cel.parser.CelParserFactory;
import dev.cel.parser.Operator;
import java.util.ArrayDeque;

/**
 * Helper class for parsing human-readable attributes into either {@link CelAttribute} or {@link
 * CelAttributePattern} instances.
 */
public class CelAttributeParser {
  private static final CelParser PARSER = CelParserFactory.standardCelParserBuilder().build();
  private static final String WILDCARD_ESCAPE = "wildcard";
  private static final char ESCAPE = '_';

  private CelAttributeParser() {}

  private static String escape(String s) {
    // escape the escape character
    String escapeStr = String.valueOf(ESCAPE);
    s = s.replace(escapeStr, escapeStr + escapeStr);
    return s.replace("*", ESCAPE + WILDCARD_ESCAPE);
  }

  private static String unescape(String s) {
    StringBuilder b = new StringBuilder();
    boolean inEscape = false;
    for (int index = 0; index < s.length(); index++) {
      char c = s.charAt(index);

      if (!inEscape && c == ESCAPE) {
        inEscape = true;
        continue;
      }

      if (inEscape) {
        if (c == ESCAPE) {
          b.append(c);
          inEscape = false;
          continue;
        } else if (s.startsWith(WILDCARD_ESCAPE, index)) {
          b.append("*");
          index += WILDCARD_ESCAPE.length() - 1;
          inEscape = false;
          continue;
        }
        throw new IllegalArgumentException("invalid escape sequence: " + ESCAPE + c);
      }

      b.append(c);
    }
    return b.toString();
  }

  private static CelAttribute.Qualifier parseConst(Constant constExpr) {
    switch (constExpr.getConstantKindCase()) {
      case BOOL_VALUE:
        return CelAttribute.Qualifier.ofBool(constExpr.getBoolValue());
      case INT64_VALUE:
        return CelAttribute.Qualifier.ofInt(constExpr.getInt64Value());
      case UINT64_VALUE:
        return CelAttribute.Qualifier.ofUint(UnsignedLong.fromLongBits(constExpr.getUint64Value()));
      case STRING_VALUE:
        return CelAttribute.Qualifier.ofString(unescape(constExpr.getStringValue()));
      default:
        throw new IllegalArgumentException(
            "Unsupported const expr kind: " + constExpr.getConstantKindCase());
    }
  }

  /**
   * Parses a string formatted cel attribute pattern into a {@link CelAttribute}.
   *
   * <p>Cel Attributes are represented by the fully-qualified cel selection path that would access
   * the represented Attribute. As an extension, wild card qualifiers '.*' are supported to indicate
   * that all fields or elements of the parent are unknown.
   *
   * @param attribute the string representation of the attribute. for example <code>
   *     namespace.object.field['index']</code>.
   * @throws IllegalArgumentException if the provided string isn't a legal attribute representation.
   */
  public static CelAttributePattern parsePattern(String attribute) {
    String qAttribute = escape(attribute);
    CelValidationResult result = PARSER.parse(qAttribute);
    try {
      CelAbstractSyntaxTree ast = result.getAst();
      ArrayDeque<CelAttribute.Qualifier> qualifiers = new ArrayDeque<>();
      Expr node = ast.getProtoExpr();
      while (node != null) {
        switch (node.getExprKindCase()) {
          case IDENT_EXPR:
            qualifiers.addFirst(CelAttribute.Qualifier.ofString(node.getIdentExpr().getName()));
            node = null;
            break;
          case CALL_EXPR:
            Expr.Call callExpr = node.getCallExpr();
            if (!callExpr.getFunction().equals(Operator.INDEX.getFunction())
                || callExpr.getArgsCount() != 2
                || !callExpr.getArgs(1).hasConstExpr()) {
              throw new IllegalArgumentException(
                  String.format(
                      "Unsupported call expr: %s(%s)",
                      callExpr.getFunction(),
                      Joiner.on(", ")
                          .join(
                              callExpr.getArgsList().stream()
                                  .map(Expr::getExprKindCase)
                                  .collect(toImmutableList()))));
            }
            qualifiers.addFirst(parseConst(callExpr.getArgs(1).getConstExpr()));
            node = callExpr.getArgs(0);
            break;
          case SELECT_EXPR:
            String field = node.getSelectExpr().getField();
            node = node.getSelectExpr().getOperand();
            if (field.equals("_" + WILDCARD_ESCAPE)) {
              qualifiers.addFirst(CelAttribute.Qualifier.ofWildCard());
              break;
            }
            qualifiers.addFirst(CelAttribute.Qualifier.ofString(unescape(field)));
            break;
          default:
            throw new IllegalArgumentException(
                "Unsupported expr kind in attribute: " + node.getExprKindCase());
        }
      }
      return CelAttributePattern.create(ImmutableList.copyOf(qualifiers));
    } catch (CelValidationException e) {
      throw new IllegalArgumentException("Illegal CEL attribute", e);
    }
  }

  /**
   * Parses a string formatted cel attribute into a {@link CelAttribute}.
   *
   * <p>Cel Attributes are represented by the fully-qualified cel selection path that would access
   * the represented Attribute. As an extension, wild card qualifiers '.*' are supported to indicate
   * that all fields or elements of the parent are unknown.
   *
   * @param attribute the string representation of the attribute. for example <code>
   *     namespace.object.field['index']</code>.
   * @throws IllegalArgumentException if the provided string isn't a legal attribute representation.
   */
  public static CelAttribute parse(String attribute) {
    // Patterns are a super set of attributes. Create throws an
    // IllegalArgumentException if a wildcard was used.
    return CelAttribute.create(parsePattern(attribute).qualifiers());
  }
}
