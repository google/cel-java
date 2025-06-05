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
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.errorprone.annotations.Immutable;
import com.google.re2j.Matcher;
import com.google.re2j.Pattern;
import com.google.re2j.PatternSyntaxException;
import dev.cel.checker.CelCheckerBuilder;
import dev.cel.common.CelFunctionDecl;
import dev.cel.common.CelOverloadDecl;
import dev.cel.common.types.ListType;
import dev.cel.common.types.MapType;
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
  private static final String REGEX_CAPTURE_FUNCTION = "regex.capture";
  private static final String REGEX_CAPTUREALL_FUNCTION = "regex.captureAll";
  private static final String REGEX_CAPTUREALLNAMED_FUNCTION = "regex.captureAllNamed";

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
    CAPTURE(
        CelFunctionDecl.newFunctionDeclaration(
            REGEX_CAPTURE_FUNCTION,
            CelOverloadDecl.newGlobalOverload(
                "regex_capture_string_string",
                "Returns the first substring that matches the regex.",
                OptionalType.create(SimpleType.STRING),
                SimpleType.STRING,
                SimpleType.STRING)),
        ImmutableSet.of(
            CelFunctionBinding.from(
                "regex_capture_string_string",
                String.class,
                String.class,
                CelRegexExtensions::captureFirstMatch))),
    CAPTUREALL(
        CelFunctionDecl.newFunctionDeclaration(
            REGEX_CAPTUREALL_FUNCTION,
            CelOverloadDecl.newGlobalOverload(
                "regex_captureAll_string_string",
                "Returns an arrat of all substrings that match the regex.",
                ListType.create(SimpleType.STRING),
                SimpleType.STRING,
                SimpleType.STRING)),
        ImmutableSet.of(
            CelFunctionBinding.from(
                "regex_captureAll_string_string",
                String.class,
                String.class,
                CelRegexExtensions::captureAllMatches))),
    CAPTUREALLNAMED(
        CelFunctionDecl.newFunctionDeclaration(
            REGEX_CAPTUREALLNAMED_FUNCTION,
            CelOverloadDecl.newGlobalOverload(
                "regex_captureAllNamed_string_string",
                "Returns a map of all named captured groups as <named_group_name, captured_string>."
                    + " Ignores the unnamed capture groups.",
                MapType.create(SimpleType.STRING, SimpleType.STRING),
                SimpleType.STRING,
                SimpleType.STRING)),
        ImmutableSet.of(
            CelFunctionBinding.from(
                "regex_captureAllNamed_string_string",
                String.class,
                String.class,
                CelRegexExtensions::captureAllNamedGroups)));

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

  private static Optional<String> captureFirstMatch(String target, String regex) {
    Pattern pattern = compileRegexPattern(regex);
    Matcher matcher = pattern.matcher(target);

    if (matcher.find()) {
      // If there are capture groups, return the first one.
      if (matcher.groupCount() > 0) {
        return Optional.ofNullable(matcher.group(1));
      } else {
        // If there are no capture groups, return the entire match.
        return Optional.of(matcher.group(0));
      }
    }

    return Optional.empty();
  }

  private static ImmutableList<String> captureAllMatches(String target, String regex) {
    Pattern pattern = compileRegexPattern(regex);

    Matcher matcher = pattern.matcher(target);
    ImmutableList.Builder<String> builder = ImmutableList.builder();

    while (matcher.find()) {
      // If there are capture groups, return all of them. Otherwise, return the entire match.
      if (matcher.groupCount() > 0) {
        // Add all the capture groups to the result list.
        for (int i = 1; i <= matcher.groupCount(); i++) {
          String group = matcher.group(i);
          if (group != null) {
            builder.add(group);
          }
        }
      } else {
        builder.add(matcher.group(0));
      }
    }

    return builder.build();
  }

  private static ImmutableMap<String, String> captureAllNamedGroups(String target, String regex) {
    ImmutableMap.Builder<String, String> builder = ImmutableMap.builder();
    Pattern pattern = compileRegexPattern(regex);

    Set<String> groupNames = pattern.namedGroups().keySet();
    if (groupNames.isEmpty()) {
      return builder.buildOrThrow();
    }

    Matcher matcher = pattern.matcher(target);

    while (matcher.find()) {

      for (String groupName : groupNames) {
        String capturedValue = matcher.group(groupName);
        if (capturedValue != null) {
          builder.put(groupName, capturedValue);
        }
      }
    }
    return builder.buildOrThrow();
  }
}
