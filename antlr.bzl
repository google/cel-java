# Copyright 2023 Google LLC
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      https://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

"""Build rules to create Java code from an ANTLR4 grammar."""

def antlr4_java_lexer(name, src, package, visibility = None, imports = None, compatible_with = []):
    """Generates the java source corresponding to an antlr4 lexer definition.

    Args:
      name: The name of the build rule.
      src: The antlr4 g4 file containing the lexer rules.
      package: The Java package to place the output in.
      visibility: The standard Bazel visibility attribute.
      imports: A list of antlr4 source imports to use when building the lexer.
      compatible_with: The standard Bazel compatible_with attribute.
    """
    suffixes = ("%s.java", "%s.tokens")
    imports = imports or []

    import_srcs = []
    for imp in imports:
        import_srcs.append(imp)
        if not imp.endswith(".g4"):
            continue
        import_srcs.append("%s.tokens" % imp[:-3])
    file_prefix = src[:-3] if src.endswith(".g4") else src
    outs = _make_outs(file_prefix, suffixes)
    native.genrule(
        name = name,
        srcs = [src] + import_srcs,
        visibility = visibility,
        outs = outs,
        tags = _make_tags(package, outs),
        cmd = ("mkdir $$$$.tmp ; " + "cp $(SRCS) $$$$.tmp/ ; " + "cd $$$$.tmp ; " +
               ("../$(location //:antlr4_tool) " + src +
                " -package " + package + " -Werror ; ") + "cd .. ; " + "".join(
            [
                " cp $$$$.tmp/%s $(@D)/ ;" % filepath
                for filepath in _make_outs(file_prefix, suffixes)
            ],
        ) + "rm -rf $$$$.tmp"),
        heuristic_label_expansion = 0,
        tools = [
            "//:antlr4_tool",
        ],
        compatible_with = compatible_with,
    )

def antlr4_java_parser(
        name,
        src,
        package,
        visibility = None,
        imports = None,
        listener = True,
        visitor = False,
        compatible_with = []):
    """Generates the java source corresponding to an antlr4 parser definition.

    Args:
      name: The name of the build rule.
      src: The antlr4 g4 file containing the parser rules.
      package: The Java package to place the output in.
      visibility: The standard Blaze visibility attribute.
      imports: A list of antlr4 source imports to use when building the parser.
      listener: Whether or not to include listener generated files.
      visitor: Whether or not to include visitor generated files.
      compatible_with: The standard Blaze compatible_with attribute.
    """
    suffixes = ("%s.java", "%s.tokens")
    visitor_flag = " "
    if visitor:
        visitor_flag = " -visitor"
        suffixes += ("%sBaseVisitor.java", "%sVisitor.java")
    listener_flag = " "
    if listener:
        suffixes += ("%sBaseListener.java", "%sListener.java")
    else:
        listener_flag = " -no-listener"
    imports = imports or []
    import_srcs = []
    for imp in imports:
        import_srcs.append(imp)
        if not imp.endswith(".g4"):
            continue
        import_srcs.append("%s.tokens" % imp[:-3])
    file_prefix = src[:-3] if src.endswith(".g4") else src
    outs = _make_outs(file_prefix, suffixes)

    native.genrule(
        name = name,
        srcs = [src] + import_srcs,
        visibility = visibility,
        outs = outs,
        tags = _make_tags(package, outs),
        cmd = ("mkdir $$$$.tmp ; " + "cp $(SRCS) $$$$.tmp/ ; " + "cd $$$$.tmp ; " +
               ("../$(location //:antlr4_tool) " + src +
                visitor_flag + listener_flag + " -package " + package + " ; ") +
               "cd .. ; " + (
            "".join([
                " cp $$$$.tmp/%s $(@D)/ ;" % filepath
                for filepath in _make_outs(
                    file_prefix,
                    suffixes,
                )
            ])
        ) + "rm -rf $$$$.tmp"),
        heuristic_label_expansion = 0,
        tools = [
            "//:antlr4_tool",
        ],
        compatible_with = compatible_with,
    )

def antlr4_java_combined(
        name,
        src,
        package,
        visibility = None,
        imports = None,
        listener = True,
        visitor = False,
        compatible_with = []):
    """Generates the java source corresponding to an antlr4 grammar definition.

       This genrule assumes that 'src' starts with 'grammar' and not
       '(lexer|parser) grammar'

    Args:
      name: The name of the build rule.
      src: The antlr4 g4 file containing the  rules.
      package: The Java package to place the output in.
      visibility: The standard Blaze visibility attribute.
      imports: A list of antlr4 source imports to use when building the parser.
      listener: Whether or not to include listener generated files.
      visitor: Whether or not to include visitor generated files.
      compatible_with: The standard Blaze compatible_with attribute.
    """
    suffixes = ("%sLexer.java", "%sParser.java", "%s.tokens")
    visitor_flag = " "
    if visitor:
        visitor_flag = " -visitor"
        suffixes += ("%sBaseVisitor.java", "%sVisitor.java")
    listener_flag = " "
    if listener:
        suffixes += ("%sBaseListener.java", "%sListener.java")
    else:
        listener_flag = " -no-listener"
    imports = imports or []
    import_srcs = []
    for imp in imports:
        import_srcs.append(imp)
        if not imp.endswith(".g4"):
            continue
        import_srcs.append("%s.tokens" % imp[:-3])
    file_prefix = src[:-3] if src.endswith(".g4") else src
    outs = _make_outs(file_prefix, suffixes)

    native.genrule(
        name = name,
        srcs = [src] + import_srcs,
        visibility = visibility,
        outs = outs,
        tags = _make_tags(package, outs),
        cmd = ("mkdir $$$$.tmp ; " + "cp $(SRCS) $$$$.tmp/ ; " + "cd $$$$.tmp ; " +
               ("../$(location //:antlr4_tool) " + src +
                visitor_flag + listener_flag + " -package " + package + " ; ") +
               "cd .. ; " + (
            "".join([
                " cp $$$$.tmp/%s $(@D)/ ;" % filepath
                for filepath in _make_outs(
                    file_prefix,
                    suffixes,
                )
            ])
        ) + "rm -rf $$$$.tmp"),
        heuristic_label_expansion = 0,
        tools = [
            "//:antlr4_tool",
        ],
        compatible_with = compatible_with,
    )

def _make_outs(file_prefix, suffixes):
    return [file_suffix % file_prefix for file_suffix in suffixes]

def _make_tags(package, outs):
    tags = []
    for file in outs:
        if file.endswith(".java"):
            tags.append("generated_java_class=%s.%s" % (package, file[:-5]))
    return tags
