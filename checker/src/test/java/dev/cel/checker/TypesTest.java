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

package dev.cel.checker;

import static com.google.common.truth.Truth.assertThat;

import dev.cel.expr.Type;
import dev.cel.expr.Type.PrimitiveType;
import dev.cel.common.types.CelKind;
import dev.cel.common.types.CelProtoTypes;
import dev.cel.common.types.CelType;
import dev.cel.common.types.SimpleType;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class TypesTest {

  @Test
  public void isAssignable_usingProtoTypes() {
    Map<Type, Type> subs = new HashMap<>();
    Type typeParamA = CelProtoTypes.createTypeParam("A");
    Type stringType = CelProtoTypes.create(PrimitiveType.STRING);

    Map<Type, Type> result = Types.isAssignable(subs, typeParamA, stringType);

    assertThat(result).containsExactly(typeParamA, stringType);
  }

  @Test
  public void isAssignable_usingCustomTypes() {
    Map<CelType, CelType> subs = new HashMap<>();
    CelType intType = SimpleType.INT;
    CelType customType = new CustomCelType();

    // A curated example where a CEL's int type can be assigned to a custom type.
    assertThat(Types.isAssignable(subs, intType, customType)).isEqualTo(subs);
    // But not the other way around.
    assertThat(Types.isAssignable(subs, customType, intType)).isNull();
  }

  private static final class CustomCelType extends CelType {

    @Override
    public CelKind kind() {
      return CelKind.INT;
    }

    @Override
    public String name() {
      return "customInt";
    }

    @Override
    public boolean isAssignableFrom(CelType other) {
      return super.isAssignableFrom(other) || other.equals(SimpleType.INT);
    }
  }
}
