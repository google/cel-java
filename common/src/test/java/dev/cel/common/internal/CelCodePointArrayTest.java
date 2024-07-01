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

package dev.cel.common.internal;

import static com.google.common.truth.Truth.assertThat;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.testing.junit.testparameterinjector.TestParameter;
import com.google.testing.junit.testparameterinjector.TestParameterInjector;
import com.google.testing.junit.testparameterinjector.TestParameterValuesProvider;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(TestParameterInjector.class)
public final class CelCodePointArrayTest {

  @Test
  public void computeLineOffset(
      @TestParameter(valuesProvider = LineOffsetDataProvider.class) LineOffsetTestCase testCase) {
    CelCodePointArray codePointArray = CelCodePointArray.fromString(testCase.text());

    assertThat(codePointArray.lineOffsets())
        .containsExactlyElementsIn(testCase.offsets())
        .inOrder();
  }

  @AutoValue
  abstract static class LineOffsetTestCase {
    abstract String text();

    abstract ImmutableList<Integer> offsets();

    static LineOffsetTestCase of(String text, Integer... offsets) {
      return of(text, Arrays.asList(offsets));
    }

    static LineOffsetTestCase of(String text, List<Integer> offsets) {
      return new AutoValue_CelCodePointArrayTest_LineOffsetTestCase(
          text, ImmutableList.copyOf(offsets));
    }
  }

  private static final class LineOffsetDataProvider extends TestParameterValuesProvider {

    @Override
    protected List<LineOffsetTestCase> provideValues(Context context) {
      return Arrays.asList(
          // Empty
          LineOffsetTestCase.of("", 1),
          // ISO-8859-1
          LineOffsetTestCase.of("hello world", 12),
          LineOffsetTestCase.of("hello\nworld", 6, 12),
          LineOffsetTestCase.of("hello\nworld\n\nfoo\n", 6, 12, 13, 17, 18),
          // BMP
          LineOffsetTestCase.of("abc 가나다", 8),
          LineOffsetTestCase.of("abc\n가나다\n我b很好\n", 4, 8, 13, 14),
          // SMP
          LineOffsetTestCase.of(" text 가나다 😦😁😑 ", 15),
          LineOffsetTestCase.of(" text\n가나다 \n😦😁😑\n\n", 6, 11, 15, 16, 17));
    }
  }
}
