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

package dev.cel.common.testing;

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.testing.junit.testparameterinjector.TestParameterValuesProvider;
import dev.cel.common.annotations.Internal;
import java.util.stream.IntStream;

/**
 * A simple test provider for repeating a test {@link #REPEATED_TEST_RUN_COUNT} times. To use:
 *
 * <ol>
 *   <li>Annotate your test class with @RunWith(TestParameterInjector.class)
 *   <li>Provide a @TestParameter as an argument with RepeatedTestProvider set as the
 *       valuesProvider. (Example: @TestParameter(valuesProvider = RepeatedTestProvider.class) int
 *       testRunIndex)
 * </ol>
 */
@Internal
@VisibleForTesting
public final class RepeatedTestProvider extends TestParameterValuesProvider {
  private static final int REPEATED_TEST_RUN_COUNT = 50;

  @Override
  public ImmutableList<Integer> provideValues(Context context) {
    return IntStream.rangeClosed(1, REPEATED_TEST_RUN_COUNT).boxed().collect(toImmutableList());
  }
}
