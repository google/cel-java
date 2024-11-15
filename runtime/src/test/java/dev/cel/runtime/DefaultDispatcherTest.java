// Copyright 2024 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License aj
//
//      https://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package dev.cel.runtime;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import java.util.HashMap;
import java.util.Map;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link DefaultDispatcher}. */
@RunWith(JUnit4.class)
public final class DefaultDispatcherTest {

  private Map<String, CelResolvedOverload> overloads;

  @Before
  public void setup() {
    overloads = new HashMap<>();
    overloads.put(
        "overload_1",
        CelResolvedOverload.of(
            "overload_1", new Class<?>[] {Long.class}, args -> (Long) args[0] + 1));
    overloads.put(
        "overload_2",
        CelResolvedOverload.of(
            "overload_2", new Class<?>[] {Long.class}, args -> (Long) args[0] + 2));
  }

  @Test
  public void findOverload_multipleMatches_throwsException() {
    InterpreterException e =
        Assert.assertThrows(
            InterpreterException.class,
            () ->
                DefaultDispatcher.findOverload(
                    "overloads",
                    ImmutableList.of("overload_1", "overload_2"),
                    overloads,
                    new Object[] {1L}));
    assertThat(e).hasMessageThat().contains("Matching candidates: overload_1, overload_2");
  }
}
