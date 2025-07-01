package dev.cel.runtime;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableCollection;
import com.google.common.primitives.UnsignedLong;
import com.google.testing.junit.testparameterinjector.TestParameter;
import com.google.testing.junit.testparameterinjector.TestParameterInjector;
import dev.cel.common.CelAbstractSyntaxTree;
import dev.cel.common.types.CelType;
import dev.cel.common.types.CelTypeProvider;
import dev.cel.common.types.SimpleType;
import dev.cel.common.types.TypeType;
import dev.cel.common.values.CelByteString;
import dev.cel.common.values.NullValue;
import dev.cel.compiler.CelCompiler;
import dev.cel.compiler.CelCompilerFactory;
import dev.cel.expr.conformance.proto2.TestAllTypes;
import dev.cel.runtime.CelLiteRuntime.Program;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(TestParameterInjector.class)
public final class ProgramPlannerTest {
  private static final CelCompiler CEL_COMPILER =
      CelCompilerFactory.standardCelCompilerBuilder()
          .addMessageTypes(TestAllTypes.getDescriptor())
          .build();
  private static ProgramPlanner PLANNER = new ProgramPlanner(
      new CelTypeProvider() {
        @Override
        public ImmutableCollection<CelType> types() {
          throw new UnsupportedOperationException();
        }
        @Override
        public Optional<CelType> findType(String typeName) {
          return Optional.empty();
        }
      }
  );

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
    CelAbstractSyntaxTree ast = CEL_COMPILER.compile(testCase.expression).getAst();
    Program program = PLANNER.plan(ast);

    Object result = program.eval();

    assertThat(result).isEqualTo(testCase.expected);
  }

  @Test
  public void planIdent_enum() throws Exception {
    CelAbstractSyntaxTree ast = CEL_COMPILER.compile("cel.expr.conformance.proto2.GlobalEnum.GAR").getAst();
    Program program = PLANNER.plan(ast);

    Object result = program.eval();

    assertThat(result).isEqualTo(1);
  }

  @Test
  public void planIdent_typeLiteral() throws Exception {
    CelAbstractSyntaxTree ast = CEL_COMPILER.compile("uint").getAst();
    Program program = PLANNER.plan(ast);

    TypeType result = (TypeType) program.eval();

    assertThat(result).isEqualTo(TypeType.create(SimpleType.UINT));
  }

  @Test
  public void smokeTest() throws Exception {
    CelAbstractSyntaxTree ast = CEL_COMPILER.compile("uint").getAst();
    CelRuntime.Program program = CelRuntimeFactory.standardCelRuntimeBuilder().build().createProgram(ast);

    TypeType result = (TypeType) program.eval();

    assertThat(result).isEqualTo(TypeType.create(SimpleType.UINT));

  }
}
