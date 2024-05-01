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

package codelab.solutions;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableMap;
import com.google.protobuf.util.Timestamps;
import com.google.testing.junit.testparameterinjector.TestParameterInjector;
import dev.cel.common.CelAbstractSyntaxTree;
import java.util.Map;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(TestParameterInjector.class)
public final class Exercise5Test {
  private final Exercise5 exercise5 = new Exercise5();

  @Test
  public void evaluate_jwtWithTimeVariable_producesJsonString() throws Exception {
    // Note the quoted keys in the CEL map literal. For proto messages the field names are unquoted
    // as they represent well-defined identifiers.
    String jwt =
        "{'sub': 'serviceAccount:delegate@acme.co',"
            + "'aud': 'my-project',"
            + "'iss': 'auth.acme.com:12350',"
            + "'iat': time,"
            + "'nbf': time,"
            + "'exp': time + duration('300s'),"
            + "'extra_claims': {"
            + "'group': 'admin'"
            + "}}";
    CelAbstractSyntaxTree ast = exercise5.compile(jwt);

    // The output of the program is a map type.
    @SuppressWarnings("unchecked")
    Map<String, Object> evaluatedResult =
        (Map<String, Object>)
            exercise5.eval(ast, ImmutableMap.of("time", Timestamps.fromSeconds(1698361778)));
    String jsonOutput = exercise5.toJson(evaluatedResult);

    assertThat(jsonOutput)
        .isEqualTo(
            "{\"sub\":\"serviceAccount:delegate@acme.co\","
                + "\"aud\":\"my-project\","
                + "\"iss\":\"auth.acme.com:12350\","
                + "\"iat\":\"2023-10-26T23:09:38Z\","
                + "\"nbf\":\"2023-10-26T23:09:38Z\","
                + "\"exp\":\"2023-10-26T23:14:38Z\","
                + "\"extra_claims\":{\"group\":\"admin\"}}");
  }
}
