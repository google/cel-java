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

import com.google.common.collect.ImmutableList;
import dev.cel.bundle.Cel;
import dev.cel.common.CelIssue;
import dev.cel.common.CelIssue.Severity;
import dev.cel.common.CelSource;
import dev.cel.common.navigation.CelNavigableAst;

/** Public interface for performing a single, custom validation on an AST. */
public interface CelAstValidator {

  void validate(CelNavigableAst navigableAst, Cel cel, IssuesFactory issuesFactory);

  /** Factory for populating issues while performing AST validation. */
  public final class IssuesFactory {
    private final ImmutableList.Builder<CelIssue> issuesBuilder;
    private final CelNavigableAst navigableAst;

    public IssuesFactory(CelNavigableAst navigableAst) {
      this.navigableAst = navigableAst;
      this.issuesBuilder = ImmutableList.builder();
    }

    /** Adds an error for the expression. */
    public void addError(long exprId, String message) {
      add(exprId, message, Severity.ERROR);
    }

    /** Adds a warning for the expression. */
    public void addWarning(long exprId, String message) {
      add(exprId, message, Severity.WARNING);
    }

    /** Adds an info for the expression. */
    public void addInfo(long exprId, String message) {
      add(exprId, message, Severity.INFORMATION);
    }

    private void add(long exprId, String message, Severity severity) {
      CelSource source = navigableAst.getAst().getSource();
      int position = source.getPositionsMap().get(exprId);
      issuesBuilder.add(
          CelIssue.newBuilder()
              .setSeverity(severity)
              .setMessage(message)
              .setSourceLocation(source.getOffsetLocation(position).get())
              .build());
    }

    public ImmutableList<CelIssue> getIssues() {
      return issuesBuilder.build();
    }
  }
}
