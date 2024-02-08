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

package dev.cel.checker;

import static com.google.common.truth.Truth.assertThat;
import static dev.cel.common.CelFunctionDecl.newFunctionDeclaration;
import static dev.cel.common.CelOverloadDecl.newGlobalOverload;
import static dev.cel.common.CelOverloadDecl.newMemberOverload;

import dev.cel.common.CelFunctionDecl;
import dev.cel.common.CelOverloadDecl;
import dev.cel.common.types.SimpleType;
import java.util.Iterator;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class CelFunctionDeclTest {

  @Test
  public void declareGlobalFunction_success() {
    CelFunctionDecl functionDecl =
        newFunctionDeclaration(
            "testGlobalFunction",
            newGlobalOverload("overloadId", SimpleType.BOOL, SimpleType.STRING, SimpleType.ANY));

    assertThat(functionDecl.name()).isEqualTo("testGlobalFunction");
    assertThat(functionDecl.overloads()).hasSize(1);
    CelOverloadDecl overloadDecl = functionDecl.overloads().iterator().next();
    assertThat(overloadDecl.overloadId()).isEqualTo("overloadId");
    assertThat(overloadDecl.isInstanceFunction()).isFalse();
    assertThat(overloadDecl.resultType()).isEqualTo(SimpleType.BOOL);
    assertThat(overloadDecl.parameterTypes()).containsExactly(SimpleType.STRING, SimpleType.ANY);
  }

  @Test
  public void declareMemberFunction_success() {
    CelFunctionDecl functionDecl =
        newFunctionDeclaration(
            "testMemberFunction",
            newMemberOverload("overloadId", SimpleType.TIMESTAMP, SimpleType.INT, SimpleType.UINT));

    assertThat(functionDecl.name()).isEqualTo("testMemberFunction");
    assertThat(functionDecl.overloads()).hasSize(1);
    CelOverloadDecl overloadDecl = functionDecl.overloads().iterator().next();
    assertThat(overloadDecl.overloadId()).isEqualTo("overloadId");
    assertThat(overloadDecl.isInstanceFunction()).isTrue();
    assertThat(overloadDecl.resultType()).isEqualTo(SimpleType.TIMESTAMP);
    assertThat(overloadDecl.parameterTypes()).containsExactly(SimpleType.INT, SimpleType.UINT);
  }

  @Test
  public void declareFunction_withBuilder_success() {
    CelFunctionDecl.Builder functionDeclBuilder =
        CelFunctionDecl.newBuilder()
            .setName("testFunction")
            .addOverloads(
                newMemberOverload("memberOverloadId", SimpleType.INT, SimpleType.UINT),
                newGlobalOverload("globalOverloadId", SimpleType.STRING, SimpleType.BOOL));
    CelFunctionDecl functionDecl = functionDeclBuilder.build();

    assertThat(functionDecl.name()).isEqualTo("testFunction");
    assertThat(functionDecl.overloads()).hasSize(2);

    Iterator<CelOverloadDecl> iterator = functionDecl.overloads().iterator();
    CelOverloadDecl memberOverloadDecl = iterator.next();
    assertThat(memberOverloadDecl.overloadId()).isEqualTo("memberOverloadId");
    assertThat(memberOverloadDecl.isInstanceFunction()).isTrue();
    assertThat(memberOverloadDecl.resultType()).isEqualTo(SimpleType.INT);
    assertThat(memberOverloadDecl.parameterTypes()).containsExactly(SimpleType.UINT);

    CelOverloadDecl globalOverloadDecl = iterator.next();
    assertThat(globalOverloadDecl.overloadId()).isEqualTo("globalOverloadId");
    assertThat(globalOverloadDecl.isInstanceFunction()).isFalse();
    assertThat(globalOverloadDecl.resultType()).isEqualTo(SimpleType.STRING);
    assertThat(globalOverloadDecl.parameterTypes()).containsExactly(SimpleType.BOOL);
  }
}
