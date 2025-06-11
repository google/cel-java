// Copyright 2025 Google LLC
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
package dev.cel.testing.testrunner;

import com.google.auto.value.AutoOneOf;
import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.errorprone.annotations.CheckReturnValue;
import com.google.protobuf.Any;
import dev.cel.common.Source;
import java.util.Optional;
import java.util.Set;

/** Class representing a CEL test suite which is generated post parsing the test suite file. */
@AutoValue
public abstract class CelTestSuite {

  public abstract String name();

  /** Test suite source in textual format (ex: textproto, YAML). */
  public abstract Optional<Source> source();

  public abstract String description();

  public abstract ImmutableSet<CelTestSection> sections();

  /** Builder for {@link CelTestSuite}. */
  @AutoValue.Builder
  public abstract static class Builder {

    public abstract Builder setName(String name);

    public abstract Builder setDescription(String description);

    public abstract Builder setSections(Set<CelTestSection> section);

    public abstract Builder setSource(Source source);

    @CheckReturnValue
    public abstract CelTestSuite build();
  }

  public abstract Builder toBuilder();

  public static Builder newBuilder() {
    return new AutoValue_CelTestSuite.Builder();
  }

  /**
   * Class representing a CEL test section within a test suite following the schema in {@link
   * dev.cel.expr.conformance.test.TestSuite}.
   */
  @AutoValue
  public abstract static class CelTestSection {

    public abstract String name();

    public abstract String description();

    public abstract ImmutableSet<CelTestCase> tests();

    /** Builder for {@link CelTestSection}. */
    @AutoValue.Builder
    public abstract static class Builder {

      public abstract Builder setName(String name);

      public abstract Builder setTests(Set<CelTestCase> tests);

      public abstract Builder setDescription(String description);

      @CheckReturnValue
      public abstract CelTestSection build();
    }

    public abstract Builder toBuilder();

    public static Builder newBuilder() {
      return new AutoValue_CelTestSuite_CelTestSection.Builder();
    }

    /** Class representing a CEL test case within a test section. */
    @AutoValue
    public abstract static class CelTestCase {

      public abstract String name();

      public abstract String description();

      public abstract Input input();

      public abstract Output output();

      /** This class represents the input of a CEL test case. */
      @AutoOneOf(Input.Kind.class)
      public abstract static class Input {
        /** Kind of input for a CEL test case. */
        public enum Kind {
          BINDINGS,
          CONTEXT_EXPR,
          CONTEXT_MESSAGE,
          NO_INPUT
        }

        public abstract Input.Kind kind();

        public abstract ImmutableMap<String, Binding> bindings();

        public abstract String contextExpr();

        public abstract Any contextMessage();

        public abstract void noInput();

        public static Input ofBindings(ImmutableMap<String, Binding> bindings) {
          return AutoOneOf_CelTestSuite_CelTestSection_CelTestCase_Input.bindings(bindings);
        }

        public static Input ofContextExpr(String contextExpr) {
          return AutoOneOf_CelTestSuite_CelTestSection_CelTestCase_Input.contextExpr(contextExpr);
        }

        public static Input ofContextMessage(Any contextMessage) {
          return AutoOneOf_CelTestSuite_CelTestSection_CelTestCase_Input.contextMessage(
              contextMessage);
        }

        public static Input ofNoInput() {
          return AutoOneOf_CelTestSuite_CelTestSection_CelTestCase_Input.noInput();
        }

        /** This class represents a binding for a CEL test case. */
        @AutoOneOf(Binding.Kind.class)
        public abstract static class Binding {

          /** Kind of binding for a CEL test case. */
          public enum Kind {
            VALUE,
            EXPR
          }

          public abstract Binding.Kind kind();

          public abstract Object value();

          public abstract String expr();

          public static Binding ofValue(Object value) {
            return AutoOneOf_CelTestSuite_CelTestSection_CelTestCase_Input_Binding.value(value);
          }

          public static Binding ofExpr(String expr) {
            return AutoOneOf_CelTestSuite_CelTestSection_CelTestCase_Input_Binding.expr(expr);
          }
        }
      }

      /** This class represents the result of a CEL test case. */
      @AutoOneOf(Output.Kind.class)
      public abstract static class Output {
        /** Kind of result for a CEL test case. */
        public enum Kind {
          RESULT_VALUE,
          RESULT_EXPR,
          EVAL_ERROR,
          UNKNOWN_SET,
          NO_OUTPUT
        }

        public abstract Output.Kind kind();

        public abstract Object resultValue();

        public abstract String resultExpr();

        public abstract void noOutput();

        public abstract ImmutableList<Object> evalError();

        public abstract ImmutableList<Long> unknownSet();

        public static Output ofResultValue(Object resultValue) {
          return AutoOneOf_CelTestSuite_CelTestSection_CelTestCase_Output.resultValue(resultValue);
        }

        public static Output ofResultExpr(String resultExpr) {
          return AutoOneOf_CelTestSuite_CelTestSection_CelTestCase_Output.resultExpr(resultExpr);
        }

        public static Output ofEvalError(ImmutableList<Object> errors) {
          return AutoOneOf_CelTestSuite_CelTestSection_CelTestCase_Output.evalError(errors);
        }

        public static Output ofUnknownSet(ImmutableList<Long> unknownSet) {
          return AutoOneOf_CelTestSuite_CelTestSection_CelTestCase_Output.unknownSet(unknownSet);
        }

        public static Output ofNoOutput() {
          return AutoOneOf_CelTestSuite_CelTestSection_CelTestCase_Output.noOutput();
        }
      }

      /** Builder for {@link CelTestCase}. */
      @AutoValue.Builder
      public abstract static class Builder {

        public abstract Builder setName(String name);

        public abstract Builder setDescription(String description);

        public abstract Builder setInput(Input input);

        public abstract Builder setOutput(Output output);

        @CheckReturnValue
        public abstract CelTestCase build();
      }

      public abstract Builder toBuilder();

      public static Builder newBuilder() {
        return new AutoValue_CelTestSuite_CelTestSection_CelTestCase.Builder()
            .setInput(Input.ofNoInput()); // Default input to no input.
      }
    }
  }
}
