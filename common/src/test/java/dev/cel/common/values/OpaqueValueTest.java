// Copyright 2023 Google LLC
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

package dev.cel.common.values;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.errorprone.annotations.Immutable;
import dev.cel.bundle.Cel;
import dev.cel.bundle.CelFactory;
import dev.cel.common.CelAbstractSyntaxTree;
import dev.cel.common.types.CelType;
import dev.cel.common.types.CelTypeProvider;
import dev.cel.common.types.OpaqueType;
import java.util.Map;
import java.util.Optional;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class OpaqueValueTest {
  @Test
  public void opaqueValue_construct() {
    OpaqueValue opaqueValue = OpaqueValue.create("opaque_type_name", "test");

    assertThat(opaqueValue.value()).isEqualTo("test");
    assertThat(opaqueValue.isZeroValue()).isFalse();
    assertThat(opaqueValue.celType()).isEqualTo(OpaqueType.create("opaque_type_name"));
  }

  @Test
  public void create_nullValue_throws() {
    assertThrows(NullPointerException.class, () -> OpaqueValue.create("opaque_type_name", null));
  }

  private static final OpaqueType CUSTOM_OPAQUE_TYPE = OpaqueType.create("custom_opaque_type");

  private static final CelTypeProvider CUSTOM_OPAQUE_TYPE_PROVIDER =
      new CelTypeProvider() {
        @Override
        public ImmutableList<CelType> types() {
          return ImmutableList.of(CUSTOM_OPAQUE_TYPE);
        }

        @Override
        public Optional<CelType> findType(String typeName) {
          return typeName.equals(CUSTOM_OPAQUE_TYPE.name())
              ? Optional.of(CUSTOM_OPAQUE_TYPE)
              : Optional.empty();
        }
      };

  private static final CelValueProvider CUSTOM_OPAQUE_VALUE_PROVIDER =
      new CelValueProvider() {
        @Override
        public Optional<Object> newValue(String structType, Map<String, Object> fields) {
          return Optional.empty();
        }

        @Override
        public CelValueConverter celValueConverter() {
          return new CelValueConverter() {
            @Override
            public Object toRuntimeValue(Object value) {
              if (value instanceof CustomOpaqueObject) {
                CustomOpaqueObject customOpaqueObject = (CustomOpaqueObject) value;
                return new CelCustomOpaqueValue(customOpaqueObject);
              }
              return super.toRuntimeValue(value);
            }
          };
        }
      };

  private static final CelValueProvider WRAPPED_CUSTOM_OPAQUE_VALUE_PROVIDER =
      new CelValueProvider() {
        @Override
        public Optional<Object> newValue(String structType, Map<String, Object> fields) {
          return Optional.empty();
        }

        @Override
        public CelValueConverter celValueConverter() {
          return new CelValueConverter() {
            @Override
            public Object toRuntimeValue(Object value) {
              if (value instanceof CustomOpaqueObject) {
                CustomOpaqueObject customOpaqueObject = (CustomOpaqueObject) value;
                return OpaqueValue.create(CUSTOM_OPAQUE_TYPE.name(), customOpaqueObject);
              }
              return super.toRuntimeValue(value);
            }
          };
        }
      };

  @Immutable
  private static class CustomOpaqueObject {
    private final String value;

    CustomOpaqueObject(String value) {
      this.value = value;
    }

    String getValue() {
      return value;
    }
  }

  @Immutable
  private static class CelCustomOpaqueValue extends OpaqueValue {
    private final CustomOpaqueObject obj;

    CelCustomOpaqueValue(CustomOpaqueObject obj) {
      this.obj = obj;
    }

    @Override
    public CustomOpaqueObject value() {
      return obj;
    }

    @Override
    public OpaqueType celType() {
      return CUSTOM_OPAQUE_TYPE;
    }
  }

  @Test
  public void evaluate_customOpaqueValue_asVariable() throws Exception {
    Cel cel =
        CelFactory.plannerCelBuilder()
            .addVar("opaque_var", CUSTOM_OPAQUE_TYPE)
            .setTypeProvider(CUSTOM_OPAQUE_TYPE_PROVIDER)
            .setValueProvider(CUSTOM_OPAQUE_VALUE_PROVIDER)
            .build();
    CelAbstractSyntaxTree ast = cel.compile("opaque_var").getAst();

    CustomOpaqueObject rawValue = new CustomOpaqueObject("hello");
    Object result = cel.createProgram(ast).eval(ImmutableMap.of("opaque_var", rawValue));

    assertThat(result).isInstanceOf(CustomOpaqueObject.class);
    assertThat(((CustomOpaqueObject) result).getValue()).isEqualTo("hello");
  }

  @Test
  public void evaluate_typeOfCustomOpaqueValue() throws Exception {
    Cel cel =
        CelFactory.plannerCelBuilder()
            .addVar("opaque_var", CUSTOM_OPAQUE_TYPE)
            .setTypeProvider(CUSTOM_OPAQUE_TYPE_PROVIDER)
            .setValueProvider(CUSTOM_OPAQUE_VALUE_PROVIDER)
            .build();
    CelAbstractSyntaxTree ast = cel.compile("type(opaque_var) == custom_opaque_type").getAst();

    CustomOpaqueObject rawValue = new CustomOpaqueObject("hello");
    Object result = cel.createProgram(ast).eval(ImmutableMap.of("opaque_var", rawValue));

    assertThat(result).isEqualTo(true);
  }

  @Test
  public void evaluate_typeOfCustomOpaqueValue_wrapped() throws Exception {
    Cel cel =
        CelFactory.plannerCelBuilder()
            .addVar("opaque_var", CUSTOM_OPAQUE_TYPE)
            .setTypeProvider(CUSTOM_OPAQUE_TYPE_PROVIDER)
            .setValueProvider(WRAPPED_CUSTOM_OPAQUE_VALUE_PROVIDER)
            .build();
    CelAbstractSyntaxTree ast = cel.compile("type(opaque_var) == custom_opaque_type").getAst();

    CustomOpaqueObject rawValue = new CustomOpaqueObject("hello");
    Object result = cel.createProgram(ast).eval(ImmutableMap.of("opaque_var", rawValue));

    assertThat(result).isEqualTo(true);
  }

  @Immutable
  private static class SelfReturningOpaqueObject extends OpaqueValue {
    SelfReturningOpaqueObject() {}

    @Override
    public Object value() {
      return this;
    }

    @Override
    public OpaqueType celType() {
      return CUSTOM_OPAQUE_TYPE;
    }
  }

  @Test
  public void evaluate_selfReturningOpaqueValue_noConverter() throws Exception {
    Cel cel =
        CelFactory.plannerCelBuilder()
            .addVar("opaque_var", CUSTOM_OPAQUE_TYPE)
            .setTypeProvider(CUSTOM_OPAQUE_TYPE_PROVIDER)
            .build();
    CelAbstractSyntaxTree ast = cel.compile("type(opaque_var) == custom_opaque_type").getAst();

    SelfReturningOpaqueObject rawValue = new SelfReturningOpaqueObject();
    Object result = cel.createProgram(ast).eval(ImmutableMap.of("opaque_var", rawValue));

    assertThat(result).isEqualTo(true);
  }
}

