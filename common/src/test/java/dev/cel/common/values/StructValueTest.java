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

import com.google.testing.junit.testparameterinjector.TestParameterInjector;
import com.google.testing.junit.testparameterinjector.TestParameters;
import dev.cel.common.types.CelType;
import dev.cel.common.types.StructTypeReference;
import java.util.Optional;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(TestParameterInjector.class)
public final class StructValueTest {

  @Test
  public void emptyStruct() {
    UserDefinedClass userDefinedPojo = new UserDefinedClass(0);
    CelCustomStruct celCustomStruct = new CelCustomStruct(userDefinedPojo);

    assertThat(celCustomStruct.value()).isEqualTo(userDefinedPojo);
    assertThat(celCustomStruct.isZeroValue()).isTrue();
  }

  @Test
  public void constructStruct() {
    UserDefinedClass userDefinedPojo = new UserDefinedClass(5);
    CelCustomStruct celCustomStruct = new CelCustomStruct(userDefinedPojo);

    assertThat(celCustomStruct.value()).isEqualTo(userDefinedPojo);
    assertThat(celCustomStruct.isZeroValue()).isFalse();
  }

  @Test
  public void selectField_success() {
    UserDefinedClass userDefinedPojo = new UserDefinedClass(5);
    CelCustomStruct celCustomStruct = new CelCustomStruct(userDefinedPojo);

    assertThat(celCustomStruct.select(StringValue.create("data"))).isEqualTo(IntValue.create(5L));
  }

  @Test
  public void selectField_nonExistentField_throws() {
    UserDefinedClass userDefinedPojo = new UserDefinedClass(5);
    CelCustomStruct celCustomStruct = new CelCustomStruct(userDefinedPojo);

    assertThrows(
        IllegalArgumentException.class, () -> celCustomStruct.select(StringValue.create("bogus")));
  }

  @Test
  @TestParameters("{fieldName: 'data', expectedResult: true}")
  @TestParameters("{fieldName: 'bogus', expectedResult: false}")
  public void findField_success(String fieldName, boolean expectedResult) {
    UserDefinedClass userDefinedPojo = new UserDefinedClass(5);
    CelCustomStruct celCustomStruct = new CelCustomStruct(userDefinedPojo);

    assertThat(celCustomStruct.find(StringValue.create(fieldName)).isPresent())
        .isEqualTo(expectedResult);
  }

  @Test
  public void celTypeTest() {
    UserDefinedClass userDefinedPojo = new UserDefinedClass(0);
    CelCustomStruct value = new CelCustomStruct(userDefinedPojo);

    assertThat(value.celType()).isEqualTo(StructTypeReference.create("customStruct"));
  }

  private static class UserDefinedClass {
    private final long data;

    private UserDefinedClass(long data) {
      this.data = data;
    }
  }

  @SuppressWarnings("Immutable") // Test only
  private static class CelCustomStruct extends StructValue<StringValue> {
    private final UserDefinedClass userDefinedClass;

    @Override
    public UserDefinedClass value() {
      return userDefinedClass;
    }

    @Override
    public boolean isZeroValue() {
      return userDefinedClass.data == 0;
    }

    @Override
    public CelType celType() {
      return StructTypeReference.create("customStruct");
    }

    @Override
    public CelValue select(StringValue field) {
      return find(field)
          .orElseThrow(() -> new IllegalArgumentException("Invalid field name: " + field));
    }

    @Override
    public Optional<CelValue> find(StringValue field) {
      if (field.value().equals("data")) {
        return Optional.of(IntValue.create(value().data));
      }

      return Optional.empty();
    }

    private CelCustomStruct(UserDefinedClass value) {
      this.userDefinedClass = value;
    }
  }
}
