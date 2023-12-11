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

package dev.cel.runtime;

// import com.google.testing.testsize.MediumTest;
import dev.cel.common.CelOptions;
import dev.cel.testing.BaseInterpreterTest;
import dev.cel.testing.Eval;
import dev.cel.testing.EvalCelValueSync;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

/** Tests for {@link Interpreter} and related functionality using {@code CelValue}. */
// @MediumTest
@RunWith(Parameterized.class)
public class CelValueInterpreterTest extends BaseInterpreterTest {

  private static final CelOptions SIGNED_UINT_TEST_OPTIONS =
      CelOptions.current()
          .enableTimestampEpoch(true)
          .enableHeterogeneousNumericComparisons(true)
          .enableCelValue(true)
          .comprehensionMaxIterations(1_000)
          .build();

  public CelValueInterpreterTest(boolean declareWithCelType, Eval eval) {
    super(declareWithCelType, eval);
  }

  /** Test relies on PartialMessage, which is deprecated and not supported for CelValue. */
  @Override
  @Test
  public void unknownField() {
    skipBaselineVerification();
  }

  /** Test relies on PartialMessage, which is deprecated and not supported for CelValue. */
  @Override
  @Test
  public void unknownResultSet() {
    skipBaselineVerification();
  }

  @Parameters
  public static List<Object[]> testData() {
    return new ArrayList<>(
        Arrays.asList(
            new Object[][] {
              // SYNC_PROTO_TYPE
              {
                /* declareWithCelType= */ false,
                new EvalCelValueSync(TEST_FILE_DESCRIPTORS, TEST_OPTIONS)
              },
              // SYNC_PROTO_TYPE_SIGNED_UINT
              {
                /* declareWithCelType= */ false,
                new EvalCelValueSync(TEST_FILE_DESCRIPTORS, SIGNED_UINT_TEST_OPTIONS)
              },
              // SYNC_CEL_TYPE
              {
                /* declareWithCelType= */ true,
                new EvalCelValueSync(TEST_FILE_DESCRIPTORS, TEST_OPTIONS)
              },
              // SYNC_CEL_TYPE_SIGNED_UINT
              {
                /* declareWithCelType= */ true,
                new EvalCelValueSync(TEST_FILE_DESCRIPTORS, SIGNED_UINT_TEST_OPTIONS)
              },
            }));
  }
}
