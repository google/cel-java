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

package dev.cel.validator;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import dev.cel.bundle.Cel;
import dev.cel.bundle.CelFactory;
import dev.cel.common.CelIssue.Severity;
import dev.cel.common.CelValidationResult;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class CelValidatorImplTest {
  private static final Cel CEL = CelFactory.standardCelBuilder().build();

  // private static final CelValidatorImpl CEL_VALIDATOR = new CelValidatorImpl(CEL,

  @Test
  public void constructCelValidator_success() {
    CelValidator celValidator =
        CelValidatorImpl.newBuilder(CEL)
            .addAstValidators(
                (navigableAst, cel, issuesFactory) -> {
                  // no-op
                })
            .build();

    assertThat(celValidator).isNotNull();
    assertThat(celValidator).isInstanceOf(CelValidatorImpl.class);
  }

  @Test
  public void validator_inOrder() throws Exception {
    List<Integer> list = new ArrayList<>();
    CelValidator celValidator =
        CelValidatorImpl.newBuilder(CEL)
            .addAstValidators((navigableAst, cel, issuesFactory) -> list.add(1))
            .addAstValidators((navigableAst, cel, issuesFactory) -> list.add(2))
            .addAstValidators((navigableAst, cel, issuesFactory) -> list.add(3))
            .build();

    CelValidationResult result = celValidator.validate(CEL.compile("'test'").getAst());

    assertThat(result.hasError()).isFalse();
    assertThat(list).containsExactly(1, 2, 3).inOrder();
  }

  @Test
  public void validator_whenAstValidatorThrows_throwsException() {
    CelValidator celValidator =
        CelValidatorImpl.newBuilder(CEL)
            .addAstValidators(
                (navigableAst, cel, issuesFactory) -> issuesFactory.addError(1, "Test error"))
            .addAstValidators(
                (navigableAst, cel, issuesFactory) -> {
                  throw new IllegalArgumentException("Test exception");
                })
            .build();

    IllegalArgumentException e =
        assertThrows(
            IllegalArgumentException.class,
            () -> celValidator.validate(CEL.compile("'test'").getAst()));
    assertThat(e).hasMessageThat().contains("Test exception");
  }

  @Test
  public void validator_addIssueWithAllSeverities() throws Exception {
    CelValidator celValidator =
        CelValidatorImpl.newBuilder(CEL)
            .addAstValidators(
                (navigableAst, cel, issuesFactory) -> issuesFactory.addError(1, "Test error"))
            .addAstValidators(
                (navigableAst, cel, issuesFactory) -> issuesFactory.addWarning(1, "Test warning"))
            .addAstValidators(
                (navigableAst, cel, issuesFactory) -> issuesFactory.addInfo(1, "Test info"))
            .build();

    CelValidationResult result = celValidator.validate(CEL.compile("'test'").getAst());

    assertThat(result.hasError()).isTrue();
    assertThat(result.getAllIssues()).hasSize(3);
    assertThat(result.getAllIssues().get(0).getSeverity()).isEqualTo(Severity.ERROR);
    assertThat(result.getAllIssues().get(1).getSeverity()).isEqualTo(Severity.WARNING);
    assertThat(result.getAllIssues().get(2).getSeverity()).isEqualTo(Severity.INFORMATION);
    assertThat(result.getIssueString())
        .contains(
            "ERROR: <input>:1:1: Test error\n"
                + " | 'test'\n"
                + " | ^\n"
                + "WARNING: <input>:1:1: Test warning\n"
                + " | 'test'\n"
                + " | ^\n"
                + "INFORMATION: <input>:1:1: Test info\n"
                + " | 'test'\n"
                + " | ^");
  }

  @Test
  public void parsedAst_throwsException() throws Exception {
    CelValidator celValidator = CelValidatorImpl.newBuilder(CEL).build();

    IllegalArgumentException e =
        assertThrows(
            IllegalArgumentException.class,
            () -> celValidator.validate(CEL.parse("'test'").getAst()));
    assertThat(e).hasMessageThat().contains("AST must be type-checked.");
  }
}
