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
final class CelRegexExtensions implements CelCompilerLibrary, CelRuntimeLibrary {

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
                  return CelRegexExtensions.replace(target, pattern, replaceStr, count);
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

  private final ImmutableSet<Function> functions;

  CelRegexExtensions() {
    this.functions = ImmutableSet.copyOf(Function.values());
  }

  CelRegexExtensions(Set<Function> functions) {
    this.functions = ImmutableSet.copyOf(functions);
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
    Pattern pattern = compileRegexPattern(regex);
    Matcher matcher = pattern.matcher(target);
    return matcher.replaceAll(replaceStr);
  }

  private static String replace(String target, String regex, String replaceStr, long replaceCount) {
    Pattern pattern = compileRegexPattern(regex);

    if (replaceCount == 0) {
      return target;
    }

    Matcher matcher = pattern.matcher(target);
    StringBuffer sb = new StringBuffer();
    int counter = 0;

    while (matcher.find()) {
      if (replaceCount != -1 && counter >= replaceCount) {
        break;
      }
      matcher.appendReplacement(sb, replaceStr);
      counter++;
    }
    matcher.appendTail(sb);

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
        // Add the captured group's content only if it's not null (e.g. optional group didn't match)
        if (group != null) {
          builder.add(group);
        }
      } else { // No capturing groups (matcher.groupCount() == 0)
        builder.add(matcher.group(0));
      }
    }

    return builder.build();
  }
}
