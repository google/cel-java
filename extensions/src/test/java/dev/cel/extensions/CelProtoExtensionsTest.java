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
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.testing.junit.testparameterinjector.TestParameter;
import com.google.testing.junit.testparameterinjector.TestParameterInjector;
import com.google.testing.junit.testparameterinjector.TestParameters;
import dev.cel.common.CelAbstractSyntaxTree;
import dev.cel.common.CelFunctionDecl;
import dev.cel.common.CelOverloadDecl;
import dev.cel.common.CelValidationException;
import dev.cel.common.types.SimpleType;
import dev.cel.common.types.StructTypeReference;
import dev.cel.compiler.CelCompiler;
import dev.cel.compiler.CelCompilerFactory;
import dev.cel.parser.CelStandardMacro;
import dev.cel.runtime.CelRuntime;
import dev.cel.runtime.CelRuntime.CelFunctionBinding;
import dev.cel.runtime.CelRuntimeFactory;
import dev.cel.testing.testdata.proto2.MessagesProto2Extensions;
import dev.cel.testing.testdata.proto2.NestedMessageInsideExtensions;
import dev.cel.testing.testdata.proto2.Proto2ExtensionScopedMessage;
import dev.cel.testing.testdata.proto2.Proto2Message;
import dev.cel.testing.testdata.proto2.StringHolder;
import dev.cel.testing.testdata.proto2.TestAllTypesProto.TestAllTypes.NestedEnum;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(TestParameterInjector.class)
public final class CelProtoExtensionsTest {

  private static final CelCompiler CEL_COMPILER =
      CelCompilerFactory.standardCelCompilerBuilder()
          .addLibraries(CelExtensions.protos())
          .setStandardMacros(CelStandardMacro.STANDARD_MACROS)
          .addFileTypes(MessagesProto2Extensions.getDescriptor())
          .addVar(
              "msg", StructTypeReference.create("dev.cel.testing.testdata.proto2.Proto2Message"))
          .setContainer("dev.cel.testing.testdata.proto2")
          .build();

  private static final CelRuntime CEL_RUNTIME =
      CelRuntimeFactory.standardCelRuntimeBuilder()
          .addFileTypes(MessagesProto2Extensions.getDescriptor())
          .build();

  private static final Proto2Message PACKAGE_SCOPED_EXT_MSG =
      Proto2Message.newBuilder()
          .setExtension(MessagesProto2Extensions.int32Ext, 1)
          .setExtension(
              MessagesProto2Extensions.nestedExt,
              Proto2Message.newBuilder().setSingleInt32(5).build())
          .setExtension(MessagesProto2Extensions.nestedEnumExt, NestedEnum.BAR)
          .setExtension(
              MessagesProto2Extensions.repeatedStringHolderExt,
              ImmutableList.of(
                  StringHolder.newBuilder().setS("A").build(),
                  StringHolder.newBuilder().setS("B").build()))
          .build();

  private static final Proto2Message MESSAGE_SCOPED_EXT_MSG =
      Proto2Message.newBuilder()
          .setExtension(
              Proto2ExtensionScopedMessage.nestedMessageInsideExt,
              NestedMessageInsideExtensions.newBuilder().setField("test").build())
          .setExtension(Proto2ExtensionScopedMessage.int64Ext, 1L)
          .build();

  @Test
  @TestParameters("{expr: 'proto.hasExt(msg, dev.cel.testing.testdata.proto2.int32_ext)'}")
  @TestParameters("{expr: 'proto.hasExt(msg, dev.cel.testing.testdata.proto2.nested_ext)'}")
  @TestParameters("{expr: 'proto.hasExt(msg, dev.cel.testing.testdata.proto2.nested_enum_ext)'}")
  @TestParameters(
      "{expr: 'proto.hasExt(msg, dev.cel.testing.testdata.proto2.repeated_string_holder_ext)'}")
  @TestParameters(
      "{expr: '!proto.hasExt(msg, dev.cel.testing.testdata.proto2.test_all_types_ext)'}")
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
          + " dev.cel.testing.testdata.proto2.Proto2ExtensionScopedMessage.nested_message_inside_ext)'}")
  @TestParameters(
      "{expr: 'proto.hasExt(msg,"
          + " dev.cel.testing.testdata.proto2.Proto2ExtensionScopedMessage.int64_ext)'}")
  @TestParameters(
      "{expr: '!proto.hasExt(msg,"
          + " dev.cel.testing.testdata.proto2.Proto2ExtensionScopedMessage.message_scoped_nested_ext)'}")
  @TestParameters(
      "{expr: '!proto.hasExt(msg,"
          + " dev.cel.testing.testdata.proto2.Proto2ExtensionScopedMessage.string_ext)'}")
  public void hasExt_messageScoped_success(String expr) throws Exception {
    CelAbstractSyntaxTree ast = CEL_COMPILER.compile(expr).getAst();
    boolean result =
        (boolean)
            CEL_RUNTIME.createProgram(ast).eval(ImmutableMap.of("msg", MESSAGE_SCOPED_EXT_MSG));

    assertThat(result).isTrue();
  }

  @Test
  @TestParameters("{expr: 'msg.hasExt(''dev.cel.testing.testdata.proto2.int32_ext'', 0)'}")
  @TestParameters("{expr: 'dyn(msg).hasExt(''dev.cel.testing.testdata.proto2.int32_ext'', 0)'}")
  public void hasExt_nonProtoNamespace_success(String expr) throws Exception {
    StructTypeReference proto2MessageTypeReference =
        StructTypeReference.create("dev.cel.testing.testdata.proto2.Proto2Message");
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
                    ImmutableList.of(Proto2Message.class, String.class, Long.class),
                    (arg) -> {
                      Proto2Message msg = (Proto2Message) arg[0];
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
                    .compile("!proto.hasExt(msg, dev.cel.testing.testdata.proto2.undefined_field)")
                    .getAst());

    assertThat(exception)
        .hasMessageThat()
        .contains("undefined field 'dev.cel.testing.testdata.proto2.undefined_field'");
  }

  @Test
  @TestParameters("{expr: 'proto.getExt(msg, dev.cel.testing.testdata.proto2.int32_ext) == 1'}")
  @TestParameters(
      "{expr: 'proto.getExt(msg, dev.cel.testing.testdata.proto2.nested_ext) =="
          + " Proto2Message{single_int32: 5}'}")
  @TestParameters(
      "{expr: 'proto.getExt(msg, dev.cel.testing.testdata.proto2.nested_enum_ext) =="
          + " TestAllTypes.NestedEnum.BAR'}")
  @TestParameters(
      "{expr: 'proto.getExt(msg, dev.cel.testing.testdata.proto2.repeated_string_holder_ext) =="
          + " [StringHolder{s: ''A''}, StringHolder{s: ''B''}]'}")
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
          + " dev.cel.testing.testdata.proto2.Proto2ExtensionScopedMessage.nested_message_inside_ext)"
          + " == NestedMessageInsideExtensions{field: ''test''}'}")
  @TestParameters(
      "{expr: 'proto.getExt(msg,"
          + " dev.cel.testing.testdata.proto2.Proto2ExtensionScopedMessage.int64_ext) == 1'}")
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
                    .compile("!proto.getExt(msg, dev.cel.testing.testdata.proto2.undefined_field)")
                    .getAst());

    assertThat(exception)
        .hasMessageThat()
        .contains("undefined field 'dev.cel.testing.testdata.proto2.undefined_field'");
  }

  @Test
  @TestParameters("{expr: 'msg.getExt(''dev.cel.testing.testdata.proto2.int32_ext'', 0) == 1'}")
  @TestParameters(
      "{expr: 'dyn(msg).getExt(''dev.cel.testing.testdata.proto2.int32_ext'', 0) == 1'}")
  public void getExt_nonProtoNamespace_success(String expr) throws Exception {
    StructTypeReference proto2MessageTypeReference =
        StructTypeReference.create("dev.cel.testing.testdata.proto2.Proto2Message");
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
                    ImmutableList.of(Proto2Message.class, String.class, Long.class),
                    (arg) -> {
                      Proto2Message msg = (Proto2Message) arg[0];
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
            + " has(dev.cel.testing.testdata.proto2.Proto2ExtensionScopedMessage.int64_ext))",
        "ERROR: <input>:1:49: invalid extension field\n"
            + " | proto.getExt(Proto2ExtensionScopedMessage{},"
            + " has(dev.cel.testing.testdata.proto2.Proto2ExtensionScopedMessage.int64_ext))\n"
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
