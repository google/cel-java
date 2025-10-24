package dev.cel.runtime.planner;

import static com.google.common.truth.Truth.assertThat;
import static dev.cel.common.CelFunctionDecl.newFunctionDeclaration;
import static dev.cel.common.CelOverloadDecl.newGlobalOverload;
import static dev.cel.common.CelOverloadDecl.newMemberOverload;
import static org.junit.Assert.assertThrows;

import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.primitives.UnsignedLong;
import com.google.testing.junit.testparameterinjector.TestParameter;
import com.google.testing.junit.testparameterinjector.TestParameterInjector;
import com.google.testing.junit.testparameterinjector.TestParameters;
import dev.cel.common.CelAbstractSyntaxTree;
import dev.cel.common.CelDescriptorUtil;
import dev.cel.common.CelErrorCode;
import dev.cel.common.CelOptions;
import dev.cel.common.internal.CelDescriptorPool;
import dev.cel.common.internal.DefaultDescriptorPool;
import dev.cel.common.internal.DefaultMessageFactory;
import dev.cel.common.internal.DynamicProto;
import dev.cel.common.types.CelType;
import dev.cel.common.types.DefaultTypeProvider;
import dev.cel.common.types.MapType;
import dev.cel.common.types.SimpleType;
import dev.cel.common.types.StructTypeReference;
import dev.cel.common.types.TypeType;
import dev.cel.common.values.BoolValue;
import dev.cel.common.values.BytesValue;
import dev.cel.common.values.CelByteString;
import dev.cel.common.values.CelValue;
import dev.cel.common.values.CelValueConverter;
import dev.cel.common.values.DoubleValue;
import dev.cel.common.values.DurationValue;
import dev.cel.common.values.IntValue;
import dev.cel.common.values.ListValue;
import dev.cel.common.values.MapValue;
import dev.cel.common.values.NullValue;
import dev.cel.common.values.ProtoMessageValueProvider;
import dev.cel.common.values.StringValue;
import dev.cel.common.values.TimestampValue;
import dev.cel.common.values.TypeValue;
import dev.cel.common.values.UintValue;
import dev.cel.compiler.CelCompiler;
import dev.cel.compiler.CelCompilerFactory;
import dev.cel.expr.conformance.proto3.GlobalEnum;
import dev.cel.expr.conformance.proto3.TestAllTypes;
import dev.cel.extensions.CelExtensions;
import dev.cel.extensions.CelOptionalLibrary;
import dev.cel.parser.CelStandardMacro;
import dev.cel.parser.Operator;
import dev.cel.runtime.CelEvaluationException;
import dev.cel.runtime.CelFunctionBinding;
import dev.cel.runtime.CelLiteRuntime.Program;
import dev.cel.runtime.CelValueFunctionBinding;
import dev.cel.runtime.CelValueFunctionOverload;
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
import java.nio.charset.StandardCharsets;

import dev.cel.runtime.DefaultDispatcher;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(TestParameterInjector.class)
public final class ProgramPlannerTest {
  private static final CelOptions CEL_OPTIONS = CelOptions.current().evaluateCanonicalTypesToNativeValues(true).build();
  private static final RuntimeEquality RUNTIME_EQUALITY = RuntimeEquality.create(RuntimeHelpers.create(), CEL_OPTIONS);
  private static final CelCompiler CEL_COMPILER =
          CelCompilerFactory.standardCelCompilerBuilder()
              .addVar("msg", StructTypeReference.create(TestAllTypes.getDescriptor().getFullName()))
              .addVar("int_var", SimpleType.INT)
              .addVar("map_var", MapType.create(SimpleType.STRING, SimpleType.DYN))
              .setStandardMacros(CelStandardMacro.STANDARD_MACROS)
              .addFunctionDeclarations(
                  newFunctionDeclaration("zero", newGlobalOverload("zero_overload", SimpleType.INT)),
                  newFunctionDeclaration("error", newGlobalOverload("error_overload", SimpleType.INT)),
                  newFunctionDeclaration("neg",
                      newGlobalOverload("neg_int", SimpleType.INT, SimpleType.INT),
                      newGlobalOverload("neg_double", SimpleType.DOUBLE, SimpleType.DOUBLE)
                  ),
                  newFunctionDeclaration("concat",
                      newGlobalOverload("concat_bytes_bytes", SimpleType.BYTES, SimpleType.BYTES, SimpleType.BYTES),
                      newMemberOverload("bytes_concat_bytes", SimpleType.BYTES, SimpleType.BYTES, SimpleType.BYTES)
                  )
              )
              .addLibraries(CelOptionalLibrary.INSTANCE, CelExtensions.comprehensions())
              .addMessageTypes(TestAllTypes.getDescriptor())
              .build();
  private static final CelDescriptorPool DESCRIPTOR_POOL =
      DefaultDescriptorPool.create(CelDescriptorUtil.getAllDescriptorsFromFileDescriptor(TestAllTypes.getDescriptor().getFile()));
  private static final CelValueConverter CEL_VALUE_CONVERTER = new CelValueConverter();
  private static final ProgramPlanner PLANNER = ProgramPlanner.newPlanner(
      DefaultTypeProvider.create(),
      ProtoMessageValueProvider.newInstance(
          CEL_OPTIONS,
          DynamicProto.create(DefaultMessageFactory.create(DESCRIPTOR_POOL))
      ),
      CEL_VALUE_CONVERTER,
      newDispatcher()
  );

  /**
   * Configure dispatcher for testing purposes. This is done manually here, but this should be driven by the top-level runtime APIs in the future
   */
  private static DefaultDispatcher newDispatcher() {
    DefaultDispatcher.Builder builder = DefaultDispatcher.newBuilder();

    // Subsetted StdLib
    addBindings(builder, Operator.INDEX.getFunction(), fromStandardFunction(IndexOperator.create()));
    addBindings(builder, Operator.LOGICAL_NOT.getFunction(), fromStandardFunction(
        LogicalNotOperator.create()));
    addBindings(builder, Operator.ADD.getFunction(), fromStandardFunction(AddOperator.create()));
    addBindings(builder, Operator.GREATER.getFunction(), fromStandardFunction(GreaterOperator.create()));
    addBindings(builder, Operator.GREATER_EQUALS.getFunction(), fromStandardFunction(
        GreaterEqualsOperator.create()));
    addBindings(builder, Operator.LESS.getFunction(), fromStandardFunction(LessOperator.create()));
    addBindings(builder, Operator.DIVIDE.getFunction(), fromStandardFunction(DivideOperator.create()));
    addBindings(builder, Operator.EQUALS.getFunction(), fromStandardFunction(EqualsOperator.create()));
    addBindings(builder, Operator.NOT_STRICTLY_FALSE.getFunction(), fromStandardFunction(
        NotStrictlyFalseFunction.create()));

    // Custom functions
    addBindings(builder, "zero", CelValueFunctionBinding.from("zero_overload", () -> IntValue.create(0L)));
    addBindings(builder, "error", CelValueFunctionBinding.from("error_overload", () -> { throw new IllegalArgumentException("Intentional error"); }));
    addBindings(builder, "neg",
        CelValueFunctionBinding.from("neg_int", IntValue.class, arg -> IntValue.create(-arg.longValue())),
        CelValueFunctionBinding.from("neg_double", DoubleValue.class, arg -> DoubleValue.create(-arg.doubleValue()))
    );
    addBindings(builder, "concat",
        CelValueFunctionBinding.from("concat_bytes_bytes", BytesValue.class, BytesValue.class, ProgramPlannerTest::concatenateByteArrays),
        CelValueFunctionBinding.from("bytes_concat_bytes", BytesValue.class, BytesValue.class,ProgramPlannerTest::concatenateByteArrays));

    return builder.build();
  }


  private static void addBindings(DefaultDispatcher.Builder builder, String functionName, CelValueFunctionBinding... functionBindings) {
    addBindings(builder, functionName, ImmutableSet.copyOf(functionBindings));
  }

  private static void addBindings(DefaultDispatcher.Builder builder, String functionName, ImmutableCollection<CelValueFunctionBinding> overloadBindings) {
    if (overloadBindings.isEmpty()) {
      throw new IllegalArgumentException("Invalid bindings");
    }
    // TODO: Runtime top-level APIs currently does not allow grouping overloads with the function name. This capability will have to be added.
    if (overloadBindings.size() == 1) {
      CelValueFunctionBinding singleBinding = Iterables.getOnlyElement(overloadBindings);
      builder.addOverload(
          CelValueFunctionBinding.from(
              functionName, singleBinding.argTypes(), singleBinding.definition())
      );
    } else {
      overloadBindings.forEach(builder::addOverload);

      // Setup dynamic dispatch
      CelValueFunctionOverload dynamicDispatchDef = args -> {
        for (CelValueFunctionBinding overload : overloadBindings) {
          if (overload.canHandle(args)) {
            return overload.definition().apply(args);
          }
        }

        throw new IllegalArgumentException("Overload not found: " + functionName);
      };

      builder.addDynamicDispatchOverload(functionName, dynamicDispatchDef);
    }
  }

  @TestParameter boolean isParseOnly;

  @SuppressWarnings("ImmutableEnumChecker") // Test only
  private enum ConstantTestCase {
    NULL(
        "null",
        NullValue.NULL_VALUE),
    BOOLEAN(
        "true",
          true),
    INT64(
        "42", 42L),
    UINT64("42u", UnsignedLong.valueOf(42)),
    DOUBLE("1.5", 1.5d),
    STRING("'hello world'", "hello world"),
    BYTES(
        "b'abc'", CelByteString.of("abc".getBytes(StandardCharsets.UTF_8)));

    private final String expression;
    private final Object expected;

    ConstantTestCase(String expression, Object expected) {
      this.expression = expression;
      this.expected = expected;
    }
  }

  @Test
  public void planConst(@TestParameter ConstantTestCase testCase) throws Exception {
    CelAbstractSyntaxTree ast = compile(testCase.expression);
    Program program = PLANNER.plan(ast);

    Object result = program.eval();

    assertThat(result).isEqualTo(testCase.expected);
  }

  @Test
  public void planIdent_enum() throws Exception {
    CelAbstractSyntaxTree ast = compile(GlobalEnum.getDescriptor().getFullName() + "." + GlobalEnum.GAR);
    Program program = PLANNER.plan(ast);

    Object result = program.eval();

    assertThat(result).isEqualTo(1);
  }

  @Test
  public void planIdent_variable() throws Exception {
    CelAbstractSyntaxTree ast = compile("int_var");
    Program program = PLANNER.plan(ast);

    Object result = program.eval(ImmutableMap.of("int_var", 1L));

    assertThat(result).isEqualTo(1);
  }

  @Test
  public void planCreateStruct() throws Exception {
    CelAbstractSyntaxTree ast = compile("cel.expr.conformance.proto3.TestAllTypes{}");
    Program program = PLANNER.plan(ast);

    Object result = program.eval();

    assertThat(result).isEqualTo(TestAllTypes.getDefaultInstance());
  }

  @Test
  public void planCreateStruct_withFields() throws Exception {
    CelAbstractSyntaxTree ast = compile("cel.expr.conformance.proto3.TestAllTypes{"
        + "single_string: 'foo',"
        + "single_bool: true"
        + "}");
    Program program = PLANNER.plan(ast);

    Object result = program.eval();

    assertThat(result).isEqualTo(TestAllTypes.newBuilder().setSingleString("foo").setSingleBool(true).build());
  }

  @Test
  public void planCreateList() throws Exception {
    CelAbstractSyntaxTree ast = compile("[1, 'foo', true]");

    Program program = PLANNER.plan(ast);

    List<Object> result = (List<Object>) program.eval();

    assertThat(result).containsExactly(1L, "foo", true).inOrder();
  }

  @Test
  public void planCreateMap() throws Exception {
    CelAbstractSyntaxTree ast = compile("{'foo': 1, true: 'bar'}");

    Program program = PLANNER.plan(ast);

    Map<Object, Object> result = (Map<Object, Object>) program.eval();

    assertThat(result).containsExactly("foo", 1L, true, "bar").inOrder();
  }

  @SuppressWarnings("ImmutableEnumChecker") // Test only
  private enum TypeLiteralTestCase {
    // BOOL("bool", SimpleType.BOOL),
    // BYTES("bytes", SimpleType.BYTES),
    // DOUBLE("double", SimpleType.DOUBLE),
    // INT("int", SimpleType.INT),
    // UINT("uint", SimpleType.UINT),
    // STRING("string", SimpleType.STRING),
    // DYN("dyn", SimpleType.DYN),
    // LIST("list", ListType.create(SimpleType.DYN)),
    // MAP("map", MapType.create(SimpleType.DYN, SimpleType.DYN)),
    // NULL("null_type", SimpleType.NULL_TYPE),
    DURATION("google.protobuf.Duration", SimpleType.DURATION),
    TIMESTAMP("google.protobuf.Timestamp", SimpleType.TIMESTAMP),
    // OPTIONAL("optional_type", OptionalType.create(SimpleType.DYN)),
    ;

    private final String expression;
    private final TypeType type;

    TypeLiteralTestCase(String expression, CelType type) {
      this.expression = expression;
      this.type = TypeType.create(type);
    }
  }

  @Test
  public void planIdent_typeLiteral(@TestParameter TypeLiteralTestCase testCase) throws Exception {
    CelAbstractSyntaxTree ast = compile(testCase.expression);
    Program program = PLANNER.plan(ast);

    TypeType result = (TypeType) program.eval();

    assertThat(result).isEqualTo(testCase.type);
  }

  @Test
  public void planCall_zeroArgs() throws Exception {
    CelAbstractSyntaxTree ast = compile("zero()");
    Program program = PLANNER.plan(ast);

    Long result = (Long) program.eval();

    assertThat(result).isEqualTo(0L);
  }

  @Test
  public void planCall_throws() throws Exception {
    CelAbstractSyntaxTree ast = compile("error()");
    Program program = PLANNER.plan(ast);

    CelEvaluationException e = assertThrows(CelEvaluationException.class, program::eval);
    assertThat(e).hasMessageThat().contains("evaluation error: Intentional error");
    assertThat(e).hasCauseThat().isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  public void planCall_oneArg_int() throws Exception {
    CelAbstractSyntaxTree ast = compile("neg(1)");
    Program program = PLANNER.plan(ast);

    Long result = (Long) program.eval();

    assertThat(result).isEqualTo(-1L);
  }

  @Test
  public void planCall_oneArg_double() throws Exception {
    CelAbstractSyntaxTree ast = compile("neg(2.5)");
    Program program = PLANNER.plan(ast);

    Double result = (Double) program.eval();

    assertThat(result).isEqualTo(-2.5d);
  }

  @Test
  public void planCall_twoArgs_global() throws Exception {
    CelAbstractSyntaxTree ast = compile("concat(b'abc', b'def')");
    Program program = PLANNER.plan(ast);

    CelByteString result = (CelByteString) program.eval();

    assertThat(result).isEqualTo(CelByteString.of("abcdef".getBytes(StandardCharsets.UTF_8)));
  }

  @Test
  public void planCall_twoArgs_receiver() throws Exception {
    CelAbstractSyntaxTree ast = compile("b'abc'.concat(b'def')");
    Program program = PLANNER.plan(ast);

    CelByteString result = (CelByteString) program.eval();

    assertThat(result).isEqualTo(CelByteString.of("abcdef".getBytes(StandardCharsets.UTF_8)));
  }

  @Test
  public void planCall_mapIndex() throws Exception {
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
  public void planCall_logicalOr_shortCircuit(String expression, boolean expectedResult) throws Exception {
    CelAbstractSyntaxTree ast = compile(expression);
    Program program = PLANNER.plan(ast);

    boolean result = (boolean) program.eval();

    assertThat(result).isEqualTo(expectedResult);
  }

  @Test
  @TestParameters("{expression: '(1 / 0 > 2) || (1 / 0 > 2)'}")
  @TestParameters("{expression: 'false || (1 / 0 > 2)'}")
  @TestParameters("{expression: '(1 / 0 > 2) || false'}")
  public void planCall_logicalOr_throws(String expression) throws Exception {
    CelAbstractSyntaxTree ast = compile(expression);
    Program program = PLANNER.plan(ast);

    CelEvaluationException e = assertThrows(CelEvaluationException.class, () -> program.eval());
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
  public void planCall_logicalAnd_shortCircuit(String expression, boolean expectedResult) throws Exception {
    CelAbstractSyntaxTree ast = compile(expression);
    Program program = PLANNER.plan(ast);

    boolean result = (boolean) program.eval();

    assertThat(result).isEqualTo(expectedResult);
  }

  @Test
  @TestParameters("{expression: '(1 / 0 > 2) && (1 / 0 > 2)'}")
  @TestParameters("{expression: 'true && (1 / 0 > 2)'}")
  @TestParameters("{expression: '(1 / 0 > 2) && true'}")
  public void planCall_logicalAnd_throws(String expression) throws Exception {
    CelAbstractSyntaxTree ast = compile(expression);
    Program program = PLANNER.plan(ast);

    CelEvaluationException e = assertThrows(CelEvaluationException.class, () -> program.eval());
    // TODO: Tag metadata (source loc)
    assertThat(e).hasMessageThat().isEqualTo("evaluation error: / by zero");
    assertThat(e).hasCauseThat().isInstanceOf(ArithmeticException.class);
    assertThat(e.getErrorCode()).isEqualTo(CelErrorCode.DIVIDE_BY_ZERO);
  }

  @Test
  public void planSelect() throws Exception {
    CelAbstractSyntaxTree ast = compile("msg.single_string");
    Program program = PLANNER.plan(ast);

    String result = (String) program.eval(
        ImmutableMap.of("msg", TestAllTypes.newBuilder().setSingleString("foo").build())
    );

    assertThat(result).isEqualTo("foo");
  }

  @Test
  // @TestParameters("{expression: '[1,2,3].exists(x, x > 0) == true'}")
  // @TestParameters("{expression: '[1,2,3].exists(x, x < 0) == false'}")
  // @TestParameters("{expression: '[1,2,3].exists(i, v, i >= 0 && v > 0) == true'}")
  // @TestParameters("{expression: '[1,2,3].exists(i, v, i < 0 || v < 0) == false'}")
  @TestParameters("{expression: '[1,2,3].map(x, x + 1) == [2,3,4]'}")
  public void planComprehension_lists(String expression) throws Exception {
    CelAbstractSyntaxTree ast = compile(expression);
    Program program = PLANNER.plan(ast);

    boolean result = (boolean) program.eval();

    assertThat(result).isTrue();
  }

  // @Test
  // @TestParameters("{expression: '[1,2,3].exists(x, x > 0) == true'}")
  // @TestParameters("{expression: '[1,2,3].exists(x, x < 0) == false'}")
  // @TestParameters("{expression: '[1,2,3].exists(i, v, i >= 0 && v > 0) == true'}")
  // @TestParameters("{expression: '[1,2,3].exists(i, v, i < 0 || v < 0) == false'}")
  // public void planComprehension_maps(String expression) throws Exception {
  //   CelAbstractSyntaxTree ast = compile(expression);
  //   Program program = PLANNER.plan(ast);
  //
  //   boolean result = (boolean) program.eval();
  //
  //   assertThat(result).isTrue();
  // }

  private CelAbstractSyntaxTree compile(String expression) throws Exception {
    CelAbstractSyntaxTree ast = CEL_COMPILER.parse(expression).getAst();
    if (isParseOnly) {
      return ast;
    }

    return CEL_COMPILER.check(ast).getAst();
  }

  private static BytesValue concatenateByteArrays(BytesValue bytes1, BytesValue bytes2) {
    if (bytes1.isZeroValue()) {
      return bytes2;
    }

    if (bytes2.isZeroValue()) {
      return bytes1;
    }

    CelByteString combined = bytes1.value().concat(bytes2.value());
    return BytesValue.create(combined);
  }

  // TODO: The following native -> CelValue function binding will need to be an adapter.
  private static ImmutableSet<CelValueFunctionBinding> fromStandardFunction(CelStandardFunction standardFunction) {
    ImmutableSet<CelFunctionBinding> functionBindings = standardFunction.newFunctionBindings(CEL_OPTIONS, RUNTIME_EQUALITY);
    ImmutableSet.Builder<CelValueFunctionBinding> builder  = ImmutableSet.builder();

    for (CelFunctionBinding functionBinding : functionBindings) {
        CelValueFunctionBinding adaptedBinding = CelValueFunctionBinding.from(functionBinding.getOverloadId(), adaptArgumentTypes(functionBinding.getArgTypes()), celValueArgs -> {
          Object[] nativeArgs = new Object[celValueArgs.length];
          for (int i = 0; i < celValueArgs.length; i++) {
            nativeArgs[i] = CEL_VALUE_CONVERTER.fromCelValueToJavaObject(celValueArgs[i]);
          }

          Object nativeResult = functionBinding.getDefinition().apply(nativeArgs);
          return CEL_VALUE_CONVERTER.fromJavaObjectToCelValue(nativeResult);
        });
        builder.add(adaptedBinding);
    }

    return builder.build();
  }

  private static ImmutableList<Class<? extends CelValue>> adaptArgumentTypes(ImmutableList<Class<?>> argTypes) {
    ImmutableList.Builder<Class<? extends CelValue>> builder = ImmutableList.builder();

    for (Class<?> argType : argTypes) {
      if (argType.equals(String.class)) {
        builder.add(StringValue.class);
      } else if (argType.equals(Long.class)) {
        builder.add(IntValue.class);
      } else if (argType.equals(Double.class)) {
        builder.add(DoubleValue.class);
      } else if (argType.equals(Boolean.class)) {
        builder.add(BoolValue.class);
      } else if (argType.equals(UnsignedLong.class)) {
        builder.add(UintValue.class);
      } else if (argType.equals(CelByteString.class)) {
        builder.add(BytesValue.class);
      } else if (argType.equals(Instant.class)) {
        builder.add(TimestampValue.class);
      } else if (argType.equals(Duration.class)) {
        builder.add(DurationValue.class);
      } else if (List.class.isAssignableFrom(argType)) {
        builder.add(ListValue.class);
      } else if (Map.class.isAssignableFrom(argType)) {
        builder.add(MapValue.class);
      } else if (CelType.class.isAssignableFrom(argType)) {
        builder.add(TypeValue.class);
      } else if (argType.equals(NullValue.class)) {
        builder.add(NullValue.class);
      } else if (
          argType.equals(Object.class) ||
            // Using Number.class was probably a mistake (see index_list). This particular overload will benefit from a concrete definition.
          argType.equals(Number.class)
      ) {
        builder.add(CelValue.class);
      } else {
        // In all likelihood -- we should probably do an OpaqueValue here
        throw new IllegalArgumentException("Unknown argument type: " + argType);
      }
    }
    return builder.build();
  }

}
