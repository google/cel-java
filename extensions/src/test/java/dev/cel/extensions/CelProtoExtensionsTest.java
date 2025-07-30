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

package dev.cel.extensions;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.protobuf.Any;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.ExtensionRegistry;
import com.google.testing.junit.testparameterinjector.TestParameter;
import com.google.testing.junit.testparameterinjector.TestParameterInjector;
import com.google.testing.junit.testparameterinjector.TestParameters;
import dev.cel.bundle.Cel;
import dev.cel.bundle.CelFactory;
import dev.cel.common.CelAbstractSyntaxTree;
import dev.cel.common.CelContainer;
import dev.cel.common.CelFunctionDecl;
import dev.cel.common.CelOptions;
import dev.cel.common.CelOverloadDecl;
import dev.cel.common.CelValidationException;
import dev.cel.common.types.SimpleType;
import dev.cel.common.types.StructTypeReference;
import dev.cel.compiler.CelCompiler;
import dev.cel.compiler.CelCompilerFactory;
import dev.cel.expr.conformance.proto2.Proto2ExtensionScopedMessage;
import dev.cel.expr.conformance.proto2.TestAllTypes;
import dev.cel.expr.conformance.proto2.TestAllTypes.NestedEnum;
import dev.cel.expr.conformance.proto2.TestAllTypesExtensions;
import dev.cel.parser.CelMacro;
import dev.cel.parser.CelStandardMacro;
import dev.cel.runtime.CelFunctionBinding;
import dev.cel.runtime.CelRuntime;
import dev.cel.runtime.CelRuntimeFactory;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(TestParameterInjector.class)
public final class CelProtoExtensionsTest {

  private static final CelCompiler CEL_COMPILER =
      CelCompilerFactory.standardCelCompilerBuilder()
          .addLibraries(CelExtensions.protos())
          .setStandardMacros(CelStandardMacro.STANDARD_MACROS)
          .addFileTypes(TestAllTypesExtensions.getDescriptor())
          .addVar("msg", StructTypeReference.create("cel.expr.conformance.proto2.TestAllTypes"))
          .setContainer(CelContainer.ofName("cel.expr.conformance.proto2"))
          .build();

  private static final CelRuntime CEL_RUNTIME =
      CelRuntimeFactory.standardCelRuntimeBuilder()
          .addFileTypes(TestAllTypesExtensions.getDescriptor())
          .build();

  private static final TestAllTypes PACKAGE_SCOPED_EXT_MSG =
      TestAllTypes.newBuilder()
          .setExtension(TestAllTypesExtensions.int32Ext, 1)
          .setExtension(
              TestAllTypesExtensions.nestedExt, TestAllTypes.newBuilder().setSingleInt32(5).build())
          .setExtension(TestAllTypesExtensions.nestedEnumExt, NestedEnum.BAR)
          .setExtension(
              TestAllTypesExtensions.repeatedTestAllTypes,
              ImmutableList.of(
                  TestAllTypes.newBuilder().setSingleString("A").build(),
                  TestAllTypes.newBuilder().setSingleString("B").build()))
          .build();

  private static final TestAllTypes MESSAGE_SCOPED_EXT_MSG =
      TestAllTypes.newBuilder()
          .setExtension(
              Proto2ExtensionScopedMessage.messageScopedNestedExt,
              TestAllTypes.newBuilder().setSingleString("test").build())
          .setExtension(Proto2ExtensionScopedMessage.int64Ext, 1L)
          .build();

  @Test
  public void library() {
    CelExtensionLibrary<?> library =
        CelExtensions.getExtensionLibrary("protos", CelOptions.DEFAULT);
    assertThat(library.name()).isEqualTo("protos");
    assertThat(library.latest().version()).isEqualTo(0);
    assertThat(library.version(0).functions()).isEmpty();
    assertThat(library.version(0).macros().stream().map(CelMacro::getFunction))
        .containsExactly("hasExt", "getExt");
  }

  @Test
  @TestParameters("{expr: 'proto.hasExt(msg, cel.expr.conformance.proto2.int32_ext)'}")
  @TestParameters("{expr: 'proto.hasExt(msg, cel.expr.conformance.proto2.nested_ext)'}")
  @TestParameters("{expr: 'proto.hasExt(msg, cel.expr.conformance.proto2.nested_enum_ext)'}")
  @TestParameters(
      "{expr: 'proto.hasExt(msg, cel.expr.conformance.proto2.repeated_test_all_types)'}")
  @TestParameters("{expr: '!proto.hasExt(msg, cel.expr.conformance.proto2.test_all_types_ext)'}")
  public void hasExt_packageScoped_success(String expr) throws Exception {
    CelAbstractSyntaxTree ast = CEL_COMPILER.compile(expr).getAst();
    boolean result =
        (boolean)
            CEL_RUNTIME.createProgram(ast).eval(ImmutableMap.of("msg", PACKAGE_SCOPED_EXT_MSG));

    assertThat(result).isTrue();
  }

  @Test
  @TestParameters(
      "{expr: 'proto.hasExt(msg,"
          + " cel.expr.conformance.proto2.Proto2ExtensionScopedMessage.message_scoped_nested_ext)'}")
  @TestParameters(
      "{expr: 'proto.hasExt(msg,"
          + " cel.expr.conformance.proto2.Proto2ExtensionScopedMessage.int64_ext)'}")
  @TestParameters(
      "{expr: '!proto.hasExt(msg,"
          + " cel.expr.conformance.proto2.Proto2ExtensionScopedMessage.message_scoped_repeated_test_all_types)'}")
  @TestParameters(
      "{expr: '!proto.hasExt(msg,"
          + " cel.expr.conformance.proto2.Proto2ExtensionScopedMessage.nested_enum_ext)'}")
  public void hasExt_messageScoped_success(String expr) throws Exception {
    CelAbstractSyntaxTree ast = CEL_COMPILER.compile(expr).getAst();
    boolean result =
        (boolean)
            CEL_RUNTIME.createProgram(ast).eval(ImmutableMap.of("msg", MESSAGE_SCOPED_EXT_MSG));

    assertThat(result).isTrue();
  }

  @Test
  @TestParameters("{expr: 'msg.hasExt(''cel.expr.conformance.proto2.int32_ext'', 0)'}")
  @TestParameters("{expr: 'dyn(msg).hasExt(''cel.expr.conformance.proto2.int32_ext'', 0)'}")
  public void hasExt_nonProtoNamespace_success(String expr) throws Exception {
    StructTypeReference proto2MessageTypeReference =
        StructTypeReference.create("cel.expr.conformance.proto2.TestAllTypes");
    CelCompiler celCompiler =
        CelCompilerFactory.standardCelCompilerBuilder()
            .addLibraries(CelExtensions.protos())
            .addVar("msg", proto2MessageTypeReference)
            .addFunctionDeclarations(
                CelFunctionDecl.newFunctionDeclaration(
                    "hasExt",
                    CelOverloadDecl.newMemberOverload(
                        "msg_hasExt",
                        SimpleType.BOOL,
                        ImmutableList.of(
                            proto2MessageTypeReference, SimpleType.STRING, SimpleType.INT))))
            .build();
    CelRuntime celRuntime =
        CelRuntimeFactory.standardCelRuntimeBuilder()
            .addFunctionBindings(
                CelFunctionBinding.from(
                    "msg_hasExt",
                    ImmutableList.of(TestAllTypes.class, String.class, Long.class),
                    (arg) -> {
                      TestAllTypes msg = (TestAllTypes) arg[0];
                      String extensionField = (String) arg[1];
                      return msg.getAllFields().keySet().stream()
                          .anyMatch(fd -> fd.getFullName().equals(extensionField));
                    }))
            .build();

    CelAbstractSyntaxTree ast = celCompiler.compile(expr).getAst();
    boolean result =
        (boolean)
            celRuntime.createProgram(ast).eval(ImmutableMap.of("msg", PACKAGE_SCOPED_EXT_MSG));

    assertThat(result).isTrue();
  }

  @Test
  public void hasExt_undefinedField_throwsException() {
    CelValidationException exception =
        assertThrows(
            CelValidationException.class,
            () ->
                CEL_COMPILER
                    .compile("!proto.hasExt(msg, cel.expr.conformance.proto2.undefined_field)")
                    .getAst());

    assertThat(exception)
        .hasMessageThat()
        .contains("undefined field 'cel.expr.conformance.proto2.undefined_field'");
  }

  @Test
  @TestParameters("{expr: 'proto.getExt(msg, cel.expr.conformance.proto2.int32_ext) == 1'}")
  @TestParameters(
      "{expr: 'proto.getExt(msg, cel.expr.conformance.proto2.nested_ext) =="
          + " TestAllTypes{single_int32: 5}'}")
  @TestParameters(
      "{expr: 'proto.getExt(msg, cel.expr.conformance.proto2.nested_enum_ext) =="
          + " TestAllTypes.NestedEnum.BAR'}")
  @TestParameters(
      "{expr: 'proto.getExt(msg, cel.expr.conformance.proto2.repeated_test_all_types) =="
          + " [TestAllTypes{single_string: ''A''}, TestAllTypes{single_string: ''B''}]'}")
  public void getExt_packageScoped_success(String expr) throws Exception {
    CelAbstractSyntaxTree ast = CEL_COMPILER.compile(expr).getAst();
    boolean result =
        (boolean)
            CEL_RUNTIME.createProgram(ast).eval(ImmutableMap.of("msg", PACKAGE_SCOPED_EXT_MSG));

    assertThat(result).isTrue();
  }

  @Test
  @TestParameters(
      "{expr: 'proto.getExt(msg,"
          + " cel.expr.conformance.proto2.Proto2ExtensionScopedMessage.message_scoped_nested_ext)"
          + " == TestAllTypes{single_string: ''test''}'}")
  @TestParameters(
      "{expr: 'proto.getExt(msg,"
          + " cel.expr.conformance.proto2.Proto2ExtensionScopedMessage.int64_ext) == 1'}")
  public void getExt_messageScopedSuccess(String expr) throws Exception {
    CelAbstractSyntaxTree ast = CEL_COMPILER.compile(expr).getAst();
    boolean result =
        (boolean)
            CEL_RUNTIME.createProgram(ast).eval(ImmutableMap.of("msg", MESSAGE_SCOPED_EXT_MSG));

    assertThat(result).isTrue();
  }

  @Test
  public void getExt_undefinedField_throwsException() {
    CelValidationException exception =
        assertThrows(
            CelValidationException.class,
            () ->
                CEL_COMPILER
                    .compile("!proto.getExt(msg, cel.expr.conformance.proto2.undefined_field)")
                    .getAst());

    assertThat(exception)
        .hasMessageThat()
        .contains("undefined field 'cel.expr.conformance.proto2.undefined_field'");
  }

  @Test
  @TestParameters("{expr: 'msg.getExt(''cel.expr.conformance.proto2.int32_ext'', 0) == 1'}")
  @TestParameters("{expr: 'dyn(msg).getExt(''cel.expr.conformance.proto2.int32_ext'', 0) == 1'}")
  public void getExt_nonProtoNamespace_success(String expr) throws Exception {
    StructTypeReference proto2MessageTypeReference =
        StructTypeReference.create("cel.expr.conformance.proto2.TestAllTypes");
    CelCompiler celCompiler =
        CelCompilerFactory.standardCelCompilerBuilder()
            .addLibraries(CelExtensions.protos())
            .addVar("msg", proto2MessageTypeReference)
            .addFunctionDeclarations(
                CelFunctionDecl.newFunctionDeclaration(
                    "getExt",
                    CelOverloadDecl.newMemberOverload(
                        "msg_getExt",
                        SimpleType.DYN,
                        ImmutableList.of(
                            proto2MessageTypeReference, SimpleType.STRING, SimpleType.INT))))
            .build();
    CelRuntime celRuntime =
        CelRuntimeFactory.standardCelRuntimeBuilder()
            .addFunctionBindings(
                CelFunctionBinding.from(
                    "msg_getExt",
                    ImmutableList.of(TestAllTypes.class, String.class, Long.class),
                    (arg) -> {
                      TestAllTypes msg = (TestAllTypes) arg[0];
                      String extensionField = (String) arg[1];
                      FieldDescriptor extensionDescriptor =
                          msg.getAllFields().keySet().stream()
                              .filter(fd -> fd.getFullName().equals(extensionField))
                              .findAny()
                              .get();
                      return msg.getField(extensionDescriptor);
                    }))
            .build();

    CelAbstractSyntaxTree ast = celCompiler.compile(expr).getAst();
    boolean result =
        (boolean)
            celRuntime.createProgram(ast).eval(ImmutableMap.of("msg", PACKAGE_SCOPED_EXT_MSG));

    assertThat(result).isTrue();
  }

  @Test
  public void getExt_onAnyPackedExtensionField_success() throws Exception {
    ExtensionRegistry extensionRegistry = ExtensionRegistry.newInstance();
    TestAllTypesExtensions.registerAllExtensions(extensionRegistry);
    Cel cel =
        CelFactory.standardCelBuilder()
            .addCompilerLibraries(CelExtensions.protos())
            .addFileTypes(TestAllTypesExtensions.getDescriptor())
            .setExtensionRegistry(extensionRegistry)
            .addVar("msg", StructTypeReference.create("cel.expr.conformance.proto2.TestAllTypes"))
            .build();
    CelAbstractSyntaxTree ast =
        cel.compile("proto.getExt(msg, cel.expr.conformance.proto2.int32_ext)").getAst();
    Any anyMsg =
        Any.pack(
            TestAllTypes.newBuilder().setExtension(TestAllTypesExtensions.int32Ext, 1).build());

    Long result = (Long) cel.createProgram(ast).eval(ImmutableMap.of("msg", anyMsg));

    assertThat(result).isEqualTo(1);
  }

  private enum ParseErrorTestCase {
    FIELD_NOT_FULLY_QUALIFIED(
        "proto.getExt(Proto2ExtensionScopedMessage{}, int64_ext)",
        "ERROR: <input>:1:46: invalid extension field\n"
            + " | proto.getExt(Proto2ExtensionScopedMessage{}, int64_ext)\n"
            + " | .............................................^"),
    CALL(
        "proto.hasExt(Proto2ExtensionScopedMessage{}, call().int64_ext)",
        "ERROR: <input>:1:52: invalid extension field\n"
            + " | proto.hasExt(Proto2ExtensionScopedMessage{}, call().int64_ext)\n"
            + " | ...................................................^"),
    FIELD_INSIDE_PRESENCE_TEST(
        "proto.getExt(Proto2ExtensionScopedMessage{},"
            + " has(cel.expr.conformance.proto2.Proto2ExtensionScopedMessage.int64_ext))",
        "ERROR: <input>:1:49: invalid extension field\n"
            + " | proto.getExt(Proto2ExtensionScopedMessage{},"
            + " has(cel.expr.conformance.proto2.Proto2ExtensionScopedMessage.int64_ext))\n"
            + " | ................................................^");

    private final String expr;
    private final String error;

    ParseErrorTestCase(String expr, String error) {
      this.expr = expr;
      this.error = error;
    }
  }

  @Test
  public void parseErrors(@TestParameter ParseErrorTestCase testcase) {
    CelValidationException e =
        assertThrows(
            CelValidationException.class, () -> CEL_COMPILER.compile(testcase.expr).getAst());

    assertThat(e).hasMessageThat().isEqualTo(testcase.error);
  }
}
