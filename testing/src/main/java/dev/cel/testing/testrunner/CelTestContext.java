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

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableMap;
import dev.cel.bundle.Cel;
import dev.cel.bundle.CelFactory;
import dev.cel.common.CelOptions;
import dev.cel.policy.CelPolicyParser;
import dev.cel.runtime.CelLateFunctionBindings;
import java.util.Map;
import java.util.Optional;

/**
 * The context class for a CEL test, holding configurations needed to create environments and
 * evaluate CEL expressions and policies.
 */
@AutoValue
public abstract class CelTestContext {

  private static final Cel DEFAULT_CEL = CelFactory.standardCelBuilder().build();

  /**
   * The CEL environment for the CEL test.
   *
   * <p>The CEL environment is created by extending the provided base CEL environment with the
   * config file if provided.
   */
  public abstract Cel cel();

  /**
   * The CEL policy parser for the CEL test.
   *
   * <p>A custom parser to be used for parsing CEL policies in scenarios where custom policy tags
   * are used. If not provided, the default CEL policy parser will be used.
   */
  public abstract Optional<CelPolicyParser> celPolicyParser();

  /**
   * The CEL options for the CEL test.
   *
   * <p>The CEL options are used to configure the {@link Cel} environment.
   */
  public abstract CelOptions celOptions();

  /**
   * The late function bindings for the CEL test.
   *
   * <p>These bindings are used to provide functions which are to be consumed during the eval phase
   * directly.
   */
  public abstract Optional<CelLateFunctionBindings> celLateFunctionBindings();

  /**
   * The variable bindings for the CEL test.
   *
   * <p>These bindings are used to provide values for variables for which it is difficult to provide
   * a value in the test suite file for example, using proto extensions or fetching the value from
   * some other source.
   */
  public abstract ImmutableMap<String, Object> variableBindings();

  /**
   * The result matcher for the CEL test.
   *
   * <p>This matcher is used to perform assertions on the result of a CEL test case.
   */
  public abstract ResultMatcher resultMatcher();

  /**
   * The CEL expression to be tested. Could be a expression string or a policy/cel file path. This
   * should only be used when invoking the runner library directly.
   */
  public abstract Optional<CelExpressionSource> celExpression();

  /**
   * The config file for the CEL test.
   *
   * <p>The config file is used to provide a custom environment for the CEL test.
   */
  public abstract Optional<String> configFile();

  /**
   * The file descriptor set path for the CEL test.
   *
   * <p>The file descriptor set path is used to provide proto descriptors for the CEL test.
   */
  public abstract Optional<String> fileDescriptorSetPath();

  /** Returns a builder for {@link CelTestContext} with the current instance's values. */
  public abstract Builder toBuilder();

  /** Returns a new builder for {@link CelTestContext}. */
  public static CelTestContext.Builder newBuilder() {
    return new AutoValue_CelTestContext.Builder()
        .setCel(DEFAULT_CEL)
        .setCelOptions(CelOptions.DEFAULT)
        .setVariableBindings(ImmutableMap.of())
        .setResultMatcher(new DefaultResultMatcher());
  }

  /** Builder for {@link CelTestContext}. */
  @AutoValue.Builder
  public abstract static class Builder {
    public abstract Builder setCel(Cel cel);

    public abstract Builder setCelPolicyParser(CelPolicyParser celPolicyParser);

    public abstract Builder setCelOptions(CelOptions celOptions);

    public abstract Builder setCelLateFunctionBindings(
        CelLateFunctionBindings celLateFunctionBindings);

    public abstract Builder setVariableBindings(Map<String, Object> variableBindings);

    public abstract Builder setResultMatcher(ResultMatcher resultMatcher);

    public abstract Builder setCelExpression(CelExpressionSource celExpression);

    public abstract Builder setConfigFile(String configFile);

    public abstract Builder setFileDescriptorSetPath(String fileDescriptorSetPath);

    public abstract CelTestContext build();
  }
}
