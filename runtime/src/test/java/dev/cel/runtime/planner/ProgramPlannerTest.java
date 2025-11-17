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
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertThrows;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.primitives.UnsignedLong;
import com.google.testing.junit.testparameterinjector.TestParameter;
import com.google.testing.junit.testparameterinjector.TestParameterInjector;
import dev.cel.common.CelAbstractSyntaxTree;
import dev.cel.common.CelSource;
import dev.cel.common.ast.CelExpr;
import dev.cel.common.types.CelType;
import dev.cel.common.types.CelTypeProvider;
import dev.cel.common.types.CelTypeProvider.CombinedCelTypeProvider;
import dev.cel.common.types.DefaultTypeProvider;
import dev.cel.common.types.ListType;
import dev.cel.common.types.MapType;
import dev.cel.common.types.OptionalType;
import dev.cel.common.types.ProtoMessageTypeProvider;
import dev.cel.common.types.SimpleType;
import dev.cel.common.types.TypeType;
import dev.cel.common.values.CelByteString;
import dev.cel.common.values.NullValue;
import dev.cel.compiler.CelCompiler;
import dev.cel.compiler.CelCompilerFactory;
import dev.cel.expr.conformance.proto3.GlobalEnum;
import dev.cel.expr.conformance.proto3.TestAllTypes;
import dev.cel.extensions.CelExtensions;
import dev.cel.runtime.CelEvaluationException;
import dev.cel.runtime.Program;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(TestParameterInjector.class)
public final class ProgramPlannerTest {
  // Note that the following deps will be built from top-level builder APIs
  private static final CelTypeProvider TYPE_PROVIDER =
      new CombinedCelTypeProvider(
          DefaultTypeProvider.getInstance(),
          new ProtoMessageTypeProvider(ImmutableSet.of(TestAllTypes.getDescriptor())));

  private static final ProgramPlanner PLANNER = ProgramPlanner.newPlanner(TYPE_PROVIDER);
  private static final CelCompiler CEL_COMPILER =
      CelCompilerFactory.standardCelCompilerBuilder()
          .addVar("int_var", SimpleType.INT)
          .addMessageTypes(TestAllTypes.getDescriptor())
          .addLibraries(CelExtensions.optional())
          .build();

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
    if (isParseOnly) {
      // TODO Skip for now, requires attribute qualification
      return;
    }
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
  @SuppressWarnings("unchecked") // test only
  public void plan_createList() throws Exception {
    CelAbstractSyntaxTree ast = compile("[1, 'foo', true, [2, false]]");
    Program program = PLANNER.plan(ast);

    ImmutableList<Object> result = (ImmutableList<Object>) program.eval();

    assertThat(result).containsExactly(1L, "foo", true, ImmutableList.of(2L, false)).inOrder();
  }

  @Test
  public void planIdent_typeLiteral(@TestParameter TypeLiteralTestCase testCase) throws Exception {
    if (isParseOnly) {
      if (testCase.equals(TypeLiteralTestCase.DURATION)
          || testCase.equals(TypeLiteralTestCase.TIMESTAMP)
          || testCase.equals(TypeLiteralTestCase.PROTO_MESSAGE_TYPE)) {
        // TODO Skip for now, requires attribute qualification
        return;
      }
    }
    CelAbstractSyntaxTree ast = compile(testCase.expression);
    Program program = PLANNER.plan(ast);

    TypeType result = (TypeType) program.eval();

    assertThat(result).isEqualTo(testCase.type);
  }

  private CelAbstractSyntaxTree compile(String expression) throws Exception {
    CelAbstractSyntaxTree ast = CEL_COMPILER.parse(expression).getAst();
    if (isParseOnly) {
      return ast;
    }

    return CEL_COMPILER.check(ast).getAst();
  }

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
