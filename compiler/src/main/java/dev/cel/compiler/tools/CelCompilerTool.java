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

package dev.cel.compiler.tools;

import dev.cel.expr.CheckedExpr;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.Files;
import com.google.protobuf.DescriptorProtos.FileDescriptorSet;
import com.google.protobuf.Descriptors.FileDescriptor;
import com.google.protobuf.ExtensionRegistry;
import dev.cel.common.CelAbstractSyntaxTree;
import dev.cel.common.CelDescriptorUtil;
import dev.cel.common.CelOptions;
import dev.cel.common.CelProtoAbstractSyntaxTree;
import dev.cel.common.CelValidationException;
import dev.cel.compiler.CelCompiler;
import dev.cel.compiler.CelCompilerBuilder;
import dev.cel.compiler.CelCompilerFactory;
import dev.cel.extensions.CelExtensions;
import dev.cel.extensions.CelOptionalLibrary;
import dev.cel.parser.CelStandardMacro;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.Callable;
import picocli.CommandLine;
import picocli.CommandLine.Option;

/**
 * CelCompilerTool is a binary that takes a CEL expression in string, compiles it into a
 * dev.cel.expr.CheckedExpr protobuf message, then writes the content to a .binary pb file.
 */
final class CelCompilerTool implements Callable<Integer> {

  @Option(
      names = {"--cel_expression"},
      description = "CEL expression")
  private String celExpression = "";

  @Option(
      names = {"--transitive_descriptor_set"},
      description = "Path to the transitive set of descriptors")
  private String transitiveDescriptorSetPath = "";

  @Option(
      names = {"--output"},
      description = "Output path for the compiled binarypb")
  private String output = "";

  private static final CelOptions CEL_OPTIONS = CelOptions.DEFAULT;

  private static FileDescriptorSet load(String descriptorSetPath) {
    try {
      byte[] descriptorBytes = Files.toByteArray(new File(descriptorSetPath));
      return FileDescriptorSet.parseFrom(descriptorBytes, ExtensionRegistry.getEmptyRegistry());
    } catch (IOException e) {
      throw new IllegalArgumentException(
          "Failed to load FileDescriptorSet from path: " + descriptorSetPath, e);
    }
  }

  private static CelAbstractSyntaxTree compile(
      String expression, String transitiveDescriptorSetPath) throws CelValidationException {
    CelCompilerBuilder compilerBuilder =
        CelCompilerFactory.standardCelCompilerBuilder()
            .setOptions(CEL_OPTIONS)
            .setStandardMacros(CelStandardMacro.STANDARD_MACROS)
            .addLibraries(
                CelExtensions.bindings(),
                CelExtensions.encoders(),
                CelExtensions.math(CEL_OPTIONS),
                CelExtensions.lists(),
                CelExtensions.protos(),
                CelExtensions.strings(),
                CelOptionalLibrary.INSTANCE);
    if (!transitiveDescriptorSetPath.isEmpty()) {
      ImmutableSet<FileDescriptor> transitiveFileDescriptors =
          CelDescriptorUtil.getFileDescriptorsFromFileDescriptorSet(
              load(transitiveDescriptorSetPath));
      compilerBuilder.addFileTypes(transitiveFileDescriptors);
    }

    CelCompiler celCompiler = compilerBuilder.build();

    return celCompiler.compile(expression).getAst();
  }

  private static void writeCheckedExpr(CelAbstractSyntaxTree ast, String filePath)
      throws IOException {
    CheckedExpr checkedExpr = CelProtoAbstractSyntaxTree.fromCelAst(ast).toCheckedExpr();
    Path path = Paths.get(filePath);
    try (FileOutputStream output = new FileOutputStream(path.toFile())) {
      checkedExpr.writeTo(output);
    }
  }

  @Override
  public Integer call() {
    try {
      CelAbstractSyntaxTree ast = compile(celExpression, transitiveDescriptorSetPath);
      writeCheckedExpr(ast, output);
    } catch (Exception e) {
      String errorMessage =
          String.format(
              "Expression [%s] failed to compile. Reason: %s", celExpression, e.getMessage());
      System.err.print(errorMessage);
      return -1;
    }

    return 0;
  }

  public static void main(String[] args) {
    CelCompilerTool compilerTool = new CelCompilerTool();
    CommandLine cmd = new CommandLine(compilerTool);
    cmd.setTrimQuotes(false);
    cmd.parseArgs(args);

    int exitCode = cmd.execute(args);
    System.exit(exitCode);
  }

  CelCompilerTool() {}
}
