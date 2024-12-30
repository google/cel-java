package dev.cel.protobuf;

import static com.google.common.truth.Truth.assertThat;
import static dev.cel.common.CelDescriptorUtil.getAllDescriptorsFromFileDescriptor;

import com.google.common.collect.ImmutableList;
import com.google.testing.junit.testparameterinjector.TestParameterInjector;
import dev.cel.expr.conformance.proto3.TestAllTypes;
import dev.cel.protobuf.CelLiteDescriptor.MessageInfo;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(TestParameterInjector.class)
public class CelLiteDescriptorGeneratorTest {

  @Test
  public void collectMessageInfo() throws Exception {
    CelLiteDescriptorGenerator celLiteDescriptorGenerator = new CelLiteDescriptorGenerator();

    ImmutableList<MessageInfo> messageInfoList = celLiteDescriptorGenerator.collectMessageInfo(TestAllTypes.getDescriptor().getFile());

    assertThat(messageInfoList.size()).isEqualTo(3);
    assertThat(messageInfoList.get(0).getFullyQualifiedProtoName()).isEqualTo("cel.expr.conformance.proto3.TestAllTypes");
    assertThat(messageInfoList.get(0).getFullyQualifiedProtoJavaClassName()).isEqualTo("dev.cel.expr.conformance.proto3.TestAllTypes");
    assertThat(messageInfoList.get(1).getFullyQualifiedProtoName()).isEqualTo("cel.expr.conformance.proto3.TestAllTypes.NestedMessage");
    assertThat(messageInfoList.get(1).getFullyQualifiedProtoJavaClassName()).isEqualTo("dev.cel.expr.conformance.proto3.TestAllTypes$NestedMessage");
    assertThat(messageInfoList.get(2).getFullyQualifiedProtoName()).isEqualTo("cel.expr.conformance.proto3.NestedTestAllTypes");
    assertThat(messageInfoList.get(2).getFullyQualifiedProtoJavaClassName()).isEqualTo("dev.cel.expr.conformance.proto3.NestedTestAllTypes");
  }
}
