package dev.cel.extensions;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
import dev.cel.checker.CelCheckerBuilder;
import dev.cel.common.CelFunctionDecl;
import dev.cel.common.CelOverloadDecl;
import dev.cel.common.types.ListType;
import dev.cel.common.types.TypeParamType;
import dev.cel.compiler.CelCompilerLibrary;
import dev.cel.runtime.CelRuntime;
import dev.cel.runtime.CelRuntime.CelFunctionBinding;
import dev.cel.runtime.CelRuntimeBuilder;
import dev.cel.runtime.CelRuntimeLibrary;
import java.util.List;
import java.util.Set;
/**
 * TODO
 */
public class CelListsExtensions implements CelCompilerLibrary, CelRuntimeLibrary {

  private static final TypeParamType LIST_PARAM_TYPE = TypeParamType.create("T");

  public enum Function {
    FLATTEN(
        CelFunctionDecl.newFunctionDeclaration(
            "flatten",
            CelOverloadDecl.newMemberOverload(
                "list_flatten",
                "TODO",
                ListType.create(LIST_PARAM_TYPE),
                ListType.create(ListType.create(LIST_PARAM_TYPE)))),
        CelRuntime.CelFunctionBinding.from(
            "list_flatten", List.class, list -> flatten(list, 1)))
    ;

    private final CelFunctionDecl functionDecl;
    private final ImmutableSet<CelFunctionBinding> functionBindings;

    String getFunction() {
      return functionDecl.name();
    }

    Function(CelFunctionDecl functionDecl, CelRuntime.CelFunctionBinding... functionBindings) {
      this.functionDecl = functionDecl;
      this.functionBindings = ImmutableSet.copyOf(functionBindings);
    }
  }

  private final ImmutableSet<Function> functions;

  CelListsExtensions() {
    this.functions = ImmutableSet.copyOf(Function.values());
  }

  CelListsExtensions(Set<Function> functions) {
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

  private static List<Object> flatten(List<Object> list, int level) {
    Preconditions.checkArgument(level == 1, "recursive flatten is not supported yet.");
    return null;
  }
}
