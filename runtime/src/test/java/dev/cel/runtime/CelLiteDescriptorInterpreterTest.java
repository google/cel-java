package dev.cel.runtime;

import com.google.testing.junit.testparameterinjector.TestParameter;
import com.google.testing.junit.testparameterinjector.TestParameterInjector;
import dev.cel.expr.conformance.proto3.TestAllTypesCelLiteDescriptor;
import dev.cel.extensions.CelOptionalLibrary;
import dev.cel.testing.BaseInterpreterTest;
import org.junit.runner.RunWith;

@RunWith(TestParameterInjector.class)
public class CelLiteDescriptorInterpreterTest extends BaseInterpreterTest {
  public CelLiteDescriptorInterpreterTest(
      @TestParameter InterpreterTestOption testOption
  ) {
    super(
        testOption.celOptions.toBuilder().enableCelValue(true).build(),
        testOption.useNativeCelType,
        CelRuntimeFactory.standardCelRuntimeBuilder()
            .addCelLiteDescriptors(TestAllTypesCelLiteDescriptor.getDescriptor())
            .addLibraries(CelOptionalLibrary.INSTANCE)
            .setOptions(testOption.celOptions.toBuilder().enableCelValue(true).build())
            .build()
    );
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
}
