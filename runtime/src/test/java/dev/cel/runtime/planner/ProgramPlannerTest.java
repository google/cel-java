package dev.cel.runtime.planner;

import static com.google.common.truth.Truth.assertThat;
import static dev.cel.common.CelFunctionDecl.*;
import static dev.cel.common.CelOverloadDecl.*;

import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.primitives.UnsignedLong;
import com.google.testing.junit.testparameterinjector.TestParameter;
import com.google.testing.junit.testparameterinjector.TestParameterInjector;
import dev.cel.common.CelAbstractSyntaxTree;
import dev.cel.common.CelOptions;
import dev.cel.common.CelValidationException;
import dev.cel.common.types.CelType;
import dev.cel.common.types.DefaultTypeProvider;
import dev.cel.common.types.ListType;
import dev.cel.common.types.MapType;
import dev.cel.common.types.OptionalType;
import dev.cel.common.types.SimpleType;
import dev.cel.common.types.TypeType;
import dev.cel.common.values.CelByteString;
import dev.cel.common.values.CelValueConverter;
import dev.cel.common.values.NullValue;
import dev.cel.compiler.CelCompiler;
import dev.cel.compiler.CelCompilerFactory;
import dev.cel.expr.conformance.proto2.TestAllTypes;
import dev.cel.extensions.CelOptionalLibrary;
import dev.cel.parser.Operator;
import dev.cel.runtime.CelFunctionBinding;
import dev.cel.runtime.CelFunctionOverload;
import dev.cel.runtime.CelLiteRuntime.Program;
import dev.cel.runtime.RuntimeEquality;
import dev.cel.runtime.RuntimeHelpers;
import dev.cel.runtime.standard.CelStandardFunction;
import dev.cel.runtime.standard.IndexOperator;
import java.nio.charset.StandardCharsets;

import dev.cel.runtime.DefaultDispatcher;
import java.util.Collection;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(TestParameterInjector.class)
public final class ProgramPlannerTest {
  private static final CelOptions CEL_OPTIONS = CelOptions.DEFAULT;
  private static final RuntimeEquality RUNTIME_EQUALITY = RuntimeEquality.create(RuntimeHelpers.create(), CEL_OPTIONS);
  private static final CelCompiler CEL_COMPILER =
          CelCompilerFactory.standardCelCompilerBuilder()
              .addVar("int_var", SimpleType.INT)
              .addVar("map_var", MapType.create(SimpleType.STRING, SimpleType.DYN))
              .addFunctionDeclarations(
                  newFunctionDeclaration("zero", newGlobalOverload("zero_overload", SimpleType.INT)),
                  newFunctionDeclaration("neg",
                      newGlobalOverload("neg_int", SimpleType.INT, SimpleType.INT),
                      newGlobalOverload("neg_double", SimpleType.DOUBLE, SimpleType.DOUBLE)
                  ),
                  newFunctionDeclaration("concat",
                      newGlobalOverload("concat_bytes_bytes", SimpleType.BYTES, SimpleType.BYTES, SimpleType.BYTES),
                      newMemberOverload("bytes_concat_bytes", SimpleType.BYTES, SimpleType.BYTES, SimpleType.BYTES)
                  )
              )
              .addLibraries(CelOptionalLibrary.INSTANCE)
              .addMessageTypes(TestAllTypes.getDescriptor())
              .build();

  private static final ProgramPlanner PLANNER = ProgramPlanner.newPlanner(
          DefaultTypeProvider.create(),
          new CelValueConverter(),
          newDispatcher()
  );

  /**
   * Configure dispatcher for testing purposes. This is done manually here, but this should be driven by the top-level runtime APIs in the future
   */
  private static DefaultDispatcher newDispatcher() {
    DefaultDispatcher.Builder builder = DefaultDispatcher.newBuilder();

    // Subsetted StdLib
    addBindings(builder, Operator.INDEX.getFunction(), fromStandardFunction(IndexOperator.create()));

    // Custom functions
    addBindings(builder, "zero", CelFunctionBinding.from("zero_overload", ImmutableList.of(), args -> 0L));
    addBindings(builder, "neg",
        CelFunctionBinding.from("neg_int", Long.class, arg -> -arg),
        CelFunctionBinding.from("neg_double", Double.class, arg -> -arg)
    );
    addBindings(builder, "concat",
        CelFunctionBinding.from("concat_bytes_bytes", CelByteString.class, CelByteString.class, ProgramPlannerTest::concatenateByteArrays),
        CelFunctionBinding.from("bytes_concat_bytes", CelByteString.class, CelByteString.class,ProgramPlannerTest::concatenateByteArrays));

    return builder.build();
  }

  private static ImmutableSet<CelFunctionBinding> fromStandardFunction(CelStandardFunction standardFunction) {
    return standardFunction.newFunctionBindings(CEL_OPTIONS, RUNTIME_EQUALITY);
  }

  private static void addBindings(DefaultDispatcher.Builder builder, String functionName, CelFunctionBinding... functionBindings) {
    addBindings(builder, functionName, ImmutableSet.copyOf(functionBindings));
  }

  private static void addBindings(DefaultDispatcher.Builder builder, String functionName, ImmutableCollection<CelFunctionBinding> overloadBindings) {
    if (overloadBindings.isEmpty()) {
      throw new IllegalArgumentException("Invalid bindings");
    }
    // TODO: Runtime top-level APIs currently does not allow grouping overloads with the function name. This capability will have to be added.
    if (overloadBindings.size() == 1) {
      CelFunctionBinding singleBinding = Iterables.getOnlyElement(overloadBindings);
      builder.addOverload(
          CelFunctionBinding.from(
              functionName, singleBinding.getArgTypes(), singleBinding.getDefinition())
      );
    } else {
      overloadBindings.forEach(builder::addOverload);

      // Setup dynamic dispatch
      CelFunctionOverload dynamicDispatchDef = args -> {
        for (CelFunctionBinding overload : overloadBindings) {
          if (overload.canHandle(args)) {
            return overload.getDefinition().apply(args);
          }
        }

        throw new IllegalArgumentException("Overload not found: " + functionName);
      };

      builder.addFunction(functionName, dynamicDispatchDef);
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
    CelAbstractSyntaxTree ast = compile("cel.expr.conformance.proto2.GlobalEnum.GAR");
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

  private CelAbstractSyntaxTree compile(String expression) throws CelValidationException {
    CelAbstractSyntaxTree ast = CEL_COMPILER.parse(expression).getAst();
    if (isParseOnly) {
      return ast;
    }

    return CEL_COMPILER.check(ast).getAst();
  }

  private static byte[] concatenateByteArrays(CelByteString bytes1, CelByteString bytes2) {
    byte[] array1 = bytes1.toByteArray();
    byte[] array2 = bytes2.toByteArray();
    // Handle null or empty arrays gracefully
    if (array1 == null || array1.length == 0) {
      return array2;
    }
    if (array2 == null || array2.length == 0) {
      return array1;
    }

    byte[] combined = new byte[array1.length + array2.length];
    System.arraycopy(array1, 0, combined, 0, array1.length);
    System.arraycopy(array2, 0, combined, array1.length, array2.length);
    return combined;
  }
}
