package dev.cel.protobuf;

import com.google.common.collect.ImmutableList;
import com.google.testing.junit.testparameterinjector.TestParameterInjector;
import dev.cel.expr.conformance.proto3.TestAllTypes;
import dev.cel.protobuf.CelLiteDescriptor.MessageLiteDescriptor;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(TestParameterInjector.class)
public class ProtoDescriptorCollectorTest {

  @Test
  public void smokeTest() {
    ProtoDescriptorCollector collector = ProtoDescriptorCollector.newInstance();

    ImmutableList<MessageLiteDescriptor> descriptors = collector.collectCodegenMetadata(
        TestAllTypes.getDescriptor().getFile());

    System.out.println(descriptors);
  }
}
