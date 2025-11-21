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

package dev.cel.runtime.planner;

import static com.google.common.truth.Truth.assertThat;
import static dev.cel.common.CelFunctionDecl.newFunctionDeclaration;
import static dev.cel.common.CelOverloadDecl.newGlobalOverload;
import static dev.cel.common.CelOverloadDecl.newMemberOverload;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertThrows;

import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.primitives.UnsignedLong;
import com.google.protobuf.DescriptorProtos.FileDescriptorSet;
import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.FileDescriptor;
import com.google.protobuf.ExtensionRegistry;
import com.google.protobuf.Message;
import com.google.testing.junit.testparameterinjector.TestParameter;
import com.google.testing.junit.testparameterinjector.TestParameterInjector;
import com.google.testing.junit.testparameterinjector.TestParameters;
import dev.cel.common.CelAbstractSyntaxTree;
import dev.cel.common.CelDescriptorUtil;
import dev.cel.common.CelErrorCode;
import dev.cel.common.CelOptions;
import dev.cel.common.CelSource;
import dev.cel.common.Operator;
import dev.cel.common.ast.CelExpr;
import dev.cel.common.internal.CelDescriptorPool;
import dev.cel.common.internal.DefaultDescriptorPool;
import dev.cel.common.internal.DefaultMessageFactory;
import dev.cel.common.internal.DynamicProto;
import dev.cel.common.types.CelType;
import dev.cel.common.types.CelTypeProvider;
import dev.cel.common.types.CelTypeProvider.CombinedCelTypeProvider;
import dev.cel.common.types.DefaultTypeProvider;
import dev.cel.common.types.ListType;
import dev.cel.common.types.MapType;
import dev.cel.common.types.OptionalType;
import dev.cel.common.types.ProtoMessageTypeProvider;
import dev.cel.common.types.SimpleType;
import dev.cel.common.types.StructTypeReference;
import dev.cel.common.types.TypeType;
import dev.cel.common.values.CelByteString;
import dev.cel.common.values.CelValueConverter;
import dev.cel.common.values.CelValueProvider;
import dev.cel.common.values.NullValue;
import dev.cel.common.values.ProtoCelValueConverter;
import dev.cel.common.values.ProtoMessageValueProvider;
import dev.cel.compiler.CelCompiler;
import dev.cel.compiler.CelCompilerFactory;
import dev.cel.expr.conformance.proto3.GlobalEnum;
import dev.cel.expr.conformance.proto3.TestAllTypes;
import dev.cel.expr.conformance.proto3.TestAllTypes.NestedMessage;
import dev.cel.extensions.CelExtensions;
import dev.cel.extensions.CelOptionalLibrary;
import dev.cel.parser.CelStandardMacro;
import dev.cel.runtime.CelEvaluationException;
import dev.cel.runtime.CelFunctionBinding;
import dev.cel.runtime.CelFunctionOverload;
import dev.cel.runtime.CelRuntime;
import dev.cel.runtime.CelRuntimeBuilder;
import dev.cel.runtime.CelRuntimeLibrary;
import dev.cel.runtime.CelStandardFunctions;
import dev.cel.runtime.CelValueDispatcher;
import dev.cel.runtime.Program;
import dev.cel.runtime.RuntimeEquality;
import dev.cel.runtime.RuntimeHelpers;
import dev.cel.runtime.standard.AddOperator;
import dev.cel.runtime.standard.CelStandardFunction;
import dev.cel.runtime.standard.DivideOperator;
import dev.cel.runtime.standard.EqualsOperator;
import dev.cel.runtime.standard.GreaterEqualsOperator;
import dev.cel.runtime.standard.GreaterOperator;
import dev.cel.runtime.standard.IndexOperator;
import dev.cel.runtime.standard.LessOperator;
import dev.cel.runtime.standard.LogicalNotOperator;
import dev.cel.runtime.standard.NotStrictlyFalseFunction;
import java.util.Optional;
import java.util.function.Function;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(TestParameterInjector.class)
public final class ProgramPlannerTest {
  // Note that the following deps are ordinarily built from top-level builder APIs
  private static final CelOptions CEL_OPTIONS = CelOptions.current().build();
  private static final CelTypeProvider TYPE_PROVIDER =
      new CombinedCelTypeProvider(
          DefaultTypeProvider.getInstance(),
          new ProtoMessageTypeProvider(ImmutableSet.of(TestAllTypes.getDescriptor())));
  private static final RuntimeEquality RUNTIME_EQUALITY =
      RuntimeEquality.create(RuntimeHelpers.create(), CEL_OPTIONS);
  private static final CelDescriptorPool DESCRIPTOR_POOL =
      DefaultDescriptorPool.create(
          CelDescriptorUtil.getAllDescriptorsFromFileDescriptor(
              TestAllTypes.getDescriptor().getFile()));
  private static final DynamicProto DYNAMIC_PROTO =
      DynamicProto.create(DefaultMessageFactory.create(DESCRIPTOR_POOL));
  private static final CelValueProvider VALUE_PROVIDER =
      ProtoMessageValueProvider.newInstance(CEL_OPTIONS, DYNAMIC_PROTO);
  private static final CelValueConverter CEL_VALUE_CONVERTER =
      ProtoCelValueConverter.newInstance(DESCRIPTOR_POOL, DYNAMIC_PROTO);

  private static final ProgramPlanner PLANNER =
      ProgramPlanner.newPlanner(
          TYPE_PROVIDER, VALUE_PROVIDER, newDispatcher(), CEL_VALUE_CONVERTER);

  private static final CelCompiler CEL_COMPILER =
      CelCompilerFactory.standardCelCompilerBuilder()
          .addVar("msg", StructTypeReference.create(TestAllTypes.getDescriptor().getFullName()))
          .addVar("map_string_int", MapType.create(SimpleType.STRING, SimpleType.INT))
          .addVar("optl", OptionalType.create(ListType.create(SimpleType.STRING)))
          .addVar("int_var", SimpleType.INT)
          .addVar("map_var", MapType.create(SimpleType.STRING, SimpleType.DYN))
          .setStandardMacros(CelStandardMacro.STANDARD_MACROS)
          .addFunctionDeclarations(
              newFunctionDeclaration("zero", newGlobalOverload("zero_overload", SimpleType.INT)),
              newFunctionDeclaration("error", newGlobalOverload("error_overload", SimpleType.INT)),
              newFunctionDeclaration(
                  "neg",
                  newGlobalOverload("neg_int", SimpleType.INT, SimpleType.INT),
                  newGlobalOverload("neg_double", SimpleType.DOUBLE, SimpleType.DOUBLE)),
              newFunctionDeclaration(
                  "concat",
                  newGlobalOverload(
                      "concat_bytes_bytes", SimpleType.BYTES, SimpleType.BYTES, SimpleType.BYTES),
                  newMemberOverload(
                      "bytes_concat_bytes", SimpleType.BYTES, SimpleType.BYTES, SimpleType.BYTES)))
          .addLibraries(CelOptionalLibrary.INSTANCE, CelExtensions.comprehensions())
          .addMessageTypes(TestAllTypes.getDescriptor())
          .build();

  private static CelValueDispatcher.Builder addOptionalBindings(
      CelValueDispatcher.Builder valueDispatcherBuilder) {
    CelRuntimeBuilder runtimeBuilder =
        new CelRuntimeBuilder() {
          @Override
          public CelRuntimeBuilder setOptions(CelOptions options) {
            return null;
          }

          @Override
          public CelRuntimeBuilder addFunctionBindings(CelFunctionBinding... bindings) {
            return addFunctionBindings(ImmutableList.copyOf(bindings));
          }

          @Override
          public CelRuntimeBuilder addFunctionBindings(Iterable<CelFunctionBinding> bindings) {
            for (CelFunctionBinding binding : bindings) {
              // TODO: Temp
              if (binding.getFunctionName().isEmpty()) {
                valueDispatcherBuilder.addOverload(binding);
              } else {
                addBindings(valueDispatcherBuilder, binding.getFunctionName(), binding);
              }
            }
            return this;
            // return this;
          }

          @Override
          public CelRuntimeBuilder addMessageTypes(Descriptor... descriptors) {
            return null;
          }

          @Override
          public CelRuntimeBuilder addMessageTypes(Iterable<Descriptor> descriptors) {
            return null;
          }

          @Override
          public CelRuntimeBuilder addFileTypes(FileDescriptor... fileDescriptors) {
            return null;
          }

          @Override
          public CelRuntimeBuilder addFileTypes(Iterable<FileDescriptor> fileDescriptors) {
            return null;
          }

          @Override
          public CelRuntimeBuilder addFileTypes(FileDescriptorSet fileDescriptorSet) {
            return null;
          }

          @Override
          public CelRuntimeBuilder setTypeFactory(Function<String, Message.Builder> typeFactory) {
            return null;
          }

          @Override
          public CelRuntimeBuilder setValueProvider(CelValueProvider celValueProvider) {
            return null;
          }

          @Override
          public CelRuntimeBuilder setEvaluateLinkedMessageTypes(boolean value) {
            return null;
          }

          @Override
          public CelRuntimeBuilder setStandardEnvironmentEnabled(boolean value) {
            return null;
          }

          @Override
          public CelRuntimeBuilder setStandardFunctions(CelStandardFunctions standardFunctions) {
            return null;
          }

          @Override
          public CelRuntimeBuilder addLibraries(CelRuntimeLibrary... libraries) {
            return null;
          }

          @Override
          public CelRuntimeBuilder addLibraries(Iterable<? extends CelRuntimeLibrary> libraries) {
            return null;
          }

          @Override
          public CelRuntimeBuilder setExtensionRegistry(ExtensionRegistry extensionRegistry) {
            return null;
          }

          @Override
          public CelRuntime build() {
            return null;
          }
        };

    CelOptionalLibrary.INSTANCE.setRuntimeOptions(runtimeBuilder, RUNTIME_EQUALITY, CEL_OPTIONS);

    return valueDispatcherBuilder;
  }

  /**
   * Configure dispatcher for testing purposes. This is done manually here, but this should be
   * driven by the top-level runtime APIs in the future
   */
  private static CelValueDispatcher newDispatcher() {
    CelValueDispatcher.Builder builder = CelValueDispatcher.newBuilder();

    // Subsetted StdLib
    addBindings(
        builder, Operator.INDEX.getFunction(), fromStandardFunction(IndexOperator.create()));
    addBindings(
        builder,
        Operator.LOGICAL_NOT.getFunction(),
        fromStandardFunction(LogicalNotOperator.create()));
    addBindings(builder, Operator.ADD.getFunction(), fromStandardFunction(AddOperator.create()));
    addBindings(
        builder, Operator.GREATER.getFunction(), fromStandardFunction(GreaterOperator.create()));
    addBindings(
        builder,
        Operator.GREATER_EQUALS.getFunction(),
        fromStandardFunction(GreaterEqualsOperator.create()));
    addBindings(builder, Operator.LESS.getFunction(), fromStandardFunction(LessOperator.create()));
    addBindings(
        builder, Operator.DIVIDE.getFunction(), fromStandardFunction(DivideOperator.create()));
    addBindings(
        builder, Operator.EQUALS.getFunction(), fromStandardFunction(EqualsOperator.create()));
    addBindings(
        builder,
        Operator.NOT_STRICTLY_FALSE.getFunction(),
        fromStandardFunction(NotStrictlyFalseFunction.create()));

    // Custom functions
    addBindings(
        builder,
        "zero",
        CelFunctionBinding.from("zero_overload", ImmutableList.of(), (unused) -> 0L));
    addBindings(
        builder,
        "error",
        CelFunctionBinding.from(
            "error_overload",
            ImmutableList.of(),
            (unused) -> {
              throw new IllegalArgumentException("Intentional error");
            }));
    addBindings(
        builder,
        "neg",
        CelFunctionBinding.from("neg_int", Long.class, arg -> -arg),
        CelFunctionBinding.from("neg_double", Double.class, arg -> -arg));
    addBindings(
        builder,
        "concat",
        CelFunctionBinding.from(
            "concat_bytes_bytes",
            CelByteString.class,
            CelByteString.class,
            ProgramPlannerTest::concatenateByteArrays),
        CelFunctionBinding.from(
            "bytes_concat_bytes",
            CelByteString.class,
            CelByteString.class,
            ProgramPlannerTest::concatenateByteArrays));

    addOptionalBindings(builder);

    return builder.build();
  }

  private static void addBindings(
      CelValueDispatcher.Builder builder,
      String functionName,
      CelFunctionBinding... functionBindings) {
    addBindings(builder, functionName, ImmutableSet.copyOf(functionBindings));
  }

  private static void addBindings(
      CelValueDispatcher.Builder builder,
      String functionName,
      ImmutableCollection<CelFunctionBinding> overloadBindings) {
    if (overloadBindings.isEmpty()) {
      throw new IllegalArgumentException("Invalid bindings");
    }
    // TODO: Runtime top-level APIs currently does not allow grouping overloads with
    // the function name. This capability will have to be added.
    if (overloadBindings.size() == 1) {
      CelFunctionBinding singleBinding = Iterables.getOnlyElement(overloadBindings);
      builder.addOverload(
          CelFunctionBinding.from(
              functionName, singleBinding.getArgTypes(), singleBinding.getDefinition()));
    } else {
      overloadBindings.forEach(builder::addOverload);

      // Setup dynamic dispatch
      CelFunctionOverload dynamicDispatchDef =
          args -> {
            for (CelFunctionBinding overload : overloadBindings) {
              if (canHandle(args, overload)) {
                return overload.getDefinition().apply(args);
              }
            }

            throw new IllegalArgumentException("Overload not found: " + functionName);
          };

      builder.addDynamicDispatchOverload(functionName, dynamicDispatchDef);
    }
  }

  private static boolean canHandle(Object[] arguments, CelFunctionBinding overload) {
    ImmutableList<Class<?>> argTypes = overload.getArgTypes();
    if (argTypes.size() != arguments.length) {
      return false;
    }

    for (int i = 0; i < argTypes.size(); i++) {
      Object arg = arguments[i];
      if (arg == null) {
        return false;
      }

      // TODO: Handle Strictness
      if (!overload.isStrict()) {
        throw new UnsupportedOperationException("TODO impl");
      }

      Class<?> paramType = argTypes.get(i);
      if (!paramType.isInstance(arg)) {
        return false;
      }
    }

    return true;
  }

  @TestParameter boolean isParseOnly;

  @Test
  public void plan_notSet_throws() {
    CelAbstractSyntaxTree invalidAst =
        CelAbstractSyntaxTree.newParsedAst(CelExpr.ofNotSet(0L), CelSource.newBuilder().build());

    CelEvaluationException e =
        assertThrows(CelEvaluationException.class, () -> PLANNER.plan(invalidAst));

    assertThat(e).hasMessageThat().contains("evaluation error: Unsupported kind: NOT_SET");
  }

  @Test
  public void plan_constant(@TestParameter ConstantTestCase testCase) throws Exception {
    CelAbstractSyntaxTree ast = compile(testCase.expression);
    Program program = PLANNER.plan(ast);

    Object result = program.eval();

    assertThat(result).isEqualTo(testCase.expected);
  }

  @Test
  public void plan_ident_enum() throws Exception {
    CelAbstractSyntaxTree ast =
        compile(GlobalEnum.getDescriptor().getFullName() + "." + GlobalEnum.GAR);
    Program program = PLANNER.plan(ast);

    Object result = program.eval();

    assertThat(result).isEqualTo(1);
  }

  @Test
  public void plan_ident_variable() throws Exception {
    CelAbstractSyntaxTree ast = compile("int_var");
    Program program = PLANNER.plan(ast);

    Object result = program.eval(ImmutableMap.of("int_var", 1L));

    assertThat(result).isEqualTo(1);
  }

  @Test
  public void planIdent_typeLiteral(@TestParameter TypeLiteralTestCase testCase) throws Exception {
    CelAbstractSyntaxTree ast = compile(testCase.expression);
    Program program = PLANNER.plan(ast);

    TypeType result = (TypeType) program.eval();

    assertThat(result).isEqualTo(testCase.type);
  }

  @Test
  @SuppressWarnings("unchecked") // test only
  public void plan_createList() throws Exception {
    CelAbstractSyntaxTree ast = compile("[1, 'foo', true, [2, false]]");
    Program program = PLANNER.plan(ast);

    ImmutableList<Object> result = (ImmutableList<Object>) program.eval();

    assertThat(result).containsExactly(1L, "foo", true, ImmutableList.of(2L, false)).inOrder();
  }

  @Test
  @SuppressWarnings("unchecked") // test only
  public void plan_createMap() throws Exception {
    CelAbstractSyntaxTree ast = compile("{'foo': 1, true: 'bar'}");
    Program program = PLANNER.plan(ast);

    ImmutableMap<Object, Object> result = (ImmutableMap<Object, Object>) program.eval();

    assertThat(result).containsExactly("foo", 1L, true, "bar").inOrder();
  }

  @Test
  public void plan_createStruct() throws Exception {
    CelAbstractSyntaxTree ast = compile("cel.expr.conformance.proto3.TestAllTypes{}");
    Program program = PLANNER.plan(ast);

    TestAllTypes result = (TestAllTypes) program.eval();

    assertThat(result).isEqualTo(TestAllTypes.getDefaultInstance());
  }

  @Test
  public void plan_createStruct_wrapper() throws Exception {
    CelAbstractSyntaxTree ast = compile("google.protobuf.StringValue { value: 'foo' }");
    Program program = PLANNER.plan(ast);

    String result = (String) program.eval();

    assertThat(result).isEqualTo("foo");
  }

  @Test
  public void planCreateStruct_withFields() throws Exception {
    CelAbstractSyntaxTree ast =
        compile(
            "cel.expr.conformance.proto3.TestAllTypes{"
                + "single_string: 'foo',"
                + "single_bool: true"
                + "}");
    Program program = PLANNER.plan(ast);

    TestAllTypes result = (TestAllTypes) program.eval();

    assertThat(result)
        .isEqualTo(TestAllTypes.newBuilder().setSingleString("foo").setSingleBool(true).build());
  }

  @Test
  public void plan_call_zeroArgs() throws Exception {
    CelAbstractSyntaxTree ast = compile("zero()");
    Program program = PLANNER.plan(ast);

    Long result = (Long) program.eval();

    assertThat(result).isEqualTo(0L);
  }

  @Test
  public void plan_call_throws() throws Exception {
    CelAbstractSyntaxTree ast = compile("error()");
    Program program = PLANNER.plan(ast);

    CelEvaluationException e = assertThrows(CelEvaluationException.class, program::eval);
    assertThat(e).hasMessageThat().contains("evaluation error: Intentional error");
    assertThat(e).hasCauseThat().isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  public void plan_call_oneArg_int() throws Exception {
    CelAbstractSyntaxTree ast = compile("neg(1)");
    Program program = PLANNER.plan(ast);

    Long result = (Long) program.eval();

    assertThat(result).isEqualTo(-1L);
  }

  @Test
  public void plan_call_oneArg_double() throws Exception {
    CelAbstractSyntaxTree ast = compile("neg(2.5)");
    Program program = PLANNER.plan(ast);

    Double result = (Double) program.eval();

    assertThat(result).isEqualTo(-2.5d);
  }

  @Test
  public void plan_call_twoArgs_global() throws Exception {
    CelAbstractSyntaxTree ast = compile("concat(b'abc', b'def')");
    Program program = PLANNER.plan(ast);

    CelByteString result = (CelByteString) program.eval();

    assertThat(result).isEqualTo(CelByteString.of("abcdef".getBytes(UTF_8)));
  }

  @Test
  public void plan_call_twoArgs_receiver() throws Exception {
    CelAbstractSyntaxTree ast = compile("b'abc'.concat(b'def')");
    Program program = PLANNER.plan(ast);

    CelByteString result = (CelByteString) program.eval();

    assertThat(result).isEqualTo(CelByteString.of("abcdef".getBytes(UTF_8)));
  }

  @Test
  public void plan_call_mapIndex() throws Exception {
    CelAbstractSyntaxTree ast = compile("map_var['key'][1]");
    Program program = PLANNER.plan(ast);
    ImmutableMap<Object, Object> mapVarPayload = ImmutableMap.of("key", ImmutableList.of(1L, 2L));

    Long result = (Long) program.eval(ImmutableMap.of("map_var", mapVarPayload));

    assertThat(result).isEqualTo(2L);
  }

  @Test
  @TestParameters("{expression: 'true || true', expectedResult: true}")
  @TestParameters("{expression: 'true || false', expectedResult: true}")
  @TestParameters("{expression: 'false || true', expectedResult: true}")
  @TestParameters("{expression: 'false || false', expectedResult: false}")
  @TestParameters("{expression: 'true || (1 / 0 > 2)', expectedResult: true}")
  @TestParameters("{expression: '(1 / 0 > 2) || true', expectedResult: true}")
  public void plan_call_logicalOr_shortCircuit(String expression, boolean expectedResult)
      throws Exception {
    CelAbstractSyntaxTree ast = compile(expression);
    Program program = PLANNER.plan(ast);

    boolean result = (boolean) program.eval();

    assertThat(result).isEqualTo(expectedResult);
  }

  @Test
  @TestParameters("{expression: '(1 / 0 > 2) || (1 / 0 > 2)'}")
  @TestParameters("{expression: 'false || (1 / 0 > 2)'}")
  @TestParameters("{expression: '(1 / 0 > 2) || false'}")
  public void plan_call_logicalOr_throws(String expression) throws Exception {
    CelAbstractSyntaxTree ast = compile(expression);
    Program program = PLANNER.plan(ast);

    CelEvaluationException e = assertThrows(CelEvaluationException.class, program::eval);
    // TODO: Tag metadata (source loc)
    assertThat(e).hasMessageThat().isEqualTo("evaluation error: / by zero");
    assertThat(e).hasCauseThat().isInstanceOf(ArithmeticException.class);
    assertThat(e.getErrorCode()).isEqualTo(CelErrorCode.DIVIDE_BY_ZERO);
  }

  @Test
  @TestParameters("{expression: 'true && true', expectedResult: true}")
  @TestParameters("{expression: 'true && false', expectedResult: false}")
  @TestParameters("{expression: 'false && true', expectedResult: false}")
  @TestParameters("{expression: 'false && false', expectedResult: false}")
  @TestParameters("{expression: 'false && (1 / 0 > 2)', expectedResult: false}")
  @TestParameters("{expression: '(1 / 0 > 2) && false', expectedResult: false}")
  public void plan_call_logicalAnd_shortCircuit(String expression, boolean expectedResult)
      throws Exception {
    CelAbstractSyntaxTree ast = compile(expression);
    Program program = PLANNER.plan(ast);

    boolean result = (boolean) program.eval();

    assertThat(result).isEqualTo(expectedResult);
  }

  @Test
  @TestParameters("{expression: '(1 / 0 > 2) && (1 / 0 > 2)'}")
  @TestParameters("{expression: 'true && (1 / 0 > 2)'}")
  @TestParameters("{expression: '(1 / 0 > 2) && true'}")
  public void plan_call_logicalAnd_throws(String expression) throws Exception {
    CelAbstractSyntaxTree ast = compile(expression);
    Program program = PLANNER.plan(ast);

    CelEvaluationException e = assertThrows(CelEvaluationException.class, program::eval);
    // TODO: Tag metadata (source loc)
    assertThat(e).hasMessageThat().isEqualTo("evaluation error: / by zero");
    assertThat(e).hasCauseThat().isInstanceOf(ArithmeticException.class);
    assertThat(e.getErrorCode()).isEqualTo(CelErrorCode.DIVIDE_BY_ZERO);
  }

  @Test
  public void plan_select_protoMessageField() throws Exception {
    CelAbstractSyntaxTree ast = compile("msg.single_string");
    Program program = PLANNER.plan(ast);

    String result =
        (String)
            program.eval(
                ImmutableMap.of("msg", TestAllTypes.newBuilder().setSingleString("foo").build()));

    assertThat(result).isEqualTo("foo");
  }

  @Test
  public void plan_select_nestedProtoMessage() throws Exception {
    CelAbstractSyntaxTree ast = compile("msg.single_nested_message");
    NestedMessage nestedMessage = NestedMessage.newBuilder().setBb(42).build();
    Program program = PLANNER.plan(ast);

    Object result =
        program.eval(
            ImmutableMap.of(
                "msg", TestAllTypes.newBuilder().setSingleNestedMessage(nestedMessage).build()));

    assertThat(result).isEqualTo(nestedMessage);
  }

  @Test
  public void plan_select_nestedProtoMessageField() throws Exception {
    CelAbstractSyntaxTree ast = compile("msg.single_nested_message.bb");
    Program program = PLANNER.plan(ast);

    Object result =
        program.eval(
            ImmutableMap.of(
                "msg",
                TestAllTypes.newBuilder()
                    .setSingleNestedMessage(NestedMessage.newBuilder().setBb(42))
                    .build()));

    assertThat(result).isEqualTo(42);
  }

  @Test
  public void plan_select_safeTraversal() throws Exception {
    CelAbstractSyntaxTree ast = compile("msg.single_nested_message.bb");
    Program program = PLANNER.plan(ast);

    Object result = program.eval(ImmutableMap.of("msg", TestAllTypes.newBuilder().build()));

    assertThat(result).isEqualTo(0L);
  }

  @Test
  public void plan_select_mapIndex() throws Exception {
    CelAbstractSyntaxTree ast = compile("map_string_int.foo");
    Program program = PLANNER.plan(ast);

    Object result = program.eval(ImmutableMap.of("map_string_int", ImmutableMap.of("foo", 42L)));

    assertThat(result).isEqualTo(42L);
  }

  @Test
  @TestParameters("{expression: '[1,2,3].exists(x, x > 0) == true'}")
  @TestParameters("{expression: '[1,2,3].exists(x, x < 0) == false'}")
  @TestParameters("{expression: '[1,2,3].exists(i, v, i >= 0 && v > 0) == true'}")
  @TestParameters("{expression: '[1,2,3].exists(i, v, i < 0 || v < 0) == false'}")
  @TestParameters("{expression: '[1,2,3].map(x, x + 1) == [2,3,4]'}")
  public void planComprehension_lists(String expression) throws Exception {
    CelAbstractSyntaxTree ast = compile(expression);
    Program program = PLANNER.plan(ast);

    boolean result = (boolean) program.eval();

    assertThat(result).isTrue();
  }

  @Test
  @TestParameters("{expression: '[1,2,3].exists(x, x > 0) == true'}")
  @TestParameters("{expression: '[1,2,3].exists(x, x < 0) == false'}")
  @TestParameters("{expression: '[1,2,3].exists(i, v, i >= 0 && v > 0) == true'}")
  @TestParameters("{expression: '[1,2,3].exists(i, v, i < 0 || v < 0) == false'}")
  public void planComprehension_maps(String expression) throws Exception {
    CelAbstractSyntaxTree ast = compile(expression);
    Program program = PLANNER.plan(ast);

    boolean result = (boolean) program.eval();

    assertThat(result).isTrue();
  }

  @Test
  public void mapIndex_onOptionalList_returnsOptionalValue() throws Exception {
    CelAbstractSyntaxTree ast = compile("{'a': 2}['a']");
    Program program = PLANNER.plan(ast);

    Object result = program.eval();

    assertThat(result).isEqualTo(2L);
  }

  @Test
  public void optionalIndex_onOptionalList_returnsOptionalValue() throws Exception {
    CelAbstractSyntaxTree ast = compile("{'a': 2}.?a");
    Program program = PLANNER.plan(ast);

    Optional<Long> result = (Optional<Long>) program.eval();

    assertThat(result).hasValue(2L);
  }

  private CelAbstractSyntaxTree compile(String expression) throws Exception {
    CelAbstractSyntaxTree ast = CEL_COMPILER.parse(expression).getAst();
    if (isParseOnly) {
      return ast;
    }

    return CEL_COMPILER.check(ast).getAst();
  }

  private static CelByteString concatenateByteArrays(CelByteString bytes1, CelByteString bytes2) {
    if (bytes1.isEmpty()) {
      return bytes2;
    }

    if (bytes2.isEmpty()) {
      return bytes1;
    }

    return bytes1.concat(bytes2);
  }

  // TODO: The following native -> CelValue function binding will need to be an
  // adapter.
  private static ImmutableSet<CelFunctionBinding> fromStandardFunction(
      CelStandardFunction standardFunction) {
    ImmutableSet<CelFunctionBinding> functionBindings =
        standardFunction.newFunctionBindings(CEL_OPTIONS, RUNTIME_EQUALITY);
    return functionBindings;
  }

  // private static CelValueFunctionBinding adaptBinding(CelFunctionBinding functionBinding) {
  //   return CelValueFunctionBinding.from(
  //       functionBinding.getOverloadId(),
  //       adaptArgumentTypes(functionBinding.getArgTypes()),
  //       celValueArgs -> {
  //         Object[] nativeArgs = new Object[celValueArgs.length];
  //         for (int i = 0; i < celValueArgs.length; i++) {
  //           nativeArgs[i] = CEL_VALUE_CONVERTER.fromCelValueToJavaObject(celValueArgs[i]);
  //         }
  //
  //         Object nativeResult;
  //         try {
  //           nativeResult = functionBinding.getDefinition().apply(nativeArgs);
  //         } catch (CelRuntimeException e) {
  //           throw e;
  //         } catch (CelEvaluationException e) {
  //           throw new CelRuntimeException(e.getCause(), e.getErrorCode());
  //         }
  //         return CEL_VALUE_CONVERTER.fromJavaObjectToCelValue(nativeResult);
  //       });
  // }

  // private static ImmutableList<Class<? extends CelValue>> adaptArgumentTypes(
  //     ImmutableList<Class<?>> argTypes) {
  //   ImmutableList.Builder<Class<? extends CelValue>> builder = ImmutableList.builder();
  //
  //   for (Class<?> argType : argTypes) {
  //     if (argType.equals(String.class)) {
  //       builder.add(StringValue.class);
  //     } else if (argType.equals(Long.class)) {
  //       builder.add(IntValue.class);
  //     } else if (argType.equals(Double.class)) {
  //       builder.add(DoubleValue.class);
  //     } else if (argType.equals(Boolean.class)) {
  //       builder.add(BoolValue.class);
  //     } else if (argType.equals(UnsignedLong.class)) {
  //       builder.add(UintValue.class);
  //     } else if (argType.equals(CelByteString.class)) {
  //       builder.add(BytesValue.class);
  //     } else if (argType.equals(Instant.class)) {
  //       builder.add(TimestampValue.class);
  //     } else if (argType.equals(Duration.class)) {
  //       builder.add(DurationValue.class);
  //     } else if (Collection.class.isAssignableFrom(argType)) {
  //       builder.add(ListValue.class);
  //     } else if (Map.class.isAssignableFrom(argType)) {
  //       builder.add(MapValue.class);
  //     } else if (CelType.class.isAssignableFrom(argType)) {
  //       builder.add(TypeValue.class);
  //     } else if (argType.equals(NullValue.class)) {
  //       builder.add(NullValue.class);
  //     } else if (argType.equals(Object.class)
  //         ||
  //         // Using Number.class was probably a mistake (see index_list). This particular overload
  //         // will benefit from a concrete definition.
  //         argType.equals(Number.class)) {
  //       builder.add(CelValue.class);
  //     } else if (argType.equals(Optional.class)) {
  //       builder.add(OptionalValue.class);
  //     } else {
  //       // In all likelihood -- we should probably do an OpaqueValue here
  //       throw new IllegalArgumentException("Unknown argument type: " + argType);
  //     }
  //   }
  //   return builder.build();
  // }

  @SuppressWarnings("ImmutableEnumChecker") // Test only
  private enum ConstantTestCase {
    NULL("null", NullValue.NULL_VALUE),
    BOOLEAN("true", true),
    INT64("42", 42L),
    UINT64("42u", UnsignedLong.valueOf(42)),
    DOUBLE("1.5", 1.5d),
    STRING("'hello world'", "hello world"),
    BYTES("b'abc'", CelByteString.of("abc".getBytes(UTF_8)));

    private final String expression;
    private final Object expected;

    ConstantTestCase(String expression, Object expected) {
      this.expression = expression;
      this.expected = expected;
    }
  }

  @SuppressWarnings("ImmutableEnumChecker") // Test only
  private enum TypeLiteralTestCase {
    BOOL("bool", SimpleType.BOOL),
    BYTES("bytes", SimpleType.BYTES),
    DOUBLE("double", SimpleType.DOUBLE),
    INT("int", SimpleType.INT),
    UINT("uint", SimpleType.UINT),
    STRING("string", SimpleType.STRING),
    DYN("dyn", SimpleType.DYN),
    LIST("list", ListType.create(SimpleType.DYN)),
    MAP("map", MapType.create(SimpleType.DYN, SimpleType.DYN)),
    NULL("null_type", SimpleType.NULL_TYPE),
    DURATION("google.protobuf.Duration", SimpleType.DURATION),
    TIMESTAMP("google.protobuf.Timestamp", SimpleType.TIMESTAMP),
    OPTIONAL("optional_type", OptionalType.create(SimpleType.DYN)),
    PROTO_MESSAGE_TYPE(
        "cel.expr.conformance.proto3.TestAllTypes",
        TYPE_PROVIDER.findType(TestAllTypes.getDescriptor().getFullName()).get());

    private final String expression;
    private final TypeType type;

    TypeLiteralTestCase(String expression, CelType type) {
      this.expression = expression;
      this.type = TypeType.create(type);
    }
  }
}
