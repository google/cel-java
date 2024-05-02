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

package codelab;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableMap;
import com.google.protobuf.Struct;
import com.google.protobuf.Timestamp;
import com.google.protobuf.Value;
import com.google.protobuf.util.Timestamps;
import com.google.rpc.context.AttributeContext;
import com.google.testing.junit.testparameterinjector.TestParameterInjector;
import dev.cel.common.CelAbstractSyntaxTree;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(TestParameterInjector.class)
public final class Exercise6Test {
  private final Exercise6 exercise6 = new Exercise6();

  @Test
  public void evaluate_constructAttributeContext() {
    // Given JSON web token and the current time as input variables,
    // Setup an expression to construct an AttributeContext protobuf object.
    //
    // Note: the field names within the proto message types are not quoted as they
    // are well-defined names composed of valid identifier characters. Also, note
    // that when building nested proto objects, the message name needs to prefix
    // the object construction.
    String expression =
        "Request{\n"
            + "auth: Auth{"
            + "  principal: jwt.iss + '/' + jwt.sub,"
            + "  audiences: [jwt.aud],"
            + "  presenter: 'azp' in jwt ? jwt.azp : '',"
            + "  claims: jwt"
            + "},"
            + "time: now"
            + "}";
    // Values for `now` and `jwt` variables to be passed into the runtime
    Timestamp now = Timestamps.now();
    ImmutableMap<String, Object> jwt =
        ImmutableMap.of(
            "sub", "serviceAccount:delegate@acme.co",
            "aud", "my-project",
            "iss", "auth.acme.com:12350",
            "extra_claims", ImmutableMap.of("group", "admin"));
    AttributeContext.Request expectedMessage =
        AttributeContext.Request.newBuilder()
            .setTime(now)
            .setAuth(
                AttributeContext.Auth.newBuilder()
                    .setPrincipal("auth.acme.com:12350/serviceAccount:delegate@acme.co")
                    .addAudiences("my-project")
                    .setClaims(
                        Struct.newBuilder()
                            .putAllFields(
                                ImmutableMap.of(
                                    "sub", newStringValue("serviceAccount:delegate@acme.co"),
                                    "aud", newStringValue("my-project"),
                                    "iss", newStringValue("auth.acme.com:12350")))
                            .putFields(
                                "extra_claims",
                                Value.newBuilder()
                                    .setStructValue(
                                        Struct.newBuilder()
                                            .putFields("group", newStringValue("admin"))
                                            .build())
                                    .build())))
            .build();

    // Compile the `Request` message construction expression and validate that
    // the resulting expression type matches the fully qualified message name.
    CelAbstractSyntaxTree ast = exercise6.compile(expression);
    AttributeContext.Request evaluatedResult =
        (AttributeContext.Request)
            exercise6.eval(
                ast,
                ImmutableMap.of(
                    "now", now,
                    "jwt", jwt));

    assertThat(evaluatedResult).isEqualTo(expectedMessage);
  }

  private static Value newStringValue(String value) {
    return Value.newBuilder().setStringValue(value).build();
  }
}
