package dev.cel.protobuf;

import com.google.testing.junit.testparameterinjector.TestParameterInjector;
import dev.cel.bundle.Cel;
import dev.cel.bundle.CelFactory;
import dev.cel.testing.SimpleTestCelLiteDescriptor;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(TestParameterInjector.class)
public class CelLiteDescriptorGeneratorTest {

  @Test
  public void smokeTest() {
    SimpleTestCelLiteDescriptor simpleTestCelLiteDescriptor = SimpleTestCelLiteDescriptor.getDescriptor();
    System.out.println();

  }
}
