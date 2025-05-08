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

package dev.cel.runtime;

import com.google.testing.junit.testparameterinjector.TestParameter;
import com.google.testing.junit.testparameterinjector.TestParameterInjector;
import dev.cel.common.values.ProtoMessageLiteValueProvider;
import dev.cel.expr.conformance.proto3.TestAllTypesCelDescriptor;
import dev.cel.extensions.CelOptionalLibrary;
import dev.cel.testing.BaseInterpreterTest;
import org.junit.runner.RunWith;

/**
 * Exercises a suite of interpreter tests defined in {@link BaseInterpreterTest} using {@link
 * ProtoMessageLiteValueProvider} and <b>full version of protobuf messages</b>.
 */
@RunWith(TestParameterInjector.class)
public class CelLiteInterpreterTest extends BaseInterpreterTest {
  public CelLiteInterpreterTest(@TestParameter InterpreterTestOption testOption) {
    super(
        testOption.celOptions.toBuilder().enableCelValue(true).build(),
        testOption.useNativeCelType,
        CelRuntimeFactory.standardCelRuntimeBuilder()
            .setValueProvider(
                ProtoMessageLiteValueProvider.newInstance(
                    TestAllTypesCelDescriptor.getDescriptor()))
            .addLibraries(CelOptionalLibrary.INSTANCE)
            .setOptions(testOption.celOptions.toBuilder().enableCelValue(true).build())
            .build());
  }

  @Override
  public void dynamicMessage_adapted() throws Exception {
    // Dynamic message is not supported in Protolite
    skipBaselineVerification();
  }

  @Override
  public void dynamicMessage_dynamicDescriptor() throws Exception {
    // Dynamic message is not supported in Protolite
    skipBaselineVerification();
  }

  // All the tests below rely on message creation with fields populated. They are excluded for time
  // being until this support is added.
  @Override
  public void wrappers() throws Exception {
    skipBaselineVerification();
  }

  @Override
  public void jsonConversions() {
    skipBaselineVerification();
  }

  @Override
  public void nestedEnums() {
    skipBaselineVerification();
  }

  @Override
  public void messages() throws Exception {
    skipBaselineVerification();
  }

  @Override
  public void packUnpackAny() {
    skipBaselineVerification();
  }

  @Override
  public void lists() throws Exception {
    skipBaselineVerification();
  }

  @Override
  public void maps() throws Exception {
    skipBaselineVerification();
  }

  @Override
  public void jsonValueTypes() {
    skipBaselineVerification();
  }

  @Override
  public void messages_error() {
    skipBaselineVerification();
  }
}
