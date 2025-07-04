package dev.cel.runtime.planner;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableMap;
import com.google.common.primitives.UnsignedLong;
import com.google.testing.junit.testparameterinjector.TestParameter;
import com.google.testing.junit.testparameterinjector.TestParameterInjector;
import dev.cel.common.CelAbstractSyntaxTree;
import dev.cel.common.CelFunctionDecl;
import dev.cel.common.CelOverloadDecl;
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
import dev.cel.runtime.CelLiteRuntime.Program;
import dev.cel.runtime.CelRuntime;
import dev.cel.runtime.CelRuntimeFactory;
import java.nio.charset.StandardCharsets;

import dev.cel.runtime.DefaultDispatcher;
import dev.cel.runtime.Dispatcher;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(TestParameterInjector.class)
public final class ProgramPlannerTest {
  private static final CelCompiler CEL_COMPILER =
      CelCompilerFactory.standardCelCompilerBuilder()
          .addVar("int_var", SimpleType.INT)
          .addFunctionDeclarations(CelFunctionDecl.newFunctionDeclaration(
                "zero", CelOverloadDecl.newGlobalOverload("zero", SimpleType.INT)
          ))
          .addLibraries(CelOptionalLibrary.INSTANCE)
          .addMessageTypes(TestAllTypes.getDescriptor())
          .build();
  private static final ProgramPlanner PLANNER = ProgramPlanner.newPlanner(
      DefaultTypeProvider.create(),
      new CelValueConverter(),
      newDispatcher()
  );

  private static Dispatcher newDispatcher() {
    return DefaultDispatcher.create();
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
  public void smokeTest() throws Exception {
    CelAbstractSyntaxTree ast = compile("google.protobuf.Duration");
    CelRuntime.Program program = CelRuntimeFactory.standardCelRuntimeBuilder().build().createProgram(ast);

    TypeType result = (TypeType) program.eval();

    assertThat(result).isEqualTo(TypeType.create(SimpleType.UINT));
  }

  private CelAbstractSyntaxTree compile(String expression) throws CelValidationException {
    CelAbstractSyntaxTree ast = CEL_COMPILER.parse(expression).getAst();
    if (isParseOnly) {
      return ast;
    }

    return CEL_COMPILER.check(ast).getAst();
  }
}
