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

package dev.cel.bundle;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableSet.toImmutableSet;

import dev.cel.expr.Decl;
import dev.cel.expr.Decl.FunctionDecl;
import dev.cel.expr.Decl.FunctionDecl.Overload;
import dev.cel.expr.Type;
import com.google.auto.value.AutoValue;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ListMultimap;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import dev.cel.bundle.CelEnvironment.ExtensionConfig;
import dev.cel.bundle.CelEnvironment.LibrarySubset;
import dev.cel.bundle.CelEnvironment.LibrarySubset.FunctionSelector;
import dev.cel.bundle.CelEnvironment.OverloadDecl;
import dev.cel.checker.CelStandardDeclarations.StandardFunction;
import dev.cel.checker.CelStandardDeclarations.StandardIdentifier;
import dev.cel.common.CelFunctionDecl;
import dev.cel.common.CelOptions;
import dev.cel.common.CelOverloadDecl;
import dev.cel.common.internal.EnvVisitable;
import dev.cel.common.types.CelProtoTypes;
import dev.cel.common.types.CelType;
import dev.cel.common.types.CelTypes;
import dev.cel.common.types.ListType;
import dev.cel.common.types.MapType;
import dev.cel.common.types.SimpleType;
import dev.cel.common.types.TypeParamType;
import dev.cel.extensions.CelExtensionLibrary;
import dev.cel.extensions.CelExtensions;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

/**
 * {@code CelEnvironmentExporter} can be used to export the configuration of a {@link Cel} instance
 * as a {@link CelEnvironment} object.
 *
 * <p>This exporter captures details such as:
 *
 * <ul>
 *   <li>Standard library subset: Functions and their overloads that are either included or
 *       excluded.
 *   <li>Extension libraries: Names and versions of the extension libraries in use.
 *   <li>Custom declarations: Functions and variables not part of standard or extension libraries.
 * </ul>
 *
 * <p>The exporter provides options to control the behavior of the export process, such as the
 * maximum number of excluded standard functions and overloads before switching to an inclusion
 * strategy.
 */
@AutoValue
public abstract class CelEnvironmentExporter {
  /**
   * Max number of overloads marked as "excluded" before the exporter switches to enumerating all
   * included ones.
   */
  abstract int getMaxExcludedStandardFunctions();

  abstract int getMaxExcludedStandardFunctionOverloads();

  abstract ImmutableSet<CelExtensionLibrary> getExtensionLibraries();

  /** Builder for {@link CelEnvironmentExporter}. */
  @AutoValue.Builder
  public abstract static class Builder {
    public abstract Builder setMaxExcludedStandardFunctions(int count);

    public abstract Builder setMaxExcludedStandardFunctionOverloads(int count);

    abstract ImmutableSet.Builder<CelExtensionLibrary> extensionLibrariesBuilder();

    @CanIgnoreReturnValue
    public Builder addStandardExtensions() {
      addExtensionLibraries(
          CelExtensions.math(CelOptions.current().build(), 0),
          CelExtensions.math(CelOptions.current().build(), 1),
          CelExtensions.math(CelOptions.current().build(), 2));
      return this;
    }

    @CanIgnoreReturnValue
    public Builder addExtensionLibraries(CelExtensionLibrary... libraries) {
      extensionLibrariesBuilder().add(libraries);
      return this;
    }

    abstract CelEnvironmentExporter autoBuild();

    public CelEnvironmentExporter build() {
      return autoBuild();
    }
  }

  /** Creates a new builder to construct a {@link CelEnvironmentExporter} instance. */
  public static CelEnvironmentExporter.Builder newBuilder() {
    return new AutoValue_CelEnvironmentExporter.Builder()
        .setMaxExcludedStandardFunctions(5)
        .setMaxExcludedStandardFunctionOverloads(15);
  }

  /**
   * Exports a {@link CelEnvironment} that describes the configuration of the given {@link Cel}
   * instance.
   *
   * <p>This method uses a default {@link CelEnvironmentExporter} with standard extensions to
   * perform the export. For more control over the export process, use {@link #export(Cel)} with a
   * custom {@link CelEnvironmentExporter}.
   */
  public static CelEnvironment exportCelEnvironment(Cel cel) {
    return CelEnvironmentExporter.newBuilder().addStandardExtensions().build().export(cel);
  }

  /**
   * Exports a {@link CelEnvironment} that describes the configuration of the given {@link Cel}
   * instance.
   *
   * <p>The exported environment includes:
   *
   * <ul>
   *   <li>Standard library subset: functions and their overloads that are either included or
   *       excluded from the standard library.
   *   <li>Extension libraries: names and versions of the extension libraries that are used.
   *   <li>Custom declarations: functions and variables that are not part of the standard library or
   *       any of the extension libraries.
   * </ul>
   */
  public CelEnvironment export(Cel cel) {
    ImmutableMap<String, InventoryItem> inventoryItems = collectInventory(cel);

    CelEnvironment.Builder envBuilder = CelEnvironment.newBuilder();

    addExtensionConfigs(envBuilder, inventoryItems);
    addStandardLibrarySubset(envBuilder, inventoryItems);
    addCustomDecls(envBuilder, inventoryItems);

    if (false) {
      System.out.println("[DEBUG] Inventory");
      for (String signature :
          inventoryItems.keySet().stream().sorted().collect(toImmutableList())) {
        System.out.println("  " + signature + " " + inventoryItems.get(signature));
      }
    }
    return envBuilder.build();
  }

  /**
   * Collects all function overloads, variable declarations and macros from the given {@link Cel}
   * instance and stores them in a map.
   */
  private ImmutableMap<String, InventoryItem> collectInventory(Cel cel) {
    ImmutableMap.Builder<String, InventoryItem> inventoryBuilder = ImmutableMap.builder();
    ((EnvVisitable) cel)
        .accept(
            (name, decls) -> {
              for (Decl decl : decls) {
                if (decl.hasFunction()) {
                  FunctionDecl function = decl.getFunction();
                  for (Overload overload : function.getOverloadsList()) {
                    InventoryItemFuncOverload item =
                        new InventoryItemFuncOverload(decl.getName(), overload);
                    inventoryBuilder.put(item.getSignature(), item);
                  }
                } else if (decl.hasIdent()) {
                  InventoryItemVar item =
                      new InventoryItemVar(decl.getName(), decl.getIdent().getType());
                  inventoryBuilder.put(item.getSignature(), item);
                }
              }
            });

    return inventoryBuilder.buildKeepingLast();
  }

  /**
   * Iterates through the available extension libraries, checks if they are included in the
   * inventory, and adds them to the environment builder. Only the highest version of a library is
   * added to the builder.
   */
  private void addExtensionConfigs(
      CelEnvironment.Builder envBuilder, ImmutableMap<String, InventoryItem> inventoryItems) {
    ImmutableList<CelExtensionLibrary> libraries =
        getExtensionLibraries().stream()
            .sorted(
                Comparator.comparing(CelExtensionLibrary::getName)
                    .thenComparing(CelExtensionLibrary::getVersion)
                    .reversed())
            .collect(toImmutableList());

    Set<String> includedExtensions = new HashSet<>();
    for (CelExtensionLibrary library : libraries) {
      if (includedExtensions.contains(library.getName())) {
        // We only need to infer the highest version library, so we can skip lower versions
        continue;
      }

      if (checkIfExtensionIsIncluded(inventoryItems, library)) {
        envBuilder.addExtensions(ExtensionConfig.of(library.getName(), library.getVersion()));
        includedExtensions.add(library.getName());
      }
    }
  }

  private boolean checkIfExtensionIsIncluded(
      ImmutableMap<String, InventoryItem> inventoryItems, CelExtensionLibrary library) {
    ImmutableSet<CelFunctionDecl> functions = library.getFunctions();
    ArrayList<InventoryItem> itemList = new ArrayList<>(functions.size());
    for (CelFunctionDecl function : functions) {
      for (CelOverloadDecl overload : function.overloads()) {
        String signature = getFunctionOverloadSignature(function.name(), overload);
        InventoryItem item = inventoryItems.get(signature);
        if (item == null) {
          return false;
        }
        itemList.add(item);
      }
    }

    // TODO - Add checks for variables and macros.

    String extensionName = library.getName();
    int extensionVersion = library.getVersion();
    for (InventoryItem inventoryItem : itemList) {
      inventoryItem.setExtension(extensionName, extensionVersion);
    }
    return true;
  }

  // See `CelStandardDeclarations.deprecatedFunctions()`. These function definitions
  // are replicated here to avoid exposing the `deprecatedFunctions` method outside the
  // `dev.cel.checker` package.
  private static final ImmutableSet<CelFunctionDecl> DEPRECATED_STANDARD_FUNCTIONS =
      ImmutableSet.of(
          CelFunctionDecl.newFunctionDeclaration(
              "__not_strictly_false__",
              CelOverloadDecl.newGlobalOverload(
                  "not_strictly_false", SimpleType.BOOL, SimpleType.BOOL)),
          CelFunctionDecl.newFunctionDeclaration(
              "_in_",
              CelOverloadDecl.newGlobalOverload(
                  "in_list",
                  SimpleType.BOOL,
                  TypeParamType.create("A"),
                  ListType.create(TypeParamType.create("A"))),
              CelOverloadDecl.newGlobalOverload(
                  "in_map",
                  SimpleType.BOOL,
                  TypeParamType.create("A"),
                  MapType.create(TypeParamType.create("A"), TypeParamType.create("B")))));

  private void addStandardLibrarySubset(
      CelEnvironment.Builder envBuilder, ImmutableMap<String, InventoryItem> inventoryItems) {
    // Claim standard identifiers for the standard library
    for (StandardIdentifier value : StandardIdentifier.values()) {
      String signature = getVarDeclSignature(value.identDecl().name(), value.identDecl().type());
      InventoryItem item = inventoryItems.get(signature);
      if (item != null) {
        item.setInStandardLibrary();
      }
    }

    Set<String> excludedFunctions = new HashSet<>();
    Set<String> includedFunctions = new HashSet<>();
    ListMultimap<String, String> excludedOverloads = ArrayListMultimap.create();
    ListMultimap<String, String> includedOverloads = ArrayListMultimap.create();

    List<CelFunctionDecl> allStdFunctions = new ArrayList<>(DEPRECATED_STANDARD_FUNCTIONS);
    Arrays.stream(StandardFunction.values())
        .map(StandardFunction::functionDecl)
        .forEach(allStdFunctions::add);

    for (CelFunctionDecl decl : allStdFunctions) {
      String functionName = decl.name();
      boolean anyOverloadIncluded = false;
      boolean allOverloadsIncluded = true;
      for (CelOverloadDecl overload : decl.overloads()) {
        String signature = getFunctionOverloadSignature(functionName, overload);
        InventoryItem item = inventoryItems.get(signature);
        if (item != null) {
          anyOverloadIncluded = true;
          item.setInStandardLibrary();
          includedOverloads.put(functionName, overload.overloadId());
        } else {
          allOverloadsIncluded = false;
          excludedOverloads.put(functionName, overload.overloadId());
        }
      }
      if (!anyOverloadIncluded) {
        excludedFunctions.add(functionName);
      }
      if (allOverloadsIncluded) {
        includedFunctions.add(functionName);
      }
    }

    LibrarySubset.Builder subsetBuilder = LibrarySubset.newBuilder();
    subsetBuilder.setDisabled(false);
    if (excludedFunctions.size() <= getMaxExcludedStandardFunctions()
        && excludedOverloads.size() <= getMaxExcludedStandardFunctionOverloads()) {
      subsetBuilder.setExcludedFunctions(
          buildFunctionSelectors(excludedFunctions, excludedOverloads));
    } else {
      subsetBuilder.setIncludedFunctions(
          buildFunctionSelectors(includedFunctions, includedOverloads));
    }

    // TODO - Add support for including/excluding macros.

    envBuilder.setStandardLibrarySubset(subsetBuilder.build());
  }

  private ImmutableSet<FunctionSelector> buildFunctionSelectors(
      Set<String> functions, ListMultimap<String, String> functionToOverloadsMap) {
    ImmutableSet.Builder<FunctionSelector> functionSelectors = ImmutableSet.builder();
    for (String excludedFunction : functions) {
      functionSelectors.add(FunctionSelector.create(excludedFunction, ImmutableSet.of()));
    }

    for (String functionName : functionToOverloadsMap.keySet()) {
      if (functions.contains(functionName)) {
        continue;
      }
      functionSelectors.add(
          FunctionSelector.create(
              functionName, ImmutableSet.copyOf(functionToOverloadsMap.get(functionName))));
    }
    return functionSelectors.build();
  }

  private void addCustomDecls(
      CelEnvironment.Builder envBuilder, ImmutableMap<String, InventoryItem> inventoryItems) {
    // Group "orphaned" function overloads and vars by their names
    ListMultimap<String, Overload> extraOverloads = ArrayListMultimap.create();
    Map<String, Type> extraVars = new HashMap<>();
    for (Entry<String, InventoryItem> entry : inventoryItems.entrySet()) {
      InventoryItem item = entry.getValue();
      if (item.extension == null) {
        if (item instanceof InventoryItemFuncOverload) {
          extraOverloads.put(
              ((InventoryItemFuncOverload) item).functionName,
              ((InventoryItemFuncOverload) item).overload);
        } else if (item instanceof InventoryItemVar) {
          extraVars.put(((InventoryItemVar) item).ident, ((InventoryItemVar) item).type);
        }
      }
    }

    if (!extraOverloads.isEmpty()) {
      ImmutableSet.Builder<CelEnvironment.FunctionDecl> functionDeclBuilder =
          ImmutableSet.builder();
      for (String functionName : extraOverloads.keySet()) {
        functionDeclBuilder.add(
            CelEnvironment.FunctionDecl.create(
                functionName,
                extraOverloads.get(functionName).stream()
                    .map(this::toCelEnvOverloadDecl)
                    .collect(toImmutableSet())));
      }
      envBuilder.setFunctions(functionDeclBuilder.build());
    }

    if (!extraVars.isEmpty()) {
      ImmutableSet.Builder<CelEnvironment.VariableDecl> varDeclBuilder = ImmutableSet.builder();
      for (String ident : extraVars.keySet()) {
        varDeclBuilder.add(
            CelEnvironment.VariableDecl.create(ident, toCelEnvTypeDecl(extraVars.get(ident))));
      }
      envBuilder.setVariables(varDeclBuilder.build());
    }
  }

  private abstract static class InventoryItem {
    private final String signature;
    private String extension;
    private int extensionVersion;

    InventoryItem(String signature) {
      this.signature = signature;
    }

    String getSignature() {
      return signature;
    }

    public void setExtension(String extension, int extensionVersion) {
      this.extension = extension;
      this.extensionVersion = extensionVersion;
    }

    public void setInStandardLibrary() {
      this.extension = "@stdlib";
    }

    @Override
    public String toString() {
      StringBuilder builder = new StringBuilder().append(getSignature());
      if (extension != null) {
        builder
            .append(" extension: ")
            .append(extension)
            .append('(')
            .append(extensionVersion)
            .append(")");
      }
      return builder.toString();
    }
  }

  private static class InventoryItemFuncOverload extends InventoryItem {
    private final String functionName;
    private final FunctionDecl.Overload overload;

    InventoryItemFuncOverload(String functionName, FunctionDecl.Overload overload) {
      super(getFunctionOverloadSignature(functionName, overload));
      this.functionName = functionName;
      this.overload = overload;
    }
  }

  private static class InventoryItemVar extends InventoryItem {
    private final String ident;
    private final Type type;

    InventoryItemVar(String ident, Type type) {
      super(getVarDeclSignature(ident, type));
      this.ident = ident;
      this.type = type;
    }
  }

  // Keep the two version of this method mutually compatible
  private static String getVarDeclSignature(String ident, Type type) {
    return "V " + ident + " " + CelTypes.format(CelProtoTypes.typeToCelType(type));
  }

  // Keep the two version of this method mutually compatible
  private static String getVarDeclSignature(String ident, CelType type) {
    return "V " + ident + " " + CelTypes.format(type);
  }

  // Keep the two version of this method mutually compatible
  private static String getFunctionOverloadSignature(String functionName, Overload overload) {
    return "F "
        + functionName
        + " "
        + overload.getOverloadId()
        + " "
        + CelTypes.formatFunction(
            /* resultType= */ null,
            overload.getParamsList().stream()
                .map(CelProtoTypes::typeToCelType)
                .collect(toImmutableList()),
            overload.getIsInstanceFunction(),
            /* typeParamToDyn= */ true);
  }

  // Keep the two version of this method mutually compatible
  private static String getFunctionOverloadSignature(
      String functionName, CelOverloadDecl overload) {
    return "F "
        + functionName
        + " "
        + overload.overloadId()
        + " "
        + CelTypes.formatFunction(
            /* resultType= */ null,
            overload.parameterTypes(),
            overload.isInstanceFunction(),
            /* typeParamToDyn= */ true);
  }

  private CelEnvironment.OverloadDecl toCelEnvOverloadDecl(Overload overload) {
    OverloadDecl.Builder builder =
        OverloadDecl.newBuilder()
            .setId(overload.getOverloadId())
            .setReturnType(toCelEnvTypeDecl(overload.getResultType()));

    if (overload.getIsInstanceFunction()) {
      builder.setTarget(toCelEnvTypeDecl(overload.getParamsList().get(0)));
      builder.setArguments(
          overload.getParamsList().subList(1, overload.getParamsCount()).stream()
              .map(this::toCelEnvTypeDecl)
              .collect(toImmutableList()));
    } else {
      builder.setArguments(
          overload.getParamsList().stream().map(this::toCelEnvTypeDecl).collect(toImmutableList()));
    }
    return builder.build();
  }

  private CelEnvironment.TypeDecl toCelEnvTypeDecl(Type type) {
    return CelEnvironment.TypeDecl.create(CelProtoTypes.format(type));
  }
}
