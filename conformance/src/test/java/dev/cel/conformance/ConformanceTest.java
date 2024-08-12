// Copyright 2024 Google LLC
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

package dev.cel.conformance;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.extensions.proto.ProtoTruth.assertThat;

import dev.cel.expr.Decl;
import com.google.api.expr.test.v1.SimpleProto.SimpleTest;
import com.google.api.expr.test.v1.proto2.TestAllTypesExtensions;
import com.google.api.expr.v1alpha1.ExprValue;
import com.google.api.expr.v1alpha1.ListValue;
import com.google.api.expr.v1alpha1.MapValue;
import com.google.api.expr.v1alpha1.Value;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.primitives.UnsignedLong;
import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.ExtensionRegistry;
import com.google.protobuf.Message;
import com.google.protobuf.NullValue;
import com.google.protobuf.TypeRegistry;
import dev.cel.checker.CelChecker;
import dev.cel.common.CelDescriptorUtil;
import dev.cel.common.CelDescriptors;
import dev.cel.common.CelOptions;
import dev.cel.common.CelValidationResult;
import dev.cel.common.internal.DefaultInstanceMessageFactory;
import dev.cel.common.types.CelType;
import dev.cel.common.types.ListType;
import dev.cel.common.types.MapType;
import dev.cel.common.types.SimpleType;
import dev.cel.compiler.CelCompilerFactory;
import dev.cel.extensions.CelExtensions;
import dev.cel.extensions.CelOptionalLibrary;
import dev.cel.parser.CelParser;
import dev.cel.parser.CelParserFactory;
import dev.cel.parser.CelStandardMacro;
import dev.cel.runtime.CelEvaluationException;
import dev.cel.runtime.CelRuntime;
import dev.cel.runtime.CelRuntime.Program;
import dev.cel.runtime.CelRuntimeFactory;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import org.junit.runners.model.Statement;

// Qualifying proto2/proto3 TestAllTypes makes it less clear.
@SuppressWarnings("UnnecessarilyFullyQualified")
public final class ConformanceTest extends Statement {

  static final TypeRegistry DEFAULT_TYPE_REGISTRY = newDefaultTypeRegistry();
  static final ExtensionRegistry DEFAULT_EXTENSION_REGISTRY = newDefaultExtensionRegistry();

  private static ExtensionRegistry newDefaultExtensionRegistry() {
    ExtensionRegistry extensionRegistry = ExtensionRegistry.newInstance();
    com.google.api.expr.test.v1.proto2.TestAllTypesExtensions.registerAllExtensions(
        extensionRegistry);

    return extensionRegistry;
  }

  private static TypeRegistry newDefaultTypeRegistry() {
    CelDescriptors allDescriptors =
        CelDescriptorUtil.getAllDescriptorsFromFileDescriptor(
            ImmutableList.of(
                com.google.api.expr.test.v1.proto2.TestAllTypesProto.TestAllTypes.getDescriptor()
                    .getFile(),
                com.google.api.expr.test.v1.proto3.TestAllTypesProto.TestAllTypes.getDescriptor()
                    .getFile(),
                com.google.api.expr.test.v1.proto2.TestAllTypesExtensions.getDescriptor()
                    .getFile()));

    return TypeRegistry.newBuilder().add(allDescriptors.messageTypeDescriptors()).build();
  }

  private static final CelOptions OPTIONS =
      CelOptions.current()
          .enableTimestampEpoch(true)
          .enableUnsignedLongs(true)
          .enableHeterogeneousNumericComparisons(true)
          .enableProtoDifferencerEquality(true)
          .enableOptionalSyntax(true)
          .build();

  private static final CelParser PARSER_WITH_MACROS =
      CelParserFactory.standardCelParserBuilder()
          .setOptions(OPTIONS)
          .addLibraries(
              CelExtensions.bindings(),
              CelExtensions.encoders(),
              CelExtensions.math(OPTIONS),
              CelExtensions.protos(),
              CelExtensions.sets(),
              CelExtensions.strings(),
              CelOptionalLibrary.INSTANCE)
          .setStandardMacros(CelStandardMacro.STANDARD_MACROS)
          .build();

  private static final CelParser PARSER_WITHOUT_MACROS =
      CelParserFactory.standardCelParserBuilder()
          .setOptions(OPTIONS)
          .addLibraries(
              CelExtensions.bindings(),
              CelExtensions.encoders(),
              CelExtensions.math(OPTIONS),
              CelExtensions.protos(),
              CelExtensions.sets(),
              CelExtensions.strings(),
              CelOptionalLibrary.INSTANCE)
          .setStandardMacros()
          .build();

  private static CelParser getParser(SimpleTest test) {
    return test.getDisableMacros() ? PARSER_WITHOUT_MACROS : PARSER_WITH_MACROS;
  }

  private static CelChecker getChecker(SimpleTest test) throws Exception {
    ImmutableList.Builder<Decl> decls =
        ImmutableList.builderWithExpectedSize(test.getTypeEnvCount());
    for (com.google.api.expr.v1alpha1.Decl decl : test.getTypeEnvList()) {
      decls.add(Decl.parseFrom(decl.toByteArray(), DEFAULT_EXTENSION_REGISTRY));
    }
    return CelCompilerFactory.standardCelCheckerBuilder()
        .setOptions(OPTIONS)
        .setContainer(test.getContainer())
        .addDeclarations(decls.build())
        .addFileTypes(TestAllTypesExtensions.getDescriptor())
        .addLibraries(
            CelExtensions.bindings(),
            CelExtensions.encoders(),
            CelExtensions.math(OPTIONS),
            CelExtensions.sets(),
            CelExtensions.strings(),
            CelOptionalLibrary.INSTANCE)
        .addMessageTypes(
            com.google.api.expr.test.v1.proto2.TestAllTypesProto.TestAllTypes.getDescriptor())
        .addMessageTypes(
            com.google.api.expr.test.v1.proto3.TestAllTypesProto.TestAllTypes.getDescriptor())
        .build();
  }

  private static final CelRuntime RUNTIME =
      CelRuntimeFactory.standardCelRuntimeBuilder()
          .setOptions(OPTIONS)
          .addLibraries(
              CelExtensions.encoders(),
              CelExtensions.math(OPTIONS),
              CelExtensions.sets(),
              CelExtensions.strings(),
              CelOptionalLibrary.INSTANCE)
          .addMessageTypes(
              com.google.api.expr.test.v1.proto2.TestAllTypesProto.TestAllTypes.getDescriptor())
          .addMessageTypes(
              com.google.api.expr.test.v1.proto3.TestAllTypesProto.TestAllTypes.getDescriptor())
          .build();

  private static ImmutableMap<String, Object> getBindings(SimpleTest test) throws Exception {
    ImmutableMap.Builder<String, Object> bindings =
        ImmutableMap.builderWithExpectedSize(test.getBindingsCount());
    for (Map.Entry<String, ExprValue> entry : test.getBindingsMap().entrySet()) {
      bindings.put(entry.getKey(), fromExprValue(entry.getValue()));
    }
    return bindings.buildOrThrow();
  }

  private static Object fromExprValue(ExprValue value) throws Exception {
    switch (value.getKindCase()) {
      case VALUE:
        return fromValue(value.getValue());
      default:
        throw new IllegalArgumentException(
            String.format("Unexpected binding value kind: %s", value.getKindCase()));
    }
  }

  private static Object fromValue(Value value) throws Exception {
    switch (value.getKindCase()) {
      case NULL_VALUE:
        return value.getNullValue();
      case BOOL_VALUE:
        return value.getBoolValue();
      case INT64_VALUE:
        return value.getInt64Value();
      case UINT64_VALUE:
        return UnsignedLong.fromLongBits(value.getUint64Value());
      case DOUBLE_VALUE:
        return value.getDoubleValue();
      case STRING_VALUE:
        return value.getStringValue();
      case BYTES_VALUE:
        return value.getBytesValue();
      case ENUM_VALUE:
        return value.getEnumValue();
      case OBJECT_VALUE:
        {
          Any object = value.getObjectValue();
          Descriptor descriptor =
              DEFAULT_TYPE_REGISTRY.getDescriptorForTypeUrl(object.getTypeUrl());
          Message prototype =
              DefaultInstanceMessageFactory.getInstance()
                  .getPrototype(descriptor)
                  .orElseThrow(
                      () ->
                          new NoSuchElementException(
                              "Could not find a default message for: " + descriptor.getFullName()));
          return prototype
              .getParserForType()
              .parseFrom(object.getValue(), DEFAULT_EXTENSION_REGISTRY);
        }
      case MAP_VALUE:
        {
          MapValue map = value.getMapValue();
          ImmutableMap.Builder<Object, Object> builder =
              ImmutableMap.builderWithExpectedSize(map.getEntriesCount());
          for (MapValue.Entry entry : map.getEntriesList()) {
            builder.put(fromValue(entry.getKey()), fromValue(entry.getValue()));
          }
          return builder.buildOrThrow();
        }
      case LIST_VALUE:
        {
          ListValue list = value.getListValue();
          ImmutableList.Builder<Object> builder =
              ImmutableList.builderWithExpectedSize(list.getValuesCount());
          for (Value element : list.getValuesList()) {
            builder.add(fromValue(element));
          }
          return builder.build();
        }
      case TYPE_VALUE:
        return value.getTypeValue();
      default:
        throw new IllegalArgumentException(
            String.format("Unexpected binding value kind: %s", value.getKindCase()));
    }
  }

  private static ExprValue toExprValue(Object object, CelType type) throws Exception {
    if (object instanceof ExprValue) {
      return (ExprValue) object;
    }
    return ExprValue.newBuilder().setValue(toValue(object, type)).build();
  }

  @SuppressWarnings("unchecked")
  private static Value toValue(Object object, CelType type) throws Exception {
    if (object == null) {
      object = NullValue.NULL_VALUE;
    }
    if (object instanceof dev.cel.expr.Value) {
      object =
          Value.parseFrom(
              ((dev.cel.expr.Value) object).toByteArray(), DEFAULT_EXTENSION_REGISTRY);
    }
    if (object instanceof Value) {
      return (Value) object;
    }
    if (object instanceof NullValue) {
      return Value.newBuilder().setNullValue((NullValue) object).build();
    }
    if (object instanceof Boolean) {
      return Value.newBuilder().setBoolValue((Boolean) object).build();
    }
    if (object instanceof UnsignedLong) {
      switch (type.kind()) {
        case UINT:
        case DYN:
        case ANY:
          return Value.newBuilder().setUint64Value(((UnsignedLong) object).longValue()).build();
        default:
          throw new IllegalArgumentException(String.format("Unexpected result type: %s", type));
      }
    }
    if (object instanceof Long) {
      switch (type.kind()) {
        case INT:
        case DYN:
        case ANY:
          return Value.newBuilder().setInt64Value((Long) object).build();
        case UINT:
          return Value.newBuilder().setUint64Value((Long) object).build();
        default:
          throw new IllegalArgumentException(String.format("Unexpected result type: %s", type));
      }
    }
    if (object instanceof Double) {
      return Value.newBuilder().setDoubleValue((Double) object).build();
    }
    if (object instanceof String) {
      switch (type.kind()) {
        case TYPE:
          return Value.newBuilder().setTypeValue((String) object).build();
        case STRING:
        case DYN:
        case ANY:
          return Value.newBuilder().setStringValue((String) object).build();
        default:
          throw new IllegalArgumentException(String.format("Unexpected result type: %s", type));
      }
    }
    if (object instanceof ByteString) {
      return Value.newBuilder().setBytesValue((ByteString) object).build();
    }
    if (object instanceof List) {
      CelType elemType = type instanceof ListType ? ((ListType) type).elemType() : SimpleType.DYN;
      ListValue.Builder builder = ListValue.newBuilder();
      for (Object element : ((List<Object>) object)) {
        builder.addValues(toValue(element, elemType));
      }
      return Value.newBuilder().setListValue(builder.build()).build();
    }
    if (object instanceof Map) {
      CelType keyType = type instanceof MapType ? ((MapType) type).keyType() : SimpleType.DYN;
      CelType valueType = type instanceof MapType ? ((MapType) type).valueType() : SimpleType.DYN;
      MapValue.Builder builder = MapValue.newBuilder();
      for (Map.Entry<Object, Object> entry : ((Map<Object, Object>) object).entrySet()) {
        builder.addEntries(
            MapValue.Entry.newBuilder()
                .setKey(toValue(entry.getKey(), keyType))
                .setValue(toValue(entry.getValue(), valueType))
                .build());
      }
      return Value.newBuilder().setMapValue(builder.build()).build();
    }
    if (object instanceof Message) {
      return Value.newBuilder().setObjectValue(Any.pack((Message) object)).build();
    }
    throw new IllegalArgumentException(
        String.format("Unexpected result type: %s", object.getClass()));
  }

  private static SimpleTest defaultTestMatcherToTrueIfUnset(SimpleTest test) {
    if (test.getResultMatcherCase() == SimpleTest.ResultMatcherCase.RESULTMATCHER_NOT_SET) {
      return test.toBuilder().setValue(Value.newBuilder().setBoolValue(true).build()).build();
    }
    return test;
  }

  private final String name;
  private final SimpleTest test;
  private final boolean skip;

  public ConformanceTest(String name, SimpleTest test, boolean skip) {
    this.name = Preconditions.checkNotNull(name);
    this.test =
        Preconditions.checkNotNull(
            defaultTestMatcherToTrueIfUnset(Preconditions.checkNotNull(test)));
    this.skip = skip;
  }

  public String getName() {
    return name;
  }

  public boolean shouldSkip() {
    return skip;
  }

  @Override
  public void evaluate() throws Throwable {
    CelValidationResult response = getParser(test).parse(test.getExpr(), test.getName());
    assertThat(response.hasError()).isFalse();
    response = getChecker(test).check(response.getAst());
    assertThat(response.hasError()).isFalse();
    Program program = RUNTIME.createProgram(response.getAst());
    ExprValue result = null;
    CelEvaluationException error = null;
    try {
      result = toExprValue(program.eval(getBindings(test)), response.getAst().getResultType());
    } catch (CelEvaluationException e) {
      error = e;
    }
    switch (test.getResultMatcherCase()) {
      case VALUE:
        assertThat(error).isNull();
        assertThat(result).isNotNull();
        assertThat(result)
            .ignoringRepeatedFieldOrderOfFieldDescriptors(
                MapValue.getDescriptor().findFieldByName("entries"))
            .unpackingAnyUsing(DEFAULT_TYPE_REGISTRY, DEFAULT_EXTENSION_REGISTRY)
            .isEqualTo(ExprValue.newBuilder().setValue(test.getValue()).build());
        break;
      case EVAL_ERROR:
        assertThat(result).isNull();
        assertThat(error).isNotNull();
        break;
      default:
        throw new IllegalStateException(
            String.format("Unexpected matcher kind: %s", test.getResultMatcherCase()));
    }
  }
}
