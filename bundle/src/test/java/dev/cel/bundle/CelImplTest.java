// Copyright 2022 Google LLC
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

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.extensions.proto.ProtoTruth.assertThat;
import static dev.cel.common.CelFunctionDecl.newFunctionDeclaration;
import static dev.cel.common.CelOverloadDecl.newGlobalOverload;
import static dev.cel.common.CelOverloadDecl.newMemberOverload;
import static org.junit.Assert.assertThrows;

import dev.cel.expr.CheckedExpr;
import dev.cel.expr.Constant;
import dev.cel.expr.Decl;
import dev.cel.expr.Decl.FunctionDecl;
import dev.cel.expr.Decl.FunctionDecl.Overload;
import dev.cel.expr.Decl.IdentDecl;
import dev.cel.expr.Expr;
import dev.cel.expr.Expr.Call;
import dev.cel.expr.Expr.Ident;
import dev.cel.expr.Expr.Select;
import dev.cel.expr.ParsedExpr;
import dev.cel.expr.Reference;
import dev.cel.expr.Type;
import dev.cel.expr.Type.PrimitiveType;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import com.google.protobuf.DescriptorProtos.FileDescriptorProto;
import com.google.protobuf.DescriptorProtos.FileDescriptorSet;
import com.google.protobuf.Descriptors.FileDescriptor;
import com.google.protobuf.Duration;
import com.google.protobuf.FieldMask;
import com.google.protobuf.Message;
import com.google.protobuf.NullValue;
import com.google.protobuf.Struct;
import com.google.protobuf.TextFormat;
import com.google.protobuf.Timestamp;
import com.google.protobuf.util.Timestamps;
import com.google.rpc.context.AttributeContext;
import com.google.testing.junit.testparameterinjector.TestParameter;
import com.google.testing.junit.testparameterinjector.TestParameterInjector;
import com.google.testing.junit.testparameterinjector.TestParameters;
import dev.cel.checker.CelCheckerLegacyImpl;
import dev.cel.checker.DescriptorTypeProvider;
import dev.cel.checker.ProtoTypeMask;
import dev.cel.checker.TypeProvider;
import dev.cel.common.CelAbstractSyntaxTree;
import dev.cel.common.CelIssue;
import dev.cel.common.CelOptions;
import dev.cel.common.CelProtoAbstractSyntaxTree;
import dev.cel.common.CelValidationException;
import dev.cel.common.CelValidationResult;
import dev.cel.common.CelVarDecl;
import dev.cel.common.ast.CelExpr;
import dev.cel.common.ast.CelExpr.CelList;
import dev.cel.common.testing.RepeatedTestProvider;
import dev.cel.common.types.CelKind;
import dev.cel.common.types.CelType;
import dev.cel.common.types.CelTypes;
import dev.cel.common.types.EnumType;
import dev.cel.common.types.ListType;
import dev.cel.common.types.MapType;
import dev.cel.common.types.OptionalType;
import dev.cel.common.types.ProtoMessageTypeProvider;
import dev.cel.common.types.SimpleType;
import dev.cel.common.types.StructTypeReference;
import dev.cel.compiler.CelCompiler;
import dev.cel.compiler.CelCompilerFactory;
import dev.cel.compiler.CelCompilerImpl;
import dev.cel.parser.CelParserImpl;
import dev.cel.parser.CelStandardMacro;
import dev.cel.runtime.CelAttribute;
import dev.cel.runtime.CelAttribute.Qualifier;
import dev.cel.runtime.CelAttributePattern;
import dev.cel.runtime.CelEvaluationException;
import dev.cel.runtime.CelRuntime;
import dev.cel.runtime.CelRuntime.CelFunctionBinding;
import dev.cel.runtime.CelRuntime.Program;
import dev.cel.runtime.CelRuntimeFactory;
import dev.cel.runtime.CelRuntimeLegacyImpl;
import dev.cel.runtime.CelUnknownSet;
import dev.cel.runtime.CelVariableResolver;
import dev.cel.runtime.UnknownContext;
import dev.cel.testing.testdata.proto3.StandaloneGlobalEnum;
import dev.cel.testing.testdata.proto3.TestAllTypesProto.TestAllTypes;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import org.jspecify.nullness.Nullable;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(TestParameterInjector.class)
public final class CelImplTest {

  private static final Expr NOT_EXPR =
      Expr.newBuilder()
          .setCallExpr(
              Call.newBuilder()
                  .setFunction("!_")
                  .addArgs(
                      Expr.newBuilder().setConstExpr(Constant.newBuilder().setBoolValue(true))))
          .build();

  private static final Expr NOT_NOT_NOT_EXPR =
      Expr.newBuilder()
          .setCallExpr(
              Call.newBuilder()
                  .setFunction("!_")
                  .addArgs(
                      Expr.newBuilder()
                          .setCallExpr(Call.newBuilder().setFunction("!_").addArgs(NOT_EXPR))))
          .build();

  private static final Expr EXPR =
      Expr.newBuilder()
          .setCallExpr(
              Call.newBuilder()
                  .setFunction("_&&_")
                  .addArgs(Expr.newBuilder().setConstExpr(Constant.newBuilder().setBoolValue(true)))
                  .addArgs(
                      Expr.newBuilder()
                          .setCallExpr(
                              Call.newBuilder()
                                  .setFunction("!_")
                                  .addArgs(
                                      Expr.newBuilder()
                                          .setConstExpr(
                                              Constant.newBuilder().setBoolValue(false))))))
          .build();

  private static final ParsedExpr PARSED_EXPR = ParsedExpr.newBuilder().setExpr(EXPR).build();
  private static final CheckedExpr CHECKED_EXPR =
      CheckedExpr.newBuilder()
          .setExpr(EXPR)
          .putTypeMap(1L, CelTypes.BOOL)
          .putTypeMap(2L, CelTypes.BOOL)
          .putTypeMap(3L, CelTypes.BOOL)
          .putTypeMap(4L, CelTypes.BOOL)
          .putReferenceMap(2L, Reference.newBuilder().addOverloadId("logical_and").build())
          .putReferenceMap(3L, Reference.newBuilder().addOverloadId("logical_not").build())
          .build();

  private static final ParsedExpr PARSED_HAS_EXPR =
      ParsedExpr.newBuilder()
          .setExpr(
              Expr.newBuilder()
                  .setSelectExpr(
                      Select.newBuilder()
                          .setOperand(
                              Expr.newBuilder().setIdentExpr(Ident.newBuilder().setName("a")))
                          .setField("b")
                          .setTestOnly(true)))
          .build();

  private CelBuilder standardCelBuilderWithMacros() {
    return CelFactory.standardCelBuilder().setStandardMacros(CelStandardMacro.STANDARD_MACROS);
  }

  @Test
  public void build_badFileDescriptorSet() {
    IllegalArgumentException e =
        Assert.assertThrows(
            IllegalArgumentException.class,
            () ->
                standardCelBuilderWithMacros()
                    .setContainer("google.rpc.context.AttributeContext")
                    .addFileTypes(
                        FileDescriptorSet.newBuilder()
                            .addFile(AttributeContext.getDescriptor().getFile().toProto())
                            .build())
                    .setProtoResultType(
                        CelTypes.createMessage("google.rpc.context.AttributeContext.Resource"))
                    .build());
    assertThat(e).hasMessageThat().contains("file descriptor set with unresolved proto file");
  }

  @Test
  public void parse() throws Exception {
    Cel cel = standardCelBuilderWithMacros().build();
    assertValidationResult(cel.parse("true && !false"), PARSED_EXPR);
  }

  @Test
  public void check() throws Exception {
    Cel cel = standardCelBuilderWithMacros().setResultType(SimpleType.BOOL).build();
    CelValidationResult parseResult = cel.parse("true && !false");
    assertValidationResult(parseResult, PARSED_EXPR);
    CelValidationResult checkResult = cel.check(parseResult.getAst());
    assertValidationResult(checkResult, CHECKED_EXPR);
  }

  @Test
  @TestParameters("{useProtoResultType: false}")
  @TestParameters("{useProtoResultType: true}")
  public void compile(boolean useProtoResultType) throws Exception {
    CelBuilder celBuilder = standardCelBuilderWithMacros();
    if (useProtoResultType) {
      celBuilder.setProtoResultType(CelTypes.BOOL);
    } else {
      celBuilder.setResultType(SimpleType.BOOL);
    }
    Cel cel = celBuilder.build();
    assertValidationResult(cel.compile("true && !false"), CHECKED_EXPR);
  }

  @Test
  @TestParameters("{useProtoResultType: false}")
  @TestParameters("{useProtoResultType: true}")
  public void compile_resultTypeCheckFailure(boolean useProtoResultType) {
    CelBuilder celBuilder = standardCelBuilderWithMacros();
    if (useProtoResultType) {
      celBuilder.setProtoResultType(CelTypes.STRING);
    } else {
      celBuilder.setResultType(SimpleType.STRING);
    }
    Cel cel = celBuilder.build();
    CelValidationResult validationResult = cel.compile("true && !false");

    assertThat(validationResult.hasError()).isTrue();
    assertThat(validationResult.getErrorString())
        .contains("expected type 'string' but found 'bool'");
  }

  @Test
  public void compile_combinedTypeProvider() {
    ProtoMessageTypeProvider celTypeProvider =
        new ProtoMessageTypeProvider(ImmutableList.of(AttributeContext.getDescriptor()));
    Cel cel =
        standardCelBuilderWithMacros()
            .setContainer("google")
            .setTypeProvider(celTypeProvider)
            .addMessageTypes(com.google.type.Expr.getDescriptor())
            .addProtoTypeMasks(
                ImmutableList.of(ProtoTypeMask.ofAllFields("google.rpc.context.AttributeContext")))
            .addVar("condition", StructTypeReference.create("google.type.Expr"))
            .setProtoResultType(CelTypes.BOOL)
            .build();
    CelValidationResult result =
        cel.compile("type.Expr{expression: \"'hello'\"}.expression == condition.expression");
    assertThat(result.getErrorString()).isEmpty();
  }

  @Test
  public void compile_customTypeProvider() {
    ProtoMessageTypeProvider celTypeProvider =
        new ProtoMessageTypeProvider(
            ImmutableList.of(
                AttributeContext.getDescriptor(), com.google.type.Expr.getDescriptor()));
    Cel cel =
        standardCelBuilderWithMacros()
            .setContainer("google")
            .setTypeProvider(celTypeProvider)
            .addVar("condition", StructTypeReference.create("google.type.Expr"))
            .setResultType(SimpleType.BOOL)
            .build();
    CelValidationResult result =
        cel.compile("type.Expr{expression: \"'hello'\"}.expression == condition.expression");
    assertThat(result.getErrorString()).isEmpty();
  }

  @Test
  public void compile_customTypesWithAliasingCombinedProviders() throws Exception {

    // The custom type provider sets up an alias from "Condition" to "google.type.Expr".
    // However, the first type resolution from the alias to the qualified type name won't be
    // sufficient as future checks will expect the resolved alias to also be a type.
    TypeProvider customTypeProvider =
        aliasingProvider(ImmutableMap.of("Condition", CelTypes.createMessage("google.type.Expr")));

    // The registration of the aliasing TypeProvider and the google.type.Expr descriptor
    // ensures that once the alias is resolved, the additional details about the Expr type
    // are discoverable.
    //
    // The custom type factory is then necessary to ensure that the Condition type listed
    // in the AST can be resolved to the appropriate message builder instance.
    Cel cel =
        standardCelBuilderWithMacros()
            .setTypeProvider(customTypeProvider)
            .addMessageTypes(com.google.type.Expr.getDescriptor())
            .setTypeFactory(
                (typeName) ->
                    typeName.equals("Condition") ? com.google.type.Expr.newBuilder() : null)
            .setResultType(StructTypeReference.create("google.type.Expr"))
            .build();
    CelValidationResult result = cel.compile("Condition{expression: \"'hello'\"}");
    assertThat(result.getErrorString()).isEmpty();
    CelRuntime.Program program = cel.createProgram(result.getAst());
    assertThat(program.eval())
        .isEqualTo(com.google.type.Expr.newBuilder().setExpression("'hello'").build());
  }

  @Test
  public void compile_customTypesWithAliasingSelfContainedProvider() throws Exception {

    // The custom type provider sets up an alias from "Condition" to "google.type.Expr".
    TypeProvider customTypeProvider =
        aliasingProvider(
            ImmutableMap.of(
                "Condition",
                CelTypes.createMessage("google.type.Expr"),
                "google.type.Expr",
                CelTypes.createMessage("google.type.Expr")));

    // The registration of the aliasing TypeProvider and the google.type.Expr descriptor
    // ensures that once the alias is resolved, the additional details about the Expr type
    // are discoverable.
    //
    // The custom type factory is then necessary to ensure that the Condition type listed
    // in the AST can be resolved to the appropriate message builder instance.
    Cel cel =
        standardCelBuilderWithMacros()
            .setTypeProvider(customTypeProvider)
            .setTypeFactory(
                (typeName) ->
                    typeName.equals("Condition") ? com.google.type.Expr.newBuilder() : null)
            .setResultType(StructTypeReference.create("google.type.Expr"))
            .build();
    CelValidationResult result = cel.compile("Condition{expression: \"'hello'\"}");
    assertThat(result.getErrorString()).isEmpty();
    CelRuntime.Program program = cel.createProgram(result.getAst());
    assertThat(program.eval())
        .isEqualTo(com.google.type.Expr.newBuilder().setExpression("'hello'").build());
  }

  @Test
  public void program_setTypeFactoryOnAnyPackedMessage_fieldSelectionSuccess() throws Exception {
    // Arrange
    CelCompiler celCompiler =
        CelCompilerFactory.standardCelCompilerBuilder()
            .addVar("input", StructTypeReference.create("google.type.Expr"))
            .addMessageTypes(com.google.type.Expr.getDescriptor())
            .setResultType(SimpleType.STRING)
            .build();
    CelAbstractSyntaxTree ast = celCompiler.compile("input.expression").getAst();
    CelRuntime celRuntime =
        CelRuntimeFactory.standardCelRuntimeBuilder()
            .setTypeFactory(
                (typeName) ->
                    typeName.equals("google.type.Expr") ? com.google.type.Expr.newBuilder() : null)
            .build();
    CelRuntime.Program program = celRuntime.createProgram(ast);
    Message exprMessage = com.google.type.Expr.newBuilder().setExpression("test").build();

    // Act
    Object evaluatedResult1 = program.eval(ImmutableMap.of("input", exprMessage));
    Object evaluatedResult2 = program.eval(ImmutableMap.of("input", Any.pack(exprMessage)));

    // Assert
    assertThat(evaluatedResult1).isEqualTo("test");
    assertThat(evaluatedResult2).isEqualTo("test");
  }

  @Test
  public void program_setTypeFactoryOnAnyPackedMessage_messageConstructionSucceeds()
      throws Exception {
    // Arrange
    CelCompiler celCompiler =
        CelCompilerFactory.standardCelCompilerBuilder()
            .addVar("input", StructTypeReference.create("google.type.Expr"))
            .addMessageTypes(com.google.type.Expr.getDescriptor())
            .build();
    CelAbstractSyntaxTree ast = celCompiler.compile("input").getAst();

    CelRuntime celRuntime =
        CelRuntimeFactory.standardCelRuntimeBuilder()
            .setTypeFactory(
                (typeName) ->
                    typeName.equals("google.type.Expr") ? com.google.type.Expr.newBuilder() : null)
            .build();
    CelRuntime.Program program = celRuntime.createProgram(ast);
    Message exprMessage = com.google.type.Expr.newBuilder().setExpression("test").build();

    // Act
    Object evaluatedResult1 = program.eval(ImmutableMap.of("input", exprMessage));
    Object evaluatedResult2 = program.eval(ImmutableMap.of("input", Any.pack(exprMessage)));

    // Assert
    assertThat(evaluatedResult1).isEqualTo(exprMessage);
    assertThat(evaluatedResult2).isEqualTo(exprMessage);
  }

  @Test
  public void program_concurrentMessageConstruction_succeeds(
      @TestParameter(valuesProvider = RepeatedTestProvider.class) int testRunIndex)
      throws Exception {
    // Arrange
    int threadCount = 10;
    Cel cel =
        standardCelBuilderWithMacros()
            .setContainer("google.rpc.context.AttributeContext")
            .addFileTypes(
                Any.getDescriptor().getFile(),
                Duration.getDescriptor().getFile(),
                Struct.getDescriptor().getFile(),
                Timestamp.getDescriptor().getFile(),
                AttributeContext.getDescriptor().getFile())
            .setResultType(
                StructTypeReference.create("google.rpc.context.AttributeContext.Resource"))
            .build();
    CelRuntime.Program program =
        cel.createProgram(cel.compile("Resource{name: \"'hello'\"}").getAst());
    ExecutorService executor =
        MoreExecutors.getExitingExecutorService(
            (ThreadPoolExecutor) Executors.newFixedThreadPool(threadCount));

    // Act
    List<Future<AttributeContext.Resource>> futures = new ArrayList<>();
    for (int i = 0; i < threadCount; i++) {
      futures.add(executor.submit(() -> (AttributeContext.Resource) program.eval()));
    }

    // Assert
    AttributeContext.Resource expectedResult =
        AttributeContext.Resource.newBuilder().setName("'hello'").build();
    for (Future<AttributeContext.Resource> future : futures) {
      assertThat(future.get()).isEqualTo(expectedResult);
    }
  }

  @Test
  public void compile_syntaxFailure() throws Exception {
    Cel cel = standardCelBuilderWithMacros().build();
    CelValidationResult result = cel.compile("|| false");
    assertThat(result.hasError()).isTrue();
    assertThat(result.getErrors())
        .containsExactly(
            CelIssue.formatError(
                1,
                0,
                "extraneous input '||' expecting {'[', '{', '(', '.', '-', '!', 'true', 'false',"
                    + " 'null', NUM_FLOAT, NUM_INT, NUM_UINT, STRING, BYTES, IDENTIFIER}"));
    assertThat(result.getErrorString())
        .isEqualTo(
            "ERROR: <input>:1:1: extraneous input '||' expecting {'[', '{', '(', '.', '-', '!',"
                + " 'true', 'false', 'null', NUM_FLOAT, NUM_INT, NUM_UINT, STRING, BYTES,"
                + " IDENTIFIER}\n"
                + " | || false\n"
                + " | ^");
  }

  @Test
  public void compile_typeCheckFailure() {
    Cel cel = standardCelBuilderWithMacros().build();
    CelValidationResult syntaxErrorResult = cel.compile("variable");
    assertThat(syntaxErrorResult.hasError()).isTrue();
    assertThat(syntaxErrorResult.getErrors())
        .containsExactly(
            CelIssue.formatError(1, 0, "undeclared reference to 'variable' (in container '')"));
    assertThat(syntaxErrorResult.getErrorString())
        .isEqualTo(
            "ERROR: <input>:1:1: undeclared reference to 'variable' (in container '')\n"
                + " | variable\n"
                + " | ^");
  }

  @Test
  public void compile_withOptionalTypes() throws Exception {
    Cel cel =
        CelFactory.standardCelBuilder()
            .setOptions(CelOptions.current().enableOptionalSyntax(true).build())
            .addVar("a", OptionalType.create(SimpleType.STRING))
            .build();

    CelAbstractSyntaxTree ast = cel.compile("[?a]").getAst();

    CelList createList = ast.getExpr().list();
    assertThat(createList.optionalIndices()).containsExactly(0);
    assertThat(createList.elements()).containsExactly(CelExpr.ofIdent(2, "a"));
  }

  @Test
  public void compile_overlappingVarsFailure() {
    Cel cel =
        standardCelBuilderWithMacros()
            .addDeclarations(
                Decl.newBuilder()
                    .setName("variable")
                    .setIdent(IdentDecl.newBuilder().setType(CelTypes.STRING))
                    .build())
            .addDeclarations(
                Decl.newBuilder()
                    .setName("variable")
                    .setIdent(IdentDecl.newBuilder().setType(CelTypes.createList(CelTypes.STRING)))
                    .build())
            .setResultType(SimpleType.BOOL)
            .build();
    CelValidationException e =
        Assert.assertThrows(
            CelValidationException.class, () -> cel.compile("variable == 'hello'").getAst());
    assertThat(e).hasMessageThat().contains("variable");
  }

  @Test
  public void program() throws Exception {
    Cel cel = standardCelBuilderWithMacros().setResultType(SimpleType.BOOL).build();
    CelRuntime.Program program = cel.createProgram(cel.compile("true && !false").getAst());
    assertThat(program.eval()).isEqualTo(true);
  }

  @Test
  public void program_withVars() throws Exception {
    Cel cel =
        standardCelBuilderWithMacros()
            .addDeclarations(
                Decl.newBuilder()
                    .setName("variable")
                    .setIdent(IdentDecl.newBuilder().setType(CelTypes.STRING))
                    .build())
            .setResultType(SimpleType.BOOL)
            .build();
    CelRuntime.Program program = cel.createProgram(cel.compile("variable == 'hello'").getAst());
    assertThat(program.eval(ImmutableMap.of("variable", "hello"))).isEqualTo(true);
  }

  @Test
  public void program_withCelValue() throws Exception {
    Cel cel =
        standardCelBuilderWithMacros()
            .setOptions(CelOptions.current().enableCelValue(true).build())
            .addDeclarations(
                Decl.newBuilder()
                    .setName("variable")
                    .setIdent(IdentDecl.newBuilder().setType(CelTypes.STRING))
                    .build())
            .setResultType(SimpleType.BOOL)
            .build();

    CelRuntime.Program program = cel.createProgram(cel.compile("variable == 'hello'").getAst());

    assertThat(program.eval(ImmutableMap.of("variable", "hello"))).isEqualTo(true);
  }

  @Test
  public void program_withProtoVars() throws Exception {
    Cel cel =
        standardCelBuilderWithMacros()
            .addMessageTypes(AttributeContext.getDescriptor())
            .addProtoTypeMasks(
                ProtoTypeMask.of(
                        "google.rpc.context.AttributeContext",
                        FieldMask.newBuilder().addPaths("resource.*").build())
                    .withFieldsAsVariableDeclarations())
            .setResultType(SimpleType.BOOL)
            .build();
    CelRuntime.Program program =
        cel.createProgram(
            cel.compile("resource.name == 'secure' && resource.type == 'compute.vm'").getAst());
    assertThat(
            program.eval(
                AttributeContext.newBuilder()
                    .setResource(
                        AttributeContext.Resource.newBuilder()
                            .setName("secure")
                            .setType("compute.vm"))
                    .build()))
        .isEqualTo(true);
  }

  @Test
  public void program_withNestedRestrictedProtoVars() throws Exception {
    Cel cel =
        standardCelBuilderWithMacros()
            .addMessageTypes(AttributeContext.getDescriptor())
            .addProtoTypeMasks(
                ProtoTypeMask.of(
                        "google.rpc.context.AttributeContext",
                        FieldMask.newBuilder().addPaths("resource.type").build())
                    .withFieldsAsVariableDeclarations())
            .setResultType(SimpleType.BOOL)
            .build();
    CelValidationException e =
        assertThrows(
            CelValidationException.class,
            () -> cel.compile("{1:resource}[1].name == 'secure'").getAst());
    assertThat(e).hasMessageThat().contains("undefined field 'name'");
  }

  @Test
  public void program_withFunctions() throws Exception {
    Cel cel =
        standardCelBuilderWithMacros()
            .addDeclarations(
                ImmutableList.of(
                    Decl.newBuilder()
                        .setName("one")
                        .setIdent(IdentDecl.newBuilder().setType(CelTypes.BOOL))
                        .build(),
                    Decl.newBuilder()
                        .setName("two")
                        .setIdent(IdentDecl.newBuilder().setType(CelTypes.BOOL))
                        .build(),
                    Decl.newBuilder()
                        .setName("any")
                        .setFunction(
                            FunctionDecl.newBuilder()
                                .addOverloads(
                                    Overload.newBuilder()
                                        .setOverloadId("any_bool")
                                        .addParams(CelTypes.BOOL)
                                        .setResultType(CelTypes.BOOL))
                                .addOverloads(
                                    Overload.newBuilder()
                                        .setOverloadId("any_bool_bool")
                                        .addParams(CelTypes.BOOL)
                                        .addParams(CelTypes.BOOL)
                                        .setResultType(CelTypes.BOOL))
                                .addOverloads(
                                    Overload.newBuilder()
                                        .setOverloadId("any_bool_bool_bool")
                                        .addParams(CelTypes.BOOL)
                                        .addParams(CelTypes.BOOL)
                                        .addParams(CelTypes.BOOL)
                                        .setResultType(CelTypes.BOOL)))
                        .build()))
            .addFunctionBindings(CelFunctionBinding.from("any_bool", Boolean.class, (arg) -> arg))
            .addFunctionBindings(
                ImmutableList.of(
                    CelFunctionBinding.from(
                        "any_bool_bool",
                        Boolean.class,
                        Boolean.class,
                        (arg1, arg2) -> (boolean) arg1 || (boolean) arg2),
                    CelFunctionBinding.from(
                        "any_bool_bool_bool",
                        ImmutableList.of(Boolean.class, Boolean.class, Boolean.class),
                        (args) -> (boolean) args[0] || (boolean) args[1] || (boolean) args[2])))
            .setResultType(SimpleType.BOOL)
            .build();
    CelRuntime.Program program =
        cel.createProgram(
            cel.compile("any(true) && any(false, one) && any(false, two, false)").getAst());
    assertThat(program.eval(ImmutableMap.of("one", true, "two", true))).isEqualTo(true);
  }

  @Test
  public void program_withThrowingFunction() throws Exception {
    Cel cel =
        standardCelBuilderWithMacros()
            .addDeclarations(
                Decl.newBuilder()
                    .setName("throws")
                    .setFunction(
                        FunctionDecl.newBuilder()
                            .addOverloads(
                                Overload.newBuilder()
                                    .setOverloadId("throws")
                                    .setResultType(CelTypes.BOOL)))
                    .build())
            .addFunctionBindings(
                CelFunctionBinding.from(
                    "throws",
                    ImmutableList.of(),
                    (args) -> {
                      throw new CelEvaluationException("this method always throws");
                    }))
            .setResultType(SimpleType.BOOL)
            .build();
    CelRuntime.Program program = cel.createProgram(cel.compile("throws()").getAst());
    CelEvaluationException e = Assert.assertThrows(CelEvaluationException.class, program::eval);
    assertThat(e).hasMessageThat().contains("this method always throws");
  }

  @Test
  public void program_withThrowingFunctionShortcircuited() throws Exception {
    Cel cel =
        standardCelBuilderWithMacros()
            .addDeclarations(
                Decl.newBuilder()
                    .setName("throws")
                    .setFunction(
                        FunctionDecl.newBuilder()
                            .addOverloads(
                                Overload.newBuilder()
                                    .setOverloadId("throws")
                                    .setResultType(CelTypes.BOOL)))
                    .build())
            .addFunctionBindings(
                CelFunctionBinding.from(
                    "throws",
                    ImmutableList.of(),
                    (args) -> {
                      throw new CelEvaluationException(
                          "this method always throws", new RuntimeException("reason"));
                    }))
            .setResultType(SimpleType.BOOL)
            .build();
    CelRuntime.Program program = cel.createProgram(cel.compile("throws() || true").getAst());
    assertThat(program.eval()).isEqualTo(true);
  }

  @Test
  public void program_simpleStructTypeReference() throws Exception {
    CelCompiler celCompiler =
        CelCompilerFactory.standardCelCompilerBuilder()
            .addVar("test", StructTypeReference.create(Expr.getDescriptor().getFullName()))
            .addMessageTypes(Expr.getDescriptor())
            .setResultType(SimpleType.BOOL)
            .build();
    CelRuntime celRuntime = CelRuntimeFactory.standardCelRuntimeBuilder().build();
    CelRuntime.Program program =
        celRuntime.createProgram(celCompiler.compile("test.id == 2").getAst());

    Object evaluatedResult =
        program.eval(ImmutableMap.of("test", Expr.newBuilder().setId(2).build()));

    assertThat(evaluatedResult).isEqualTo(true);
  }

  @Test
  public void program_messageConstruction() throws Exception {
    Cel cel =
        standardCelBuilderWithMacros()
            .setContainer("google.type")
            .addMessageTypes(com.google.type.Expr.getDescriptor())
            .setResultType(StructTypeReference.create("google.type.Expr"))
            .setStandardEnvironmentEnabled(false)
            .build();
    CelRuntime.Program program =
        cel.createProgram(cel.compile("type.Expr{expression: \"'hello'\"}").getAst());
    assertThat(program.eval())
        .isEqualTo(com.google.type.Expr.newBuilder().setExpression("'hello'").build());
  }

  @Test
  public void program_duplicateTypeDescriptor() throws Exception {
    Cel cel =
        standardCelBuilderWithMacros()
            .addMessageTypes(Timestamp.getDescriptor())
            .addMessageTypes(ImmutableList.of(Timestamp.getDescriptor()))
            .setContainer("google")
            .setResultType(SimpleType.TIMESTAMP)
            .build();
    CelRuntime.Program program =
        cel.createProgram(cel.compile("protobuf.Timestamp{seconds: 12}").getAst());
    assertThat(program.eval()).isEqualTo(Timestamps.fromSeconds(12));
  }

  @Test
  public void program_hermeticDescriptors_wellKnownProtobuf() throws Exception {
    Cel cel =
        standardCelBuilderWithMacros()
            .addMessageTypes(Timestamp.getDescriptor())
            .setContainer("google")
            .setResultType(SimpleType.TIMESTAMP)
            .build();
    CelRuntime.Program program =
        cel.createProgram(cel.compile("protobuf.Timestamp{seconds: 12}").getAst());
    assertThat(program.eval()).isEqualTo(Timestamps.fromSeconds(12));
  }

  @Test
  public void program_partialMessageTypes() throws Exception {
    String packageName = CheckedExpr.getDescriptor().getFile().getPackage();
    Cel cel =
        standardCelBuilderWithMacros()
            .addFileTypes(CheckedExpr.getDescriptor().getFile())
            // Disabling the resolution of type dependencies can be risky as message types which
            // are expected to be available in an imported file may not be present if the type
            // is not referenced in a field within the provided file descriptors.
            //
            // In this test 'Expr' is defined in syntax.proto, but the descriptor provided is
            // defined in checked.proto. Because the `Expr` type is referenced within a message
            // field of the CheckedExpr, it is available for use.
            .setOptions(CelOptions.current().resolveTypeDependencies(false).build())
            .setContainer(packageName)
            .setResultType(StructTypeReference.create(packageName + ".Expr"))
            .build();
    CelRuntime.Program program = cel.createProgram(cel.compile("Expr{}").getAst());
    assertThat(program.eval()).isEqualTo(Expr.getDefaultInstance());
  }

  @Test
  public void program_partialMessageTypeFailure() {
    String packageName = CheckedExpr.getDescriptor().getFile().getPackage();
    Cel cel =
        standardCelBuilderWithMacros()
            .addFileTypes(CheckedExpr.getDescriptor().getFile())
            // In this test 'ParsedExpr' is defined in syntax.proto, but the descriptor provided is
            // defined in checked.proto. Because the `ParsedExpr` type is not referenced, it is not
            // available for use within CEL when deep type resolution is disabled.
            .setOptions(CelOptions.current().resolveTypeDependencies(false).build())
            .setContainer(packageName)
            .setResultType(StructTypeReference.create(packageName + ".ParsedExpr"))
            .build();
    CelValidationException e =
        Assert.assertThrows(
            CelValidationException.class, () -> cel.compile("ParsedExpr{}").getAst());
    assertThat(e).hasMessageThat().contains("undeclared reference to 'ParsedExpr'");
  }

  @Test
  public void program_deepTypeResolution() throws Exception {
    String packageName = CheckedExpr.getDescriptor().getFile().getPackage();
    Cel cel =
        standardCelBuilderWithMacros()
            .addFileTypes(CheckedExpr.getDescriptor().getFile())
            // In this test 'ParsedExpr' is defined in syntax.proto, but the descriptor provided is
            // defined in checked.proto. Because deep type dependency resolution is enabled, the
            // `ParsedExpr` may be used within CEL.
            .setOptions(CelOptions.current().resolveTypeDependencies(true).build())
            .setContainer(packageName)
            .setResultType(StructTypeReference.create(packageName + ".ParsedExpr"))
            .build();
    CelRuntime.Program program = cel.createProgram(cel.compile("ParsedExpr{}").getAst());
    assertThat(program.eval()).isEqualTo(ParsedExpr.getDefaultInstance());
  }

  @Test
  public void program_deepTypeResolutionEnabledForRuntime_success() throws Exception {
    String packageName = CheckedExpr.getDescriptor().getFile().getPackage();
    CelCompiler celCompiler =
        CelCompilerFactory.standardCelCompilerBuilder()
            .addFileTypes(ParsedExpr.getDescriptor().getFile())
            .setResultType(StructTypeReference.create(packageName + ".ParsedExpr"))
            .setContainer(packageName)
            .build();

    CelAbstractSyntaxTree ast = celCompiler.compile("ParsedExpr{}").getAst();

    CelRuntime celRuntime =
        CelRuntimeFactory.standardCelRuntimeBuilder()
            .addFileTypes(CheckedExpr.getDescriptor().getFile())
            .setOptions(CelOptions.current().resolveTypeDependencies(true).build())
            .build();
    CelRuntime.Program program = celRuntime.createProgram(ast);

    // 'ParsedExpr' is defined in syntax.proto but the descriptor provided to the runtime is from
    // 'checked.proto'.
    // 'ParsedExpr' is transitively available for use because deep type resolution is enabled.
    assertThat(program.eval()).isEqualTo(ParsedExpr.getDefaultInstance());
  }

  @Test
  public void program_deepTypeResolutionDisabledForRuntime_fails() throws Exception {
    String packageName = CheckedExpr.getDescriptor().getFile().getPackage();
    CelCompiler celCompiler =
        CelCompilerFactory.standardCelCompilerBuilder()
            .addFileTypes(CheckedExpr.getDescriptor().getFile())
            .setOptions(CelOptions.current().resolveTypeDependencies(true).build())
            .setResultType(StructTypeReference.create(packageName + ".ParsedExpr"))
            .setContainer(packageName)
            .build();

    // 'ParsedExpr' is defined in syntax.proto but the descriptor provided is from 'checked.proto'.
    // 'ParsedExpr' is transitively available for use because deep type resolution is enabled.
    CelAbstractSyntaxTree ast = celCompiler.compile("ParsedExpr{}").getAst();

    CelRuntime celRuntime =
        CelRuntimeFactory.standardCelRuntimeBuilder()
            .addFileTypes(CheckedExpr.getDescriptor().getFile())
            .setOptions(CelOptions.current().resolveTypeDependencies(false).build())
            .build();
    CelRuntime.Program program = celRuntime.createProgram(ast);

    // In this case, linked types are disabled so the same descriptors
    // provided to the CelCompiler must also be provided into the runtime.
    // As deep type resolution is disabled, 'ParsedExpr' is not available for use in runtime so an
    // error is thrown.
    CelEvaluationException e = Assert.assertThrows(CelEvaluationException.class, program::eval);
    assertThat(e)
        .hasMessageThat()
        .contains(String.format("cannot resolve '%s.ParsedExpr' as a message", packageName));
  }

  @Test
  @SuppressWarnings("deprecation") // Test for existing deprecated method setTypeProvider
  public void program_typeProvider() throws Exception {
    Cel cel =
        standardCelBuilderWithMacros()
            .setTypeProvider(
                new DescriptorTypeProvider(ImmutableList.of(Timestamp.getDescriptor())))
            .setContainer("google")
            .setResultType(SimpleType.TIMESTAMP)
            .build();
    CelRuntime.Program program =
        cel.createProgram(cel.compile("protobuf.Timestamp{seconds: 12}").getAst());
    assertThat(program.eval()).isEqualTo(Timestamps.fromSeconds(12));
  }

  @Test
  public void program_protoActivation() throws Exception {
    Cel cel =
        standardCelBuilderWithMacros()
            .addMessageTypes(AttributeContext.getDescriptor())
            .addDeclarations(
                Decl.newBuilder()
                    .setName("resource")
                    .setIdent(
                        IdentDecl.newBuilder()
                            .setType(
                                CelTypes.createMessage(
                                    "google.rpc.context.AttributeContext.Resource")))
                    .build())
            .setResultType(SimpleType.STRING)
            .build();
    CelRuntime.Program program = cel.createProgram(cel.compile("resource.name").getAst());
    assertThat(
            program.eval(
                AttributeContext.newBuilder()
                    .setResource(AttributeContext.Resource.newBuilder().setName("test/name"))
                    .build()))
        .isEqualTo("test/name");
  }

  @Test
  @TestParameters("{resolveTypeDependencies: false}")
  @TestParameters("{resolveTypeDependencies: true}")
  public void program_enumTypeDirectResolution(boolean resolveTypeDependencies) throws Exception {
    Cel cel =
        standardCelBuilderWithMacros()
            .addFileTypes(StandaloneGlobalEnum.getDescriptor().getFile())
            .setOptions(
                CelOptions.current().resolveTypeDependencies(resolveTypeDependencies).build())
            .setContainer("dev.cel.testing.testdata.proto3.StandaloneGlobalEnum")
            .setResultType(SimpleType.BOOL)
            .build();

    // Providing an enum proto file directly should not cause an error
    // regardless of the resolveTypeDependencies settings
    StandaloneGlobalEnum testEnum = StandaloneGlobalEnum.SGAR;
    CelRuntime.Program program =
        cel.createProgram(
            cel.compile(String.format("%s == %d", testEnum, testEnum.getNumber())).getAst());
    assertThat(program.eval()).isEqualTo(true);
  }

  @Test
  @TestParameters("{resolveTypeDependencies: false}")
  @TestParameters("{resolveTypeDependencies: true}")
  public void program_enumTypeReferenceResolution(boolean resolveTypeDependencies)
      throws Exception {
    Cel cel =
        standardCelBuilderWithMacros()
            .setOptions(
                CelOptions.current().resolveTypeDependencies(resolveTypeDependencies).build())
            .addMessageTypes(Struct.getDescriptor())
            .setResultType(StructTypeReference.create("google.protobuf.NullValue"))
            .setContainer("google.protobuf")
            .build();

    // `Value` is defined in `Struct` proto and NullValue is an enum within this `Value` struct.
    // The following evaluation should work regardless of resolveTypeDependencies settings
    // as the enum definition is found in the same `Struct` proto definition.
    CelRuntime.Program program =
        cel.createProgram(cel.compile("Value{null_value: NullValue.NULL_VALUE}").getAst());
    assertThat(program.eval()).isEqualTo(NullValue.NULL_VALUE);
  }

  @Test
  public void program_enumTypeTransitiveResolution() throws Exception {
    Cel cel =
        standardCelBuilderWithMacros()
            .setOptions(CelOptions.current().resolveTypeDependencies(true).build())
            .addMessageTypes(TestAllTypes.getDescriptor())
            .setResultType(StructTypeReference.create("google.protobuf.NullValue"))
            .setContainer("google.protobuf")
            .build();

    // 'Value' is a struct defined as a dependency of messages_proto2.proto and 'NullValue' is an
    // enum within this 'Value' struct.
    // As deep type dependency is enabled, the following evaluation should work by as the
    // 'NullValue' enum type is transitively discovered

    CelRuntime.Program program =
        cel.createProgram(cel.compile("Value{null_value: NullValue.NULL_VALUE}").getAst());
    assertThat(program.eval()).isEqualTo(NullValue.NULL_VALUE);
  }

  @Test
  public void compile_enumTypeIsEquivalentToInt() throws Exception {
    Cel cel =
        standardCelBuilderWithMacros()
            .addFileTypes(StandaloneGlobalEnum.getDescriptor().getFile())
            .addVar("enumVar", EnumType.create("enum", ImmutableMap.of("FOO", 0, "BAR", 1)))
            .setResultType(SimpleType.BOOL)
            .build();

    CelAbstractSyntaxTree ast = cel.compile("enumVar == 1").getAst();

    assertThat(ast).isNotNull();
  }

  @Test
  public void compile_enumTypeTransitiveResolutionFailure() {
    Cel cel =
        standardCelBuilderWithMacros()
            .setOptions(CelOptions.current().resolveTypeDependencies(false).build())
            .addMessageTypes(TestAllTypes.getDescriptor())
            .setResultType(StructTypeReference.create("google.protobuf.NullValue"))
            .setContainer("google.protobuf")
            .build();

    // 'Value' is a struct defined as a dependency of messages_proto2.proto and 'NullValue' is an
    // enum within this 'Value' struct.
    // As deep type dependency is disabled, the following evaluation will fail as CEL will not be
    // aware of the dependent enum type
    CelValidationException e =
        Assert.assertThrows(
            CelValidationException.class,
            () -> cel.compile("Value{null_value: NullValue.NULL_VALUE}").getAst());
    assertThat(e).hasMessageThat().contains("undeclared reference to 'NullValue'");
  }

  @Test
  public void compile_multipleInstancesOfEnumDescriptor_dedupedByFullName() throws Exception {
    String enumTextProto =
        "name: \"standalone_global_enum.proto\"\n"
            + "package: \"dev.cel.testing.testdata.proto3\"\n"
            + "enum_type {\n"
            + "  name: \"StandaloneGlobalEnum\"\n"
            + "  value {\n"
            + "    name: \"SGOO\"\n"
            + "    number: 0\n"
            + "  }\n"
            + "}\n"
            + "syntax: \"proto3\"\n";
    FileDescriptorProto enumFileDescriptorProto =
        TextFormat.parse(enumTextProto, FileDescriptorProto.class);
    FileDescriptor enumFileDescriptor =
        FileDescriptor.buildFrom(enumFileDescriptorProto, new FileDescriptor[] {});
    Cel cel =
        standardCelBuilderWithMacros()
            .setContainer("dev.cel.testing.testdata")
            .addFileTypes(enumFileDescriptor)
            .addFileTypes(StandaloneGlobalEnum.getDescriptor().getFile())
            .build();

    assertThat(cel.compile("dev.cel.testing.testdata.proto3.StandaloneGlobalEnum.SGOO").getAst())
        .isNotNull();
  }

  @Test
  public void program_customVarResolver() throws Exception {
    Cel cel =
        standardCelBuilderWithMacros()
            .addDeclarations(
                Decl.newBuilder()
                    .setName("variable")
                    .setIdent(IdentDecl.newBuilder().setType(CelTypes.STRING))
                    .build())
            .setResultType(SimpleType.BOOL)
            .build();
    CelRuntime.Program program = cel.createProgram(cel.compile("variable == 'hello'").getAst());
    assertThat(
            program.eval(
                (name) -> name.equals("variable") ? Optional.of("hello") : Optional.empty()))
        .isEqualTo(true);
    assertThat(program.eval((name) -> Optional.of(""))).isEqualTo(false);
  }

  @Test
  public void program_wrongTypeComprehensionThrows() throws Exception {
    Cel cel = standardCelBuilderWithMacros().setResultType(SimpleType.BOOL).build();
    CelRuntime.Program program =
        cel.createProgram(cel.compile("dyn(42).exists(x, x != 'foo')").getAst());

    CelEvaluationException e = assertThrows(CelEvaluationException.class, program::eval);

    assertThat(e).hasMessageThat().contains("expected a list or a map");
  }

  @Test
  public void program_stringFormatInjection_throwsEvaluationException() throws Exception {
    Cel cel = standardCelBuilderWithMacros().build();
    CelRuntime.Program program = cel.createProgram(cel.compile("{}['%2000222222s']").getAst());

    CelEvaluationException e = assertThrows(CelEvaluationException.class, program::eval);
    assertThat(e).hasMessageThat().contains("evaluation error");
  }

  @Test
  public void program_emptyTypeProviderConfig() throws Exception {
    Cel cel = standardCelBuilderWithMacros().build();
    assertThat(cel.createProgram(cel.compile("true && !false").getAst()).eval()).isEqualTo(true);
  }

  @Test
  public void program_messageTypeAddedAsVarWithoutDescriptor_throwsHumanReadableError() {
    String packageName = CheckedExpr.getDescriptor().getFile().getPackage();
    Cel cel =
        standardCelBuilderWithMacros()
            .addVar("parsedExprVar", CelTypes.createMessage(ParsedExpr.getDescriptor()))
            .build();
    CelValidationException exception =
        assertThrows(
            CelValidationException.class,
            () -> cel.createProgram(cel.compile("parsedExprVar.source_info").getAst()));
    assertThat(exception)
        .hasMessageThat()
        .contains(
            String.format(
                "Message type resolution failure while referencing field 'source_info'. Ensure that"
                    + " the descriptor for type '%s.ParsedExpr' was added to the environment",
                packageName));
  }

  @Test
  public void setOptions() throws Exception {
    Cel cel = standardCelBuilderWithMacros().build();
    CelValidationResult result = cel.parse("!!!true");
    assertThat(result.hasError()).isFalse();
    assertThat(CelProtoAbstractSyntaxTree.fromCelAst(result.getAst()).toParsedExpr().getExpr())
        .ignoringFieldDescriptors(Expr.getDescriptor().findFieldByName("id"))
        .isEqualTo(NOT_EXPR);

    cel =
        standardCelBuilderWithMacros()
            .setOptions(CelOptions.newBuilder().retainRepeatedUnaryOperators(true).build())
            .build();
    result = cel.parse("!!!true");
    assertThat(result.hasError()).isFalse();
    assertThat(CelProtoAbstractSyntaxTree.fromCelAst(result.getAst()).toParsedExpr().getExpr())
        .ignoringFieldDescriptors(Expr.getDescriptor().findFieldByName("id"))
        .isEqualTo(NOT_NOT_NOT_EXPR);
  }

  @Test
  public void setStandardMacros() throws Exception {
    Cel cel = standardCelBuilderWithMacros().setStandardMacros(CelStandardMacro.HAS).build();
    assertValidationResult(cel.parse("has(a.b)"), PARSED_HAS_EXPR);
  }

  private void assertValidationResult(CelValidationResult result, ParsedExpr parsedExpr)
      throws Exception {
    assertThat(result.hasError()).isFalse();
    assertThat(result.getAllIssues()).isEmpty();
    assertThat(CelProtoAbstractSyntaxTree.fromCelAst(result.getAst()).toParsedExpr())
        .ignoringFieldDescriptors(
            Expr.getDescriptor().findFieldByName("id"),
            ParsedExpr.getDescriptor().findFieldByName("source_info"))
        .reportingMismatchesOnly()
        .isEqualTo(parsedExpr);
  }

  private void assertValidationResult(CelValidationResult result, CheckedExpr checkedExpr)
      throws Exception {
    assertThat(result.hasError()).isFalse();
    assertThat(CelProtoAbstractSyntaxTree.fromCelAst(result.getAst()).toCheckedExpr())
        .ignoringFieldDescriptors(
            Expr.getDescriptor().findFieldByName("id"),
            CheckedExpr.getDescriptor().findFieldByName("source_info"))
        .reportingMismatchesOnly()
        .isEqualTo(checkedExpr);
  }

  private CelVariableResolver fromMap(ImmutableMap<String, ?> m) {
    return (String s) -> Optional.ofNullable(m.get(s));
  }

  @Test
  public void programAdvanceEvaluation_unknownsBasic() throws Exception {
    Cel cel =
        standardCelBuilderWithMacros()
            .setOptions(CelOptions.current().enableUnknownTracking(true).build())
            .addVar("a", SimpleType.BOOL)
            .addVar("b", SimpleType.BOOL)
            .addFunctionBindings()
            .setResultType(SimpleType.BOOL)
            .build();
    CelRuntime.Program program = cel.createProgram(cel.compile("a || b").getAst());
    assertThat(
            program.eval(
                ImmutableMap.of(
                    "a", true,
                    "b", false)))
        .isEqualTo(true);

    ImmutableMap<String, Object> partial = ImmutableMap.of("b", false);

    assertThat(
            program.advanceEvaluation(
                UnknownContext.create(
                    fromMap(partial),
                    ImmutableList.of(CelAttributePattern.fromQualifiedIdentifier("a")))))
        .isEqualTo(CelUnknownSet.create(CelAttribute.fromQualifiedIdentifier("a")));
  }

  @Test
  public void programAdvanceEvaluation_attributesIgnoredIfDisabled() throws Exception {
    Cel cel =
        standardCelBuilderWithMacros()
            .setOptions(CelOptions.current().enableUnknownTracking(false).build())
            .addVar("a", SimpleType.BOOL)
            .addVar("b", SimpleType.BOOL)
            .addFunctionBindings()
            .setResultType(SimpleType.BOOL)
            .build();
    CelRuntime.Program program = cel.createProgram(cel.compile("a || b").getAst());

    assertThat(
            program.advanceEvaluation(
                UnknownContext.create(
                    fromMap(
                        ImmutableMap.of(
                            "a", true,
                            "b", false)),
                    ImmutableList.of(CelAttributePattern.fromQualifiedIdentifier("a")))))
        .isEqualTo(true);
  }

  @Test
  public void programAdvanceEvaluation_logicOperatorTypeMismatchThrows() throws Exception {
    Cel cel =
        standardCelBuilderWithMacros()
            .setOptions(CelOptions.current().enableUnknownTracking(true).build())
            .addVar("a", SimpleType.BOOL)
            .addFunctionBindings()
            .setResultType(SimpleType.BOOL)
            .build();
    CelRuntime.Program program = cel.createProgram(cel.compile("a || dyn(42)").getAst());

    CelEvaluationException e =
        assertThrows(
            CelEvaluationException.class,
            () ->
                program.advanceEvaluation(
                    UnknownContext.create(
                        fromMap(ImmutableMap.of()),
                        ImmutableList.of(CelAttributePattern.fromQualifiedIdentifier("a")))));
    assertThat(e).hasMessageThat().contains("expected boolean");
  }

  @Test
  public void programAdvanceEvaluation_unknownsCollection() throws Exception {
    Cel cel =
        standardCelBuilderWithMacros()
            .setOptions(CelOptions.current().enableUnknownTracking(true).build())
            .addVar("a", SimpleType.BOOL)
            .addVar("b", SimpleType.BOOL)
            .addFunctionBindings()
            .setResultType(SimpleType.BOOL)
            .build();
    CelRuntime.Program program = cel.createProgram(cel.compile("a || b").getAst());
    assertThat(
            program.eval(
                ImmutableMap.of(
                    "a", true,
                    "b", false)))
        .isEqualTo(true);

    ImmutableMap<String, Object> partial = ImmutableMap.of("b", false);

    assertThat(
            program.advanceEvaluation(
                UnknownContext.create(
                    fromMap(partial),
                    ImmutableList.of(
                        CelAttributePattern.create("a"), CelAttributePattern.create("b")))))
        .isEqualTo(
            CelUnknownSet.create(
                ImmutableSet.of(CelAttribute.create("a"), CelAttribute.create("b"))));
  }

  @Test
  public void programAdvanceEvaluation_unknownsNamespaceSupport() throws Exception {
    Cel cel =
        standardCelBuilderWithMacros()
            .setOptions(CelOptions.current().enableUnknownTracking(true).build())
            .addVar("com.google.a", SimpleType.BOOL)
            .addVar("com.google.b", SimpleType.BOOL)
            .setContainer("com.google")
            .addFunctionBindings()
            .setResultType(SimpleType.BOOL)
            .build();
    CelRuntime.Program program = cel.createProgram(cel.compile("a || b").getAst());
    assertThat(
            program.eval(
                ImmutableMap.of(
                    "com.google.a", true,
                    "com.google.b", false)))
        .isEqualTo(true);

    ImmutableMap<String, Object> partial = ImmutableMap.of("com.google.b", false);

    assertThat(
            program.advanceEvaluation(
                UnknownContext.create(
                    fromMap(partial),
                    ImmutableList.of(CelAttributePattern.fromQualifiedIdentifier("com.google.a")))))
        .isEqualTo(CelUnknownSet.create(CelAttribute.fromQualifiedIdentifier("com.google.a")));
  }

  @Test
  public void programAdvanceEvaluation_unknownsIterativeEvalExample() throws Exception {
    Cel cel =
        standardCelBuilderWithMacros()
            .setOptions(CelOptions.current().enableUnknownTracking(true).build())
            .addVar("com.google.a", SimpleType.BOOL)
            .addVar("com.google.b", SimpleType.BOOL)
            .setContainer("com.google")
            .addFunctionBindings()
            .setResultType(SimpleType.BOOL)
            .build();
    CelRuntime.Program program = cel.createProgram(cel.compile("a || b").getAst());
    UnknownContext context =
        UnknownContext.create(
            fromMap(ImmutableMap.of("com.google.b", false)),
            ImmutableList.of(CelAttributePattern.fromQualifiedIdentifier("com.google.a")));

    assertThat(program.advanceEvaluation(context))
        .isEqualTo(CelUnknownSet.create(CelAttribute.fromQualifiedIdentifier("com.google.a")));

    assertThat(
            program.advanceEvaluation(
                context.withResolvedAttributes(
                    ImmutableMap.of(CelAttribute.fromQualifiedIdentifier("com.google.a"), true))))
        .isEqualTo(true);
  }

  @Test
  public void programAdvanceEvaluation_nestedSelect() throws Exception {
    Cel cel =
        standardCelBuilderWithMacros()
            .setOptions(CelOptions.current().enableUnknownTracking(true).build())
            .addVar("com", MapType.create(SimpleType.STRING, SimpleType.DYN))
            .addFunctionBindings()
            .setResultType(SimpleType.BOOL)
            .build();
    CelRuntime.Program program = cel.createProgram(cel.compile("com.google.a || false").getAst());

    assertThat(
            program.advanceEvaluation(
                UnknownContext.create(
                    fromMap(ImmutableMap.of()),
                    ImmutableList.of(CelAttributePattern.fromQualifiedIdentifier("com.google.a")))))
        .isEqualTo(CelUnknownSet.create(CelAttribute.fromQualifiedIdentifier("com.google.a")));
  }

  @Test
  public void programAdvanceEvaluation_nestedSelect_withCelValue() throws Exception {
    Cel cel =
        standardCelBuilderWithMacros()
            .setOptions(
                CelOptions.current().enableUnknownTracking(true).enableCelValue(true).build())
            .addVar("com", MapType.create(SimpleType.STRING, SimpleType.DYN))
            .addFunctionBindings()
            .setResultType(SimpleType.BOOL)
            .build();
    CelRuntime.Program program = cel.createProgram(cel.compile("com.google.a || false").getAst());

    assertThat(
            program.advanceEvaluation(
                UnknownContext.create(
                    fromMap(ImmutableMap.of()),
                    ImmutableList.of(CelAttributePattern.fromQualifiedIdentifier("com.google.a")))))
        .isEqualTo(CelUnknownSet.create(CelAttribute.fromQualifiedIdentifier("com.google.a")));
  }

  @Test
  public void programAdvanceEvaluation_argumentMergeErrorPriority() throws Exception {
    Cel cel =
        standardCelBuilderWithMacros()
            .setOptions(CelOptions.current().enableUnknownTracking(true).build())
            .addVar("unk", SimpleType.BOOL)
            .addDeclarations(
                Decl.newBuilder()
                    .setName("acceptThreeBoolArgs")
                    .setFunction(
                        FunctionDecl.newBuilder()
                            .addOverloads(
                                Overload.newBuilder()
                                    .setOverloadId("acceptThreeBoolArgs")
                                    .addParams(Type.newBuilder().setPrimitive(PrimitiveType.BOOL))
                                    .addParams(Type.newBuilder().setPrimitive(PrimitiveType.BOOL))
                                    .addParams(Type.newBuilder().setPrimitive(PrimitiveType.BOOL))
                                    .setResultType(
                                        Type.newBuilder().setPrimitive(PrimitiveType.BOOL))))
                    .build())
            .setContainer("")
            .addFunctionBindings()
            .setResultType(SimpleType.BOOL)
            .build();

    // The type signature at check time is acceptThreeBoolArgs(bool, bool, bool)
    // at runtime, it's acceptThreeBoolArgs(bool, unknown, error).
    // For a strict function, an error argument should propagate instead of dispatching to the
    // implementation.
    CelRuntime.Program program =
        cel.createProgram(cel.compile("acceptThreeBoolArgs(false, unk, [false][1])").getAst());

    Assert.assertThrows(
        CelEvaluationException.class,
        () ->
            program.advanceEvaluation(
                UnknownContext.create(
                    fromMap(ImmutableMap.of()),
                    ImmutableList.of(CelAttributePattern.fromQualifiedIdentifier("unk")))));
  }

  @Test
  public void programAdvanceEvaluation_argumentMergeUnknowns() throws Exception {
    Cel cel =
        standardCelBuilderWithMacros()
            .setOptions(CelOptions.current().enableUnknownTracking(true).build())
            .addVar("unk.a", SimpleType.BOOL)
            .addVar("unk.b", SimpleType.BOOL)
            .addVar("unk.c", SimpleType.BOOL)
            .addDeclarations(
                Decl.newBuilder()
                    .setName("acceptThreeBoolArgs")
                    .setFunction(
                        FunctionDecl.newBuilder()
                            .addOverloads(
                                Overload.newBuilder()
                                    .setOverloadId("acceptThreeBoolArgs")
                                    .addParams(Type.newBuilder().setPrimitive(PrimitiveType.BOOL))
                                    .addParams(Type.newBuilder().setPrimitive(PrimitiveType.BOOL))
                                    .addParams(Type.newBuilder().setPrimitive(PrimitiveType.BOOL))
                                    .setResultType(
                                        Type.newBuilder().setPrimitive(PrimitiveType.BOOL))))
                    .build())
            .setContainer("")
            .addFunctionBindings()
            .setResultType(SimpleType.BOOL)
            .build();
    CelRuntime.Program program =
        cel.createProgram(cel.compile("acceptThreeBoolArgs(unk.a, unk.b, unk.c)").getAst());

    assertThat(
            program.advanceEvaluation(
                UnknownContext.create(
                    fromMap(ImmutableMap.of()),
                    ImmutableList.of(
                        CelAttributePattern.create("unk").qualify(Qualifier.ofWildCard())))))
        .isEqualTo(
            CelUnknownSet.create(
                ImmutableSet.of(
                    CelAttribute.fromQualifiedIdentifier("unk.a"),
                    CelAttribute.fromQualifiedIdentifier("unk.c"),
                    CelAttribute.fromQualifiedIdentifier("unk.b"))));
  }

  @Test
  public void programAdvanceEvaluation_mapSelectUnknowns() throws Exception {
    Cel cel =
        standardCelBuilderWithMacros()
            .setOptions(CelOptions.current().enableUnknownTracking(true).build())
            .addVar("unk", MapType.create(SimpleType.STRING, SimpleType.BOOL))
            .setContainer("")
            .addFunctionBindings()
            .setResultType(SimpleType.BOOL)
            .build();
    CelRuntime.Program program = cel.createProgram(cel.compile("unk.a || unk.b || unk.c").getAst());

    assertThat(
            program.advanceEvaluation(
                UnknownContext.create(
                    fromMap(ImmutableMap.of()),
                    ImmutableList.of(
                        CelAttributePattern.create("unk").qualify(Qualifier.ofWildCard())))))
        .isEqualTo(
            CelUnknownSet.create(
                ImmutableSet.of(
                    CelAttribute.fromQualifiedIdentifier("unk.a"),
                    CelAttribute.fromQualifiedIdentifier("unk.c"),
                    CelAttribute.fromQualifiedIdentifier("unk.b"))));
  }

  @Test
  public void programAdvanceEvaluation_mapIndexUnknowns() throws Exception {
    Cel cel =
        standardCelBuilderWithMacros()
            .setOptions(CelOptions.current().enableUnknownTracking(true).build())
            .addVar("unk", MapType.create(SimpleType.STRING, SimpleType.BOOL))
            .setContainer("")
            .addFunctionBindings()
            .setResultType(SimpleType.BOOL)
            .build();

    CelRuntime.Program program =
        cel.createProgram(cel.compile("unk['a'] || unk['b'] || unk['c']").getAst());

    assertThat(
            program.advanceEvaluation(
                UnknownContext.create(
                    fromMap(ImmutableMap.of()),
                    ImmutableList.of(
                        CelAttributePattern.fromQualifiedIdentifier("unk")
                            .qualify(Qualifier.ofWildCard())))))
        .isEqualTo(
            CelUnknownSet.create(
                ImmutableSet.of(
                    CelAttribute.fromQualifiedIdentifier("unk.a"),
                    CelAttribute.fromQualifiedIdentifier("unk.c"),
                    CelAttribute.fromQualifiedIdentifier("unk.b"))));
  }

  @Test
  public void programAdvanceEvaluation_listIndexUnknowns() throws Exception {
    Cel cel =
        standardCelBuilderWithMacros()
            .setOptions(CelOptions.current().enableUnknownTracking(true).build())
            .addVar("unk", ListType.create(SimpleType.BOOL))
            .setContainer("")
            .addFunctionBindings()
            .setResultType(SimpleType.BOOL)
            .build();

    CelRuntime.Program program =
        cel.createProgram(cel.compile("unk[0] || unk[1] || unk[2]").getAst());

    assertThat(
            program.advanceEvaluation(
                UnknownContext.create(
                    fromMap(ImmutableMap.of()),
                    ImmutableList.of(
                        CelAttributePattern.fromQualifiedIdentifier("unk")
                            .qualify(Qualifier.ofWildCard())))))
        .isEqualTo(
            CelUnknownSet.create(
                ImmutableSet.of(
                    CelAttribute.create("unk").qualify(Qualifier.ofInt(0)),
                    CelAttribute.create("unk").qualify(Qualifier.ofInt(1)),
                    CelAttribute.create("unk").qualify(Qualifier.ofInt(2)))));
  }

  @Test
  public void programAdvanceEvaluation_indexOnUnknownContainer() throws Exception {
    Cel cel =
        standardCelBuilderWithMacros()
            .setOptions(CelOptions.current().enableUnknownTracking(true).build())
            .addVar("unk", ListType.create(SimpleType.BOOL))
            .setContainer("")
            .addFunctionBindings()
            .setResultType(SimpleType.BOOL)
            .build();

    CelRuntime.Program program =
        cel.createProgram(cel.compile("unk[0] || unk[1] || unk[2]").getAst());

    assertThat(
            program.advanceEvaluation(
                UnknownContext.create(
                    fromMap(ImmutableMap.of()),
                    ImmutableList.of(CelAttributePattern.fromQualifiedIdentifier("unk")))))
        .isEqualTo(CelUnknownSet.create(ImmutableSet.of(CelAttribute.create("unk"))));
  }

  @Test
  public void programAdvanceEvaluation_unsupportedIndexIgnored() throws Exception {
    Cel cel =
        standardCelBuilderWithMacros()
            .setOptions(CelOptions.current().enableUnknownTracking(true).build())
            .addVar("unk", MapType.create(SimpleType.STRING, SimpleType.BOOL))
            .setContainer("")
            .addFunctionBindings()
            .setResultType(SimpleType.BOOL)
            .build();

    CelRuntime.Program program = cel.createProgram(cel.compile("unk[dyn(b'a')]").getAst());

    assertThat(
            program.advanceEvaluation(
                UnknownContext.create(
                    fromMap(ImmutableMap.of()),
                    ImmutableList.of(
                        CelAttributePattern.fromQualifiedIdentifier("unk")
                            .qualify(Qualifier.ofWildCard())))))
        .isEqualTo(
            CelUnknownSet.create(
                ImmutableSet.of(
                    // Map is partially unknown, but we can't provide a more specific attribute if
                    // the index is of an unsupported type.
                    CelAttribute.fromQualifiedIdentifier("unk"))));

    assertThat(
            program.advanceEvaluation(
                UnknownContext.create(
                    fromMap(
                        ImmutableMap.of(
                            "unk", ImmutableMap.of(ByteString.copyFromUtf8("a"), false))),
                    ImmutableList.of())))
        .isEqualTo(false);
  }

  @Test
  public void programAdvanceEvaluation_listIndexMacroTracking() throws Exception {
    Cel cel =
        standardCelBuilderWithMacros()
            .setOptions(CelOptions.current().enableUnknownTracking(true).build())
            .addVar("testList", ListType.create(SimpleType.BOOL))
            .setContainer("")
            .addFunctionBindings()
            .setResultType(SimpleType.BOOL)
            .build();

    CelRuntime.Program program =
        cel.createProgram(cel.compile("testList.all(x, x == true)").getAst());

    assertThat(
            program.advanceEvaluation(
                UnknownContext.create(
                    fromMap(ImmutableMap.of("testList", ImmutableList.of(true, true, false))),
                    ImmutableList.of(
                        CelAttributePattern.create("testList").qualify(Qualifier.ofInt(2))))))
        .isEqualTo(
            CelUnknownSet.create(
                ImmutableSet.of(CelAttribute.create("testList").qualify(Qualifier.ofInt(2)))));

    assertThat(
            program.advanceEvaluation(
                UnknownContext.create(
                    fromMap(ImmutableMap.of("testList", ImmutableList.of(true, false, false))),
                    ImmutableList.of(
                        CelAttributePattern.create("testList").qualify(Qualifier.ofInt(2))))))
        .isEqualTo(false);
  }

  @Test
  public void programAdvanceEvaluation_mapIndexMacroTracking() throws Exception {
    Cel cel =
        standardCelBuilderWithMacros()
            .setOptions(CelOptions.current().enableUnknownTracking(true).build())
            .addVar("testMap", MapType.create(SimpleType.STRING, SimpleType.BOOL))
            .setContainer("")
            .addFunctionBindings()
            .setResultType(SimpleType.BOOL)
            .build();

    CelRuntime.Program program =
        cel.createProgram(cel.compile("testMap.all(x, testMap[x] == true)").getAst());

    assertThat(
            program.advanceEvaluation(
                UnknownContext.create(
                    fromMap(
                        ImmutableMap.of(
                            "testMap",
                            ImmutableMap.of(
                                "key1", true,
                                "key2", true,
                                "key3", false))),
                    ImmutableList.of(
                        CelAttributePattern.create("testMap")
                            .qualify(Qualifier.ofString("key3"))))))
        .isEqualTo(
            CelUnknownSet.create(
                ImmutableSet.of(
                    CelAttribute.create("testMap").qualify(Qualifier.ofString("key3")))));

    assertThat(
            program.advanceEvaluation(
                UnknownContext.create(
                    fromMap(
                        ImmutableMap.of(
                            "testMap",
                            ImmutableMap.of(
                                "key1", true,
                                "key2", false,
                                "key3", false))),
                    ImmutableList.of(
                        CelAttributePattern.create("testMap")
                            .qualify(Qualifier.ofString("key3"))))))
        .isEqualTo(false);
  }

  @Test
  public void programAdvanceEvaluation_boolOperatorMergeUnknownPriority() throws Exception {
    Cel cel =
        standardCelBuilderWithMacros()
            .setOptions(CelOptions.current().enableUnknownTracking(true).build())
            .addVarDeclarations(
                CelVarDecl.newVarDeclaration("unk", SimpleType.BOOL),
                CelVarDecl.newVarDeclaration("err", SimpleType.BOOL))
            .setContainer("com.google")
            .addFunctionBindings()
            .setResultType(SimpleType.BOOL)
            .build();
    CelRuntime.Program program = cel.createProgram(cel.compile("unk || err").getAst());
    assertThat(program.eval(ImmutableMap.of("unk", true))).isEqualTo(true);

    assertThat(
            program.advanceEvaluation(
                UnknownContext.create(
                    fromMap(ImmutableMap.of()),
                    ImmutableList.of(CelAttributePattern.fromQualifiedIdentifier("unk")))))
        .isEqualTo(CelUnknownSet.create(CelAttribute.fromQualifiedIdentifier("unk")));
  }

  @Test
  public void programAdvanceEvaluation_partialUnknownMapEntryPropagates() throws Exception {
    Cel cel =
        standardCelBuilderWithMacros()
            .setOptions(CelOptions.current().enableUnknownTracking(true).build())
            .addVarDeclarations(
                ImmutableList.of(
                    CelVarDecl.newVarDeclaration("partialList1", ListType.create(SimpleType.INT)),
                    CelVarDecl.newVarDeclaration("partialList2", ListType.create(SimpleType.INT))))
            .setContainer("com.google")
            .addFunctionBindings()
            .build();
    CelRuntime.Program program =
        cel.createProgram(
            cel.compile("{'key1': partialList1, 'key2': partialList2, 'key3': [1, 2, 3]}")
                .getAst());

    assertThat(
            program.advanceEvaluation(
                UnknownContext.create(
                    fromMap(
                        ImmutableMap.of(
                            "partialList1", ImmutableList.of(1, 2),
                            "partialList2", ImmutableList.of(1, 2))),
                    ImmutableList.of(
                        CelAttributePattern.create("partialList1").qualify(Qualifier.ofInt(1)),
                        CelAttributePattern.create("partialList2").qualify(Qualifier.ofInt(0))))))
        .isEqualTo(
            CelUnknownSet.create(
                ImmutableSet.of(
                    CelAttribute.create("partialList1"), CelAttribute.create("partialList2"))));
  }

  @Test
  public void programAdvanceEvaluation_partialUnknownListElementPropagates() throws Exception {
    Cel cel =
        standardCelBuilderWithMacros()
            .setOptions(CelOptions.current().enableUnknownTracking(true).build())
            .addVar("partialList1", ListType.create(SimpleType.INT))
            .addVar("partialList2", ListType.create(SimpleType.INT))
            .setContainer("com.google")
            .addFunctionBindings()
            .build();
    CelRuntime.Program program =
        cel.createProgram(cel.compile("[partialList1, partialList2, [1, 2, 3]]").getAst());

    assertThat(
            program.advanceEvaluation(
                UnknownContext.create(
                    fromMap(
                        ImmutableMap.of(
                            "partialList1", ImmutableList.of(1, 2),
                            "partialList2", ImmutableList.of(1, 2))),
                    ImmutableList.of(
                        CelAttributePattern.create("partialList1").qualify(Qualifier.ofInt(1)),
                        CelAttributePattern.create("partialList2").qualify(Qualifier.ofInt(0))))))
        .isEqualTo(
            CelUnknownSet.create(
                ImmutableSet.of(
                    CelAttribute.create("partialList1"), CelAttribute.create("partialList2"))));
  }

  @Test
  public void programAdvanceEvaluation_partialUnknownMessageFieldPropagates() throws Exception {
    Cel cel =
        standardCelBuilderWithMacros()
            .setOptions(CelOptions.current().enableUnknownTracking(true).build())
            .addMessageTypes(TestAllTypes.getDescriptor())
            .addVar(
                "partialMessage1",
                StructTypeReference.create("dev.cel.testing.testdata.proto3.TestAllTypes"))
            .addVar(
                "partialMessage2",
                StructTypeReference.create("dev.cel.testing.testdata.proto3.TestAllTypes"))
            .setResultType(
                StructTypeReference.create("dev.cel.testing.testdata.proto3.NestedTestAllTypes"))
            .setContainer("dev.cel.testing.testdata.proto3")
            .addFunctionBindings()
            .build();
    Program program =
        cel.createProgram(
            cel.compile(
                    "NestedTestAllTypes{"
                        + "   child: NestedTestAllTypes{"
                        + "       payload: partialMessage1"
                        + "   }, "
                        + "   payload: partialMessage2"
                        + "}")
                .getAst());

    assertThat(
            program.advanceEvaluation(
                UnknownContext.create(
                    fromMap(
                        ImmutableMap.of(
                            "partialMessage1", TestAllTypes.getDefaultInstance(),
                            "partialMessage2", TestAllTypes.getDefaultInstance())),
                    ImmutableList.of(
                        CelAttributePattern.fromQualifiedIdentifier(
                            "partialMessage1.single_double"),
                        CelAttributePattern.fromQualifiedIdentifier(
                            "partialMessage2.single_int64")))))
        .isEqualTo(
            CelUnknownSet.create(
                ImmutableSet.of(
                    CelAttribute.create("partialMessage1"),
                    CelAttribute.create("partialMessage2"))));
  }

  @Test
  public void program_functionBindingWithCustomType_assignableToRelatedType() throws Exception {
    // A custom type that is assignable to an integer
    CelType customType =
        new CelType() {
          @Override
          public CelKind kind() {
            return CelKind.INT;
          }

          @Override
          public String name() {
            return "customInt";
          }

          @Override
          public boolean isAssignableFrom(CelType other) {
            return super.isAssignableFrom(other) || other.equals(SimpleType.INT);
          }
        };
    Cel cel =
        CelFactory.standardCelBuilder()
            .addVar("x", SimpleType.INT)
            .addFunctionDeclarations(
                newFunctionDeclaration(
                    "print",
                    newGlobalOverload(
                        "print_overload",
                        SimpleType.STRING,
                        customType))) // The overload would accept either Int or CustomType
            .addFunctionBindings(
                CelFunctionBinding.from("print_overload", Long.class, String::valueOf))
            .build();
    CelAbstractSyntaxTree ast = cel.compile("print(x)").getAst();

    String result = (String) cel.createProgram(ast).eval(ImmutableMap.of("x", 5));

    assertThat(result).isEqualTo("5");
  }

  @Test
  @SuppressWarnings("unchecked") // test only
  public void program_functionParamWithWellKnownType() throws Exception {
    Cel cel =
        CelFactory.standardCelBuilder()
            .addFunctionDeclarations(
                newFunctionDeclaration(
                    "hasStringValue",
                    newMemberOverload(
                        "struct_hasStringValue_string_string",
                        SimpleType.BOOL,
                        StructTypeReference.create("google.protobuf.Struct"),
                        SimpleType.STRING,
                        SimpleType.STRING)))
            .addFunctionBindings(
                CelFunctionBinding.from(
                    "struct_hasStringValue_string_string",
                    ImmutableList.of(Map.class, String.class, String.class),
                    args -> {
                      Map<String, String> map = (Map<String, String>) args[0];
                      return map.containsKey(args[1]) && map.containsValue(args[2]);
                    }))
            .build();
    CelAbstractSyntaxTree ast = cel.compile("{'a': 'b'}.hasStringValue('a', 'b')").getAst();

    boolean result = (boolean) cel.createProgram(ast).eval();

    assertThat(result).isTrue();
  }

  @Test
  public void toBuilder_isImmutable() {
    CelBuilder celBuilder = CelFactory.standardCelBuilder();
    CelImpl celImpl = (CelImpl) celBuilder.build();

    CelImpl.Builder newCelBuilder = (CelImpl.Builder) celImpl.toCelBuilder();
    CelParserImpl.Builder newParserBuilder = (CelParserImpl.Builder) celImpl.toParserBuilder();
    CelCheckerLegacyImpl.Builder newCheckerBuilder =
        (CelCheckerLegacyImpl.Builder) celImpl.toCheckerBuilder();
    CelCompilerImpl.Builder newCompilerBuilder =
        (CelCompilerImpl.Builder) celImpl.toCompilerBuilder();
    CelRuntimeLegacyImpl.Builder newRuntimeBuilder =
        (CelRuntimeLegacyImpl.Builder) celImpl.toRuntimeBuilder();

    assertThat(newCelBuilder).isNotEqualTo(celBuilder);
    assertThat(newParserBuilder).isNotEqualTo(celImpl.toParserBuilder());
    assertThat(newCheckerBuilder).isNotEqualTo(celImpl.toCheckerBuilder());
    assertThat(newCompilerBuilder).isNotEqualTo(celImpl.toCompilerBuilder());
    assertThat(newRuntimeBuilder).isNotEqualTo(celImpl.toRuntimeBuilder());
  }

  private static TypeProvider aliasingProvider(ImmutableMap<String, Type> typeAliases) {
    return new TypeProvider() {
      @Override
      public @Nullable Type lookupType(String typeName) {
        Type alias = typeAliases.get(typeName);
        if (alias != null) {
          return CelTypes.create(alias);
        }
        return null;
      }

      @Override
      public @Nullable Integer lookupEnumValue(String enumName) {
        return null;
      }

      @Override
      public TypeProvider.@Nullable FieldType lookupFieldType(Type type, String fieldName) {
        if (typeAliases.containsKey(type.getMessageType())) {
          return TypeProvider.FieldType.of(CelTypes.STRING);
        }
        return null;
      }
    };
  }
}
