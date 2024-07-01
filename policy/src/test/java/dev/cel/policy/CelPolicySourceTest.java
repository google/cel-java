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

package dev.cel.policy;

import static com.google.common.truth.Truth.assertThat;

import com.google.testing.junit.testparameterinjector.TestParameterInjector;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(TestParameterInjector.class)
public final class CelPolicySourceTest {

  @Test
  public void constructPolicySource_success() {
    CelPolicySource policySource = CelPolicySource.newBuilder("hello world").build();

    assertThat(policySource.getContent().toString()).isEqualTo("hello world");
    assertThat(policySource.getDescription()).isEqualTo("<input>");
  }

  @Test
  public void getSnippet_success() {
    CelPolicySource policySource = CelPolicySource.newBuilder("hello\nworld").build();

    assertThat(policySource.getSnippet(1)).hasValue("hello");
    assertThat(policySource.getSnippet(2)).hasValue("world");
  }

  @Test
  public void getSnippet_returnsEmpty() {
    CelPolicySource policySource = CelPolicySource.newBuilder("hello\nworld").build();

    assertThat(policySource.getSnippet(3)).isEmpty();
  }
}
