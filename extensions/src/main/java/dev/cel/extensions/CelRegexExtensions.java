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

import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.errorprone.annotations.Immutable;
import com.google.re2j.Matcher;
import com.google.re2j.Pattern;
import com.google.re2j.PatternSyntaxException;
import dev.cel.checker.CelCheckerBuilder;
import dev.cel.common.CelFunctionDecl;
import dev.cel.common.CelOverloadDecl;
import dev.cel.common.types.ListType;
import dev.cel.common.types.OptionalType;
import dev.cel.common.types.SimpleType;
import dev.cel.compiler.CelCompilerLibrary;
import dev.cel.runtime.CelFunctionBinding;
import dev.cel.runtime.CelRuntimeBuilder;
import dev.cel.runtime.CelRuntimeLibrary;
import java.util.Optional;
import java.util.Set;

/** Internal implementation of CEL regex extensions. */
@Immutable
final class CelRegexExtensions
    implements CelCompilerLibrary, CelRuntimeLibrary, CelExtensionLibrary.FeatureSet {

  private static final String REGEX_REPLACE_FUNCTION = "regex.replace";
  private static final String REGEX_EXTRACT_FUNCTION = "regex.extract";
  private static final String REGEX_EXTRACT_ALL_FUNCTION = "regex.extractAll";

  enum Function {
    REPLACE(
        CelFunctionDecl.newFunctionDeclaration(
            REGEX_REPLACE_FUNCTION,
            CelOverloadDecl.newGlobalOverload(
                "regex_replaceAll_string_string_string",
                "Replaces all the matched values using the given replace string.",
                SimpleType.STRING,
                SimpleType.STRING,
                SimpleType.STRING,
                SimpleType.STRING),
            CelOverloadDecl.newGlobalOverload(
                "regex_replaceCount_string_string_string_int",
                "Replaces the given number of matched values using the given replace string.",
                SimpleType.STRING,
                SimpleType.STRING,
                SimpleType.STRING,
                SimpleType.STRING,
                SimpleType.INT)),
        ImmutableSet.of(
            CelFunctionBinding.from(
                "regex_replaceAll_string_string_string",
                ImmutableList.of(String.class, String.class, String.class),
                (args) -> {
                  String target = (String) args[0];
                  String pattern = (String) args[1];
                  String replaceStr = (String) args[2];
                  return CelRegexExtensions.replace(target, pattern, replaceStr);
                }),
            CelFunctionBinding.from(
                "regex_replaceCount_string_string_string_int",
                ImmutableList.of(String.class, String.class, String.class, Long.class),
                (args) -> {
                  String target = (String) args[0];
                  String pattern = (String) args[1];
                  String replaceStr = (String) args[2];
                  long count = (long) args[3];
                  return CelRegexExtensions.replaceN(target, pattern, replaceStr, count);
                }))),
    EXTRACT(
        CelFunctionDecl.newFunctionDeclaration(
            REGEX_EXTRACT_FUNCTION,
            CelOverloadDecl.newGlobalOverload(
                "regex_extract_string_string",
                "Returns the first substring that matches the regex.",
                OptionalType.create(SimpleType.STRING),
                SimpleType.STRING,
                SimpleType.STRING)),
        ImmutableSet.of(
            CelFunctionBinding.from(
                "regex_extract_string_string",
                String.class,
                String.class,
                CelRegexExtensions::extract))),
    EXTRACTALL(
        CelFunctionDecl.newFunctionDeclaration(
            REGEX_EXTRACT_ALL_FUNCTION,
            CelOverloadDecl.newGlobalOverload(
                "regex_extractAll_string_string",
                "Returns an array of all substrings that match the regex.",
                ListType.create(SimpleType.STRING),
                SimpleType.STRING,
                SimpleType.STRING)),
        ImmutableSet.of(
            CelFunctionBinding.from(
                "regex_extractAll_string_string",
                String.class,
                String.class,
                CelRegexExtensions::extractAll)));

    private final CelFunctionDecl functionDecl;
    private final ImmutableSet<CelFunctionBinding> functionBindings;

    String getFunction() {
      return functionDecl.name();
    }

    Function(CelFunctionDecl functionDecl, ImmutableSet<CelFunctionBinding> functionBindings) {
      this.functionDecl = functionDecl;
      this.functionBindings = functionBindings;
    }
  }

  private static final CelExtensionLibrary<CelRegexExtensions> LIBRARY =
      new CelExtensionLibrary<CelRegexExtensions>() {
        private final CelRegexExtensions version0 = new CelRegexExtensions();

        @Override
        public String name() {
          return "regex";
        }

        @Override
        public ImmutableSet<CelRegexExtensions> versions() {
          return ImmutableSet.of(version0);
        }
      };

  static CelExtensionLibrary<CelRegexExtensions> library() {
    return LIBRARY;
  }

  private final ImmutableSet<Function> functions;

  CelRegexExtensions() {
    this.functions = ImmutableSet.copyOf(Function.values());
  }

  CelRegexExtensions(Set<Function> functions) {
    this.functions = ImmutableSet.copyOf(functions);
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

  @Override
  public void setRuntimeOptions(CelRuntimeBuilder runtimeBuilder) {
    functions.forEach(function -> runtimeBuilder.addFunctionBindings(function.functionBindings));
  }

  private static Pattern compileRegexPattern(String regex) {
    try {
      return Pattern.compile(regex);
    } catch (PatternSyntaxException e) {
      throw new IllegalArgumentException("Failed to compile regex: " + regex, e);
    }
  }

  private static String replace(String target, String regex, String replaceStr) {
    return replaceN(target, regex, replaceStr, -1);
  }

  private static String replaceN(
      String target, String regex, String replaceStr, long replaceCount) {
    if (replaceCount == 0) {
      return target;
    }
    // For all negative replaceCount, do a replaceAll
    if (replaceCount < 0) {
      replaceCount = -1;
    }

    Pattern pattern = compileRegexPattern(regex);
    Matcher matcher = pattern.matcher(target);
    StringBuffer sb = new StringBuffer();
    int counter = 0;

    while (matcher.find()) {
      if (replaceCount != -1 && counter >= replaceCount) {
        break;
      }

      String processedReplacement = replaceStrValidator(matcher, replaceStr);
      matcher.appendReplacement(sb, Matcher.quoteReplacement(processedReplacement));
      counter++;
    }
    matcher.appendTail(sb);

    return sb.toString();
  }

  private static String replaceStrValidator(Matcher matcher, String replacement) {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < replacement.length(); i++) {
      char c = replacement.charAt(i);

      if (c != '\\') {
        sb.append(c);
        continue;
      }

      if (i + 1 >= replacement.length()) {
        throw new IllegalArgumentException("Invalid replacement string: \\ not allowed at end");
      }

      char nextChar = replacement.charAt(++i);

      if (Character.isDigit(nextChar)) {
        int groupNum = Character.digit(nextChar, 10);
        int groupCount = matcher.groupCount();

        if (groupNum > groupCount) {
          throw new IllegalArgumentException(
              "Replacement string references group "
                  + groupNum
                  + " but regex has only "
                  + groupCount
                  + " group(s)");
        }

        String groupValue = matcher.group(groupNum);
        if (groupValue != null) {
          sb.append(groupValue);
        }
      } else if (nextChar == '\\') {
        sb.append('\\');
      } else {
        throw new IllegalArgumentException(
            "Invalid replacement string: \\ must be followed by a digit");
      }
    }
    return sb.toString();
  }

  private static Optional<String> extract(String target, String regex) {
    Pattern pattern = compileRegexPattern(regex);
    Matcher matcher = pattern.matcher(target);

    if (!matcher.find()) {
      return Optional.empty();
    }

    int groupCount = matcher.groupCount();
    if (groupCount > 1) {
      throw new IllegalArgumentException(
          "Regular expression has more than one capturing group: " + regex);
    }

    String result = (groupCount == 1) ? matcher.group(1) : matcher.group(0);

    return Optional.ofNullable(result);
  }

  private static ImmutableList<String> extractAll(String target, String regex) {
    Pattern pattern = compileRegexPattern(regex);
    Matcher matcher = pattern.matcher(target);

    if (matcher.groupCount() > 1) {
      throw new IllegalArgumentException(
          "Regular expression has more than one capturing group: " + regex);
    }

    ImmutableList.Builder<String> builder = ImmutableList.builder();
    boolean hasOneGroup = matcher.groupCount() == 1;

    while (matcher.find()) {
      if (hasOneGroup) {
        String group = matcher.group(1);
        // Add the captured group's content only if it's not null
        if (group != null) {
          builder.add(group);
        }
      } else {
        // No capturing groups
        builder.add(matcher.group(0));
      }
    }

    return builder.build();
  }
}
