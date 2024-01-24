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

package dev.cel.common;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class CelValidationExceptionTest {

  @Test
  public void construct_withLargeErrorCount() {
    ImmutableList.Builder<CelIssue> issueBuilder = ImmutableList.builder();
    for (int i = 0; i < 50000; i++) {
      issueBuilder.add(CelIssue.formatError(i + 1, i + 1, "generic error"));
    }

    CelValidationException celValidationException =
        new CelValidationException(CelSource.newBuilder().build(), issueBuilder.build());

    assertThat(celValidationException).isNotNull();
    assertThat(celValidationException.getErrors()).hasSize(50000);
  }
}
