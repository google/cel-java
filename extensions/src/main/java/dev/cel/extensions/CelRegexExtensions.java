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
import dev.cel.common.types.MapType;
import dev.cel.common.types.SimpleType;
import dev.cel.compiler.CelCompilerLibrary;
import dev.cel.runtime.CelFunctionBinding;
import dev.cel.runtime.CelRuntimeBuilder;
import dev.cel.runtime.CelRuntimeLibrary;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
                "regex_replace_all",
                "Replaces all the matched values using the given replace string.",
                SimpleType.STRING,
                SimpleType.STRING,
                SimpleType.STRING,
                SimpleType.STRING),
            CelOverloadDecl.newGlobalOverload(
                "regex_replace_count",
                "Replaces the given number of matched values using the given replace string.",
                SimpleType.STRING,
                SimpleType.STRING,
                SimpleType.STRING,
                SimpleType.STRING,
                SimpleType.INT)),
        ImmutableSet.of(
            CelFunctionBinding.from(
                "regex_replace_count",
                ImmutableList.of(String.class, String.class, String.class, Integer.class),
                (args) -> {
                  String target = (String) args[0];
                  String pattern = (String) args[1];
                  String replaceStr = (String) args[2];
                  int count = (int) args[3];
                  return CelRegexExtensions.replace(target, pattern, replaceStr, count);
                }),
            CelFunctionBinding.from(
                "regex_replace_all",
                ImmutableList.of(String.class, String.class, String.class),
                (args) -> {
                  String target = (String) args[0];
                  String pattern = (String) args[1];
                  String replaceStr = (String) args[2];
                  return CelRegexExtensions.replace(target, pattern, replaceStr);
                }))),
    CAPTURE(
        CelFunctionDecl.newFunctionDeclaration(
            REGEX_CAPTURE_FUNCTION,
            CelOverloadDecl.newGlobalOverload(
                "regex_capture",
                "Captures the first unnamed/named group value.",
                SimpleType.STRING,
                SimpleType.STRING,
                SimpleType.STRING)),
        ImmutableSet.of(
            CelFunctionBinding.from(
                "regex_capture",
                String.class,
                String.class,
                CelRegexExtensions::captureFirstGroup))),
    CAPTUREALL(
        CelFunctionDecl.newFunctionDeclaration(
            REGEX_CAPTUREALL_FUNCTION,
            CelOverloadDecl.newGlobalOverload(
                "regex_captureAll",
                "Returns a list of all captured groups.",
                ListType.create(SimpleType.STRING),
                SimpleType.STRING,
                SimpleType.STRING)),
        ImmutableSet.of(
            CelFunctionBinding.from(
                "regex_captureAll",
                String.class,
                String.class,
                CelRegexExtensions::captureAllGroups))),
    CAPTUREALLNAMED(
        CelFunctionDecl.newFunctionDeclaration(
            REGEX_CAPTUREALLNAMED_FUNCTION,
            CelOverloadDecl.newGlobalOverload(
                "regex_captureAllNamed",
                "Returns a map of all named captured groups as <named_group_name, captured_string>."
                    + " Ignores the unnamed capture groups.",
                MapType.create(SimpleType.STRING, SimpleType.STRING),
                SimpleType.STRING,
                SimpleType.STRING)),
        ImmutableSet.of(
            CelFunctionBinding.from(
                "regex_captureAllNamed",
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

  private static String replace(String target, String regex, String replaceStr) {
    Pattern pattern;
    try {
      pattern = Pattern.compile(regex);
    } catch (PatternSyntaxException e) {
      throw new IllegalArgumentException("Given Regex is Invalid", e);
    }
    Matcher matcher = pattern.matcher(target);
    return matcher.replaceAll(replaceStr);
  }

  private static String replace(String target, String regex, String replaceStr, int count) {
    Pattern pattern;
    try {
      pattern = Pattern.compile(regex);
    } catch (PatternSyntaxException e) {
      throw new IllegalArgumentException("Given Regex is Invalid", e);
    }

    if (count == 0) {
      return target;
    }

    Matcher matcher = pattern.matcher(target);
    StringBuffer sb = new StringBuffer();
    int counter = 0;

    while (matcher.find()) {
      if (count != -1 && counter >= count) {
        break;
      }
      matcher.appendReplacement(sb, replaceStr);
      count++;
    }
    matcher.appendTail(sb);

    return sb.toString();
  }

  private static String captureFirstGroup(String target, String regex) {
    Pattern pattern;
    try {
      pattern = Pattern.compile(regex);
    } catch (PatternSyntaxException e) {
      throw new IllegalArgumentException("Given Regex is Invalid", e);
    }
    Matcher matcher = pattern.matcher(target);
    if (!matcher.find() || matcher.groupCount() == 0) {
      return "";
    }
    return matcher.group(1);
  }

  private static List<String> captureAllGroups(String target, String regex) {
    Pattern pattern;
    try {
      // Compile the regular expression.
      pattern = Pattern.compile(regex);
    } catch (PatternSyntaxException e) {
      throw new IllegalArgumentException("Given Regex is Invalid", e);
    }

    Matcher matcher = pattern.matcher(target);
    List<String> result = new ArrayList<>();

    // Find each non-overlapping match of the entire pattern.
    while (matcher.find()) {
      // Iterate through all capture groups for the current match.
      // Group indices are 1-based. Group 0 is the entire match.
      for (int i = 1; i <= matcher.groupCount(); i++) {
        result.add(matcher.group(i));
      }
    }

    return result;
  }

  private static Map<String, String> captureAllNamedGroups(String target, String regex) {
    Map<String, String> capturedGroups = new HashMap<>();
    Pattern pattern;
    try {
      pattern = Pattern.compile(regex);
    } catch (PatternSyntaxException e) {
      throw new IllegalArgumentException("Given Regex is Invalid", e);
    }
    Matcher matcher = pattern.matcher(target);
    if (matcher.matches()) {

      for (Map.Entry<String, Integer> entry : pattern.namedGroups().entrySet()) {
        String groupName = entry.getKey();
        int groupIndex = entry.getValue();
        capturedGroups.put(groupName, matcher.group(groupIndex));
      }
    }
    return capturedGroups;
  }
}
