package dev.cel.extensions;

import dev.cel.checker.CelCheckerBuilder;
import dev.cel.common.CelFunctionDecl;
import dev.cel.common.CelOverloadDecl;
import dev.cel.common.types.ListType;
import dev.cel.common.types.SimpleType;
import dev.cel.compiler.CelCompilerLibrary;
import dev.cel.parser.CelParserBuilder;

/**
 * TODO
 */
final class CelBlockExtensions implements CelCompilerLibrary {
  private static final String CEL_BLOCK_FUNCTION = "cel.@block";
  private static final String CEL_BLOCK_INDEX_FUNCTION = "cel.@index";

  @Override
  public void setParserOptions(CelParserBuilder parserBuilder) {
    // parserBuilder.addMacros(
    //     CelMacro.newReceiverMacro("bind", 3, CelBindingsExtensions::expandBind));
  }


  @Override
  public void setCheckerOptions(CelCheckerBuilder checkerBuilder) {
    checkerBuilder.addFunctionDeclarations(
        CelFunctionDecl.newFunctionDeclaration(CEL_BLOCK_FUNCTION,
            CelOverloadDecl.newGlobalOverload("cel_block_list", SimpleType.DYN, ListType.create(SimpleType.DYN), SimpleType.DYN)
        ),
        CelFunctionDecl.newFunctionDeclaration(CEL_BLOCK_INDEX_FUNCTION,
            CelOverloadDecl.newGlobalOverload("cel_index_int", SimpleType.DYN, SimpleType.INT)
        )
    );
  }
}
