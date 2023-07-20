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
import static org.junit.Assert.assertThrows;

import com.google.protobuf.FieldMask;
import dev.cel.checker.ProtoTypeMask.FieldPath;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class ProtoTypeMaskTest {

  @Test
  public void ofType() {
    ProtoTypeMask typeExpr = ProtoTypeMask.ofAllFields("google.type.Expr");
    assertThat(typeExpr.getTypeName()).isEqualTo("google.type.Expr");
    assertThat(typeExpr.areAllFieldPathsExposed()).isTrue();
    assertThat(typeExpr.getFieldPathsExposed())
        .containsExactly(FieldPath.of(ProtoTypeMask.WILDCARD_FIELD));
  }

  @Test
  public void ofType_emptyType() {
    assertThrows(IllegalArgumentException.class, () -> ProtoTypeMask.ofAllFields(""));
  }

  @Test
  public void ofType_nullType() {
    assertThrows(IllegalArgumentException.class, () -> ProtoTypeMask.ofAllFields(null));
  }

  @Test
  public void ofTypeWithFieldMask() {
    ProtoTypeMask typeExpr =
        ProtoTypeMask.of(
            "google.type.Expr",
            FieldMask.newBuilder().addPaths("expression").addPaths("description").build());
    assertThat(typeExpr.getTypeName()).isEqualTo("google.type.Expr");
    assertThat(typeExpr.areAllFieldPathsExposed()).isFalse();
    assertThat(typeExpr.getFieldPathsExposed())
        .containsExactly(FieldPath.of("expression"), FieldPath.of("description"));
  }

  @Test
  public void ofTypeWithFieldMask_complexMask() {
    ProtoTypeMask typeExpr =
        ProtoTypeMask.of(
            "google.rpc.context.AttributeContext",
            FieldMask.newBuilder().addPaths("resource.name").build());
    assertThat(typeExpr.areAllFieldPathsExposed()).isFalse();
    assertThat(typeExpr.getFieldPathsExposed()).containsExactly(FieldPath.of("resource.name"));
  }

  @Test
  public void ofTypeWithFieldMask_emptyMask() {
    assertThat(ProtoTypeMask.of("test", FieldMask.getDefaultInstance()).areAllFieldPathsExposed())
        .isTrue();
  }

  @Test
  public void ofTypeWithFieldMask_nullMask() {
    assertThat(ProtoTypeMask.of("test", null).areAllFieldPathsExposed()).isTrue();
  }

  @Test
  public void ofTypeWithFieldMask_invalidMask() {
    assertThrows(
        IllegalArgumentException.class,
        () -> ProtoTypeMask.of("test", FieldMask.newBuilder().addPaths("").build()));
  }

  @Test
  public void withFieldsAsVariableDeclarations() {
    assertThat(ProtoTypeMask.ofAllFields("google.type.Expr").fieldsAreVariableDeclarations())
        .isFalse();
    assertThat(
            ProtoTypeMask.ofAllFields("google.type.Expr")
                .withFieldsAsVariableDeclarations()
                .fieldsAreVariableDeclarations())
        .isTrue();
  }

  @Test
  public void fieldPathOf() {
    assertThat(FieldPath.of("resource.name").getFieldSelection())
        .containsExactly("resource", "name");
  }
}
